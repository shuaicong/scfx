package com.scfx.service;

import com.scfx.entity.KnowledgeImage;
import java.util.List;

public interface ImageService {
    /** 保存知识库图片记录 */
    void saveImages(Long knowledgeId, List<KnowledgeImage> images);
    /** 删除知识库关联的所有图片（数据库记录 + MinIO 文件） */
    void deleteByKnowledgeId(Long knowledgeId);
}
