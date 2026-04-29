package com.scfx.service;

import cn.hutool.core.io.FileUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scfx.entity.Report;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 粮信网采集器服务
 * 注意：此类需要Playwright依赖，运行时需要安装浏览器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiangxinwangCollector {

    private final ReportService reportService;
    private final CollectionLogService logService;
    private final AlertService alertService;

    @Value("${app.collection.sources.liangxinwang.username:33022}")
    private String username;

    @Value("${app.collection.sources.liangxinwang.password:qlp707}")
    private String password;

    @Value("${app.collection.sources.liangxinwang.login-url:https://my.chinagrain.cn/jinnong/a/login}")
    private String loginUrl;

    @Value("${app.collection.sources.liangxinwang.report-url:https://www.chinagrain.cn/report/}")
    private String reportUrl;

    @Value("${app.collection.cookie-dir:data/sessions}")
    private String cookieDir;

    @Value("${app.collection.data-dir:data/raw}")
    private String dataDir;

    private static final String COOKIE_FILE = "data/sessions/liangxinwang_cookies.json";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 采集今天的玉米报告
     * 注意：完整实现需要Playwright浏览器自动化，当前为简化版本
     */
    public Map<String, Object> collectTodayCornReports() {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> collectedReports = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        logService.addLog(null, "粮信网采集器", "INFO", "开始采集粮信网玉米报告", "liangxinwang", null, null, null, null);
        result.put("startTime", LocalDateTime.now());

        try {
            // 由于当前环境没有完整的Playwright安装和浏览器，提供模拟数据用于测试
            logService.addLog(null, "粮信网采集器", "INFO", "注意: 当前运行在模拟模式", "liangxinwang", null, null, null, null);

            // 模拟采集结果
            Map<String, Object> mockReport = new HashMap<>();
            mockReport.put("title", "（" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日")) + "）玉米晨报");
            mockReport.put("variety", "玉米");
            mockReport.put("reportType", "晨报");
            mockReport.put("source", "粮信网");
            mockReport.put("publishTime", LocalDateTime.now().toString());
            mockReport.put("content", "【模拟数据】今日国内玉米价格震荡运行为主...");
            mockReport.put("url", "https://www.chinagrain.cn/report/mock");

            collectedReports.add(mockReport);

            // 保存到数据库（模拟）
            saveReportToDatabase(mockReport);

            result.put("successCount", 1);
            result.put("totalCount", 1);
            result.put("collectedReports", collectedReports);
            result.put("message", "模拟采集完成，实际采集需要完整的Playwright环境");
            result.put("endTime", LocalDateTime.now());

            logService.addLog(null, "粮信网采集器", "INFO", "采集完成，成功: 1", "liangxinwang", null, null, null, null);

        } catch (Exception e) {
            String errorMsg = "采集过程出错: " + e.getMessage();
            logService.addLog(null, "粮信网采集器", "ERROR", errorMsg, "liangxinwang", null, null, null, null);
            errors.add(errorMsg);
            result.put("errors", errors);
            alertService.createAlert("task_error", "error", "粮信网采集失败", errorMsg, null);
        }

        return result;
    }

    /**
     * 保存报告到数据库
     */
    private void saveReportToDatabase(Map<String, Object> data) {
        try {
            String url = (String) data.get("url");

            // 检查是否已存在
            if (reportService.existsByUrl(url)) {
                logService.addLog(null, "粮信网采集器", "INFO", "报告已存在，跳过: " + data.get("title"), "liangxinwang", null, null, null, null);
                return;
            }

            Report report = new Report();
            report.setTitle((String) data.get("title"));
            report.setOriginalUrl(url);
            report.setSource((String) data.getOrDefault("source", "粮信网"));
            report.setAuthor((String) data.getOrDefault("author", "粮信网"));
            report.setEditor((String) data.getOrDefault("editor", ""));
            report.setVariety((String) data.get("variety"));
            report.setReportType((String) data.get("reportType"));
            report.setContent((String) data.get("content"));

            String publishTime = (String) data.get("publishTime");
            if (publishTime != null) {
                try {
                    report.setPublishTime(LocalDateTime.parse(publishTime.substring(0, Math.min(19, publishTime.length()))));
                } catch (Exception e) {
                    report.setPublishTime(LocalDateTime.now());
                }
            }

            // 保存文件路径
            String dateStr = LocalDate.now().toString();
            String fileName = "report_" + System.currentTimeMillis() + ".json";
            report.setContentTextPath(dataDir + "/chinagrain/" + dateStr + "/text/" + fileName);
            report.setContentHtmlPath(dataDir + "/chinagrain/" + dateStr + "/html/" + fileName.replace(".json", ".html"));

            reportService.saveReport(report);
            logService.addLog(null, "粮信网采集器", "INFO", "报告已保存到数据库: " + report.getTitle(), "liangxinwang", null, null, null, null);

        } catch (Exception e) {
            logService.addLog(null, "粮信网采集器", "ERROR", "保存报告失败: " + e.getMessage(), "liangxinwang", null, null, null, null);
        }
    }

    /**
     * 提取报告类型
     */
    private String extractReportType(String title) {
        if (title.contains("晨报")) return "晨报";
        if (title.contains("日报")) return "日报";
        if (title.contains("周报")) return "周报";
        if (title.contains("月报")) return "月报";
        return "其他";
    }
}
