package com.scfx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 海南储备集团粮食市场智能分析平台 - 启动类
 */
@SpringBootApplication
public class GrainPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrainPlatformApplication.class, args);
        System.out.println("========================================");
        System.out.println("粮食市场智能分析平台启动成功！");
        System.out.println("访问地址: http://localhost:8080/api");
        System.out.println("========================================");
    }
}
