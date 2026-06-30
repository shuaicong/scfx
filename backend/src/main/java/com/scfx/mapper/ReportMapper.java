package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.Report;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReportMapper extends BaseMapper<Report> {

    /**
     * 搜索研报（按品种、状态、关键词过滤）
     */
    List<Report> searchReports(@Param("variety") String variety,
                               @Param("status") String status,
                               @Param("keyword") String keyword);
}
