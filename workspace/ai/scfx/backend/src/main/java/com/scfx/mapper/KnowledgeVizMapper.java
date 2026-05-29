package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.KnowledgeViz;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeVizMapper extends BaseMapper<KnowledgeViz> {

    /** 通过 t_knowledge_base.category_id 联表查询可视化向量 */
    List<KnowledgeViz> selectByCategoryId(@Param("categoryId") Long categoryId);

    KnowledgeViz selectByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    /** 按分类和 viz 状态查询（用于限流补偿重试） */
    List<KnowledgeViz> selectByCategoryIdAndVizStatus(
            @Param("categoryId") Long categoryId,
            @Param("vizStatus") String vizStatus);
}
