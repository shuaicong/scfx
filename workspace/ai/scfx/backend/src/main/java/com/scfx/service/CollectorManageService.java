package com.scfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scfx.common.Result;
import com.scfx.entity.CollectorInfo;
import com.scfx.mapper.CollectorInfoMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 采集器管理服务
 * 负责管理 Python SDK 的注册和状态
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorManageService {

    private final CollectorInfoMapper collectorInfoMapper;

    /**
     * 注册采集器
     */
    public CollectorInfo register(CollectorInfo info) {
        // 检查是否已存在
        LambdaQueryWrapper<CollectorInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectorInfo::getCollectorName, info.getCollectorName())
               .eq(CollectorInfo::getSource, info.getSource());

        CollectorInfo existing = collectorInfoMapper.selectOne(wrapper);
        if (existing != null) {
            // 更新已有记录
            existing.setSdkVersion(info.getSdkVersion());
            existing.setLastHeartbeat(LocalDateTime.now());
            existing.setStatus("online");
            existing.setInstanceCount(existing.getInstanceCount() == null ? 1 : existing.getInstanceCount() + 1);
            collectorInfoMapper.updateById(existing);
            log.info("采集器已上线: {}", info.getCollectorName());
            return existing;
        }

        // 新增
        info.setStatus("online");
        info.setRegisteredAt(LocalDateTime.now());
        info.setLastHeartbeat(LocalDateTime.now());
        info.setInstanceCount(1);
        collectorInfoMapper.insert(info);
        log.info("采集器注册成功: {}", info.getCollectorName());
        return info;
    }

    /**
     * 心跳
     */
    public void heartbeat(String collectorName, String source) {
        LambdaQueryWrapper<CollectorInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectorInfo::getCollectorName, collectorName)
               .eq(CollectorInfo::getSource, source);

        CollectorInfo info = collectorInfoMapper.selectOne(wrapper);
        if (info != null) {
            info.setLastHeartbeat(LocalDateTime.now());
            info.setStatus("online");
            collectorInfoMapper.updateById(info);
        }
    }

    /**
     * 下线采集器
     */
    public void offline(String collectorName, String source) {
        LambdaQueryWrapper<CollectorInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectorInfo::getCollectorName, collectorName)
               .eq(CollectorInfo::getSource, source);

        CollectorInfo info = collectorInfoMapper.selectOne(wrapper);
        if (info != null) {
            info.setStatus("offline");
            info.setInstanceCount(info.getInstanceCount() == null ? 0 : Math.max(0, info.getInstanceCount() - 1));
            collectorInfoMapper.updateById(info);
            log.info("采集器已下线: {}", collectorName);
        }
    }

    /**
     * 获取所有在线采集器
     */
    public List<CollectorInfo> getOnlineCollectors() {
        LambdaQueryWrapper<CollectorInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectorInfo::getStatus, "online");
        return collectorInfoMapper.selectList(wrapper);
    }

    /**
     * 获取采集器列表（分页）
     */
    public List<CollectorInfo> getCollectors(int page, int size) {
        LambdaQueryWrapper<CollectorInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(CollectorInfo::getLastHeartbeat);
        wrapper.last("LIMIT " + size + " OFFSET " + ((page - 1) * size));
        return collectorInfoMapper.selectList(wrapper);
    }

    /**
     * 获取采集器详情
     */
    public Result<CollectorInfo> getCollectorById(Long id) {
        CollectorInfo info = collectorInfoMapper.selectById(id);
        if (info == null) {
            return Result.error("采集器不存在");
        }
        return Result.success(info);
    }

    /**
     * 更新采集器信息
     */
    public Result<CollectorInfo> updateCollector(CollectorInfo info) {
        CollectorInfo existing = collectorInfoMapper.selectById(info.getId());
        if (existing == null) {
            return Result.error("采集器不存在");
        }
        collectorInfoMapper.updateById(info);
        log.info("采集器信息已更新: {}", info.getCollectorName());
        return Result.success(collectorInfoMapper.selectById(info.getId()));
    }

    /**
     * 启用采集器
     */
    public Result<Void> enableCollector(Long id) {
        CollectorInfo info = collectorInfoMapper.selectById(id);
        if (info == null) {
            return Result.error("采集器不存在");
        }
        info.setStatus("online");
        collectorInfoMapper.updateById(info);
        log.info("采集器已启用: {}", info.getCollectorName());
        return Result.success();
    }

    /**
     * 禁用采集器
     */
    public Result<Void> disableCollector(Long id) {
        CollectorInfo info = collectorInfoMapper.selectById(id);
        if (info == null) {
            return Result.error("采集器不存在");
        }
        info.setStatus("disabled");
        collectorInfoMapper.updateById(info);
        log.info("采集器已禁用: {}", info.getCollectorName());
        return Result.success();
    }

    /**
     * 删除采集器
     */
    public Result<Void> deleteCollector(Long id) {
        collectorInfoMapper.deleteById(id);
        log.info("采集器已删除: id={}", id);
        return Result.success();
    }

    /**
     * 获取采集器统计信息
     */
    public Result<Map<String, Object>> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long total = collectorInfoMapper.selectCount(null);
        long online = collectorInfoMapper.selectCount(
            new LambdaQueryWrapper<CollectorInfo>().eq(CollectorInfo::getStatus, "online"));
        long offline = collectorInfoMapper.selectCount(
            new LambdaQueryWrapper<CollectorInfo>().eq(CollectorInfo::getStatus, "offline"));
        long disabled = collectorInfoMapper.selectCount(
            new LambdaQueryWrapper<CollectorInfo>().eq(CollectorInfo::getStatus, "disabled"));

        stats.put("total", total);
        stats.put("online", online);
        stats.put("offline", offline);
        stats.put("disabled", disabled);

        return Result.success(stats);
    }

    /**
     * 按来源获取采集器列表
     */
    public List<CollectorInfo> getCollectorsBySource(String source) {
        LambdaQueryWrapper<CollectorInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectorInfo::getSource, source);
        return collectorInfoMapper.selectList(wrapper);
    }

    /**
     * 定时清理离线采集器（超过5分钟无心跳）
     */
    @Scheduled(fixedRate = 60000)
    public void cleanupOfflineCollectors() {
        LambdaQueryWrapper<CollectorInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectorInfo::getStatus, "online")
               .lt(CollectorInfo::getLastHeartbeat, LocalDateTime.now().minusMinutes(5));

        List<CollectorInfo> offlineList = collectorInfoMapper.selectList(wrapper);
        for (CollectorInfo info : offlineList) {
            info.setStatus("offline");
            collectorInfoMapper.updateById(info);
            log.warn("采集器超时下线: {}", info.getCollectorName());
        }

        if (!offlineList.isEmpty()) {
            log.info("清理 {} 个离线采集器", offlineList.size());
        }
    }

    /**
     * 按来源统计采集器
     */
    public long countBySource(String source) {
        LambdaQueryWrapper<CollectorInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CollectorInfo::getSource, source)
               .eq(CollectorInfo::getStatus, "online");
        return collectorInfoMapper.selectCount(wrapper);
    }
}
