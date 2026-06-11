# AI 问答 — 历史记录与会话管理设计方案（Phase 1 基础版）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 AI 问答功能提供完整的历史记录管理能力：左侧抽屉快速切换会话、独立历史页面支持批量管理、会话标题自动生成与手动锁定。

**Architecture:** 前端新增 SessionDrawer.vue（侧边抽屉）+ HistoryPage.vue（独立管理页），后端新增 t_chat_session 表 + 4 个 RESTful API + 标题生成机制。状态通过 Pinia 统一管理。

**Tech Stack:** Vue 3 + TypeScript, Spring Boot 2.7+, MySQL 8.0+, Python 3.9+ (FastAPI)

---

## 🔴 阶段边界约束

| 维度 | Phase 1（当前） | Phase 2（后续迭代） |
|------|----------------|-------------------|
| 平台 | 仅桌面端 | 移动端适配、手势逻辑 |
| 功能 | 抽屉切换、历史页面（搜索/批量删除/重命名）、标题生成 | 归档、置顶、导出、标签/分组管理 |
| 数据策略 | 仅软删除，不提供恢复/物理删除 | 彻底删除、数据恢复 |

---

## 设计决策

### 入口方式
- **左侧抽屉**：高频切换入口，点击左上角 ☰ 按钮滑出/收起，覆盖在聊天界面上方
- **独立历史页面**：顶部导航栏「历史」按钮跳转，承载搜索、批量删除、重命名等管理操作

### 抽屉交互
- 点击 ☰ 按钮切换显示/隐藏
- 点击遮罩层或选择会话后自动收起
- 移动端后续支持左滑手势唤起（Phase 2）

### 历史列表展示
每条会话显示：**标题 + 最后更新时间 + 最后消息摘要预览**

### 消息摘要截断规则
- `last_message` 摘要文本由后端统一截断，最大保留 **100 字符**
- 超长内容尾部拼接 `...`，避免列表样式错乱、数据传输冗余

### 静默计时规则
- 从用户最后一次发送消息开始计时，30 秒后触发智能标题生成
- 计时期间若产生新消息，**重置计时器**，确保仅会话真正停止交互后才生成

### 会话标题生成策略（三层）

| 层级 | 触发时机 | title_source | 说明 |
|------|----------|--------------|------|
| 默认 | 首条消息发送后 | `default` | 截取首条问题前 20 字作为占位标题 |
| 智能 | 会话静默 30 秒后 | `auto` | Python 异步生成更准确的标题，覆盖 `default`/`auto` |
| 手动 | 用户编辑标题 | `manual` | **永久锁定**，不再被任何自动更新覆盖 |

**强制规则：** `title_source = manual` 时，后端必须拦截所有自动标题更新请求，不可将用户手动编辑的标题覆盖。

### 软删除规则
- 所有删除操作为软删除（`is_deleted = 1`），不物理删除数据
- Phase 1 不提供数据恢复、物理删除、彻底删除能力
- 后端查询接口统一过滤 `is_deleted = 0`
- 前端列表页、抽屉同步屏蔽已删除会话

### 单条删除规则（抽屉内）
- 抽屉列表支持单条删除，同遵循全局软删除逻辑
- 弹出二次确认框，确认后执行 `is_deleted = 1`
- 接口过滤后即时从列表移除

### 新建会话行为
- 清空当前聊天界面内容
- 重置 `currentSessionId` 为 `null`
- 创建全新空会话上下文（等待用户输入后生成）

### 时间格式统一
- 接口入参：`yyyy-MM-dd`
- 接口返回、数据库存储：`yyyy-MM-ddTHH:mm:ss`（ISO 8601）
- 页面展示：后端/前端格式化为人易读格式（如 `2026-06-11 10:30`、`10分钟前`）

---

## API 设计

### `GET /api/ai-chat/sessions` — 会话列表（分页）

**Params:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| page | int | 否 | 页码，默认 1 |
| size | int | 否 | 每页条数，默认 20 |
| keyword | string | 否 | 标题模糊搜索 |
| start | string | 否 | 起始时间 (yyyy-MM-dd) |
| end | string | 否 | 结束时间 (yyyy-MM-dd) |

**后端过滤规则：**
- 统一 `WHERE is_deleted = 0`
- 按 `updated_at DESC` 排序
- **权限隔离：** 后端强制按 `user_id` 过滤，不同用户数据完全隔离
- 时间参数非法时（格式错误、起大于止），返回默认全量查询，不抛 500

