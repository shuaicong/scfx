-- V12__add_t_price_unique_key.sql
-- 为 t_price 表添加联合唯一键，支撑 ON DUPLICATE KEY UPDATE 幂等写入
-- 唯一键: (date, variety, region, area_type, source)
-- 同一天同一港口既有平仓价又有深加工收购价，area_type 区分后不会互相覆盖

ALTER TABLE `t_price`
ADD UNIQUE KEY `uk_date_variety_region_area_source` (`date`, `variety`, `region`, `area_type`, `source`);
