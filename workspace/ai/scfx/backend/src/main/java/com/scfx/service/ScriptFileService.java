package com.scfx.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 脚本文件管理服务
 * 负责脚本文件的物理存储和读取
 */
@Slf4j
@Service
public class ScriptFileService {

    @Value("${script.storage.path:${user.home}/scfx-scripts}")
    private String scriptStoragePath;

    private static final String SCRIPT_EXTENSION = ".py";

    /**
     * 初始化脚本存储目录
     */
    public void initStorageDir() {
        try {
            Path path = Paths.get(scriptStoragePath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
                log.info("创建脚本存储目录: {}", scriptStoragePath);
            }
        } catch (IOException e) {
            log.error("创建脚本存储目录失败", e);
            throw new RuntimeException("无法创建脚本存储目录", e);
        }
    }

    /**
     * 保存脚本内容到文件
     */
    public String saveScript(String scriptName, String content) {
        initStorageDir();
        String safeFileName = sanitizeFileName(scriptName);
        String fileName = safeFileName + SCRIPT_EXTENSION;
        Path filePath = Paths.get(scriptStoragePath, fileName);

        try {
            // 如果文件已存在，先备份
            if (Files.exists(filePath)) {
                String backupName = safeFileName + "_backup_" + System.currentTimeMillis() + SCRIPT_EXTENSION;
                Path backupPath = Paths.get(scriptStoragePath, backupName);
                Files.copy(filePath, backupPath);
                log.info("备份旧脚本: {} -> {}", fileName, backupName);
            }

            Files.writeString(filePath, content);
            log.info("保存脚本文件: {}", filePath);
            return filePath.toString();
        } catch (IOException e) {
            log.error("保存脚本文件失败: {}", fileName, e);
            throw new RuntimeException("保存脚本文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 上传脚本文件
     */
    public String uploadScript(MultipartFile file, String scriptName) {
        initStorageDir();
        String safeFileName = sanitizeFileName(scriptName);
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);

        if (!"py".equalsIgnoreCase(extension)) {
            throw new RuntimeException("只支持 Python 脚本文件 (.py)");
        }

        String fileName = safeFileName + SCRIPT_EXTENSION;
        Path filePath = Paths.get(scriptStoragePath, fileName);

        try {
            Files.write(filePath, file.getBytes());
            log.info("上传脚本文件: {}", filePath);
            return filePath.toString();
        } catch (IOException e) {
            log.error("上传脚本文件失败: {}", fileName, e);
            throw new RuntimeException("上传脚本文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读取脚本内容
     */
    public String readScript(String scriptPath) {
        try {
            Path path = Paths.get(scriptPath);
            if (!Files.exists(path)) {
                throw new RuntimeException("脚本文件不存在: " + scriptPath);
            }
            return Files.readString(path);
        } catch (IOException e) {
            log.error("读取脚本文件失败: {}", scriptPath, e);
            throw new RuntimeException("读取脚本文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新脚本内容
     */
    public String updateScript(String scriptPath, String content) {
        try {
            Path path = Paths.get(scriptPath);
            if (!Files.exists(path)) {
                throw new RuntimeException("脚本文件不存在: " + scriptPath);
            }
            Files.writeString(path, content);
            log.info("更新脚本文件: {}", scriptPath);
            return scriptPath;
        } catch (IOException e) {
            log.error("更新脚本文件失败: {}", scriptPath, e);
            throw new RuntimeException("更新脚本文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除脚本文件
     */
    public void deleteScript(String scriptPath) {
        try {
            Path path = Paths.get(scriptPath);
            if (!Files.exists(path)) {
                log.warn("脚本文件不存在，跳过删除: {}", scriptPath);
                return;
            }
            Files.delete(path);
            log.info("删除脚本文件: {}", scriptPath);
        } catch (IOException e) {
            log.error("删除脚本文件失败: {}", scriptPath, e);
            throw new RuntimeException("删除脚本文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取存储目录下的所有脚本文件
     */
    public List<String> listScripts() {
        initStorageDir();
        try {
            return Files.list(Paths.get(scriptStoragePath))
                    .filter(path -> path.toString().endsWith(SCRIPT_EXTENSION))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("列出脚本文件失败", e);
            throw new RuntimeException("列出脚本文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取脚本文件的绝对路径
     */
    public String getScriptPath(String scriptName) {
        initStorageDir();
        String safeFileName = sanitizeFileName(scriptName);
        return Paths.get(scriptStoragePath, safeFileName + SCRIPT_EXTENSION).toString();
    }

    /**
     * 检查脚本文件是否存在
     */
    public boolean scriptExists(String scriptPath) {
        return Files.exists(Paths.get(scriptPath));
    }

    /**
     * 生成安全的文件名
     */
    private String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            fileName = "untitled_script";
        }
        // 移除不安全字符
        return fileName.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fa5_-]", "_");
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
}
