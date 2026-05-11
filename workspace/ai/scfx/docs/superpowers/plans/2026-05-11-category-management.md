# 知识库分类管理实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现知识库分类管理功能，支持多级分类、软删除、回收站、批量操作、颜色主题、描述备注、实时同步、置顶、预览、批量创建、自动补全、统计面板、操作日志、导入导出、重名检测、快捷切换、容量预警、排序切换、最近访问、复制备份、订阅通知、分类内搜索、权限管理、知识批量移动、数据对比、命名校验

**Architecture:**
- 后端：Spring Boot + MySQL 提供分类 CRUD API，AI-QA Service 提供知识-分类关联接口
- 前端：Vue 3 + Composition API，调用后端 API 实现分类管理 UI
- 数据模型：`t_category`（分类表）+ `t_knowledge_category`（关联表）

**Tech Stack:** Spring Boot, MySQL, Vue 3, TypeScript, RestTemplate

---

## 任务总览

| 任务 | 范围 |
|------|------|
| Task 1 | 数据库：创建分类表、关联表、日志表 |
| Task 2 | Spring Boot：分类 CRUD API（含回收站、批量创建、预览） |
| Task 3 | Spring Boot：知识-分类关联 API |
| Task 4 | Spring Boot：统计面板、操作日志、导入导出 API |
| Task 5 | 前端 API 层：category.ts + knowledge-category.ts |
| Task 6 | 前端基础：分类树组件 + CRUD |
| Task 7 | 前端功能：搜索、拖拽排序、右键菜单 |
| Task 8 | 前端功能：批量操作、回收站、合并 |
| Task 9 | 前端增强：颜色选择器、描述 tooltip |
| Task 10 | 前端增强：层级深度显示、Undo、实时同步 |
| Task 11 | 前端增强：置顶、预览、批量创建、自动补全 |
| Task 12 | 前端增强：统计面板、操作日志、导入导出、重名检测 |
| Task 13 | 前端增强：容量预警、排序切换、最近访问、复制备份、订阅通知 |
| Task 14 | 前端增强：分类内搜索、权限管理、知识批量移动、数据对比、命名校验 |
| Task 15 | 前端集成：Knowledge.vue 完整集成 |

---

## Task 1: 数据库建表

**Files:**
- Modify: `backend/src/main/resources/schema.sql`

- [ ] **Step 1: 添加分类表和关联表 DDL**

```sql
-- 分类表
CREATE TABLE IF NOT EXISTS t_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    icon VARCHAR(50) DEFAULT '📁',
    color VARCHAR(20) DEFAULT NULL COMMENT '分类主题颜色',
    description VARCHAR(500) DEFAULT NULL COMMENT '分类描述/备注',
    parent_id BIGINT DEFAULT NULL COMMENT '父分类ID，NULL表示顶级',
    sort_order INT DEFAULT 0 COMMENT '排序序号，数字越小越靠前',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME DEFAULT NULL COMMENT '软删除时间，NULL表示未删除',
    version BIGINT DEFAULT 0 COMMENT '版本号，用于实时同步检测',
    FOREIGN KEY (parent_id) REFERENCES t_category(id) ON DELETE SET NULL,
    INDEX idx_category_parent (parent_id),
    INDEX idx_category_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库分类表';

-- 知识-分类关联表
CREATE TABLE IF NOT EXISTS t_knowledge_category (
    knowledge_id BIGINT NOT NULL COMMENT '知识条目ID',
    category_id BIGINT NOT NULL COMMENT '分类ID',
    PRIMARY KEY (knowledge_id, category_id),
    INDEX idx_knowledge_category_kid (knowledge_id),
    INDEX idx_knowledge_category_cid (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识-分类关联表';

-- 初始化数据：粮信网顶级分类
INSERT INTO t_category (name, icon, color, sort_order) VALUES
('粮信网', '🌐', '#58A6FF', 1),
('我的钢铁', '🏭', '#3FB950', 2),
('中华粮网', '🌾', '#F0883E', 3);
```

