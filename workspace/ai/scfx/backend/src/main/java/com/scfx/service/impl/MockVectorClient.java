package com.scfx.service.impl;

import com.scfx.service.VectorClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * Mock 向量客户端（开发环境使用）
 * 返回随机向量，仅用于测试
 */
@Component
@ConditionalOnProperty(name = "vector.enabled", havingValue = "false", matchIfMissing = true)
public class MockVectorClient implements VectorClient {

    private static final int DEFAULT_DIMENSIONS = 1536;

    @Override
    public VectorResult embed(String text) {
        if (text == null || text.isEmpty()) {
            return new VectorResult("vec_empty");
        }

        // 生成随机向量（用于测试）
        float[] vector = new float[DEFAULT_DIMENSIONS];
        Random random = new Random(text.hashCode());
        for (int i = 0; i < DEFAULT_DIMENSIONS; i++) {
            vector[i] = random.nextFloat() * 2 - 1; // -1 to 1
        }

        return new VectorResult("vec_" + Math.abs(text.hashCode()), vector);
    }
}