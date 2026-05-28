package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.OperationLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * Operation log mapper for database operations
 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {

    @Select("SELECT * FROM t_operation_log WHERE target_type = #{targetType} AND target_id = #{targetId} ORDER BY operate_time DESC")
    List<OperationLog> findByTarget(@Param("targetType") String targetType, @Param("targetId") Long targetId);

    @Select("SELECT * FROM t_operation_log ORDER BY operate_time DESC LIMIT #{offset}, #{size}")
    List<OperationLog> findPage(@Param("offset") int offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM t_operation_log")
    long count();

    @Delete("DELETE FROM t_operation_log WHERE operate_time < DATE_SUB(NOW(), INTERVAL 90 DAY)")
    int deleteOlderThan90Days();
}