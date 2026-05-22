package com.scfx.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 向量调用监控埋点
 * 统计两个模型（硅基流动 + DashScope）的 API 调用耗时、成功率
 */
@Slf4j
@Component
public class VectorMetrics {

    private final ConcurrentHashMap<String, ModelMetrics> metricsMap = new ConcurrentHashMap<>();

    /**
     * 记录一次 API 调用结果
     * @param model   模型标识：siliconflow / dashscope
     * @param durationMs 调用耗时（毫秒）
     * @param success    是否成功
     */
    public void record(String model, long durationMs, boolean success) {
        ModelMetrics m = metricsMap.computeIfAbsent(model, k -> new ModelMetrics());
        m.totalCalls.incrementAndGet();
        m.totalDurationMs.addAndGet(durationMs);
        if (durationMs > m.maxDurationMs.get()) {
            m.maxDurationMs.set(durationMs);
        }
        if (success) {
            m.successCount.incrementAndGet();
        } else {
            m.failCount.incrementAndGet();
        }
        m.lastCalledAt.set(System.currentTimeMillis());

        log.info("[VectorMetrics] model={}, duration={}ms, success={}, totalCalls={}, avgDuration={}ms, successRate={}%",
            model, durationMs, success,
            m.totalCalls.get(), m.getAvgDuration(), m.getSuccessRate());
    }

    /**
     * 获取所有模型的统计摘要
     */
    public Map<String, Object> getStats() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, ModelMetrics> entry : metricsMap.entrySet()) {
            ModelMetrics m = entry.getValue();
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("totalCalls", m.totalCalls.get());
            stat.put("successCount", m.successCount.get());
            stat.put("failCount", m.failCount.get());
            stat.put("avgDurationMs", m.getAvgDuration());
            stat.put("maxDurationMs", m.maxDurationMs.get());
            stat.put("successRate", m.getSuccessRate());
            stat.put("lastCalledAt", new Date(m.lastCalledAt.get()));
            result.put(entry.getKey(), stat);
        }
        return result;
    }

    /**
     * 重置所有统计
     */
    public void reset() {
        metricsMap.clear();
        log.info("[VectorMetrics] 所有统计已重置");
    }

    // ======================== 内部类 ========================

    private static class ModelMetrics {
        private final AtomicLong totalCalls = new AtomicLong(0);
        private final AtomicLong successCount = new AtomicLong(0);
        private final AtomicLong failCount = new AtomicLong(0);
        private final AtomicLong totalDurationMs = new AtomicLong(0);
        private final AtomicLong maxDurationMs = new AtomicLong(0);
        private final AtomicLong lastCalledAt = new AtomicLong(0);

        double getAvgDuration() {
            long calls = totalCalls.get();
            return calls == 0 ? 0 : totalDurationMs.get() / (double) calls;
        }

        int getSuccessRate() {
            long calls = totalCalls.get();
            return calls == 0 ? 100 : (int) (successCount.get() * 100 / calls);
        }
    }
}
