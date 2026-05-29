# 数据源管理模块实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现数据源的完整管理功能，包括 CRUD、脚本上传、版本管理、查看脚本

**Architecture:** 后端 Spring Boot + MyBatis-Plus，前端 Vue3 + Element Plus，Python 采集器 SDK

**Tech Stack:** Java (Spring Boot, MyBatis-Plus), Vue3 (Element Plus, TypeScript), Python (Flask, watchdog)

---

## 一、文件结构映射

### 1.1 后端文件结构

```
backend/src/main/java/com/scfx/
├── entity/
│   └── DataSource.java              # 新增
├── mapper/
│   └── DataSourceMapper.java        # 新增
├── service/
│   └── DataSourceService.java       # 新增
├── controller/
│   └── DataSourceController.java    # 新增
```

### 1.2 前端文件结构

```
frontend/src/
├── api/
│   └── datasource.ts                # 新增
├── views/system/
│   └── DataSource.vue               # 新增
├── components/
│   └── SourceTag.vue                # 修改（改为动态获取）
└── router/
    └── index.ts                     # 修改（添加路由）
```

### 1.3 Python 文件结构

```
python-collector-sdk/collectorsdk/collectors/
├── __init__.py                      # 修改（添加 watchdog 监听）
```

---

## 二、数据库变更

### 2.1 新增表

```sql
-- 数据源表
CREATE TABLE t_data_source (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE COMMENT '数据源编码',
    name VARCHAR(100) NOT NULL COMMENT '显示名称',
    description VARCHAR(500) COMMENT '描述信息',
    logo_url VARCHAR(255) COMMENT 'logo URL',
    enabled TINYINT(1) DEFAULT 1 COMMENT '启用状态',
    sort_order INT DEFAULT 0 COMMENT '排序',
    config JSON COMMENT '配置信息（登录URL、认证方式等）',
    last_heartbeat TIMESTAMP COMMENT '最后心跳时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据源表';

-- 脚本版本表
CREATE TABLE t_script_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_code VARCHAR(50) NOT NULL COMMENT '数据源编码',
    version INT NOT NULL COMMENT '版本号',
    file_path VARCHAR(255) NOT NULL COMMENT '文件路径',
    file_md5 VARCHAR(32) NOT NULL COMMENT '文件MD5',
    file_size INT COMMENT '文件大小',
    is_current TINYINT(1) DEFAULT 0 COMMENT '是否当前版本',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) COMMENT '创建人',
    UNIQUE INDEX idx_datasource_current (datasource_code, is_current),
    INDEX idx_datasource_version (datasource_code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='脚本版本表';

-- 脚本操作日志表
CREATE TABLE t_script_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_code VARCHAR(50) NOT NULL COMMENT '数据源编码',
    operation_type VARCHAR(20) NOT NULL COMMENT '操作类型(UPLOAD/UPDATE/DELETE/ROLLBACK)',
    operator VARCHAR(100) COMMENT '操作人',
    operate_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    file_md5 VARCHAR(32) NOT NULL COMMENT '文件MD5',
    file_size INT COMMENT '文件大小',
    backup_path VARCHAR(255) COMMENT '备份路径',
    remark VARCHAR(500) COMMENT '备注',
    INDEX idx_datasource_code (datasource_code),
    INDEX idx_operate_time (operate_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='脚本操作日志表';
```

### 2.2 初始化数据

```sql
INSERT INTO t_data_source (code, name, description, config) VALUES
('liangxin', '粮信网', '中国粮食网玉米晨报', '{"loginUrl": "https://my.chinagrain.cn/jinnong/a/login", "authType": "cookie"}'),
('mysteel', '我的钢铁网', '我的钢铁网价格数据', '{"loginUrl": "https://login.mysteel.com.cn", "authType": "cookie"}'),
('chinagrain', '中华粮网', '中华粮网市场数据', '{"loginUrl": "https://www.chinagrain.cn/login", "authType": "cookie"}'),
('usda', 'USDA', '美国农业部数据', '{"authType": "api", "apiKey": "xxx"}'),
('market', '市场数据', '第三方市场数据', '{"authType": "api"}');
```

---

## 三、后端实现

### Task 1: DataSource 实体类

**Files:**
- Create: `backend/src/main/java/com/scfx/entity/DataSource.java`
- Test: `backend/src/test/java/com/scfx/entity/DataSourceTest.java`

- [ ] **Step 1: 创建 DataSource 实体类**

