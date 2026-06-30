package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.ReportTemplateVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ReportTemplateVersionMapper extends BaseMapper<ReportTemplateVersion> {

    @Select("SELECT MAX(version_number) FROM t_report_template_version WHERE template_id = #{templateId}")
    Integer selectMaxVersion(@Param("templateId") Long templateId);

    @Select("SELECT * FROM t_report_template_version WHERE template_id = #{templateId} ORDER BY version_number DESC")
    List<ReportTemplateVersion> selectByTemplateId(@Param("templateId") Long templateId);
}