- [ ] **Step 2: 验证表创建成功**

Run: `mysql -u root -p -e "USE grain_platform; DESCRIBE t_category; DESCRIBE t_knowledge_category;"`
Expected: 显示表结构

- [ ] **Step 3: 提交**

```bash
git add backend/src/main/resources/schema.sql
git commit -m "feat(db): add t_category and t_knowledge_category tables"
```

---

## Task 2: Spring Boot 分类 CRUD API

**Files:**
- Create: `backend/src/main/java/com/scfx/entity/Category.java`
- Create: `backend/src/main/java/com/scfx/mapper/CategoryMapper.java`
- Create: `backend/src/main/java/com/scfx/service/CategoryService.java`
- Create: `backend/src/main/java/com/scfx/controller/CategoryController.java`
- Modify: `backend/src/main/resources/application.yml`（添加路由前缀配置）

- [ ] **Step 1: 创建 Category 实体类**

```java
package com.scfx.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Category {
    private Long id;
    private String name;
    private String icon;
    private String color;
    private String description;
    private Long parentId;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private Long version;
}
```

- [ ] **Step 2: 创建 CategoryMapper**

```java
package com.scfx.mapper;

import com.scfx.entity.Category;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface CategoryMapper {
    @Select("SELECT * FROM t_category WHERE deleted_at IS NULL ORDER BY sort_order")
    List<Category> findAll();

    @Select("SELECT * FROM t_category WHERE deleted_at IS NULL AND parent_id IS NULL ORDER BY sort_order")
    List<Category> findRoots();

    @Select("SELECT * FROM t_category WHERE deleted_at IS NULL AND parent_id = #{parentId} ORDER BY sort_order")
    List<Category> findByParentId(Long parentId);

    @Select("SELECT * FROM t_category WHERE id = #{id}")
    Category findById(Long id);

    @Insert("INSERT INTO t_category (name, icon, color, description, parent_id, sort_order, version) " +
            "VALUES (#{name}, #{icon}, #{color}, #{description}, #{parentId}, #{sortOrder}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Category category);

    @Update("UPDATE t_category SET name=#{name}, icon=#{icon}, color=#{color}, description=#{description}, " +
            "parent_id=#{parentId}, sort_order=#{sortOrder}, version=version+1, updated_at=NOW() WHERE id=#{id}")
    int update(Category category);

    @Update("UPDATE t_category SET deleted_at=NOW(), version=version+1 WHERE id=#{id}")
    int softDelete(Long id);

    @Update("UPDATE t_category SET deleted_at=NULL, version=version+1, updated_at=NOW() WHERE id=#{id}")
    int restore(Long id);

    @Delete("DELETE FROM t_category WHERE id=#{id}")
    int permanentDelete(Long id);

    @Select("SELECT MAX(version) FROM t_category")
    Long getMaxVersion();

    @Select("SELECT COUNT(*) FROM t_category WHERE deleted_at IS NOT NULL")
    int countInTrash();
}
```

- [ ] **Step 3: 创建 CategoryService**

```java
package com.scfx.service;

import com.scfx.entity.Category;
import com.scfx.mapper.CategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {
    private final CategoryMapper mapper;

    public List<Category> getTree() {
        List<Category> all = mapper.findAll();
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
        return category;
    }

    public Category update(Long id, Category category) {
        category.setId(id);
        mapper.update(category);
        return mapper.findById(id);
    }

    public void delete(Long id) {
        // 软删除
        mapper.softDelete(id);
        // 递归软删除子分类
        List<Category> children = mapper.findByParentId(id);
        for (Category child : children) {
            delete(child.getId());
        }
    }

    public void restore(Long id) {
        mapper.restore(id);
        // 递归恢复子分类
        List<Category> children = mapper.findByParentId(id);
        for (Category child : children) {
            restore(child.getId());
        }
    }

    public void permanentDelete(Long id) {
        // 先删子分类
        List<Category> children = mapper.findByParentId(id);
        for (Category child : children) {
            permanentDelete(child.getId());
        }
        mapper.permanentDelete(id);
    }

    public Long getVersion() {
        Long maxVersion = mapper.getMaxVersion();
        return maxVersion == null ? 0L : maxVersion;
    }

    public List<Category> getTrash() {
        return mapper.findAll().stream()
            .filter(c -> c.getDeletedAt() != null)
            .collect(Collectors.toList());
    }
}
```

