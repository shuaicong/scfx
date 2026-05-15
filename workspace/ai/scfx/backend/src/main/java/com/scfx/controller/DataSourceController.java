package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.entity.CollectorScriptVersion;
import com.scfx.entity.DataSource;
import com.scfx.service.CollectorScriptVersionService;
import com.scfx.service.DataSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/datasource")
@RequiredArgsConstructor
public class DataSourceController {
    private final DataSourceService dataSourceService;
    private final CollectorScriptVersionService scriptVersionService;

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
        return Result.success(dataSourceService.create(dataSource));
    }

    @PutMapping("/{code}")
    public Result<DataSource> update(@PathVariable String code, @RequestBody DataSource dataSource) {
        return Result.success(dataSourceService.update(code, dataSource));
    }

    @DeleteMapping("/{code}")
    public Result<Void> delete(@PathVariable String code) {
        dataSourceService.delete(code);
        return Result.success();
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
}