# 使用体验优化实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现使用体验优化功能，包括任务列表实时状态、心跳监控、执行详情页、三种对比功能、Cron校验、版本关联、操作日志、告警通知、数据源关联任务视图

**Architecture:** 后端 Spring Boot + 前端 Vue3 + Python 采集服务

**Tech Stack:** Java (Spring Boot, WebSocket), Vue3 (Element Plus, SSE), Python (Flask, heartbeat)

---

## 一、文件结构映射

### 1.1 后端文件结构

```
backend/src/main/java/com/scfx/
├── entity/
│   ├── OperationLog.java              # 新增
│   ├── AlertRule.java                # 新增
│   ├── AlertRecord.java              # 新增
│   └── 修改: TaskExecution.java       # 添加 scriptVersion, scriptMd5
├── mapper/
│   ├── OperationLogMapper.java       # 新增
│   └── AlertRuleMapper.java          # 新增
├── service/
│   ├── impl/
│   │   ├── TaskStatusServiceImpl.java # 新增 - 批量查询任务状态
│   │   ├── AlertServiceImpl.java      # 新增
│   │   └── OperationLogServiceImpl.java # 新增
├── controller/
│   ├── TaskStatusController.java     # 新增 - 批量查询任务状态
│   ├── AlertController.java         # 新增
│   ├── OperationLogController.java   # 新增
│   └── 修改: CollectorManageController.java # 添加心跳状态查询
├── aspect/
│   └── OperationLogAspect.java        # 新增 - 操作日志 AOP
└── annotation/
    └── OperationLog.java              # 新增 - 操作日志注解
```

### 1.2 前端文件结构

```
frontend/src/
├── api/
│   ├── task-status.ts                # 新增 - 任务状态 API
│   ├── alert.ts                      # 新增 - 告警 API
│   └── operation-log.ts               # 新增 - 操作日志 API
├── views/
│   ├── scripts/
│   │   ├── ExecutionDetail.vue       # 新增 - 执行详情独立页面
│   │   └── components/
│   │       ├── ExecutionCompareDialog.vue      # 新增
│   │       ├── ScriptVersionCompareDialog.vue   # 新增
│   │       └── ExecutionLogCompareDialog.vue    # 新增
│   └── system/
│       ├── DataSourceDetail.vue      # 修改 - 添加关联任务列表
│       └── OperationLog.vue          # 新增 - 操作日志页面
├── components/
│   └── ServiceStatus.vue             # 新增 - 服务状态监控组件
└── router/
    └── index.ts                      # 修改 - 添加路由
```

---

## 二、Task 列表

### Task 1: 任务列表实时状态监控 (2.1)

**Files:**
- Create: `backend/src/main/java/com/scfx/service/TaskStatusService.java`
- Create: `backend/src/main/java/com/scfx/service/impl/TaskStatusServiceImpl.java`
- Create: `backend/src/main/java/com/scfx/controller/TaskStatusController.java`
- Modify: `frontend/src/views/scripts/TaskList.vue`

- [ ] **Step 1: 创建 TaskStatusService 接口**

```java
package com.scfx.service;

import java.util.List;
import java.util.Map;

public interface TaskStatusService {
    /**
     * 批量查询任务状态
     * @param scriptIds 脚本ID列表
     * @return Map<scriptId, TaskStatus>
     */
    Map<Long, TaskStatus> getTasksStatus(List<Long> scriptIds);

    /**
     * 获取单个任务状态
     * @param scriptId 脚本ID
     * @return TaskStatus
     */
    TaskStatus getTaskStatus(Long scriptId);

    /**
     * 停止执行中的任务
     * @param executionId 执行ID
     */
    void stopExecution(String executionId);
}
```

- [ ] **Step 2: 创建 TaskStatus 内部类**

```java
package com.scfx.service;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TaskStatus {
    private Long scriptId;
    private String status;           // pending/running/success/failed/cancelled
    private Integer collectedCount;   // 实时采集数量
    private String executionId;       // 当前执行ID
    private LocalDateTime startTime;  // 开始时间
    private LocalDateTime lastExecuted; // 最后执行时间
    private Integer lastCollectedCount; // 上次采集数量
}
```

- [ ] **Step 3: 创建 TaskStatusServiceImpl**

```java
package com.scfx.service.impl;

import com.scfx.entity.TaskExecution;
import com.scfx.mapper.TaskExecutionMapper;
import com.scfx.service.TaskStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskStatusServiceImpl implements TaskStatusService {
    private final TaskExecutionMapper executionMapper;
    private final TaskExecutionService executionService;

    @Override
    public Map<Long, TaskStatus> getTasksStatus(List<Long> scriptIds) {
        if (scriptIds == null || scriptIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, TaskStatus> result = new HashMap<>();

        for (Long scriptId : scriptIds) {
            TaskStatus status = getTaskStatus(scriptId);
            result.put(scriptId, status);
        }

        return result;
    }

    @Override
    public TaskStatus getTaskStatus(Long scriptId) {
        TaskStatus status = new TaskStatus();
        status.setScriptId(scriptId);

        // 查询正在执行的记录
        TaskExecution running = executionMapper.findRunningByScriptId(scriptId);
        if (running != null) {
            status.setStatus("running");
            status.setExecutionId(running.getExecutionId());
            status.setStartTime(running.getStartTime());
            status.setCollectedCount(running.getCollectedCount() != null ? running.getCollectedCount() : 0);
        } else {
            // 查询最近一次执行记录
            TaskExecution last = executionMapper.findLastByScriptId(scriptId);
            if (last != null) {
                status.setStatus(last.getStatus());
                status.setLastExecuted(last.getEndTime());
                status.setLastCollectedCount(last.getCollectedCount() != null ? last.getCollectedCount() : 0);
            } else {
                status.setStatus("pending");
            }
        }

        return status;
    }

    @Override
    public void stopExecution(String executionId) {
        executionService.updateStatus(executionId, "cancelled");

        // 通知 Python 停止（写标记文件）
        String stopFile = "/tmp/stop_" + executionId;
        try {
            java.nio.file.Files.write(
                java.nio.file.Paths.get(stopFile),
                "stop".getBytes()
            );
        } catch (Exception e) {
            // 忽略，Python 会定期检查
        }
    }
}
```

- [ ] **Step 4: 创建 TaskStatusController**

```java
package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.service.TaskStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/scripts/status")
@RequiredArgsConstructor
public class TaskStatusController {
    private final TaskStatusService taskStatusService;

    @GetMapping
    public Result<Map<Long, TaskStatus>> getTasksStatus(
            @RequestParam("ids") List<Long> ids) {
        Map<Long, TaskStatus> statuses = taskStatusService.getTasksStatus(ids);
        return Result.success(statuses);
    }

    @PostMapping("/{scriptId}/executions/{executionId}/stop")
    public Result<Void> stopExecution(
            @PathVariable Long scriptId,
            @PathVariable String executionId) {
        taskStatusService.stopExecution(executionId);
        return Result.success();
    }
}
```

- [ ] **Step 5: 修改 TaskExecutionMapper 添加方法**

```java
// 在 TaskExecutionMapper 中添加
@Select("SELECT * FROM t_task_execution WHERE script_id = #{scriptId} AND status = 'running' LIMIT 1")
TaskExecution findRunningByScriptId(@Param("scriptId") Long scriptId);

@Select("SELECT * FROM t_task_execution WHERE script_id = #{scriptId} ORDER BY start_time DESC LIMIT 1")
TaskExecution findLastByScriptId(@Param("scriptId") Long scriptId);
```

