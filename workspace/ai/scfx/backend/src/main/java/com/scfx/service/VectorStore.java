package com.scfx.service;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 向量存储抽象接口
 * 支持 MySQL / Qdrant 等多种实现，业务代码不直接依赖存储实现
 */
public interface VectorStore {

    /** 保存或更新向量 */
    void saveVector(Long knowledgeId, float[] vector);

    /** 更新向量状态 */
    void updateStatus(Long knowledgeId, String status);

    /** 删除向量 */
    void deleteVector(Long knowledgeId);

    /** 获取单条向量（含元数据） */
    VectorEntry getByKnowledgeId(Long knowledgeId);

    /** 获取分类下所有向量（用于 PCA 降维） */
    List<VectorEntry> getByCategoryId(Long categoryId);

    /** 批量获取向量（用于相似度计算） */
    Map<Long, float[]> getVectorMapByKnowledgeIds(Collection<Long> knowledgeIds);

    /** 获取分类下向量映射（knowledgeId → vector，用于 PCA） */
    Map<Long, float[]> getVectorMapByCategoryId(Long categoryId);

    /** 存储实现名称：MySQL / Qdrant */
    String name();

    /** 存储是否可用 */
    boolean isAvailable();

    @Data
    @AllArgsConstructor
    class VectorEntry {
        private Long id;            // t_knowledge_viz.id
        private Long knowledgeId;
        private float[] vector;
        private String status;
    }
}
