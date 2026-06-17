package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.KnowledgeImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface KnowledgeImageMapper extends BaseMapper<KnowledgeImage> {
    List<KnowledgeImage> findByKnowledgeId(@Param("knowledgeId") Long knowledgeId);
    void deleteByKnowledgeId(@Param("knowledgeId") Long knowledgeId);
}