- [ ] **Step 6: 前端 TaskList.vue 添加实时状态**

```vue
<template>
  <!-- 在现有表格中添加状态列 -->
  <el-table :data="tableData" stripe>
    <el-table-column prop="name" label="任务名称" />
    <el-table-column prop="datasourceName" label="数据源" />
    <el-table-column prop="cronExpression" label="定时" />
    <el-table-column label="状态" width="150">
      <template #default="{ row }">
        <span :class="'status-' + taskStatuses[row.id]?.status">
          {{ getStatusText(taskStatuses[row.id]) }}
        </span>
      </template>
    </el-table-column>
    <el-table-column label="操作" width="200">
      <template #default="{ row }">
        <el-button v-if="taskStatuses[row.id]?.status === 'running'" size="small" @click="handleStop(row)">
          停止
        </el-button>
        <el-button size="small" @click="handleExecute(row)">执行</el-button>
        <el-button size="small" @click="handleViewDetail(row)">详情</el-button>
      </template>
    </el-table-column>
  </el-table>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { scriptApi } from '@/api/scripts'

const taskStatuses = ref<Record<number, TaskStatus>>({})
let pollTimer: number | null = null

onMounted(() => {
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})

function startPolling() {
  pollTimer = window.setInterval(async () => {
    const ids = tableData.value.map(t => t.id)
    if (ids.length === 0) return

    const res = await fetch(`/api/scripts/status?ids=${ids.join(',')}`)
    const data = await res.json()
    taskStatuses.value = data.data
  }, 5000) // 5秒轮询
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function getStatusText(status: TaskStatus | undefined): string {
  if (!status) return '未知'
  switch (status.status) {
    case 'pending': return '○ 待执行'
    case 'running': return `⚡ 执行中 ${status.collectedCount || 0}条`
    case 'success': return `✓ 成功 ${status.lastCollectedCount || 0}条`
    case 'failed': return '⚠ 失败'
    case 'cancelled': return '⊘ 已取消'
    default: return status.status
  }
}

async function handleStop(row: any) {
  const executionId = taskStatuses.value[row.id]?.executionId
  if (executionId) {
    await fetch(`/api/scripts/${row.id}/executions/${executionId}/stop`, { method: 'POST' })
  }
}
</script>

<style scoped>
.status-pending { color: #999; }
.status-running { color: #1890ff; font-weight: bold; }
.status-success { color: #52c41a; }
.status-failed { color: #ff4d4f; }
.status-cancelled { color: #faad14; }
</style>
```

- [ ] **Step 7: 提交**

```bash
git add backend/src/main/java/com/scfx/service/TaskStatusService.java
git add backend/src/main/java/com/scfx/service/impl/TaskStatusServiceImpl.java
git add backend/src/main/java/com/scfx/controller/TaskStatusController.java
git add frontend/src/views/scripts/TaskList.vue
git commit -m "feat: add real-time task status monitoring"
```

---

### Task 2: Python 服务心跳监控 (2.2)

**Files:**
- Create: `backend/src/main/java/com/scfx/controller/HeartbeatStatusController.java`
- Modify: `frontend/src/components/ServiceStatus.vue`

- [ ] **Step 1: 创建 HeartbeatStatusController**

```java
package com.scfx.controller;

import com.scfx.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/collector/heartbeat")
@RequiredArgsConstructor
public class HeartbeatStatusController {
    private final StringRedisTemplate redisTemplate;

    private static final String HEARTBEAT_KEY = "heartbeat:python-collector";
    private static final long TIMEOUT_MS = 120000; // 2分钟超时

    @GetMapping("/status")
    public Result<Map<String, Object>> getHeartbeatStatus() {
        String lastHeartbeat = redisTemplate.opsForValue().get(HEARTBEAT_KEY);

        Map<String, Object> result = new HashMap<>();
        result.put("enabled", true);

        if (lastHeartbeat != null) {
            long timestamp = Long.parseLong(lastHeartbeat);
            long now = System.currentTimeMillis();
            long diff = now - timestamp;

            result.put("lastHeartbeat", timestamp);
            result.put("online", diff < TIMEOUT_MS);
            result.put("diffMs", diff);
        } else {
            result.put("lastHeartbeat", null);
            result.put("online", false);
            result.put("diffMs", null);
        }

        return Result.success(result);
    }

    @PostMapping("/refresh")
    public Result<Void> refreshStatus() {
        // 强制刷新：通过触发检查来更新状态
        return Result.success();
    }
}
```

- [ ] **Step 2: 创建 ServiceStatus.vue 组件**

```vue
<template>
  <div class="service-status" :class="{ offline: !isOnline }">
    <span class="status-icon">{{ isOnline ? '●' : '⚠' }}</span>
    <span class="status-text">{{ isOnline ? '采集服务正常' : '采集服务离线' }}</span>
    <el-button v-if="!isOnline" size="small" @click="handleReconnect">重新连接</el-button>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'

const isOnline = ref(true)
let pollTimer: number | null = null

const props = defineProps<{
  pollingInterval?: number
}>()

onMounted(() => {
  checkStatus()
  startPolling()
})

onUnmounted(() => {
  stopPolling()
})

function startPolling() {
  pollTimer = window.setInterval(() => {
    checkStatus()
  }, props.pollingInterval || 10000)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

async function checkStatus() {
  try {
    const res = await fetch('/api/collector/heartbeat/status')
    const data = await res.json()

    if (data.code === 200) {
      isOnline.value = data.data.online
    }
  } catch {
    isOnline.value = false
  }
}

async function handleReconnect() {
  try {
    await fetch('/api/collector/heartbeat/refresh', { method: 'POST' })
    await checkStatus()
    if (isOnline.value) {
      ElMessage.success('重新连接成功')
    }
  } catch {
    ElMessage.error('重新连接失败')
  }
}
</script>

<style scoped>
.service-status {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 12px;
  border-radius: 4px;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
}

.service-status.offline {
  background: #fff2e8;
  border-color: #ffbb96;
}

.status-icon {
  font-size: 16px;
}

.status-icon:not(.offline) .status-icon {
  color: #52c41a;
}

.status-icon.offline {
  color: #ff4d4f;
}

.status-text {
  font-size: 14px;
}
</style>
```

- [ ] **Step 3: 提交**

```bash
git add backend/src/main/java/com/scfx/controller/HeartbeatStatusController.java
git add frontend/src/components/ServiceStatus.vue
git commit -m "feat: add heartbeat monitoring and service status component"
```

---

### Task 3: 执行详情独立页面 (2.3)

**Files:**
- Create: `frontend/src/views/scripts/ExecutionDetail.vue`
- Modify: `frontend/src/router/index.ts`

- [ ] **Step 1: 创建 ExecutionDetail.vue**

