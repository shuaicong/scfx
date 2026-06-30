package com.scfx.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.scfx.entity.Report;
import com.scfx.entity.ReportVersion;

import java.util.List;

public interface ReportService extends IService<Report> {
    Report createFromTemplate(Long templateId, String title);
    ReportVersion saveVersion(Long reportId, String title, String richContent, String editorJson, String changeSummary);
    List<ReportVersion> getVersions(Long reportId);
    ReportVersion restoreVersion(Long reportId, Integer versionNumber);
    IPage<Report> search(int page, int size, String variety, String status, String keyword);
}
