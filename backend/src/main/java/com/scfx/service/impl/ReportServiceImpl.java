package com.scfx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scfx.entity.Report;
import com.scfx.entity.ReportTemplate;
import com.scfx.entity.ReportTemplateVersion;
import com.scfx.entity.ReportVersion;
import com.scfx.mapper.ReportMapper;
import com.scfx.mapper.ReportTemplateMapper;
import com.scfx.mapper.ReportTemplateVersionMapper;
import com.scfx.mapper.ReportVersionMapper;
import com.scfx.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl extends ServiceImpl<ReportMapper, Report> implements ReportService {

    private final ReportMapper reportMapper;
    private final ReportVersionMapper versionMapper;
    private final ReportTemplateMapper templateMapper;
    private final ReportTemplateVersionMapper templateVersionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Report createFromTemplate(Long templateId, String title) {
        // 1. 加载模板
        ReportTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new IllegalArgumentException("模板不存在: " + templateId);
        }

        // 2. 从模板版本中获取最新 HTML
        ReportTemplateVersion latestVersion = templateVersionMapper.selectOne(
            new LambdaQueryWrapper<ReportTemplateVersion>()
                .eq(ReportTemplateVersion::getTemplateId, templateId)
                .orderByDesc(ReportTemplateVersion::getVersionNumber)
                .last("LIMIT 1")
        );
        String richContent = latestVersion != null ? latestVersion.getRichContent() : "";

        // 3. 创建研报
        Report report = new Report();
        report.setTitle(title);
        report.setTemplateId(templateId);
        report.setVariety(template.getVariety());
        report.setReportType(template.getReportType());
        report.setStatus("draft");
        report.setGenerationStatus("none");
        report.setCurrentVersion(0);
        report.setRichContent(richContent);

        reportMapper.insert(report);
        log.info("研报已从模板创建: reportId={}, templateId={}, title={}", report.getId(), templateId, title);
        return report;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportVersion saveVersion(Long reportId, String title, String richContent, String editorJson, String changeSummary) {
        // 1. 查询当前最大版本号
        ReportVersion latest = versionMapper.selectOne(
            new LambdaQueryWrapper<ReportVersion>()
                .eq(ReportVersion::getReportId, reportId)
                .orderByDesc(ReportVersion::getVersionNumber)
                .last("LIMIT 1")
        );
        int nextVersion = (latest != null ? latest.getVersionNumber() : 0) + 1;

        // 2. 创建版本记录
        ReportVersion version = new ReportVersion();
        version.setReportId(reportId);
        version.setVersionNumber(nextVersion);
        version.setTitle(title);
        version.setRichContent(richContent);
        version.setEditorJson(editorJson);
        version.setChangeSummary(changeSummary);
        versionMapper.insert(version);

        // 3. 更新报告主表
        Report report = reportMapper.selectById(reportId);
        if (report != null) {
            report.setCurrentVersion(nextVersion);
            report.setRichContent(richContent);
            reportMapper.updateById(report);
        }

        log.info("研报版本已保存: reportId={}, version={}", reportId, nextVersion);
        return version;
    }

    @Override
    public List<ReportVersion> getVersions(Long reportId) {
        return versionMapper.selectList(
            new LambdaQueryWrapper<ReportVersion>()
                .eq(ReportVersion::getReportId, reportId)
                .orderByDesc(ReportVersion::getVersionNumber)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReportVersion restoreVersion(Long reportId, Integer versionNumber) {
        // 1. 读取历史版本
        ReportVersion oldVersion = versionMapper.selectOne(
            new LambdaQueryWrapper<ReportVersion>()
                .eq(ReportVersion::getReportId, reportId)
                .eq(ReportVersion::getVersionNumber, versionNumber)
        );
        if (oldVersion == null) {
            throw new IllegalArgumentException("版本不存在: reportId=" + reportId + ", version=" + versionNumber);
        }

        // 2. 保存为新版本（回滚操作视为一次新版本）
        Report report = reportMapper.selectById(reportId);
        String changeSummary = "回滚到版本 v" + versionNumber;
        return saveVersion(reportId, report.getTitle(), oldVersion.getRichContent(),
            oldVersion.getEditorJson(), changeSummary);
    }

    @Override
    public IPage<Report> search(int page, int size, String variety, String status, String keyword) {
        Page<Report> pageParam = new Page<>(page, size);
        return reportMapper.selectPage(pageParam, new LambdaQueryWrapper<Report>()
            .eq(Report::getDeleted, 0)
            .eq(variety != null && !variety.isEmpty(), Report::getVariety, variety)
            .eq(status != null && !status.isEmpty(), Report::getStatus, status)
            .like(keyword != null && !keyword.isEmpty(), Report::getTitle, keyword)
            .orderByDesc(Report::getCreatedAt)
        );
    }
}
