package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.TaskExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TaskExecutionMapper extends BaseMapper<TaskExecution> {

    @Select("SELECT * FROM t_task_execution WHERE script_id = #{scriptId} AND status = 'running' LIMIT 1")
    TaskExecution findRunningByScriptId(@Param("scriptId") Long scriptId);

    @Select("SELECT * FROM t_task_execution WHERE script_id = #{scriptId} ORDER BY start_time DESC LIMIT 1")
    TaskExecution findLastByScriptId(@Param("scriptId") Long scriptId);
}