package com.scfx.mapper;

import com.scfx.entity.Category;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface CategoryMapper {
    @Select("SELECT * FROM t_category WHERE deleted_at IS NULL ORDER BY sort_order")
    List<Category> findAll();

    @Select("SELECT * FROM t_category WHERE deleted_at IS NULL AND parent_id = #{parentId} ORDER BY sort_order")
    List<Category> findByParentId(Long parentId);

    @Select("SELECT * FROM t_category WHERE id = #{id}")
    Category findById(Long id);

    @Select("SELECT * FROM t_category WHERE deleted_at IS NOT NULL ORDER BY deleted_at DESC")
    List<Category> findDeleted();

    @Select("SELECT * FROM t_category WHERE deleted_at IS NULL AND name LIKE CONCAT('%', #{name}, '%')")
    List<Category> searchByName(String name);

    @Select("SELECT COUNT(*) FROM t_knowledge_category WHERE category_id = #{categoryId}")
    int countKnowledgeByCategoryId(Long categoryId);

    @Insert("INSERT INTO t_category (name, icon, color, description, parent_id, sort_order, pinned, last_operated_by, last_operated_at, permission_level, allowed_users, active_season_start, active_season_end, version) " +
            "VALUES (#{name}, #{icon}, #{color}, #{description}, #{parentId}, #{sortOrder}, #{pinned}, #{lastOperatedBy}, #{lastOperatedAt}, #{permissionLevel}, #{allowedUsers}, #{activeSeasonStart}, #{activeSeasonEnd}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Category category);

    @Update("UPDATE t_category SET name=#{name}, icon=#{icon}, color=#{color}, description=#{description}, " +
            "parent_id=#{parentId}, sort_order=#{sortOrder}, pinned=#{pinned}, last_operated_by=#{lastOperatedBy}, " +
            "last_operated_at=NOW(), permission_level=#{permissionLevel}, allowed_users=#{allowedUsers}, " +
            "active_season_start=#{activeSeasonStart}, active_season_end=#{activeSeasonEnd}, " +
            "version=version+1, updated_at=NOW() WHERE id=#{id}")
    int update(Category category);

    @Update("UPDATE t_category SET deleted_at=NOW(), version=version+1, last_operated_by=#{operator}, last_operated_at=NOW() WHERE id=#{id}")
    int softDelete(@Param("id") Long id, @Param("operator") String operator);

    @Update("UPDATE t_category SET deleted_at=NULL, version=version+1, updated_at=NOW(), last_operated_by=#{operator}, last_operated_at=NOW() WHERE id=#{id}")
    int restore(@Param("id") Long id, @Param("operator") String operator);

    @Delete("DELETE FROM t_category WHERE id=#{id}")
    int permanentDelete(Long id);

    @Select("SELECT COALESCE(MAX(version), 0) FROM t_category")
    Long getMaxVersion();

    @Select("SELECT id FROM t_category WHERE parent_id = #{parentId} AND deleted_at IS NULL")
    List<Long> findChildIds(Long parentId);
}