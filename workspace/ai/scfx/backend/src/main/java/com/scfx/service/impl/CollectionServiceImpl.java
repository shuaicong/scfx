package com.scfx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scfx.entity.KnowledgeBase;
import com.scfx.entity.TaskExecution;
import com.scfx.mapper.KnowledgeBaseMapper;
import com.scfx.mapper.TaskExecutionMapper;
import com.scfx.service.TaskExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 采集服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionServiceImpl {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final TaskExecutionMapper executionMapper;
    private final RestTemplate restTemplate;

    @Value("${app.ai-qa-service.url}")
    private String aiQaServiceUrl;

    /**
     * 完成执行并触发知识库接入
     */
    public void completeExecution(String executionId) {
        // 更新执行状态
        TaskExecution execution = executionMapper.selectOne(
            new LambdaQueryWrapper<TaskExecution>()
                .eq(TaskExecution::getExecutionId, executionId)
        );
        if (execution != null) {
            execution.setStatus("success");
            executionMapper.updateById(execution);
        }

        // 触发知识库接入
        triggerKnowledgeIngest(executionId);
    }

    /**
     * 触发知识库接入
     * 查询本次执行对应的知识库条目并发送到AI QA服务
     */
    public void triggerKnowledgeIngest(String executionId) {
        List<KnowledgeBase> items = knowledgeBaseMapper.selectList(
            new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getExecutionId, executionId)
        );
        if (items.isEmpty()) {
            log.info("没有找到知识库条目可接入: executionId={}", executionId);
            return;
        }

        String url = aiQaServiceUrl + "/api/knowledge/ingest";
        Map<String, Object> payload = new HashMap<>();
        payload.put("executionId", executionId);
        payload.put("source", "liangxinwang");
        payload.put("reports", items.stream().map(kb -> {
            Map<String, Object> item = new HashMap<>();
            item.put("title", kb.getTitle());
            item.put("source", kb.getSourceType());
            item.put("url", kb.getOriginalUrl());
            item.put("publishTime", kb.getPublishTime() != null ? kb.getPublishTime().toString() : null);
            item.put("content", kb.getContent());
            return item;
        }).collect(Collectors.toList()));

        int retryCount = 0;
        while (retryCount < 3) {
            try {
                restTemplate.postForObject(url, payload, Map.class);
                log.info("知识库接入成功: executionId={}", executionId);
                return;
            } catch (Exception e) {
                retryCount++;
                log.warn("知识库接入重试 {}: executionId={}, error={}", retryCount, executionId, e.getMessage());
                if (retryCount >= 3) {
                    log.error("知识库接入失败，已重试3次: executionId={}", executionId);
                } else {
                    try {
                        Thread.sleep(1000 * retryCount);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }
}