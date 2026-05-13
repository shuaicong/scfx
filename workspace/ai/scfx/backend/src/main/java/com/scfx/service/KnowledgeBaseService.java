package com.scfx.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scfx.entity.KnowledgeBase;

public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    /**
     * 根据内容Hash检查知识是否已存在
     */
    boolean existsByHash(String hash);
}