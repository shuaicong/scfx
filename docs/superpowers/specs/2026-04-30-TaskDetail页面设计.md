# 任务详情页 (TaskDetail) 设计文档

## 1. 概述

### 1.1 目的
设计一个专业的任务详情页面，支持脚本在线编辑、执行管理、版本查看等核心功能。

### 1.2 设计风格
- **主题**: 深色科技风格 (与现有原型保持一致)
- **色系**: GitHub Dark 风格，蓝色为主强调色
- **字体**: Inter (正文) + JetBrains Mono (代码)
- **布局**: 单页式设计，左侧辅助信息 + 右侧主内容

### 1.3 技术选型
- Vue 3 + Element Plus
- Monaco Editor (Python 语法高亮)
- 响应式布局

---

## 2. 页面布局

```
┌─────────────────────────────────────────────────────────────────────────────┐
│  ← 返回    粮信网玉米晨报采集                              [禁用] [执行] [编辑] │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────┐  ┌──────────────────────────────────────────────────────┐ │
│  │   任务统计    │  │                      脚本配置                          │ │
│  │              │  │  ┌──────────────────────────────────────────────────┐ │ │
│  │  执行次数 156│  │  │  # Python Script                                 │ │ │
│  │  成功    153 │  │  │  1  import requests                              │ │ │
│  │  失败      3 │  │  │  2  from bs4 import BeautifulSoup                │ │ │
│  │  成功率 98%  │  │  │  3                                                │ │ │
│  │              │  │  │  4  def crawl(url):                             │ │ │
│  │  ─────────── │  │  │  5      response = requests.get(url)             │ │ │
│  │              │  │  │  ...                                             │ │ │
│  │  状态  启用中 │  │  │                                                  │ │ │
│  │  版本    v3   │  │  └──────────────────────────────────────────────────┘ │ │
│  │  下次 明天8点 │  │  [保存脚本]                                           │ │
│  └──────────────┘  └──────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  基本信息                                    触发配置                  │  │
│  │  ────────────────────────────────────────────────────────────────────│  │
│  │  数据源:  粮信网                        触发方式:  Cron 表达式         │  │
│  │  创建时间: 2026-04-01 08:00              Cron:     0 0 8 * * *         │  │
│  │  更新时间: 2026-04-30 10:30              下次执行: 2026-05-01 08:00:00  │  │
│  │  创建人:   admin                                                  │  │
│  │  修改人:   admin                                                  │  │
│  │  描述:    每日定时采集粮信网玉米价格数据...                         │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
│  ┌───────────────────────────────────────────────────────────────────────┐  │
│  │  版本历史                                              [查看全部]       │  │
│  │  ────────────────────────────────────────────────────────────────────│  │
│  │  v3  2026-04-30 10:30  修复登录问题                     [查看] [回滚] │  │
│  │  v2  2026-04-28 09:00  优化爬取逻辑                     [查看] [回滚] │  │
│  │  v1  2026-04-01 08:00  首次上线                         [查看]       │  │
│  └───────────────────────────────────────────────────────────────────────┘  │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. 组件设计

### 3.1 顶部操作栏
- **返回按钮**: 左上角，圆形按钮
- **任务名称**: 大标题，居中
- **操作按钮组**: 右侧，禁用/启用(危险色) / 执行(主色) / 编辑(默认)

### 3.2 左侧统计卡片
- 圆角卡片，深色背景
- 统计数据: 执行次数、成功次数、失败次数、成功率
- 分隔线
- 状态标签(绿色启用/灰色禁用)
- 当前版本号
- 下次执行时间

### 3.3 脚本配置区 (核心区域)
- Monaco Editor 编辑器
- 深色主题 (vs-dark)
- Python 语法高亮
- 行号显示
- 代码折叠
- 搜索功能 (Ctrl+F)
- 底部: 保存按钮 + 快捷键提示 (Ctrl+S)

### 3.4 基本信息区
- 两列布局: 左列数据源/创建时间/创建人/描述，右列触发配置详情
- 标签色区分: 数据源用蓝色 tag，状态用成功/灰色 tag

### 3.5 版本历史区
- 紧凑列表，每行: 版本号 + 时间 + 变更说明 + 操作按钮
- 操作: 查看(预览内容) / 回滚(需确认)
- 右侧: "查看全部"链接跳转到版本历史页

### 3.6 快捷操作
- 键盘快捷键:
  - `Ctrl+S`: 保存脚本
  - `Ctrl+Enter`: 执行任务
  - `Ctrl+E`: 编辑任务

---

## 4. 功能细节

### 4.1 脚本编辑
- 实时语法检查
- 自动保存草稿到 localStorage (防丢失)
- 保存时输入变更说明
- 保存后自动创建新版本

### 4.2 状态切换
- 启用 → 禁用: 确认对话框，禁用后停止调度
- 禁用 → 启用: 启用调度，提示下次执行时间

### 4.3 立即执行
- 点击后弹出执行进度对话框
- 实时显示执行日志
- 执行完成/失败/取消后关闭对话框
- 支持取消执行

### 4.4 版本回滚
- 选择目标版本
- 确认对话框显示回滚后的变更
- 回滚创建新版本 (不是删除历史)

---

## 5. 响应式策略

| 宽度 | 布局变化 |
|------|----------|
| ≥1200px | 左侧统计 + 右侧主内容，两列布局 |
| 768-1200px | 统计卡片移到顶部横向排列，主内容单列 |
| <768px | 所有内容单列，统计卡片收起为下拉 |

---

## 6. API 对接

### 6.1 现有 API (来自 @/api/index.ts)
```typescript
// 获取任务详情
scriptApi.getById(id: number): Promise<CollectionScript>

