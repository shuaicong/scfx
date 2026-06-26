package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.entity.CollectorScriptVersion;
import com.scfx.entity.DataSource;
import com.scfx.service.CollectionScriptService;
import com.scfx.service.CollectorScriptVersionService;
import com.scfx.service.DataSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/datasource")
@RequiredArgsConstructor
public class DataSourceController {
    private final DataSourceService dataSourceService;
    private final CollectorScriptVersionService scriptVersionService;
    private final CollectionScriptService collectionScriptService;

    // 编码规则：以小写字母开头，只能包含小写字母、数字、单个连字符，不能以连字符结尾
    private static final Pattern CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9]*(-[a-z0-9]+)*$");

    private String validateCode(String code) {
        if (code == null || code.isEmpty()) {
            return "数据源编码不能为空";
        }
        if (!CODE_PATTERN.matcher(code).matches()) {
            return "数据源编码只能以小写字母开头，包含小写字母、数字，多个单词用单个连字符分隔（如 liangxin-yumi-morning）";
        }
        return null;
    }

    @GetMapping
    public Result<List<DataSource>> getAll() {
        return Result.success(dataSourceService.getAll());
    }

    @GetMapping("/{code}")
    public Result<DataSource> getByCode(@PathVariable String code) {
        DataSource ds = dataSourceService.getByCode(code);
        if (ds == null) {
            return Result.error("数据源不存在");
        }
        return Result.success(ds);
    }

    @PostMapping
    public Result<DataSource> create(@RequestBody DataSource dataSource) {
        String error = validateCode(dataSource.getCode());
        if (error != null) {
            return Result.error(error);
        }
        return Result.success(dataSourceService.create(dataSource));
    }

    @PutMapping("/{code}")
    public Result<DataSource> update(@PathVariable String code, @RequestBody DataSource dataSource) {
        try {
            return Result.success(dataSourceService.update(code, dataSource));
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/{code}")
    public Result<Void> delete(@PathVariable String code) {
        try {
            dataSourceService.delete(code);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/{code}/enable")
    public Result<Void> enable(@PathVariable String code) {
        dataSourceService.enable(code);
        return Result.success();
    }

    @PostMapping("/{code}/disable")
    public Result<Void> disable(@PathVariable String code) {
        dataSourceService.disable(code);
        return Result.success();
    }

    @PostMapping("/upload-collector")
    public Result<Map<String, Object>> uploadCollector(
            @RequestParam("file") MultipartFile file,
            @RequestParam("code") String code,
            @RequestParam(value = "operator", defaultValue = "admin") String operator) {

        try {
            CollectorScriptVersion version = scriptVersionService.uploadScript(
                code,
                file.getBytes(),
                file.getOriginalFilename(),
                operator
            );

            if (version == null) {
                return Result.sameContent();
            }

            return Result.success(Map.of(
                "code", version.getDatasourceCode(),
                "version", version.getVersion(),
                "md5", version.getFileMd5()
            ));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{code}/script")
    public Result<String> getScriptSource(
            @PathVariable String code,
            @RequestParam(value = "version", defaultValue = "0") int version) {

        try {
            String content = scriptVersionService.getScriptContent(code, version);
            return Result.success(content);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/{code}/exists")
    public Result<Map<String, Boolean>> checkScriptExists(@PathVariable String code) {
        boolean exists = scriptVersionService.scriptExists(code);
        return Result.success(Map.of("exists", exists));
    }

    @GetMapping("/{code}/versions")
    public Result<List<CollectorScriptVersion>> getVersions(@PathVariable String code) {
        return Result.success(scriptVersionService.getVersions(code));
    }

    /**
     * 获取数据源的启用任务
     * 用于 main.py 自动获取 task_id
     * GET /api/datasource/{code}/active-script
     */
    @GetMapping("/{code}/active-script")
    public Result<Map<String, Object>> getActiveScript(@PathVariable String code) {
        Map<String, Object> script = collectionScriptService.getActiveScriptBySource(code);
        if (script == null) {
            return Result.error("该数据源未找到启用中的采集任务");
        }
        return Result.success(script);
    }
}