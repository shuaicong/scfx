package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.DrVersion;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DrVersionMapper extends BaseMapper<DrVersion> {

    @Insert("INSERT INTO t_dr_version(category_id, algorithm, current_version) " +
            "VALUES(#{categoryId}, #{algorithm}, 1) " +
            "ON DUPLICATE KEY UPDATE current_version = current_version + 1")
    int incrementVersion(@Param("categoryId") Long categoryId, @Param("algorithm") String algorithm);

    @Insert("INSERT IGNORE INTO t_dr_version(category_id, algorithm, current_version) " +
            "VALUES(#{categoryId}, #{algorithm}, 1)")
    int tryInitVersion(@Param("categoryId") Long categoryId, @Param("algorithm") String algorithm);

    @Select("SELECT current_version FROM t_dr_version WHERE category_id = #{categoryId} AND algorithm = #{algorithm}")
    Integer selectCurrentVersion(@Param("categoryId") Long categoryId, @Param("algorithm") String algorithm);
}
