package com.scfx.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;
import java.util.Map;

@Mapper
public interface CategoryStatsMapper {
    @Select("SELECT c.id, c.name, c.icon, COUNT(kc.knowledge_id) as knowledge_count " +
            "FROM t_category c LEFT JOIN t_knowledge_category kc ON c.id = kc.category_id " +
            "WHERE c.deleted_at IS NULL " +
            "GROUP BY c.id, c.name, c.icon ORDER BY knowledge_count DESC LIMIT 10")
    List<Map<String, Object>> findTopCategoriesByKnowledgeCount();

    @Select("SELECT c.id, c.name, COUNT(kc.knowledge_id) as knowledge_count " +
            "FROM t_category c LEFT JOIN t_knowledge_category kc ON c.id = kc.category_id " +
            "WHERE c.deleted_at IS NULL " +
            "GROUP BY c.id, c.name ORDER BY knowledge_count ASC")
    List<Map<String, Object>> findCategoriesByKnowledgeCountAsc();

    @Select("SELECT c.id, c.name " +
            "FROM t_category c " +
            "WHERE c.deleted_at IS NULL " +
            "AND c.name IN (SELECT name FROM t_category WHERE deleted_at IS NULL GROUP BY name HAVING COUNT(*) > 1)")
    List<Map<String, Object>> findDuplicateNameCategories();
}