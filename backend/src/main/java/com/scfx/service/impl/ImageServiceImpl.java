package com.scfx.service.impl;

import com.scfx.entity.KnowledgeImage;
import com.scfx.mapper.KnowledgeImageMapper;
import com.scfx.service.ImageService;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private final KnowledgeImageMapper knowledgeImageMapper;

    @Value("${minio.endpoint:http://localhost:9000}")
    private String minioEndpoint;

    @Value("${minio.access-key:admin}")
    private String minioAccessKey;

    @Value("${minio.secret-key:password}")
    private String minioSecretKey;

    private MinioClient getMinioClient() {
        return MinioClient.builder()
            .endpoint(minioEndpoint)
            .credentials(minioAccessKey, minioSecretKey)
            .build();
    }

    @Override
    @Transactional
    public void saveImages(Long knowledgeId, List<KnowledgeImage> images) {
        if (images == null || images.isEmpty()) return;
        for (KnowledgeImage img : images) {
            img.setKnowledgeId(knowledgeId);
            knowledgeImageMapper.insert(img);
        }
        log.info("知识库图片记录保存完成: knowledgeId={}, count={}", knowledgeId, images.size());
    }

    @Override
    @Transactional
    public void deleteByKnowledgeId(Long knowledgeId) {
        List<KnowledgeImage> images = knowledgeImageMapper.findByKnowledgeId(knowledgeId);
        if (images.isEmpty()) return;

        MinioClient client = getMinioClient();
        for (KnowledgeImage img : images) {
            try {
                client.removeObject(RemoveObjectArgs.builder()
                    .bucket(img.getMinioBucket())
                    .object(img.getMinioPath())
                    .build());
            } catch (Exception e) {
                log.warn("MinIO 删除图片失败: bucket={}, path={}, err={}",
                    img.getMinioBucket(), img.getMinioPath(), e.getMessage());
            }
        }

        knowledgeImageMapper.deleteByKnowledgeId(knowledgeId);
        log.info("知识库图片清理完成: knowledgeId={}, count={}", knowledgeId, images.size());
    }
}
