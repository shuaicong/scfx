package com.scfx.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface CategoryOperationLogMapper {
    @Insert("INSERT INTO t_category_operation_log (category_id, operator, operation_type, operation_detail, operated_at) " +
            "VALUES (#{categoryId}, #{operator}, #{operationType}, #{operationDetail}, NOW())")
    int insert(@Param("categoryId") Long categoryId, @Param("operator") String operator,
               @Param("operationType") String operationType, @Param("operationDetail") String operationDetail);

    @Select("SELECT * FROM t_category_operation_log WHERE category_id = #{categoryId} ORDER BY operated_at DESC")
    List<Map<String, Object>> findByCategoryId(Long categoryId);

    @Select("SELECT * FROM t_category_operation_log ORDER BY operated_at DESC LIMIT 100")
    List<Map<String, Object>> findRecent();
}