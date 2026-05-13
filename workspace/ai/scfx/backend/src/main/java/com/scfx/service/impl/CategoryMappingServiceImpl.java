package com.scfx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scfx.entity.CategoryMapping;
import com.scfx.entity.KnowledgeBase;
import com.scfx.mapper.CategoryMappingMapper;
import com.scfx.mapper.KnowledgeBaseMapper;
import com.scfx.service.CategoryMappingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryMappingServiceImpl implements CategoryMappingService {

    private final CategoryMappingMapper mappingMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    // 默认分类ID（未分类）
    private static final Long DEFAULT_CATEGORY_ID = 1L;

    @Override
    public Long map(String sourceType, String variety, String reportType) {
        // 1. 优先精确匹配（三个字段都匹配）
        CategoryMapping exact = findMapping(sourceType, variety, reportType);
        if (exact != null) return exact.getCategoryId();

        // 2. 匹配来源+品种
        CategoryMapping sourceVariety = findMapping(sourceType, variety, null);
        if (sourceVariety != null) return sourceVariety.getCategoryId();

        // 3. 只匹配来源
        CategoryMapping sourceOnly = findMapping(sourceType, null, null);
        if (sourceOnly != null) return sourceOnly.getCategoryId();

        // 4. 匹配通配符规则（variety=* 或 reportType=*）
        CategoryMapping wildcard = findWildcardMapping(sourceType);
        if (wildcard != null) return wildcard.getCategoryId();

        // 5. 返回默认分类
        return DEFAULT_CATEGORY_ID;
    }

    private CategoryMapping findMapping(String source, String variety, String reportType) {
        LambdaQueryWrapper<CategoryMapping> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryMapping::getSourceType, source)
               .eq(variety != null, CategoryMapping::getVariety, variety)
               .eq(reportType != null, CategoryMapping::getReportType, reportType)
               .eq(CategoryMapping::getEnabled, 1)
               .orderByDesc(CategoryMapping::getPriority)
               .last("LIMIT 1");
        return mappingMapper.selectOne(wrapper);
    }

    private CategoryMapping findWildcardMapping(String source) {
        // 查找 enabled 且 variety 或 report_type 为空的规则
        LambdaQueryWrapper<CategoryMapping> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryMapping::getSourceType, source)
               .eq(CategoryMapping::getEnabled, 1)
               .and(w -> w.isNull(CategoryMapping::getVariety)
                       .or()
                       .isNull(CategoryMapping::getReportType))
               .orderByDesc(CategoryMapping::getPriority)
               .last("LIMIT 1");
        return mappingMapper.selectOne(wrapper);
    }

    @Override
    public List<CategoryMapping> list() {
        return mappingMapper.selectList(
            new LambdaQueryWrapper<CategoryMapping>()
                .orderByDesc(CategoryMapping::getPriority)
                .orderByDesc(CategoryMapping::getCreatedAt)
        );
    }

    @Override
    public void save(CategoryMapping mapping) {
        if (mapping.getPriority() == null) {
            mapping.setPriority(5);  // 默认优先级 5
        }
        mapping.setEnabled(1);
        mappingMapper.insert(mapping);
    }

    @Override
    public void updateById(CategoryMapping mapping) {
        mappingMapper.updateById(mapping);
    }

    @Override
    public void removeById(Long id) {
        mappingMapper.deleteById(id);
    }

    @Override
    public Long preview(String source, String variety, String reportType) {
        return map(source, variety, reportType);
    }

    @Override
    public List<CategoryMapping> getByCategoryId(Long categoryId) {
        LambdaQueryWrapper<CategoryMapping> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CategoryMapping::getCategoryId, categoryId);
        return mappingMapper.selectList(wrapper);
    }

    @Override
    public Map<String, Object> getCategoryDependency(Long categoryId) {
        Map<String, Object> result = new HashMap<>();

        // 获取引用该分类的映射规则
        List<CategoryMapping> mappings = getByCategoryId(categoryId);
        result.put("mappingCount", mappings.size());
        result.put("mappings", mappings);

        // 获取该分类下的知识数量
        LambdaQueryWrapper<KnowledgeBase> kbWrapper = new LambdaQueryWrapper<>();
        kbWrapper.eq(KnowledgeBase::getCategoryId, categoryId);
        Long knowledgeCount = knowledgeBaseMapper.selectCount(kbWrapper);
        result.put("knowledgeCount", knowledgeCount);

        return result;
    }
}