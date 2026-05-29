# 采集管理页面增强方案

## 目标

将采集管理页面 (`Collection.vue`) 整合为一个完整的 Tab 式页面，包含：采集任务、脚本管理、执行记录、向量化监控，并优化用户体验。

## 当前状态

- `Collection.vue` 仅有采集任务列表和空的向量化监控 Tab
- `TaskList.vue` 等已有功能未被复用
- 用户体验缺失：执行无反馈、日志不可见、详情为空

## 设计方案

### Tab 结构

```
采集管理页面
├── Tab 1: 采集任务    - 任务列表 + 立即采集 + 执行反馈
├── Tab 2: 脚本管理    - 脚本列表 + 在线编辑 + 版本管理
├── Tab 3: 执行记录    - 执行列表 + 实时日志
└── Tab 4: 向量化监控  - 统计 + 任务列表 + 重试机制
```

### 1. 采集任务 Tab 增强

**功能**：
- 统计卡片：任务总数、启用数、今日执行、失败数
- 筛选栏：按状态、数据源搜索
- 执行功能：点击执行 → 显示进度抽屉 + 实时日志
- 详情功能：弹出详情弹窗，显示脚本配置、执行记录
- 分页支持

**交互优化**：
- 执行按钮 → 打开进度抽屉，显示实时日志
- 日志分级颜色：INFO(白)、WARN(黄)、ERROR(红)
- 执行完成自动刷新列表

### 2. 脚本管理 Tab

**功能**：
- 统计卡片：总数、启用/禁用、今日执行、成功/失败
- 筛选栏：状态、触发方式、数据源、关键词
- 列表：脚本名称、数据源、触发方式、状态、执行次数、操作
- 操作按钮：执行、详情、版本历史、编辑、启用/禁用
- 新建脚本：弹窗 + Monaco 编辑器
- 上传脚本：文件上传

**交互优化**：
- 详情弹窗显示脚本内容、配置、执行历史
- Monaco 编辑器集成：语法高亮、自动补全
- 版本历史时间线展示
- 克隆脚本功能

### 3. 执行记录 Tab

**功能**：
- 执行列表：脚本名称、状态、开始时间、耗时、操作
- 操作：查看日志、取消执行
- 日志查看器：实时日志 + 暂停滚动 + 导出

**交互优化**：
- 日志流式输出，支持暂停
- 日志分级显示
- 失败时显示错误堆栈
- 一键复制日志

### 4. 向量化监控 Tab

**功能**：
- 统计卡片：待处理、处理中、已完成、失败
- 任务列表：分类、总数、已处理、失败数、状态
- 操作：触发分类向量化、批量触发、重试失败项

**交互优化**：
- 失败项显示错误信息
- "重试全部失败"按钮
- 进度条显示处理状态

### 5. 整体体验优化

**功能**：
- Tab 切换自动刷新数据
- 操作成功/失败 Toast 提示
- 快捷键支持（Ctrl+Enter 执行等）

**组件复用**：
- `ScriptEditor.vue` - Monaco 编辑器
- `ExecutionLogViewer.vue` - 日志查看器
- `CollectionProgress.vue` - 进度抽屉
- `TriggerBadge.vue` - 触发方式标签
- `StatusBadge.vue` - 状态标签

## API 对应

| 功能 | API |
|-----|-----|
| 脚本列表 | `GET /scripts` |
| 执行脚本 | `POST /scripts/{id}/execute-now` |
| 执行记录 | `GET /scripts/executions/{id}` |
| 执行日志 | `GET /scripts/executions/{id}/logs` |
| 取消执行 | `POST /scripts/executions/{id}/cancel` |
| 向量化统计 | `GET /vectorization/stats` |
| 向量化任务 | `GET /vectorization/tasks` |
| 触发向量化 | `POST /vectorization/trigger/{id}` |
| 脚本内容 | `GET /scripts/{id}/content` |
| 版本历史 | `GET /scripts/{scriptId}/versions` |

## 文件改动

**新增/修改文件**：
- `frontend/src/views/collection/Collection.vue` - 主页面（重构）
- `frontend/src/views/scripts/TaskList.vue` - 可保留或删除（功能迁移到 Collection.vue）
- `frontend/src/components/ExecutionLogViewer.vue` - 日志查看器
- `frontend/src/components/CollectionProgress.vue` - 进度抽屉
- `frontend/src/api/index.ts` - 补充缺失的 API

## 实施顺序

1. 采集任务 Tab 增强
2. 脚本管理 Tab 整合
3. 执行记录 Tab 整合
4. 向量化监控 Tab 实现
5. 整体体验优化