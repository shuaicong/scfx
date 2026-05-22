package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.service.KnowledgeCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeCategoryController {
    private final KnowledgeCategoryService service;

    @GetMapping("/uncategorized")
    public Result<?> getUncategorized() {
        return Result.success(service.getUncategorizedKnowledgeIds());
    }

    @PostMapping("/{id}/categories")
    public Result<?> assign(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> categoryIds = (List<Long>) body.get("categoryIds");
        String operator = (String) body.getOrDefault("operator", "system");
        service.assign(id, categoryIds, operator);
        return Result.success(Map.of("assigned", true));
    }

    @DeleteMapping("/{id}/categories/{categoryId}")
    public Result<?> remove(@PathVariable Long id, @PathVariable Long categoryId) {
        service.remove(id, categoryId);
        return Result.success(Map.of("removed", true));
    }

    @PutMapping("/{id}/categories/replace")
    public Result<?> replace(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Long> categoryIds = (List<Long>) body.get("categoryIds");
        String operator = (String) body.getOrDefault("operator", "system");
        service.assign(id, categoryIds, operator);
        return Result.success(Map.of("replaced", true));
    }

    @GetMapping("/{id}/categories")
    public Result<?> getCategories(@PathVariable Long id) {
        return Result.success(service.getCategoryIds(id));
    }

    @GetMapping("/{id}/move-history")
    public Result<?> getMoveHistory(@PathVariable Long id) {
        return Result.success(service.getMoveHistory(id));
    }

    @GetMapping("/category/{categoryId}/count")
    public Result<?> getKnowledgeCount(@PathVariable Long categoryId) {
        return Result.success(Map.of("count", service.getKnowledgeCount(categoryId)));
    }
}