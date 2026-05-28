package com.scfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.entity.CollectionScript;
import com.scfx.entity.CollectorScriptVersion;
import com.scfx.entity.ExecutionItem;
import com.scfx.entity.KnowledgeBase;
import com.scfx.entity.TaskExecution;
import com.scfx.entity.TaskExecutionLog;
import com.scfx.mapper.CollectionScriptMapper;
import com.scfx.mapper.ExecutionItemMapper;
import com.scfx.mapper.TaskExecutionLogMapper;
import com.scfx.mapper.TaskExecutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutionService {

    private final TaskExecutionMapper executionMapper;
    private final TaskExecutionLogMapper logMapper;
    private final ExecutionItemMapper executionItemMapper;
    private final CollectorScriptVersionService collectorScriptVersionService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final CollectionScriptMapper collectionScriptMapper;

    /**
     * 创建执行记录
     */
    public TaskExecution createExecution(Long scriptId, String triggerType) {
        TaskExecution execution = new TaskExecution();
        execution.setExecutionId(UUID.randomUUID().toString().replace("-", ""));
        execution.setScriptId(scriptId);
        execution.setTriggerType(triggerType);
        execution.setStatus("pending");
        execution.setStartTime(LocalDateTime.now());

        // 记录当前数据源脚本版本（t_collector_script_version）
        CollectionScript script = collectionScriptMapper.selectById(scriptId);
        if (script != null && script.getSource() != null) {
            CollectorScriptVersion version = collectorScriptVersionService.getCurrentVersion(script.getSource());
            if (version != null) {
                execution.setVersionId(version.getId());
                execution.setVersionNum(version.getVersion());
            }
        }

        executionMapper.insert(execution);
        return execution;
    }

    /**
     * 更新执行状态
     */
    public void updateStatus(String executionId, String status, String errorMessage) {
        TaskExecution execution = findByExecutionId(executionId);
        if (execution != null) {
            execution.setStatus(status);
            if (errorMessage != null) {
                execution.setErrorMessage(errorMessage);
            }
            if ("success".equals(status) || "failed".equals(status) || "cancelled".equals(status)) {
                execution.setEndTime(LocalDateTime.now());
                execution.setDurationMs(
                    java.time.Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis()
                );
            }
            executionMapper.updateById(execution);
        }
    }

    /**
     * 超时时间默认值（30分钟）
     */
    public static final int DEFAULT_TIMEOUT_MINUTES = 30;

    /**
     * 检查并终止超时的执行记录
     * @return 被终止的记录数
     */
    public int checkAndTimeoutRunningExecutions(int timeoutMinutes) {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(timeoutMinutes);
        LambdaQueryWrapper<TaskExecution> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskExecution::getStatus, "running")
               .lt(TaskExecution::getStartTime, deadline);
        List<TaskExecution> timeoutList = executionMapper.selectList(wrapper);

        for (TaskExecution exec : timeoutList) {
            exec.setStatus("failed");
            exec.setEndTime(LocalDateTime.now());
            exec.setErrorMessage("执行超时（系统自动终止）");
            if (exec.getStartTime() != null) {
                exec.setDurationMs(
                    java.time.Duration.between(exec.getStartTime(), exec.getEndTime()).toMillis()
                );
            }
            executionMapper.updateById(exec);
            addLog(exec.getExecutionId(), exec.getScriptId(), "ERROR",
                "执行超时（超过 " + timeoutMinutes + " 分钟），系统自动终止");
        }
        return timeoutList.size();
    }

    /**
     * 分页查询执行记录（基础版，兼容旧调用）
     */
    public Page<TaskExecution> getExecutions(Long scriptId, int page, int size) {
        return getExecutions(scriptId, page, size, null, null);
    }

    /**
     * 分页查询执行记录（支持状态 / 触发类型筛选）
     */
    public Page<TaskExecution> getExecutions(Long scriptId, int page, int size, String status, String triggerType) {
        Page<TaskExecution> pageInfo = new Page<>(page, size);
        LambdaQueryWrapper<TaskExecution> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(scriptId != null, TaskExecution::getScriptId, scriptId)
               .eq(status != null && !status.isEmpty(), TaskExecution::getStatus, status)
               .eq(triggerType != null && !triggerType.isEmpty(), TaskExecution::getTriggerType, triggerType)
               .orderByDesc(TaskExecution::getStartTime);
        Page<TaskExecution> result = executionMapper.selectPage(pageInfo, wrapper);

        // 填充 versionNum
        populateVersionNum(result.getRecords());

        return result;
    }

    /**
     * 获取执行详情
     */
    public TaskExecution getExecution(String executionId) {
        TaskExecution execution = findByExecutionId(executionId);
        if (execution != null) {
            populateVersionNum(List.of(execution));
        }
        return execution;
    }

    /**
     * 批量填充 versionNum
     */
    private void populateVersionNum(List<TaskExecution> executions) {
        for (TaskExecution exec : executions) {
            if (exec.getVersionId() != null) {
                CollectorScriptVersion version = collectorScriptVersionService.getVersionById(exec.getVersionId());
                if (version != null) {
                    exec.setVersionNum(version.getVersion());
                }
            }
        }
    }

    /**
     * 添加日志
     */
    public void addLog(String executionId, Long scriptId, String level, String message) {
        TaskExecutionLog logEntry = new TaskExecutionLog();
        logEntry.setExecutionId(executionId);
        logEntry.setScriptId(scriptId);
        logEntry.setLevel(level);
        logEntry.setMessage(message);
        logEntry.setTimestamp(LocalDateTime.now());
        logMapper.insert(logEntry);
    }

    /**
     * 添加结构化日志（带 stage/category/elapsedMs）
     */
    public void addStructuredLog(String executionId, Long scriptId, String level, String message,
                                  String phase, String category, Long elapsedMs) {
        TaskExecutionLog logEntry = new TaskExecutionLog();
        logEntry.setExecutionId(executionId);
        logEntry.setScriptId(scriptId);
        logEntry.setLevel(level);
        logEntry.setMessage(message);
        logEntry.setPhase(phase);
        logEntry.setCategory(category);
        logEntry.setElapsedMs(elapsedMs);
        logEntry.setTimestamp(LocalDateTime.now());
        logMapper.insert(logEntry);
    }

    /**
     * 上报错误（被采集器调用）
     */
    public void reportError(String executionId, String errorMessage) {
        updateStatus(executionId, "failed", errorMessage);
        addLog(executionId, null, "ERROR", errorMessage);
    }

    /**
     * 完成执行（被采集器调用）- 基础版，兼容旧调用
     */
    public void completeExecution(String executionId, String status, int collectedCount) {
        completeExecution(executionId, status, collectedCount, null);
    }

    /**
     * 完成执行（带错误信息）
     */
    public void completeExecution(String executionId, String status, int collectedCount, String errorMessage) {
        ExecutionResult result = new ExecutionResult();
        result.collectedCount = collectedCount;
        completeExecution(executionId, status, result, errorMessage);
    }

    /**
     * 完成执行（完整统计版）
     */
    public void completeExecution(String executionId, String status, ExecutionResult result) {
        completeExecution(executionId, status, result, null);
    }

    /**
     * 完成执行（完整统计版 + 错误信息）
     */
    public void completeExecution(String executionId, String status, ExecutionResult result, String errorMessage) {
        updateStatus(executionId, status, errorMessage);

        // 更新统计字段
        TaskExecution execution = findByExecutionId(executionId);
        if (execution != null && result != null) {
            execution.setCollectedCount(result.collectedCount);
            execution.setTotalCount(result.totalCount);
            execution.setSuccessCount(result.successCount);
            execution.setSkipCount(result.skipCount);
            execution.setErrorCount(result.errorCount);
            execution.setDataSizeMb(result.dataSizeMb);
            execution.setPhaseLoginMs(result.phaseLoginMs);
            execution.setPhaseCrawlMs(result.phaseCrawlMs);
            execution.setPhaseParseMs(result.phaseParseMs);
            execution.setPhaseReportMs(result.phaseReportMs);
            execution.setDetailJson(result.detailJson);
            executionMapper.updateById(execution);
        }

        // 写完成日志
        StringBuilder logMsg = new StringBuilder("Execution completed with status: ").append(status);
        if (result != null) {
            logMsg.append(", total: ").append(result.totalCount)
                  .append(", success: ").append(result.successCount)
                  .append(", skip: ").append(result.skipCount)
                  .append(", error: ").append(result.errorCount);
        }
        if (errorMessage != null) {
            logMsg.append(", error: ").append(errorMessage);
        }
        addLog(executionId, null, "INFO", logMsg.toString());

        // 更新脚本统计（成功/失败次数）
        if (execution != null && execution.getScriptId() != null) {
            LambdaUpdateWrapper<CollectionScript> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(CollectionScript::getId, execution.getScriptId());
            if ("success".equals(status)) {
                wrapper.setSql("success_count = COALESCE(success_count, 0) + 1");
            } else if ("failed".equals(status)) {
                wrapper.setSql("failed_count = COALESCE(failed_count, 0) + 1");
            }
            if (!"success".equals(status) && !"failed".equals(status)) {
                // cancelled or pending — no count update
                return;
            }
            collectionScriptMapper.update(null, wrapper);
        }
    }

    /**
     * 执行结果统计
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    public static class ExecutionResult {
        private int collectedCount;
        private int totalCount;
        private int successCount;
        private int skipCount;
        private int errorCount;
        private java.math.BigDecimal dataSizeMb;
        private Long phaseLoginMs;
        private Long phaseCrawlMs;
        private Long phaseParseMs;
        private Long phaseReportMs;
        private String detailJson;
    }

    /**
     * 获取执行日志
     */
    public List<TaskExecutionLog> getLogs(String executionId) {
        return logMapper.selectList(
            new LambdaQueryWrapper<TaskExecutionLog>()
                .eq(TaskExecutionLog::getExecutionId, executionId)
                .orderByAsc(TaskExecutionLog::getTimestamp)
        );
    }

    /**
     * 获取执行采集数据项列表（含内容统计信息）
     */
    public List<ExecutionItem> getItems(String executionId) {
        List<ExecutionItem> items = executionItemMapper.selectList(
            new LambdaQueryWrapper<ExecutionItem>()
                .eq(ExecutionItem::getExecutionId, executionId)
                .orderByAsc(ExecutionItem::getId)
        );

        // 批量查询关联的知识库记录，填充内容统计信息
        List<Long> kbIds = items.stream()
            .map(ExecutionItem::getKnowledgeId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        if (!kbIds.isEmpty()) {
            List<KnowledgeBase> kbList = knowledgeBaseService.listByIds(kbIds);
            Map<Long, KnowledgeBase> kbMap = kbList.stream()
                .collect(Collectors.toMap(KnowledgeBase::getId, Function.identity()));

            for (ExecutionItem item : items) {
                if (item.getKnowledgeId() != null) {
                    KnowledgeBase kb = kbMap.get(item.getKnowledgeId());
                    if (kb != null && kb.getContent() != null) {
                        String content = kb.getContent().trim();
                        item.setContentLength(content.length());

                        // 统计图片数量（从 contentHtml 中统计 <img 标签）
                        if (kb.getContentHtml() != null) {
                            String html = kb.getContentHtml();
                            int count = 0;
                            int idx = 0;
                            while ((idx = html.indexOf("<img", idx)) != -1) {
                                count++;
                                idx += 4;
                            }
                            item.setImageCount(count);
                        } else {
                            item.setImageCount(0);
                        }

                        // 内容截取：头200字 + "..." + 尾100字
                        if (content.length() > 300) {
                            item.setContentPreview(content.substring(0, 200) + "\n...\n" + content.substring(content.length() - 100));
                        } else {
                            item.setContentPreview(content);
                        }
                    }
                }
            }
        }

        return items;
    }

    public TaskExecution findByExecutionId(String executionId) {
        return executionMapper.selectOne(
            new LambdaQueryWrapper<TaskExecution>()
                .eq(TaskExecution::getExecutionId, executionId)
        );
    }
}
