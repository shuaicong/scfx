package com.scfx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.entity.KnowledgeBase;
import com.scfx.entity.KnowledgeViz;
import com.scfx.entity.PCABaseline;
import com.scfx.entity.PCACalculationRecord;
import com.scfx.entity.VectorizationLog;
import com.scfx.entity.VectorizationTask;
import com.scfx.mapper.KnowledgeBaseMapper;
import com.scfx.mapper.KnowledgeVizMapper;
import com.scfx.mapper.PCABaselineMapper;
import com.scfx.mapper.PCACalculationRecordMapper;
import com.scfx.mapper.VectorizationLogMapper;
import com.scfx.mapper.VectorizationTaskMapper;
import com.scfx.service.DimensionalityReducer;
import com.scfx.service.VectorClient;
import com.scfx.service.VectorStore;
import com.scfx.service.VectorTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorTaskServiceImpl implements VectorTaskService {

    private final VectorizationTaskMapper taskMapper;
    private final VectorizationLogMapper logMapper;
    private final KnowledgeBaseMapper knowledgeMapper;
    private final KnowledgeVizMapper vizMapper;
    private final PCABaselineMapper baselineMapper;
    private final PCACalculationRecordMapper calcRecordMapper;
    private final VectorClient vectorClient;
    private final VectorStore vectorStore;
    private final DimensionalityReducer reducer;
    private final VizAsyncProcessor vizAsyncProcessor;

    @Value("${pca.dirty-threshold:20}")
    private int dirtyThreshold;

    /** 脏数据计数器：categoryId → processSingle 累计次数，达到阈值自动触发增量 PCA */
    private final ConcurrentHashMap<Long, AtomicInteger> dirtyCount = new ConcurrentHashMap<>();

    private static final int AUTO_TRIGGER_THRESHOLD = 5;
    private static final int MAX_RETRIES = 3;

    // ======================== 队列入口 ========================

    @Override
    @Transactional
    public void enqueue(Long categoryId, Long knowledgeId) {
        KnowledgeBase kb = knowledgeMapper.selectById(knowledgeId);
        kb.setVectorStatus("pending");
        knowledgeMapper.updateById(kb);

        VectorizationLog log = new VectorizationLog();
        log.setKnowledgeId(knowledgeId);
        log.setCategoryId(categoryId);
        log.setStatus("pending");
        logMapper.insert(log);

        long pendingCount = countPendingInCategory(categoryId);
        if (pendingCount >= AUTO_TRIGGER_THRESHOLD) {
            triggerCategory(categoryId, "auto");
        }
    }

    // ======================== 单条处理（含脏数据追踪） ========================

    @Override
    @Async
    public void processSingle(Long knowledgeId) {
        KnowledgeBase kb = knowledgeMapper.selectById(knowledgeId);
        if (kb == null) {
            log.warn("processSingle: 知识不存在, knowledgeId={}", knowledgeId);
            return;
        }
        kb.setVectorStatus("processing");
        knowledgeMapper.updateById(kb);

        if (!computeDualVectors(kb)) {
            return;
        }

        // 脏数据追踪：单条更新不触 PCA，累计达到阈值后自动触发增量 PCA
        if (kb.getCategoryId() != null) {
            AtomicInteger counter = dirtyCount.computeIfAbsent(
                kb.getCategoryId(), k -> new AtomicInteger(0));
            int current = counter.incrementAndGet();
            log.debug("脏数据计数器: categoryId={}, count={}, threshold={}",
                kb.getCategoryId(), current, dirtyThreshold);
            if (current >= dirtyThreshold) {
                log.info("脏数据达阈值: categoryId={}, count={}, 触发 auto_dirty PCA",
                    kb.getCategoryId(), current);
                triggerCategory(kb.getCategoryId(), "auto_dirty");
            }
        }
    }

    // ======================== 批量处理（并行 DashScope + 补偿重试） ========================

    @Override
    @Async
    public void triggerCategory(Long categoryId, String triggerType) {
        // -- Phase 0: 收集待处理条目（pending + rate_limited） --

        List<KnowledgeBase> pendingList = knowledgeMapper.selectPending(categoryId);

        List<KnowledgeViz> rateLimitedVizList =
            vizMapper.selectByCategoryIdAndVizStatus(categoryId, "rate_limited");
        Set<Long> rateLimitedKnowledgeIds = rateLimitedVizList.stream()
            .map(KnowledgeViz::getKnowledgeId)
            .collect(Collectors.toSet());

        int totalEntries = pendingList.size() + rateLimitedKnowledgeIds.size();

        if (totalEntries == 0) {
            // auto_dirty 类型即使无队列条目也执行 PCA
            if ("auto_dirty".equals(triggerType)) {
                log.info("auto_dirty 触发无待处理条目，仍执行 PCA: categoryId={}", categoryId);
                recomputePCA(categoryId);
            } else {
                log.debug("triggerCategory 无待处理条目: categoryId={}", categoryId);
            }
            return;
        }

        log.info("triggerCategory: categoryId={}, type={}, pending={}, rateLimited={}",
            categoryId, triggerType, pendingList.size(), rateLimitedKnowledgeIds.size());

        VectorizationTask task = new VectorizationTask();
        task.setCategoryId(categoryId);
        task.setTotalCount(totalEntries);
        task.setStatus("processing");
        task.setTriggerType(triggerType);
        taskMapper.insert(task);

        // -- Phase 1: BGE-M3 顺序执行 + dispatch 异步 DashScope --

        List<CompletableFuture<Boolean>> vizFutures = new ArrayList<>();
        for (KnowledgeBase kb : pendingList) {
            if (computeBgeM3(kb)) {
                vizFutures.add(vizAsyncProcessor.asyncVizCall(kb));
            }
        }

        // -- Phase 2: 补偿重试 rate_limited 条目（BGE-M3 已成功，仅跑 DashScope） --

        if (!rateLimitedKnowledgeIds.isEmpty()) {
            LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
            wrapper.in(KnowledgeBase::getId, rateLimitedKnowledgeIds);
            List<KnowledgeBase> rateLimitedKbs = knowledgeMapper.selectList(wrapper);
            for (KnowledgeBase kb : rateLimitedKbs) {
                log.info("补偿重试 rate_limited: knowledgeId={}", kb.getId());
                vizFutures.add(vizAsyncProcessor.asyncVizCall(kb));
            }
        }

        // -- Phase 3: 等待所有 DashScope 异步调用完成 --

        boolean hasVizData;
        if (!vizFutures.isEmpty()) {
            CompletableFuture.allOf(
                vizFutures.toArray(new CompletableFuture[0])
            ).join();
            hasVizData = vizFutures.stream()
                .map(f -> {
                    try { return f.get(); } catch (Exception e) { return Boolean.FALSE; }
                })
                .anyMatch(Boolean.TRUE::equals);
        } else {
            hasVizData = false;
        }

        // -- Phase 4: PCA --

        if (hasVizData || "auto_dirty".equals(triggerType)) {
            recomputePCA(categoryId);
        }

        task.setStatus("completed");
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    // ======================== BGE-M3 核心 ========================

    /**
     * BGE-M3 文本向量化，含重试
     * @return true 如果向量化成功
     */
    private boolean computeBgeM3(KnowledgeBase kb) {
        int retryCount = 0;
        while (retryCount <= MAX_RETRIES) {
            try {
                long startTime = System.currentTimeMillis();

                VectorClient.VectorResult retrievalResult = vectorClient.embed(
                    kb.getContent(), kb.getContentHtml(), kb.getId());

                long retrievalTime = System.currentTimeMillis() - startTime;

                kb.setVectorStatus("vectorized");
                kb.setVectorIds(retrievalResult.getVectorId());
                knowledgeMapper.updateById(kb);

                updateLog(kb.getId(), "success", retrievalResult.getVectorId(), (int) retrievalTime);

                log.info("BGE-M3 向量化成功: knowledgeId={}, vectorId={}, cost={}ms",
                    kb.getId(), retrievalResult.getVectorId(), retrievalTime);
                return true;

            } catch (Exception e) {
                retryCount++;
                log.warn("BGE-M3 重试 {}/{}: knowledgeId={}, err={}",
                    retryCount, MAX_RETRIES, kb.getId(), e.getMessage());

                if (retryCount > MAX_RETRIES) {
                    kb.setVectorStatus("failed");
                    knowledgeMapper.updateById(kb);
                    updateLog(kb.getId(), "failed", e.getMessage(), null);
                    return false;
                }
                try {
                    Thread.sleep((long) Math.pow(2, retryCount) * 1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    // ======================== 双向量计算核心（单条同步） ========================

    /**
     * 对单条知识进行双向量计算：BGE-M3 检索 + DashScope 768d 可视化（同步）
     * processSingle 使用此方法；triggerCategory 改用 computeBgeM3 + 异步 VizAsyncProcessor
     * @return true 如果可视化向量计算成功
     */
    private boolean computeDualVectors(KnowledgeBase kb) {
        if (!computeBgeM3(kb)) {
            return false;
        }

        // DashScope 可视化（失败不影响主流程）
        boolean hasViz = false;
        try {
            long vizStart = System.currentTimeMillis();
            VectorClient.VectorResult vizResult = vectorClient.embedVisualization(
                kb.getContent(), kb.getContentHtml(), kb.getId());
            long vizTime = System.currentTimeMillis() - vizStart;

            if (vizResult.getVector() != null && vizResult.getVector().length > 0) {
                vectorStore.saveVector(kb.getId(), vizResult.getVector());
                hasViz = true;
                log.info("DashScope 可视化成功: knowledgeId={}, dims={}, cost={}ms",
                    kb.getId(), vizResult.getVector().length, vizTime);
            } else if ("ds_rate_limited".equals(vizResult.getVectorId())) {
                log.warn("DashScope 429 限流（标记 rate_limited）: knowledgeId={}", kb.getId());
                vectorStore.updateStatus(kb.getId(), "rate_limited");
            } else {
                log.warn("DashScope 可视化失败: knowledgeId={}, vectorId={}",
                    kb.getId(), vizResult.getVectorId());
                vectorStore.updateStatus(kb.getId(), "failed");
            }
        } catch (Exception e) {
            log.warn("DashScope 可视化异常（不影响检索）: knowledgeId={}, err={}",
                kb.getId(), e.getMessage());
            vectorStore.updateStatus(kb.getId(), "failed");
        }

        return hasViz;
    }

    // ======================== PCA 降维 ========================

    /**
     * 批量新增后的 PCA 更新（优先增量，基线不存在时全量）
     * 增量投影：新向量用缓存均值/主成分投影，不回算已有点
     */
    private void recomputePCA(Long categoryId) {
        // 重置脏计数器（PCA 已刷新投影）
        dirtyCount.remove(categoryId);

        PCABaseline baseline = baselineMapper.selectById(categoryId);
        if (baseline == null) {
            fullPCA(categoryId);
        } else {
            incrementalPCA(categoryId, baseline);
        }
    }

    /** 全量 PCA：初次构建或基线丢失时执行 */
    private void fullPCA(Long categoryId) {
        long start = System.currentTimeMillis();
        try {
            Map<Long, float[]> vectorMap = vectorStore.getVectorMapByCategoryId(categoryId);
            if (vectorMap.size() < 2) {
                log.info("fullPCA: categoryId={} 有效向量不足 2 条，跳过", categoryId);
                return;
            }

            int beforeCount = 0;
            PCABaseline old = baselineMapper.selectById(categoryId);
            if (old != null) beforeCount = old.getVectorCount();

            DimensionalityReducer.ReductionResult result = reducer.reduce(vectorMap);
            long cost = System.currentTimeMillis() - start;
            log.info("fullPCA: categoryId={}, totalPoints={}, cost={}ms", categoryId, result.getPoints().size(), cost);

            // 写坐标（含 Z）
            for (DimensionalityReducer.Point p : result.getPoints()) {
                updateVizCoords(p);
            }

            // 持久化基线
            int newVersion = (old != null ? old.getVersion() : 0) + 1;
            saveBaseline(categoryId, result, newVersion);

            // 记录版本快照
            recordCalculation(categoryId, newVersion, "manual_full",
                result.getPoints().size(), beforeCount, cost,
                "全量 PCA " + (old == null ? "初次构建" : "重建（含 3D 坐标）"));

        } catch (Exception e) {
            log.error("fullPCA 失败: categoryId={}, err={}", categoryId, e.getMessage(), e);
        }
    }

    /** 增量 PCA：复用基线投影新增向量 */
    private void incrementalPCA(Long categoryId, PCABaseline baseline) {
        long start = System.currentTimeMillis();
        try {
            List<VectorStore.VectorEntry> allViz = vectorStore.getByCategoryId(categoryId);

            // 筛选新增向量（id > lastVizId 且 vector 非空）
            Map<Long, float[]> newVectors = new LinkedHashMap<>();
            VectorStore.VectorEntry maxViz = null;
            for (VectorStore.VectorEntry viz : allViz) {
                if (viz.getVector() != null && viz.getId() > baseline.getLastVizId()) {
                    newVectors.put(viz.getKnowledgeId(), viz.getVector());
                }
                if (maxViz == null || viz.getId() > maxViz.getId()) {
                    maxViz = viz;
                }
            }

            if (newVectors.isEmpty()) {
                log.debug("incrementalPCA: categoryId={} 无新增向量，跳过", categoryId);
                return;
            }

            log.info("incrementalPCA: categoryId={}, newVectors={}", categoryId, newVectors.size());

            // 增量投影（含 PC3）
            DimensionalityReducer.IncrementalResult inc = reducer.projectIncremental(
                newVectors,
                toDoubleArray(baseline.getMeanVector()),
                toDoubleArray(baseline.getPc1()),
                toDoubleArray(baseline.getPc2()),
                toDoubleArray(baseline.getPc3()),
                baseline.getXMin(), baseline.getXMax(),
                baseline.getYMin(), baseline.getYMax(),
                baseline.getZMin(), baseline.getZMax());

            // 写新增点的坐标
            for (DimensionalityReducer.Point p : inc.getPoints()) {
                updateVizCoords(p);
            }

            // 更新基线
            long cost = System.currentTimeMillis() - start;
            int newVersion = baseline.getVersion() + 1;
            baseline.setVersion(newVersion);
            baseline.setLastVizId(maxViz != null ? maxViz.getId() : baseline.getLastVizId());
            baseline.setVectorCount(baseline.getVectorCount() + newVectors.size());
            baseline.setXMin(inc.getXMin());
            baseline.setXMax(inc.getXMax());
            baseline.setYMin(inc.getYMin());
            baseline.setYMax(inc.getYMax());
            baselineMapper.updateById(baseline);

            // 记录版本快照
            recordCalculation(categoryId, newVersion, "auto_incremental",
                newVectors.size(), newVectors.size(), cost,
                newVectors.size() + " 条新增增量投影（含 3D 坐标）");

        } catch (Exception e) {
            log.error("incrementalPCA 失败: categoryId={}, err={}", categoryId, e.getMessage(), e);
        }
    }

    /** 全量重算（手动触发），清理旧基线重新构建 */
    public void recomputePCAFull(Long categoryId) {
        log.info("recomputePCAFull: categoryId={}", categoryId);
        fullPCA(categoryId);
    }

    // ======================== PCA 辅助方法 ========================

    private void updateVizCoords(DimensionalityReducer.Point p) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setId(p.getKnowledgeId());
        kb.setVizX(p.getX());
        kb.setVizY(p.getY());
        kb.setVizZ(p.getZ());
        knowledgeMapper.updateById(kb);
    }

    private void saveBaseline(Long categoryId, DimensionalityReducer.ReductionResult result, int version) {
        List<VectorStore.VectorEntry> vizList = vectorStore.getByCategoryId(categoryId);
        PCABaseline baseline = new PCABaseline();
        baseline.setCategoryId(categoryId);
        baseline.setLastVizId(vizList.stream().mapToLong(VectorStore.VectorEntry::getId).max().orElse(0L));
        baseline.setVectorCount(vizList.size());
        baseline.setMeanVector(toFloatArray(result.getMean()));
        baseline.setPc1(toFloatArray(result.getPc1()));
        baseline.setPc2(toFloatArray(result.getPc2()));
        baseline.setPc3(toFloatArray(result.getPc3()));
        baseline.setXMin(result.getXMin());
        baseline.setXMax(result.getXMax());
        baseline.setYMin(result.getYMin());
        baseline.setYMax(result.getYMax());
        baseline.setZMin(result.getZMin());
        baseline.setZMax(result.getZMax());
        baseline.setVersion(version);
        baselineMapper.insert(baseline);
    }

    /** 记录 PCA 计算版本快照 */
    private void recordCalculation(Long categoryId, Integer version, String triggerType,
                                    int pointCount, int beforeCount, long costMs, String remark) {
        try {
            PCACalculationRecord record = new PCACalculationRecord();
            record.setCategoryId(categoryId);
            record.setVersion(version);
            record.setTriggerType(triggerType);
            record.setPointCount(pointCount);
            record.setBeforeCount(beforeCount);
            record.setComputationCostMs(costMs);
            record.setRemark(remark);
            calcRecordMapper.insert(record);
            log.info("PCA版本记录: categoryId={}, version={}, trigger={}, points={}, cost={}ms",
                categoryId, version, triggerType, pointCount, costMs);
        } catch (Exception e) {
            log.warn("PCA版本记录失败（不影响主流程）: categoryId={}, version={}, err={}",
                categoryId, version, e.getMessage());
        }
    }

    private static double[] toDoubleArray(float[] arr) {
        if (arr == null) return new double[0];
        double[] d = new double[arr.length];
        for (int i = 0; i < arr.length; i++) d[i] = arr[i];
        return d;
    }

    private static float[] toFloatArray(double[] arr) {
        if (arr == null) return new float[0];
        float[] f = new float[arr.length];
        for (int i = 0; i < arr.length; i++) f[i] = (float) arr[i];
        return f;
    }

    // ======================== 工具方法 ========================

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
        Page<VectorizationTask> pageObj = new Page<>(page, size);
        LambdaQueryWrapper<VectorizationTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(VectorizationTask::getCreatedAt);
        return taskMapper.selectPage(pageObj, wrapper).getRecords();
    }

    private long countPendingInCategory(Long categoryId) {
        return knowledgeMapper.selectCount(
            new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getCategoryId, categoryId)
                .eq(KnowledgeBase::getVectorStatus, "pending"));
    }
}
