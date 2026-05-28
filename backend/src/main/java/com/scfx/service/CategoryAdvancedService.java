package com.scfx.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scfx.entity.Category;
import com.scfx.mapper.CategoryMapper;
import com.scfx.mapper.CategoryOperationLogMapper;
import com.scfx.mapper.CategoryStatsMapper;
import com.scfx.mapper.KnowledgeCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CategoryAdvancedService {
    private final CategoryMapper categoryMapper;
    private final CategoryOperationLogMapper operationLogMapper;
    private final CategoryStatsMapper statsMapper;
    private final KnowledgeCategoryMapper knowledgeCategoryMapper;

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("topCategories", statsMapper.findTopCategoriesByKnowledgeCount());
        stats.put("lowCategories", statsMapper.findCategoriesByKnowledgeCountAsc());
        stats.put("totalCategories", categoryMapper.findAll().size());
        return stats;
    }

    public List<Map<String, Object>> getOperationHistory(Long categoryId) {
        return operationLogMapper.findByCategoryId(categoryId);
    }

    public List<Map<String, Object>> getRecentOperations() {
        return operationLogMapper.findRecent();
    }

    @Transactional
    public void logOperation(Long categoryId, String operator, String operationType, String detail) {
        operationLogMapper.insert(categoryId, operator, operationType, detail);
    }

    public List<Map<String, Object>> getMergeSuggestions() {
        return statsMapper.findDuplicateNameCategories();
    }

    public String exportCategories() {
        List<Category> all = categoryMapper.findAll();
        List<Map<String, Object>> exportData = new ArrayList<>();
        for (Category c : all) {
            Map<String, Object> item = new HashMap<>();
            item.put("name", c.getName());
            item.put("icon", c.getIcon());
            item.put("color", c.getColor());
            item.put("description", c.getDescription());
            item.put("parentId", c.getParentId());
            item.put("sortOrder", c.getSortOrder());
            item.put("pinned", c.getPinned());
            exportData.add(item);
        }
        try {
            return new ObjectMapper().writeValueAsString(Map.of("categories", exportData));
        } catch (Exception e) {
            return "{}";
        }
    }

    @Transactional
    public int importCategories(String json, String operator) {
        // 简化实现，实际应解析 JSON 并批量导入
        return 0;
    }

    public List<Map<String, Object>> getHotAnalysis() {
        return statsMapper.findTopCategoriesByKnowledgeCount();
    }
}