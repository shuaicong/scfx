package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.entity.CollectionScript;
import com.scfx.entity.ExecutionItem;
import com.scfx.entity.KnowledgeBase;
import com.scfx.entity.TaskExecution;
import com.scfx.mapper.ExecutionItemMapper;
import com.scfx.mapper.KnowledgeCategoryMapper;
import com.scfx.service.CollectionScriptService;
import com.scfx.service.KnowledgeBaseService;
import com.scfx.service.TaskExecutionService;
import com.scfx.service.VectorTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * 采集器控制器 - 提供给 Python 采集器调用的 REST API
 */
@Slf4j
@RestController
@RequestMapping("/collector/exec")
@RequiredArgsConstructor
public class CollectorController {

    private final TaskExecutionService executionService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ExecutionItemMapper executionItemMapper;
    private final VectorTaskService vectorTaskService;
    private final CollectionScriptService scriptService;
    private final KnowledgeCategoryMapper knowledgeCategoryMapper;

    /**
     * 启动采集任务
     * POST /collector/exec/start
     * Body: {"taskId": 1}
     * Response: {"code":200, "data": {"executionId": "xxx", "taskId": 1, ...}}
     */
    @PostMapping("/start")
    public Result<Map<String, Object>> startExecution(@RequestBody Map<String, Object> request) {
        Long scriptId = ((Number) request.get("taskId")).longValue();
        TaskExecution execution = executionService.createExecution(scriptId, "manual");

        return Result.success(Map.of(
            "executionId", execution.getExecutionId(),
            "scriptId", execution.getScriptId(),
            "startTime", execution.getStartTime()
        ));
    }

    /**
     * 上报进度
     * POST /collector/exec/{executionId}/progress
     * Body: {"collectedCount": 5}
     */
    @PostMapping("/{executionId}/progress")
    public Result<Void> reportProgress(
            @PathVariable String executionId,
            @RequestBody Map<String, Object> request) {
        // 不需要单独存储进度，在完成时更新即可
        return Result.success();
    }

    /**
     * 上报日志
     * POST /collector/exec/{executionId}/log
     * Body: {"level": "INFO", "message": "登录成功", "phase": "login", "category": "checkpoint", "elapsedMs": 1234}
     */
    @PostMapping("/{executionId}/log")
    public Result<Void> addLog(
            @PathVariable String executionId,
            @RequestBody Map<String, Object> request) {
        String level = (String) request.getOrDefault("level", "INFO");
        String message = (String) request.get("message");
        String phase = (String) request.get("phase");
        String category = (String) request.get("category");
        Long elapsedMs = request.get("elapsedMs") != null ? ((Number) request.get("elapsedMs")).longValue() : null;

        if (phase != null || category != null || elapsedMs != null) {
            executionService.addStructuredLog(executionId, null, level, message, phase, category, elapsedMs);
        } else {
            executionService.addLog(executionId, null, level, message);
        }
        return Result.success();
    }

