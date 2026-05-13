package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_category_mapping")
public class CategoryMapping {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String sourceType;        // 来源类型: liangxinwang/mysteel
    private String variety;           // 品种: corn/wheat/rice
    private String reportType;        // 报告类型: 日报/周报/月报
    private Long categoryId;          // 目标分类ID
    private Integer priority;         // 优先级 0-10
    private Integer enabled;          // 是否启用: 0-禁用 1-启用
    private String description;       // 规则描述

    // 扩展字段
    private LocalDateTime startTime;  // 生效开始时间
    private LocalDateTime endTime;    // 生效结束时间
    private String createdBy;         // 创建人
    private String updatedBy;         // 修改人
    private Long matchCount;          // 匹配次数（缓存）

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}