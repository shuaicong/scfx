package com.scfx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scfx.entity.KnowledgeBase;
import com.scfx.entity.VectorizationLog;
import com.scfx.entity.VectorizationTask;
import com.scfx.mapper.KnowledgeBaseMapper;
import com.scfx.mapper.VectorizationLogMapper;
import com.scfx.mapper.VectorizationTaskMapper;
import com.scfx.service.VectorClient;
import com.scfx.service.VectorTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class VectorTaskServiceImpl implements VectorTaskService {

    private final VectorizationTaskMapper taskMapper;
    private final VectorizationLogMapper logMapper;
    private final KnowledgeBaseMapper knowledgeMapper;
    private final VectorClient vectorClient;

    // 阈值：达到多少条待处理数据时自动触发
    private static final int AUTO_TRIGGER_THRESHOLD = 5;
    // 最大重试次数
    private static final int MAX_RETRIES = 3;

    @Override
    @Transactional
    public void enqueue(Long categoryId, Long knowledgeId) {
        // 1. 更新知识库状态
        KnowledgeBase kb = knowledgeMapper.selectById(knowledgeId);
        kb.setVectorStatus("pending");
        knowledgeMapper.updateById(kb);

        // 2. 记录日志
        VectorizationLog log = new VectorizationLog();
        log.setKnowledgeId(knowledgeId);
        log.setCategoryId(categoryId);
        log.setStatus("pending");
        logMapper.insert(log);

        // 3. 检查是否达到自动触发阈值
        long pendingCount = countPendingInCategory(categoryId);
        if (pendingCount >= AUTO_TRIGGER_THRESHOLD) {
            triggerCategory(categoryId, "auto");
        }
    }

    @Override
    @Async
    public void triggerCategory(Long categoryId, String triggerType) {
        // 1. 查询该分类下所有待处理数据
        List<KnowledgeBase> pendingList = knowledgeMapper.selectPending(categoryId);
        if (pendingList.isEmpty()) return;

        // 2. 创建任务记录
        VectorizationTask task = new VectorizationTask();
        task.setCategoryId(categoryId);
        task.setTotalCount(pendingList.size());
        task.setStatus("processing");
        task.setTriggerType(triggerType);
        taskMapper.insert(task);

        // 3. 批量处理
        for (KnowledgeBase kb : pendingList) {
            processVectorization(kb, task);
        }

        // 4. 更新任务状态
        task.setStatus("completed");
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private void processVectorization(KnowledgeBase kb, VectorizationTask task) {
        int retryCount = 0;

        while (retryCount <= MAX_RETRIES) {
            try {
                long startTime = System.currentTimeMillis();

                // 调用向量库API
                VectorClient.VectorResult result = vectorClient.embed(kb.getContent());

                long processTime = System.currentTimeMillis() - startTime;

                // 更新成功状态
                kb.setVectorStatus("vectorized");
                kb.setVectorIds(result.getVectorId());
                knowledgeMapper.updateById(kb);

                // 更新日志
                updateLog(kb.getId(), "success", result.getVectorId(), (int) processTime);
                task.setProcessedCount(task.getProcessedCount() + 1);
                return;

            } catch (Exception e) {
                retryCount++;
                if (retryCount > MAX_RETRIES) {
                    // 最终失败
                    kb.setVectorStatus("failed");
                    knowledgeMapper.updateById(kb);
                    updateLog(kb.getId(), "failed", e.getMessage(), null);
                    task.setFailedCount(task.getFailedCount() + 1);
                } else {
                    // 指数退避重试: 2s, 4s, 8s
                    try {
                        Thread.sleep((long) Math.pow(2, retryCount) * 1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
    }

    private void updateLog(Long knowledgeId, String status, String result, Integer processTime) {
        LambdaQueryWrapper<VectorizationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VectorizationLog::getKnowledgeId, knowledgeId)
               .orderByDesc(VectorizationLog::getCreatedAt)
               .last("LIMIT 1");
        VectorizationLog log = logMapper.selectOne(wrapper);

        if (log != null) {
            log.setStatus(status);
            if ("success".equals(status)) {
                log.setVectorId(result);
            } else {
                log.setErrorMessage(result);
            }
            log.setRetryCount(log.getRetryCount() + 1);
            if (processTime != null) {
                log.setProcessTimeMs(processTime);
            }
            logMapper.updateById(log);
        }
    }

    @Override
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();

        long pending = knowledgeMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeBase>().eq(KnowledgeBase::getVectorStatus, "pending"));
        long processing = knowledgeMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeBase>().eq(KnowledgeBase::getVectorStatus, "processing"));
        long vectorized = knowledgeMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeBase>().eq(KnowledgeBase::getVectorStatus, "vectorized"));
        long failed = knowledgeMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeBase>().eq(KnowledgeBase::getVectorStatus, "failed"));

        stats.put("pending", pending);
        stats.put("processing", processing);
        stats.put("vectorized", vectorized);
        stats.put("failed", failed);
        stats.put("total", pending + processing + vectorized + failed);

        return stats;
    }

    @Override
    public void retry(Long knowledgeId) {
        KnowledgeBase kb = knowledgeMapper.selectById(knowledgeId);
        if (kb != null && "failed".equals(kb.getVectorStatus())) {
            enqueue(kb.getCategoryId(), kb.getId());
        }
    }

    @Override
    public List<VectorizationTask> getTasks(int page, int size) {
        LambdaQueryWrapper<VectorizationTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(VectorizationTask::getCreatedAt)
               .last("LIMIT " + (page - 1) * size + ", " + size);
        return taskMapper.selectList(wrapper);
    }

    private long countPendingInCategory(Long categoryId) {
        return knowledgeMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getCategoryId, categoryId)
                .eq(KnowledgeBase::getVectorStatus, "pending"));
    }
}