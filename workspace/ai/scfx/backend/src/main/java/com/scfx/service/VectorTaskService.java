package com.scfx.service;

import com.scfx.entity.VectorizationTask;
import java.util.List;
import java.util.Map;

public interface VectorTaskService {

    /**
     * 加入向量化队列
     */
    void enqueue(Long categoryId, Long knowledgeId);

    /**
     * 触发分类向量化（异步）
     */
    void triggerCategory(Long categoryId, String triggerType);

    /**
     * 获取待向量化数量统计
     */
    Map<String, Object> getStats();

    /**
     * 重试失败任务
     */
    void retry(Long knowledgeId);

    /**
     * 获取任务列表
     */
    List<VectorizationTask> getTasks(int page, int size);
}