package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.entity.Category;
import com.scfx.entity.CategoryMapping;
import com.scfx.service.CategoryMappingService;
import com.scfx.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/category-mapping")
@RequiredArgsConstructor
public class CategoryMappingController {

    private final CategoryMappingService categoryMappingService;
    private final CategoryService categoryService;

    // 获取所有映射规则
    @GetMapping("/list")
    public Result<List<CategoryMapping>> list() {
        return Result.success(categoryMappingService.list());
    }

    // 创建映射规则
    @PostMapping
    public Result<Void> create(@RequestBody CategoryMapping mapping) {
        categoryMappingService.save(mapping);
        return Result.success();
    }

    // 更新映射规则
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CategoryMapping mapping) {
        mapping.setId(id);
        categoryMappingService.updateById(mapping);
        return Result.success();
    }

    // 删除映射规则
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryMappingService.removeById(id);
        return Result.success();
    }

    // 测试映射预览
    @GetMapping("/preview")
    public Result<Category> preview(
            @RequestParam String source,
            @RequestParam(required = false) String variety,
            @RequestParam(required = false) String reportType) {
        Long categoryId = categoryMappingService.preview(source, variety, reportType);
        Category category = categoryService.getById(categoryId);
        return Result.success(category);
    }
}