# 知识库分类管理设计

## Context

Knowledge.vue 页面侧边栏的分类功能目前使用前端硬编码的 mock 数据，没有后端接口支持。用户需要真实的分类管理能力：
- 多用户并发操作
- 所有用户共享分类
- 支持多级层级结构
- 用户可自由管理（增删改查）

## Architecture

```
前端(Vue) → Spring Boot(网关) → MySQL
                    ↑
              AI-QA Service(知识库核心)
```

- **分类 CRUD**: Spring Boot + MySQL
- **知识管理**: AI-QA Service + MySQL (已有)
- **知识-分类关联**: MySQL 中间表

## Data Model

### 分类表 `t_category`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| name | VARCHAR(100) | 分类名称 |
| icon | VARCHAR(50) | 图标，如"🌐" |
| parent_id | BIGINT | 父分类ID，NULL表示顶级 |
| sort_order | INT | 排序序号，数字越小越靠前 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
| deleted_at | DATETIME | 软删除时间，NULL表示未删除 |

### 知识-分类关联表 `t_knowledge_category`

| 字段 | 类型 | 说明 |
|------|------|------|
| knowledge_id | BIGINT | 知识条目ID |
| category_id | BIGINT | 分类ID |
| PRIMARY KEY | (knowledge_id, category_id) | 联合主键 |

## API Design

### 分类管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/category/tree | 获取分类树（层级结构） |
| GET | /api/category/trash | 获取回收站中的分类 |
| POST | /api/category | 新建分类 |
| PUT | /api/category/{id} | 更新分类（名称/图标/父级/排序） |
| DELETE | /api/category/{id} | 软删除分类（移入回收站） |
| POST | /api/category/{id}/restore | 从回收站恢复分类 |
| DELETE | /api/category/{id}/permanent | 永久删除分类 |
| POST | /api/category/{id}/merge | 合并分类到目标分类 |
| GET | /api/category/search | 搜索分类（?name=） |

### 知识-分类关联

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/knowledge | 按分类筛选知识（?categoryId=） |
| GET | /api/knowledge/uncategorized | 获取未分类的知识（无任何分类关联） |
| POST | /api/knowledge/{id}/categories | 知识分配到分类 |
| DELETE | /api/knowledge/{id}/categories/{categoryId} | 移除知识与分类的关联 |
| POST | /api/knowledge/batch/categories | 批量分配知识到分类 |
| PUT | /api/knowledge/{id}/categories/replace | 替换知识的所有分类 |

### Request/Response 格式

**GET /api/category/tree**

Response:
```json
{
  "code": 200,
  "data": [
    {
      "id": 1,
      "name": "粮信网",
      "icon": "🌐",
      "parentId": null,
      "sortOrder": 1,
      "knowledgeCount": 156,
      "children": [
        { "id": 11, "name": "玉米", "icon": "🌐", "parentId": 1, "sortOrder": 1, "knowledgeCount": 45, "children": [] },
        { "id": 12, "name": "小麦", "icon": "🌐", "parentId": 1, "sortOrder": 2, "knowledgeCount": 23, "children": [] }
      ]
    }
  ]
}
```

**POST /api/category**

Request:
```json
{ "name": "新建分类", "icon": "📁", "parentId": null, "sortOrder": 99 }
```

**PUT /api/category/{id}**

Request:
```json
{ "name": "新名称", "icon": "📂", "parentId": 1, "sortOrder": 5 }
```

**DELETE /api/category/{id}**

Response:
```json
{
  "code": 200,
  "data": {
    "deletedCategoryCount": 3,
    "affectedKnowledgeCount": 23,
    "orphanedKnowledgeCount": 5
  }
}
```

**POST /api/category/{id}/merge**

Request:
```json
{ "targetId": 10 }
```

合并后：
- 分类 A 的所有知识转移到分类 B
- 分类 A 的所有子分类移到分类 B 下
- 分类 A 被软删除

**GET /api/category/search?name=玉米**

Response: 名称包含"玉米"的分类列表

**GET /api/knowledge/uncategorized**

Response: 没有关联任何分类的知识列表

**POST /api/knowledge/batch/categories**

Request:
```json
{
  "knowledgeIds": [1, 2, 3, 4, 5],
  "categoryIds": [11, 12]
}
```

### 业务规则

1. **删除分类**：
   - 软删除：`deleted_at` 标记时间，不真正删除
   - 有子分类时：递归软删除所有子分类
   - 有知识关联时：知识保留，关联解除
   - 返回被影响的知识数量

2. **恢复分类**：
   - 从回收站恢复分类及其所有子分类
   - 恢复后关联关系需要重新建立（关联已在删除时解除）

3. **永久删除**：
   - 回收站中的分类可以永久删除
   - 永久删除后不可恢复

4. **移动分类**：
   - `parentId` 为 null 表示移到顶级
   - 不能将自己设为自己的子分类（需校验循环引用）

