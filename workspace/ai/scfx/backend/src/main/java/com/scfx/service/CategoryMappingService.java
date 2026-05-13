package com.scfx.service;

import com.scfx.entity.CategoryMapping;
import java.util.List;
import java.util.Map;

public interface CategoryMappingService {

    /**
     * 根据来源、品种、报告类型映射到分类ID
     * 匹配规则：完全匹配优先 > 部分匹配 > 通配符 > 默认分类
     */
    Long map(String sourceType, String variety, String reportType);

    /**
     * 获取所有映射规则
     */
    List<CategoryMapping> list();

    /**
     * 创建映射规则
     */
    void save(CategoryMapping mapping);

    /**
     * 更新映射规则
     */
    void updateById(CategoryMapping mapping);

    /**
     * 删除映射规则
     */
    void removeById(Long id);

    /**
     * 测试映射预览
     */
    Long preview(String source, String variety, String reportType);

    /**
     * 获取引用某个分类的所有映射规则
     */
    List<CategoryMapping> getByCategoryId(Long categoryId);

    /**
     * 获取分类的引用统计
     */
    Map<String, Object> getCategoryDependency(Long categoryId);
}