```vue
<template>
  <div class="execution-detail">
    <div class="header">
      <el-button @click="goBack">← 返回任务列表</el-button>
      <h2>执行详情</h2>
      <span class="time">{{ execution?.startTime }}</span>
    </div>

    <div class="content">
      <!-- 基本信息 -->
      <el-card class="info-card">
        <template #header>基本信息</template>
        <div class="info-grid">
          <div class="info-item">
            <label>任务：</label>
            <span>{{ execution?.scriptName }}</span>
          </div>
          <div class="info-item">
            <label>数据源：</label>
            <span>{{ execution?.datasourceName }}</span>
          </div>
          <div class="info-item">
            <label>触发方式：</label>
            <span>{{ execution?.triggerType === 'scheduled' ? '定时' : '手动' }}</span>
          </div>
          <div class="info-item">
            <label>脚本版本：</label>
            <span>{{ execution?.scriptVersion }} ({{ execution?.scriptMd5 }})</span>
            <el-button size="small" @click="handleViewVersion">查看此版本</el-button>
            <el-button size="small" @click="handleCompareVersion">对比版本</el-button>
          </div>
          <div class="info-item">
            <label>执行状态：</label>
            <span :class="'status-' + execution?.status">{{ execution?.statusText }}</span>
          </div>
          <div class="info-item">
            <label>采集数量：</label>
            <span>{{ execution?.collectedCount }} 条</span>
          </div>
        </div>
      </el-card>

      <!-- 执行日志 -->
      <el-card class="log-card">
        <template #header>
          <span>执行日志</span>
          <div class="log-actions">
            <span v-if="isRunning" class="realtime">⚡ 实时</span>
            <el-button size="small" @click="handleDownloadLog">下载日志</el-button>
            <el-button size="small" @click="handleClearLog">清屏</el-button>
          </div>
        </template>
        <div class="log-container" ref="logContainer">
          <div v-for="(log, index) in logs" :key="index" class="log-item" :class="'level-' + log.level">
            <span class="log-time">[{{ log.timestamp }}]</span>
            <span class="log-level">{{ log.level }}</span>
            <span class="log-message">{{ log.message }}</span>
          </div>
        </div>
      </el-card>

      <!-- 操作按钮 -->
      <div class="actions">
        <el-button type="primary" @click="handleReExecute">重新执行</el-button>
        <el-button @click="handleCompareExecution">对比执行记录</el-button>
        <el-button @click="handleCompareScript">对比脚本版本</el-button>
        <el-button @click="handleCompareLog">对比执行日志</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { executionApi } from '@/api/execution'

const router = useRouter()
const route = useRoute()

const execution = ref<any>(null)
const logs = ref<any[]>([])
const logContainer = ref<HTMLElement>()

let pollTimer: number | null = null

const isRunning = computed(() => execution.value?.status === 'running')

onMounted(async () => {
  await loadExecution()
  startLogPolling()
})

onUnmounted(() => {
  stopLogPolling()
})

async function loadExecution() {
  const executionId = route.params.executionId
  const res = await executionApi.getById(executionId)
  execution.value = res.data
}

function startLogPolling() {
  pollTimer = window.setInterval(async () => {
    const executionId = route.params.executionId
    const res = await executionApi.getLogs(executionId, logs.value.length)
    if (res.data.logs.length > 0) {
      logs.value.push(...res.data.logs)
      await nextTick()
      scrollToBottom()
    }
    if (res.data.status !== 'running' && pollTimer) {
      clearInterval(pollTimer)
      pollTimer = null
    }
  }, 2000)
}

function stopLogPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function scrollToBottom() {
  if (logContainer.value) {
    logContainer.value.scrollTop = logContainer.value.scrollHeight
  }
}

function goBack() {
  router.push('/scripts')
}

function handleViewVersion() {
  // 跳转到数据源详情页的版本历史
  router.push(`/system/datasource/${execution.value.datasourceCode}?tab=versions`)
}

function handleCompareVersion() {
  // 打开版本对比弹窗
}

function handleDownloadLog() {
  const content = logs.value.map(l => `[${l.timestamp}] ${l.level} ${l.message}`).join('\n')
  const blob = new Blob([content], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `execution_${execution.value.executionId}_log.txt`
  a.click()
}

function handleClearLog() {
  logs.value = []
}

function handleReExecute() {
  // 重新执行
}

function handleCompareExecution() {
  // 打开执行记录对比弹窗
}

function handleCompareScript() {
  // 打开脚本版本对比弹窗
}

function handleCompareLog() {
  // 打开执行日志对比弹窗
}
</script>

<style scoped>
.execution-detail {
  padding: 20px;
}

.header {
  display: flex;
  align-items: center;
  gap: 20px;
  margin-bottom: 20px;
}

.header h2 {
  margin: 0;
}

.info-card, .log-card {
  margin-bottom: 20px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.info-item label {
  color: #999;
  min-width: 80px;
}

.log-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.realtime {
  color: #1890ff;
  font-weight: bold;
}

.log-container {
  height: 400px;
  overflow-y: auto;
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  font-family: 'Courier New', monospace;
  font-size: 13px;
}

.log-item {
  padding: 4px 0;
}

.log-time {
  color: #888;
}

.log-level {
  padding: 0 8px;
  font-weight: bold;
}

.level-INFO .log-level { color: #4ec9b0; }
.level-WARN .log-level { color: #dcdcaa; }
.level-ERROR .log-level { color: #f48771; }

.actions {
  display: flex;
  gap: 10px;
}

.status-success { color: #52c41a; }
.status-failed { color: #ff4d4f; }
.status-running { color: #1890ff; }
</style>
```

- [ ] **Step 2: 添加路由配置**

```typescript
// 在 router/index.ts 中添加
{
  path: '/scripts/:scriptId/executions/:executionId',
  name: 'ExecutionDetail',
  component: () => import('../views/scripts/ExecutionDetail.vue'),
  meta: { title: '执行详情' }
}
```

- [ ] **Step 3: 创建 execution API**

```typescript
// frontend/src/api/execution.ts
import request from './index'

export const executionApi = {
  getById: (executionId: string) =>
    request.get<any>(`/scripts/executions/${executionId}`),

  getLogs: (executionId: string, offset: number = 0) =>
    request.get<{ logs: any[]; status: string }>(`/scripts/executions/${executionId}/logs?offset=${offset}`),

  compare: (scriptId: number, executionId1: string, executionId2: string) =>
    request.get<any>(`/scripts/${scriptId}/executions/compare?executionId1=${executionId1}&executionId2=${executionId2}`)
}
```

- [ ] **Step 4: 提交**

```bash
git add frontend/src/views/scripts/ExecutionDetail.vue
git add frontend/src/api/execution.ts
git add frontend/src/router/index.ts
git commit -m "feat: add execution detail page"
```

---

### Task 4: 三种对比功能 (2.3 对比部分)

**Files:**
- Create: `frontend/src/views/scripts/components/ExecutionCompareDialog.vue`
- Create: `frontend/src/views/scripts/components/ScriptVersionCompareDialog.vue`
- Create: `frontend/src/views/scripts/components/ExecutionLogCompareDialog.vue`
- Create: `backend/src/main/java/com/scfx/controller/ExecutionCompareController.java`

- [ ] **Step 1: 创建 ExecutionCompareDialog.vue**

