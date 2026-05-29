# 前端设计规范

## 1. 设计理念

**Warm Industrial / Organic Precision** - 温暖的大地色调配合精准的数据展示

粮讯分析平台的设计风格融合了粮食行业的温暖质感和现代数据管理的精准专业。整体视觉语言传达出可靠、智能、高效的品牌调性。

### 核心原则
- **自然与科技的平衡**: 大地色调（麦金、赭石）象征农业本源，精准布局体现数据专业性
- **克制的优雅**: 避免过度装饰，通过色彩渐变和微妙阴影创造层次
- **可读的沉浸感**: 深色侧边栏聚焦内容区，平滑过渡引导视觉流动

## 2. 设计规范

### 2.1 色彩系统

粮讯平台的色彩体系以「麦金」为主色调，搭配深青灰侧边栏和纯净白内容区，营造温暖而专业的视觉体验。

```css
:root {
  /* 主色调 - 麦金色（温暖、专业） */
  --color-primary: #d4a574;         /* 麦金 - 主品牌色 */
  --color-primary-light: #e5c4a0;  /* 浅麦金 */
  --color-primary-dark: #c49464;   /* 深麦金 */
  --color-primary-hover: #c99b5e;  /* 悬停态 */

  /* 功能色 - 保持 Element Plus 语义 */
  --color-success: #48bb78;        /* 成功绿 */
  --color-warning: #f6ad55;         /* 警告橙 */
  --color-danger: #fc8181;          /* 危险红 */
  --color-info: #63b3ed;            /* 信息蓝 */

  /* 侧边栏深色系 */
  --color-sidebar-bg: #1e2433;     /* 侧边栏背景 */
  --color-sidebar-dark: #161b26;   /* 侧边栏深色 */
  --color-sidebar-text: #c8d1dc;   /* 侧边栏文字 */
  --color-sidebar-muted: #7a8599;  /* 侧边栏次要文字 */
  --color-sidebar-active: #f5c87a; /* 侧边栏激活态 */

  /* 内容区背景 */
  --color-bg-page: #f0f2f5;         /* 页面背景渐变起点 */
  --color-bg-page-end: #e8eaef;    /* 页面背景渐变终点 */
  --color-bg-card: #ffffff;         /* 卡片背景 */
  --color-bg-header: #ffffff;       /* 顶部栏背景 */

  /* 文字色 */
  --color-text-primary: #1a202c;    /* 主要文字 - 深灰黑 */
  --color-text-regular: #4a5568;    /* 常规文字 */
  --color-text-secondary: #718096;  /* 次要文字 */
  --color-text-muted: #a0aec0;     /* 弱化文字 */

  /* 边框色 */
  --color-border: #e2e8f0;
  --color-border-light: #edf2f7;
  --color-border-lighter: #f7f8fa;

  /* 状态指示 */
  --color-online: #48bb78;         /* 在线/采集中 */
  --color-idle: #d69e2e;           /* 空闲 */
  --color-offline: #a0aec0;         /* 离线 */
}
```

