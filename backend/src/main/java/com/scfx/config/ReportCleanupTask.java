package com.scfx.config;

import com.scfx.entity.ReportVersion;
import com.scfx.entity.ReportTemplateVersion;
import com.scfx.mapper.ReportVersionMapper;
import com.scfx.mapper.ReportTemplateVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReportCleanupTask {

    private final ReportVersionMapper versionMapper;
    private final ReportTemplateVersionMapper templateVersionMapper;

    private static final int MAX_VERSIONS = 50;

    /**
     * 每日凌晨2:00清理超出版本限制的旧版本
     * 保留每个 report_id / template_id 最新的 50 条
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanOldVersions() {
        cleanReportVersions();
        cleanTemplateVersions();
    }

    private void cleanReportVersions() {
        List<Long> reportIds = versionMapper.selectDistinctReportIds();
        int totalDeleted = 0;

        for (Long reportId : reportIds) {
            List<ReportVersion> allVersions = versionMapper.selectByReportId(reportId);
            if (allVersions.size() <= MAX_VERSIONS) continue;

            List<ReportVersion> toDelete = allVersions.stream()
                .sorted(Comparator.comparingInt(ReportVersion::getVersionNumber))
                .limit(allVersions.size() - MAX_VERSIONS)
                .collect(Collectors.toList());

            for (ReportVersion v : toDelete) {
                versionMapper.deleteById(v.getId());
            }
            totalDeleted += toDelete.size();
            log.info("已清理报告 {} 的 {} 个旧版本", reportId, toDelete.size());
        }

        if (totalDeleted > 0) {
            log.info("报告版本清理完成：共删除 {} 个旧版本", totalDeleted);
        }
    }

    private void cleanTemplateVersions() {
        List<Long> templateIds = templateVersionMapper.selectDistinctTemplateIds();
        int totalDeleted = 0;

        for (Long templateId : templateIds) {
            List<ReportTemplateVersion> allVersions = templateVersionMapper.selectByTemplateId(templateId);
            if (allVersions.size() <= MAX_VERSIONS) continue;

            List<ReportTemplateVersion> toDelete = allVersions.stream()
                .sorted(Comparator.comparingInt(ReportTemplateVersion::getVersionNumber))
                .limit(allVersions.size() - MAX_VERSIONS)
                .collect(Collectors.toList());

            for (ReportTemplateVersion v : toDelete) {
                templateVersionMapper.deleteById(v.getId());
            }
            totalDeleted += toDelete.size();
            log.info("已清理模板 {} 的 {} 个旧版本", templateId, toDelete.size());
        }

        if (totalDeleted > 0) {
            log.info("模板版本清理完成：共删除 {} 个旧版本", totalDeleted);
        }
    }
}
