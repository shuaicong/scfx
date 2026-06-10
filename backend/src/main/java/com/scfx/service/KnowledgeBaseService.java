package com.scfx.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scfx.entity.KnowledgeBase;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface KnowledgeBaseService extends IService<KnowledgeBase> {

    /**
     * 根据内容Hash检查知识是否已存在
     */
    boolean existsByHash(String hash);

    /**
     * 删除知识条目并联动清理可视化数据
     */
    boolean removeWithViz(Long id);

    /**
     * 从上传文件创建知识记录
     * @param file       上传的 .docx 文件
     * @param categoryId 所属分类 ID
     * @param title      文档标题
     * @return 创建的知识记录
     * @throws IOException 文件存储失败
     */
    KnowledgeBase createFromUpload(MultipartFile file, Long categoryId, String title) throws IOException;

    /**
     * 同步分类关联到 t_knowledge_category 关联表
     * <p>
     * 所有写 categoryId 的地方都应调用此方法，确保关联表与主表字段一致。
     * 会先清除旧的关联记录再写入新的。
     *
     * @param knowledgeId 知识 ID
     * @param categoryId  分类 ID（null 则只清除不写入）
     */
    void updateCategory(Long knowledgeId, Long categoryId);
}