package com.scfx.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.KnowledgeBase;
import com.scfx.service.KnowledgeBaseService;
import com.scfx.service.VectorStore;
import com.scfx.service.VectorTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final VectorTaskService vectorTaskService;
    private final VectorStore vectorStore;

    @GetMapping("/list")
    public Result<Map<?, ?>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sourceType,
            @RequestParam(required = false) String vectorStatus,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String executionId) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        if (sourceType != null && !sourceType.isEmpty()) {
            wrapper.eq(KnowledgeBase::getSourceType, sourceType);
        }
        if (vectorStatus != null && !vectorStatus.isEmpty()) {
            wrapper.eq(KnowledgeBase::getVectorStatus, vectorStatus);
        }
        if (executionId != null && !executionId.isEmpty()) {
            wrapper.eq(KnowledgeBase::getExecutionId, executionId);
        }
        if (categoryId != null) {
            wrapper.eq(KnowledgeBase::getCategoryId, categoryId);
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
        knowledgeBaseService.removeWithViz(id);
        return Result.success(null);
    }

    @PutMapping("/{id}")
    public Result<KnowledgeBase> update(@PathVariable Long id, @RequestBody Map<String, Object> payload) {
        KnowledgeBase kb = knowledgeBaseService.getById(id);
        if (kb == null) return Result.error("Not found");
        if (payload.containsKey("title")) kb.setTitle((String) payload.get("title"));
        if (payload.containsKey("content")) kb.setContent((String) payload.get("content"));
        if (payload.containsKey("sourceType")) kb.setSourceType((String) payload.get("sourceType"));
        if (payload.containsKey("author")) kb.setAuthor((String) payload.get("author"));
        knowledgeBaseService.updateById(kb);
        return Result.success(kb);
    }

    @PostMapping("/{id}/revectorize")
    public Result<Void> revectorize(@PathVariable Long id) {
        KnowledgeBase kb = knowledgeBaseService.getById(id);
        if (kb == null) return Result.error("知识不存在");
        // 单条重新向量化（双向量计算，但不触发全局 PCA 重算）
        vectorTaskService.processSingle(kb.getId());
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

    /**
     * 获取指定分类的 2D 可视化数据（PCA 降维后）
     * 支持分页、随机抽样、内容类型检测、条目间相似度
     *
     * @param sample true=随机抽样（忽略 page），false=标准分页
     */
    @GetMapping("/{categoryId}/visualization")
    public Result<Map<String, Object>> getVisualization(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "200") int size,
            @RequestParam(defaultValue = "false") boolean sample) {

        // 1. 查总数
        long total = knowledgeBaseService.count(
            new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getCategoryId, categoryId)
                .eq(KnowledgeBase::getDeleted, 0)
                .isNotNull(KnowledgeBase::getVizX)
                .isNotNull(KnowledgeBase::getVizY)
        );

        // 2. 取数据（分页 or 随机抽样）
        List<KnowledgeBase> list;
        if (sample && total > size) {
            // 随机抽样：取全量 ID → shuffle → 按需取
            List<Long> allIds = knowledgeBaseService.list(
                new LambdaQueryWrapper<KnowledgeBase>()
                    .select(KnowledgeBase::getId)
                    .eq(KnowledgeBase::getCategoryId, categoryId)
                    .eq(KnowledgeBase::getDeleted, 0)
                    .isNotNull(KnowledgeBase::getVizX)
                    .isNotNull(KnowledgeBase::getVizY)
            ).stream().map(KnowledgeBase::getId).collect(Collectors.toList());

            Collections.shuffle(allIds, new Random());
            List<Long> sampledIds = allIds.subList(0, Math.min(size, allIds.size()));

            list = knowledgeBaseService.list(
                new LambdaQueryWrapper<KnowledgeBase>()
                    .in(KnowledgeBase::getId, sampledIds)
                    .eq(KnowledgeBase::getDeleted, 0)
            );
        } else {
            Page<KnowledgeBase> p = knowledgeBaseService.page(
                new Page<>(page, size),
                new LambdaQueryWrapper<KnowledgeBase>()
                    .eq(KnowledgeBase::getCategoryId, categoryId)
                    .eq(KnowledgeBase::getDeleted, 0)
                    .isNotNull(KnowledgeBase::getVizX)
                    .isNotNull(KnowledgeBase::getVizY)
                    .orderByDesc(KnowledgeBase::getCreatedAt)
            );
            list = p.getRecords();
        }

        if (list.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("points", Collections.emptyList());
            empty.put("total", total);
            empty.put("page", page);
            empty.put("size", size);
            empty.put("sample", sample);
            empty.put("similarities", Collections.emptyMap());
            return Result.success(empty);
        }

        // 3. 通过 VectorStore 批量查询 viz 状态 + 768d 向量（用于相似度计算）
        List<Long> ids = list.stream().map(KnowledgeBase::getId).collect(Collectors.toList());
        Map<Long, String> vizStatusMap = new HashMap<>();
        Map<Long, float[]> vectorMap = new HashMap<>();
        vectorStore.getByCategoryId(categoryId).stream()
            .filter(e -> ids.contains(e.getKnowledgeId()))
            .forEach(e -> {
                vizStatusMap.put(e.getKnowledgeId(), e.getStatus());
                if (e.getVector() != null) {
                    vectorMap.put(e.getKnowledgeId(), e.getVector());
                }
            });

        // 4. 计算条目间余弦相似度（top-5 邻居）
        Map<Long, List<Map<String, Object>>> similarities = computeTopNeighbors(vectorMap, 5);

        // 5. 构建结果点集（含 contentType 检测）
        List<Map<String, Object>> points = list.stream().map(kb -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", kb.getId());
            item.put("title", kb.getTitle());
            item.put("x", kb.getVizX());
            item.put("y", kb.getVizY());
            item.put("z", kb.getVizZ() != null ? kb.getVizZ() : 0);
            item.put("vectorStatus", kb.getVectorStatus());
            item.put("vizStatus", vizStatusMap.getOrDefault(kb.getId(), "pending"));
            item.put("contentType", detectContentType(kb));
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("points", points);
        result.put("total", total);
        result.put("page", page);
        result.put("size", points.size());
        result.put("sample", sample);
        result.put("similarities", similarities);
        return Result.success(result);
    }

    /**
     * 判断知识内容类型：是否包含图片
     */
    private String detectContentType(KnowledgeBase kb) {
        if (kb.getContentHtml() != null && kb.getContentHtml().contains("<img")) {
            return "multimodal";
        }
        return "text";
    }

    /**
     * 在给定向量集合内计算每个点的 topK 余弦相似邻居
     */
    private Map<Long, List<Map<String, Object>>> computeTopNeighbors(
            Map<Long, float[]> vectorMap, int topK) {
        Map<Long, List<Map<String, Object>>> result = new HashMap<>();
        List<Long> ids = new ArrayList<>(vectorMap.keySet());
        if (ids.size() < 2) return result;

        for (int i = 0; i < ids.size(); i++) {
            Long id1 = ids.get(i);
            float[] v1 = vectorMap.get(id1);
            if (v1 == null) continue;

            List<Map<String, Object>> neighbors = new ArrayList<>();
            for (int j = 0; j < ids.size(); j++) {
                if (i == j) continue;
                Long id2 = ids.get(j);
                float[] v2 = vectorMap.get(id2);
                if (v2 == null) continue;

                double sim = cosineSimilarity(v1, v2);
                Map<String, Object> entry = new HashMap<>();
                entry.put("id", id2);
                entry.put("score", Math.round(sim * 10000) / 10000.0);
                neighbors.add(entry);
            }

            neighbors.sort((a, b) -> Double.compare(
                (Double) b.get("score"), (Double) a.get("score")));
            result.put(id1, neighbors.subList(0, Math.min(topK, neighbors.size())));
        }
        return result;
    }

    /**
     * 余弦相似度
     */
    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0 ? 0 : dot / denom;
    }

    /**
     * 手动触发指定分类的 PCA 重算
     */
    @PostMapping("/{categoryId}/visualization/recompute")
    public Result<Void> recomputeVisualization(@PathVariable Long categoryId) {
        Map<Long, float[]> vectorMap = vectorStore.getVectorMapByCategoryId(categoryId);
        if (vectorMap.size() < 2) {
            return Result.error("可视化数据不足（至少需要 2 条已向量化的知识）");
        }
        // 全量重算（清理旧基线，重新构建）
        vectorTaskService.recomputePCAFull(categoryId);
        return Result.success(null);
    }
}