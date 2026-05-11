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
| color | VARCHAR(20) | 分类主题颜色，如 "#FF6B6B" |
| description | VARCHAR(500) | 分类描述/备注，hover 时显示 |
| pinned | TINYINT | 是否置顶，1=置顶显示在顶部 |
| last_operated_by | VARCHAR(100) | 最后操作人 |
| last_operated_at | DATETIME | 最后操作时间 |

### 知识-分类关联表 `t_knowledge_category`

| 字段 | 类型 | 说明 |
|------|------|------|
| knowledge_id | BIGINT | 知识条目ID |
| category_id | BIGINT | 分类ID |
| PRIMARY KEY | (knowledge_id, category_id) | 联合主键 |

### 分类操作日志表 `t_category_operation_log`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，自增 |
| category_id | BIGINT | 分类ID |
| operator | VARCHAR(100) | 操作人 |
| operation_type | VARCHAR(50) | 操作类型（CREATE/UPDATE/DELETE/RESTORE/MOVE） |
| operation_detail | VARCHAR(500) | 操作详情（JSON 格式保存操作前后状态） |
| operated_at | DATETIME | 操作时间 |

## API Design

### 分类管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/category/tree | 获取分类树（层级结构） |
| GET | /api/category/version | 获取分类树版本号（用于检测更新） |
| GET | /api/category/trash | 获取回收站中的分类 |
| POST | /api/category | 新建分类 |
| PUT | /api/category/{id} | 更新分类（名称/图标/父级/排序） |
| DELETE | /api/category/{id} | 软删除分类（移入回收站） |
| POST | /api/category/{id}/restore | 从回收站恢复分类 |
| DELETE | /api/category/{id}/permanent | 永久删除分类 |
| POST | /api/category/batch | 批量创建分类模板 |
| POST | /api/category/{id}/merge | 合并分类到目标分类 |
| PUT | /api/category/batch/move | 批量移动分类到目标父分类 |
| POST | /api/category/{id}/copy | 复制分类结构 |
| GET | /api/category/search | 搜索分类（?name=） |
| GET | /api/category/preview/{id} | 获取分类下的知识列表（快速预览） |
| GET | /api/category/stats | 获取分类统计面板数据 |
| GET | /api/category/export | 导出分类结构为 JSON |
| POST | /api/category/import | 导入分类结构 JSON |
| GET | /api/category/{id}/history | 获取分类操作历史 |

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
      "color": "#FF6B6B",
      "description": "粮信网主分类",
      "parentId": null,
      "sortOrder": 1,
      "knowledgeCount": 156,
      "children": [
        { "id": 11, "name": "玉米", "icon": "🌐", "color": "#FFD93D", "description": "玉米期货价格", "parentId": 1, "sortOrder": 1, "knowledgeCount": 45, "children": [] },
        { "id": 12, "name": "小麦", "icon": "🌐", "color": "#D4A574", "description": null, "parentId": 1, "sortOrder": 2, "knowledgeCount": 23, "children": [] }
      ]
    }
  ],
  "version": 12
}
```

**GET /api/category/version**

Response:
```json
{
  "code": 200,
  "data": { "version": 12 }
}
```

**POST /api/category**

Request:
```json
{ "name": "新建分类", "icon": "📁", "parentId": null, "sortOrder": 99, "color": "#58A6FF", "description": "分类描述" }
```

**PUT /api/category/{id}**

Request:
```json
{ "name": "新名称", "icon": "📂", "parentId": 1, "sortOrder": 5, "color": "#FF6B6B", "description": "分类描述文字" }
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

**POST /api/category/batch**

Request:
```json
{
  "categories": [
    { "name": "玉米", "icon": "🌐", "parentId": 1 },
    { "name": "小麦", "icon": "🌾", "parentId": 1 },
    { "name": "大豆", "icon": "🫘", "parentId": 1 }
  ]
}
```

Response:
```json
{
  "code": 200,
  "data": {
    "created": 3,
    "ids": [11, 12, 13]
  }
}
```

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

### 分类颜色/主题
- 每个分类左侧显示小色块（8px 宽的左边线）
- 新建/编辑弹窗中增加颜色选择器（预设 8-10 种常用颜色）
- 默认颜色跟随 emoji 主题色或随机分配

### 分类层级深度显示
- 分类树中根据层级深度动态计算缩进量（每层 +16px）
- 分类名前显示序号路径，如 "1.2.3" 表示三级分类
- 序号路径可点击跳转至对应层级

### 多级 Undo（撤销）
- 前端维护操作历史栈（最多保存 10 条）
- 每次操作记录：type + beforeState + afterState
- 工具栏或快捷键 Ctrl+Z 触发撤销
- 支持连续撤销直到栈空
- 切换分类 Tab 时清空栈

### 分类备注/描述
- 新建/编辑弹窗中增加"描述"输入框（最多 500 字）
- 鼠标悬停分类名称时显示 tooltip
- 描述文字支持复制

### 实时同步通知
- 前端每 30 秒轮询检查分类树版本
- 发现更新时顶部弹出通知条幅："检测到分类更新，点击刷新"
- 点击刷新按钮重新加载分类树
- 通知条幅 5 秒后自动消失（未点击情况下）

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
  batchMove: (categoryIds, targetParentId) =>
    request.put('/category/batch/move', { categoryIds, targetParentId }),
  copy: (id, name) => request.post(`/category/${id}/copy`, { name }),
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

### 分类图标选择器

- 新建/编辑分类弹窗中显示常用 emoji 网格
- 点击即可选择，无需手动输入
- 预置常用 emoji：📁 📂 🌐 🌾 🌽 🍚 📊 📈 📉 📰 📑 🗂️ 📚 🏷️

### 分类层级深度限制

