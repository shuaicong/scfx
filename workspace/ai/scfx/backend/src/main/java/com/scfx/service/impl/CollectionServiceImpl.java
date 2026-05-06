package com.scfx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scfx.entity.Report;
import com.scfx.entity.TaskExecution;
import com.scfx.mapper.ReportMapper;
import com.scfx.mapper.TaskExecutionMapper;
import com.scfx.service.TaskExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    private final ReportMapper reportMapper;
    private final TaskExecutionMapper executionMapper;

    @Autowired
    private RestTemplate restTemplate;

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
     * 查询所有报告并发送到AI QA服务
     */
    public void triggerKnowledgeIngest(String executionId) {
        List<Report> reports = reportMapper.selectList(
            new LambdaQueryWrapper<Report>()
                .eq(Report::getExecutionId, executionId)
        );
        if (reports.isEmpty()) {
            log.info("没有找到报告可接入知识库: executionId={}", executionId);
            return;
        }

        String url = "http://localhost:5002/api/knowledge/ingest";
        Map<String, Object> payload = new HashMap<>();
        payload.put("executionId", executionId);
        payload.put("source", "liangxinwang");
        payload.put("reports", reports.stream().map(r -> {
            Map<String, Object> item = new HashMap<>();
            item.put("title", r.getTitle());
            item.put("source", r.getSource());
            item.put("url", r.getOriginalUrl());
            item.put("author", r.getAuthor());
            item.put("publishTime", r.getPublishTime() != null ? r.getPublishTime().toString() : null);
            item.put("content", r.getContent());
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