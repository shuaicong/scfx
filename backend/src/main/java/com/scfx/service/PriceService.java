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
        // 写入前校验——跳过非法记录，不阻塞批次
        List<Price> validRecords = new java.util.ArrayList<>();
        for (Price p : records) {
            try {
                validatePrice(p);
                validRecords.add(p);
            } catch (IllegalArgumentException e) {
                log.warn("跳过无效价格记录: {} region={} variety={}", e.getMessage(), p.getRegion(), p.getVariety());
            }
        }
        if (validRecords.isEmpty()) {
            log.warn("所有记录均无效，跳过写入");
            return 0;
        }
        // 补充 variety/region 非空校验
        for (Price p : validRecords) {
            if (p.getVariety() == null || p.getVariety().isBlank()) {
                log.warn("品种不能为空，跳过: region={}", p.getRegion());
                continue;
            }
            if (p.getRegion() == null || p.getRegion().isBlank()) {
                log.warn("区域不能为空，跳过: variety={}", p.getVariety());
                continue;
            }
        }
        int affected = priceMapper.batchInsertOrUpdate(validRecords);
        log.info("batchInsertOrUpdate: {} records ({} valid), affected: {}", records.size(), validRecords.size(), affected);
        return affected;
    }

    /**
     * 校验价格记录合法性
     * - price 为 null 或 ≤0 时抛出异常
     * - price > 99999 时抛出异常
     * - change_val 绝对值 > 1000（生猪 > 5）时抛出异常
     * - area_type 为空时抛出异常
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
