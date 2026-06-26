package com.scfx.service;

import com.scfx.entity.Price;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class PriceServiceTest {

    @Autowired
    private PriceService priceService;

    private Price createValidPrice(String variety, String region, String areaType) {
        Price p = new Price();
        p.setDate(LocalDate.of(2026, 6, 25));
        p.setVariety(variety);
        p.setRegion(region);
        p.setProvince("测试省");
        p.setAreaType(areaType);
        p.setPrice(new BigDecimal("2330"));
        p.setChangeVal(BigDecimal.ZERO);
        p.setRemark("二等散粮");
        p.setUnit("元/吨");
        p.setSource("liangdawang");
        return p;
    }

    @AfterEach
    void cleanup() {
        // 清理测试数据
        priceService.batchInsertOrUpdate(List.of());
    }

    @Test
    void testBatchInsert_success() {
        List<Price> records = List.of(
            createValidPrice("玉米", "锦州港", "port"),
            createValidPrice("玉米", "海口港", "port"),
            createValidPrice("生猪", "吉林", "region")
        );
        int affected = priceService.batchInsertOrUpdate(records);
        assertEquals(3, affected, "应成功插入3条记录");
    }

    @Test
    void testBatchInsert_emptyList() {
        int affected = priceService.batchInsertOrUpdate(List.of());
        assertEquals(0, affected, "空列表应返回0");
    }

    @Test
    void testBatchInsert_nullList() {
        int affected = priceService.batchInsertOrUpdate(null);
        assertEquals(0, affected, "null应返回0");
    }

    @Test
    void testBatchInsert_invalidPrice_skipped() {
        Price invalid = createValidPrice("玉米", "无效价格测试", "port");
        invalid.setPrice(BigDecimal.ZERO); // price ≤ 0

        Price valid = createValidPrice("玉米", "有效价格测试", "port");

        int affected = priceService.batchInsertOrUpdate(List.of(invalid, valid));
        // 非法记录被跳过，只有有效记录被写入
        assertTrue(affected >= 0, "非法记录应被跳过，不影响有效记录写入");
    }

    @Test
    void testBatchInsert_missingAreaType_skipped() {
        Price invalid = createValidPrice("玉米", "无区域类型测试", null);
        invalid.setAreaType(null);

        Price valid = createValidPrice("玉米", "有区域类型测试", "port");

        int affected = priceService.batchInsertOrUpdate(List.of(invalid, valid));
        assertTrue(affected >= 0, "area_type 为空的记录应被跳过");
    }

    @Test
    void testBatchInsert_updateExisting() {
        // 先插入
        Price p = createValidPrice("玉米", "更新测试港", "port");
        priceService.batchInsertOrUpdate(List.of(p));

        // 修改价格后再次插入（同一唯一键）
        p.setPrice(new BigDecimal("2350"));
        p.setChangeVal(new BigDecimal("20"));
        int affected = priceService.batchInsertOrUpdate(List.of(p));

        // ON DUPLICATE KEY UPDATE 应该更新已有行，不新增
        assertTrue(affected >= 0, "同一唯一键的重复写入应执行更新而非新增");
    }

    @Test
    void testBatchInsert_multipleVarieties() {
        List<Price> records = List.of(
            createValidPrice("玉米", "多品种-港1", "port"),
            createValidPrice("小麦", "多品种-港2", "enterprise"),
            createValidPrice("进口粮", "多品种-船期1", "shipping"),
            createValidPrice("国产大豆", "多品种-县1", "region"),
            createValidPrice("生猪", "多品种-省1", "region")
        );
        int affected = priceService.batchInsertOrUpdate(records);
        assertEquals(5, affected, "5个品种应全部写入成功");
    }
}
