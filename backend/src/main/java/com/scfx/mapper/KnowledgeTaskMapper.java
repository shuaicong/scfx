package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.KnowledgeTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface KnowledgeTaskMapper extends BaseMapper<KnowledgeTask> {

    @Select("SELECT * FROM t_knowledge_task WHERE knowledge_id = #{knowledgeId} ORDER BY id DESC LIMIT 1")
    KnowledgeTask selectByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    @Select("SELECT * FROM t_knowledge_task WHERE idempotent_key = #{key} LIMIT 1")
    KnowledgeTask selectByIdempotentKey(@Param("key") String idempotentKey);

    @Select("SELECT * FROM t_knowledge_task WHERE knowledge_id = #{knowledgeId} AND status IN (${statuses}) LIMIT 1")
    KnowledgeTask selectByKnowledgeIdAndStatusIn(@Param("knowledgeId") Long knowledgeId,
                                                  @Param("statuses") String statuses);

    @Select("SELECT * FROM t_knowledge_task WHERE status IN (${statuses}) AND updated_at < #{before}")
    List<KnowledgeTask> selectZombieTasks(@Param("statuses") String statuses,
                                           @Param("before") LocalDateTime before);

    @Update("UPDATE t_knowledge_task SET status = 'cancelled', error_message = '任务已取消' WHERE knowledge_id = #{knowledgeId} AND status NOT IN ('completed', 'failed', 'cancelled')")
    void cancelByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    int deleteByKnowledgeId(@Param("knowledgeId") Long knowledgeId);
}
