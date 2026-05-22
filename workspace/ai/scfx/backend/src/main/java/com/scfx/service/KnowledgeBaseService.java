package com.scfx.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scfx.entity.KnowledgeBase;

public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    /**
     * 根据内容Hash检查知识是否已存在
     */
    boolean existsByHash(String hash);

    /**
     * 删除知识条目并联动清理可视化数据
     * 1. 清理 t_knowledge_viz 记录
     * 2. 重置 viz_x / viz_y 为空
     * 3. 执行软删除
     */
    boolean removeWithViz(Long id);
}