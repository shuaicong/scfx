package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.DrCoord;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface DrCoordMapper extends BaseMapper<DrCoord> {

    @Insert("INSERT INTO t_knowledge_dr_coords(knowledge_id, category_id, algorithm, version, x, y, z) " +
            "VALUES(#{knowledgeId}, #{categoryId}, #{algorithm}, #{version}, #{x}, #{y}, #{z}) " +
            "ON DUPLICATE KEY UPDATE version = VALUES(version), x = VALUES(x), y = VALUES(y), z = VALUES(z)")
    int upsert(DrCoord coord);

    @Select("SELECT * FROM t_knowledge_dr_coords WHERE knowledge_id = #{knowledgeId} AND algorithm = #{algorithm} LIMIT 1")
    DrCoord selectByKnowledgeIdAndAlgorithm(@Param("knowledgeId") Long knowledgeId, @Param("algorithm") String algorithm);

    @Select("SELECT * FROM t_knowledge_dr_coords WHERE category_id = #{categoryId} AND algorithm = #{algorithm} AND version = #{version}")
    List<DrCoord> selectByCategoryAndAlgorithm(@Param("categoryId") Long categoryId,
                                                @Param("algorithm") String algorithm,
                                                @Param("version") Integer version);

    @Select("SELECT MAX(version) FROM t_knowledge_dr_coords WHERE category_id = #{categoryId} AND algorithm = #{algorithm}")
    Integer selectMaxVersion(@Param("categoryId") Long categoryId, @Param("algorithm") String algorithm);

    @Delete("DELETE FROM t_knowledge_dr_coords WHERE category_id = #{categoryId} AND algorithm = #{algorithm} AND version < #{maxKeepVersion}")
    int deleteOldVersions(@Param("categoryId") Long categoryId,
                          @Param("algorithm") String algorithm,
                          @Param("maxKeepVersion") Integer maxKeepVersion);

}