- [ ] **Step 4: 创建 CategoryController**

```java
package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.entity.Category;
import com.scfx.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/category")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService service;

    @GetMapping("/tree")
    public Result<?> tree() {
        return Result.success(Map.of(
            "data", service.getTree(),
            "version", service.getVersion()
        ));
    }

    @GetMapping("/version")
    public Result<?> version() {
        return Result.success(Map.of("version", service.getVersion()));
    }

    @GetMapping("/{id}")
    public Result<?> getById(@PathVariable Long id) {
        return Result.success(service.getById(id));
    }

    @PostMapping
    public Result<?> create(@RequestBody Category category) {
        return Result.success(service.create(category));
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody Category category) {
        return Result.success(service.update(id, category));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.success(Map.of("deleted", true));
    }

    @PostMapping("/{id}/restore")
    public Result<?> restore(@PathVariable Long id) {
        service.restore(id);
        return Result.success(Map.of("restored", true));
    }

    @DeleteMapping("/{id}/permanent")
    public Result<?> permanentDelete(@PathVariable Long id) {
        service.permanentDelete(id);
        return Result.success(Map.of("permanentDeleted", true));
    }

    @GetMapping("/trash")
    public Result<?> trash() {
        return Result.success(service.getTrash());
    }
}
```

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/scfx/entity/Category.java
git add backend/src/main/java/com/scfx/mapper/CategoryMapper.java
git add backend/src/main/java/com/scfx/service/CategoryService.java
git add backend/src/main/java/com/scfx/controller/CategoryController.java
git commit -m "feat(backend): add category CRUD API with soft delete"
```

---

## Task 3: Spring Boot 知识-分类关联 API

**Files:**
- Modify: `backend/src/main/java/com/scfx/mapper/KnowledgeCategoryMapper.java`（新建）
- Modify: `backend/src/main/java/com/scfx/service/KnowledgeCategoryService.java`（新建）
- Modify: `backend/src/main/java/com/scfx/controller/KnowledgeCategoryController.java`（新建）
- Modify: `backend/src/main/java/com/scfx/controller/KnowledgeBaseController.java`

- [ ] **Step 1: 创建 KnowledgeCategoryMapper**

```java
package com.scfx.mapper;

import org.apache.ibatis.annotations.*;

@Mapper
public interface KnowledgeCategoryMapper {
    @Insert("<script>" +
        "INSERT INTO t_knowledge_category (knowledge_id, category_id) VALUES " +
        "<foreach collection='categoryIds' item='cid' separator=','>(#{knowledgeId}, #{cid})</foreach>" +
        "</script>")
    int insertBatch(@Param("knowledgeId") Long knowledgeId, @Param("categoryIds") List<Long> categoryIds);

    @Delete("DELETE FROM t_knowledge_category WHERE knowledge_id=#{knowledgeId} AND category_id=#{categoryId}")
    int delete(@Param("knowledgeId") Long knowledgeId, @Param("categoryId") Long categoryId);

    @Delete("DELETE FROM t_knowledge_category WHERE knowledge_id=#{knowledgeId}")
    int deleteAllByKnowledgeId(Long knowledgeId);