// 更新任务
scriptApi.update(id: number, data: CollectionScript): Promise<void>

// 获取脚本内容
scriptApi.getContent(id: number): Promise<{data: string}>

// 更新脚本内容
scriptApi.updateContent(id: number, content: string): Promise<void>

// 执行任务
executionApi.execute(id: number): Promise<{data: {executionId: string}}>

// 获取执行状态
executionApi.get(executionId: string): Promise<Execution>

// 启用/禁用
scriptApi.enable(id: number): Promise<void>
scriptApi.disable(id: number): Promise<void>
```

### 6.2 需要的字段扩展
```typescript
interface CollectionScript {
  // 已有字段...
  scriptName: string
  scriptContent: string
  source: string
  status: 'enabled' | 'disabled'
  triggerType: 'once' | 'repeat' | 'cron'
  cronExpression?: string
  repeatType?: string
  repeatTime?: string

  // 需要新增的字段...
  createdAt: string
  updatedAt: string
  createdBy: string
  updatedBy: string
  description: string
  currentVersion: number
  nextExecutionTime: string
  lastExecutionTime: string
  executionCount: number
  successCount: number
  failedCount: number
}
```

---

## 7. 快捷键映射

| 快捷键 | 功能 | 条件 |
|--------|------|------|
| Ctrl+S | 保存脚本 | 编辑模式下 |
| Ctrl+Enter | 执行任务 | 全局 |
| Ctrl+E | 打开编辑对话框 | 全局 |
| Escape | 关闭对话框 | 对话框打开时 |

---

## 8. 状态管理

```typescript
// 页面状态
const state = reactive({
  script: null as CollectionScript | null,
  isEditing: false,
  scriptContent: '',
  originalContent: '',
  hasUnsavedChanges: false,
  executing: false,
  executionId: null as string | null,
  executionLogs: [] as ExecutionLog[],
  showEditDialog: false,
  showExecuteDialog: false,
})
```

---

## 9. 组件清单

| 组件 | 类型 | 说明 |
|------|------|------|
| TaskDetail.vue | 页面 | 主页面容器 |
| ScriptEditor.vue | 子组件 | Monaco 编辑器封装 |
| StatsCard.vue | 子组件 | 左侧统计卡片 |
| VersionList.vue | 子组件 | 版本历史列表 |
| ExecuteDialog.vue | 子组件 | 执行进度弹窗 |

---

## 10. 实现优先级

1. **P0 - 核心功能**
   - 页面布局框架
   - 脚本内容展示
   - Monaco Editor 集成
   - 保存脚本功能

2. **P1 - 重要功能**
   - 基本信息展示
   - 执行任务功能
   - 版本历史展示

3. **P2 - 增强体验**
   - 快捷键支持
   - 自动保存草稿
   - 状态切换
   - 回滚功能

---

## 11. 注意事项

1. **Monaco Editor 加载**: 使用 `vue-monaco-editor` 或手动加载，确保 Tree-shaking 友好
2. **性能优化**: 脚本内容较大时，考虑虚拟滚动
3. **错误处理**: API 失败时显示友好错误提示
4. **权限控制**: 根据用户角色显示/隐藏操作按钮
