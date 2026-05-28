package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.DataSource;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DataSourceMapper extends BaseMapper<DataSource> {

    @Select("SELECT * FROM t_data_source WHERE code = #{code}")
    DataSource findByCode(@Param("code") String code);

    @Select("SELECT * FROM t_data_source WHERE enabled = 1 ORDER BY sort_order ASC")
    List<DataSource> findAllEnabled();

    @Select("SELECT * FROM t_data_source ORDER BY sort_order ASC")
    List<DataSource> findAll();

    @Update("UPDATE t_data_source SET last_heartbeat = NOW() WHERE code = #{code}")
    void updateHeartbeat(@Param("code") String code);

    @Update("UPDATE t_data_source SET enabled = #{enabled} WHERE id = #{id}")
    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);
}