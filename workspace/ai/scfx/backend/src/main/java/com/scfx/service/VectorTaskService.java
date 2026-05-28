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
     * 处理单条知识（双向量计算，不触发全局 PCA）
     */
    void processSingle(Long knowledgeId);

    /**
     * 获取任务列表
     */
    List<VectorizationTask> getTasks(int page, int size);

    /**
     * 全量重算指定分类的 PCA（手动触发，清理旧基线重新构建）
     */
    void recomputePCAFull(Long categoryId);

    /**
     * 全量重算（指定算法），结果写入 t_knowledge_dr_coords
     * @return 新版本号
     */
    int recomputeDR(Long categoryId, String algorithm);
}