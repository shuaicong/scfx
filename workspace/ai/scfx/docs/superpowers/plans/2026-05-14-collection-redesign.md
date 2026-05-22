# 采集管理页面重构实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 统一采集管理页面入口，简化数据模型，提升用户体验

**Architecture:** 以 CollectionScript 为核心实体，废弃 CollectionTask。前端整合 TaskList.vue 与 Collection.vue，移除 /collection 路由。

**Tech Stack:** Vue 3 + Element Plus + Monaco Editor + Spring Boot + MyBatis-Plus

---

## 文件结构分析

### 前端改动
| 文件 | 责任 |
|------|------|
| `frontend/src/router/index.ts` | 路由统一，/collection 重定向到 /scripts |
| `frontend/src/views/scripts/TaskList.vue` | 整合后的唯一入口页面 |
| `frontend/src/views/scripts/ScriptEditor.vue` | 已存在，Monaco编辑器组件 |
| `frontend/src/views/collection/Collection.vue` | 废弃 |
| `frontend/src/views/collection/CollectionProgress.vue` | 进度抽屉组件 |
| `frontend/src/api/scripts.ts` | 脚本管理 API（新建或扩展） |

### 后端改动
| 文件 | 责任 |
|------|------|
| `backend/.../entity/CollectionTask.java` | 删除 |
| `backend/.../entity/CollectionScript.java` | 保留，作为核心实体 |
| `backend/.../controller/CollectionController.java` | 清理 CollectionTask 引用 |
| `backend/.../controller/ScriptController.java` | 新建，脚本 CRUD |
| `backend/.../service/CollectionTaskService.java` | 删除 |
| `backend/.../service/ScriptService.java` | 新建，脚本业务逻辑 |

---

## Task 1: 后端 - 清理 CollectionTask 相关代码

**Files:**
- Delete: `backend/src/main/java/com/scfx/entity/CollectionTask.java`
- Delete: `backend/src/main/java/com/scfx/service/CollectionTaskService.java`
- Modify: `backend/src/main/java/com/scfx/controller/CollectionController.java` - 移除 taskService 引用
- Modify: `backend/src/main/java/com/scfx/controller/TaskController.java` - 重命名为 ScriptController 或删除
- Modify: `backend/src/main/java/com/scfx/mapper/CollectionTaskMapper.java` - 删除

---

## Task 2: 后端 - 创建 ScriptService

**Files:**
- Create: `backend/src/main/java/com/scfx/service/ScriptService.java`

```java
package com.scfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.CollectionScript;
import com.scfx.mapper.CollectionScriptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptService {

    private final CollectionScriptMapper scriptMapper;

    /**
     * 分页查询脚本
     */
    public Result<Page<CollectionScript>> getScripts(int page, int size, String status, String source, String triggerType, String keyword) {
        Page<CollectionScript> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<CollectionScript> wrapper = new LambdaQueryWrapper<>();

        if (status != null && !status.isEmpty()) {
            wrapper.eq(CollectionScript::getStatus, status);
        }
        if (source != null && !source.isEmpty()) {
            wrapper.eq(CollectionScript::getSource, source);
        }
        if (triggerType != null && !triggerType.isEmpty()) {
            wrapper.eq(CollectionScript::getTriggerType, triggerType);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(CollectionScript::getScriptName, keyword);
        }

        wrapper.orderByDesc(CollectionScript::getUpdatedAt);
        Page<CollectionScript> result = scriptMapper.selectPage(pageInfo, wrapper);
        return Result.success(result);
    }

    /**
     * 获取脚本详情
     */
    public Result<CollectionScript> getScriptById(Long id) {
        CollectionScript script = scriptMapper.selectById(id);
        if (script == null) {
            return Result.error("脚本不存在");
        }
        return Result.success(script);
    }

    /**
     * 新建脚本
     */
    public Result<CollectionScript> createScript(CollectionScript script) {
        script.setCreatedAt(LocalDateTime.now());
        script.setUpdatedAt(LocalDateTime.now());
        script.setStatus("enabled");
        script.setExecutionCount(0);
        script.setSuccessCount(0);
        script.setFailedCount(0);
        script.setCurrentVersion(1);
        scriptMapper.insert(script);
        log.info("创建脚本: {}", script.getScriptName());
        return Result.success(script);
    }

    /**
     * 更新脚本
     */
    public Result<CollectionScript> updateScript(Long id, CollectionScript script) {
        CollectionScript existing = scriptMapper.selectById(id);
        if (existing == null) {
            return Result.error("脚本不存在");
        }
        script.setId(id);
        script.setUpdatedAt(LocalDateTime.now());
        scriptMapper.updateById(script);
        return Result.success(script);
    }

    /**
     * 删除脚本
     */
    public Result<Void> deleteScript(Long id) {
        scriptMapper.deleteById(id);
        return Result.success();
    }

    /**
     * 获取统计信息
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", scriptMapper.selectCount(null));

        LambdaQueryWrapper<CollectionScript> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectionScript::getStatus, "enabled");
        stats.put("enabled", scriptMapper.selectCount(wrapper));

        wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectionScript::getStatus, "disabled");
        stats.put("disabled", scriptMapper.selectCount(wrapper));

        // 今日执行统计...
        return stats;
    }

    /**
     * 获取所有脚本（不分页）
     */
    public List<CollectionScript> getAllScripts() {
        return scriptMapper.selectList(null);
    }
}
```