    /**
     * 上报采集数据
     * POST /collector/exec/{executionId}/data
     * Body: {"title": "...", "source": "...", "url": "...", "variety": "...", "reportType": "...", "content": "...", "publishTime": "..."}
     */
    @PostMapping("/{executionId}/data")
    public Result<Map<String, Object>> submitData(
            @PathVariable String executionId,
            @RequestBody Map<String, Object> request) {
        String source = (String) request.get("source");
        String originalUrl = (String) request.get("url");
        String title = (String) request.get("title");
        String content = (String) request.get("content");
        int contentLen = content != null ? content.length() : 0;
        log.info("submitData: executionId={}, title={}, source={}, contentLength={}",
            executionId, title, source, contentLen);

        // 0. 查询脚本配置（用于知识库同步和分类归属）
        boolean syncToKnowledgeBase = true;
        CollectionScript script = null;
        try {
            TaskExecution exec = executionService.findByExecutionId(executionId);
            if (exec != null) {
                script = scriptService.getById(exec.getScriptId());
                if (script != null && Boolean.FALSE.equals(script.getSyncToKnowledgeBase())) {
                    syncToKnowledgeBase = false;
                }
            }
        } catch (Exception e) {
            log.warn("submitData: 查询脚本配置失败，默认同步: {}", e.getMessage());
        }

        try {
            // 1. 解析发布时间
            LocalDateTime publishTime = null;
            Object publishTimeObj = request.get("publishTime");
            if (publishTimeObj != null) {
                if (publishTimeObj instanceof String) {
                    publishTime = LocalDateTime.parse((String) publishTimeObj);
                } else if (publishTimeObj instanceof Number) {
                    publishTime = LocalDateTime.ofEpochSecond(((Number) publishTimeObj).longValue() / 1000, 0, java.time.ZoneOffset.ofHours(8));
                }
            }

            // 2. 构建 KnowledgeBase
            KnowledgeBase kb = new KnowledgeBase();
            kb.setTitle(title);
            kb.setSourceType(getSourceTypeCode(source));
            kb.setSourceName(getSourceName(source));
            kb.setOriginalUrl(originalUrl);
            kb.setContent(content);
            kb.setContentHtml((String) request.get("contentHtml"));
            kb.setPublishTime(publishTime);
            kb.setExecutionId(executionId);
            kb.setCollectionSource(source);
            kb.setCollectionVariety((String) request.get("variety"));
            kb.setCollectionReportType((String) request.get("reportType"));
            kb.setVectorStatus("pending");

            // 3. 分类归属：使用脚本绑定的分类
            Long categoryId = script != null ? script.getCategoryId() : null;
            kb.setCategoryId(categoryId);

            // 4. 内容指纹（null 安全）
            kb.setContentHash(DigestUtils.md5Hex(content != null ? content : ""));

            if (!syncToKnowledgeBase) {
                // 同步已关闭：跳过知识库创建，只记录采集项
                log.info("submitData: 同步已关闭，跳过知识库创建: title={}", title);
                writeItem(executionId, null, title, originalUrl, "skipped", null);
                return Result.success(Map.of("knowledgeId", -1));
            }

            // 5. 写入知识库（处理重复内容）
            try {
                knowledgeBaseService.save(kb);
            } catch (DataIntegrityViolationException e) {
                log.warn("submitData: 内容重复，关联已有知识库: contentHash={}, title={}", kb.getContentHash(), title);
                KnowledgeBase existing = knowledgeBaseService.lambdaQuery()
                    .eq(KnowledgeBase::getContentHash, kb.getContentHash())
                    .one();
                if (existing == null) {
                    throw e; // 不是真正重复，重新抛出
                }
                kb = existing;
            }

            log.info("submitData: 知识库已保存, knowledgeId={}, contentHash={}", kb.getId(), kb.getContentHash());

            // 写入 t_knowledge_category 关联
            if (kb.getCategoryId() != null && kb.getId() != null) {
                try {
                    knowledgeCategoryMapper.insertBatch(kb.getId(), java.util.List.of(kb.getCategoryId()));
                } catch (Exception e) {
                    log.warn("submitData: 写入分类关联失败(不影响主流程): {}", e.getMessage());
                }
            }

            // 6. 实时触发向量化（@Async 异步执行，不阻塞响应）
            if (kb.getCategoryId() != null && kb.getId() != null) {
                vectorTaskService.triggerCategory(kb.getCategoryId(), "collection");
            }

            // 7. 记录采集数据项
            writeItem(executionId, kb.getId(), title, originalUrl, "created", null);

            return Result.success(Map.of("knowledgeId", kb.getId()));

        } catch (Exception e) {
            log.error("submitData 失败: executionId={}, title={}, error={}", executionId, title, e.getMessage(), e);
            return Result.error("提交数据失败: " + e.getMessage());
        }
    }

