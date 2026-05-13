package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.entity.BatchOperationLog;
import com.scfx.service.BatchOperationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/batch-operation")
@RequiredArgsConstructor
public class BatchOperationController {

    private final BatchOperationService batchOperationService;

    @GetMapping("/undoable")
    public Result<List<BatchOperationLog>> getUndoable() {
        return Result.success(batchOperationService.getUndoableOperations());
    }

    @GetMapping("/{id}")
    public Result<BatchOperationLog> getById(@PathVariable Long id) {
        return Result.success(batchOperationService.getById(id));
    }

    @PostMapping("/{id}/undo")
    public Result<Void> undo(@PathVariable Long id, @RequestParam String operator) {
        try {
            batchOperationService.undo(id, operator);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}