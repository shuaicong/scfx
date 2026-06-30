package com.scfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.entity.ParseLog;
import com.scfx.entity.ParseRecord;
import com.scfx.entity.WasdeData;
import com.scfx.enums.ParseStatusEnum;
import com.scfx.mapper.ParseLogMapper;
import com.scfx.mapper.ParseRecordMapper;
import com.scfx.mapper.WasdeDataMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * WASDE/CONAB 报告解析调度服务。
 * <p>
 * 职责：幂等校验 → Redis 分布式锁 → MinIO 读取 → XML 解析 → 批量入库 → 状态更新 → 异常告警
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportParseService {

    private final WasdeDataMapper wasdeDataMapper;
    private final ParseRecordMapper parseRecordMapper;
    private final ParseLogMapper parseLogMapper;
    private final MinioClient minioClient;
    private final StringRedisTemplate redisTemplate;
    private final AlertService alertService;
    private final WasdeXmlParser wasdeXmlParser;

    @Value("${parse.minio.bucket:wasde-xml}")
    private String minioBucket;

    @Value("${parse.retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${parse.retry.base-delay-ms:1000}")
    private long baseDelayMs;

    // ==================== 核心调度方法 ====================

    /**
     * 触发单次解析（幂等）。
     *
     * @param sourceType 数据来源（wasde / conab）
     * @param reportKey  报告标识
     * @param minioPath  MinIO 对象路径（相对桶的路径）
     * @param reportDate 报告日期
     */
    @Transactional
    public void triggerParse(String sourceType, String reportKey,
                              String minioPath, LocalDate reportDate) {
        // ---------- 1. 幂等校验 ----------
        ParseRecord existing = parseRecordMapper.selectOne(
                new LambdaQueryWrapper<ParseRecord>()
                        .eq(ParseRecord::getSourceType, sourceType)
                        .eq(ParseRecord::getReportKey, reportKey)
        );
        if (existing != null && ParseStatusEnum.SUCCESS.getCode().equals(existing.getStatus())) {
            log.info("报告已解析成功，跳过: sourceType={}, reportKey={}", sourceType, reportKey);
            return;
        }

        // ---------- 2. Redis 分布式锁 ----------
        String lockKey = "lock:parse:" + sourceType + ":" + reportKey;
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(300));
        if (Boolean.FALSE.equals(locked)) {
            log.warn("其他线程正在解析该报告，跳过: sourceType={}, reportKey={}", sourceType, reportKey);
            return;
        }
        try {
            // ---------- 3. 创建/更新解析记录 ----------
            Long recordId;
            ParseRecord record;
            if (existing != null) {
                record = existing;
                record.setStatus(ParseStatusEnum.PENDING.getCode());
                record.setErrorMessage(null);
                record.setMinioPath(minioPath);
                record.setReportDate(reportDate);
                parseRecordMapper.updateById(record);
                recordId = record.getId();
            } else {
                record = new ParseRecord();
                record.setSourceType(sourceType);
                record.setReportKey(reportKey);
                record.setStatus(ParseStatusEnum.PENDING.getCode());
                record.setMinioPath(minioPath);
                record.setReportDate(reportDate);
                record.setRetryCount(0);
                record.setCreatedAt(LocalDateTime.now());
                parseRecordMapper.insert(record);
                recordId = record.getId();
            }

            // ---------- 4. 从 MinIO 读取 XML（阶梯重试） ----------
            byte[] xmlBytes = readFromMinioWithRetry(minioPath);

            // ---------- 5. 解析 XML ----------
            List<WasdeData> dataList;
            try (InputStream xmlStream = new java.io.ByteArrayInputStream(xmlBytes)) {
                dataList = wasdeXmlParser.parse(sourceType, reportKey, reportDate, xmlStream);
            }

            if (dataList.isEmpty()) {
                throw new RuntimeException("XML 解析结果为空，无可入库数据");
            }
            log.info("XML 解析完成, reportKey={}, 行数={}", reportKey, dataList.size());

            // ---------- 6. 批量入库 ----------
            int affectedRows = wasdeDataMapper.batchInsertOrUpdate(dataList);
            log.info("批量入库完成, reportKey={}, 影响行数={}", reportKey, affectedRows);

            // ---------- 7. 更新记录为成功 ----------
            record.setStatus(ParseStatusEnum.SUCCESS.getCode());
            record.setParseAt(LocalDateTime.now());
            parseRecordMapper.updateById(record);

            log.info("报告解析成功: sourceType={}, reportKey={}, 数据量={}",
                    sourceType, reportKey, dataList.size());

        } catch (Exception e) {
            log.error("报告解析失败: sourceType={}, reportKey={}", sourceType, reportKey, e);
            markFailed(sourceType, reportKey, minioPath, e);
            throw new RuntimeException("报告解析失败: " + e.getMessage(), e);
        } finally {
            // ---------- 释放 Redis 锁 ----------
            redisTemplate.delete(lockKey);
        }
    }

    // ==================== 异常处理 ====================

    /**
     * 标记解析失败：更新记录 + 写入日志 + 钉钉告警。
     */
    @Transactional
    public void markFailed(String sourceType, String reportKey,
                            String minioPath, Exception e) {
        // 1. 更新 parse_record 状态
        ParseRecord record = parseRecordMapper.selectOne(
                new LambdaQueryWrapper<ParseRecord>()
                        .eq(ParseRecord::getSourceType, sourceType)
                        .eq(ParseRecord::getReportKey, reportKey)
        );
        if (record != null) {
            record.setStatus(ParseStatusEnum.FAILED.getCode());
            record.setErrorMessage(truncate(e.getMessage(), 500));
            record.setRetryCount(record.getRetryCount() == null ? 1 : record.getRetryCount() + 1);
            parseRecordMapper.updateById(record);
        }

        // 2. 写入 parse_log
        ParseLog parseLog = new ParseLog();
        parseLog.setSourceType(sourceType);
        parseLog.setReportKey(reportKey);
        parseLog.setLevel("ERROR");
        parseLog.setMessage("报告解析失败: " + truncate(e.getMessage(), 500));
        parseLog.setStackTrace(truncate(getStackTrace(e), 2000));
        parseLog.setMinioPath(minioPath);
        parseLog.setCreatedAt(LocalDateTime.now());
        parseLogMapper.insert(parseLog);

        // 3. 钉钉告警（通过 AlertService）
        String title = "WASDE 解析失败 - " + sourceType + "/" + reportKey;
        String content = String.format(
                "报告周期: %s\n错误原因: %s\nMinIO 路径: %s\n时间: %s",
                reportKey,
                truncate(e.getMessage(), 200),
                minioPath,
                LocalDateTime.now()
        );
        try {
            alertService.createAlert("PARSE_ERROR", "error", title, content, reportKey);
            log.info("解析失败告警已发送: title={}", title);
        } catch (Exception alertEx) {
            log.error("发送解析失败告警异常: {}", alertEx.getMessage());
        }
    }

    // ==================== 异步批量解析 ====================

    /**
     * 异步批量解析指定时间段内的所有月份报告。
     * <p>
     * 遍历 [startMonth, endMonth] 区间内的每个月份，逐个调用 triggerParse。
     * 月份列表由调用方提供或自动生成，每个月份会构造对应的 reportKey 和 minioPath。
     *
     * @param sourceType     数据来源
     * @param startMonth     起始月份（含）
     * @param endMonth       结束月份（含）
     * @param monthList      预生成的月份列表，格式 yyyy-MM（如 ["2024-01", "2024-02"]）
     * @param minioPathFn    月份到 MinIO 路径的映射函数（lambda），参数 (year, month) 返回路径
     */
    @Async("parseTaskExecutor")
    public void asyncBatchParse(String sourceType, YearMonth startMonth, YearMonth endMonth,
                                 List<String> monthList,
                                 java.util.function.BiFunction<Integer, Integer, String> minioPathFn) {
        List<YearMonth> months;
        if (monthList != null && !monthList.isEmpty()) {
            months = new ArrayList<>();
            for (String ym : monthList) {
                months.add(YearMonth.parse(ym));
            }
        } else {
            months = new ArrayList<>();
            YearMonth current = startMonth;
            while (!current.isAfter(endMonth)) {
                months.add(current);
                current = current.plusMonths(1);
            }
        }

        log.info("异步批量解析开始: sourceType={}, 月份数={}, 范围={} ~ {}",
                sourceType, months.size(),
                months.get(0), months.get(months.size() - 1));

        for (YearMonth month : months) {
            int year = month.getYear();
            int monthValue = month.getMonthValue();
            String reportKey = String.format("%s-%04d-%02d", sourceType.toUpperCase(), year, monthValue);
            LocalDate reportDate = month.atDay(1);
            String minioPath = minioPathFn.apply(year, monthValue);

            try {
                triggerParse(sourceType, reportKey, minioPath, reportDate);
            } catch (Exception e) {
                log.error("单月解析失败，继续下一个月: sourceType={}, month={}", sourceType, month, e);
                // 继续下一个月，不中断整个批次
            }
        }

        log.info("异步批量解析完成: sourceType={}, 月份数={}", sourceType, months.size());
    }

    // ==================== 查询方法 ====================

    /**
     * 查询单条解析记录。
     */
    public ParseRecord getRecord(String sourceType, String reportKey) {
        return parseRecordMapper.selectOne(
                new LambdaQueryWrapper<ParseRecord>()
                        .eq(ParseRecord::getSourceType, sourceType)
                        .eq(ParseRecord::getReportKey, reportKey)
        );
    }

    /**
     * 分页查询解析记录列表。
     *
     * @param page       页码（从 1 开始）
     * @param size       每页大小
     * @param sourceType 数据来源（可选，为空则不筛选）
     * @param status     解析状态（可选，为空则不筛选）
     * @return 分页结果
     */
    public Page<ParseRecord> getRecords(int page, int size, String sourceType, String status) {
        Page<ParseRecord> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<ParseRecord> wrapper = new LambdaQueryWrapper<>();

        if (sourceType != null && !sourceType.isEmpty()) {
            wrapper.eq(ParseRecord::getSourceType, sourceType);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ParseRecord::getStatus, status);
        }

        wrapper.orderByDesc(ParseRecord::getCreatedAt);
        return parseRecordMapper.selectPage(pageInfo, wrapper);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 从 MinIO 读取文件，带阶梯重试。
     */
    private byte[] readFromMinioWithRetry(String minioPath) {
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioBucket)
                            .object(minioPath)
                            .build())) {
                return stream.readAllBytes();
            } catch (Exception e) {
                lastException = e;
                if (attempt < maxRetryAttempts) {
                    long delay = baseDelayMs * attempt; // 1s, 2s, 3s
                    log.warn("MinIO 读取失败(尝试 {}/{}), {}ms 后重试: {}",
                            attempt, maxRetryAttempts, delay, e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("MinIO 读取被中断", ie);
                    }
                }
            }
        }

        throw new RuntimeException("MinIO 读取失败，已重试 " + maxRetryAttempts + " 次",
                lastException);
    }

    /**
     * 截断字符串到指定长度。
     */
    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() <= maxLength ? str : str.substring(0, maxLength);
    }

    /**
     * 获取异常的堆栈跟踪字符串。
     */
    private String getStackTrace(Exception e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.getClass().getName()).append(": ").append(e.getMessage()).append("\n");
        for (StackTraceElement element : e.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
            if (sb.length() > 2000) {
                sb.append("\t...");
                break;
            }
        }
        return sb.toString();
    }
}
