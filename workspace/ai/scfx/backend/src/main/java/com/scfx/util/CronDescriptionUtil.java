package com.scfx.util;

import org.springframework.scheduling.support.CronExpression;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class CronDescriptionUtil {

    /**
     * 解析 Cron 表达式返回人类可读描述
     */
    public static String describe(String cronExpression) {
        try {
            String normalized = normalizeToSixFields(cronExpression);
            CronExpression.parse(normalized);

            String[] parts = normalized.split(" ");
            if (parts.length < 6) {
                return "无效的 Cron 表达式";
            }

            String second = parts[0];
            String minute = parts[1];
            String hour = parts[2];
            String dayOfMonth = parts[3];
            String month = parts[4];
            String dayOfWeek = parts[5];

            // 每天定点执行
            if ("0".equals(second) && !minute.contains(",") && !hour.contains(",")) {
                if ("*".equals(dayOfMonth) && "*".equals(month) && "*".equals(dayOfWeek)) {
                    return String.format("每天 %s:%s 执行", padLeft(hour, 2, '0'), padLeft(minute, 2, '0'));
                }
                // 每周定点
                if ("*".equals(dayOfMonth) && "*".equals(month) && !dayOfWeek.equals("?")) {
                    String dayName = getDayOfWeekName(dayOfWeek);
                    return String.format("每周%s %s:%s 执行", dayName, padLeft(hour, 2, '0'), padLeft(minute, 2, '0'));
                }
                // 每月定点
                if (!dayOfMonth.equals("*") && "*".equals(month) && "*".equals(dayOfWeek)) {
                    return String.format("每月第%s天 %s:%s 执行", dayOfMonth, padLeft(hour, 2, '0'), padLeft(minute, 2, '0'));
                }
            }

            // 工作日
            if (dayOfWeek.equals("MON-FRI")) {
                return String.format("工作日 %s:%s 执行", padLeft(hour, 2, '0'), padLeft(minute, 2, '0'));
            }

            return cronExpression;

        } catch (Exception e) {
            return "无效的 Cron 表达式";
        }
    }

    /**
     * 计算未来 N 次触发时间
     */
    public static List<String> calculateNextExecutions(String cronExpression, int count) {
        List<String> result = new ArrayList<>();
        try {
            String normalizedCron = normalizeToSixFields(cronExpression);
            CronExpression parsed = CronExpression.parse(normalizedCron);
            ZonedDateTime now = ZonedDateTime.now();

            ZonedDateTime next = parsed.next(now);
            for (int i = 0; i < count && next != null; i++) {
                result.add(next.toString());
                next = parsed.next(next);
            }
        } catch (Exception e) {
            // 忽略
        }
        return result;
    }

    /**
     * 将5字段Unix cron转换为6字段Spring cron
     * Unix: minute hour day month dow
     * Spring: second minute hour day month dow
     */
    public static String normalizeToSixFields(String cronExpression) {
        if (cronExpression == null) return null;
        String[] parts = cronExpression.trim().split("\\s+");
        if (parts.length == 5) {
            // Unix cron: minute hour day month dow -> prepend "0 second"
            return "0 " + cronExpression;
        }
        return cronExpression;
    }

    private static String getDayOfWeekName(String dayOfWeek) {
        switch (dayOfWeek) {
            case "MON": return "周一";
            case "TUE": return "周二";
            case "WED": return "周三";
            case "THU": return "周四";
            case "FRI": return "周五";
            case "SAT": return "周六";
            case "SUN": return "周日";
            case "1": return "周一";
            case "2": return "周二";
            case "3": return "周三";
            case "4": return "周四";
            case "5": return "周五";
            case "6": return "周六";
            case "7": return "周日";
            default: return dayOfWeek;
        }
    }

    private static String padLeft(String str, int len, char pad) {
        String s = str;
        while (s.length() < len) {
            s = pad + s;
        }
        return s;
    }
}