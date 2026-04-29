package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.service.LiangxinwangCollector;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 采集控制器
 */
@RestController
@RequestMapping("/collection")
@RequiredArgsConstructor
public class CollectionController {

    private final LiangxinwangCollector collector;

    /**
     * 触发粮信网采集
     */
    @PostMapping("/liangxinwang")
    public Result<Map<String, Object>> collectLiangxinwang() {
        // 异步执行采集
        new Thread(() -> collector.collectTodayCornReports()).start();
        Map<String, Object> response = new java.util.HashMap<>();
        response.put("message", "粮信网采集任务已启动，请稍后查看日志");
        return Result.success(response);
    }

    /**
     * 获取采集状态
     */
    @GetMapping("/status")
    public Result<Map<String, Object>> getStatus() {
        Map<String, Object> status = Map.of(
            "collector", "liangxinwang",
            "status", "ready",
            "message", "采集器就绪，可以触发采集"
        );
        return Result.success(status);
    }
}
