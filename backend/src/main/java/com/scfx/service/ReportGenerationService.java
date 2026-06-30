package com.scfx.service;

import com.scfx.entity.Report;
import com.scfx.mapper.PriceMapper;
import com.scfx.mapper.ReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportGenerationService {

    private final ReportService reportService;
    private final ReportMapper reportMapper;
    private final PriceMapper priceMapper;

    @Async("reportExecutor")
    public void generate(Long reportId) {
        try {
            // 1. 加载报告
            Report report = reportService.getById(reportId);
            if (report == null) {
                log.warn("报告不存在: {}", reportId);
                return;
            }

            // 2. 更新状态为生成中
            report.setGenerationStatus("generating");
            reportService.updateById(report);

            // 3. 获取模板配置（从报告的 template_id 解析）
            // 暂跳过实际 AI 生成，后续迭代实现占位符解析和数据填充

            // 4. 生成占位 HTML
            String todayHtml = String.format(
                "<h2>自动生成报告</h2><p>报告ID: %d</p><p>生成时间: %s</p><p>数据来源：粮达网 t_price</p>",
                reportId, java.time.LocalDateTime.now().toString()
            );

            // 5. 保存为版本 1
            reportService.saveVersion(reportId, report.getTitle(), todayHtml, null, "AI 自动生成");

            // 6. 更新状态
            report.setGenerationStatus("completed");
            reportService.updateById(report);

            log.info("报告生成完成: reportId={}", reportId);

        } catch (Exception e) {
            log.error("报告生成失败: reportId={}, error={}", reportId, e.getMessage());
            Report report = reportService.getById(reportId);
            if (report != null) {
                report.setGenerationStatus("failed");
                reportService.updateById(report);
            }
        }
    }
}
