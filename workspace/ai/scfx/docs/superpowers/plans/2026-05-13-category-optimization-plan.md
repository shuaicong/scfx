# 分类功能优化实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 优化分类搜索功能（关键词高亮+描述显示）+ 简化右键菜单（保留9项删除7项）

**Architecture:** 在前端 CategoryTree.vue 中实现搜索优化：前端实时过滤 + 关键词高亮 + 描述显示；右键菜单从16项精简到9项。

**Tech Stack:** Vue 3 Composition API, TypeScript, Element Plus

---

## 文件清单

| 文件 | 操作 |
|------|------|
| `frontend/src/components/CategoryTree.vue` | 修改搜索逻辑、右键菜单、预览弹窗 |

---

## 任务 1：搜索关键词高亮

**Files:**
- Modify: `frontend/src/components/CategoryTree.vue:140-155` (搜索结果计算逻辑)
- Modify: `frontend/src/components/CategoryTree.vue:1242-1260` (搜索结果模板)
- Modify: `frontend/src/components/CategoryTree.vue:2080-2105` (搜索结果 CSS)

- [ ] **Step 1: 添加高亮函数**

在 `<script setup>` 中添加高亮函数（在 `searchCategories` 函数附近）：

```typescript
// 关键词高亮函数
const highlightText = (text: string, query: string): string => {
  if (!query) return text
  const regex = new RegExp(`(${query})`, 'gi')
  return text.replace(regex, '<mark>$1</mark>')
}
```

- [ ] **Step 2: 搜索结果计算修改**

找到 `searchResults` computed（约第142行），确认它已包含 description 搜索：

```typescript
const searchResults = computed(() => {
  if (!autocompleteQuery.value) return []
  const query = autocompleteQuery.value.toLowerCase()
  return flattenCategories(folders.value).filter(c =>
    c.name.toLowerCase().includes(query) ||
    (c.description && c.description.toLowerCase().includes(query))
  )
})
```

- [ ] **Step 3: 修改搜索结果模板中的名称高亮**

找到搜索结果 item 模板（约第1252行），修改为：

```html
<span class="search-result-name" v-html="highlightText(result.name, autocompleteQuery)"></span>
```

- [ ] **Step 4: 添加 description 显示**

在同一搜索结果模板中，在 `.search-result-info` 内添加（高亮名称后面）：

```html
<span v-if="result.description" class="search-result-desc">
  {{ result.description.length > 50 ? result.description.slice(0, 50) + '...' : result.description }}
</span>
```

- [ ] **Step 5: 添加高亮 CSS 样式**

在样式区添加（约在 `.search-result-name` 样式后）：

```css
.search-result-name mark {
  background: var(--accent-bg);
  color: var(--accent);
  padding: 0 2px;
  border-radius: 2px;
  font-weight: 600;
}

.search-result-desc {
  font-size: 12px;
  color: var(--text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 2px;
}
```

- [ ] **Step 6: 提交**

```bash
git add frontend/src/components/CategoryTree.vue
git commit -m "feat(CategoryTree): add search keyword highlighting and description display"
```

---

## 任务 2：简化右键菜单

**Files:**
- Modify: `frontend/src/components/CategoryTree.vue:1459-1514` (右键菜单模板)
- Modify: `frontend/src/components/CategoryTree.vue:2080-2150` (右键菜单 CSS)

- [ ] **Step 1: 确定保留的菜单项**

右键菜单模板保留以下9项（按此顺序）：
1. 新建子分类
2. 编辑
3. 置顶
4. 预览
5. 复制
6. 合并
7. 权限设置
8. 添加到收藏夹
9. 删除

删除以下7项：
- 批量创建
- 订阅通知
- 搜索知识
- 移动历史
- AI 自动标签
- 加入对比
- 完整性检查

- [ ] **Step 2: 修改右键菜单模板**

找到 context menu 模板（约第1459行），替换为：

