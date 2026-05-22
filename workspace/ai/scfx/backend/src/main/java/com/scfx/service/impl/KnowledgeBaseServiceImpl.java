package com.scfx.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scfx.entity.KnowledgeBase;
import com.scfx.mapper.KnowledgeBaseMapper;
import com.scfx.service.KnowledgeBaseService;
import com.scfx.service.VectorStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase>
    implements KnowledgeBaseService {

    private final VectorStore vectorStore;

    @Override
    public boolean existsByHash(String hash) {
        LambdaQueryWrapper<KnowledgeBase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(KnowledgeBase::getContentHash, hash);
        return baseMapper.selectCount(wrapper) > 0;
    }

    @Override
    @Transactional
    public boolean removeWithViz(Long id) {
        KnowledgeBase kb = baseMapper.selectById(id);
        if (kb == null) return false;

        // 1. 通过 VectorStore 清理向量数据
        vectorStore.deleteVector(id);

        // 2. 重置可视化坐标
        kb.setVizX(null);
        kb.setVizY(null);
        kb.setVizZ(null);
        baseMapper.updateById(kb);

        // 3. 执行软删除
        return super.removeById(id);
    }
}