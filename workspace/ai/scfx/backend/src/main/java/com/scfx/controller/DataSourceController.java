package com.scfx.controller;

import com.scfx.common.Result;
import com.scfx.entity.DataSource;
import com.scfx.service.DataSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/datasource")
@RequiredArgsConstructor
public class DataSourceController {
    private final DataSourceService dataSourceService;

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
}