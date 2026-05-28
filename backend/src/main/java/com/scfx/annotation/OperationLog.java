package com.scfx.annotation;

import java.lang.annotation.*;

/**
 * Operation log annotation for marking methods to log
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {
    String type();       // Operation type: CREATE/UPDATE/DELETE/EXECUTE/UPLOAD/ROLLBACK
    String targetType(); // Target type: SCRIPT/DATASOURCE/TASK
}