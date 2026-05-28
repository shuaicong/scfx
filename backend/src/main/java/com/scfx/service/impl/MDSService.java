package com.scfx.service.impl;

import com.scfx.service.DimensionalityReducer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MDS（多维缩放）降维服务
 * 通过保持点对距离保留局部结构，O(n²) 余弦距离矩阵 + 幂迭代
 * 硬上限 500 条，不支持增量
 */
@Slf4j
@Component("mds")
public class MDSService implements DimensionalityReducer {

    private static final int MAX_POINTS = 500;
    private static final long DEADLINE_MS = 270_000; // 4.5min
    private static final int MAX_ITERATIONS = 200;
    private static final double CONVERGENCE_THRESHOLD = 1e-8;

    @Override
    public String name() {
        return "mds";
    }

    @Override
    public boolean supportsIncremental() {
        return false;
    }

    @Override
    public IncrementalResult projectIncremental(
            Map<Long, float[]> newVectors,
            double[] mean, double[] pc1, double[] pc2, double[] pc3,
            double xMin, double xMax, double yMin, double yMax,
            double zMin, double zMax) {
        throw new UnsupportedOperationException("MDS 不支持增量投影，请使用全量重算");
    }

    @Override
    public ReductionResult reduce(Map<Long, float[]> vectorMap) {
        long startTime = System.currentTimeMillis();
        int n = vectorMap.size();
        log.info("MDS.reduce start | vectorCount={}", n);

        // ─── 前置校验 ───
        if (n > MAX_POINTS) {
            log.warn("MDS超限 | count={} | max={}", n, MAX_POINTS);
            throw new IllegalArgumentException("MDS_TOO_MANY_POINTS:MDS 样本量 " + n + " 超过上限 " + MAX_POINTS);
        }
        if (n < 2) {
            throw new IllegalArgumentException("MDS_TOO_FEW_POINTS:MDS 至少需要 2 条向量");
        }

        List<Long> ids = new ArrayList<>(vectorMap.keySet());
        List<float[]> vectors = new ArrayList<>(vectorMap.values());
        int dim = vectors.get(0).length;

        // 全相同向量检查
        float[] first = vectors.get(0);
        boolean allIdentical = vectors.stream().allMatch(v -> Arrays.equals(v, first));
        if (allIdentical) {
            throw new IllegalArgumentException("MDS_ALL_IDENTICAL:所有向量完全相同，无法计算 MDS");
        }

        // 全零向量检查
        boolean allZero = vectors.stream().allMatch(v -> {
            for (float f : v) if (f != 0) return false;
            return true;
        });
        if (allZero) {
            throw new IllegalArgumentException("MDS_ALL_ZERO:所有向量均为空向量，无法执行降维");
        }

        try {
            // ─── Step 1: 余弦距离矩阵 O(n²) ───
            log.info("MDS 计算距离矩阵: n={}, dim={}", n, dim);
            double[][] distMatrix = new double[n][n];
            for (int i = 0; i < n; i++) {
                checkDeadline(startTime);
                for (int j = i + 1; j < n; j++) {
                    double dist = cosineDistance(vectors.get(i), vectors.get(j));
                    distMatrix[i][j] = dist;
                    distMatrix[j][i] = dist;
                }
                distMatrix[i][i] = 0.0;
            }

            // ─── Step 2: 平方距离矩阵 D² ───
            double[][] D2 = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    double d = distMatrix[i][j];
                    D2[i][j] = d * d;
                }
            }

            // ─── Step 3: 双中心化 J * D² * J ───
            // J = I - (1/n) * ones(n)
            // B = -0.5 * J * D² * J
            checkDeadline(startTime);

