package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.entity.Category;
import com.scfx.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/category")
@RequiredArgsConstructor
public class CategoryController {
    private final CategoryService service;

    @GetMapping("/tree")
    public Result<?> tree() {
        return Result.success(Map.of(
            "data", service.getTree(),
            "version", service.getVersion()
        ));
    }

    @GetMapping("/version")
    public Result<?> version() {
        return Result.success(Map.of("version", service.getVersion()));
    }

    @GetMapping("/{id}")
    public Result<?> getById(@PathVariable Long id) {
        return Result.success(service.getById(id));
    }

    @PostMapping
    public Result<?> create(@RequestBody Category category) {
        return Result.success(service.create(category));
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody Category category) {
        String operator = category.getLastOperatedBy();
        return Result.success(service.update(id, category, operator));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id, @RequestParam(required = false, defaultValue = "system") String operator) {
        service.delete(id, operator);
        return Result.success(Map.of("deleted", true));
    }

    @PostMapping("/{id}/restore")
    public Result<?> restore(@PathVariable Long id, @RequestParam(required = false, defaultValue = "system") String operator) {
        service.restore(id, operator);
        return Result.success(Map.of("restored", true));
    }

    @DeleteMapping("/{id}/permanent")
    public Result<?> permanentDelete(@PathVariable Long id) {
        service.permanentDelete(id);
        return Result.success(Map.of("permanentDeleted", true));
    }

    @GetMapping("/trash")
    public Result<?> trash() {
        return Result.success(service.getTrash());
    }

    @GetMapping("/search")
    public Result<?> search(@RequestParam String name) {
        return Result.success(service.search(name));
    }

    @GetMapping("/preview/{id}")
    public Result<?> preview(@PathVariable Long id) {
        return Result.success(service.getPreview(id));
    }
}