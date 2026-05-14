package com.scfx.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scfx.common.Result;
import com.scfx.entity.CollectionTask;
import com.scfx.service.CollectionTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final CollectionTaskService taskService;

    @GetMapping
    public Result<Page<CollectionTask>> getTasks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source) {
        return taskService.getTasks(page, size, status, source);
    }

    @GetMapping("/{id}")
    public Result<CollectionTask> getTask(@PathVariable Long id) {
        return taskService.getTaskById(id);
    }

    @PostMapping
    public Result<CollectionTask> createTask(@RequestBody CollectionTask task) {
        return taskService.createTask(task);
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        return taskService.updateTaskStatus(id, status);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteTask(@PathVariable Long id) {
        return taskService.deleteTask(id);
    }
}
