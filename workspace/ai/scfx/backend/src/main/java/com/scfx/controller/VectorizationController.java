package com.scfx.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.PCACalculationRecord;
import com.scfx.entity.VectorizationTask;
import com.scfx.mapper.PCACalculationRecordMapper;
import com.scfx.service.VectorMetrics;
import com.scfx.service.VectorProperties;
import com.scfx.service.VectorTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/vectorization")
@RequiredArgsConstructor
public class VectorizationController {

    private final VectorTaskService vectorTaskService;
    private final VectorProperties vectorProperties;
    private final VectorMetrics vectorMetrics;
    private final PCACalculationRecordMapper calcRecordMapper;

    // 获取向量化配置状态
    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", vectorProperties.isEnabled());
        config.put("mode", vectorProperties.isEnabled() ? "real" : "mock");
        config.put("siliconflow", Map.of(
            "model", vectorProperties.getSiliconflow().getModel(),
            "dimensions", vectorProperties.getSiliconflow().getDimensions(),
            "connectTimeout", vectorProperties.getSiliconflow().getConnectTimeout(),
            "readTimeout", vectorProperties.getSiliconflow().getReadTimeout()
        ));
        config.put("dashscope", Map.of(
            "model", vectorProperties.getDashscope().getModel(),
            "dimensions", vectorProperties.getDashscope().getDimensions(),
            "connectTimeout", vectorProperties.getDashscope().getConnectTimeout(),
            "readTimeout", vectorProperties.getDashscope().getReadTimeout()
        ));
        return Result.success(config);
    }

    // 获取向量化调用监控指标
    @GetMapping("/metrics")
    public Result<Map<String, Object>> metrics() {
        return Result.success(vectorMetrics.getStats());
    }

    // 重置监控指标
    @PostMapping("/metrics/reset")
    public Result<Void> resetMetrics() {
        vectorMetrics.reset();
        return Result.success();
    }

    // 获取向量化任务列表
    @GetMapping("/tasks")
    public Result<List<VectorizationTask>> tasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(vectorTaskService.getTasks(page, size));
    }

    // 获取待向量化数量统计
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(vectorTaskService.getStats());
    }

    // 手动触发分类向量化
    @PostMapping("/trigger/{categoryId}")
    public Result<Void> trigger(@PathVariable Long categoryId) {
        vectorTaskService.triggerCategory(categoryId, "manual");
        return Result.success();
    }

    // 批量手动触发
    @PostMapping("/trigger/batch")
    public Result<Void> triggerBatch(@RequestBody List<Long> categoryIds) {
        for (Long categoryId : categoryIds) {
            vectorTaskService.triggerCategory(categoryId, "manual");
        }
        return Result.success();
    }

    // 重试失败任务
    @PostMapping("/retry/{knowledgeId}")
    public Result<Void> retry(@PathVariable Long knowledgeId) {
        vectorTaskService.retry(knowledgeId);
        return Result.success();
    }

    // 获取 PCA 版本历史
    @GetMapping("/pca-versions/{categoryId}")
    public Result<List<PCACalculationRecord>> pcaVersions(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PCACalculationRecord> result = calcRecordMapper.selectPage(
            new Page<>(page, size),
            new LambdaQueryWrapper<PCACalculationRecord>()
                .eq(PCACalculationRecord::getCategoryId, categoryId)
                .orderByDesc(PCACalculationRecord::getVersion)
        );
        return Result.success(result.getRecords());
    }
}