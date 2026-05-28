package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.service.TaskStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/scripts/status")
@RequiredArgsConstructor
public class TaskStatusController {
    private final TaskStatusService taskStatusService;

    @GetMapping
    public Result<Map<Long, TaskStatusService.TaskStatus>> getTasksStatus(
            @RequestParam("ids") List<Long> idsArr) {
        Map<Long, TaskStatusService.TaskStatus> statuses = taskStatusService.getTasksStatus(idsArr);
        return Result.success(statuses);
    }

    @PostMapping("/{scriptId}/executions/{executionId}/stop")
    public Result<Void> stopExecution(
            @PathVariable Long scriptId,
            @PathVariable String executionId) {
        taskStatusService.stopExecution(executionId);
        return Result.success();
    }
}