    @Select("SELECT category_id FROM t_knowledge_category WHERE knowledge_id=#{knowledgeId}")
    List<Long> findCategoryIdsByKnowledgeId(Long knowledgeId);

    @Select("SELECT knowledge_id FROM t_knowledge_category WHERE category_id=#{categoryId}")
    List<Long> findKnowledgeIdsByCategoryId(Long categoryId);

    @Select("SELECT COUNT(*) FROM t_knowledge_category WHERE category_id=#{categoryId}")
    int countByCategoryId(Long categoryId);

    @Select("SELECT k.id FROM t_knowledge_base k LEFT JOIN t_knowledge_category kc ON k.id = kc.knowledge_id " +
            "WHERE kc.knowledge_id IS NULL GROUP BY k.id")
    List<Long> findUncategorizedKnowledgeIds();
}
```

- [ ] **Step 2: 创建 KnowledgeCategoryService**

```java
package com.scfx.service;

import com.scfx.mapper.KnowledgeCategoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class KnowledgeCategoryService {
    private final KnowledgeCategoryMapper mapper;

    public void assign(Long knowledgeId, List<Long> categoryIds) {
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
}
```

- [ ] **Step 3: 创建 KnowledgeCategoryController**

```java
package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.service.KnowledgeCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeCategoryController {
    private final KnowledgeCategoryService service;

    @GetMapping("/uncategorized")
    public Result<?> getUncategorized() {
        return Result.success(service.getUncategorizedKnowledgeIds());
    }

    @PostMapping("/{id}/categories")
    public Result<?> assign(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        service.assign(id, body.get("categoryIds"));
        return Result.success(Map.of("assigned", true));
    }

    @DeleteMapping("/{id}/categories/{categoryId}")
    public Result<?> remove(@PathVariable Long id, @PathVariable Long categoryId) {
        service.remove(id, categoryId);
        return Result.success(Map.of("removed", true));
    }

    @PutMapping("/{id}/categories/replace")
    public Result<?> replace(@PathVariable Long id, @RequestBody Map<String, List<Long>> body) {
        service.assign(id, body.get("categoryIds"));
        return Result.success(Map.of("replaced", true));
    }

    @GetMapping("/{id}/categories")
    public Result<?> getCategories(@PathVariable Long id) {
        return Result.success(service.getCategoryIds(id));
    }
}
```

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/java/com/scfx/mapper/KnowledgeCategoryMapper.java
git add backend/src/main/java/com/scfx/service/KnowledgeCategoryService.java
git add backend/src/main/java/com/scfx/controller/KnowledgeCategoryController.java
git commit -m "feat(backend): add knowledge-category association API"
```

---

## Task 4: 前端 API 层

**Files:**
- Create: `frontend/src/api/category.ts`
- Create: `frontend/src/api/knowledge-category.ts`

- [ ] **Step 1: 创建 category.ts**

```typescript
import request from './index'

export interface Category {
  id: number
  name: string
  icon: string
  color?: string
  description?: string
  parentId: number | null
  sortOrder: number
  knowledgeCount: number
  children: Category[]
  version?: number
}

export const categoryApi = {
  tree: () => request.get<{ data: Category[]; version: number }>('/category/tree'),
  version: () => request.get<{ version: number }>('/category/version'),
  search: (name: string) => request.get<Category[]>('/category/search', { params: { name } }),
  trash: () => request.get<Category[]>('/category/trash'),
  getById: (id: number) => request.get<Category>(`/category/${id}`),
  create: (data: Partial<Category>) => request.post<Category>('/category', data),
  update: (id: number, data: Partial<Category>) => request.put<Category>(`/category/${id}`, data),
  delete: (id: number) => request.delete<void>(`/category/${id}`),
  restore: (id: number) => request.post<void>(`/category/${id}/restore`, {}),
  permanentDelete: (id: number) => request.delete<void>(`/category/${id}/permanent`),
}
```

- [ ] **Step 2: 创建 knowledge-category.ts**

```typescript
import request from './index'

export const knowledgeCategoryApi = {
  getCategories: (knowledgeId: number) =>
    request.get<number[]>(`/knowledge/${knowledgeId}/categories`),

  assign: (knowledgeId: number, categoryIds: number[]) =>
    request.post(`/knowledge/${knowledgeId}/categories`, { categoryIds }),

  remove: (knowledgeId: number, categoryId: number) =>
    request.delete(`/knowledge/${knowledgeId}/categories/${categoryId}`),

  replace: (knowledgeId: number, categoryIds: number[]) =>
    request.put(`/knowledge/${knowledgeId}/categories/replace`, { categoryIds }),

  getUncategorized: () => request.get<number[]>('/knowledge/uncategorized'),
}
```

- [ ] **Step 3: 提交**

```bash
git add frontend/src/api/category.ts
git add frontend/src/api/knowledge-category.ts
git commit -m "feat(frontend): add category and knowledge-category API layer"
```

---

## Task 5: 前端分类树组件 + CRUD

**Files:**
- Create: `frontend/src/components/CategoryTree.vue`
- Modify: `frontend/src/views/knowledge/Knowledge.vue`

- [ ] **Step 1: 创建 CategoryTree.vue**

```vue
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { categoryApi, type Category } from '@/api/category'

const props = defineProps<{
  selectedId?: number
}>()

const emit = defineEmits<{
  (e: 'select', category: Category): void
}>()

const folders = ref<Category[]>([])
const expandedIds = ref<Set<number>>(new Set())
const loading = ref(false)

const loadTree = async () => {
  loading.value = true
  try {
    const res = await categoryApi.tree()
    folders.value = res.data.data
  } finally {
    loading.value = false
  }
}

const toggleExpand = (id: number) => {
  if (expandedIds.value.has(id)) {
    expandedIds.value.delete(id)
  } else {
    expandedIds.value.add(id)
  }
  saveExpandedState()
}

const getDepthPath = (category: Category, all: Category[]): string => {
  // 计算序号路径，如 "1.2.3"
  // 实现遍历父链
  return ''
}

const getIndent = (depth: number) => depth * 16

loadTree()
</script>

<template>
  <div class="category-tree">
    <div v-for="folder in folders" :key="folder.id" class="folder-item">
      <div
        class="folder-row"
        :style="{ paddingLeft: getIndent(depth) + 'px' }"
        @click="emit('select', folder)"
      >
        <span
          v-if="folder.children?.length"
          class="folder-toggle"
          @click.stop="toggleExpand(folder.id)"
        >
          ▶
        </span>
        <span v-else class="folder-toggle-placeholder"></span>
        <span class="folder-icon">{{ folder.icon }}</span>
        <span
          v-if="folder.color"
          class="folder-color"
          :style="{ backgroundColor: folder.color }"
        ></span>
        <span class="folder-name">{{ folder.name }}</span>
      </div>
      <template v-if="folder.children?.length && expandedIds.has(folder.id)">
        <div
          v-for="child in folder.children"
          :key="child.id"
          class="folder-item sub-item"
        >
          <!-- 递归渲染 -->
        </div>
      </template>
    </div>
  </div>
</template>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/src/components/CategoryTree.vue
git commit -m "feat(frontend): add CategoryTree component"
```

---

## Task 6-10: 后续任务**

后续任务包括搜索、拖拽排序、右键菜单、批量操作、颜色选择器、描述 tooltip、层级深度显示、Undo、实时同步、Knowledge.vue 完整集成等功能。

**确认计划后开始执行。**

---

## 自检清单

- [ ] 每个 Task 都有明确的文件列表
- [ ] 每个 Step 都有实际的代码
- [ ] 没有 placeholder（TODO、TBD）
- [ ] 类型、方法名、字段名在前后面保持一致
- [ ] 按 implementation order 排列任务