package com.scfx.service;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 向量模型配置中心
 * 统一管理硅基流动 BGE-M3 和阿里云 DashScope 多模态的 API 地址、密钥、纬度、超时参数
 */
@Data
@Component
@ConfigurationProperties(prefix = "vector")
public class VectorProperties {

    private boolean enabled = true;

    private SiliconFlow siliconflow = new SiliconFlow();
    private DashScope dashscope = new DashScope();

    @Data
    public static class SiliconFlow {
        private String apiKey;
        private String apiUrl = "https://api.siliconflow.cn/v1/embeddings";
        private String model = "BAAI/bge-m3";
        private int dimensions = 1024;
        private int connectTimeout = 5000;
        private int readTimeout = 30000;
    }

    @Data
    public static class DashScope {
        private String apiKey;
        private String apiUrl = "https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding";
        private String model = "tongyi-embedding-vision-flash-2026-03-06";
        private int dimensions = 768;
        private int connectTimeout = 5000;
        private int readTimeout = 30000;
    }
}
