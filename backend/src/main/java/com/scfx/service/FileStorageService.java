package com.scfx.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 文件存储服务
 * 将上传文件保存到磁盘，返回文件路径
 */
@Service
public class FileStorageService {

    @Value("${app.upload.dir:data/upload}")
    private String uploadDir;

    /**
     * 存储上传文件
     * @param file       上传的文件
     * @param categoryId 所属分类 ID
     * @param knowledgeId 知识库记录 ID
     * @return 文件存储路径
     * @throws IOException 目录创建或文件写入失败
     */
    public String store(MultipartFile file, Long categoryId, Long knowledgeId) throws IOException {
        String dir = uploadDir + "/" + categoryId;
        Path dirPath = Path.of(dir);
        Files.createDirectories(dirPath);
        String fileName = knowledgeId + ".docx";
        Path targetPath = dirPath.resolve(fileName);
        file.transferTo(targetPath);
        return targetPath.toString();
    }

    /**
     * 根据路径加载文件
     * @param relativePath 文件路径（相对 uploadDir 或绝对路径）
     * @return File 对象
     */
    public File load(String relativePath) {
        Path path = Path.of(relativePath);
        if (path.isAbsolute()) {
            return path.toFile();
        }
        return Path.of(uploadDir, relativePath).toFile();
    }
}