```vue
<template>
  <el-dialog v-model="visible" title="对比执行记录" width="800px">
    <div class="compare-container">
      <!-- 选择执行记录 -->
      <div class="select-area" v-if="!selected">
        <p>选择要对比的两条执行记录：</p>
        <el-radio-group v-model="leftId">
          <el-radio v-for="record in records" :key="record.executionId" :value="record.executionId">
            {{ formatDate(record.startTime) }} - {{ record.statusText }} - {{ record.collectedCount }}条
          </el-radio>
        </el-radio-group>
        <el-radio-group v-model="rightId" class="right-group">
          <el-radio v-for="record in records" :key="record.executionId" :value="record.executionId">
            {{ formatDate(record.startTime) }} - {{ record.statusText }} - {{ record.collectedCount }}条
          </el-radio>
        </el-radio-group>
      </div>

      <!-- 对比结果 -->
      <div class="compare-result" v-else>
        <div class="record-header">
          <div class="record left">
            <h3>{{ formatDate(result.record1.startTime) }}</h3>
            <span :class="'status-' + result.record1.status">{{ result.record1.statusText }}</span>
          </div>
          <div class="record right">
            <h3>{{ formatDate(result.record2.startTime) }}</h3>
            <span :class="'status-' + result.record2.status">{{ result.record2.statusText }}</span>
          </div>
        </div>

        <div class="record-body">
          <div class="metric">
            <label>采集数量</label>
            <div class="values">
              <span>{{ result.record1.collectedCount }} 条</span>
              <span class="diff" :class="result.changes.collectedCount.diff < 0 ? 'negative' : 'positive'">
                {{ result.changes.collectedCount.diff > 0 ? '+' : '' }}{{ result.changes.collectedCount.diff }} ({{ result.changes.collectedCount.percent }}%)
              </span>
              <span>{{ result.record2.collectedCount }} 条</span>
            </div>
          </div>
          <div class="metric">
            <label>执行耗时</label>
            <div class="values">
              <span>{{ result.record1.duration }} 秒</span>
              <span class="diff" :class="result.changes.duration.diff > 0 ? 'negative' : 'positive'">
                {{ result.changes.duration.diff > 0 ? '+' : '' }}{{ result.changes.duration.diff }} 秒
              </span>
              <span>{{ result.record2.duration }} 秒</span>
            </div>
          </div>
          <div class="metric">
            <label>脚本版本</label>
            <div class="values">
              <span>{{ result.record1.scriptVersion }}</span>
              <span class="vs">→</span>
              <span>{{ result.record2.scriptVersion }}</span>
            </div>
          </div>
        </div>

        <div class="changes-summary">
          <h4>变化分析</h4>
          <ul>
            <li v-if="result.changes.collectedCount.diff !== 0">
              采集数量{{ result.changes.collectedCount.diff < 0 ? '减少' : '增加' }}
              {{ Math.abs(result.changes.collectedCount.diff) }}条
            </li>
            <li v-if="result.changes.duration.diff !== 0">
              执行耗时{{ result.changes.duration.diff > 0 ? '增加' : '减少' }}
              {{ Math.abs(result.changes.duration.diff) }}秒
            </li>
          </ul>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button v-if="!selected" type="primary" @click="doCompare" :disabled="!leftId || !rightId">确认对比</el-button>
      <el-button v-else @click="selected = false">重新选择</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { executionApi } from '@/api/execution'

const props = defineProps<{
  modelValue: boolean
  scriptId: number
  currentExecutionId: string
}>()

const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
const records = ref<any[]>([])
const leftId = ref('')
const rightId = ref('')
const selected = ref(false)
const result = ref<any>(null)

watch(() => props.modelValue, async (val) => {
  visible.value = val
  if (val) {
    await loadRecords()
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

async function loadRecords() {
  // 加载执行记录列表
  const res = await executionApi.getList(props.scriptId)
  records.value = res.data.filter(r => r.executionId !== props.currentExecutionId).slice(0, 10)
}

async function doCompare() {
  const res = await executionApi.compare(props.scriptId, leftId.value, rightId.value)
  result.value = res.data
  selected.value = true
}

function formatDate(date: string): string {
  return new Date(date).toLocaleString()
}
</script>
```

- [ ] **Step 2: 创建 ScriptVersionCompareDialog.vue**

```vue
<template>
  <el-dialog v-model="visible" title="对比脚本版本" width="900px">
    <div class="compare-container">
      <!-- 选择版本 -->
      <div class="select-area" v-if="!selected">
        <p>选择要对比的两个版本：</p>
        <div class="version-list">
          <div class="version-group">
            <h4>版本 1</h4>
            <el-radio-group v-model="leftVersion">
              <el-radio v-for="v in versions" :key="v.version" :value="v.version">
                v{{ v.version }} ({{ formatDate(v.createdAt) }}) - 上传 by {{ v.createdBy }}
              </el-radio>
            </el-radio-group>
          </div>
          <div class="version-group">
            <h4>版本 2</h4>
            <el-radio-group v-model="rightVersion">
              <el-radio v-for="v in versions" :key="v.version" :value="v.version">
                v{{ v.version }} ({{ formatDate(v.createdAt) }}) - 上传 by {{ v.createdBy }}
              </el-radio>
            </el-radio-group>
          </div>
        </div>
      </div>

      <!-- 对比结果 -->
      <div class="compare-result" v-else>
        <div class="diff-header">
          <span>v{{ leftVersion }} vs v{{ rightVersion }}</span>
          <span class="diff-stats">
            新增 {{ result.stats.added }} 行，删除 {{ result.stats.deleted }} 行，修改 {{ result.stats.modified }} 行
          </span>
        </div>

        <div class="diff-view">
          <div class="diff-line" v-for="(line, index) in result.diff" :key="index" :class="'diff-' + line.type">
            <span class="line-number">{{ line.line }}</span>
            <span class="line-content">{{ line.content }}</span>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button v-if="!selected" type="primary" @click="doCompare" :disabled="!leftVersion || !rightVersion">确认对比</el-button>
      <el-button v-else @click="selected = false">重新选择</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { datasourceApi } from '@/api/datasource'

const props = defineProps<{
  modelValue: boolean
  datasourceCode: string
}>()

const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
const versions = ref<any[]>([])
const leftVersion = ref<number | null>(null)
const rightVersion = ref<number | null>(null)
const selected = ref(false)
const result = ref<any>(null)

watch(() => props.modelValue, async (val) => {
  visible.value = val
  if (val) {
    await loadVersions()
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

async function loadVersions() {
  const res = await datasourceApi.getVersions(props.datasourceCode)
  versions.value = res.data
  if (versions.value.length >= 2) {
    leftVersion.value = versions.value[0].version
    rightVersion.value = versions.value[1].version
  }
}

async function doCompare() {
  const res = await fetch(`/api/datasource/${props.datasourceCode}/versions/compare?v1=${leftVersion.value}&v2=${rightVersion.value}`)
  const data = await res.json()
  result.value = data.data
  selected.value = true
}

function formatDate(date: string): string {
  return new Date(date).toLocaleString()
}
</script>

<style scoped>
.version-list {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.version-group h4 {
  margin-bottom: 10px;
}

.diff-line {
  display: flex;
  font-family: 'Courier New', monospace;
  font-size: 13px;
}

.diff-add { background: #f6ffed; }
.diff-delete { background: #fff2f0; }
.diff-modify { background: #fffbe6; }

.line-number {
  width: 50px;
  padding: 0 8px;
  color: #999;
  text-align: right;
}

.line-content {
  flex: 1;
}
</style>
```

- [ ] **Step 3: 创建 ExecutionLogCompareDialog.vue**