```java
package com.scfx.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("t_data_source")
public class DataSource {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private String code;                    // 数据源编码: liangxin, mysteel, chinagrain

    private String name;                    // 显示名称: 粮信网, 我的钢铁网

    private String description;            // 描述信息

    private String logoUrl;                // logo URL

    private Integer enabled;               // 启用状态: 1=启用, 0=禁用

    private Integer sortOrder;             // 排序

    private String config;                 // 配置信息（JSON格式）

    private LocalDateTime lastHeartbeat;   // 最后心跳时间

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
```

- [ ] **Step 2: 创建测试类**

```java
package com.scfx.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DataSourceTest {
    @Test
    void testDataSourceCreation() {
        DataSource ds = new DataSource();
        ds.setCode("liangxin");
        ds.setName("粮信网");
        ds.setDescription("中国粮食网玉米晨报");
        ds.setEnabled(1);
        ds.setSortOrder(1);

        assertEquals("liangxin", ds.getCode());
        assertEquals("粮信网", ds.getName());
        assertEquals(1, ds.getEnabled());
    }

    @Test
    void testConfigJson() {
        DataSource ds = new DataSource();
        ds.setConfig("{\"loginUrl\": \"https://example.com/login\", \"authType\": \"cookie\"}");

        assertTrue(ds.getConfig().contains("loginUrl"));
        assertTrue(ds.getConfig().contains("cookie"));
    }
}
```

- [ ] **Step 3: 验证测试通过**

Run: `mvn test -Dtest=DataSourceTest -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/java/com/scfx/entity/DataSource.java
git add backend/src/test/java/com/scfx/entity/DataSourceTest.java
git commit -m "feat: add DataSource entity class"
```

---

### Task 2: DataSourceMapper

**Files:**
- Create: `backend/src/main/java/com/scfx/mapper/DataSourceMapper.java`
- Test: `backend/src/test/java/com/scfx/mapper/DataSourceMapperTest.java`

- [ ] **Step 1: 创建 DataSourceMapper 接口**

```java
package com.scfx.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scfx.entity.DataSource;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DataSourceMapper extends BaseMapper<DataSource> {

    @Select("SELECT * FROM t_data_source WHERE code = #{code}")
    DataSource findByCode(@Param("code") String code);

    @Select("SELECT * FROM t_data_source WHERE enabled = 1 ORDER BY sort_order ASC")
    List<DataSource> findAllEnabled();

    @Select("SELECT * FROM t_data_source ORDER BY sort_order ASC")
    List<DataSource> findAll();

    @Update("UPDATE t_data_source SET last_heartbeat = NOW() WHERE code = #{code}")
    void updateHeartbeat(@Param("code") String code);

    @Update("UPDATE t_data_source SET enabled = #{enabled} WHERE id = #{id}")
    int updateEnabled(@Param("id") Long id, @Param("enabled") Integer enabled);
}
```

- [ ] **Step 2: 创建测试类**

```java
package com.scfx.mapper;

import com.scfx.entity.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DataSourceMapperTest {
    @Autowired
    private DataSourceMapper mapper;

    @Test
    void testFindAll() {
        List<DataSource> list = mapper.findAll();
        assertNotNull(list);
    }

    @Test
    void testFindAllEnabled() {
        List<DataSource> enabled = mapper.findAllEnabled();
        assertNotNull(enabled);
        for (DataSource ds : enabled) {
            assertEquals(1, ds.getEnabled());
        }
    }

    @Test
    void testFindByCode() {
        DataSource ds = mapper.findByCode("liangxin");
        if (ds != null) {
            assertEquals("liangxin", ds.getCode());
        }
    }
}
```

- [ ] **Step 3: 验证测试通过**

Run: `mvn test -Dtest=DataSourceMapperTest -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/java/com/scfx/mapper/DataSourceMapper.java
git add backend/src/test/java/com/scfx/mapper/DataSourceMapperTest.java
git commit -m "feat: add DataSourceMapper with CRUD operations"
```

---

### Task 3: DataSourceService

**Files:**
- Create: `backend/src/main/java/com/scfx/service/DataSourceService.java`
- Create: `backend/src/main/java/com/scfx/service/impl/DataSourceServiceImpl.java`
- Test: `backend/src/test/java/com/scfx/service/DataSourceServiceTest.java`

- [ ] **Step 1: 创建 DataSourceService 接口**

```java
package com.scfx.service;

import com.scfx.entity.DataSource;
import java.util.List;

public interface DataSourceService {
    List<DataSource> getAll();

    List<DataSource> getAllEnabled();

    DataSource getByCode(String code);

    DataSource create(DataSource dataSource);

    DataSource update(String code, DataSource dataSource);

    void delete(String code);

    void enable(String code);

    void disable(String code);

    void updateHeartbeat(String code);
}
```

