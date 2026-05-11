package com.scfx.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class Category {
    private Long id;
    private String name;
    private String icon;
    private String color;
    private String description;
    private Long parentId;
    private Integer sortOrder;
    private Integer pinned;
    private String lastOperatedBy;
    private LocalDateTime lastOperatedAt;
    private String permissionLevel;
    private String allowedUsers;
    private String activeSeasonStart;
    private String activeSeasonEnd;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    private Long version;
    private Integer knowledgeCount;
    private List<Category> children;
}