package com.scfx.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.CollectionLog;
import com.scfx.service.CollectionLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 日志控制器
 */
@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
public class LogController {

    private final CollectionLogService logService;

    /**
     * 获取日志列表
     */
    @GetMapping
    public Result<Page<CollectionLog>> getLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        return logService.getLogs(page, size, level, source, startTime, endTime);
    }

    /**
     * 获取日志统计
     */
    @GetMapping("/stats")
    public Result<java.util.Map<String, Long>> getStats() {
        return logService.getLogStats();
    }
}
