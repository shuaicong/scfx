package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.entity.Price;
import com.scfx.mapper.PriceMapper;
import com.scfx.service.PriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/price")
@RequiredArgsConstructor
public class PriceController {

    private final PriceService priceService;
    private final PriceMapper priceMapper;

    /**
     * 批量写入价格记录
     * {
     *   "execution_id": "uuid",
     *   "source": "liangdawang",
     *   "total_records": 224,
     *   "records": [...]
     * }
     */
    @PostMapping("/batch")
    public Result<Map<String, Object>> batchInsert(@RequestBody Map<String, Object> body) {
        String source = (String) body.get("source");
        String executionId = (String) body.get("execution_id");

        // 校验 source（防误写入）
        if (source == null || !source.equals("liangdawang")) {
            return Result.error("仅支持 source=liangdawang 的写入");
        }

        // 解析 records
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawRecords = (List<Map<String, Object>>) body.get("records");
        if (rawRecords == null || rawRecords.isEmpty()) {
            return Result.error("records 不能为空");
        }

        List<Price> records = rawRecords.stream().map(this::mapToPrice).toList();

        try {
            int affected = priceService.batchInsertOrUpdate(records);
            // 注意：批量写入时 source=liangdawang 跳过知识库生成
            log.info("Price batch insert: executionId={}, source={}, records={}, affected={}",
                    executionId, source, records.size(), affected);
            return Result.success(Map.of(
                    "affected", affected,
                    "total", records.size()
            ));
        } catch (Exception e) {
            log.error("Price batch insert failed: executionId={}, error={}", executionId, e.getMessage());
            return Result.error("批量写入失败: " + e.getMessage());
        }
    }

    /**
     * 将 Map 转为 Price 实体
     */
    private Price mapToPrice(Map<String, Object> m) {
        Price p = new Price();
        try {
            if (m.get("date") != null) p.setDate(java.time.LocalDate.parse(m.get("date").toString()));
        } catch (java.time.format.DateTimeParseException e) {
            log.warn("日期解析失败: {}", m.get("date"));
        }
        if (m.get("variety") != null) p.setVariety(m.get("variety").toString());
        if (m.get("region") != null) p.setRegion(m.get("region").toString());
        if (m.get("province") != null) p.setProvince(m.get("province").toString());
        if (m.get("area_type") != null) p.setAreaType(m.get("area_type").toString());
        try {
            if (m.get("price") != null) p.setPrice(new java.math.BigDecimal(m.get("price").toString()));
        } catch (NumberFormatException e) {
            log.warn("价格解析失败: {}", m.get("price"));
        }
        if (m.get("change_val") != null) {
            String cv = m.get("change_val").toString();
            if (!cv.isEmpty()) {
                try {
                    p.setChangeVal(new java.math.BigDecimal(cv));
                } catch (NumberFormatException e) {
                    log.warn("涨跌值解析失败: {}", cv);
                }
            }
        }
        if (m.get("remark") != null) p.setRemark(m.get("remark").toString());
        if (m.get("unit") != null) p.setUnit(m.get("unit").toString());
        if (m.get("source") != null) p.setSource(m.get("source").toString());
        return p;
    }

    /**
     * 获取动态建议问题（基于 t_price 最新数据）
     * GET /api/price/suggestions
     */
    @GetMapping("/suggestions")
    public Result<List<String>> getSuggestions() {
        List<String> suggestions = new ArrayList<>();

        // 1. 价格查询建议
        try {
            List<String> varieties = priceMapper.getDistinctVarieties();
            for (String v : varieties) {
                List<String> regions = priceMapper.getRegionsByVariety(v);
                if (!regions.isEmpty()) {
                    suggestions.add("今天" + v + "价格是多少？");
                    suggestions.add(v + " " + regions.get(0) + " 价格");
                } else {
                    suggestions.add("今天" + v + "价格是多少？");
                }
            }
        } catch (Exception e) {
            log.warn("获取价格建议失败: {}", e.getMessage());
        }

        // 2. 补充趋势/对比类建议
        suggestions.add("最近一周玉米走势");
        suggestions.add("北港和南港玉米价差");

        // 限制返回数量
        if (suggestions.size() > 8) {
            suggestions = suggestions.subList(0, 8);
        }

        return Result.success(suggestions);
    }
}
