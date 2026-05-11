package com.scfx.service;

import com.scfx.mapper.KnowledgeCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KnowledgeCategoryService {
    private final KnowledgeCategoryMapper mapper;

    @Transactional
    public void assign(Long knowledgeId, List<Long> categoryIds, String movedBy) {
        List<Long> oldCategoryIds = mapper.findCategoryIdsByKnowledgeId(knowledgeId);
        // 记录移动历史
        for (Long oldCatId : oldCategoryIds) {
            if (categoryIds == null || !categoryIds.contains(oldCatId)) {
                // 从旧分类移除
            }
        }
        mapper.deleteAllByKnowledgeId(knowledgeId);
        if (categoryIds != null && !categoryIds.isEmpty()) {
            mapper.insertBatch(knowledgeId, categoryIds);
        }
    }

    public void remove(Long knowledgeId, Long categoryId) {
        mapper.delete(knowledgeId, categoryId);
    }

    public List<Long> getCategoryIds(Long knowledgeId) {
        return mapper.findCategoryIdsByKnowledgeId(knowledgeId);
    }

    public int getKnowledgeCount(Long categoryId) {
        return mapper.countByCategoryId(categoryId);
    }

    public List<Long> getUncategorizedKnowledgeIds() {
        return mapper.findUncategorizedKnowledgeIds();
    }

    public List<Map<String, Object>> getMoveHistory(Long knowledgeId) {
        return mapper.findMoveHistory(knowledgeId);
    }
}