- [ ] **Step 2: 创建 DataSourceServiceImpl 实现类**

```java
package com.scfx.service.impl;

import com.scfx.entity.DataSource;
import com.scfx.mapper.DataSourceMapper;
import com.scfx.service.DataSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DataSourceServiceImpl implements DataSourceService {
    private final DataSourceMapper mapper;

    @Override
    public List<DataSource> getAll() {
        return mapper.findAll();
    }

    @Override
    public List<DataSource> getAllEnabled() {
        return mapper.findAllEnabled();
    }

    @Override
    public DataSource getByCode(String code) {
        return mapper.findByCode(code);
    }

    @Override
    @Transactional
    public DataSource create(DataSource dataSource) {
        mapper.insert(dataSource);
        return dataSource;
    }

    @Override
    @Transactional
    public DataSource update(String code, DataSource dataSource) {
        DataSource existing = mapper.findByCode(code);
        if (existing == null) {
            throw new RuntimeException("数据源不存在: " + code);
        }
        existing.setName(dataSource.getName());
        existing.setDescription(dataSource.getDescription());
        existing.setLogoUrl(dataSource.getLogoUrl());
        existing.setEnabled(dataSource.getEnabled());
        existing.setSortOrder(dataSource.getSortOrder());
        existing.setConfig(dataSource.getConfig());
        mapper.updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public void delete(String code) {
        mapper.delete(wrapper -> wrapper.eq("code", code));
    }

    @Override
    @Transactional
    public void enable(String code) {
        mapper.updateEnabled(mapper.findByCode(code).getId(), 1);
    }

    @Override
    @Transactional
    public void disable(String code) {
        mapper.updateEnabled(mapper.findByCode(code).getId(), 0);
    }

    @Override
    public void updateHeartbeat(String code) {
        mapper.updateHeartbeat(code);
    }
}
```

- [ ] **Step 3: 创建测试类**

```java
package com.scfx.service;

import com.scfx.entity.DataSource;
import com.scfx.service.impl.DataSourceServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class DataSourceServiceTest {
    @Autowired
    private DataSourceServiceImpl service;

    @Test
    void testGetAll() {
        List<DataSource> list = service.getAll();
        assertNotNull(list);
    }

    @Test
    void testGetAllEnabled() {
        List<DataSource> enabled = service.getAllEnabled();
        assertNotNull(enabled);
    }

    @Test
    void testGetByCode() {
        DataSource ds = service.getByCode("liangxin");
        if (ds != null) {
            assertEquals("liangxin", ds.getCode());
        }
    }
}
```

- [ ] **Step 4: 验证测试通过**

Run: `mvn test -Dtest=DataSourceServiceTest -q`
Expected: BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add backend/src/main/java/com/scfx/service/DataSourceService.java
git add backend/src/main/java/com/scfx/service/impl/DataSourceServiceImpl.java
git add backend/src/test/java/com/scfx/service/DataSourceServiceTest.java
git commit -m "feat: add DataSourceService with business logic"
```

---

### Task 4: DataSourceController

**Files:**
- Create: `backend/src/main/java/com/scfx/controller/DataSourceController.java`
- Modify: `backend/src/main/java/com/scfx/controller/CollectorManageController.java`（添加心跳接口）

- [ ] **Step 1: 创建 DataSourceController**

```java
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
```

- [ ] **Step 2: 提交 DataSourceController**

```bash
git add backend/src/main/java/com/scfx/controller/DataSourceController.java
git commit -m "feat: add DataSourceController with CRUD endpoints"
```

---

### Task 5: 脚本上传功能

**Files:**
- Modify: `backend/src/main/java/com/scfx/controller/DataSourceController.java`
- Create: `backend/src/main/java/com/scfx/service/ScriptVersionService.java`

- [ ] **Step 1: 创建 ScriptVersionService**

```java
package com.scfx.service;

import com.scfx.entity.ScriptVersion;
import java.util.List;

public interface ScriptVersionService {
    List<ScriptVersion> getVersions(String datasourceCode);

    ScriptVersion getCurrentVersion(String datasourceCode);

    ScriptVersion createVersion(String datasourceCode, String filePath, String fileMd5, int fileSize, String operator);

    ScriptVersion uploadScript(String datasourceCode, byte[] content, String originalFilename, String operator);

    String getScriptContent(String datasourceCode, int version);

    boolean scriptExists(String datasourceCode);

    void rollback(String datasourceCode, int version);
}
```

- [ ] **Step 2: 创建 ScriptVersionServiceImpl**

```java
package com.scfx.service.impl;

