package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.ReportVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReportVersionMapper extends BaseMapper<ReportVersion> {

    @Select("SELECT DISTINCT report_id FROM t_report_version")
    List<Long> selectDistinctReportIds();

    @Select("SELECT * FROM t_report_version WHERE report_id = #{reportId} ORDER BY version_number DESC")
    List<ReportVersion> selectByReportId(@Param("reportId") Long reportId);
}