    private void writeItem(String executionId, Long knowledgeId, String title, String url, String action, String errorMessage) {
        ExecutionItem item = new ExecutionItem();
        item.setExecutionId(executionId);
        item.setKnowledgeId(knowledgeId);
        item.setTitle(title);
        item.setUrl(url);
        item.setAction(action);
        item.setErrorMessage(errorMessage);
        executionItemMapper.insert(item);
    }

    /**
     * 获取来源名称
     */
    private String getSourceName(String source) {
        Map<String, String> map = Map.of(
            "liangxinwang", "粮信网",
            "mysteel", "我的钢铁网",
            "china_grain", "中华粮网"
        );
        return map.getOrDefault(source, source);
    }

    /**
     * 获取来源短码（对齐前端知识库页面筛选器）
     */
    private String getSourceTypeCode(String source) {
        Map<String, String> map = Map.of(
            "liangxinwang", "liangxin",
            "mysteel", "mysteel",
            "china_grain", "chinagrain"
        );
        return map.getOrDefault(source, source);
    }

    /**
     * 上报错误
     * POST /collector/exec/{executionId}/error
     * Body: {"errorMessage": "页面元素未找到"}
     */
    @PostMapping("/{executionId}/error")
    public Result<Void> reportError(
            @PathVariable String executionId,
            @RequestBody Map<String, Object> request) {
        String errorMessage = (String) request.get("errorMessage");
        executionService.reportError(executionId, errorMessage);
        return Result.success();
    }

    /**
     * 完成执行
     * POST /collector/exec/{executionId}/complete
     * Body: {
     *   "status": "success",
     *   "collectedCount": 10,
     *   "totalCount": 50,
     *   "successCount": 45,
     *   "skipCount": 3,
     *   "errorCount": 2,
     *   "dataSizeMb": 2.3,
     *   "phase": {"loginMs": 5120, "crawlMs": 30400, "parseMs": 8500, "reportMs": 3200}
     * }
     */
    @PostMapping("/{executionId}/complete")
    public Result<Void> completeExecution(
            @PathVariable String executionId,
            @RequestBody Map<String, Object> request) {
        String status = (String) request.get("status");
        TaskExecutionService.ExecutionResult result = new TaskExecutionService.ExecutionResult();

        result.setCollectedCount(((Number) request.getOrDefault("collectedCount", 0)).intValue());
        result.setTotalCount(((Number) request.getOrDefault("totalCount", 0)).intValue());
        result.setSuccessCount(((Number) request.getOrDefault("successCount", 0)).intValue());
        result.setSkipCount(((Number) request.getOrDefault("skipCount", 0)).intValue());
        result.setErrorCount(((Number) request.getOrDefault("errorCount", 0)).intValue());

        Object dataSizeObj = request.get("dataSizeMb");
        if (dataSizeObj != null) {
            result.setDataSizeMb(new java.math.BigDecimal(dataSizeObj.toString()));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> phase = (Map<String, Object>) request.get("phase");
        if (phase != null) {
            result.setPhaseLoginMs(phase.get("loginMs") != null ? ((Number) phase.get("loginMs")).longValue() : null);
            result.setPhaseCrawlMs(phase.get("crawlMs") != null ? ((Number) phase.get("crawlMs")).longValue() : null);
            result.setPhaseParseMs(phase.get("parseMs") != null ? ((Number) phase.get("parseMs")).longValue() : null);
            result.setPhaseReportMs(phase.get("reportMs") != null ? ((Number) phase.get("reportMs")).longValue() : null);
        }

        executionService.completeExecution(executionId, status, result);
        return Result.success();
    }

    /**
     * 获取执行状态
     * GET /collector/exec/{executionId}
     */
    @GetMapping("/{executionId}")
    public Result<TaskExecution> getExecution(@PathVariable String executionId) {
        TaskExecution execution = executionService.findByExecutionId(executionId);
        if (execution == null) {
            return Result.error("执行记录不存在");
        }
        return Result.success(execution);
    }
}
