package com.scfx.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scfx.entity.CollectionScript;
import com.scfx.entity.TaskExecution;
import com.scfx.mapper.CollectionScriptMapper;
import com.scfx.mapper.DataSourceMapper;
import com.scfx.mapper.TaskExecutionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 采集器代理服务
 * 负责轮询待执行的采集任务，并调用 Python SDK 实际执行脚本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectorAgentService {

    private final TaskExecutionMapper executionMapper;
    private final CollectionScriptMapper scriptMapper;
    private final TaskExecutionService executionService;
    private final DataSourceMapper dataSourceMapper;
    @Autowired
    private DataSource dataSource;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${collector.sdk-path:${user.home}/workspace/ai/scfx/python-collector-sdk}")
    private String collectorSdkPath;

    @Value("${collector.api.base:http://localhost:8080/api}")
    private String apiBase;

    private final ExecutorService executor = Executors.newFixedThreadPool(3);

    /**
     * 轮询 pending 状态的执行记录，启动 Python 采集器
     * 每 5 秒检查一次
     */
    @Scheduled(fixedDelay = 5000)
    public void pollPendingExecutions() {
        LambdaQueryWrapper<TaskExecution> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TaskExecution::getStatus, "pending")
               .orderByAsc(TaskExecution::getCreatedAt);
        List<TaskExecution> pendingList = executionMapper.selectList(wrapper);

        for (TaskExecution execution : pendingList) {
            processExecution(execution);
        }
    }

    /**
     * 兜底清理超时的运行中执行（每60秒检查一次）
     * 防止 process.waitFor 超时未能生效的极端情况
     */
    @Scheduled(fixedDelay = 60000)
    public void cleanupTimedOutExecutions() {
        int count = executionService.checkAndTimeoutRunningExecutions(
            TaskExecutionService.DEFAULT_TIMEOUT_MINUTES);
        if (count > 0) {
            log.warn("兜底清理: 已终止 {} 个超时执行", count);
        }
    }

    /**
     * 处理单个执行记录
     */
    private void processExecution(TaskExecution execution) {
        Long scriptId = execution.getScriptId();
        CollectionScript script = scriptMapper.selectById(scriptId);

        if (script == null) {
            log.warn("执行记录 {} 对应的脚本不存在, scriptId={}", execution.getExecutionId(), scriptId);
            executionService.completeExecution(execution.getExecutionId(), "failed", 0, "脚本不存在");
            return;
        }

        // 更新状态为 running
        execution.setStatus("running");
        execution.setStartTime(LocalDateTime.now());
        executionMapper.updateById(execution);

        // 异步执行 Python 脚本
        executor.submit(() -> executePythonCollector(execution, script));
    }

    /**
     * 执行 Python 采集器脚本
     */
    private void executePythonCollector(TaskExecution execution, CollectionScript script) {
        String executionId = execution.getExecutionId();

        try {
            log.info("开始执行采集器: executionId={}, scriptId={}", executionId, script.getId());

            // 获取数据源名称（如 liangxin, mysteel, chinagrain）
            String datasourceName = script.getSource();
            if (datasourceName == null || datasourceName.isEmpty()) {
                throw new RuntimeException("脚本 " + script.getId() + " 没有关联数据源");
            }

            // 从脚本名称推断报告类型（晨报/日报），通过环境变量传递给采集器
            String reportType = "morning";
            if (script.getScriptName() != null && script.getScriptName().contains("日报")) {
                reportType = "evening";
            }

            // 数据源代码归一化（用于环境变量命名）
            String datasourceCode = datasourceName.toUpperCase().replaceAll("[^a-zA-Z0-9]+", "_")
                .replaceAll("^_|_$", "");

            // 构建命令 - 使用 main.py run <code> 方式运行
            ProcessBuilder pb = new ProcessBuilder(
                "python3", "main.py",
                "--api-base", apiBase,
                "run", datasourceName,
                "--execution-id", executionId,
                "--task-id", String.valueOf(script.getId())
            );
            // 设置工作目录为 SDK 根目录
            pb.directory(new java.io.File(collectorSdkPath));
            // 通过环境变量传递报告类型
            pb.environment().put(datasourceCode + "_REPORT_TYPE", reportType);

            // 读取数据源配置（JSON），注入采集器特有参数
            com.scfx.entity.DataSource ds = dataSourceMapper.findByCode(datasourceName);
            String dsConfig = (ds != null) ? ds.getConfig() : null;
            if (dsConfig != null && !dsConfig.isEmpty()) {
                try {
                    Map<String, Object> configMap = objectMapper.readValue(dsConfig,
                        new TypeReference<Map<String, Object>>() {});
                    for (Map.Entry<String, Object> entry : configMap.entrySet()) {
                        if (entry.getValue() != null) {
                            pb.environment().put(
                                datasourceCode + "_" + entry.getKey().toUpperCase(),
                                entry.getValue().toString());
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析数据源配置失败: datasource={}", datasourceName, e);
                }
            }

            pb.redirectErrorStream(true);

            log.info("执行命令: {} {}", pb.command());

            Process process = pb.start();

            // 读取输出 - 使用 UTF-8 编码
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[collector-{}] {}", executionId, line);
                    // 上报日志到后端
                    executionService.addLog(executionId, script.getId(), "INFO", line);
                }
            }

            boolean timedOut = !process.waitFor(TaskExecutionService.DEFAULT_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (timedOut) {
                process.destroyForcibly();
                log.warn("采集器执行超时: executionId={}, timeout={}分钟", executionId, TaskExecutionService.DEFAULT_TIMEOUT_MINUTES);
                executionService.reportError(executionId,
                    "执行超时（已超过 " + TaskExecutionService.DEFAULT_TIMEOUT_MINUTES + " 分钟）");
                return;
            }

            int exitCode = process.exitValue();
            log.info("采集器执行完成: executionId={}, exitCode={}", executionId, exitCode);

            // 检查是否已被 SDK 上报完成（run() 中已调用 report_complete）
            TaskExecution completedExec = executionMapper.selectById(execution.getId());
            boolean alreadyCompleted = "success".equals(completedExec.getStatus())
                || "failed".equals(completedExec.getStatus())
                || "cancelled".equals(completedExec.getStatus());

            if (!alreadyCompleted) {
                if (exitCode == 0) {
                    executionService.completeExecution(executionId, "success", 0);
                } else {
                    executionService.reportError(executionId, "Exit code: " + exitCode);
                }
            }

        } catch (Exception e) {
            log.error("执行采集器失败: executionId={}", executionId, e);
            executionService.addLog(executionId, script.getId(), "ERROR", "执行失败: " + e.getMessage());
            executionService.reportError(executionId, e.getMessage());
        }
    }

    /**
     * 根据数据源获取采集器脚本路径
     * 从 t_collector_script_version 表查询当前版本的脚本路径
     */
    private String getCollectorScriptPath(String datasourceCode) {
        if (datasourceCode == null || datasourceCode.isEmpty()) {
            return null;
        }

        try {
            String sql = "SELECT file_path FROM t_collector_script_version WHERE datasource_code = ? AND is_current = 1 LIMIT 1";
            try (java.sql.Connection conn = dataSource.getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, datasourceCode);
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("file_path");
                    }
                }
            }
        } catch (Exception e) {
            log.error("查询采集器脚本路径失败: datasourceCode={}", datasourceCode, e);
        }
        return null;
    }

    /**
     * 手动触发执行（供其他服务调用）
     */
    public void triggerExecution(Long scriptId) {
        TaskExecution execution = executionService.createExecution(scriptId, "manual");
        CollectionScript script = scriptMapper.selectById(scriptId);
        if (script != null) {
            processExecution(execution);
        }
    }

    /**
     * 从文件路径获取模块名
     * 例如: /path/to/collectorsdk/collectors/liangxin.py -> collectorsdk.collectors.liangxin
     */
    private String getModuleNameFromPath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        // 移除 .py 后缀
        String path = filePath.replace(".py", "");
        // 提取相对于 collectorSdkPath 的部分
        String sdkPath = collectorSdkPath.replace("\\", "/");
        if (path.startsWith(sdkPath)) {
            path = path.substring(sdkPath.length());
        }
        // 移除开头的 /
        path = path.replaceFirst("^/+", "");
        // 将 / 替换为 .
        return path.replace("/", ".");
    }

    /**
     * 从文件路径获取数据源名称
     * 例如: /path/to/collectorsdk/collectors/liangxin.py -> liangxin
     */
    private String getDatasourceNameFromPath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }
        // 获取文件名（不含路径和扩展名）
        String fileName = new java.io.File(filePath).getName().replace(".py", "");
        return fileName;
    }
}