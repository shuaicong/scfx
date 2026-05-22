package com.scfx.service.impl;

import com.scfx.entity.CollectorScriptVersion;
import com.scfx.mapper.CollectorScriptVersionMapper;
import com.scfx.service.CollectorScriptVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CollectorScriptVersionServiceImpl implements CollectorScriptVersionService {
    private final CollectorScriptVersionMapper scriptVersionMapper;

    @Value("${collector.script.path:#{systemProperties['user.home']}/workspace/ai/scfx/python-collector-sdk/collectorsdk/collectors}")
    private String scriptBasePath;

    private void ensureDirectoryExists() {
        File dir = new File(scriptBasePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    @Override
    public List<CollectorScriptVersion> getVersions(String datasourceCode) {
        return scriptVersionMapper.findByDatasourceCode(datasourceCode);
    }

    @Override
    public CollectorScriptVersion getCurrentVersion(String datasourceCode) {
        return scriptVersionMapper.findCurrentByDatasourceCode(datasourceCode);
    }

    @Override
    public CollectorScriptVersion getVersionById(Long id) {
        return scriptVersionMapper.selectById(id);
    }

    @Override
    @Transactional
    public CollectorScriptVersion createVersion(String datasourceCode, String filePath, String fileMd5, int fileSize, String operator) {
        // 标记旧版本为非当前
        CollectorScriptVersion old = scriptVersionMapper.findCurrentByDatasourceCode(datasourceCode);
        if (old != null) {
            old.setIsCurrent(0);
            scriptVersionMapper.updateById(old);
        }

        // 获取最新版本号
        Integer maxVersion = scriptVersionMapper.findMaxVersion(datasourceCode);
        int newVersion = (maxVersion == null) ? 1 : maxVersion + 1;

        // 创建新版本
        CollectorScriptVersion version = new CollectorScriptVersion();
        version.setDatasourceCode(datasourceCode);
        version.setVersion(newVersion);
        version.setFilePath(filePath);
        version.setFileMd5(fileMd5);
        version.setFileSize(fileSize);
        version.setIsCurrent(1);
        version.setCreatedBy(operator);
        scriptVersionMapper.insert(version);

        return version;
    }

    @Override
    @Transactional
    public CollectorScriptVersion uploadScript(String datasourceCode, byte[] content, String originalFilename, String operator) {
        // 验证文件类型
        if (!originalFilename.endsWith(".py")) {
            throw new RuntimeException("只能上传 .py 文件");
        }

        // 验证文件大小 (100KB)
        if (content.length > 100 * 1024) {
            throw new RuntimeException("文件不能超过 100KB");
        }

        // 计算 MD5
        String md5 = calculateMd5(content);

        // 检查是否有相同 MD5 的最新版本
        CollectorScriptVersion current = scriptVersionMapper.findCurrentByDatasourceCode(datasourceCode);
        if (current != null && md5.equals(current.getFileMd5())) {
            return null; // 返回 null 让 controller 处理
        }

        // 保存文件
        ensureDirectoryExists();
        String filePath = scriptBasePath + "/" + datasourceCode + ".py";
        Path path = Path.of(filePath);

        // 备份旧文件（如果存在）
        if (Files.exists(path)) {
            String backupPath = filePath + ".bak." + System.currentTimeMillis();
            try {
                Files.copy(path, Path.of(backupPath));
            } catch (java.io.IOException e) {
                throw new RuntimeException("备份文件失败", e);
            }
        }

        // 写入新文件
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(content);
        } catch (java.io.IOException e) {
            throw new RuntimeException("写入文件失败", e);
        }

        // 创建版本记录
        return createVersion(datasourceCode, filePath, md5, content.length, operator);
    }

    @Override
    public String getScriptContent(String datasourceCode, int version) {
        String filePath;
        if (version > 0) {
            CollectorScriptVersion v = scriptVersionMapper.findByDatasourceCodeAndVersion(datasourceCode, version);
            filePath = v != null ? v.getFilePath() : null;
        } else {
            CollectorScriptVersion current = scriptVersionMapper.findCurrentByDatasourceCode(datasourceCode);
            filePath = current != null ? current.getFilePath() : null;
        }

        if (filePath == null) {
            throw new RuntimeException("脚本版本不存在");
        }

        try {
            return Files.readString(Path.of(filePath));
        } catch (java.io.IOException e) {
            throw new RuntimeException("读取文件失败", e);
        }
    }

    @Override
    public boolean scriptExists(String datasourceCode) {
        String filePath = scriptBasePath + "/" + datasourceCode + ".py";
        return Files.exists(Path.of(filePath));
    }

    @Override
    @Transactional
    public void rollback(String datasourceCode, int version) {
        CollectorScriptVersion target = scriptVersionMapper.findByDatasourceCodeAndVersion(datasourceCode, version);
        if (target == null) {
            throw new RuntimeException("版本不存在");
        }

        // 备份当前版本
        CollectorScriptVersion current = scriptVersionMapper.findCurrentByDatasourceCode(datasourceCode);
        if (current != null) {
            current.setIsCurrent(0);
            scriptVersionMapper.updateById(current);
        }

        // 复制目标版本文件到当前版本路径
        String currentPath = scriptBasePath + "/" + datasourceCode + ".py";
        try {
            Files.copy(Path.of(target.getFilePath()), Path.of(currentPath));
        } catch (java.io.IOException e) {
            throw new RuntimeException("复制文件失败", e);
        }

        // 创建新的版本记录
        createVersion(datasourceCode, currentPath, target.getFileMd5(), target.getFileSize(), "system");
    }

    private String calculateMd5(byte[] content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5计算失败", e);
        }
    }
}