import com.scfx.entity.ScriptVersion;
import com.scfx.mapper.ScriptVersionMapper;
import com.scfx.service.ScriptVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScriptVersionServiceImpl implements ScriptVersionService {
    private final ScriptVersionMapper scriptVersionMapper;

    @Value("${collector.script.path:/python-collector-sdk/collectorsdk/collectors}")
    private String scriptBasePath;

    @Override
    public List<ScriptVersion> getVersions(String datasourceCode) {
        return scriptVersionMapper.findByDatasourceCode(datasourceCode);
    }

    @Override
    public ScriptVersion getCurrentVersion(String datasourceCode) {
        return scriptVersionMapper.findCurrentByDatasourceCode(datasourceCode);
    }

    @Override
    @Transactional
    public ScriptVersion createVersion(String datasourceCode, String filePath, String fileMd5, int fileSize, String operator) {
        // 标记旧版本为非当前
        ScriptVersion old = scriptVersionMapper.findCurrentByDatasourceCode(datasourceCode);
        if (old != null) {
            old.setIsCurrent(0);
            scriptVersionMapper.updateById(old);
        }

        // 获取最新版本号
        Integer maxVersion = scriptVersionMapper.findMaxVersion(datasourceCode);
        int newVersion = (maxVersion == null) ? 1 : maxVersion + 1;

        // 创建新版本
        ScriptVersion version = new ScriptVersion();
        version.setDatasourceCode(datasourceCode);
        version.setVersion(newVersion);
        version.setFilePath(filePath);
        version.setFileMd5(fileMd5);
        version.setFileSize(fileSize);
        version.setIsCurrent(1);
        version.setCreatedBy(operator);
        scriptVersionMapper.insert(version);

        return version;
    }

    @Override
    @Transactional
    public ScriptVersion uploadScript(String datasourceCode, byte[] content, String originalFilename, String operator) {
        // 验证文件类型
        if (!originalFilename.endsWith(".py")) {
            throw new RuntimeException("只能上传 .py 文件");
        }

        // 验证文件大小 (100KB)
        if (content.length > 100 * 1024) {
            throw new RuntimeException("文件不能超过 100KB");
        }

        // 计算 MD5
        String md5 = calculateMd5(content);

        // 检查是否有相同 MD5 的最新版本
        ScriptVersion current = scriptVersionMapper.findCurrentByDatasourceCode(datasourceCode);
        if (current != null && md5.equals(current.getFileMd5())) {
            throw new RuntimeException("文件内容与最新版本相同，无需重复上传");
        }

        // 保存文件
        String filePath = scriptBasePath + "/" + datasourceCode + ".py";
        Path path = Path.of(filePath);

        // 备份旧文件（如果存在）
        if (Files.exists(path)) {
            String backupPath = filePath + ".bak." + System.currentTimeMillis();
            Files.copy(path, Path.of(backupPath));
        }

        // 写入新文件
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(content);
        }

        // 创建版本记录
        return createVersion(datasourceCode, filePath, md5, content.length, operator);
    }

    @Override
    public String getScriptContent(String datasourceCode, int version) {
        String filePath;
        if (version > 0) {
            ScriptVersion v = scriptVersionMapper.findByDatasourceCodeAndVersion(datasourceCode, version);
            filePath = v != null ? v.getFilePath() : null;
        } else {
            ScriptVersion current = scriptVersionMapper.findCurrentByDatasourceCode(datasourceCode);
            filePath = current != null ? current.getFilePath() : null;
        }

        if (filePath == null) {
            throw new RuntimeException("脚本版本不存在");
        }

        return Files.readString(Path.of(filePath));
    }

    @Override
    public boolean scriptExists(String datasourceCode) {
        String filePath = scriptBasePath + "/" + datasourceCode + ".py";
        return Files.exists(Path.of(filePath));
    }

    @Override
    @Transactional
    public void rollback(String datasourceCode, int version) {
        ScriptVersion target = scriptVersionMapper.findByDatasourceCodeAndVersion(datasourceCode, version);
        if (target == null) {
            throw new RuntimeException("版本不存在");
        }

        // 备份当前版本
        ScriptVersion current = scriptVersionMapper.findCurrentByDatasourceCode(datasourceCode);
        if (current != null) {
            current.setIsCurrent(0);
            scriptVersionMapper.updateById(current);
        }

        // 复制目标版本文件到当前版本路径
        String currentPath = scriptBasePath + "/" + datasourceCode + ".py";
        Files.copy(Path.of(target.getFilePath()), Path.of(currentPath));

        // 创建新的版本记录
        createVersion(datasourceCode, currentPath, target.getFileMd5(), target.getFileSize(), "system");
    }

    private String calculateMd5(byte[] content) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5计算失败", e);
        }
    }
}
```

- [ ] **Step 3: 添加上传接口到 DataSourceController**

```java
// 在 DataSourceController 中添加以下接口

