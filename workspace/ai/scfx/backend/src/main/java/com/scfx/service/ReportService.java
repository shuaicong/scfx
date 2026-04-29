package com.scfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.Report;
import com.scfx.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 报告服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportMapper reportMapper;

    /**
     * 保存报告
     */
    public Result<Report> saveReport(Report report) {
        report.setCreatedAt(LocalDateTime.now());
        report.setUpdatedAt(LocalDateTime.now());
        reportMapper.insert(report);
        log.info("保存报告: {}", report.getTitle());
        return Result.success(report);
    }

    /**
     * 分页查询报告
     */
    public Result<Page<Report>> getReports(int page, int size, String variety, String reportType, String startDate, String endDate) {
        Page<Report> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();

        if (variety != null && !variety.isEmpty()) {
            wrapper.eq(Report::getVariety, variety);
        }
        if (reportType != null && !reportType.isEmpty()) {
            wrapper.eq(Report::getReportType, reportType);
        }
        if (startDate != null && !startDate.isEmpty()) {
            wrapper.ge(Report::getPublishTime, LocalDateTime.parse(startDate + " 00:00:00"));
        }
        if (endDate != null && !endDate.isEmpty()) {
            wrapper.le(Report::getPublishTime, LocalDateTime.parse(endDate + " 23:59:59"));
        }

        wrapper.orderByDesc(Report::getPublishTime);
        Page<Report> result = reportMapper.selectPage(pageInfo, wrapper);

        return Result.success(result);
    }

    /**
     * 根据ID获取报告
     */
    public Result<Report> getReportById(Long id) {
        Report report = reportMapper.selectById(id);
        if (report == null) {
            return Result.error("报告不存在");
        }
        return Result.success(report);
    }

    /**
     * 检查报告是否已存在（根据URL去重）
     */
    public boolean existsByUrl(String url) {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Report::getOriginalUrl, url);
        return reportMapper.selectCount(wrapper) > 0;
    }

    /**
     * 获取今日报告数
     */
    public long getTodayReportCount() {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Report::getCreatedAt, LocalDateTime.now().withHour(0).withMinute(0).withSecond(0));
        return reportMapper.selectCount(wrapper);
    }

    /**
     * 获取报告列表（不分页）
     */
    public List<Report> getRecentReports(int limit) {
        LambdaQueryWrapper<Report> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Report::getPublishTime);
        wrapper.last("LIMIT " + limit);
        return reportMapper.selectList(wrapper);
    }
}
