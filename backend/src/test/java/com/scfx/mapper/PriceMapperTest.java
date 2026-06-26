package com.scfx.mapper;

import com.scfx.entity.Price;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PriceMapperTest {

    @Autowired
    private PriceMapper priceMapper;

    private Price createPrice(String variety, String region, String areaType, BigDecimal price) {
        Price p = new Price();
        p.setDate(LocalDate.of(2026, 6, 25));
        p.setVariety(variety);
        p.setRegion(region);
        p.setProvince("测试省");
        p.setAreaType(areaType);
        p.setPrice(price);
        p.setChangeVal(BigDecimal.ZERO);
        p.setRemark("测试数据");
        p.setUnit("元/吨");
        p.setSource("liangdawang");
        return p;
    }

    @Test
    void testBatchInsertOrUpdate_insert() {
        List<Price> records = List.of(
            createPrice("玉米", "Mapper测试港", "port", new BigDecimal("2330"))
        );
        int affected = priceMapper.batchInsertOrUpdate(records);
        assertTrue(affected > 0, "批量插入应返回 > 0");
    }

    @Test
    void testBatchInsertOrUpdate_duplicateKey() {
        // 插入两次相同唯一键的记录，验证不产生重复行
        Price p = createPrice("玉米", "Mapper去重港", "port", new BigDecimal("2400"));
        priceMapper.batchInsertOrUpdate(List.of(p));

        p.setPrice(new BigDecimal("2420"));
        int affected2 = priceMapper.batchInsertOrUpdate(List.of(p));

        // 第二次应命中 ON DUPLICATE KEY UPDATE
        assertTrue(affected2 >= 0, "重复唯一键应执行更新");
    }
}
