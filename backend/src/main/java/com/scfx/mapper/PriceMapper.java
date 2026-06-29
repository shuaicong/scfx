package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.Price;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PriceMapper extends BaseMapper<Price> {

    /**
     * 批量插入或更新（ON DUPLICATE KEY UPDATE）
     * 唯一键: (date, variety, region, source)
     */
    int batchInsertOrUpdate(@Param("list") List<Price> list);

    /** 获取有最新数据的品种列表 */
    List<String> getDistinctVarieties();

    /** 获取品种最新日的代表性地区 */
    List<String> getRegionsByVariety(@Param("variety") String variety);
}