5. **合并分类**：
   - 源分类的知识全部关联到目标分类
   - 源分类的子分类移到目标分类下
   - 源分类软删除

6. **排序**：
   - `sortOrder` 数字越小排越前
   - 同父级的分类按 sortOrder 排序
   - 支持拖拽排序

7. **回收站**：
   - 删除的分类进入回收站
   - 回收站保留 7 天后自动清理
   - 可手动恢复或永久删除

## Frontend Changes

### API Layer

`frontend/src/api/category.ts`：

```typescript
export const categoryApi = {
  tree: () => request.get('/category/tree'),
  search: (name) => request.get('/category/search', { params: { name } }),
  trash: () => request.get('/category/trash'),
  create: (data) => request.post('/category', data),
  update: (id, data) => request.put(`/category/${id}`, data),
  delete: (id) => request.delete(`/category/${id}`),
  restore: (id) => request.post(`/category/${id}/restore`),
  permanentDelete: (id) => request.delete(`/category/${id}/permanent`),
  merge: (id, targetId) => request.post(`/category/${id}/merge`, { targetId }),
}

export const knowledgeCategoryApi = {
  assign: (knowledgeId, categoryIds) =>
    request.post(`/knowledge/${knowledgeId}/categories`, { categoryIds }),
  remove: (knowledgeId, categoryId) =>
    request.delete(`/knowledge/${knowledgeId}/categories/${categoryId}`),
  batchAssign: (knowledgeIds, categoryIds) =>
    request.post('/knowledge/batch/categories', { knowledgeIds, categoryIds }),
  replace: (knowledgeId, categoryIds) =>
    request.put(`/knowledge/${knowledgeId}/categories/replace`, { categoryIds }),
}
```

### 知识分配入口

1. **右键菜单**：在知识卡片/列表项上右键 → "添加到分类" 子菜单 → 多选分类
2. **详情页勾选**：打开知识详情时，侧边有"分类"区块，直接勾选分类

### 批量操作

1. 多选 + 批量分配：列表左上角显示已选数量，选中多条 → 点"批量分配到分类"
2. 全选当前筛选结果：支持"全选当前筛选结果"，一次性批量分配
3. 选中后才出现批量操作区：选中 2 条及以上时，顶部出现批量操作栏

### 分类树状态保存

- 使用 localStorage 持久化每个分类的展开/折叠状态
- 刷新页面后保持上次的展开状态

### 新建分类弹窗

- 新建分类弹窗内嵌知识选择区块
- 可多选知识打勾，创建分类时同时建立关联
- 创建成功后 toast 提示"添加知识"按钮，作为补充增强

### 分类搜索

- 侧边栏分类区域顶部增加搜索输入框
- 输入即过滤，实时高亮匹配的分类
- 支持按名称模糊搜索

### 分类拖拽排序

- 支持拖拽分类调整顺序
- 拖拽时显示插入位置指示器
- 拖拽释放后自动更新 sortOrder

### 回收站入口

- 侧边栏底部或设置中提供"回收站"入口
- 显示已删除的分类列表
- 支持一键恢复或永久删除

### Knowledge.vue Changes

1. `folders` 数据从 API 获取（`GET /api/category/tree`）
2. 支持完整 CRUD 操作
3. 操作后刷新分类树
4. 知识列表筛选支持 categoryId 参数
5. 右键菜单支持分配到分类
6. 详情页支持分类勾选
7. 批量选择和批量操作
8. 分类展开状态 localStorage 持久化
9. 分类搜索功能
10. 分类拖拽排序
11. 回收站管理
12. 分类合并
13. "未分类"知识入口

## Database Schema

```sql
CREATE TABLE t_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    icon VARCHAR(50) DEFAULT '📁',
    parent_id BIGINT DEFAULT NULL,
    sort_order INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME DEFAULT NULL,
    FOREIGN KEY (parent_id) REFERENCES t_category(id) ON DELETE SET NULL,
    INDEX idx_category_parent (parent_id),
    INDEX idx_category_deleted (deleted_at)
);

CREATE TABLE t_knowledge_category (
    knowledge_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (knowledge_id, category_id),
    FOREIGN KEY (knowledge_id) REFERENCES t_knowledge_base(id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES t_category(id) ON DELETE CASCADE,
    INDEX idx_knowledge_category_kid (knowledge_id)
);
```

## Implementation Order

1. Spring Boot: 添加分类 CRUD API（含回收站）+ MySQL 配置
2. AI-QA Service: 添加知识-分类关联接口（含批量、未分类查询）
3. 数据库: 创建表 + 迁移初始数据
4. 前端基础: 改造 Knowledge.vue 使用 API，分类树、CRUD
5. 前端功能: 搜索、拖拽排序、右键菜单、批量操作
6. 前端增强: 新建弹窗内嵌知识选择、回收站、合并
7. 测试: 完整流程验证