```html
<!-- Context menu -->
<div
  v-if="contextMenuVisible"
  class="context-menu"
  :style="{ left: contextMenuPosition.x + 'px', top: contextMenuPosition.y + 'px' }"
  @click.stop
>
  <div class="context-menu-item" @click="openCreateDialog(contextMenuCategory?.id || null)">
    新建子分类
  </div>
  <div class="context-menu-item" @click="openEditDialog(contextMenuCategory!)">
    编辑
  </div>
  <div class="context-menu-item" @click="togglePin(contextMenuCategory!)">
    {{ contextMenuCategory?.pinned === 1 ? '取消置顶' : '置顶' }}
  </div>
  <div class="context-menu-item" @click="openPreview(contextMenuCategory!)">
    预览
  </div>
  <div class="context-menu-item" @click="duplicateCategory(contextMenuCategory!)">
    复制
  </div>
  <div class="context-menu-item" @click="openMergeDialog(contextMenuCategory!)">
    合并
  </div>
  <div class="context-menu-item" @click="showPermissionDialog(contextMenuCategory!)">
    权限设置
  </div>
  <div class="context-menu-item" @click="addToFavorites([contextMenuCategory!.id])">
    添加到收藏夹
  </div>
  <div class="context-menu-item danger" @click="deleteCategory(contextMenuCategory!.id)">
    删除
  </div>
</div>
```

- [ ] **Step 3: 确认右键菜单 CSS 存在**

右键菜单已有样式（第2427-2453行），无需修改。

- [ ] **Step 4: 提交**

```bash
git add frontend/src/components/CategoryTree.vue
git commit -m "feat(CategoryTree): simplify context menu to 9 items"
```

---

## 任务 3：增强版预览弹窗

**Files:**
- Modify: `frontend/src/components/CategoryTree.vue:1615-1647` (预览弹窗模板)
- Modify: `frontend/src/components/CategoryTree.vue:3060-3090` (预览弹窗 CSS)

- [ ] **Step 1: 修改预览弹窗模板**

找到 Preview dialog 模板（约第1615行），替换为：

```html
<!-- Preview dialog -->
<div v-if="showPreview" class="dialog-overlay" @click.self="closePreview">
  <div class="dialog dialog-preview">
    <div class="dialog-header">
      <h3>分类预览：{{ previewCategory?.name }}</h3>
      <button class="close-btn" @click="closePreview">×</button>
    </div>
    <div class="dialog-body">
      <div class="preview-info">
        <span class="preview-icon">{{ previewCategory?.icon }}</span>
        <span class="preview-name">{{ previewCategory?.name }}</span>
      </div>
      <div class="preview-details">
        <div class="preview-detail-item" v-if="previewCategory?.description">
          <span class="preview-label">描述</span>
          <span class="preview-value">{{ previewCategory?.description }}</span>
        </div>
        <div class="preview-detail-item">
          <span class="preview-label">知识数量</span>
          <span class="preview-value">{{ previewCategory?.knowledgeCount || 0 }} 条</span>
        </div>
        <div class="preview-detail-item">
          <span class="preview-label">子分类</span>
          <span class="preview-value">{{ previewCategory?.children?.length || 0 }} 个</span>
        </div>
        <div class="preview-detail-item" v-if="getParentPath(previewCategory)">
          <span class="preview-label">父分类</span>
          <span class="preview-value">{{ getParentPath(previewCategory) }}</span>
        </div>
        <div class="preview-detail-item">
          <span class="preview-label">创建时间</span>
          <span class="preview-value">{{ previewCategory?.createdAt || '-' }}</span>
        </div>
        <div class="preview-detail-item">
          <span class="preview-label">最后更新</span>
          <span class="preview-value">{{ previewCategory?.updatedAt || '-' }}</span>
        </div>
      </div>
    </div>
    <div class="dialog-footer">
      <button class="btn-cancel" @click="closePreview">关闭</button>
    </div>
  </div>
</div>
```

- [ ] **Step 2: 添加预览弹窗 CSS**

找到 `.preview-info` 样式（约第3061行），在后面添加：

```css
.dialog-preview {
  width: 480px;
}

.preview-details {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.preview-detail-item {
  display: flex;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-color);
}

.preview-detail-item:last-child {
  border-bottom: none;
}

.preview-label {
  width: 80px;
  flex-shrink: 0;
  color: var(--text-muted);
  font-size: 13px;
}

.preview-value {
  flex: 1;
  color: var(--text-primary);
  font-size: 13px;
  word-break: break-all;
}
```

- [ ] **Step 3: 提交**

```bash
git add frontend/src/components/CategoryTree.vue
git commit -m "feat(CategoryTree): enhance preview dialog with full details"
```

---

## 验收标准

1. 搜索关键词在结果中高亮显示（橙色背景+加粗）
2. 搜索结果显示分类描述（截断显示，最多50字符）
3. 右键菜单从16项减少到9项
4. 预览弹窗显示：描述、知识数量、子分类数、父分类路径、创建时间、更新时间

---

## 暂不实现功能（后续批次）

- 分类统计面板（第2批）
- 键盘快捷键（第3批）