package com.scfx.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.Report;
import com.scfx.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 报告控制器
 */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @GetMapping
    public Result<Page<Report>> getReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String variety,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return reportService.getReports(page, size, variety, reportType, startDate, endDate);
    }

    @GetMapping("/{id}")
    public Result<Report> getReport(@PathVariable Long id) {
        return reportService.getReportById(id);
    }

    @GetMapping("/recent")
    public Result<?> getRecentReports(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(reportService.getRecentReports(limit));
    }
}
