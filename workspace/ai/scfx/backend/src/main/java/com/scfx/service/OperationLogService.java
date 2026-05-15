package com.scfx.service;

import com.scfx.entity.OperationLog;
import java.util.List;
import java.util.Map;

/**
 * Operation log service interface
 */
public interface OperationLogService {
    void log(String operator, String operationType, String targetType, Long targetId, Object detail);

    List<OperationLog> findByTarget(String targetType, Long targetId);

    Map<String, Object> findPage(int page, int size);

    void cleanup();
}