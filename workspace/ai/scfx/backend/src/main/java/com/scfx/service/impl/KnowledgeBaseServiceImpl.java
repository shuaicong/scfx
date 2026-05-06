package com.scfx.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scfx.entity.KnowledgeBase;
import com.scfx.mapper.KnowledgeBaseMapper;
import com.scfx.service.KnowledgeBaseService;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase>
    implements KnowledgeBaseService {
}