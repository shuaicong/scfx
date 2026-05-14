package com.scfx.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.KnowledgeBase;
import com.scfx.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @GetMapping("/list")
    public Result<Map<?, ?>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String vectorStatus,
            @RequestParam(required = false) Integer categoryId) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        if (sourceType != null && !sourceType.isEmpty()) {
            wrapper.eq(KnowledgeBase::getSourceType, sourceType);
        }
        if (vectorStatus != null && !vectorStatus.isEmpty()) {
            wrapper.eq(KnowledgeBase::getVectorStatus, vectorStatus);
        }
        wrapper.eq(KnowledgeBase::getDeleted, 0).orderByDesc(KnowledgeBase::getCreatedAt);
        Page<KnowledgeBase> result = knowledgeBaseService.page(new Page<>(page, size), wrapper);
        Map<String, Object> data = new HashMap<>();
        data.put("records", result.getRecords());
        data.put("total", result.getTotal());
        data.put("pages", result.getPages());
        data.put("current", result.getCurrent());
        data.put("size", result.getSize());
        return Result.success(data);
    }

    @PostMapping("/ingest")
    public Result<Map<?, ?>> ingest(@RequestBody Map<String, Object> payload) {
        // TODO: implement local knowledge ingestion
        return Result.success(Map.of("status", "ok"));
    }

    @GetMapping("/{id}")
    public Result<KnowledgeBase> getById(@PathVariable Long id) {
        KnowledgeBase kb = knowledgeBaseService.getById(id);
        return kb != null ? Result.success(kb) : Result.error("Not found");
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.removeById(id);
        return Result.success(null);
    }

    @PostMapping("/{id}/revectorize")
    public Result<Void> revectorize(@PathVariable Long id) {
        return Result.success(null);
    }

    @PostMapping("/upload")
    public Result<Map<?, ?>> upload(@RequestBody Map<String, Object> payload) {
        return Result.success(Map.of("status", "ok"));
    }

    @PostMapping("/manual")
    public Result<KnowledgeBase> manualAdd(@RequestBody Map<String, Object> payload) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setTitle((String) payload.get("title"));
        kb.setContent((String) payload.get("content"));
        kb.setSourceType((String) payload.get("source"));
        knowledgeBaseService.save(kb);
        return Result.success(kb);
    }

    @GetMapping("/uncategorized/count")
    public Result<Map<String, Long>> getUncategorizedCount() {
        Long count = knowledgeBaseService.count(
            new LambdaQueryWrapper<KnowledgeBase>()
                .isNull(KnowledgeBase::getCategoryId)
                .eq(KnowledgeBase::getDeleted, 0)
        );
        return Result.success(Map.of("count", count));
    }
}