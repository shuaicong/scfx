package com.scfx.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scfx.entity.OperationLog;
import com.scfx.mapper.OperationLogMapper;
import com.scfx.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Operation log service implementation
 */
@Service
@RequiredArgsConstructor
public class OperationLogServiceImpl implements OperationLogService {
    private final OperationLogMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void log(String operator, String operationType, String targetType, Long targetId, Object detail) {
        OperationLog log = new OperationLog();
        log.setOperator(operator);
        log.setOperationType(operationType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setIp(getCurrentIp());

        try {
            log.setDetail(objectMapper.writeValueAsString(detail));
        } catch (Exception e) {
            log.setDetail("{}");
        }

        mapper.insert(log);
    }

    @Override
    public List<OperationLog> findByTarget(String targetType, Long targetId) {
        return mapper.findByTarget(targetType, targetId);
    }

    @Override
    public Map<String, Object> findPage(int page, int size) {
        int offset = (page - 1) * size;
        List<OperationLog> records = mapper.findPage(offset, size);
        long total = mapper.count();

        return Map.of(
            "records", records,
            "total", total,
            "page", page,
            "size", size
        );
    }

    @Override
    @Scheduled(cron = "0 0 3 * * ?") // Cleanup at 3am daily
    public void cleanup() {
        mapper.deleteOlderThan90Days();
    }

    private String getCurrentIp() {
        // Simplified implementation, should get from request context in production
        return "127.0.0.1";
    }
}