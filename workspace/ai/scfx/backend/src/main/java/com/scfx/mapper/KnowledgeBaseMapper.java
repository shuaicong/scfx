package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

    List<KnowledgeBase> selectPending(@Param("categoryId") Long categoryId);
}