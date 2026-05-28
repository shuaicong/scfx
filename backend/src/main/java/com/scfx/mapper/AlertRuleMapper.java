package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.AlertRule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 告警规则 Mapper
 */
@Mapper
public interface AlertRuleMapper extends BaseMapper<AlertRule> {

    /**
     * 根据规则类型查询
     */
    @Select("SELECT * FROM t_alert_rule WHERE rule_type = #{ruleType} AND enabled = 1 LIMIT 1")
    AlertRule findByType(@Param("ruleType") String ruleType);
}