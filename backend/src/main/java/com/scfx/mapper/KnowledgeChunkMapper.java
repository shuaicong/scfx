package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeChunkMapper extends BaseMapper<KnowledgeChunk> {

    List<KnowledgeChunk> selectByKnowledgeIdAndIsActive(
        @Param("knowledgeId") Long knowledgeId,
        @Param("isActive") Integer isActive);

    List<KnowledgeChunk> selectVectorizedByCategoryId(@Param("categoryId") Long categoryId);

    /** 批量插入切片 */
    int insertBatch(List<KnowledgeChunk> chunks);

    /** 按 knowledge_id 软删除切片 */
    int updateByKnowledgeId(@Param("knowledgeId") Long knowledgeId, @Param("isActive") Integer isActive);
}