```vue
<template>
  <el-dialog v-model="visible" title="对比执行日志" width="1000px">
    <div class="compare-container">
      <div class="compare-header">
        <span>本次: {{ currentTime }} vs 上次: {{ lastTime }}</span>
      </div>

      <div class="log-compare">
        <div class="log-column left">
          <div class="log-item" v-for="(item, index) in aligned" :key="'l-' + index" :class="{ diff: item.diff }">
            <span class="log-time">[{{ item.time1 }}]</span>
            <span class="log-level" :class="'level-' + item.level1">{{ item.level1 }}</span>
            <span class="log-msg">{{ item.msg1 }}</span>
            <span v-if="item.diff" class="diff-mark" :class="getDiffClass(item)">{{ getDiffMark(item) }}</span>
          </div>
        </div>
        <div class="log-column right">
          <div class="log-item" v-for="(item, index) in aligned" :key="'r-' + index" :class="{ diff: item.diff }">
            <span class="log-time">[{{ item.time2 }}]</span>
            <span class="log-level" :class="'level-' + item.level2">{{ item.level2 }}</span>
            <span class="log-msg">{{ item.msg2 }}</span>
          </div>
        </div>
      </div>

      <div class="diff-summary">
        <p>差异统计：相同 {{ stats.same }} 条，警告差异 {{ stats.warning }} 条，失败差异 {{ stats.error }} 条</p>
        <p v-if="firstDiff" class="first-diff">首次出现差异：{{ firstDiff.time1 }} [{{ firstDiff.msg1 }} vs {{ firstDiff.msg2 }}]</p>
      </div>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { executionApi } from '@/api/execution'

const props = defineProps<{
  modelValue: boolean
  currentExecutionId: string
}>()

const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
const aligned = ref<any[]>([])
const stats = ref({ same: 0, warning: 0, error: 0 })
const firstDiff = ref<any>(null)
const currentTime = ref('')
const lastTime = ref('')

watch(() => props.modelValue, async (val) => {
  visible.value = val
  if (val) {
    await loadComparison()
  }
})

watch(visible, (val) => {
  emit('update:modelValue', val)
})

async function loadComparison() {
  const res = await executionApi.compareLogs(props.currentExecutionId)
  const data = res.data
  aligned.value = data.aligned
  stats.value = data.stats
  firstDiff.value = aligned.value.find(l => l.diff && l.severity === 'error')
  currentTime.value = data.current.startTime
  lastTime.value = data.last.startTime
}

function getDiffClass(item: any): string {
  if (!item.diff) return ''
  return item.severity === 'error' ? 'error' : 'warning'
}

function getDiffMark(item: any): string {
  if (!item.diff) return '✓'
  return item.severity === 'error' ? '✗' : '⚠'
}
</script>

<style scoped>
.log-compare {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  max-height: 500px;
  overflow-y: auto;
}

.log-item {
  padding: 4px;
  display: flex;
  gap: 8px;
  align-items: center;
}

.log-item.diff {
  background: #fffbe6;
}

.diff-mark {
  margin-left: auto;
  font-weight: bold;
}

.diff-mark.error { color: #ff4d4f; }
.diff-mark.warning { color: #faad14; }
.diff-mark.same { color: #52c41a; }

.log-time { color: #888; }
.log-level { width: 50px; }
.level-INFO { color: #4ec9b0; }
.level-WARN { color: #dcdcaa; }
.level-ERROR { color: #f48771; }
</style>
```

- [ ] **Step 4: 提交**

```bash
git add frontend/src/views/scripts/components/ExecutionCompareDialog.vue
git add frontend/src/views/scripts/components/ScriptVersionCompareDialog.vue
git add frontend/src/views/scripts/components/ExecutionLogCompareDialog.vue
git commit -m "feat: add three compare dialogs"
```

---

### Task 5: Cron 表达式校验增强 (2.4)

**Files:**
- Create: `backend/src/main/java/com/scfx/util/CronDescriptionUtil.java`
- Modify: `backend/src/main/java/com/scfx/controller/CollectionScriptController.java`
- Create: `frontend/src/components/CronInput.vue`

- [ ] **Step 1: 创建 CronDescriptionUtil**

```java
package com.scfx.util;

import org.springframework.scheduling.support.CronExpression;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CronDescriptionUtil {

    /**
     * 解析 Cron 表达式返回人类可读描述
     */
    public static String describe(String cronExpression) {
        try {
            CronExpression.parse(cronExpression);

            // 简单判断 cron 格式
            String[] parts = cronExpression.split(" ");
            if (parts.length < 6) {
                return "无效的 Cron 表达式";
            }

            String second = parts[0];
            String minute = parts[1];
            String hour = parts[2];
            String dayOfMonth = parts[3];
            String month = parts[4];
            String dayOfWeek = parts[5];

            // 每天定点执行
            if ("0".equals(second) && !minute.contains(",") && !hour.contains(",")) {
                if ("*".equals(dayOfMonth) && "*".equals(month) && "*".equals(dayOfWeek)) {
                    return String.format("每天 %s:%s 执行", padLeft(hour, 2, '0'), padLeft(minute, 2, '0'));
                }
                // 每周定点
                if ("*".equals(dayOfMonth) && "*".equals(month) && !dayOfWeek.equals("?")) {
                    String dayName = getDayOfWeekName(dayOfWeek);
                    return String.format("每周%s %s:%s 执行", dayName, padLeft(hour, 2, '0'), padLeft(minute, 2, '0'));
                }
                // 每月定点
                if (!dayOfMonth.equals("*") && "*".equals(month) && "*".equals(dayOfWeek)) {
                    return String.format("每月第%s天 %s:%s 执行", dayOfMonth, padLeft(hour, 2, '0'), padLeft(minute, 2, '0'));
                }
            }

            // 工作日
            if (dayOfWeek.equals("MON-FRI")) {
                return String.format("工作日 %s:%s 执行", padLeft(hour, 2, '0'), padLeft(minute, 2, '0'));
            }

            return cronExpression;

        } catch (Exception e) {
            return "无效的 Cron 表达式";
        }
    }

    /**
     * 计算未来 N 次触发时间
     */
    public static List<String> calculateNextExecutions(String cronExpression, int count) {
        List<String> result = new ArrayList<>();
        try {
            CronExpression parsed = CronExpression.parse(cronExpression);
            ZonedDateTime now = ZonedDateTime.now();

            TemporalAccessor next = parsed.next(now);
            for (int i = 0; i < count && next != null; i++) {
                result.add(formatTemporal(next));
                next = parsed.next(next);
            }
        } catch (Exception e) {
            // 忽略
        }
        return result;
    }

    private static String formatTemporal(TemporalAccessor temporal) {
        int year = TemporalAccessor.class.isInstance(temporal) ? 0 : 0;
        // 简化实现，使用 toString 格式化
        return temporal.toString().replace("[", "").replace("]", "");
    }

    private static String getDayOfWeekName(String dayOfWeek) {
        switch (dayOfWeek) {
            case "MON": return "周一";
            case "TUE": return "周二";
            case "WED": return "周三";
            case "THU": return "周四";
            case "FRI": return "周五";
            case "SAT": return "周六";
            case "SUN": return "周日";
            case "1": return "周一";
            case "2": return "周二";
            case "3": return "周三";
            case "4": return "周四";
            case "5": return "周五";
            case "6": return "周六";
            case "7": return "周日";
            default: return dayOfWeek;
        }
    }

    private static String padLeft(String str, int len, char pad) {
        String s = str;
        while (s.length() < len) {
            s = pad + s;
        }
        return s;
    }
}
```

- [ ] **Step 2: 修改 CollectionScriptController 添加校验接口**

```java
// 在 CollectionScriptController 中添加

@PostMapping("/validate-cron")
public Result<Map<String, Object>> validateCron(@RequestBody Map<String, String> request) {
    String cron = request.get("cron");
    Map<String, Object> result = new HashMap<>();

    try {
        CronExpression.parse(cron);
        result.put("valid", true);
        result.put("description", CronDescriptionUtil.describe(cron));

        List<String> nextExecutions = CronDescriptionUtil.calculateNextExecutions(cron, 5);
        result.put("nextExecutions", nextExecutions);

    } catch (Exception e) {
        result.put("valid", false);
        result.put("error", e.getMessage());
    }

    return Result.success(result);
}
```

- [ ] **Step 3: 创建 CronInput.vue 组件**

