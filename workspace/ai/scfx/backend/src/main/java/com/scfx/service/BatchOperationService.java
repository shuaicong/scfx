package com.scfx.service;

import com.scfx.entity.BatchOperationLog;
import java.util.List;

public interface BatchOperationService {

    /**
     * 记录批量移动操作
     */
    void logMove(List<Long> knowledgeIds, Long sourceCategoryId, Long targetCategoryId, String operator);

    /**
     * 记录批量删除操作
     */
    void logDelete(List<Long> knowledgeIds, Long targetCategoryId, String operator);

    /**
     * 撤销批量操作
     */
    void undo(Long operationId, String operator);

    /**
     * 获取可撤销的操作（30分钟内）
     */
    List<BatchOperationLog> getUndoableOperations();

    /**
     * 获取操作详情
     */
    BatchOperationLog getById(Long id);
}