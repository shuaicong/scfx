-- V9__create_knowledge_image_table.sql
CREATE TABLE IF NOT EXISTS `t_knowledge_image` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `knowledge_id` bigint NOT NULL COMMENT '关联知识库ID',
  `source_url` varchar(2048) NOT NULL COMMENT '原始图片URL',
  `minio_path` varchar(512) NOT NULL COMMENT 'MinIO对象路径（相对桶路径）',
  `minio_bucket` varchar(128) NOT NULL DEFAULT 'knowledge-img' COMMENT 'MinIO桶名称',
  `file_size` int NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `file_type` varchar(32) NOT NULL DEFAULT '' COMMENT '文件类型 image/jpeg',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_id` (`knowledge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库图片明细';
