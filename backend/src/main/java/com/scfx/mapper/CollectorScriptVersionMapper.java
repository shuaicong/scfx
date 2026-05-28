package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.CollectorScriptVersion;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CollectorScriptVersionMapper extends BaseMapper<CollectorScriptVersion> {

    @Select("SELECT * FROM t_collector_script_version WHERE datasource_code = #{datasourceCode} ORDER BY version DESC")
    List<CollectorScriptVersion> findByDatasourceCode(@Param("datasourceCode") String datasourceCode);

    @Select("SELECT * FROM t_collector_script_version WHERE datasource_code = #{datasourceCode} AND is_current = 1")
    CollectorScriptVersion findCurrentByDatasourceCode(@Param("datasourceCode") String datasourceCode);

    @Select("SELECT MAX(version) FROM t_collector_script_version WHERE datasource_code = #{datasourceCode}")
    Integer findMaxVersion(@Param("datasourceCode") String datasourceCode);

    @Select("SELECT * FROM t_collector_script_version WHERE datasource_code = #{datasourceCode} AND version = #{version}")
    CollectorScriptVersion findByDatasourceCodeAndVersion(@Param("datasourceCode") String datasourceCode, @Param("version") int version);
}