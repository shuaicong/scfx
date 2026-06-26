-- V10__alter_t_price_add_fields.sql
-- 扩展 t_price 表，新增粮达网价格指数所需的字段

-- 新增 province 字段：省份/大区分组
ALTER TABLE `t_price`
ADD COLUMN `province` varchar(50) DEFAULT NULL
COMMENT '省份/大区（北港/黑龙江/美湾/东北等）'
AFTER `region`;

-- 新增 area_type 字段：region 分类
ALTER TABLE `t_price`
ADD COLUMN `area_type` varchar(20) DEFAULT NULL
COMMENT 'region分类: port=港口, enterprise=企业, region=产区, shipping=船期, origin=原产国'
AFTER `province`;

-- 新增 remark 字段：粮质等级/蛋白含量/关税等备注
ALTER TABLE `t_price`
ADD COLUMN `remark` varchar(200) DEFAULT NULL
COMMENT '备注（粮质等级/蛋白含量/关税/品种说明）'
AFTER `change_val`;

-- 新增索引优化 AI 问答查询
ALTER TABLE `t_price` ADD INDEX `idx_variety_region_date` (`variety`, `region`, `date`);
ALTER TABLE `t_price` ADD INDEX `idx_source_date` (`source`, `date`);
ALTER TABLE `t_price` ADD INDEX `idx_variety_province` (`variety`, `province`);
