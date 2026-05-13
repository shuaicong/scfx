package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.entity.VectorizationTask;
import com.scfx.service.VectorTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vectorization")
@RequiredArgsConstructor
public class VectorizationController {

    private final VectorTaskService vectorTaskService;

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
}