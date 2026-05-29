package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.TaskExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface TaskExecutionMapper extends BaseMapper<TaskExecution> {

    @Select("SELECT * FROM t_task_execution WHERE script_id = #{scriptId} AND status = 'running' LIMIT 1")
    TaskExecution findRunningByScriptId(@Param("scriptId") Long scriptId);

    @Select("SELECT * FROM t_task_execution WHERE script_id = #{scriptId} ORDER BY start_time DESC LIMIT 1")
    TaskExecution findLastByScriptId(@Param("scriptId") Long scriptId);

    @Select("<script>"
        + "SELECT te.* FROM t_task_execution te "
        + "INNER JOIN ("
        + "  SELECT script_id, MAX(start_time) AS max_start "
        + "  FROM t_task_execution "
        + "  WHERE script_id IN <foreach collection='scriptIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
        + "  GROUP BY script_id"
        + ") latest ON te.script_id = latest.script_id AND te.start_time = latest.max_start"
        + "</script>")
    List<TaskExecution> getLatestByScriptIds(@Param("scriptIds") List<Long> scriptIds);
}