package com.scfx.service.impl;

import com.scfx.service.DimensionalityReducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * PCA 降维服务（幂迭代法，无外部依赖）
 * 将高维向量（768d）降为 2D / 3D 用于可视化
 * 实现 DimensionalityReducer 接口，支持算法切换
 */
@Slf4j
@Service("pca")
public class PCAService implements DimensionalityReducer {

    @Value("${pca.max-iterations:100}")
    private int maxIterations;

    @Value("${pca.convergence-threshold:1.0E-6}")
    private double convergenceThreshold;

    @Override
    public String name() {
        return "pca";
    }

    @Override
    public boolean supportsIncremental() {
        return true;
    }

    // ======================== 全量 PCA ========================

    @Override
    public ReductionResult reduce(Map<Long, float[]> vectors) {
        return reduceWithBaseline(vectors);
    }

    /**
     * 全量 PCA 降维，返回完整结果（含基线数据）
     */
    public ReductionResult reduceWithBaseline(Map<Long, float[]> vectors) {
        int n = vectors.size();
        if (n < 2) {
            log.warn("PCA: 数据点不足 2 个 (n={})，无法降维", n);
            List<Point> result = new ArrayList<>();
            for (Map.Entry<Long, float[]> entry : vectors.entrySet()) {
                result.add(new Point(entry.getKey(), 0, 0, 0));
            }
            return new ReductionResult(result, new double[0], new double[0], new double[0], new double[0],
                    0, 0, 0, 0, 0, 0);
        }

        int dim = resolveDim(vectors);
        if (dim <= 0) {
            log.warn("PCA: 向量维度无效");
            return new ReductionResult(List.of(), new double[0], new double[0], new double[0], new double[0],
                    0, 0, 0, 0, 0, 0);
        }

        // 构建矩阵
        List<Long> ids = new ArrayList<>();
        double[][] X = new double[n][dim];
        int row = 0;
        for (Map.Entry<Long, float[]> entry : vectors.entrySet()) {
            ids.add(entry.getKey());
            float[] v = entry.getValue();
            for (int j = 0; j < dim && j < v.length; j++) {
                X[row][j] = v[j];
            }
            row++;
        }

        // 中心化
        double[] mean = new double[dim];
        for (int j = 0; j < dim; j++) {
            for (int i = 0; i < n; i++) {
                mean[j] += X[i][j];
            }
            mean[j] /= n;
            for (int i = 0; i < n; i++) {
                X[i][j] -= mean[j];
            }
        }

        // 幂迭代求 PC1、PC2、PC3
        double[] pc1 = powerIteration(X, n, dim, null);
        double[] pc2 = powerIteration(X, n, dim, pc1);
        double[] pc3 = powerIteration(X, n, dim, pc2);

        // 投影
        List<Point> points = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            points.add(new Point(ids.get(i),
                    dot(X[i], pc1), dot(X[i], pc2), dot(X[i], pc3)));
        }

        // 计算原始坐标范围（不做归一化，保留真实分布）
        double xMin = Double.MAX_VALUE, xMax = -Double.MAX_VALUE;
        double yMin = Double.MAX_VALUE, yMax = -Double.MAX_VALUE;
        double zMin = Double.MAX_VALUE, zMax = -Double.MAX_VALUE;
        for (Point p : points) {
            if (p.getX() < xMin) xMin = p.getX();
            if (p.getX() > xMax) xMax = p.getX();
            if (p.getY() < yMin) yMin = p.getY();
            if (p.getY() > yMax) yMax = p.getY();
            if (p.getZ() < zMin) zMin = p.getZ();
            if (p.getZ() > zMax) zMax = p.getZ();
        }

        // 均值向量转为 float[] 供持久化
        return new ReductionResult(points, mean, pc1, pc2, pc3,
                xMin, xMax, yMin, yMax, zMin, zMax);
    }

    // ======================== 增量投影 ========================

    /**
     * 对新增向量做增量投影，复用缓存的均值/主成分
     */
    @Override
    public IncrementalResult projectIncremental(
            Map<Long, float[]> newVectors,
            double[] mean, double[] pc1, double[] pc2, double[] pc3,
            double xMin, double xMax, double yMin, double yMax,
            double zMin, double zMax) {

        int dim = mean.length;
        double nxMin = xMin, nxMax = xMax;
        double nyMin = yMin, nyMax = yMax;
        double nzMin = zMin, nzMax = zMax;
        List<Point> points = new ArrayList<>(newVectors.size());

        for (Map.Entry<Long, float[]> entry : newVectors.entrySet()) {
            float[] v = entry.getValue();

            // 中心化
            double[] centered = new double[dim];
            for (int j = 0; j < dim; j++) {
                centered[j] = (v != null && j < v.length) ? v[j] - mean[j] : -mean[j];
            }

            // 投影到主成分（含 PC3）
            double px = dot(centered, pc1);
            double py = dot(centered, pc2);
            double pz = pc3 != null ? dot(centered, pc3) : 0;

            // 扩展归一化边界
            if (px < nxMin) nxMin = px;
            if (px > nxMax) nxMax = px;
            if (py < nyMin) nyMin = py;
            if (py > nyMax) nyMax = py;
            if (pz < nzMin) nzMin = pz;
            if (pz > nzMax) nzMax = pz;

            points.add(new Point(entry.getKey(), px, py, pz));
        }

        // 不做归一化，保留真实坐标分布
        return new IncrementalResult(points, nxMin, nxMax, nyMin, nyMax);
    }

    // ======================== 内部工具 ========================

    private int resolveDim(Map<Long, float[]> vectors) {
        for (float[] v : vectors.values()) {
            if (v != null) return v.length;
        }
        return -1;
    }

    // ======================== 幂迭代 ========================

    private double[] powerIteration(double[][] X, int n, int dim, double[] deflation) {
        double[] v = new double[dim];
        Random rng = new Random(42);
        for (int j = 0; j < dim; j++) {
            v[j] = rng.nextDouble() * 2 - 1;
        }
        normalize(v);
        if (deflation != null) {
            projectOut(v, deflation);
        }

        for (int iter = 0; iter < maxIterations; iter++) {
            double[] temp = new double[n];
            for (int i = 0; i < n; i++) {
                temp[i] = dot(X[i], v);
            }

            double[] vNew = new double[dim];
            for (int j = 0; j < dim; j++) {
                for (int i = 0; i < n; i++) {
                    vNew[j] += X[i][j] * temp[i];
                }
                vNew[j] /= n;
            }

            if (deflation != null) {
                projectOut(vNew, deflation);
            }

            normalize(vNew);

            double diff = 0;
            for (int j = 0; j < dim; j++) {
                double d = vNew[j] - v[j];
                diff += d * d;
            }
            v = vNew;

            if (Math.sqrt(diff) < convergenceThreshold) {
                break;
            }
        }

        return v;
    }

    private double dot(double[] a, double[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) sum += a[i] * b[i];
        return sum;
    }

    private double normalize(double[] v) {
        double norm = Math.sqrt(dot(v, v));
        if (norm > 1e-12) {
            for (int i = 0; i < v.length; i++) v[i] /= norm;
        }
        return norm;
    }

    private void projectOut(double[] v, double[] d) {
        double dot = dot(v, d);
        double normD = dot(d, d);
        if (normD > 1e-12) {
            double factor = dot / normD;
            for (int i = 0; i < v.length; i++) v[i] -= factor * d[i];
        }
    }
}
