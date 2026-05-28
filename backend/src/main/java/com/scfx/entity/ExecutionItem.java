package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 执行采集数据项 - 记录每次执行关联的数据条目
 */
@Data
@TableName("t_execution_item")
public class ExecutionItem {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 执行ID */
    private String executionId;

    /** 知识库条目ID */
    private Long knowledgeId;

    /** 标题 */
    private String title;

    /** 来源URL */
    private String url;

    /** 操作类型：created/skipped_duplicate/skipped_existing/error */
    private String action;

    /** 失败原因 */
    private String errorMessage;

    // === 以下字段不在数据库表中，由服务层查询时填充 ===

    /** 内容长度（字数） */
    @TableField(exist = false)
    private Integer contentLength;

    /** 图片数量 */
    @TableField(exist = false)
    private Integer imageCount;

    /** 内容截取（头200字 + 尾100字） */
    @TableField(exist = false)
    private String contentPreview;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
