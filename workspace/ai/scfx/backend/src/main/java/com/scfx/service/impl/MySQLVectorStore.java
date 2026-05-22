package com.scfx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scfx.entity.KnowledgeViz;
import com.scfx.mapper.KnowledgeVizMapper;
import com.scfx.service.VectorStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MySQL 向量存储实现
 * 将 768 维向量存入 t_knowledge_viz 表
 */
@Component
@RequiredArgsConstructor
public class MySQLVectorStore implements VectorStore {

    private final KnowledgeVizMapper vizMapper;

    @Override
    public void saveVector(Long knowledgeId, float[] vector) {
        KnowledgeViz existing = vizMapper.selectByKnowledgeId(knowledgeId);
        if (existing == null) {
            KnowledgeViz viz = new KnowledgeViz();
            viz.setKnowledgeId(knowledgeId);
            viz.setVector768(vector);
            viz.setVizStatus("vectorized");
            vizMapper.insert(viz);
        } else {
            existing.setVector768(vector);
            existing.setVizStatus("vectorized");
            vizMapper.updateById(existing);
        }
    }

    @Override
    public void updateStatus(Long knowledgeId, String status) {
        KnowledgeViz existing = vizMapper.selectByKnowledgeId(knowledgeId);
        if (existing != null) {
            existing.setVizStatus(status);
            vizMapper.updateById(existing);
        }
    }

    @Override
    public void deleteVector(Long knowledgeId) {
        vizMapper.delete(new LambdaQueryWrapper<KnowledgeViz>()
                .eq(KnowledgeViz::getKnowledgeId, knowledgeId));
    }

    @Override
    public VectorEntry getByKnowledgeId(Long knowledgeId) {
        KnowledgeViz viz = vizMapper.selectByKnowledgeId(knowledgeId);
        return viz != null ? toEntry(viz) : null;
    }

    @Override
    public List<VectorEntry> getByCategoryId(Long categoryId) {
        return vizMapper.selectByCategoryId(categoryId)
                .stream().map(this::toEntry)
                .collect(Collectors.toList());
    }

    @Override
    public Map<Long, float[]> getVectorMapByKnowledgeIds(Collection<Long> knowledgeIds) {
        List<KnowledgeViz> list = vizMapper.selectList(
                new LambdaQueryWrapper<KnowledgeViz>().in(KnowledgeViz::getKnowledgeId, knowledgeIds));
        Map<Long, float[]> map = new LinkedHashMap<>();
        for (KnowledgeViz viz : list) {
            if (viz.getVector768() != null) {
                map.put(viz.getKnowledgeId(), viz.getVector768());
            }
        }
        return map;
    }

    @Override
    public Map<Long, float[]> getVectorMapByCategoryId(Long categoryId) {
        List<VectorEntry> entries = getByCategoryId(categoryId);
        Map<Long, float[]> map = new LinkedHashMap<>();
        for (VectorEntry entry : entries) {
            if (entry.getVector() != null) {
                map.put(entry.getKnowledgeId(), entry.getVector());
            }
        }
        return map;
    }

    @Override
    public String name() {
        return "MySQL";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    private VectorEntry toEntry(KnowledgeViz viz) {
        return new VectorEntry(viz.getId(), viz.getKnowledgeId(),
                viz.getVector768(), viz.getVizStatus());
    }
}
