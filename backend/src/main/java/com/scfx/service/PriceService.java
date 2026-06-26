package com.scfx.service;

import com.scfx.entity.Price;
import com.scfx.mapper.PriceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceService {

    private final PriceMapper priceMapper;

    /**
     * 批量插入或更新价格记录
     * 使用 ON DUPLICATE KEY UPDATE 确保幂等
     * 唯一键: (date, variety, region, area_type, source)
     */
    @Transactional(rollbackFor = Exception.class)
    public int batchInsertOrUpdate(List<Price> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        // 写入前校验
        for (Price p : records) {
            validatePrice(p);
        }
        int affected = priceMapper.batchInsertOrUpdate(records);
        log.info("batchInsertOrUpdate: {} records, affected: {}", records.size(), affected);
        return affected;
    }

    /**
     * 校验价格记录合法性
     * - price 为 null 或 ≤0 时丢弃
     * - price > 99999 时丢弃
     * - change_val 绝对值 > 1000（生猪 > 5）时丢弃
     * - area_type 为空时丢弃
     */
    private void validatePrice(Price p) {
        if (p.getPrice() == null || p.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("非法价格: " + p.getPrice());
        }
        if (p.getPrice().compareTo(new BigDecimal("99999")) > 0) {
            throw new IllegalArgumentException("价格异常过大: " + p.getPrice());
        }
        if (p.getChangeVal() != null) {
            BigDecimal absChange = p.getChangeVal().abs();
            if ("生猪".equals(p.getVariety()) && absChange.compareTo(new BigDecimal("5")) > 0) {
                throw new IllegalArgumentException("生猪涨跌异常: " + p.getChangeVal());
            }
            if (!"生猪".equals(p.getVariety()) && absChange.compareTo(new BigDecimal("1000")) > 0) {
                throw new IllegalArgumentException("涨跌异常: " + p.getChangeVal());
            }
        }
        if (p.getAreaType() == null || p.getAreaType().isBlank()) {
            throw new IllegalArgumentException("area_type 不能为空, region=" + p.getRegion());
        }
    }
}