            double[] rowMean = new double[n];
            double[] colMean = new double[n];
            double totalMean = 0.0;
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    rowMean[i] += D2[i][j];
                    colMean[j] += D2[i][j];
                    totalMean += D2[i][j];
                }
            }
            for (int i = 0; i < n; i++) {
                rowMean[i] /= n;
                colMean[i] /= n;
            }
            totalMean /= (n * n);

            double[][] B = new double[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    B[i][j] = -0.5 * (D2[i][j] - rowMean[i] - colMean[j] + totalMean);
                }
            }

            // ─── Step 4: 幂迭代取前 3 个特征值/特征向量 ───
            checkDeadline(startTime);

            double[] v1 = powerIteration(B, n, null, startTime);
            double[] v2 = powerIteration(B, n, v1, startTime);
            double[] v3 = powerIteration(B, n, v2, startTime);

            // 计算特征值 λ = vᵀBv / vᵀv (v 已归一化，所以 λ = vᵀBv)
            double lambda1 = rayleighQuotient(B, v1, n);
            double lambda2 = rayleighQuotient(B, v2, n);
            double lambda3 = rayleighQuotient(B, v3, n);

            // 符号翻转对齐（确保正特征值对应的方向一致）
            // MDS 坐标 = sqrt(|λ|) * v
            double s1 = Math.sqrt(Math.max(Math.abs(lambda1), 1e-12));
            double s2 = Math.sqrt(Math.max(Math.abs(lambda2), 1e-12));
            double s3 = Math.sqrt(Math.max(Math.abs(lambda3), 1e-12));

            List<Point> points = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                points.add(new Point(ids.get(i),
                        v1[i] * s1,
                        v2[i] * s2,
                        v3[i] * s3));
            }

            // ─── Step 5: 计算原始坐标范围（不做归一化，保留真实分布）───
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

            long cost = System.currentTimeMillis() - startTime;
            log.info("MDS.reduce done | vectorCount={} | cost={}ms", n, cost);

            // MDS 不返回 mean/pc1/pc2/pc3（与 PCA 不同）
            return new ReductionResult(points,
                    new double[0], new double[0], new double[0], new double[0],
                    xMin, xMax, yMin, yMax, zMin, zMax);

        } catch (IllegalArgumentException e) {
            // 已带有错误码的异常直接抛出
            throw e;
        } catch (Exception e) {
            log.error("MDS计算异常 | count={}", n, e);
            throw new IllegalArgumentException("DR_COMPUTE_FAILED:降维计算失败: " + e.getMessage());
        }
    }

    // ======================== 余弦距离 ========================

    private double cosineDistance(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        int len = Math.min(a.length, b.length);
        for (int i = 0; i < len; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        normA = Math.sqrt(normA);
        normB = Math.sqrt(normB);
        if (normA < 1e-12 || normB < 1e-12) return 1.0; // 零向量 → 最大距离
        double cos = dot / (normA * normB);
        // 裁剪浮点误差
        cos = Math.max(-1.0, Math.min(1.0, cos));
        // 余弦距离 = 1 - cos
        return 1.0 - cos;
    }

    // ======================== 幂迭代 ========================

    private double[] powerIteration(double[][] B, int n, double[] deflation, long startTime) {
        double[] v = new double[n];
        Random rng = new Random(42);
        for (int i = 0; i < n; i++) {
            v[i] = rng.nextDouble() * 2 - 1;
        }
        normalize(v);
        if (deflation != null) {
            projectOut(v, deflation);
        }

        for (int iter = 0; iter < MAX_ITERATIONS; iter++) {
            checkDeadline(startTime);
            double[] vNew = new double[n];
            for (int i = 0; i < n; i++) {
                double sum = 0;
                for (int j = 0; j < n; j++) {
                    sum += B[i][j] * v[j];
                }
                vNew[i] = sum;
            }

            if (deflation != null) {
                projectOut(vNew, deflation);
            }

            normalize(vNew);

            // 收敛检查
            double diff = 0;
            for (int i = 0; i < n; i++) {
                double d = vNew[i] - v[i];
                diff += d * d;
            }
            v = vNew;

            if (Math.sqrt(diff) < CONVERGENCE_THRESHOLD) {
                break;
            }
        }

        return v;
    }

    private double rayleighQuotient(double[][] B, double[] v, int n) {
        double num = 0, den = 0;
        for (int i = 0; i < n; i++) {
            double rowSum = 0;
            for (int j = 0; j < n; j++) {
                rowSum += B[i][j] * v[j];
            }
            num += v[i] * rowSum;
            den += v[i] * v[i];
        }
        return num / (den > 1e-12 ? den : 1);
    }

    // ======================== 工具方法 ========================

    private void checkDeadline(long startTime) {
        if (System.currentTimeMillis() - startTime > DEADLINE_MS) {
            throw new IllegalArgumentException("DR_COMPUTE_FAILED:MDS 计算超时，请减小数据量后重试");
        }
    }

    private void normalize(double[] v) {
        double norm = 0;
        for (double d : v) norm += d * d;
        norm = Math.sqrt(norm);
        if (norm > 1e-12) {
            for (int i = 0; i < v.length; i++) v[i] /= norm;
        }
    }

    private void projectOut(double[] v, double[] d) {
        double dot = 0, normD = 0;
        for (int i = 0; i < v.length; i++) {
            dot += v[i] * d[i];
            normD += d[i] * d[i];
        }
        if (normD > 1e-12) {
            double factor = dot / normD;
            for (int i = 0; i < v.length; i++) v[i] -= factor * d[i];
        }
    }

}
