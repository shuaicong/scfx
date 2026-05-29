package com.scfx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scfx.entity.BatchOperationLog;
import com.scfx.entity.KnowledgeBase;
import com.scfx.mapper.BatchOperationLogMapper;
import com.scfx.mapper.KnowledgeBaseMapper;
import com.scfx.service.BatchOperationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BatchOperationServiceImpl implements BatchOperationService {

    private final BatchOperationLogMapper operationLogMapper;
    private final KnowledgeBaseMapper knowledgeMapper;
    private final ObjectMapper objectMapper;

    // 撤销有效期（分钟）
    private static final int UNDO_VALID_MINUTES = 30;

    @Override
    @Transactional
    public void logMove(List<Long> knowledgeIds, Long sourceCategoryId, Long targetCategoryId, String operator) {
        BatchOperationLog log = new BatchOperationLog();
        log.setOperationType("move");
        log.setSourceCategoryId(sourceCategoryId);
        log.setTargetCategoryId(targetCategoryId);
        log.setKnowledgeIds(toJson(knowledgeIds));
        log.setOperator(operator);
        operationLogMapper.insert(log);
    }

    @Override
    @Transactional
    public void logDelete(List<Long> knowledgeIds, Long targetCategoryId, String operator) {
        BatchOperationLog log = new BatchOperationLog();
        log.setOperationType("delete");
        log.setTargetCategoryId(targetCategoryId);
        log.setSourceCategoryId(null);
        log.setKnowledgeIds(toJson(knowledgeIds));
        log.setOperator(operator);
        operationLogMapper.insert(log);
    }

    @Override
    @Transactional
    public void undo(Long operationId, String operator) {
        BatchOperationLog operation = operationLogMapper.selectById(operationId);
        if (operation == null || operation.getUndoneAt() != null) {
            throw new RuntimeException("操作不存在或已撤销");
        }

        // 检查是否在有效期内
        LocalDateTime deadline = operation.getCreatedAt().plusMinutes(UNDO_VALID_MINUTES);
        if (LocalDateTime.now().isAfter(deadline)) {
            throw new RuntimeException("超过撤销有效期（30分钟）");
        }

        List<Long> knowledgeIds = fromJson(operation.getKnowledgeIds());

        // 执行撤销
        if ("move".equals(operation.getOperationType())) {
            // 移动撤销：从目标分类移回来源分类
            for (Long knowledgeId : knowledgeIds) {
                KnowledgeBase kb = knowledgeMapper.selectById(knowledgeId);
                if (kb != null) {
                    kb.setCategoryId(operation.getSourceCategoryId());
                    knowledgeMapper.updateById(kb);
                }
            }
        } else if ("delete".equals(operation.getOperationType())) {
            // 删除撤销：恢复已删除状态（如果使用软删除）
            for (Long knowledgeId : knowledgeIds) {
                KnowledgeBase kb = knowledgeMapper.selectById(knowledgeId);
                if (kb != null) {
                    kb.setDeleted(0);
                    knowledgeMapper.updateById(kb);
                }
            }
        }

        // 标记为已撤销
        operation.setUndoneAt(LocalDateTime.now());
        operation.setUndoneOperator(operator);
        operationLogMapper.updateById(operation);
    }

    @Override
    public List<BatchOperationLog> getUndoableOperations() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(UNDO_VALID_MINUTES);
        LambdaQueryWrapper<BatchOperationLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(BatchOperationLog::getUndoneAt)
              .gt(BatchOperationLog::getCreatedAt, deadline)
              .orderByDesc(BatchOperationLog::getCreatedAt);
        return operationLogMapper.selectList(wrapper);
    }

    @Override
    public BatchOperationLog getById(Long id) {
        return operationLogMapper.selectById(id);
    }

    private String toJson(List<Long> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<Long> fromJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}