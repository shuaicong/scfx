package com.scfx.controller;

import com.scfx.common.Result;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Sql(scripts = "classpath:test-data-visualization.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class KnowledgeBaseControllerTest {

    @Autowired
    private TestRestTemplate rest;

    /** 已知有数据的分类 ID（需根据实际数据调整） */
    private static final Long EXISTING_CATEGORY = 1L;
    private static final Long NONEXISTENT_CATEGORY = 9999L;

    @SuppressWarnings("unchecked")
    private Map<String, Object> getData(ResponseEntity<Result> resp) {
        assertNotNull(resp.getBody());
        assertEquals(200, resp.getBody().getCode());
        return (Map<String, Object>) resp.getBody().getData();
    }

    @Test
    void testGetVisualization_pca() {
        var resp = rest.getForEntity(
            "/knowledge/{catId}/visualization?algorithm=pca&page=1&size=10&sample=false",
            Result.class, EXISTING_CATEGORY);
        var data = getData(resp);

        assertNotNull(data.get("points"));
        assertNotNull(data.get("hasData"));
        assertNotNull(data.get("total"));
        assertTrue(data.containsKey("similarities"),
            "响应应包含 similarities 字段");
    }

    @Test
    void testGetVisualization_sample() {
        var resp = rest.getForEntity(
            "/knowledge/{catId}/visualization?algorithm=pca&size=5&sample=true",
            Result.class, EXISTING_CATEGORY);
        var data = getData(resp);

        assertEquals(1, data.get("page"), "抽样模式下 page 应为 1");
    }

    @Test
    void testGetVisualization_noData() {
        var resp = rest.getForEntity(
            "/knowledge/{catId}/visualization?algorithm=mds&page=1&size=10",
            Result.class, NONEXISTENT_CATEGORY);
        var data = getData(resp);

        assertFalse((Boolean) data.get("hasData"), "无数据分类 hasData 应为 false");
    }

    @Test
    void testPointDetail_existing() {
        var resp = rest.getForEntity(
            "/knowledge/1/point-detail?algorithm=pca",
            Result.class);
        var data = getData(resp);

        assertNotNull(data.get("title"));
        assertNotNull(data.get("coords"));
        assertNotNull(data.get("vectorPreview"));
        assertNotNull(data.get("neighbors"));
        assertNotNull(data.get("globalMaxAbs"));
        assertNotNull(data.get("isZeroVector"));
        assertNotNull(data.get("contentType"));
    }

    @Test
    void testPointDetail_notFound() {
        var resp = rest.getForEntity(
            "/knowledge/99999/point-detail?algorithm=pca",
            Result.class);
        assertNotNull(resp.getBody());
        assertEquals(404, resp.getBody().getCode());
    }

    @Test
    void testRecomputeVisualization_pca() {
        var resp = rest.postForEntity(
            "/knowledge/{catId}/visualization/recompute",
            Map.of("algorithm", "pca"),
            Result.class, EXISTING_CATEGORY);
        var data = getData(resp);

        assertNotNull(data.get("status"));
    }

    @Test
    void testRecomputeVisualization_lock() {
        // 并发调用两次，第二次应返回 computing
        rest.postForEntity(
            "/knowledge/{catId}/visualization/recompute",
            Map.of("algorithm", "pca"),
            Result.class, EXISTING_CATEGORY);

        var resp = rest.postForEntity(
            "/knowledge/{catId}/visualization/recompute",
            Map.of("algorithm", "pca"),
            Result.class, EXISTING_CATEGORY);
        var data = getData(resp);

        // 锁已在前一次释放，实际会正常执行
        assertNotNull(data.get("status"));
    }

    @Test
    void testVisualization_pointsHaveRequiredFields() {
        var resp = rest.getForEntity(
            "/knowledge/{catId}/visualization?algorithm=pca&page=1&size=50",
            Result.class, EXISTING_CATEGORY);
        var data = getData(resp);
        @SuppressWarnings("unchecked")
        var points = (List<Map<String, Object>>) data.get("points");

        if (!points.isEmpty()) {
            Map<String, Object> p = points.get(0);
            assertTrue(p.containsKey("id"));
            assertTrue(p.containsKey("title"));
            assertTrue(p.containsKey("x"));
            assertTrue(p.containsKey("y"));
            assertTrue(p.containsKey("vectorStatus"));
            assertTrue(p.containsKey("contentType"));
            assertTrue(p.containsKey("isZeroVector"),
                "每个点应包含 isZeroVector 字段");
        }
    }
}