```vue
<template>
  <div class="cron-input">
    <div class="quick-select">
      <el-select v-model="selectedTemplate" placeholder="快速选择" clearable @change="handleTemplateChange">
        <el-option label="每天9:30" value="0 30 9 * * ?" />
        <el-option label="每天18:00" value="0 0 18 * * ?" />
        <el-option label="每周一9:00" value="0 0 9 ? * MON" />
        <el-option label="每月1日9:00" value="0 0 9 1 * ?" />
        <el-option label="工作日9:30" value="0 30 9 * * MON-FRI" />
      </el-select>
    </div>

    <div class="cron-input-wrapper">
      <el-input
        v-model="cronExpression"
        placeholder="0 30 9 * * ?"
        @input="handleCronInput"
        @blur="handleCronBlur"
      />
      <el-button @click="handleValidate">校验</el-button>
    </div>

    <div class="validation-result" v-if="validationResult">
      <div v-if="validationResult.valid" class="valid">
        <span class="icon">✓</span>
        <span class="description">{{ validationResult.description }}</span>
        <div class="next-executions">
          <p>未来5次触发时间：</p>
          <ul>
            <li v-for="(time, index) in validationResult.nextExecutions" :key="index">{{ time }}</li>
          </ul>
        </div>
      </div>
      <div v-else class="invalid">
        <span class="icon">✗</span>
        <span class="error">{{ validationResult.error }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { scriptApi } from '@/api/scripts'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits(['update:modelValue'])

const cronExpression = ref(props.modelValue)
const selectedTemplate = ref('')
const validationResult = ref<any>(null)
let debounceTimer: number | null = null

watch(() => props.modelValue, (val) => {
  cronExpression.value = val
})

function handleTemplateChange(template: string) {
  if (template) {
    cronExpression.value = template
    emit('update:modelValue', template)
    validateCron()
  }
}

function handleCronInput() {
  // 防抖 300ms
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = window.setTimeout(() => {
    emit('update:modelValue', cronExpression.value)
    validateCron()
  }, 300)
}

function handleCronBlur() {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
    emit('update:modelValue', cronExpression.value)
    validateCron()
  }
}

async function handleValidate() {
  await validateCron()
}

async function validateCron() {
  if (!cronExpression.value) {
    validationResult.value = null
    return
  }

  try {
    const res = await scriptApi.validateCron(cronExpression.value)
    validationResult.value = res.data
  } catch {
    validationResult.value = { valid: false, error: '校验失败' }
  }
}
</script>

<style scoped>
.cron-input {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.quick-select {
  margin-bottom: 10px;
}

.cron-input-wrapper {
  display: flex;
  gap: 10px;
}

.validation-result {
  padding: 10px;
  border-radius: 4px;
}

.validation-result.valid {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
}

.validation-result.invalid {
  background: #fff2f0;
  border: 1px solid #ffbb96;
}

.icon {
  font-weight: bold;
  margin-right: 8px;
}

.valid .icon { color: #52c41a; }
.invalid .icon { color: #ff4d4f; }

.next-executions ul {
  margin: 5px 0 0 20px;
}
</style>
```

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/java/com/scfx/util/CronDescriptionUtil.java
git add backend/src/main/java/com/scfx/controller/CollectionScriptController.java
git add frontend/src/components/CronInput.vue
git commit -m "feat: add Cron validation with description and next executions"
```

---

### Task 6: 脚本版本与执行记录关联 (2.5)

**Files:**
- Modify: `backend/src/main/java/com/scfx/entity/TaskExecution.java`
- Modify: `backend/src/main/java/com/scfx/service/TaskExecutionService.java`

- [ ] **Step 1: 修改 TaskExecution 添加字段**

```java
// 在 TaskExecution.java 中添加
private Integer scriptVersion;    // 脚本版本号
private String scriptMd5;        // 脚本MD5
```

- [ ] **Step 2: 修改 TaskExecutionService 保存时记录版本**

```java
// 在 completeExecution 或 saveExecution 方法中添加
public void completeExecution(String executionId, String status, int collectedCount) {
    TaskExecution execution = findByExecutionId(executionId);
    if (execution == null) return;

    // 获取当前脚本版本
    ScriptVersion currentVersion = scriptVersionService.getCurrentVersion(
        execution.getScriptId()
    );
    if (currentVersion != null) {
        execution.setScriptVersion(currentVersion.getVersion());
        execution.setScriptMd5(currentVersion.getFileMd5());
    }

    execution.setStatus(status);
    execution.setEndTime(LocalDateTime.now());
    execution.setCollectedCount(collectedCount);

    taskExecutionMapper.updateById(execution);
}
```

- [ ] **Step 3: 提交**

```bash
git add backend/src/main/java/com/scfx/entity/TaskExecution.java
git add backend/src/main/java/com/scfx/service/TaskExecutionService.java
git commit -m "feat: add script version tracking in execution records"
```

---

### Task 7: 操作日志和审计 (2.6)

**Files:**
- Create: `backend/src/main/java/com/scfx/annotation/OperationLog.java`
- Create: `backend/src/main/java/com/scfx/entity/OperationLog.java`
- Create: `backend/src/main/java/com/scfx/mapper/OperationLogMapper.java`
- Create: `backend/src/main/java/com/scfx/service/OperationLogService.java`
- Create: `backend/src/main/java/com/scfx/service/impl/OperationLogServiceImpl.java`
- Create: `backend/src/main/java/com/scfx/aspect/OperationLogAspect.java`
- Create: `backend/src/main/java/com/scfx/controller/OperationLogController.java`

- [ ] **Step 1: 创建 @OperationLog 注解**

```java
package com.scfx.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {
    String type();       // 操作类型: CREATE/UPDATE/DELETE/EXECUTE/UPLOAD/ROLLBACK
    String targetType(); // 目标类型: SCRIPT/DATASOURCE/TASK
}
```

- [ ] **Step 2: 创建 OperationLog 实体**

```java
package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_operation_log")
public class OperationLog {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String operator;         // 操作人
    private String operationType;     // 操作类型
    private String targetType;       // 目标类型
    private Long targetId;           // 目标ID
    private String detail;          // 操作详情(JSON)
    private String ip;              // IP地址

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime operateTime;
}
```

- [ ] **Step 3: 创建 OperationLogMapper**

```java
package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.OperationLog;
import org.apache.ibatis.annotations.*;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {

    @Select("SELECT * FROM t_operation_log WHERE target_type = #{targetType} AND target_id = #{targetId} ORDER BY operate_time DESC")
    List<OperationLog> findByTarget(@Param("targetType") String targetType, @Param("targetId") Long targetId);

    @Select("SELECT * FROM t_operation_log ORDER BY operate_time DESC LIMIT #{offset}, #{size}")
    List<OperationLog> findPage(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM t_operation_log")
    long count();

    @Delete("DELETE FROM t_operation_log WHERE operate_time < DATE_SUB(NOW(), INTERVAL 90 DAY)")
    int deleteOlderThan90Days();
}
```

- [ ] **Step 4: 创建 OperationLogService**

```java
package com.scfx.service;

import com.scfx.entity.OperationLog;
import java.util.List;
import java.util.Map;

public interface OperationLogService {
    void log(String operator, String operationType, String targetType, Long targetId, Object detail);

    List<OperationLog> findByTarget(String targetType, Long targetId);

    Map<String, Object> findPage(int page, int size);

    void cleanup();
}
```

- [ ] **Step 5: 创建 OperationLogServiceImpl**

```java
package com.scfx.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scfx.entity.OperationLog;
import com.scfx.mapper.OperationLogMapper;
import com.scfx.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {
    private final OperationLogMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void log(String operator, String operationType, String targetType, Long targetId, Object detail) {
        OperationLog log = new OperationLog();
        log.setOperator(operator);
        log.setOperationType(operationType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setIp(getCurrentIp());

        try {
            log.setDetail(objectMapper.writeValueAsString(detail));
        } catch (Exception e) {
            log.setDetail("{}");
        }

        mapper.insert(log);
    }

    @Override
    public List<OperationLog> findByTarget(String targetType, Long targetId) {
        return mapper.findByTarget(targetType, targetId);
    }

    @Override
    public Map<String, Object> findPage(int page, int size) {
        int offset = (page - 1) * size;
        List<OperationLog> records = mapper.findPage(offset, size);
        long total = mapper.count();

        return Map.of(
            "records", records,
            "total", total,
            "page", page,
            "size", size
        );
    }

    @Override
    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨3点清理
    public void cleanup() {
        mapper.deleteOlderThan90Days();
    }

    private String getCurrentIp() {
        // 简化实现，实际应从请求上下文获取
        return "127.0.0.1";
    }
}
```

- [ ] **Step 6: 创建 OperationLogAspect**

```java
package com.scfx.aspect;

import com.scfx.annotation.OperationLog;
import com.scfx.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {
    private final OperationLogService operationLogService;

    @Pointcut("@annotation(com.scfx.annotation.OperationLog)")
    public void operationLogPointcut() {}

    @AfterReturning(pointcut = "operationLogPointcut()", returning = "result")
    public void afterReturning(JoinPoint joinPoint, Object result) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OperationLog annotation = method.getAnnotation(OperationLog.class);

        if (annotation == null) return;

        String operator = getCurrentUser();
        String operationType = annotation.type();
        String targetType = annotation.targetType();
        Long targetId = extractTargetId(joinPoint.getArgs());

        operationLogService.log(operator, operationType, targetType, targetId, joinPoint.getArgs());
    }

    private String getCurrentUser() {
        // 简化实现，实际应从安全上下文获取
        return "admin";
    }

    private Long extractTargetId(Object[] args) {
        if (args == null || args.length == 0) return null;
        // 简化：通常第一个参数是 ID 或包含 ID 的对象
        for (Object arg : args) {
            if (arg instanceof Long) return (Long) arg;
            if (arg instanceof String) {
                try {
                    return Long.parseLong((String) arg);
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
}
```

- [ ] **Step 7: 创建 OperationLogController**

```java
package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {
    private final OperationLogService operationLogService;

    @GetMapping
    public Result<Map<String, Object>> findPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(operationLogService.findPage(page, size));
    }
}
```

- [ ] **Step 8: 提交**

```bash
git add backend/src/main/java/com/scfx/annotation/OperationLog.java
git add backend/src/main/java/com/scfx/entity/OperationLog.java
git add backend/src/main/java/com/scfx/mapper/OperationLogMapper.java
git add backend/src/main/java/com/scfx/service/OperationLogService.java
git add backend/src/main/java/com/scfx/service/impl/OperationLogServiceImpl.java
git add backend/src/main/java/com/scfx/aspect/OperationLogAspect.java
git add backend/src/main/java/com/scfx/controller/OperationLogController.java
git commit -m "feat: add operation log and audit system"
```

---

### Task 8: 告警通知 (2.7)

**Files:**
- Create: `backend/src/main/java/com/scfx/entity/AlertRule.java`
- Create: `backend/src/main/java/com/scfx/entity/AlertRecord.java`
- Create: `backend/src/main/java/com/scfx/mapper/AlertRuleMapper.java`
- Create: `backend/src/main/java/com/scfx/mapper/AlertRecordMapper.java`
- Create: `backend/src/main/java/com/scfx/service/AlertService.java`
- Create: `backend/src/main/java/com/scfx/service/impl/AlertServiceImpl.java`
- Create: `backend/src/main/java/com/scfx/controller/AlertController.java`

- [ ] **Step 1: 创建 AlertRule 实体**

```java
package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_alert_rule")
public class AlertRule {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String ruleName;
    private String ruleType;          // CONTINUOUS_FAIL/SERVICE_OFFLINE/ZERO_RESULT/TIMEOUT
    private String condition;         // JSON: { threshold: 3 }
    private Integer enabled;         // 1=启用, 0=禁用
    private String notifyChannels;   // JSON: ["dingtalk", "email"]
    private String notifyTarget;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 创建 AlertRecord 实体**

```java
package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_alert_record")
public class AlertRecord {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long ruleId;
    private Long scriptId;
    private String alertType;
    private String message;
    private String status;           // SENT/FAILED/READ
    private String errorInfo;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
```

- [ ] **Step 3: 创建 AlertServiceImpl（核心告警逻辑）**

```java
package com.scfx.service.impl;

import com.scfx.entity.AlertRule;
import com.scfx.entity.AlertRecord;
import com.scfx.entity.Script;
import com.scfx.mapper.AlertRuleMapper;
import com.scfx.mapper.AlertRecordMapper;
import com.scfx.service.AlertService;
import com.scfx.service.ScriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {
    private final AlertRuleMapper alertRuleMapper;
    private final AlertRecordMapper alertRecordMapper;
    private final ScriptService scriptService;
    private final StringRedisTemplate redisTemplate;

    private static final String HEARTBEAT_KEY = "heartbeat:python-collector";

    @Override
    @Scheduled(fixedRate = 60000) // 每分钟检查
    public void checkAndAlert() {
        checkContinuousFailures();
        checkServiceOffline();
    }

    private void checkContinuousFailures() {
        // 获取连续失败告警规则
        AlertRule rule = alertRuleMapper.findByType("CONTINUOUS_FAIL");
        if (rule == null || rule.getEnabled() == 0) return;

        int threshold = parseThreshold(rule.getCondition());

        // 获取连续失败次数 >= threshold 的脚本
        List<Script> failedScripts = scriptService.getContinuouslyFailedScripts(threshold);

        for (Script script : failedScripts) {
            sendAlert(rule, script.getId(), "连续执行失败",
                String.format("脚本 %s 连续%d次执行失败", script.getName(), threshold));
        }
    }

    private void checkServiceOffline() {
        AlertRule rule = alertRuleMapper.findByType("SERVICE_OFFLINE");
        if (rule == null || rule.getEnabled() == 0) return;

        String lastHeartbeat = redisTemplate.opsForValue().get(HEARTBEAT_KEY);
        if (lastHeartbeat == null) {
            sendAlert(rule, null, "采集服务离线", "采集服务心跳超时，请检查服务状态");
            return;
        }

        long diff = System.currentTimeMillis() - Long.parseLong(lastHeartbeat);
        if (diff > 120000) { // 2分钟
            sendAlert(rule, null, "采集服务离线",
                String.format("采集服务心跳超时（%d秒无响应）", diff / 1000));
        }
    }

    private void sendAlert(AlertRule rule, Long scriptId, String alertType, String message) {
        // 检查是否已发送（避免重复）
        if (hasRecentAlert(rule.getId(), scriptId)) return;

        AlertRecord record = new AlertRecord();
        record.setRuleId(rule.getId());
        record.setScriptId(scriptId);
        record.setAlertType(alertType);
        record.setMessage(message);
        record.setStatus("SENT");

        try {
            // 发送告警（根据渠道）
            List<String> channels = parseChannels(rule.getNotifyChannels());
            for (String channel : channels) {
                if ("dingtalk".equals(channel)) {
                    sendDingTalkAlert(rule.getNotifyTarget(), message);
                } else if ("email".equals(channel)) {
                    sendEmailAlert(rule.getNotifyTarget(), message);
                }
            }
        } catch (Exception e) {
            record.setStatus("FAILED");
            record.setErrorInfo(e.getMessage());
        }

        alertRecordMapper.insert(record);
    }

    private boolean hasRecentAlert(Long ruleId, Long scriptId) {
        // 检查最近30分钟是否已发送
        AlertRecord recent = alertRecordMapper.findRecent(ruleId, scriptId, 30);
        return recent != null;
    }

    private int parseThreshold(String condition) {
        try {
            return Integer.parseInt(condition.replaceAll("[^0-9]", ""));
        } catch {
            return 3; // 默认阈值
        }
    }

    private List<String> parseChannels(String channelsJson) {
        // 简化解析
        return List.of(channelsJson.replace("[", "").replace("]", "").replace("\"", "").split(","));
    }

    private void sendDingTalkAlert(String webhook, String message) {
        // 实现钉钉通知发送
    }

    private void sendEmailAlert(String to, String message) {
        // 实现邮件通知发送
    }
}
```

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/java/com/scfx/entity/AlertRule.java
git add backend/src/main/java/com/scfx/entity/AlertRecord.java
git add backend/src/main/java/com/scfx/service/AlertService.java
git add backend/src/main/java/com/scfx/service/impl/AlertServiceImpl.java
git add backend/src/main/java/com/scfx/controller/AlertController.java
git commit -m "feat: add alert notification system"
```

---

### Task 9: 数据源关联任务视图 (2.8)

**Files:**
- Modify: `frontend/src/views/system/DataSourceDetail.vue`

- [ ] **Step 1: 修改 DataSourceDetail.vue**

```vue
<template>
  <div class="datasource-detail">
    <el-tabs v-model="activeTab">
      <el-tab-pane label="基本信息" name="info">
        <!-- 现有基本信息表单 -->
      </el-tab-pane>

      <el-tab-pane label="关联任务" name="tasks">
        <div class="tasks-header">
          <h3>关联任务（{{ tasks.length }}个）</h3>
          <el-select v-model="sortBy" placeholder="排序" style="width: 150px">
            <el-option label="按最后执行时间" value="lastExecuted" />
            <el-option label="按状态" value="status" />
            <el-option label="按累计采集" value="totalCollected" />
          </el-select>
        </div>

        <el-table :data="sortedTasks" stripe>
          <el-table-column prop="name" label="任务名称" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <span :class="'status-' + row.status">{{ row.statusText }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最后执行" width="180">
            <template #default="{ row }">
              {{ row.lastExecuted ? formatDate(row.lastExecuted) : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="累计采集" width="120">
            <template #default="{ row }">
              {{ row.totalCollected }} 条
            </template>
          </el-table-column>
          <el-table-column label="累计执行" width="120">
            <template #default="{ row }">
              {{ row.totalExecutions }} 次
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button size="small" @click="goToTask(row)">查看任务</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="版本历史" name="versions">
        <div class="versions-header">
          <h3>脚本版本</h3>
          <el-button size="small" @click="handleViewVersions">查看版本历史</el-button>
        </div>

        <div class="current-version" v-if="currentVersion">
          <p>当前版本：v{{ currentVersion.version }} ({{ formatDate(currentVersion.createdAt) }})</p>
          <p>MD5: {{ currentVersion.fileMd5 }}</p>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { datasourceApi } from '@/api/datasource'
import { scriptApi } from '@/api/scripts'

const router = useRouter()

const activeTab = ref('info')
const tasks = ref<any[]>([])
const sortBy = ref('lastExecuted')
const currentVersion = ref<any>(null)

const sortedTasks = computed(() => {
  const sorted = [...tasks.value]
  switch (sortBy.value) {
    case 'lastExecuted':
      return sorted.sort((a, b) => (b.lastExecuted || '').localeCompare(a.lastExecuted || ''))
    case 'status':
      const statusOrder = { failed: 0, running: 1, success: 2, pending: 3 }
      return sorted.sort((a, b) => statusOrder[a.status] - statusOrder[b.status])
    case 'totalCollected':
      return sorted.sort((a, b) => b.totalCollected - a.totalCollected)
    default:
      return sorted
  }
})

onMounted(async () => {
  await loadTasks()
  await loadCurrentVersion()
})

async function loadTasks() {
  const code = route.params.code
  const res = await scriptApi.getByDatasource(code)
  tasks.value = res.data
}

async function loadCurrentVersion() {
  const code = route.params.code
  const res = await datasourceApi.getVersions(code)
  currentVersion.value = res.data.find((v: any) => v.isCurrent === 1)
}

function formatDate(date: string): string {
  return new Date(date).toLocaleString()
}

function goToTask(task: any) {
  router.push(`/scripts/${task.id}`)
}

function handleViewVersions() {
  // 打开版本历史弹窗
}
</script>

<style scoped>
.tasks-header, .versions-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.current-version {
  padding: 16px;
  background: #f6ffed;
  border: 1px solid #b7eb8f;
  border-radius: 4px;
}

.status-success { color: #52c41a; }
.status-failed { color: #ff4d4f; }
.status-running { color: #1890ff; }
.status-pending { color: #999; }
</style>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/src/views/system/DataSourceDetail.vue
git commit -m "feat: add datasource linked tasks view"
```

---

## 三、数据库变更

```sql
-- 操作日志表
CREATE TABLE t_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator VARCHAR(100) NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT NOT NULL,
    detail JSON,
    ip VARCHAR(50),
    operate_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_target (target_type, target_id),
    INDEX idx_operator_time (operator, operate_time)
);

-- 告警规则表
CREATE TABLE t_alert_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name VARCHAR(100),
    rule_type VARCHAR(50),
    condition JSON,
    enabled TINYINT(1) DEFAULT 1,
    notify_channels JSON,
    notify_target VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 告警记录表
CREATE TABLE t_alert_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id BIGINT,
    script_id BIGINT,
    alert_type VARCHAR(50),
    message TEXT,
    status VARCHAR(20),
    error_info TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- t_task_execution 新增字段
ALTER TABLE t_task_execution ADD COLUMN script_version INT;
ALTER TABLE t_task_execution ADD COLUMN script_md5 VARCHAR(32);
```

---

## 四、优先级

| Task | 名称 | 优先级 |
|------|------|--------|
| Task 1 | 任务列表实时状态监控 | P0 |
| Task 2 | Python 服务心跳监控 | P0 |
| Task 3 | 执行详情独立页面 | P0 |
| Task 4 | 三种对比功能 | P1 |
| Task 5 | Cron 表达式校验增强 | P1 |
| Task 6 | 脚本版本与执行记录关联 | P1 |
| Task 7 | 操作日志和审计 | P1 |
| Task 8 | 告警通知 | P2 |
| Task 9 | 数据源关联任务视图 | P2 |

---

## 五、验证方案

1. **任务列表实时状态**
   - 启动执行后，列表状态自动更新（5秒轮询）
   - 点击停止按钮，执行状态变为 cancelled

2. **服务心跳监控**
   - 停止 Python 服务，前端显示异常状态
   - 恢复 Python 服务，前端显示正常
   - 点击重新连接按钮强制刷新

3. **执行详情页面**
   - 从任务列表进入执行详情页
   - 日志实时刷新
   - 可下载日志

4. **对比功能**
   - 执行记录对比：选择两条记录对比
   - 脚本版本对比：选择两个版本对比
   - 执行日志对比：并排显示差异

5. **Cron 校验**
   - 选择预设模板自动填充
   - 输入自定义表达式实时校验
   - 显示描述和未来5次触发时间