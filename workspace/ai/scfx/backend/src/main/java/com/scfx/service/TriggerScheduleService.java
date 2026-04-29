package com.scfx.service;

import com.scfx.entity.CollectionScript;
import com.scfx.entity.TaskExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TriggerScheduleService {

    private final CollectionScriptService scriptService;
    private final TaskExecutionService executionService;

    /**
     * 每分钟检查一次待执行的脚本
     */
    @Scheduled(fixedRate = 60000)
    public void checkScheduledScripts() {
        LocalDateTime now = LocalDateTime.now();
        LocalTime nowTime = now.toLocalTime();

        List<CollectionScript> scripts = scriptService.getEnabledScripts();
        for (CollectionScript script : scripts) {
            if (shouldExecute(script, now, nowTime)) {
                log.info("触发定时任务: {}", script.getScriptName());
                executeScript(script.getId(), "scheduled");
            }
        }
    }

    /**
     * 判断是否应该执行
     */
    private boolean shouldExecute(CollectionScript script, LocalDateTime now, LocalTime nowTime) {
        if (script.getStartTime() != null && now.isBefore(script.getStartTime())) {
            return false;
        }
        if (script.getEndTime() != null && now.isAfter(script.getEndTime())) {
            return false;
        }

        if ("date".equals(script.getEndType()) && script.getEndTime() != null && now.isAfter(script.getEndTime())) {
            return false;
        }

        String triggerType = script.getTriggerType();
        if ("once".equals(triggerType)) {
            if (script.getLastExecutionTime() != null) {
                return false;
            }
            return isTimeMatch(script.getRepeatTime(), nowTime) && isDateMatch(script, now);
        } else if ("repeat".equals(triggerType)) {
            String repeatType = script.getRepeatType();
            if ("daily".equals(repeatType)) {
                return isTimeMatch(script.getRepeatTime(), nowTime);
            } else if ("weekly".equals(repeatType)) {
                return isWeeklyMatch(script, now) && isTimeMatch(script.getRepeatTime(), nowTime);
            } else if ("monthly".equals(repeatType)) {
                return isMonthlyMatch(script, now) && isTimeMatch(script.getRepeatTime(), nowTime);
            }
        } else if ("cron".equals(triggerType)) {
            return isCronMatch(script.getCronExpression());
        }

        return false;
    }

    private boolean isTimeMatch(String repeatTime, LocalTime nowTime) {
        if (repeatTime == null) return false;
        LocalTime targetTime = LocalTime.parse(repeatTime);
        return Math.abs(nowTime.toSecondOfDay() - targetTime.toSecondOfDay()) < 60;
    }

    private boolean isDateMatch(CollectionScript script, LocalDateTime now) {
        if (script.getStartTime() == null) return true;
        LocalDateTime targetDateTime = script.getStartTime();
        return now.toLocalDate().equals(targetDateTime.toLocalDate());
    }

    private boolean isWeeklyMatch(CollectionScript script, LocalDateTime now) {
        if (script.getWeeklyDays() == null || script.getWeeklyDays().isEmpty()) {
            return false;
        }
        DayOfWeek today = now.getDayOfWeek();
        int todayValue = today.getValue();
        String[] days = script.getWeeklyDays().split(",");
        for (String day : days) {
            if (Integer.parseInt(day.trim()) == todayValue) {
                return true;
            }
        }
        return false;
    }

    private boolean isMonthlyMatch(CollectionScript script, LocalDateTime now) {
        if (Boolean.TRUE.equals(script.getMonthlyLastDay())) {
            int lastDay = now.toLocalDate().lengthOfMonth();
            return now.getDayOfMonth() == lastDay;
        } else if (script.getMonthlyDay() != null) {
            int targetDay = script.getMonthlyDay();
            int actualMaxDay = now.toLocalDate().lengthOfMonth();
            int actualDay = Math.min(targetDay, actualMaxDay);
            return now.getDayOfMonth() == actualDay;
        }
        return false;
    }

    private boolean isCronMatch(String cronExpression) {
        if (cronExpression == null || cronExpression.isEmpty()) {
            return false;
        }
        try {
            org.springframework.scheduling.support.CronExpression.parse(cronExpression);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 执行脚本
     */
    public void executeScript(Long scriptId, String triggerType) {
        TaskExecution execution = executionService.createExecution(scriptId, triggerType);
        scriptService.updateLastExecutionTime(scriptId);
        log.info("创建执行记录: executionId={}, scriptId={}", execution.getExecutionId(), scriptId);
    }
}