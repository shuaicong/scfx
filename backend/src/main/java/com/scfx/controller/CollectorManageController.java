package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.entity.CollectorInfo;
import com.scfx.service.CollectorManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 采集器管理控制器
 * 提供给 Python SDK 注册和心跳
 */
@RestController
@RequestMapping("/collector")
@RequiredArgsConstructor
public class CollectorManageController {

    private final CollectorManageService collectorManageService;

    /**
     * 注册采集器
     * POST /collector/register
     *
     * Python SDK 应在启动时调用此接口进行注册
     */
    @PostMapping("/register")
    public Result<CollectorInfo> register(@RequestBody CollectorInfo info) {
        CollectorInfo registered = collectorManageService.register(info);
        return Result.success(registered);
    }

    /**
     * 心跳
     * POST /collector/heartbeat
     *
     * Python SDK 应每分钟发送一次心跳
     */
    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(@RequestBody Map<String, String> request) {
        String collectorName = request.get("collectorName");
        String source = request.get("source");
        collectorManageService.heartbeat(collectorName, source);
        return Result.success();
    }

    /**
     * 下线采集器
     * POST /collector/offline
     *
     * Python SDK 应在退出时调用此接口
     */
    @PostMapping("/offline")
    public Result<Void> offline(@RequestBody Map<String, String> request) {
        String collectorName = request.get("collectorName");
        String source = request.get("source");
        collectorManageService.offline(collectorName, source);
        return Result.success();
    }

    /**
     * 获取所有采集器（分页）
     * GET /collector/all?page=1&size=20&status=&source=
     */
    @GetMapping("/all")
    public Result<List<CollectorInfo>> getAllCollectors(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source) {
        List<CollectorInfo> list = collectorManageService.getCollectors(page, size);
        return Result.success(list);
    }

    /**
     * 获取采集器详情
     * GET /collector/{id}
     */
    @GetMapping("/{id}")
    public Result<CollectorInfo> getCollectorById(@PathVariable Long id) {
        return collectorManageService.getCollectorById(id);
    }

    /**
     * 更新采集器信息
     * PUT /collector/{id}
     */
    @PutMapping("/{id}")
    public Result<CollectorInfo> updateCollector(@PathVariable Long id, @RequestBody CollectorInfo info) {
        info.setId(id);
        return collectorManageService.updateCollector(info);
    }

    /**
     * 启用采集器
     * PUT /collector/{id}/enable
     */
    @PutMapping("/{id}/enable")
    public Result<Void> enableCollector(@PathVariable Long id) {
        return collectorManageService.enableCollector(id);
    }

    /**
     * 禁用采集器
     * PUT /collector/{id}/disable
     */
    @PutMapping("/{id}/disable")
    public Result<Void> disableCollector(@PathVariable Long id) {
        return collectorManageService.disableCollector(id);
    }

    /**
     * 删除采集器
     * DELETE /collector/{id}
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteCollector(@PathVariable Long id) {
        return collectorManageService.deleteCollector(id);
    }

    /**
     * 获取采集器统计信息
     * GET /collector/stats
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStatistics() {
        return collectorManageService.getStatistics();
    }

    /**
     * 获取在线采集器列表
     * GET /collector/online
     */
    @GetMapping("/online")
    public Result<List<CollectorInfo>> getOnlineCollectors() {
        return Result.success(collectorManageService.getOnlineCollectors());
    }

    /**
     * 获取SDK版本信息
     * GET /collector/version
     */
    @GetMapping("/version")
    public Result<Map<String, String>> getVersion() {
        return Result.success(Map.of(
            "sdk_version", "1.0.0",
            "java_version", "17",
            "spring_boot", "3.2"
        ));
    }
}
