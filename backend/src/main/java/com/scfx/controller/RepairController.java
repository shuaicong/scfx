package com.scfx.controller;

import com.scfx.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据修复控制器
 */
@RestController
@RequestMapping("/repair")
@RequiredArgsConstructor
public class RepairController {

    private final JdbcTemplate jdbcTemplate;

    @PostMapping("/encoding")
    public Result<String> fixEncoding() {
        // 直接用二进制更新，使用正确的UTF-8字节
        // 粮信网-玉米日报采集 的 UTF-8 字节是:
        // E7B29C E4BFA1 E7BD91 2D E78E89 E7B1B3 E697A5 E68AA5 E98787 E99B86
        byte[] correctBytes = new byte[] {
            (byte)0xE7, (byte)0xB2, (byte)0x9C,  // 粮
            (byte)0xE4, (byte)0xBF, (byte)0xA1,  // 信
            (byte)0xE7, (byte)0xBD, (byte)0x91,  // 网
            (byte)0x2D,                           // -
            (byte)0xE7, (byte)0x8E, (byte)0x89,  // 玉
            (byte)0xE7, (byte)0xB1, (byte)0xB3,  // 米
            (byte)0xE6, (byte)0x97, (byte)0xA5,  // 日
            (byte)0xE6, (byte)0x8A, (byte)0xA5,  // 报
            (byte)0xE9, (byte)0x87, (byte)0x87,  // 采
            (byte)0xE9, (byte)0x9B, (byte)0x86   // 集
        };

        String correctTaskName = new String(correctBytes, java.nio.charset.StandardCharsets.UTF_8);
        jdbcTemplate.update("UPDATE t_collection_task SET task_name = ? WHERE id = 1", correctTaskName);

        // 修复脚本名称: 粮信网玉米晨报采集
        byte[] scriptBytes = new byte[] {
            (byte)0xE7, (byte)0xB2, (byte)0x9C,  // 粮
            (byte)0xE4, (byte)0xBF, (byte)0xA1,  // 信
            (byte)0xE7, (byte)0xBD, (byte)0x91,  // 网
            (byte)0xE7, (byte)0x8E, (byte)0x89,  // 玉
            (byte)0xE7, (byte)0xB1, (byte)0xB3,  // 米
            (byte)0xE6, (byte)0x97, (byte)0xA5,  // 日
            (byte)0xE6, (byte)0x98, (byte)0xBC,  // 晨报 (晨 = E5 98 BC)
            (byte)0xE9, (byte)0x87, (byte)0x87,  // 采
            (byte)0xE9, (byte)0x9B, (byte)0x86   // 集
        };

        String correctScriptName = new String(scriptBytes, java.nio.charset.StandardCharsets.UTF_8);
        jdbcTemplate.update("UPDATE t_script SET script_name = ? WHERE id = 1", correctScriptName);

        return Result.success("数据修复完成: " + correctTaskName);
    }

    @GetMapping("/check")
    public Result<String> check() {
        String taskName = jdbcTemplate.queryForObject(
            "SELECT task_name FROM t_collection_task WHERE id = 1",
            String.class
        );
        return Result.success("当前taskName: " + taskName);
    }

    @GetMapping("/column-info")
    public Result<Map<String, Object>> columnInfo() {
        Map<String, Object> info = new HashMap<>();
        try {
            Map<String, Object> colInfo = jdbcTemplate.queryForMap(
                "SELECT COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH, COLUMN_TYPE " +
                "FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = 'grain_platform' AND TABLE_NAME = 't_collection_script' AND COLUMN_NAME = 'repeat_time'"
            );
            info.put("column", colInfo);
            // Also check all repeat_time values in the table
            List<String> values = jdbcTemplate.queryForList(
                "SELECT DISTINCT repeat_time FROM t_collection_script WHERE repeat_time IS NOT NULL", String.class);
            info.put("values", values);
            info.put("value_lengths", values.stream().map(String::length).toList());
        } catch (Exception e) {
            info.put("error", e.getMessage());
        }
        return Result.success(info);
    }

    @PostMapping("/fix-column")
    public Result<String> fixColumn() {
        try {
            jdbcTemplate.execute("ALTER TABLE t_collection_script MODIFY COLUMN repeat_time VARCHAR(20)");
            return Result.success("repeat_time 列已改为 VARCHAR(20)");
        } catch (Exception e) {
            return Result.error("修改失败: " + e.getMessage());
        }
    }
}