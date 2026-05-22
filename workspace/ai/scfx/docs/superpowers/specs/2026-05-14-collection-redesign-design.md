# 采集管理页面重构设计方案

## 1. 背景与目标

### 现状问题
- 存在两个页面入口：`/collection` 和 `/scripts`，用户困惑
- `CollectionTask` 和 `CollectionScript` 两个实体职责混乱
- 采集任务没有关联到具体Python脚本，无法独立执行

### 目标
- 统一页面入口：保留 `/scripts`，整合 Collection.vue 内容
- 简化数据模型：废弃 CollectionTask，以 CollectionScript 为核心
- 提升用户体验：执行反馈透明、日志可见

## 2. 页面结构调整

### 入口统一
| 现状 | 重构后 |
|------|--------|
| `/collection` → Collection.vue (4 Tab) | 废弃 |
| `/scripts` → TaskList.vue | 保留，作为唯一入口 |

### Tab 结构
| Tab | 内容 | 说明 |
|-----|------|------|
| 脚本管理 | 脚本列表 + 统计卡片 + 筛选 + 增删改查 | 核心 Tab |
| 执行记录 | 执行历史 + 日志查看 | 保留 |

**移除：**
- 向量化监控 Tab（移至知识库页面）

## 3. 数据模型

### CollectionScript（核心实体）
```
CollectionScript
├── id                    # 主键
├── scriptName           # 脚本名称
├── description          # 脚本描述
├── source               # 数据源标识（liangxinwang/mysteel/china_grain）
├── scriptContent        # Python代码内容
├── collType             # 采集类型
├── collObject           # 采集对象
├── status               # 状态（enabled/disabled）
├── triggerType          # 触发方式（manual/single/cron/repeat）
├── cronExpression       # Cron表达式
├── repeatConfig         # 重复配置（JSON）
├── repeatType           # 重复周期类型（daily/weekly/monthly）
├── repeatTime           # 重复时间（HH:mm:ss）
├── weeklyDays           # 每周执行日（1,3,5）
├── monthlyDay           # 每月执行日
├── monthlyLastDay       # 是否每月最后一天
├── endType              # 结束类型（never/date/count）
├── repeatCount          # 重复次数
├── reportIntervalSeconds # 上报频率（秒）
├── lastExecutionTime    # 最后执行时间
├── nextExecutionTime    # 下次执行时间
├── executionCount       # 执行次数
├── successCount         # 成功次数
├── failedCount          # 失败次数
├── createdBy             # 创建者
├── createdAt             # 创建时间
├── updatedAt             # 更新时间
├── currentVersion        # 当前版本号
```

### 废弃实体
- `CollectionTask` - 实体类删除
- `t_collection_task` - 数据表保留或清理

## 4. 功能设计

### 4.1 脚本管理 Tab

#### 统计卡片（4个）
| 卡片 | 数值 |
|------|------|
| 脚本总数 | 所有脚本数量 |
| 启用中 | status=enabled 的脚本数量 |
| 今日执行 | 今日执行次数 |
| 失败数 | 今日失败次数 |

#### 列表字段
| 字段 | 说明 |
|------|------|
| 脚本名称 | scriptName |
| 数据源 | source（显示中文名） |
| 触发方式 | triggerType（Badge 展示） |
| 状态 | status（enabled/disabled） |
| 执行次数 | executionCount |
| 操作 | 执行、编辑、详情、删除 |

#### 筛选功能
- 状态筛选（启用/禁用）
- 数据源筛选
- 触发方式筛选
- 关键词搜索

#### 新建脚本（抽屉编辑）
| 字段 | 类型 | 说明 |
|------|------|------|
| 脚本名称 | 文本框 | 必填 |
| 数据源 | 下拉选择 | 必填 |
| 触发方式 | 下拉选择 | 必填 |
| Cron表达式 | 文本框（图形化辅助） | 定时触发时必填 |
| 脚本内容 | Monaco编辑器 + 文件上传 | 必填 |
| 描述 | 文本域 | 选填 |

