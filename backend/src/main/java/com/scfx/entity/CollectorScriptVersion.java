package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_collector_script_version")
public class CollectorScriptVersion {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String datasourceCode;     // 数据源编码

    private Integer version;           // 版本号

    private String filePath;           // 文件路径

    private String fileMd5;            // 文件MD5

    private Integer fileSize;          // 文件大小

    private Integer isCurrent;         // 是否当前版本: 1=是, 0=否

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private String createdBy;          // 创建人
}