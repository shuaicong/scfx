package com.scfx.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.ParseRecord;
import com.scfx.enums.ParseStatusEnum;
import com.scfx.enums.SourceTypeEnum;
import com.scfx.service.ReportParseService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

@Slf4j
@RestController
@RequestMapping("/parse")
@RequiredArgsConstructor
public class ParseController {

    private final ReportParseService parseService;
    private final StringRedisTemplate redisTemplate;

    // ==================== 请求体 DTOs ====================

    @Data
    public static class ParseTriggerRequest {
        private String sourceType;
        private String reportKey;
        private String minioPath;
        private LocalDate reportDate;
    }

    @Data
    public static class ParseManualRequest {
        private String sourceType;
        private String reportKey;
        private String minioPath;
    }

    @Data
    public static class ParseBatchRequest {
        private String sourceType;
        private LocalDate startDate;
        private LocalDate endDate;
    }

    // ==================== 1. 采集器自动触发 ====================

    /**
     * 采集器自动触发解析。
     * 请求体含 sourceType、reportKey、minioPath、reportDate。
     */
    @PostMapping("/trigger")
    public Result<Void> trigger(@RequestBody ParseTriggerRequest req) {
        // 校验 sourceType 枚举合法性
        if (req.getSourceType() == null || !SourceTypeEnum.isValid(req.getSourceType())) {
            return Result.error(400, "无效的 sourceType: " + req.getSourceType());
        }
        // 校验非空
        if (req.getReportKey() == null || req.getReportKey().isBlank()) {
            return Result.error(400, "reportKey 不能为空");
        }
        if (req.getMinioPath() == null || req.getMinioPath().isBlank()) {
            return Result.error(400, "minioPath 不能为空");
        }
        if (req.getReportDate() == null) {
            return Result.error(400, "reportDate 不能为空");
        }

        try {
            parseService.triggerParse(
                    req.getSourceType(),
                    req.getReportKey(),
                    req.getMinioPath(),
                    req.getReportDate());
            return Result.success();
        } catch (Exception e) {
            log.error("触发解析失败: sourceType={}, reportKey={}", req.getSourceType(), req.getReportKey(), e);
            return Result.error("触发解析失败: " + e.getMessage());
        }
    }

    // ==================== 2. 手工重试 ====================

    /**
     * 手工重试解析（仅允许已失败的解析记录）。
     * 先查询现有记录，校验 status 为 "failed" 后才允许重试。
     */
    @PostMapping("/manual")
    public Result<Void> manual(@RequestBody ParseManualRequest req) {
        // 校验 sourceType
        if (req.getSourceType() == null || !SourceTypeEnum.isValid(req.getSourceType())) {
            return Result.error(400, "无效的 sourceType: " + req.getSourceType());
        }
        if (req.getReportKey() == null || req.getReportKey().isBlank()) {
            return Result.error(400, "reportKey 不能为空");
        }
        if (req.getMinioPath() == null || req.getMinioPath().isBlank()) {
            return Result.error(400, "minioPath 不能为空");
        }

        // 查询是否存在记录，仅允许重试 failed 状态
        ParseRecord record = parseService.getRecord(req.getSourceType(), req.getReportKey());
        if (record == null) {
            return Result.error(400, "解析记录不存在");
        }
        if (!ParseStatusEnum.FAILED.getCode().equals(record.getStatus())) {
            return Result.error(400, "仅允许重试已失败的解析记录，当前状态: " + record.getStatus());
        }

        LocalDate reportDate = record.getReportDate() != null ? record.getReportDate() : LocalDate.now();
        try {
            parseService.triggerParse(
                    req.getSourceType(),
                    req.getReportKey(),
                    req.getMinioPath(),
                    reportDate);
            return Result.success();
        } catch (Exception e) {
            log.error("手工重试解析失败: sourceType={}, reportKey={}", req.getSourceType(), req.getReportKey(), e);
            return Result.error("手工重试解析失败: " + e.getMessage());
        }
    }

    // ==================== 3. 批量补数 ====================

    /**
     * 批量补数（异步）。
     * 限制 startDate ~ endDate 跨度不超过 90 天。
     * 使用 Redis 分布式锁 {@code lock:parse:batch:{sourceType}} 防止重复提交。
     */
    @PostMapping("/batch")
    public Result<String> batch(@RequestBody ParseBatchRequest req) {
        // 校验 sourceType
        if (req.getSourceType() == null || !SourceTypeEnum.isValid(req.getSourceType())) {
            return Result.error(400, "无效的 sourceType: " + req.getSourceType());
        }
        if (req.getStartDate() == null) {
            return Result.error(400, "startDate 不能为空");
        }
        if (req.getEndDate() == null) {
            return Result.error(400, "endDate 不能为空");
        }
        if (req.getStartDate().isAfter(req.getEndDate())) {
            return Result.error(400, "startDate 不能晚于 endDate");
        }

        // 90 天时间跨度校验
        long daysBetween = ChronoUnit.DAYS.between(req.getStartDate(), req.getEndDate());
        if (daysBetween > 90) {
            return Result.error(400, "时间跨度不能超过 90 天，当前: " + daysBetween + " 天");
        }

        // Redis 分布式锁，防止重复触发
        String lockKey = "lock:parse:batch:" + req.getSourceType();
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(300));
        if (Boolean.FALSE.equals(locked)) {
            return Result.error("批量补数任务已存在，请稍后重试");
        }

        try {
            YearMonth startMonth = YearMonth.from(req.getStartDate());
            YearMonth endMonth = YearMonth.from(req.getEndDate());

            // 构造月份列表（yyyy-MM 格式）
            List<String> monthList = new ArrayList<>();
            YearMonth current = startMonth;
            while (!current.isAfter(endMonth)) {
                monthList.add(current.toString());
                current = current.plusMonths(1);
            }

            // MinIO 路径生成函数：{sourceType}/{year}/{month}.xml
            BiFunction<Integer, Integer, String> minioPathFn = (year, month) ->
                    req.getSourceType() + "/" + year + "/" + String.format("%02d", month) + ".xml";

            parseService.asyncBatchParse(req.getSourceType(), startMonth, endMonth, monthList, minioPathFn);
            log.info("批量解析任务已提交: sourceType={}, 月份数={}, 范围={} ~ {}",
                    req.getSourceType(), monthList.size(), startMonth, endMonth);

            return Result.success("任务已提交");
        } catch (Exception e) {
            log.error("提交批量解析任务失败: sourceType={}", req.getSourceType(), e);
            return Result.error("提交批量解析任务失败: " + e.getMessage());
        }
    }

    // ==================== 4. 解析记录列表 ====================

    /**
     * 分页查询解析记录列表。
     * 支持按 sourceType、status 过滤。
     */
    @GetMapping("/records")
    public Result<Page<ParseRecord>> records(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String status) {
        // 分页参数有效性钳制
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 200) size = 200;

        // 校验 status 枚举
        if (status != null && !status.isEmpty() && !ParseStatusEnum.isValid(status)) {
            return Result.error(400, "无效的 status: " + status);
        }
        // 校验 sourceType 枚举
        if (sourceType != null && !sourceType.isEmpty() && !SourceTypeEnum.isValid(sourceType)) {
            return Result.error(400, "无效的 sourceType: " + sourceType);
        }

        Page<ParseRecord> result = parseService.getRecords(page, size, sourceType, status);
        return Result.success(result);
    }
}
