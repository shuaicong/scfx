package com.scfx.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 对象存储客户端配置
 */
@Configuration
public class MinioConfig {

    @Value("${minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${minio.access-key:admin}")
    private String accessKey;

    @Value("${minio.secret-key:password}")
    private String secretKey;

    /**
     * MinIO 客户端 Bean，供各服务注入使用
     */
    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