@PostMapping("/upload-collector")
public Result<Map<String, Object>> uploadCollector(
        @RequestParam("file") MultipartFile file,
        @RequestParam("code") String code,
        @RequestParam(value = "operator", defaultValue = "admin") String operator) {

    try {
        ScriptVersion version = scriptVersionService.uploadScript(
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
```

- [ ] **Step 4: 提交**

```bash
git add backend/src/main/java/com/scfx/service/ScriptVersionService.java
git add backend/src/main/java/com/scfx/service/impl/ScriptVersionServiceImpl.java
git add backend/src/main/java/com/scfx/controller/DataSourceController.java
git commit -m "feat: add script upload and version management"
```

---

### Task 6: 前端 API

**Files:**
- Create: `frontend/src/api/datasource.ts`

- [ ] **Step 1: 创建 datasource.ts API**

```typescript
import request from './index'

export interface DataSource {
  id?: number
  code: string
  name: string
  description?: string
  logoUrl?: string
  enabled: number
  sortOrder?: number
  config?: string
  lastHeartbeat?: string
  createdAt?: string
  updatedAt?: string
}

export interface ScriptVersion {
  id: number
  datasourceCode: string
  version: number
  filePath: string
  fileMd5: string
  fileSize: number
  isCurrent: number
  createdAt: string
  createdBy: string
}

export const datasourceApi = {
  list: () => request.get<DataSource[]>('/datasource'),

  getByCode: (code: string) => request.get<DataSource>(`/datasource/${code}`),

  create: (data: Partial<DataSource>) => request.post<DataSource>('/datasource', data),

  update: (code: string, data: Partial<DataSource>) =>
    request.put<DataSource>(`/datasource/${code}`, data),

  delete: (code: string) => request.delete(`/datasource/${code}`),

  enable: (code: string) => request.post(`/datasource/${code}/enable`),

  disable: (code: string) => request.post(`/datasource/${code}/disable`),

  uploadScript: (file: File, code: string, operator?: string) => {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('code', code)
    if (operator) formData.append('operator', operator)
    return request.post<{ code: string; version: number; md5: string }>(
      '/datasource/upload-collector',
      formData,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    )
  },

  getScriptContent: (code: string, version?: number) =>
    request.get<string>(`/datasource/${code}/script${version ? `?version=${version}` : ''}`),

  checkScriptExists: (code: string) =>
    request.get<{ exists: boolean }>(`/datasource/${code}/exists`),

  getVersions: (code: string) =>
    request.get<ScriptVersion[]>(`/datasource/${code}/versions`)
}
```

- [ ] **Step 2: 提交**

```bash
git add frontend/src/api/datasource.ts
git commit -m "feat: add datasource API module"
```

---

### Task 7: 数据源管理页面

**Files:**
- Create: `frontend/src/views/system/DataSource.vue`

- [ ] **Step 1: 创建 DataSource.vue 页面**

```vue
<template>
  <div class="datasource-container">
    <div class="header">
      <h2>数据源管理</h2>
      <el-button type="primary" @click="handleCreate">新增数据源</el-button>
    </div>

    <!-- 筛选区域 -->
    <div class="filter-area">
      <el-select v-model="filterEnabled" placeholder="状态筛选" clearable style="width: 120px">
        <el-option label="全部" value="" />
        <el-option label="启用" value="1" />
        <el-option label="禁用" value="0" />
      </el-select>
      <el-input v-model="searchKeyword" placeholder="搜索数据源" style="width: 200px" clearable />
    </div>

    <!-- 数据源列表 -->
    <el-table :data="filteredList" stripe style="width: 100%">
      <el-table-column prop="code" label="标识" width="120" />
      <el-table-column prop="name" label="名称" width="150" />
      <el-table-column prop="description" label="描述" min-width="200" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'info'">
            {{ row.enabled === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="认证方式" width="120">
        <template #default="{ row }">
          {{ getAuthType(row.config) }}
        </template>
      </el-table-column>
      <el-table-column label="脚本" width="120">
        <template #default="{ row }">
          <el-tag v-if="scriptExists[row.code]" type="success">已上传</el-tag>
          <el-tag v-else type="warning">未上传</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button size="small" @click="handleUpload(row)">上传脚本</el-button>
          <el-button size="small" @click="handleViewScript(row)" :disabled="!scriptExists[row.code]">
            查看脚本
          </el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="600px">
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item label="数据源标识" prop="code">
          <el-input v-model="form.code" :disabled="isEdit" />
          <span class="form-hint">建议使用英文小写，用于系统内部标识</span>
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="登录URL">
          <el-input v-model="form.loginUrl" placeholder="https://..." />
        </el-form-item>
        <el-form-item label="认证方式">
          <el-select v-model="form.authType" style="width: 100%">
            <el-option label="Cookie" value="cookie" />
            <el-option label="Token" value="token" />
            <el-option label="Basic" value="basic" />
            <el-option label="API" value="api" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 上传脚本弹窗 -->
    <el-dialog v-model="uploadDialogVisible" title="上传采集脚本" width="500px">
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        :accept="'.py'"
        :before-upload="handleBeforeUpload"
        :on-change="handleFileChange"
        :on-success="handleUploadSuccess"
        :on-error="handleUploadError"
        drag
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">只能上传 .py 文件，且不超过 100KB</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUploadSubmit">上传</el-button>
      </template>
    </el-dialog>

    <!-- 查看脚本弹窗 -->
    <el-dialog v-model="scriptDialogVisible" title="查看采集脚本" width="80%">
      <el-input v-model="scriptContent" type="textarea" readonly :rows="20" class="code-textarea" />
      <template #footer>
        <el-button @click="scriptDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { datasourceApi, type DataSource } from '@/api/datasource'

const list = ref<DataSource[]>([])
const filterEnabled = ref('')
const searchKeyword = ref('')
const scriptExists = ref<Record<string, boolean>>({})

const dialogVisible = ref(false)
const dialogTitle = ref('新增数据源')
const isEdit = ref(false)
const formRef = ref()
const form = ref({
  code: '',
  name: '',
  description: '',
  loginUrl: '',
  authType: 'cookie',
  enabled: 1
})

const uploadDialogVisible = ref(false)
const uploadFile = ref<File | null>(null)
const uploadCode = ref('')
const uploadRef = ref()

const scriptDialogVisible = ref(false)
const scriptContent = ref('')

const filteredList = computed(() => {
  return list.value.filter(item => {
    const matchEnabled = !filterEnabled.value || String(item.enabled) === filterEnabled.value
    const matchSearch = !searchKeyword.value ||
      item.code.includes(searchKeyword.value) ||
      item.name.includes(searchKeyword.value)
    return matchEnabled && matchSearch
  })
})

const formRules = {
  code: [{ required: true, message: '请输入数据源标识', trigger: 'blur' }],
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

onMounted(async () => {
  await loadList()
})

async function loadList() {
  try {
    const res = await datasourceApi.list()
    list.value = res.data
    // 检查每个数据源的脚本是否存在
    for (const ds of res.data) {
      try {
        const existsRes = await datasourceApi.checkScriptExists(ds.code)
        scriptExists.value[ds.code] = existsRes.data.exists
      } catch {
        scriptExists.value[ds.code] = false
      }
    }
  } catch (e) {
    ElMessage.error('加载数据源列表失败')
  }
}

function getAuthType(config?: string): string {
  if (!config) return '-'
  try {
    const obj = JSON.parse(config)
    return obj.authType || '-'
  } catch {
    return '-'
  }
}

function handleCreate() {
  form.value = { code: '', name: '', description: '', loginUrl: '', authType: 'cookie', enabled: 1 }
  isEdit.value = false
  dialogTitle.value = '新增数据源'
  dialogVisible.value = true
}

function handleEdit(row: DataSource) {
  form.value = {
    code: row.code,
    name: row.name,
    description: row.description || '',
    loginUrl: getLoginUrl(row.config),
    authType: getAuthType(row.config) || 'cookie',
    enabled: row.enabled
  }
  isEdit.value = true
  dialogTitle.value = '编辑数据源'
  dialogVisible.value = true
}

function getLoginUrl(config?: string): string {
  if (!config) return ''
  try {
    const obj = JSON.parse(config)
    return obj.loginUrl || ''
  } catch {
    return ''
  }
}

function buildConfig(): string {
  return JSON.stringify({
    loginUrl: form.value.loginUrl,
    authType: form.value.authType
  })
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return

    const data = {
      code: form.value.code,
      name: form.value.name,
      description: form.value.description,
      config: buildConfig(),
      enabled: form.value.enabled
    }

    try {
      if (isEdit.value) {
        await datasourceApi.update(form.value.code, data)
        ElMessage.success('更新成功')
      } else {
        await datasourceApi.create(data)
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      await loadList()
    } catch (e: any) {
      ElMessage.error(e.message || '操作失败')
    }
  })
}

function handleUpload(row: DataSource) {
  uploadCode.value = row.code
  uploadFile.value = null
  uploadDialogVisible.value = true
}

function handleBeforeUpload(file: File) {
  if (!file.name.endsWith('.py')) {
    ElMessage.error('只能上传 .py 文件')
    return false
  }
  if (file.size > 100 * 1024) {
    ElMessage.error('文件不能超过 100KB')
    return false
  }
  return true
}

function handleFileChange(file: any) {
  uploadFile.value = file.raw
}

async function handleUploadSubmit() {
  if (!uploadFile.value) {
    ElMessage.warning('请选择文件')
    return
  }

  try {
    await datasourceApi.uploadScript(uploadFile.value, uploadCode.value)
    ElMessage.success('上传成功')
    uploadDialogVisible.value = false
    await loadList()
  } catch (e: any) {
    ElMessage.error(e.message || '上传失败')
  }
}

function handleUploadSuccess() {
  ElMessage.success('上传成功')
  uploadDialogVisible.value = false
  loadList()
}

function handleUploadError(e: any) {
  ElMessage.error(e.message || '上传失败')
}

async function handleViewScript(row: DataSource) {
  try {
    const res = await datasourceApi.getScriptContent(row.code)
    scriptContent.value = res.data
    scriptDialogVisible.value = true
  } catch (e: any) {
    ElMessage.error(e.message || '获取脚本内容失败')
  }
}

async function handleDelete(row: DataSource) {
  await ElMessageBox.confirm(`确定删除数据源 ${row.name}？`, '提示', { type: 'warning' })
  try {
    await datasourceApi.delete(row.code)
    ElMessage.success('删除成功')
    await loadList()
  } catch (e: any) {
    ElMessage.error(e.message || '删除失败')
  }
}
</script>

<style scoped>
.datasource-container {
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header h2 {
  margin: 0;
}

.filter-area {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.form-hint {
  font-size: 12px;
  color: #999;
  margin-left: 8px;
}

.code-textarea textarea {
  font-family: 'Courier New', monospace;
  font-size: 13px;
}
</style>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/src/views/system/DataSource.vue
git commit -m "feat: add DataSource management page"
```

---

### Task 8: SourceTag 组件改造

**Files:**
- Modify: `frontend/src/components/SourceTag.vue`

- [ ] **Step 1: 修改 SourceTag.vue 为动态获取**

```vue
<template>
  <span class="source-tag">{{ label }}</span>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { datasourceApi } from '@/api/datasource'

const props = defineProps<{ source?: string }>()

const datasourceMap = ref<Record<string, string>>({})

const label = computed(() => {
  if (!props.source) return '-'
  return datasourceMap.value[props.source] || props.source
})

onMounted(async () => {
  try {
    const res = await datasourceApi.list()
    const map: Record<string, string> = {}
    for (const ds of res.data) {
      map[ds.code] = ds.name
    }
    datasourceMap.value = map
  } catch {
    // 使用默认映射
    datasourceMap.value = {
      liangxin: '粮信网',
      mysteel: '我的钢铁网',
      chinagrain: '中华粮网',
      usda: 'USDA',
      market: '市场数据'
    }
  }
})
</script>

<style scoped>
.source-tag {
  display: inline-block;
  padding: 2px 8px;
  background: #f0f0f0;
  border-radius: 4px;
  font-size: 12px;
}
</style>
```

- [ ] **Step 2: 提交**

```bash
git add frontend/src/components/SourceTag.vue
git commit -m "feat: update SourceTag to fetch from API dynamically"
```

---

### Task 9: 路由配置

**Files:**
- Modify: `frontend/src/router/index.ts`

- [ ] **Step 1: 添加数据源管理路由**

在 routes 数组中添加：

```typescript
{
  path: '/system',
  name: 'System',
  component: () => import('../views/layout/Layout.vue'),
  meta: { title: '系统管理' },
  children: [
    // ... existing routes
    {
      path: 'datasource',
      name: 'DataSource',
      component: () => import('../views/system/DataSource.vue'),
      meta: { title: '数据源管理' }
    }
  ]
}
```

- [ ] **Step 2: 提交**

```bash
git add frontend/src/router/index.ts
git commit -m "feat: add datasource route to router"
```

---

### Task 10: Python watchdog 监听

**Files:**
- Modify: `python-collector-sdk/collectorsdk/collectors/__init__.py`

- [ ] **Step 1: 添加 watchdog 监听实现**

```python
"""采集器子包 - 包含采集器注册表和文件监听"""
import os
import sys
import importlib
import logging
from pathlib import Path

logger = logging.getLogger(__name__)

# 尝试导入 watchdog（可选依赖）
try:
    from watchdog.observers import Observer
    from watchdog.events import FileSystemEventHandler
    WATCHDOG_AVAILABLE = True
except ImportError:
    WATCHDOG_AVAILABLE = False
    logger.warning("watchdog not available, file watching disabled")

from ..base import BaseCollector

# 采集器注册表
COLLECTORS = {}


def discover_collectors():
    """自动发现所有采集器"""
    collectors = {}

    collectors_dir = Path(__file__).parent
    for filename in os.listdir(collectors_dir):
        if filename.endswith('.py') and filename not in ('__init__.py', 'base.py', 'knowledge_api.py'):
            module_name = filename[:-3]
            try:
                module = importlib.import_module(f'collectorsdk.collectors.{module_name}')

                for attr_name in dir(module):
                    cls = getattr(module, attr_name)
                    if isinstance(cls, type) and issubclass(cls, BaseCollector) and cls is not BaseCollector:
                        if hasattr(cls, 'META') and 'code' in cls.META:
                            code = cls.META['code']
                            collectors[code] = cls
                            logger.info(f"Discovered collector: {code}")

            except Exception as e:
                logger.error(f"Failed to load collector {module_name}: {e}")

    return collectors


def reload_collector(module_name: str):
    """重新加载单个采集器模块"""
    global COLLECTORS

    full_name = f'collectorsdk.collectors.{module_name}'
    if full_name in sys.modules:
        importlib.reload(sys.modules[full_name])

    # 重新发现采集器
    COLLECTORS = discover_collectors()
    logger.info(f"Collector reloaded: {module_name}, total: {len(COLLECTORS)}")


class CollectorFileHandler(FileSystemEventHandler if WATCHDOG_AVAILABLE else object):
    """监听 collectors 目录变化"""

    def __init__(self, callback):
        self.callback = callback

    def on_created(self, event):
        if event.src_path.endswith('.py') and not event.src_path.endswith('__init__.py'):
            module_name = os.path.basename(event.src_path)[:-3]
            logger.info(f"Collector file created: {module_name}")
            self.callback(module_name, "created")

    def on_modified(self, event):
        if event.src_path.endswith('.py') and not event.src_path.endswith('__init__.py'):
            module_name = os.path.basename(event.src_path)[:-3]
            logger.info(f"Collector file modified: {module_name}")
            self.callback(module_name, "modified")


def start_file_watcher():
    """启动文件监听（仅在 watchdog 可用时）"""
    if not WATCHDOG_AVAILABLE:
        logger.info("Watchdog not available, skipping file watcher")
        return

    collectors_dir = Path(__file__).parent

    def on_change(module_name: str, event_type: str):
        logger.info(f"File change detected: {module_name} ({event_type})")
        reload_collector(module_name)

    handler = CollectorFileHandler(on_change)
    observer = Observer()
    observer.schedule(handler, str(collectors_dir), recursive=False)
    observer.start()
    logger.info("Collector file watcher started")


# 初始化时发现所有采集器
COLLECTORS = discover_collectors()

__all__ = ["BaseCollector", "COLLECTORS", "start_file_watcher", "reload_collector"]
```

- [ ] **Step 2: 提交**

```bash
git add python-collector-sdk/collectorsdk/collectors/__init__.py
git commit -m "feat: add watchdog file watcher for hot reload"
```

---

## 四、优先级

| Task | 名称 | 优先级 |
|------|------|--------|
| Task 1 | DataSource 实体类 | P0 |
| Task 2 | DataSourceMapper | P0 |
| Task 3 | DataSourceService | P0 |
| Task 4 | DataSourceController | P0 |
| Task 5 | 脚本上传功能 | P0 |
| Task 6 | 前端 API | P0 |
| Task 7 | 数据源管理页面 | P0 |
| Task 8 | SourceTag 组件改造 | P1 |
| Task 9 | 路由配置 | P0 |
| Task 10 | Python watchdog 监听 | P1 |

---

## 五、验证方案

1. **后端接口测试**
   - CRUD 接口正常返回
   - 脚本上传成功
   - 脚本查看正常

2. **前端页面测试**
   - 数据源列表正确显示
   - 新增/编辑功能正常
   - 脚本上传功能正常
   - 查看脚本功能正常

3. **集成测试**
   - SourceTag 组件动态获取数据源名称
   - 路由跳转正常