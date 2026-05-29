package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Operation log controller for querying logs
 */
@RestController
@RequestMapping("/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {
    private final OperationLogService operationLogService;

    @GetMapping
    public Result<Map<String, Object>> findPage(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(operationLogService.findPage(page, size));
    }
}