**响应字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 会话 ID (UUID) |
| title | string | 会话标题 |
| title_source | string | 标题来源：default/auto/manual |
| message_count | int | 会话消息总条数 |
| last_message | string | 最后一条消息摘要，用于列表预览 |
| updated_at | string | 最后更新时间 (ISO 8601) |

**Response:**
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "id": "session-uuid",
        "title": "玉米价格走势分析",
        "title_source": "auto",
        "message_count": 3,
        "last_message": "目前山东地区玉米收购价在2300-2400元/吨区间震荡...",
        "updated_at": "2026-06-11T10:30:00"
      }
    ],
    "total": 42,
    "page": 1,
    "size": 20
  }
}
```

### `GET /api/ai-chat/sessions/{id}` — 会话详情

返回会话完整消息列表（按 message_id 升序），用于会话切换时恢复上下文。

### `PATCH /api/ai-chat/sessions/{id}/title` — 更新标题

**两种调用场景：**

```json
// 场景1：用户手动编辑（前端调用）
{
  "title": "2026年6月玉米价格分析",
  "source": "manual"
}

// 场景2：后端异步智能生成（内部调用，前端不触发）
{
  "title": "玉米行情汇总",
  "source": "auto"
}
```

**后端强校验规则：**
- 若库中 `title_source = 'manual'`：无论入参 `source` 是什么，**直接拒绝更新**，返回提示 + 日志
- 若库中 `title_source != 'manual'`：正常更新 `title` + `title_source`

### `DELETE /api/ai-chat/sessions` — 批量软删除

**Request:**
```json
{
  "ids": ["uuid1", "uuid2", "uuid3"]
}
```

后端执行 `UPDATE t_chat_session SET is_deleted = 1 WHERE id IN (...)`，单条删除复用此接口（传入单元素数组）。

操作成功后，前端同步更新 Pinia 会话列表，移除已删除项，保持状态一致。

### 统一错误响应规范

所有接口统一错误响应格式：

```json
{
  "code": 500,
  "msg": "错误描述",
  "data": null
}
```

**各接口异常场景：**

| 接口 | 场景 | code | msg |
|------|------|------|-----|
| PATCH /title | title_source=manual 拒绝更新 | 403 | 手动命名的标题不允许自动修改 |
| PATCH /title | 会话不存在/已软删除 | 404 | 会话不存在 |
| DELETE /sessions | ids 为空数组 | 400 | 删除列表不能为空 |
| DELETE /sessions | 部分 ID 不存在/已删除 | 200 | 整体成功，日志记录无效 ID |
| 所有接口 | 会话 ID 不存在/已软删除 | 404 | 会话不存在 |
| 所有接口 | 未登录/无权限 | 按项目现有鉴权规范 | — |

**DELETE 补充规则：** 入参 `ids` 为空数组时后端直接拦截，不执行 SQL，返回 `code: 400`；部分 ID 不存在或已删除时仅更新有效 ID，接口整体返回成功，无效 ID 记日志不抛错。

---

## 数据库变更

新建 Flyway 迁移脚本 `V7__create_chat_session.sql`：

```sql
-- V7__create_chat_session.sql
-- AI 问答会话管理表
CREATE TABLE IF NOT EXISTS t_chat_session (
  id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '会话ID (UUID)',
  user_id VARCHAR(64) NOT NULL COMMENT '用户ID，关联系统用户',
  title VARCHAR(255) NOT NULL DEFAULT '' COMMENT '会话标题',
  title_source ENUM('default','auto','manual') NOT NULL DEFAULT 'default' COMMENT '标题来源：默认/智能生成/手动编辑',
  message_count INT NOT NULL DEFAULT 0 COMMENT '会话消息总数',
  last_message TEXT COMMENT '最后一条消息摘要',
  is_deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标记：0-正常 1-已删除',
  is_archived TINYINT(1) NOT NULL DEFAULT 0 COMMENT '归档标记：0-未归档 1-已归档（Phase2 启用）',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  INDEX idx_user_deleted_time (user_id, is_deleted, updated_at DESC),
  INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI问答会话管理表';
```

> **说明：**
> - `user_id` 关联用户表，Phase 1 不创建物理外键（避免锁表/迁移限制），代码层做逻辑关联
> - `title_source` ENUM 仅允许 `default`/`auto`/`manual` 三个枚举值，应用层和数据库双层校验，禁止非法值插入
> - 新增会话时，数据库字段默认值已配置，代码层无需重复赋初始值

---

## 前端组件

### AiChat.vue 改造

| 改动项 | 说明 |
|--------|------|
| 左侧 40px 占位列 | 替换为 `<SessionDrawer />` 组件 |
| 会话切换 | 调用 `GET /sessions/{id}` 加载历史消息，复用 history_manager 分组逻辑 |
| 标题栏 | 展示当前会话标题，点击进入编辑态，调用 `PATCH /sessions/{id}/title` |
| 新建会话 | 清空聊天内容 + 重置 currentSessionId |

### SessionDrawer.vue

| 功能 | 说明 |
|------|------|
| 列表 | 按 updated_at 倒序展示，标题+时间+预览 |
| 新建会话 | 清空聊天内容 + 重置 currentSessionId |
| 单条删除 | 点击删除按钮 → 二次确认 → 调用批量接口（单元素数组）→ 刷新列表 |
| 空状态 | "暂无历史对话，开始你的第一次提问" |
| 加载态 | 请求过程中展示 loading，避免空白 |
| 交互 | 点击遮罩/选择会话自动收起 |

### HistoryPage.vue

| 功能 | 说明 |
|------|------|
| 列表 | 与抽屉保持一致的会话卡片 |
| 搜索 | 关键词 + 时间范围筛选 |
| 搜索无结果 | 展示空状态文案 |
| 批量删除 | 多选后批量删除（二次确认弹窗），支持全选/反选 |
| 单条操作 | 重命名、跳转聊天页 |

### ChatSession 类型定义

在 `api/sessions.ts` 或独立 `types` 文件中声明：

```typescript
export interface ChatSession {
  id: string
  title: string
  title_source: 'default' | 'auto' | 'manual'
  message_count: number
  last_message: string
  updated_at: string
}
```

### 统一二次确认弹窗

单条删除和批量删除共用统一文案：

| 项目 | 内容 |
|------|------|
| 标题 | 确认删除 |
| 内容 | 删除后仅隐藏，可在后续版本恢复，是否继续？ |
| 按钮 | 取消 / 确认 |

### api/sessions.ts

```typescript
import request from '@/utils/request'

export function getSessions(params: {
  page: number
  size: number
  keyword?: string
  start?: string
  end?: string
}) {
  return request({ url: '/api/ai-chat/sessions', method: 'get', params })
}

export function getSessionDetail(id: string) {
  return request({ url: `/api/ai-chat/sessions/${id}`, method: 'get' })
}

export function updateSessionTitle(id: string, title: string, source: 'default' | 'auto' | 'manual') {
  return request({
    url: `/api/ai-chat/sessions/${id}/title`,
    method: 'patch',
    data: { title, source }
  })
}

export function batchDeleteSessions(ids: string[]) {
  return request({ url: '/api/ai-chat/sessions', method: 'delete', data: { ids } })
}
```

### Pinia 状态管理

新增 `useChatStore`：

| 状态 | 类型 | 说明 |
|------|------|------|
| currentSessionId | string \| null | 当前会话 ID |
| sessions | Session[] | 会话列表 |
| loading | boolean | 列表加载状态 |

**loading 生命周期：**
- 列表请求发起前置为 `true`
- 请求结束（成功或失败）置为 `false`
- 避免无限 loading 阻塞 UI

**错误处理：**
- 接口请求失败时通过 Toast 给出友好提示，不破坏当前页面状态
- 不在 Store 中持久化错误状态（避免状态污染）

**状态变更规范（强制）：**
- 所有会话新增、删除、标题修改、切换，**必须通过接口请求后再更新 Store**
- 禁止前端私自修改状态
- sessions 列表与后端数据保持单向同步
- 切换会话时，currentSessionId 同步变更，并触发聊天页加载上下文

---

## 风险与优化

| 风险 | 缓解措施 |
|------|----------|
| 会话列表数据量大 | 分页 20 条/次；会话量 > 100 时前端虚拟滚动 |
| 会话切换状态不一致 | Pinia 统一管理，API 操作为唯一状态变更入口 |
| 标题自动覆盖用户编辑 | `title_source = manual` 后端强制拦截，不信任前端 |
| 软删除数据膨胀 | Phase 1 仅软删，数据量可控；Phase 2 提供彻底删除/归档 |
| 异步标题生成重复触发 | 后端对同一会话加防重控制，静默 30 秒内仅触发一次智能标题生成 |
| 时间筛选参数非法 | 后端校验 start/end 日期格式与大小，非法参数直接返回默认全量查询 |
