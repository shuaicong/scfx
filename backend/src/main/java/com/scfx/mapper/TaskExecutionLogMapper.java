package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.TaskExecutionLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskExecutionLogMapper extends BaseMapper<TaskExecutionLog> {
}