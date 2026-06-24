package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    List<KnowledgeBase> selectPending(@Param("categoryId") Long categoryId);

    /**
     * 同步 chunk_count 为 t_knowledge_chunk 中实际未删除的切片数
     */
    @Update("UPDATE t_knowledge_base kb " +
        "JOIN (SELECT knowledge_id, COUNT(*) cnt FROM t_knowledge_chunk " +
        "      WHERE deleted = 0 GROUP BY knowledge_id) c " +
        "ON kb.id = c.knowledge_id " +
        "SET kb.chunk_count = c.cnt " +
        "WHERE kb.id = #{id}")
    void syncChunkCount(@Param("id") Long id);
}