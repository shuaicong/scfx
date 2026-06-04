package com.scfx.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.DrCoord;
import com.scfx.entity.KnowledgeBase;
import com.scfx.entity.KnowledgeChunk;
import com.scfx.entity.KnowledgeViz;
import com.scfx.mapper.DrCoordMapper;
import com.scfx.mapper.DrVersionMapper;
import com.scfx.mapper.KnowledgeChunkMapper;
import com.scfx.mapper.KnowledgeBaseMapper;
import com.scfx.mapper.KnowledgeVizMapper;
import com.scfx.service.DocumentPipeline;
import com.scfx.service.FileStorageService;
import com.scfx.service.KnowledgeBaseService;
import com.scfx.service.VectorStore;
import com.scfx.service.VectorTaskService;
import com.scfx.service.impl.KnowledgeSearchService;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/knowledge")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final VectorTaskService vectorTaskService;
    private final DocumentPipeline documentPipeline;
    private final VectorStore vectorStore;
    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final KnowledgeVizMapper knowledgeVizMapper;
    private final DrCoordMapper drCoordMapper;
    private final DrVersionMapper drVersionMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final KnowledgeSearchService knowledgeSearchService;
    private final FileStorageService fileStorageService;

    /** 重算锁：key=(categoryId,algorithm)，防重复点击 */
    private final ConcurrentHashMap<String, Boolean> recomputeLocks = new ConcurrentHashMap<>();

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
        boolean contentChanged = false;
        if (payload.containsKey("title")) kb.setTitle((String) payload.get("title"));
        if (payload.containsKey("content")) {
            kb.setContent((String) payload.get("content"));
            kb.setTableMeta(null);  // 清除旧 table_meta，前端降级为 marked 渲染
            contentChanged = true;
        }
        if (payload.containsKey("sourceType")) kb.setSourceType((String) payload.get("sourceType"));
        if (payload.containsKey("author")) kb.setAuthor((String) payload.get("author"));
        if (payload.containsKey("categoryId")) {
            kb.setCategoryId(((Number) payload.get("categoryId")).longValue());
        }
        knowledgeBaseService.updateById(kb);
        // 内容变更 → 异步触发重新切片+向量化
        if (contentChanged && kb.getCategoryId() != null) {
            vectorTaskService.processSingle(kb.getId());
        }
        return Result.success(kb);
    }

    @PostMapping("/{id}/revectorize")
    public Result<Void> revectorize(@PathVariable Long id) {
        KnowledgeBase kb = knowledgeBaseService.getById(id);
        if (kb == null) return Result.error("知识不存在");
        // 由 @Async("vectorEmbedExecutor") 异步执行
        vectorTaskService.processSingle(kb.getId());
        return Result.success(null);
    }

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "title", required = false) String title) {
        try {
            String fileName = file.getOriginalFilename();
            if (title == null || title.isEmpty()) {
                title = fileName != null ? fileName.replaceFirst("\\.[^.]+$", "") : "未命名文档";
            }
            // 文件大小限制 10MB
            if (file.getSize() > 10 * 1024 * 1024) {
                return Result.error("文件不能超过 10MB");
            }
            boolean isDocx = fileName != null && fileName.endsWith(".docx");

            KnowledgeBase kb = new KnowledgeBase();
            kb.setTitle(title);
            // .docx 是二进制格式，不能按 UTF-8 读取；后续由解析管道异步提取纯文本
            if (isDocx) {
                kb.setContent("");
                kb.setContentHash(DigestUtils.md5Hex(Long.toString(file.getSize())));
            } else {
                String content = new String(file.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
                if (content.length() > 10 * 1024 * 1024) {
                    content = content.substring(0, 10 * 1024 * 1024);
                }
                kb.setContent(content);
                kb.setContentHash(DigestUtils.md5Hex(content));
            }
            kb.setSourceType("upload");
            kb.setSourceName("人工录入");
            kb.setCategoryId(categoryId);
            kb.setFileType(isDocx ? "docx" : "txt");
            kb.setVectorStatus("pending");
            // 先入库获取 ID
            knowledgeBaseService.save(kb);
            // .docx 文件保存原始文件供预览
            if (isDocx) {
                try {
                    String storedPath = fileStorageService.store(file, categoryId, kb.getId());
                    kb.setFilePath(storedPath);
                    knowledgeBaseService.updateById(kb);
                } catch (Exception e) {
                    log.warn("保存docx文件失败(不影响入库): {}", e.getMessage());
                }
            }

            // 触发异步解析管道（提取文本 → 切片入库）
            documentPipeline.start(kb.getId());
            // 触发异步向量化（现有流程：BGE-M3 + DashScope）
            vectorTaskService.processSingle(kb.getId());

            log.info("上传文档成功: knowledgeId={}, title={}", kb.getId(), title);
            return Result.success(Map.of("knowledgeId", kb.getId(), "title", title));
        } catch (Exception e) {
            log.error("上传失败: {}", e.getMessage(), e);
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    /**
     * 语义搜索：对指定分类执行向量检索，返回 Top-K 文档。
     * <p>
     * 请求体：
     *   { "query": "山东玉米价格", "categoryId": 1, "topK": 10 }
     * <p>
     * 搜索覆盖：
     * - 已切片文档（≥500 字）：搜切片向量，按 doc_id 聚合取 MAX score
     * - 未切片文档（&lt;500 字）：搜 DashScope 可视化向量
     * - summary 切片权重 ×1.1
     */
    @PostMapping("/search")
    public Result<List<KnowledgeSearchService.SearchResult>> search(
            @RequestBody Map<String, Object> payload) {
        String query = (String) payload.get("query");
        Number catId = (Number) payload.get("categoryId");
        int topK = payload.containsKey("topK") ? ((Number) payload.get("topK")).intValue() : 10;

        if (query == null || query.isBlank()) {
            return Result.error(400, "查询词不能为空", "EMPTY_QUERY");
        }
        if (catId == null) {
            return Result.error(400, "分类 ID 不能为空", "NO_CATEGORY");
        }

        List<KnowledgeSearchService.SearchResult> results =
            knowledgeSearchService.search(query, catId.longValue(), topK);
        return Result.success(results);
    }

    /**
     * 获取指定知识的切片列表
     */
    @GetMapping("/{knowledgeId}/chunks")
    public Result<List<KnowledgeChunk>> getChunks(@PathVariable Long knowledgeId) {
        List<KnowledgeChunk> chunks = knowledgeChunkMapper.selectByKnowledgeIdAndIsActive(knowledgeId, 1);
        return Result.success(chunks);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long id) {
        KnowledgeBase kb = knowledgeBaseService.getById(id);
        if (kb == null || kb.getFilePath() == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            java.nio.file.Path filePath = java.nio.file.Path.of(kb.getFilePath());
            if (!java.nio.file.Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            byte[] data = java.nio.file.Files.readAllBytes(filePath);
            String contentType = "application/octet-stream";
            if (kb.getFilePath().endsWith(".docx")) {
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + kb.getTitle() + "\"")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(data);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/manual")
    public Result<KnowledgeBase> manualAdd(@RequestBody Map<String, Object> payload) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setTitle((String) payload.get("title"));
        kb.setContent((String) payload.get("content"));
        kb.setSourceType((String) payload.get("source"));
        if (payload.containsKey("categoryId")) {
            kb.setCategoryId(((Number) payload.get("categoryId")).longValue());
        }
        knowledgeBaseService.save(kb);
        // 触发异步向量化
        vectorTaskService.processSingle(kb.getId());
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
     * 获取指定分类的可视化数据（降维坐标 + 相似度）
     * 支持分页/抽样、多算法
     */
    @GetMapping("/{categoryId}/visualization")
    public Result<Map<String, Object>> getVisualization(
            @PathVariable Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "200") int size,
            @RequestParam(defaultValue = "false") boolean sample,
            @RequestParam(defaultValue = "pca") String algorithm) {

        // sample/page 冲突：静默忽略 page，记录 warn
        if (sample && page != 1) {
            log.warn("sample=true 与 page 冲突，忽略 page | categoryId={}", categoryId);
            page = 1;
        }

        // 该分类下已向量化的总条数（用于统计）
        long totalVectorized = knowledgeBaseService.count(
            new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getCategoryId, categoryId)
                .eq(KnowledgeBase::getDeleted, 0)
                .eq(KnowledgeBase::getVectorStatus, "vectorized")
        );

        // 查询 t_knowledge_dr_coords 获取坐标
        Integer currentVersion = drVersionMapper.selectCurrentVersion(categoryId, algorithm);
        boolean hasData = currentVersion != null;

        // PCA 无数据时尝试从旧表迁移
        if (!hasData && "pca".equals(algorithm)) {
            hasData = tryPcaMigration(categoryId);
            if (hasData) {
                currentVersion = drVersionMapper.selectCurrentVersion(categoryId, algorithm);
            }
        }

        if (!hasData) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("points", Collections.emptyList());
            empty.put("total", totalVectorized);
            empty.put("page", sample ? 1 : page);
            empty.put("size", 0);
            empty.put("sample", sample);
            empty.put("hasData", false);
            empty.put("version", null);
            empty.put("similarities", Collections.emptyMap());
            return Result.success(empty);
        }

        // 读取坐标（分页或全量）
        List<DrCoord> allCoords = drCoordMapper.selectByCategoryAndAlgorithm(categoryId, algorithm, currentVersion);
        if (allCoords.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("points", Collections.emptyList());
            empty.put("total", totalVectorized);
            empty.put("page", sample ? 1 : page);
            empty.put("size", 0);
            empty.put("sample", sample);
            empty.put("hasData", true);
            empty.put("version", currentVersion);
            empty.put("similarities", Collections.emptyMap());
            return Result.success(empty);
        }

        // 分页/抽样
        List<DrCoord> pagedCoords;
        if (sample && allCoords.size() > size) {
            Collections.shuffle(allCoords, new Random());
            pagedCoords = allCoords.subList(0, Math.min(size, allCoords.size()));
        } else {
            int from = (page - 1) * size;
            int to = Math.min(from + size, allCoords.size());
            if (from >= allCoords.size()) {
                pagedCoords = Collections.emptyList();
            } else {
                pagedCoords = allCoords.subList(from, to);
            }
        }

        if (pagedCoords.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("points", Collections.emptyList());
            empty.put("total", totalVectorized);
            empty.put("page", sample ? 1 : page);
            empty.put("size", 0);
            empty.put("sample", sample);
            empty.put("hasData", true);
            empty.put("version", currentVersion);
            empty.put("similarities", Collections.emptyMap());
            return Result.success(empty);
        }

        // 查询知识基本信息
        Set<Long> coordIds = pagedCoords.stream().map(DrCoord::getKnowledgeId).collect(Collectors.toSet());
        List<KnowledgeBase> kbList = knowledgeBaseService.list(
            new LambdaQueryWrapper<KnowledgeBase>()
                .in(KnowledgeBase::getId, coordIds)
                .eq(KnowledgeBase::getDeleted, 0)
        );
        Map<Long, KnowledgeBase> kbMap = kbList.stream().collect(Collectors.toMap(KnowledgeBase::getId, kb -> kb));

        // 查询向量数据（用于相似度 + 空向量检测）
        Set<Long> allIds = allCoords.stream().map(DrCoord::getKnowledgeId).collect(Collectors.toSet());
        Map<Long, String> vizStatusMap = new HashMap<>();
        Map<Long, float[]> vectorMap = new HashMap<>();
        vectorStore.getByCategoryId(categoryId).stream()
            .filter(e -> allIds.contains(e.getKnowledgeId()))
            .forEach(e -> {
                vizStatusMap.put(e.getKnowledgeId(), e.getStatus());
                if (e.getVector() != null) {
                    vectorMap.put(e.getKnowledgeId(), e.getVector());
                }
            });

        // 构建点集
        List<Map<String, Object>> points = pagedCoords.stream().map(coord -> {
            KnowledgeBase kb = kbMap.get(coord.getKnowledgeId());
            float[] vec = vectorMap.get(coord.getKnowledgeId());
            boolean isZero = vec == null || isZeroVector(vec);

            Map<String, Object> item = new HashMap<>();
            item.put("id", coord.getKnowledgeId());
            item.put("title", kb != null ? kb.getTitle() : null);
            item.put("x", coord.getX());
            item.put("y", coord.getY());
            item.put("z", coord.getZ() != null ? coord.getZ() : 0);
            item.put("vectorStatus", kb != null ? kb.getVectorStatus() : null);
            item.put("vizStatus", vizStatusMap.getOrDefault(coord.getKnowledgeId(), "pending"));
            item.put("contentType", kb != null ? detectContentType(kb) : "text");
            item.put("isZeroVector", isZero);
            return item;
        }).collect(Collectors.toList());

        // 计算当前页内相似度（仅可见点之间，裁剪体积）
        Map<Long, List<Map<String, Object>>> similarities = computeTopNeighbors(vectorMap, coordIds, 5);

        Map<String, Object> result = new HashMap<>();
        result.put("points", points);
        result.put("total", totalVectorized);
        result.put("page", sample ? 1 : page);
        result.put("size", points.size());
        result.put("sample", sample);
        result.put("hasData", true);
        result.put("version", currentVersion);
        result.put("similarities", similarities);
        return Result.success(result);
    }

    /**
     * 手动触发指定分类的降维重算
     */
    @PostMapping("/{categoryId}/visualization/recompute")
    public Result<Map<String, Object>> recomputeVisualization(
            @PathVariable Long categoryId,
            @RequestBody Map<String, String> body) {
        String algorithm = body != null ? body.getOrDefault("algorithm", "pca") : "pca";

        // 幂等锁
        String lockKey = categoryId + ":" + algorithm;
        Boolean existing = recomputeLocks.putIfAbsent(lockKey, Boolean.TRUE);
        if (existing != null && existing) {
            return Result.success(Map.of("status", "computing"));
        }

        try {
            log.info("重算触发: categoryId={}, algorithm={}", categoryId, algorithm);
            vectorTaskService.recomputeDR(categoryId, algorithm);

            // 如果是 PCA，同时写旧表保持兼容
            if ("pca".equals(algorithm)) {
                try {
                    List<DrCoord> coords = drCoordMapper.selectByCategoryAndAlgorithm(
                        categoryId, algorithm,
                        drVersionMapper.selectCurrentVersion(categoryId, algorithm));
                    for (DrCoord c : coords) {
                        KnowledgeBase kb = new KnowledgeBase();
                        kb.setId(c.getKnowledgeId());
                        kb.setVizX(c.getX());
                        kb.setVizY(c.getY());
                        kb.setVizZ(c.getZ());
                        knowledgeBaseMapper.updateById(kb);
                    }
                } catch (Exception e) {
                    log.warn("PCA 旧表回写失败（不影响主流程）: categoryId={}", categoryId);
                }
            }

            return Result.success(Map.of("status", "done"));

        } catch (IllegalArgumentException e) {
            String msg = e.getMessage();
            String errorCode = "DR_COMPUTE_FAILED";
            String userMsg = msg;

            if (msg != null && msg.contains(":")) {
                String[] parts = msg.split(":", 2);
                errorCode = parts[0];
                userMsg = parts[1];
            }

            log.warn("重算失败: categoryId={}, algorithm={}, errorCode={}", categoryId, algorithm, errorCode);
            return Result.error(400, userMsg, errorCode);

        } catch (Exception e) {
            log.error("重算异常: categoryId={}, algorithm={}", categoryId, algorithm, e);
            return Result.error(500, "降维计算失败，请重试", "DR_COMPUTE_FAILED");

        } finally {
            recomputeLocks.remove(lockKey);
        }
    }

    /**
     * 获取指定知识的详情（含实时 top-5 相似条目）
     */
    @GetMapping("/{knowledgeId}/point-detail")
    public Result<Map<String, Object>> pointDetail(
            @PathVariable Long knowledgeId,
            @RequestParam(defaultValue = "pca") String algorithm) {

        // 1. 基本信息
        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeId);
        if (kb == null) {
            return Result.error(404, "知识不存在", "NO_DATA");
        }

        // 2. 降维坐标
        DrCoord coord = drCoordMapper.selectByKnowledgeIdAndAlgorithm(knowledgeId, algorithm);

        // 3. 向量数据
        KnowledgeViz viz = knowledgeVizMapper.selectByKnowledgeId(knowledgeId);
        float[] fullVector = viz != null ? viz.getVector768() : null;
        boolean isZero = fullVector == null || isZeroVector(fullVector);
        double[] preview = fullVector != null
            ? toDoubleArray(fullVector, 20)
            : new double[0];

        // 4. 全局归一化最大值
        double globalMaxAbs = 0;
        if (kb.getCategoryId() != null && fullVector != null) {
            Map<Long, float[]> allVectors = vectorStore.getVectorMapByCategoryId(kb.getCategoryId());
            for (float[] v : allVectors.values()) {
                if (v != null) {
                    for (float f : v) {
                        double abs = Math.abs(f);
                        if (abs > globalMaxAbs) globalMaxAbs = abs;
                    }
                }
            }
        }

        // 5. 实时计算 top-5 相似条目
        List<Map<String, Object>> neighbors = new ArrayList<>();
        if (!isZero && fullVector != null && kb.getCategoryId() != null) {
            neighbors = computeSimilarNeighbors(knowledgeId, fullVector, kb.getCategoryId(), 5);
        }

        // 6. 组装响应
        Map<String, Object> result = new HashMap<>();
        result.put("id", kb.getId());
        result.put("title", kb.getTitle());
        result.put("content", kb.getContent() != null && kb.getContent().length() > 500
            ? kb.getContent().substring(0, 500) : kb.getContent());
        result.put("contentHtml", kb.getContentHtml());
        result.put("algorithm", algorithm);
        result.put("coords", coord != null
            ? Map.of("x", coord.getX(), "y", coord.getY(), "z", coord.getZ() != null ? coord.getZ() : 0)
            : Map.of("x", 0, "y", 0, "z", 0));
        result.put("vectorPreview", preview);
        result.put("fullVector", fullVector != null ? toDoubleArray(fullVector, fullVector.length) : new double[0]);
        result.put("globalMaxAbs", globalMaxAbs > 0 ? globalMaxAbs : 1.0);
        result.put("vectorStatus", kb.getVectorStatus());
        result.put("vizStatus", viz != null ? viz.getVizStatus() : "pending");
        result.put("contentType", detectContentType(kb));
        result.put("isZeroVector", isZero);
        result.put("neighbors", neighbors);

        return Result.success(result);
    }

    // ======================== PCA 迁移 ========================

    /**
     * 将旧表 t_knowledge_base.viz_x/y/z 迁移到 t_knowledge_dr_coords
     * 使用 t_dr_version INSERT IGNORE 做门闩，防并发竞态
     */
    private boolean tryPcaMigration(Long categoryId) {
        Long oldDataCount = knowledgeBaseService.count(
            new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getCategoryId, categoryId)
                .eq(KnowledgeBase::getDeleted, 0)
                .isNotNull(KnowledgeBase::getVizX)
                .isNotNull(KnowledgeBase::getVizY)
        );
        if (oldDataCount == 0) return false;

        if (drVersionMapper.tryInitVersion(categoryId, "pca") > 0) {
            log.info("PCA 数据迁移: categoryId={}, count={}", categoryId, oldDataCount);
            List<KnowledgeBase> oldData = knowledgeBaseService.list(
                new LambdaQueryWrapper<KnowledgeBase>()
                    .eq(KnowledgeBase::getCategoryId, categoryId)
                    .eq(KnowledgeBase::getDeleted, 0)
                    .isNotNull(KnowledgeBase::getVizX)
                    .isNotNull(KnowledgeBase::getVizY)
            );
            for (KnowledgeBase oldKb : oldData) {
                DrCoord c = new DrCoord();
                c.setKnowledgeId(oldKb.getId());
                c.setCategoryId(categoryId);
                c.setAlgorithm("pca");
                c.setVersion(1);
                c.setX(oldKb.getVizX());
                c.setY(oldKb.getVizY());
                c.setZ(oldKb.getVizZ() != null ? oldKb.getVizZ() : 0);
                drCoordMapper.upsert(c);
            }
        }

        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        Integer v = drVersionMapper.selectCurrentVersion(categoryId, "pca");
        return v != null;
    }

    // ======================== 相似度计算 ========================

    private Map<Long, List<Map<String, Object>>> computeTopNeighbors(
            Map<Long, float[]> vectorMap, Set<Long> visibleIds, int topK) {
        Map<Long, List<Map<String, Object>>> result = new HashMap<>();
        List<Long> ids = new ArrayList<>(visibleIds);
        if (ids.size() < 2) return result;

        for (int i = 0; i < ids.size(); i++) {
            Long id1 = ids.get(i);
            float[] v1 = vectorMap.get(id1);
            if (v1 == null || isZeroVector(v1)) continue;

            List<Map<String, Object>> neighbors = new ArrayList<>();
            for (int j = 0; j < ids.size(); j++) {
                if (i == j) continue;
                Long id2 = ids.get(j);
                float[] v2 = vectorMap.get(id2);
                if (v2 == null || isZeroVector(v2)) continue;

                double sim = cosineSimilarity(v1, v2);
                neighbors.add(Map.of("id", id2, "score", Math.round(sim * 10000) / 10000.0));
            }

            neighbors.sort((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")));
            result.put(id1, neighbors.subList(0, Math.min(topK, neighbors.size())));
        }
        return result;
    }

    private List<Map<String, Object>> computeSimilarNeighbors(
            Long knowledgeId, float[] vector, Long categoryId, int topK) {
        List<Map<String, Object>> neighbors = new ArrayList<>();

        Map<Long, float[]> vectorMap = vectorStore.getVectorMapByCategoryId(categoryId);
        if (vectorMap.size() < 2) return neighbors;

        List<Long> ids = new ArrayList<>();
        List<float[]> vecs = new ArrayList<>();
        for (Map.Entry<Long, float[]> e : vectorMap.entrySet()) {
            if (e.getKey().equals(knowledgeId)) continue;
            if (e.getValue() == null || isZeroVector(e.getValue())) continue;
            ids.add(e.getKey());
            vecs.add(e.getValue());
        }

        List<KnowledgeBase> kbList = knowledgeBaseService.list(
            new LambdaQueryWrapper<KnowledgeBase>()
                .in(KnowledgeBase::getId, ids)
                .eq(KnowledgeBase::getDeleted, 0)
        );
        Map<Long, String> titleMap = kbList.stream()
            .collect(Collectors.toMap(KnowledgeBase::getId, kb -> kb.getTitle() != null ? kb.getTitle() : ""));

        List<Map<String, Object>> candidates = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            double sim = cosineSimilarity(vector, vecs.get(i));
            Long id = ids.get(i);
            candidates.add(Map.of(
                "id", id,
                "title", titleMap.getOrDefault(id, ""),
                "score", Math.round(sim * 10000) / 10000.0,
                "isZeroVector", false
            ));
        }

        candidates.sort((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")));
        return candidates.subList(0, Math.min(topK, candidates.size()));
    }

    // ======================== 工具方法 ========================

    private String detectContentType(KnowledgeBase kb) {
        if (kb.getContentHtml() != null && kb.getContentHtml().contains("<img")) {
            return "multimodal";
        }
        return "text";
    }

    private boolean isZeroVector(float[] v) {
        for (float f : v) if (f != 0) return false;
        return true;
    }

    private double[] toDoubleArray(float[] arr, int limit) {
        int len = Math.min(arr.length, limit);
        double[] result = new double[len];
        for (int i = 0; i < len; i++) result[i] = arr[i];
        return result;
    }

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

    @Data
    @AllArgsConstructor
    public static class BatchResult {
        private int total;
        private int success;
        private int skipped;
        private int failed;
    }
}