**色彩语义映射**:
- 麦金 (#d4a574) → 主操作、品牌标识、侧边栏 logo 区
- 成功绿 (#48bb78) → 成功状态、数据正向指标
- 危险红 (#fc8181) → 错误、失败、严重告警
- 警告橙 (#f6ad55) → 警告、中等告警
- 信息蓝 (#63b3ed) → 信息提示、数据指标

### 2.2 字体规范

平台使用系统字体栈，确保跨平台一致性。数字使用等宽字体展示保证对齐。

```css
/* 字体栈 */
--font-family: 'PingFang SC', 'Helvetica Neue', Helvetica, 'Microsoft YaHei', Arial, sans-serif;
--font-mono: 'SF Mono', 'Monaco', 'Inconsolata', 'Roboto Mono', monospace;

/* 字号体系 */
--font-size-xs: 11px;      /* 版本号、极小标签 */
--font-size-sm: 12px;      /* 辅助说明、时间戳 */
--font-size-base: 13px;    /* 正文小 */
--font-size-md: 14px;      /* 正文 */
--font-size-lg: 15px;      /* 页面标题 */
--font-size-xl: 18px;      /* 大标题 */
--font-size-xxl: 22px;     /* 页面主标题 */
--font-size-2xxl: 28px;    /* 数据统计数字 */

/* 字重 */
--font-weight-normal: 400;
--font-weight-medium: 500;
--font-weight-semibold: 600;
--font-weight-bold: 700;

/* 行高 */
--line-height-tight: 1.2;
--line-height-normal: 1.5;
--line-height-relaxed: 1.75;
```

### 2.4 圆角与阴影规范

```css
/* 圆角 - 卡片和按钮 */
--radius-sm: 6px;     /* 输入框、选择器 */
--radius-md: 8px;     /* 按钮、标签 */
--radius-lg: 12px;    /* 卡片、面板 */
--radius-xl: 16px;    /* 大卡片、模态框 */

/* 阴影 */
--shadow-sm: 0 1px 3px rgba(0,0,0,0.04), 0 4px 12px rgba(0,0,0,0.03);
--shadow-md: 0 8px 24px rgba(0,0,0,0.08);
--shadow-glow: 0 4px 12px rgba(212,165,116,0.3);
```

## 3. 布局规范

### 3.1 整体布局

深色侧边栏 (220px) + 白色顶栏 (72px) + 渐变内容区。

```
┌────────────────────────────────────────────────────────┐
│                    Header (72px)                        │
│  [Logo + 标题]                    [时间] [采集按钮]     │
├──────────────┬─────────────────────────────────────────┤
│              │                                         │
│   Sidebar    │            Main Content                 │
│   (220px)    │       (padding: 24px, 渐变背景)        │
│              │                                         │
│  • 仪表板    │  ┌──────────────────────────────────┐  │
│  • 采集管理  │  │         Page Content              │  │
│  • SDK管理   │  │                                    │  │
│  • 脚本管理  │  │  统计卡片 / 表格 / 表单            │  │
│  • 日志查看  │  │                                    │  │
│  ──────────  │  └──────────────────────────────────┘  │
│  • 系统设置  │                                         │
│              │                                         │
└──────────────┴─────────────────────────────────────────┘
```

**布局特点**:
- 侧边栏: 深色渐变 + 麦金色 logo + 悬浮指示条
- 顶栏: 白色背景 + 页面标题 + 实时时间 + 主采集按钮
- 内容区: 浅灰渐变 + 白色圆角卡片

### 3.2 页面结构

每个页面应遵循以下结构：

```
┌──────────────────────────────────────────────────────┐
│  Page Header (页面标题 + 操作按钮)                     │
├──────────────────────────────────────────────────────┤
│  Filter Bar (筛选条件栏，可折叠)                       │
├──────────────────────────────────────────────────────┤
│  Stats Cards (统计卡片，2-4列)                        │
├──────────────────────────────────────────────────────┤
│  Data Table (数据表格)                               │
│  ┌────┬────┬────┬────┬────┬────┬────┬─────────┐ │
│  │ ID │名称│状态│时间│来源│操作│... │操作列  │ │
│  ├────┼────┼────┼────┼────┼────┼────┼─────────┤ │
│  │    │    │    │    │    │    │    │         │ │
│  └────┴────┴────┴────┴────┴────┴────┴─────────┘ │
├──────────────────────────────────────────────────────┤
│  Pagination (分页)                                  │
└──────────────────────────────────────────────────────┘
```

## 4. 组件规范

### 4.1 统计卡片

统计卡片采用顶部渐变边框指示类型色，悬停时轻微上浮并放大阴影。

```vue
<div class="stat-card" :class="stat.type">
  <div class="stat-inner">
    <div class="stat-icon">
      <el-icon><SuccessFilled /></el-icon>
    </div>
    <div class="stat-data">
      <div class="stat-value">{{ stat.value }}</div>
      <div class="stat-label">{{ stat.label }}</div>
    </div>
    <div class="stat-trend up" v-if="stat.trend">
      <el-icon><CaretTop /></el-icon>
      <span>{{ stat.trend }}%</span>
    </div>
  </div>
</div>

<style scoped>
.stat-card {
  border-radius: 12px;
  padding: 18px;
  position: relative;
  overflow: hidden;
  transition: all 0.3s ease;
}
.stat-card::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0;
  height: 3px;
  background: linear-gradient(90deg, var(--color-primary), var(--color-primary-dark));
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}
.stat-card.success::before { background: linear-gradient(90deg, #48bb78, #38a169); }
.stat-card.danger::before { background: linear-gradient(90deg, #fc8181, #f56565); }
.stat-card.primary::before { background: linear-gradient(90deg, #63b3ed, #4299e1); }
.stat-card.warning::before { background: linear-gradient(90deg, #f6ad55, #ed8936); }
.stat-inner {
  display: flex;
  align-items: center;
  gap: 14px;
}
.stat-icon {
  width: 48px; height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
}
.stat-card.success .stat-icon { background: rgba(72, 187, 120, 0.1); color: #48bb78; }
.stat-card.danger .stat-icon { background: rgba(252, 129, 129, 0.1); color: #fc8181; }
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1a202c;
  font-variant-numeric: tabular-nums;
}
</style>
```

### 4.2 数据表格

```vue
<el-table
  :data="tableData"
  stripe
  v-loading="loading"
  element-loading-text="加载中..."
  style="width: 100%"
  row-key="id"
  class="data-table"
>
  <el-table-column prop="name" label="名称" min-width="150" />
  <el-table-column prop="status" label="状态" width="100">
    <template #default="{ row }">
      <el-tag :type="getStatusType(row.status)">
        {{ getStatusText(row.status) }}
      </el-tag>
    </template>
  </el-table-column>
  <el-table-column label="操作" width="200" fixed="right">
    <template #default="{ row }">
      <el-button type="primary" size="small" @click="handleView(row)">查看</el-button>
      <el-button type="warning" size="small" @click="handleEdit(row)">编辑</el-button>
    </template>
  </el-table-column>
</el-table>

<style scoped>
.data-table :deep(.el-table__header-wrapper th) {
  background: #f7f8fa;
  font-weight: 600;
  color: #1a202c;
}
.data-table :deep(.el-table__row:hover > td) {
  background: #f7f8fa !important;
}
</style>
```

### 4.3 表单布局

```vue
<el-form :model="form" :rules="rules" label-width="120px">
  <el-row :gutter="20">
    <el-col :span="12">
      <el-form-item label="名称" prop="name">
        <el-input v-model="form.name" placeholder="请输入名称" />
      </el-form-item>
    </el-col>
    <el-col :span="12">
      <el-form-item label="状态" prop="status">
        <el-select v-model="form.status" placeholder="请选择状态">
          <el-option label="启用" value="enabled" />
          <el-option label="禁用" value="disabled" />
        </el-select>
      </el-form-item>
    </el-col>
  </el-row>
</el-form>
```

### 4.4 筛选栏

```vue
<div class="filter-bar">
  <el-form :inline="true" :model="filters">
    <el-form-item label="状态">
      <el-select v-model="filters.status" clearable placeholder="全部">
        <el-option label="启用" value="enabled" />
        <el-option label="禁用" value="disabled" />
      </el-select>
    </el-form-item>
    <el-form-item label="时间范围">
      <el-date-picker
        v-model="filters.dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
      />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="handleQuery">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </el-form-item>
  </el-form>
</div>
```

## 5. 页面模板

### 5.1 页面 Header

```vue
<div class="page-header">
  <div class="header-left">
    <h1 class="page-title">{{ title }}</h1>
    <div class="breadcrumb">
      <span>首页</span>
      <span class="separator">/</span>
      <span class="current">{{ title }}</span>
    </div>
  </div>
  <div class="header-right">
    <el-button type="primary" class="primary-btn" @click="handleCreate">
      <el-icon><Plus /></el-icon>新建
    </el-button>
  </div>
</div>

<style scoped>
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}
.page-title {
  font-size: 22px;
  font-weight: 600;
  color: #1a202c;
  margin: 0 0 4px 0;
}
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #718096;
}
.breadcrumb .separator {
  color: #cbd5e0;
}
.breadcrumb .current {
  color: #4a5568;
  font-weight: 500;
}
.primary-btn {
  background: linear-gradient(135deg, #d4a574 0%, #c49464 100%);
  border: none;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(212, 165, 116, 0.25);
}
</style>
```

### 5.2 完整页面模板

```vue
<template>
  <div class="page-container">
    <!-- 页面标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">{{ pageName }}</h1>
      </div>
      <div class="header-actions">
        <el-button type="primary" class="primary-btn" @click="handleCreate">
          <el-icon><Plus /></el-icon>新建
        </el-button>
      </div>
    </div>

    <!-- 筛选栏 -->
    <el-card class="filter-card">
      <el-form :inline="true" :model="filters">
        <el-form-item label="状态">
          <el-select v-model="filters.status" clearable placeholder="全部">
            <el-option label="启用" value="enabled" />
            <el-option label="禁用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card">
      <el-table :data="list" stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="名称" min-width="150" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'enabled' ? 'success' : 'danger'">
              {{ row.status === 'enabled' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.page-container {
  animation: slideUp 0.4s ease-out;
}
@keyframes slideUp {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.page-title {
  font-size: 22px;
  font-weight: 600;
  color: #1a202c;
  margin: 0;
}
.filter-card,
.table-card {
  border-radius: 16px;
  margin-bottom: 20px;
}
.filter-card :deep(.el-card__body),
.table-card :deep(.el-card__body) {
  padding: 20px 24px;
}
.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
.primary-btn {
  background: linear-gradient(135deg, #d4a574 0%, #c49464 100%);
  border: none;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(212, 165, 116, 0.25);
}
</style>
```

## 6. 命名规范

### 6.1 Vue 组件命名

- 页面组件：`PageName.vue` (PascalCase)
- 布局组件：`LayoutName.vue`
- 业务组件：`BusinessName.vue`
- 通用组件：`CommonName.vue`

### 6.2 CSS 类名命名

使用 **BEM 命名规范**：

```css
.block {}
.block__element {}
.block--modifier {}
```

示例：

```css
.page-header {}
.page-header__title {}
.page-header--dark {}
```

### 6.3 目录结构

```
src/
├── assets/
│   ├── styles/
│   │   ├── variables.scss    # CSS变量
│   │   ├── mixins.scss      # 混入
│   │   ├── common.scss      # 公共样式
│   │   └── transitions.scss # 动画
│   └── images/
├── components/
│   ├── common/              # 通用组件
│   │   ├── StatCard.vue
│   │   ├── PageHeader.vue
│   │   └── FilterBar.vue
│   └── business/            # 业务组件
├── composables/             # 组合式函数
│   ├── useTable.js
│   └── useForm.js
└── views/
    └── pages...
```

## 7. 交互规范

### 7.1 按钮交互

| 类型 | 样式 | 使用场景 |
|------|------|----------|
| 主要按钮 | 麦金渐变实心 | 主操作，如新建、保存 |
| 次要按钮 | 白色边框 | 次要操作，如取消、重置 |
| 文字按钮 | 无边框 | 辅助操作，如查看详情 |
| 危险按钮 | 红色实心 | 危险操作，如删除 |

**主要按钮样式**:
```css
.el-button.primary-btn {
  background: linear-gradient(135deg, #d4a574 0%, #c49464 100%);
  border: none;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(212, 165, 116, 0.25);
}
.primary-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(212, 165, 116, 0.35);
}
```

### 7.2 反馈机制

- **成功反馈**：`ElMessage.success()`
- **错误反馈**：`ElMessage.error()`
- **警告反馈**：`ElMessage.warning()`
- **确认对话框**：`ElMessageBox.confirm()`

### 7.3 加载状态

- 表格：使用 `v-loading` + `element-loading-text`
- 按钮：使用 `:loading="submitting"`
- 页面：使用 `el-loading` 遮罩

## 8. 响应式规范

```css
/* 断点 */
@media (max-width: 768px)  { /* 平板 */ }
@media (max-width: 480px)  { /* 手机 */ }

/* 栅格 */
.el-col-24 { width: 100%; }
.el-col-12 { width: 50%; }
.el-col-8 { width: 33.33%; }
.el-col-6 { width: 25%; }
```

## 9. 动画规范

```css
/* 淡入淡出 */
.fade-enter-active, .fade-leave-active { transition: opacity 0.3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

/* 滑入滑出 */
.slide-enter-active, .slide-leave-active { transition: transform 0.3s; }
.slide-enter-from { transform: translateX(-20px); }
.slide-leave-to { transform: translateX(20px); }
```

## 10. 代码规范

### 10.1 Vue SFC 规范

```vue
<template>
  <!-- 模板内容 -->
</template>

<script setup lang="ts">
// 1. 导入
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'

// 2. 常量定义
const TITLE = '页面标题'

// 3. 响应式数据
const loading = ref(false)
const list = ref([])

// 4. 计算属性
const filteredList = computed(() => list.value)

// 5. 方法
function handleSubmit() { }

// 6. 生命周期
onMounted(() => { })
</script>

<style scoped>
/* 使用 CSS 变量 */
.page-container {
  padding: var(--spacing-lg);
}
</style>
```

### 10.2 TypeScript 规范

```typescript
// 使用 interface 定义数据结构
interface User {
  id: number
  name: string
  status: 'active' | 'inactive'
}

// 使用泛型
function useTable<T>() {
  const data = ref<T[]>([])
  return { data }
}
```
