# 分类功能优化设计

> **创建日期：** 2026-05-13
> **更新日期：** 2026-05-13

---

## 一、搜索优化

### 需求确认

| 功能 | 选择 |
|------|------|
| 关键词高亮 | 需要 |
| 搜索历史 | 不需要 |
| 显示描述 | 需要 |

### 实现方案

前端实时搜索 + 高亮，利用已有前端数据。

#### 搜索逻辑

```typescript
const searchResults = computed(() => {
  if (!searchQuery.value) return []
  const query = searchQuery.value.toLowerCase()
  return flattenCategories(folders.value).filter(c =>
    c.name.toLowerCase().includes(query) ||
    (c.description && c.description.toLowerCase().includes(query))
  )
})
```

#### 关键词高亮

```typescript
const highlightText = (text: string, query: string): string => {
  if (!query) return text
  const regex = new RegExp(`(${query})`, 'gi')
  return text.replace(regex, '<mark>$1</mark>')
}
```

模板：
```html
<span class="search-result-name" v-html="highlightText(result.name, autocompleteQuery)"></span>
```

#### CSS 样式

```css
.search-result-name mark {
  background: var(--accent-bg);
  color: var(--accent);
  padding: 0 2px;
  border-radius: 2px;
}
```

---

## 二、右键菜单简化

### 保留的9项菜单

| 菜单项 | 说明 |
|--------|------|
| 新建子分类 | 创建子分类 |
| 编辑 | 编辑分类信息 |
| 置顶 | 置顶/取消置顶 |
| 预览 | 增强版预览 |
| 复制 | 复制分类 |
| 合并 | 合并到其他分类 |
| 权限设置 | 修改访问权限 |
| 添加到收藏夹 | 收藏此分类 |
| 删除 | 删除分类（含确认） |

### 删除的7项

| 删除项 | 原因 |
|--------|------|
| 批量创建 | 工具栏已有入口 |
| 订阅通知 | 低频功能 |
| 搜索知识 | 工具栏搜索可替代 |
| 移动历史 | 几乎不用 |
| AI 自动标签 | 功能未实现 |
| 加入对比 | 低频功能 |
| 完整性检查 | 功能未实现 |

### 增强版预览设计

预览弹窗显示：

| 信息 | 说明 |
|------|------|
| 图标 + 名称 | 分类基本信息 |
| 描述 | description 完整显示 |
| 知识数量 | 该分类下的知识条数 |
| 子分类数量 | 有几个子分类 |
| 创建时间 | createdAt |
| 最后更新 | updatedAt |
| 父分类路径 | 如：根分类 > 粮食 > 玉米 |

---

## 修改文件

| 文件 | 操作 |
|------|------|
| `frontend/src/components/CategoryTree.vue` | 搜索逻辑、右键菜单、预览弹窗 |

---

## 暂不实现功能

- 搜索历史记录（用户选择不需要）
- AI 自动标签（功能未开发）
- 完整性检查（功能未开发）
- 移动历史（低频）

---

## 后续批次

| 批次 | 功能 | 状态 |
|------|------|------|
| 第1批 | 搜索优化 + 右键菜单简化 | 进行中 |
| 第2批 | 分类统计面板 | 未开始 |
| 第3批 | 键盘快捷键 | 未开始 |