package com.scfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scfx.entity.Report;
import com.scfx.entity.ReportVersion;
import com.scfx.mapper.ReportMapper;
import com.scfx.mapper.ReportVersionMapper;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportExportService {

    private final ReportMapper reportMapper;
    private final ReportVersionMapper versionMapper;
    private final HtmlSanitizer htmlSanitizer;

    @Value("${gotenberg.endpoint:http://localhost:3001}")
    private String gotenbergEndpoint;

    @Value("${minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    @Value("${minio.access-key:admin}")
    private String minioAccessKey;

    @Value("${minio.secret-key:password}")
    private String minioSecretKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public Map<String, String> export(Long reportId, Integer versionNumber) {
        try {
            // 1. 获取 HTML 内容
            Report report = reportMapper.selectById(reportId);
            if (report == null) {
                throw new IllegalArgumentException("报告不存在: " + reportId);
            }

            String html;
            if (versionNumber != null) {
                ReportVersion version = versionMapper.selectOne(
                    new LambdaQueryWrapper<ReportVersion>()
                        .eq(ReportVersion::getReportId, reportId)
                        .eq(ReportVersion::getVersionNumber, versionNumber)
                );
                html = version != null ? version.getRichContent() : report.getRichContent();
            } else {
                html = report.getRichContent();
            }

            String variety = report.getVariety();

            // 2. 清洗 HTML
            html = htmlSanitizer.sanitize(html);

            // 3. 生成文件名
            String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String fileName = String.format("%d_v%d_%s_%s", reportId,
                versionNumber != null ? versionNumber : report.getCurrentVersion(),
                variety != null ? variety : "report", dateStr);

            // 4. 调用 Gotenberg 转换 DOCX
            String docxPath = sendToGotenberg(html, fileName, "docx");

            // 5. 调用 Gotenberg 转换 PDF
            String pdfPath = sendToGotenberg(html, fileName, "pdf");

            // 6. 更新报告导出路径
            report.setExportDocxPath(docxPath);
            report.setExportPdfPath(pdfPath);
            reportMapper.updateById(report);

            log.info("报告导出完成: reportId={}, docx={}, pdf={}", reportId, docxPath, pdfPath);
            return Map.of("docx", docxPath, "pdf", pdfPath);
        } catch (Exception e) {
            log.error("报告导出失败: reportId={}, error={}", reportId, e.getMessage());
            throw new RuntimeException("导出失败: " + e.getMessage());
        }
    }

    private String sendToGotenberg(String html, String fileName, String format) throws Exception {
        // 构建 Gotenberg multipart 请求
        String boundary = "----" + UUID.randomUUID().toString();
        String body = "--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"files\"; filename=\"index.html\"\r\n"
            + "Content-Type: text/html\r\n\r\n"
            + html + "\r\n"
            + "--" + boundary + "--\r\n";

        String endpoint = format.equals("pdf")
            ? gotenbergEndpoint + "/forms/chromium/convert/html"
            : gotenbergEndpoint + "/forms/libreoffice/convert";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(60))
            .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Gotenberg 返回 " + response.statusCode());
        }

        // 上传到 MinIO
        String ext = format.equals("pdf") ? "pdf" : "docx";
        String objectName = "reports/" + LocalDate.now().getYear() + "/"
            + LocalDate.now().getMonthValue() + "/" + fileName + "." + ext;

        MinioClient minioClient = MinioClient.builder()
            .endpoint(minioEndpoint)
            .credentials(minioAccessKey, minioSecretKey)
            .build();

        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket("reports")
                .object(objectName)
                .stream(new ByteArrayInputStream(response.body()), response.body().length, -1)
                .contentType(format.equals("pdf")
                    ? "application/pdf"
                    : "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .build()
        );

        log.info("文件已上传到 MinIO: bucket=reports, object={}", objectName);
        return objectName;
    }
}
