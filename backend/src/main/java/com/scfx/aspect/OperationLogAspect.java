package com.scfx.aspect;

import com.scfx.annotation.OperationLog;
import com.scfx.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * AOP aspect for automatic operation logging
 */
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {
    private final OperationLogService operationLogService;

    @Pointcut("@annotation(com.scfx.annotation.OperationLog)")
    public void operationLogPointcut() {}

    @AfterReturning(pointcut = "operationLogPointcut()", returning = "result")
    public void afterReturning(JoinPoint joinPoint, Object result) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        OperationLog annotation = method.getAnnotation(OperationLog.class);

        if (annotation == null) return;

        String operator = getCurrentUser();
        String operationType = annotation.type();
        String targetType = annotation.targetType();
        Long targetId = extractTargetId(joinPoint.getArgs());

        operationLogService.log(operator, operationType, targetType, targetId, joinPoint.getArgs());
    }

    private String getCurrentUser() {
        // Simplified implementation, should get from security context in production
        return "admin";
    }

    private Long extractTargetId(Object[] args) {
        if (args == null || args.length == 0) return null;
        for (Object arg : args) {
            if (arg instanceof Long) return (Long) arg;
            if (arg instanceof String) {
                try {
                    return Long.parseLong((String) arg);
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
}