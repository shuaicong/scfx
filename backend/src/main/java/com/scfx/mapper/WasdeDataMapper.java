package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.WasdeData;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface WasdeDataMapper extends BaseMapper<WasdeData> {

    /**
     * 批量插入或更新（基于联合唯一索引 ON DUPLICATE KEY UPDATE）
     */
    int batchInsertOrUpdate(@Param("list") List<WasdeData> list);
}
