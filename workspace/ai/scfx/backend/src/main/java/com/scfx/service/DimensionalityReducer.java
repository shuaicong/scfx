package com.scfx.service;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 降维算法抽象接口
 * 支持 PCA / UMAP 等多种算法按需切换
 */
public interface DimensionalityReducer {

    /** 算法名称：PCA / UMAP */
    String name();

    /** 是否支持增量投影 */
    boolean supportsIncremental();

    /** 全量降维 */
    ReductionResult reduce(Map<Long, float[]> vectors);

    /**
     * 增量投影（对新增向量复用缓存的基线参数）
     * 仅在 supportsIncremental() = true 时调用
     */
    IncrementalResult projectIncremental(
            Map<Long, float[]> newVectors,
            double[] mean, double[] pc1, double[] pc2, double[] pc3,
            double xMin, double xMax, double yMin, double yMax,
            double zMin, double zMax);

    // ======================== 结果类型 ========================

    @Data
    @AllArgsConstructor
    class Point {
        private Long knowledgeId;
        private double x;
        private double y;
        private double z;   // 3D 预留
    }

    @Data
    @AllArgsConstructor
    class ReductionResult {
        private List<Point> points;
        private double[] mean;
        private double[] pc1;
        private double[] pc2;
        private double[] pc3;       // 3D 预留
        private double xMin, xMax;
        private double yMin, yMax;
        private double zMin, zMax;  // 3D 预留
    }

    @Data
    @AllArgsConstructor
    class IncrementalResult {
        private List<Point> points;
        private double xMin, xMax;
        private double yMin, yMax;
    }
}