#### 触发方式配置
| 方式 | 配置项 |
|------|--------|
| 手动执行 | 无需配置 |
| 单次执行 | 执行时间 |
| 定时cron | Cron表达式 + 图形化辅助说明 |
| 间隔重复 | repeatType + repeatConfig |

#### 脚本详情弹窗
- 完整信息展示：名称、数据源、触发方式、脚本内容（只读）、执行统计、最后执行时间、下次执行时间
- 支持部分字段直接编辑

#### 编辑交互
- 点击"编辑" → 右侧抽屉滑出
- Monaco编辑器编辑脚本内容
- 文件上传功能（上传.py文件）
- 保存后刷新列表

### 4.2 执行记录 Tab

#### 列表字段
| 字段 | 说明 |
|------|------|
| 脚本名称 | scriptName |
| 状态 | status（Badge） |
| 开始时间 | startTime |
| 耗时 | durationMs |
| 操作 | 查看日志、取消（running时） |

#### 执行反馈
- 点击"执行" → 打开进度抽屉
- 实时日志流
- 执行进度（采集中数量）
- 完成/失败状态
- 完成后自动刷新列表

### 4.3 删除确认
- 普通确认弹窗："确定删除该脚本吗？"

## 5. API 设计

### 脚本管理
| 接口 | 方法 | 说明 |
|------|------|------|
| GET /scripts | GET | 分页列表，支持筛选 |
| GET /scripts/{id} | GET | 脚本详情 |
| POST /scripts | POST | 新建脚本 |
| PUT /scripts/{id} | PUT | 更新脚本 |
| DELETE /scripts/{id} | DELETE | 删除脚本 |
| POST /scripts/{id}/execute | POST | 触发执行 |
| GET /scripts/{id}/versions | GET | 版本历史 |

### 执行记录
| 接口 | 方法 | 说明 |
|------|------|------|
| GET /executions | GET | 执行记录列表 |
| GET /executions/{id} | GET | 执行详情 |
| GET /executions/{id}/logs | GET | 执行日志 |
| POST /executions/{id}/cancel | POST | 取消执行 |

## 6. 实施顺序

1. **数据模型重构**
   - 清理 CollectionTask 相关代码
   - 保留 CollectionScript

2. **页面整合**
   - 将 Collection.vue 的脚本管理Tab + 执行记录Tab 整合到 TaskList.vue
   - 移除 /collection 路由（或重定向到 /scripts）

3. **脚本管理功能**
   - 完善 CRUD
   - Monaco编辑器集成
   - 文件上传功能
   - 触发配置（图形化 + 高级模式）

4. **执行功能**
   - 执行反馈（进度抽屉）
   - 实时日志

5. **向量化监控**
   - 从采集页面移除
   - 在知识库页面增加向量化监控入口

## 7. 文件改动清单

### 前端
| 文件 | 改动 |
|------|------|
| `frontend/src/router/index.ts` | 移除 /collection 路由或重定向 |
| `frontend/src/views/scripts/TaskList.vue` | 重构为整合页面 |
| `frontend/src/views/collection/Collection.vue` | 废弃 |
| `frontend/src/components/ScriptEditor.vue` | Monaco编辑器组件（新建） |
| `frontend/src/components/CollectionProgress.vue` | 进度抽屉组件 |
| `frontend/src/views/knowledge/Knowledge.vue` | 增加向量化监控 |

### 后端
| 文件 | 改动 |
|------|------|
| `backend/.../entity/CollectionTask.java` | 删除 |
| `backend/.../entity/CollectionScript.java` | 保留并增强 |
| `backend/.../controller/ScriptController.java` | 新建或从 CollectionController 分离 |
| `backend/.../service/ScriptService.java` | 新建 |
| 数据库 | 清理 t_collection_task 表 |