package com.scfx.service;

import com.scfx.entity.Category;
import com.scfx.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryMapper mapper;

    @Transactional
    public List<Category> getTree() {
        List<Category> all = mapper.findAll();
        // 填充 knowledgeCount
        for (Category c : all) {
            c.setKnowledgeCount(mapper.countKnowledgeByCategoryId(c.getId()));
        }
        return buildTree(all, null);
    }

    private List<Category> buildTree(List<Category> all, Long parentId) {
        return all.stream()
            .filter(c -> (parentId == null && c.getParentId() == null) ||
                         (parentId != null && parentId.equals(c.getParentId())))
            .peek(c -> c.setChildren(buildTree(all, c.getId())))
            .collect(Collectors.toList());
    }

    public Category getById(Long id) {
        return mapper.findById(id);
    }

    public Category create(Category category) {
        mapper.insert(category);
        return mapper.findById(category.getId());
    }

    @Transactional
    public Category update(Long id, Category category, String operator) {
        category.setId(id);
        category.setLastOperatedBy(operator);
        mapper.update(category);
        return mapper.findById(id);
    }

    @Transactional
    public void delete(Long id, String operator) {
        mapper.softDelete(id, operator);
        // 递归软删除子分类
        List<Long> childIds = mapper.findChildIds(id);
        for (Long childId : childIds) {
            delete(childId, operator);
        }
    }

    @Transactional
    public void restore(Long id, String operator) {
        mapper.restore(id, operator);
        List<Long> childIds = mapper.findChildIds(id);
        for (Long childId : childIds) {
            restore(childId, operator);
        }
    }

    @Transactional
    public void permanentDelete(Long id) {
        List<Long> childIds = mapper.findChildIds(id);
        for (Long childId : childIds) {
            permanentDelete(childId);
        }
        mapper.permanentDelete(id);
    }

    public Long getVersion() {
        Long maxVersion = mapper.getMaxVersion();
        return maxVersion == null ? 0L : maxVersion;
    }

    public List<Category> getTrash() {
        return mapper.findDeleted();
    }

    public List<Category> search(String name) {
        return mapper.searchByName(name);
    }

    public List<Category> getPreview(Long categoryId) {
        Category category = mapper.findById(categoryId);
        return category != null ? List.of(category) : List.of();
    }
}