package com.scfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scfx.entity.CollectionScript;
import com.scfx.entity.ScriptVersion;
import com.scfx.mapper.ScriptVersionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptVersionService {

    private final ScriptVersionMapper versionMapper;

    /**
     * 创建新版本
     */
    public ScriptVersion createVersion(CollectionScript script, String changeDescription, String createdBy) {
        Integer versionNum = getCurrentVersionNum(script.getId()) + 1;

        LambdaQueryWrapper<ScriptVersion> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ScriptVersion::getScriptId, script.getId())
               .eq(ScriptVersion::getIsCurrent, true);
        List<ScriptVersion> oldVersions = versionMapper.selectList(wrapper);
        for (ScriptVersion v : oldVersions) {
            v.setIsCurrent(false);
            versionMapper.updateById(v);
        }

        ScriptVersion version = new ScriptVersion();
        version.setScriptId(script.getId());
        version.setVersionNum(versionNum);
        version.setScriptName(script.getScriptName());
        version.setScriptContent(script.getScriptContent());
        version.setTriggerType(script.getTriggerType());
        version.setRepeatType(script.getRepeatType());
        version.setRepeatTime(script.getRepeatTime());
        version.setWeeklyDays(script.getWeeklyDays());
        version.setMonthlyDay(script.getMonthlyDay());
        version.setMonthlyLastDay(script.getMonthlyLastDay());
        version.setCronExpression(script.getCronExpression());
        version.setEndType(script.getEndType());
        version.setEndTime(script.getEndTime());
        version.setRepeatCount(script.getRepeatCount());
        version.setChangeDescription(changeDescription);
        version.setCreatedBy(createdBy);
        version.setCreatedAt(LocalDateTime.now());
        version.setIsCurrent(true);

        versionMapper.insert(version);
        return version;
    }

    /**
     * 获取脚本所有版本
     */
    public List<ScriptVersion> getVersions(Long scriptId) {
        return versionMapper.selectList(
            new LambdaQueryWrapper<ScriptVersion>()
                .eq(ScriptVersion::getScriptId, scriptId)
                .orderByDesc(ScriptVersion::getVersionNum)
        );
    }

    /**
     * 获取当前版本号
     */
    public Integer getCurrentVersionNum(Long scriptId) {
        ScriptVersion version = versionMapper.selectOne(
            new LambdaQueryWrapper<ScriptVersion>()
                .eq(ScriptVersion::getScriptId, scriptId)
                .eq(ScriptVersion::getIsCurrent, true)
        );
        return version != null ? version.getVersionNum() : 0;
    }

    /**
     * 获取版本详情
     */
    public ScriptVersion getVersion(Long versionId) {
        return versionMapper.selectById(versionId);
    }

    /**
     * 恢复版本
     */
    public CollectionScript restoreVersion(Long scriptId, Long versionId, CollectionScript currentScript) {
        ScriptVersion version = versionMapper.selectById(versionId);
        if (version == null || !version.getScriptId().equals(scriptId)) {
            throw new IllegalArgumentException("版本不存在");
        }

        currentScript.setScriptContent(version.getScriptContent());
        currentScript.setTriggerType(version.getTriggerType());
        currentScript.setRepeatType(version.getRepeatType());
        currentScript.setRepeatTime(version.getRepeatTime());
        currentScript.setWeeklyDays(version.getWeeklyDays());
        currentScript.setMonthlyDay(version.getMonthlyDay());
        currentScript.setMonthlyLastDay(version.getMonthlyLastDay());
        currentScript.setCronExpression(version.getCronExpression());
        currentScript.setEndType(version.getEndType());
        currentScript.setEndTime(version.getEndTime());
        currentScript.setRepeatCount(version.getRepeatCount());

        return currentScript;
    }

    /**
     * 版本对比
     */
    public Map<String, Object> compareVersions(Long versionId1, Long versionId2) {
        ScriptVersion v1 = versionMapper.selectById(versionId1);
        ScriptVersion v2 = versionMapper.selectById(versionId2);

        Map<String, Object> result = new HashMap<>();
        result.put("version1", v1);
        result.put("version2", v2);

        if (v1 != null && v2 != null) {
            Map<String, Object> diff = new HashMap<>();
            if (!equals(v1.getScriptContent(), v2.getScriptContent())) {
                diff.put("scriptContent", new String[]{
                    v1.getScriptContent() != null ? v1.getScriptContent() : "",
                    v2.getScriptContent() != null ? v2.getScriptContent() : ""
                });
            }
            if (!equals(v1.getTriggerType(), v2.getTriggerType())) {
                diff.put("triggerType", new String[]{
                    v1.getTriggerType() != null ? v1.getTriggerType() : "",
                    v2.getTriggerType() != null ? v2.getTriggerType() : ""
                });
            }
            if (!equals(v1.getRepeatType(), v2.getRepeatType())) {
                diff.put("repeatType", new String[]{
                    v1.getRepeatType() != null ? v1.getRepeatType() : "",
                    v2.getRepeatType() != null ? v2.getRepeatType() : ""
                });
            }
            result.put("diff", diff);
        }

        return result;
    }

    private boolean equals(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}