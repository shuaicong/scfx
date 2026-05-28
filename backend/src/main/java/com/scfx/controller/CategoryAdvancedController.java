package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.service.CategoryAdvancedService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/category")
@RequiredArgsConstructor
public class CategoryAdvancedController {
    private final CategoryAdvancedService service;

    @GetMapping("/stats")
    public Result<?> stats() {
        return Result.success(service.getStats());
    }

    @GetMapping("/{id}/history")
    public Result<?> history(@PathVariable Long id) {
        return Result.success(service.getOperationHistory(id));
    }

    @GetMapping("/history/recent")
    public Result<?> recentHistory() {
        return Result.success(service.getRecentOperations());
    }

    @GetMapping("/merge-suggestions")
    public Result<?> mergeSuggestions() {
        return Result.success(service.getMergeSuggestions());
    }

    @GetMapping("/export")
    public Result<?> export() {
        return Result.success(Map.of("data", service.exportCategories()));
    }

    @PostMapping("/import")
    public Result<?> importCategories(@RequestBody Map<String, String> body) {
        String json = body.get("data");
        int count = service.importCategories(json, body.getOrDefault("operator", "system"));
        return Result.success(Map.of("imported", count));
    }

    @GetMapping("/hot-analysis")
    public Result<?> hotAnalysis() {
        return Result.success(service.getHotAnalysis());
    }
}