---

## Task 3: 后端 - 创建/完善 ScriptController

**Files:**
- Create: `backend/src/main/java/com/scfx/controller/ScriptController.java`

```java
package com.scfx.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.CollectionScript;
import com.scfx.service.ScriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/scripts")
@RequiredArgsConstructor
public class ScriptController {

    private final ScriptService scriptService;

    @GetMapping
    public Result<Page<CollectionScript>> getScripts(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String triggerType,
            @RequestParam(required = false) String keyword) {
        return scriptService.getScripts(page, size, status, source, triggerType, keyword);
    }

    @GetMapping("/{id}")
    public Result<CollectionScript> getScript(@PathVariable Long id) {
        return scriptService.getScriptById(id);
    }

    @PostMapping
    public Result<CollectionScript> createScript(@RequestBody CollectionScript script) {
        return scriptService.createScript(script);
    }

    @PutMapping("/{id}")
    public Result<CollectionScript> updateScript(@PathVariable Long id, @RequestBody CollectionScript script) {
        return scriptService.updateScript(id, script);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteScript(@PathVariable Long id) {
        return scriptService.deleteScript(id);
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        return Result.success(scriptService.getStats());
    }

    @PostMapping("/{id}/execute")
    public Result<Map<String, Object>> executeScript(@PathVariable Long id) {
        // 调用采集执行逻辑，创建执行记录
        return scriptService.executeScript(id);
    }
}
```

---

## Task 4: 前端 - 更新路由配置

**Files:**
- Modify: `frontend/src/router/index.ts`

```typescript
{
  path: '/collection',
  redirect: '/scripts'  // 重定向到 /scripts
}
```

---

## Task 5: 前端 - 创建脚本管理 API 模块

**Files:**
- Create: `frontend/src/api/scripts.ts`

```typescript
import request from './index'

export const getScripts = (params: any) => request.get('/scripts', { params })

export const getScriptById = (id: number) => request.get(`/scripts/${id}`)

export const createScript = (data: any) => request.post('/scripts', data)

export const updateScript = (id: number, data: any) => request.put(`/scripts/${id}`, data)

export const deleteScript = (id: number) => request.delete(`/scripts/${id}`)

export const getScriptStats = () => request.get('/scripts/stats')

export const executeScript = (id: number) => request.post(`/scripts/${id}/execute`)
```

---

## Task 6: 前端 - 重构 TaskList.vue 页面

**Files:**
- Modify: `frontend/src/views/scripts/TaskList.vue`

整合内容：
1. 保留现有的统计卡片和数据表格
2. 添加新建脚本按钮 -> 打开抽屉
3. 添加编辑按钮 -> 打开抽屉
4. 添加详情按钮 -> 打开弹窗
5. 添加删除按钮 -> 确认弹窗
6. 执行按钮 -> 调用 executeScript -> 打开进度抽屉

抽屉编辑包含：
- ScriptEditor 组件（Monaco编辑器）
- 触发方式选择（manual/single/cron/repeat）
- Cron表达式输入（图形化辅助）
- 文件上传功能

---

## Task 7: 前端 - 创建抽屉编辑组件

**Files:**
- Create: `frontend/src/views/scripts/components/ScriptEditDrawer.vue`

包含：
- el-drawer 抽屉
- el-form 表单
- ScriptEditor Monaco编辑器
- 文件上传功能
- 触发方式配置表单
- 保存/取消按钮

---

## Task 8: 前端 - 脚本详情弹窗

**Files:**
- Create: `frontend/src/views/scripts/components/ScriptDetailDialog.vue`

包含：
- el-dialog 弹窗
- el-descriptions 详情展示
- 脚本内容只读展示
- 部分字段编辑功能

---

## Task 9: 前端 - 完善执行记录 Tab

**Files:**
- Modify: `frontend/src/views/scripts/TaskList.vue` - 添加执行记录 Tab

```typescript
// 执行记录 Tab
const executionList = ref([])
const executionLoading = ref(false)

const fetchExecutions = async () => {
  executionLoading.value = true
  try {
    const res = await getExecutions()
    executionList.value = res.data
  } finally {
    executionLoading.value = false
  }
}
```

---

## Task 10: 前端 - 完善进度抽屉

**Files:**
- Modify: `frontend/src/views/scripts/TaskList.vue` - 调用 CollectionProgress

```typescript
import CollectionProgress from '../collection/CollectionProgress.vue'

// 执行按钮
const executeScript = async (row) => {
  const res = await executeScriptApi(row.id)
  progressDrawer.value?.open(res.data.executionId)
}
```

---

## Task 11: 知识库页面 - 添加向量化监控入口

**Files:**
- Modify: `frontend/src/views/knowledge/Knowledge.vue` - 添加向量化监控 Tab 或卡片

---

## Task 12: 数据库 - 清理废弃表

```sql
-- 可选：保留 t_collection_task 数据备份后清理
-- DROP TABLE IF EXISTS t_collection_task;
```

---

## 执行选项

**1. Subagent-Driven (推荐)** - 每次 dispatch 一个 subagent 执行任务，review 后继续

**2. Inline Execution** - 在当前 session 内顺序执行

选择哪种方式？