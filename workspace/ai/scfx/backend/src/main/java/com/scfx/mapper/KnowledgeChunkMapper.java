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
}
