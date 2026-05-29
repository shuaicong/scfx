package com.scfx.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface KnowledgeCategoryMapper {
    @Insert("<script>" +
        "INSERT INTO t_knowledge_category (knowledge_id, category_id) VALUES " +
        "<foreach collection='categoryIds' item='cid' separator=','>(#{knowledgeId}, #{cid})</foreach>" +
        "</script>")
    int insertBatch(@Param("knowledgeId") Long knowledgeId, @Param("categoryIds") List<Long> categoryIds);

    @Delete("DELETE FROM t_knowledge_category WHERE knowledge_id=#{knowledgeId} AND category_id=#{categoryId}")
    int delete(@Param("knowledgeId") Long knowledgeId, @Param("categoryId") Long categoryId);

    @Delete("DELETE FROM t_knowledge_category WHERE knowledge_id=#{knowledgeId}")
    int deleteAllByKnowledgeId(Long knowledgeId);

    @Select("SELECT category_id FROM t_knowledge_category WHERE knowledge_id=#{knowledgeId}")
    List<Long> findCategoryIdsByKnowledgeId(Long knowledgeId);

    @Select("SELECT knowledge_id FROM t_knowledge_category WHERE category_id=#{categoryId}")
    List<Long> findKnowledgeIdsByCategoryId(Long categoryId);

    @Select("SELECT COUNT(*) FROM t_knowledge_category WHERE category_id=#{categoryId}")
    int countByCategoryId(Long categoryId);

    @Select("SELECT k.id FROM t_knowledge_base k LEFT JOIN t_knowledge_category kc ON k.id = kc.knowledge_id " +
            "WHERE kc.knowledge_id IS NULL GROUP BY k.id")
    List<Long> findUncategorizedKnowledgeIds();

    @Insert("INSERT INTO t_knowledge_move_log (knowledge_id, from_category_id, to_category_id, moved_by) " +
            "VALUES (#{knowledgeId}, #{fromCategoryId}, #{toCategoryId}, #{movedBy})")
    int insertMoveLog(@Param("knowledgeId") Long knowledgeId, @Param("fromCategoryId") Long fromCategoryId,
                      @Param("toCategoryId") Long toCategoryId, @Param("movedBy") String movedBy);

    @Select("SELECT * FROM t_knowledge_move_log WHERE knowledge_id=#{knowledgeId} ORDER BY moved_at DESC")
    List<Map<String, Object>> findMoveHistory(Long knowledgeId);
}