- 分类最大支持 4 层层级深度
- 新建子分类时，如果当前层级已达到 4 层，禁止创建
- 已有超过 4 层的分类数据允许访问但不可继续加深

### 分类颜色/主题

- 每个分类左侧显示小色块（8px 宽左边线）
- 颜色值存储在 `color` 字段
- 新建/编辑时从预设 8 色盘中选择

### 分类描述/备注

- 描述文字存储在 `description` 字段
- hover 分类名称时显示 tooltip 浮层
- 描述最长 500 字符

### 实时同步

- 前端每 30 秒调用 `GET /api/category/version` 获取当前版本号
- 与本地缓存的 version 对比，发生变化时弹出刷新通知
- 版本号在分类任何变更时 +1

### 分类收藏/置顶
- 每个分类右侧显示"Pin"图标按钮
- 点击后分类置顶到列表最上方（最多置顶 3 个）
- 置顶分类保存在 localStorage，切换页面后保持
- 置顶分类优先于普通分类显示

### 分类知识快速预览
- 鼠标悬停分类名称 500ms 后显示浮层
- 浮层中列出该分类下的知识条目（标题 + 更新时间，最多显示 5 条）
- 点击浮层中的知识标题直接跳转到知识详情
- 浮层随鼠标移动，取消时自动消失

### 分类创建模板
- 新建分类弹窗中提供"批量创建"切换 Tab
- 输入多个分类名称（每行一个），如：
  ```
  玉米
  小麦
  大豆
  稻谷
  ```
- 选择统一的图标和父分类，一次性创建
- 创建成功后显示创建结果：已创建 X 个分类

### 分类名称自动补全
- 在知识详情页的分类分配区域
- 输入框输入分类名首字母（如"ym"）
- 下拉菜单实时显示匹配的分类（"玉米"）
- 支持键盘上下键选择，Enter 确认

### 分类统计面板
- 侧边栏顶部显示分类统计概览
- 知识数量排行榜（TOP 5 分类）
- 一周新增知识趋势图（折线图）
- 点击统计卡片直接跳转到对应分类

### 分类操作日志
- 每个分类显示最后修改人、最后修改时间
- 悬停时显示 tooltip："最后修改：张三，2024-01-15 14:30"
- 支持查看操作历史列表（创建/移动/重命名/删除等）
- 操作历史包含：操作人、操作时间、操作类型、操作内容

### 分类结构导出/导入
- 设置菜单提供"导出分类结构"按钮
- 导出为 JSON 文件，包含：name、icon、color、parentId、sortOrder
- 不导出 id（导入时自动生成新 id）
- "导入分类结构"支持拖拽上传 JSON 文件
- 导入时检测重名，提示用户选择覆盖或跳过

### 分类重名检测
- 创建/重命名分类时，后端自动检测同名分类
- 如有同名，返回提示："已存在同名分类 '玉米'，ID=5"
- 前端弹窗询问："已存在同名分类，是否合并到现有分类？"
- 确认后执行合并操作

### 分类快捷切换
- 支持 Ctrl+1/2/3/4/5 快捷键直接跳转到第 1-5 个分类
- 按住 Ctrl 再按数字键，快速切换当前分类
- 侧边栏分类名称旁显示对应的快捷键提示（如"玉米 ①②"）

### 批量移动分类

- 分类支持复选框多选
- 选中多个分类后，可一次性移动到同一父分类
- 批量移动操作通过 `PUT /api/category/batch/move` 实现

### 分类结构复制

- 右键分类菜单提供"复制结构"选项
- 复制时创建同名空结构（仅复制层级，不复制知识关联）
- 新结构名称可编辑

### 键盘快捷键

| 快捷键 | 功能 |
|--------|------|
| N | 新建分类（聚焦到分类树时） |
| F | 聚焦分类搜索框 |
| Delete | 删除选中分类 |
| Enter | 确认/保存 |
| Escape | 取消/关闭弹窗 |
| ↑↓ | 在分类树中上下导航 |
| →← | 展开/折叠分类 |

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
14. 分类图标选择器（emoji 网格）
15. 分类层级深度限制（最大 4 层）
16. 批量移动分类
17. 分类结构复制
18. 键盘快捷键支持
19. **分类颜色/主题**（左侧小色块 + 颜色选择器）
20. **分类层级深度显示**（缩进 + 序号路径）
21. **多级 Undo 撤销**（Ctrl+Z / 操作历史栈）
22. **分类备注/描述**（hover tooltip）
23. **实时同步通知**（30 秒轮询 + 刷新条幅）
24. **分类收藏/置顶**（一键访问常用分类）
25. **分类知识快速预览**（hover 浮层显示知识列表）
26. **分类创建模板**（一次性批量创建多个分类）
27. **分类名称自动补全**（快速归类时输入首字母）
28. **分类统计面板**（知识数量排行榜 + 趋势图）
29. **分类操作日志**（最后修改人/时间 + 操作历史）
30. **分类结构导出/导入**（JSON 格式）
31. **分类重名检测**（创建时提示合并）
32. **分类快捷切换**（Ctrl+1/2/3 直接跳转）

## Database Schema

```sql
CREATE TABLE t_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    icon VARCHAR(50) DEFAULT '📁',
    color VARCHAR(20) DEFAULT NULL,
    description VARCHAR(500) DEFAULT NULL,
    parent_id BIGINT DEFAULT NULL,
    sort_order INT DEFAULT 0,
    pinned TINYINT DEFAULT 0 COMMENT '是否置顶，1=置顶',
    last_operated_by VARCHAR(100) DEFAULT NULL COMMENT '最后操作人',
    last_operated_at DATETIME DEFAULT NULL COMMENT '最后操作时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at DATETIME DEFAULT NULL,
    version BIGINT DEFAULT 0,
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
