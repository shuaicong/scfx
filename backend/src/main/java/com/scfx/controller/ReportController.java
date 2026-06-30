package com.scfx.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.scfx.common.Result;
import com.scfx.entity.*;
import com.scfx.mapper.*;
import com.scfx.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final ReportGenerationService generationService;
    private final ReportExportService exportService;
    private final ReportMapper reportMapper;
    private final ReportVersionMapper versionMapper;
    private final ReportTemplateMapper templateMapper;
    private final ReportTemplateVersionMapper templateVersionMapper;
    private final HtmlSanitizer htmlSanitizer;
    private final TemplateConfigValidator configValidator;

    // ── Report CRUD ──

    @GetMapping
    public Result<IPage<Report>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) String variety,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.success(reportService.search(page, size, variety, status, keyword));
    }

    @PostMapping
    public Result<Report> create(@RequestBody Map<String, Object> body) {
        Long templateId = body.get("templateId") != null ? Long.parseLong(body.get("templateId").toString()) : null;
        String title = (String) body.get("title");
        if (title == null || title.isEmpty()) {
            return Result.error("标题不能为空");
        }
        Report report = reportService.createFromTemplate(templateId, title);
        return Result.success(report);
    }

    @GetMapping("/{id}")
    public Result<Report> get(@PathVariable Long id) {
        Report report = reportService.getById(id);
        if (report == null) return Result.error("报告不存在");
        return Result.success(report);
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Report report) {
        report.setId(id);
        reportService.updateById(report);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reportService.removeById(id);
        return Result.success();
    }

    // ── Generation ──

    @PostMapping("/{id}/generate")
    public Result<Void> generate(@PathVariable Long id) {
        Report report = reportService.getById(id);
        if (report == null) return Result.error("报告不存在");
        if ("generating".equals(report.getGenerationStatus())) {
            return Result.error("报告正在生成中");
        }
        generationService.generate(id);
        return Result.success();
    }

    @GetMapping("/{id}/generation-status")
    public Result<Map<String, Object>> generationStatus(@PathVariable Long id) {
        Report report = reportService.getById(id);
        if (report == null) return Result.error("报告不存在");
        return Result.success(Map.of(
            "status", report.getGenerationStatus(),
            "generationStatus", report.getGenerationStatus()
        ));
    }

    // ── Version ──

    @PostMapping("/{id}/save")
    public Result<ReportVersion> save(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        String richContent = (String) body.get("richContent");
        String editorJson = (String) body.get("editorJson");
        String changeSummary = (String) body.get("changeSummary");

        // Sanitize HTML before saving
        if (richContent != null) {
            richContent = htmlSanitizer.sanitize(richContent);
        }

        ReportVersion version = reportService.saveVersion(id, title, richContent, editorJson, changeSummary);
        return Result.success(version);
    }

    @GetMapping("/{id}/versions")
    public Result<List<ReportVersion>> versions(@PathVariable Long id) {
        return Result.success(reportService.getVersions(id));
    }

    @PostMapping("/{id}/versions/{v}/restore")
    public Result<ReportVersion> restore(@PathVariable Long id, @PathVariable("v") Integer versionNumber) {
        return Result.success(reportService.restoreVersion(id, versionNumber));
    }

    // ── Export ──

    @PostMapping("/{id}/export")
    public Result<Map<String, String>> export(
            @PathVariable Long id,
            @RequestParam(required = false) Integer version) {
        Map<String, String> result = exportService.export(id, version);
        return Result.success(result);
    }

    @GetMapping("/{id}/download")
    public Result<Map<String, String>> download(@PathVariable Long id, @RequestParam String type) {
        Report report = reportService.getById(id);
        if (report == null) return Result.error("报告不存在");
        String path = "docx".equals(type) ? report.getExportDocxPath() : report.getExportPdfPath();
        if (path == null) return Result.error("导出文件不存在");
        // Return the path — frontend can construct the download URL
        return Result.success(Map.of("path", path, "type", type));
    }

    // ── Template ──

    @GetMapping("/templates")
    public Result<List<ReportTemplate>> templateList() {
        List<ReportTemplate> list = templateMapper.selectList(null);
        return Result.success(list);
    }

    @PostMapping("/templates")
    public Result<ReportTemplate> createTemplate(@RequestBody ReportTemplate template) {
        templateMapper.insert(template);
        return Result.success(template);
    }

    @GetMapping("/templates/{id}")
    public Result<ReportTemplate> getTemplate(@PathVariable Long id) {
        ReportTemplate tpl = templateMapper.selectById(id);
        if (tpl == null) return Result.error("模板不存在");
        return Result.success(tpl);
    }

    @PutMapping("/templates/{id}")
    public Result<Void> updateTemplate(@PathVariable Long id, @RequestBody ReportTemplate template) {
        template.setId(id);
        templateMapper.updateById(template);
        return Result.success();
    }

    @DeleteMapping("/templates/{id}")
    public Result<Void> deleteTemplate(@PathVariable Long id) {
        templateMapper.deleteById(id);
        return Result.success();
    }

    @PostMapping("/templates/{id}/save")
    public Result<ReportTemplateVersion> saveTemplateVersion(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        String name = (String) body.get("name");
        String richContent = (String) body.get("richContent");
        String editorJson = (String) body.get("editorJson");

        if (richContent != null) {
            richContent = htmlSanitizer.sanitize(richContent);
        }

        // Validate config vs placeholders
        ReportTemplate tpl = templateMapper.selectById(id);
        if (tpl != null && tpl.getGenerationConfig() != null) {
            configValidator.validate(richContent, tpl.getGenerationConfig());
        }

        // Get next version number
        Integer maxVersion = templateVersionMapper.selectMaxVersion(id);
        int nextVersion = (maxVersion != null ? maxVersion : 0) + 1;

        ReportTemplateVersion version = new ReportTemplateVersion();
        version.setTemplateId(id);
        version.setVersionNumber(nextVersion);
        version.setName(name);
        version.setRichContent(richContent);
        version.setEditorJson(editorJson);
        templateVersionMapper.insert(version);

        // Update template's current_version
        if (tpl != null) {
            tpl.setCurrentVersion(nextVersion);
            templateMapper.updateById(tpl);
        }

        return Result.success(version);
    }

    @GetMapping("/templates/{id}/versions")
    public Result<List<ReportTemplateVersion>> templateVersions(@PathVariable Long id) {
        List<ReportTemplateVersion> list = templateVersionMapper.selectByTemplateId(id);
        return Result.success(list);
    }

    @PostMapping("/templates/{id}/versions/{v}/restore")
    public Result<ReportTemplateVersion> restoreTemplate(
            @PathVariable Long id, @PathVariable("v") Integer versionNumber) {
        ReportTemplateVersion oldVersion = templateVersionMapper.selectOne(
            new LambdaQueryWrapper<ReportTemplateVersion>()
                .eq(ReportTemplateVersion::getTemplateId, id)
                .eq(ReportTemplateVersion::getVersionNumber, versionNumber)
        );
        if (oldVersion == null) return Result.error("版本不存在");

        // Create new version with old content
        Integer maxVersion = templateVersionMapper.selectMaxVersion(id);
        int nextVersion = (maxVersion != null ? maxVersion : 0) + 1;

        ReportTemplateVersion newVersion = new ReportTemplateVersion();
        newVersion.setTemplateId(id);
        newVersion.setVersionNumber(nextVersion);
        newVersion.setName(oldVersion.getName());
        newVersion.setRichContent(oldVersion.getRichContent());
        newVersion.setEditorJson(oldVersion.getEditorJson());
        newVersion.setChangeSummary("回滚到版本 " + versionNumber);
        templateVersionMapper.insert(newVersion);

        ReportTemplate tpl = templateMapper.selectById(id);
        if (tpl != null) {
            tpl.setCurrentVersion(nextVersion);
            templateMapper.updateById(tpl);
        }

        return Result.success(newVersion);
    }

    // ── Image Upload ──

    @PostMapping("/upload-image")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            // Simple: save path placeholder — actual MinIO upload will be added when images are used
            String fileName = file.getOriginalFilename();
            return Result.success(Map.of("url", "/uploads/" + fileName, "filename", fileName));
        } catch (Exception e) {
            return Result.error("上传失败: " + e.getMessage());
        }
    }
}
