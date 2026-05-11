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
| POST | /api/category | 新建分类 |
| PUT | /api/category/{id} | 更新分类（名称/图标/父级/排序） |
| DELETE | /api/category/{id} | 删除分类 |

### 知识-分类关联

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/knowledge | 按分类筛选知识（?categoryId=） |
| POST | /api/knowledge/{id}/categories | 知识分配到分类 |
| DELETE | /api/knowledge/{id}/categories/{categoryId} | 移除知识与分类的关联 |
| POST | /api/knowledge/batch/categories | 批量分配知识到分类 |

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
      "children": [
        { "id": 11, "name": "玉米", "icon": "🌐", "parentId": 1, "sortOrder": 1, "children": [] },
        { "id": 12, "name": "小麦", "icon": "🌐", "parentId": 1, "sortOrder": 2, "children": [] }
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
    "orphanedKnowledgeCount": 5,
    "deletedKnowledgeCount": 0
  }
}
```

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
   - 有子分类时：递归删除所有子分类
   - 有知识关联时：
     - 如果知识只属于被删除的分类，视为"孤儿知识"
     - 孤儿知识处理方式由用户选择：保留到"未分类"或同时删除
     - 返回受影响的分类数量、知识数量、孤儿知识数量

2. **移动分类**：
   - `parentId` 为 null 表示移到顶级
   - 不能将自己设为自己的子分类（需校验循环引用）

3. **排序**：
   - `sortOrder` 数字越小排越前
   - 同父级的分类按 sortOrder 排序

## Frontend Changes

### API Layer

在 `frontend/src/api/category.ts`：

```typescript
export const categoryApi = {
  tree: () => request.get('/category/tree'),
  create: (data) => request.post('/category', data),
  update: (id, data) => request.put(`/category/${id}`, data),
  delete: (id) => request.delete(`/category/${id}`),
}

export const knowledgeCategoryApi = {
  assign: (knowledgeId, categoryIds) =>
    request.post(`/knowledge/${knowledgeId}/categories`, { categoryIds }),
  remove: (knowledgeId, categoryId) =>
    request.delete(`/knowledge/${knowledgeId}/categories/${categoryId}`),
  batchAssign: (knowledgeIds, categoryIds) =>
    request.post('/knowledge/batch/categories', { knowledgeIds, categoryIds }),
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

### Knowledge.vue Changes

1. `folders` 数据从 API 获取（`GET /api/category/tree`）
2. 支持完整 CRUD 操作：
   - 新建根分类：POST /api/category
   - 新建子分类：POST /api/category（带 parentId）
   - 重命名：PUT /api/category/{id}
   - 移动分类：PUT /api/category/{id}（改 parentId）
   - 删除分类：DELETE /api/category/{id}
3. 操作后刷新分类树
4. 知识列表筛选支持 categoryId 参数
5. 右键菜单支持分配到分类
6. 详情页支持分类勾选
7. 批量选择和批量操作
8. 分类展开状态 localStorage 持久化

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
    FOREIGN KEY (parent_id) REFERENCES t_category(id) ON DELETE CASCADE
);

CREATE TABLE t_knowledge_category (
    knowledge_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    PRIMARY KEY (knowledge_id, category_id),
    FOREIGN KEY (knowledge_id) REFERENCES t_knowledge_base(id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES t_category(id) ON DELETE CASCADE
);

CREATE INDEX idx_category_parent ON t_category(parent_id);
CREATE INDEX idx_knowledge_category_kid ON t_knowledge_category(knowledge_id);
```

## Implementation Order

1. Spring Boot: 添加分类 CRUD API + MySQL 配置
2. AI-QA Service: 添加知识-分类关联接口（含批量分配）
3. 数据库: 创建表 + 迁移初始数据
4. 前端: 改造 Knowledge.vue 使用 API
5. 测试: 完整流程验证
