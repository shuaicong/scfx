# 数据源管理模块设计

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立统一的数据源管理机制，解决当前数据源标识不一致、无法动态维护的问题

**Architecture:** 数据源作为独立实体管理，关联采集器配置、分类映射、脚本管理

**Tech Stack:** Spring Boot + Vue + MySQL

---

## 一、现状问题

### 1.1 数据源标识不一致

| 位置 | 数据源标识 | 数量 |
|------|-----------|------|
| **SourceTag.vue** | liangxin, mysteel, chinagrain, usda, market | 5 个 |
| **CollectorController** | liangxinwang, mysteel, china_grain | 3 个 |
| **前端其他位置** | 不统一 | - |

**问题：**
- 前端用 `liangxin`，后端用 `liangxinwang`
- 前端用 `chinagrain`，后端用 `china_grain`
- 没有统一的配置中心

### 1.2 硬编码问题

- 数据源列表硬编码在组件中
- 新增数据源需要修改代码
- 无法通过界面管理

### 1.3 重复存储

- `KnowledgeBase` 表中存储了 `sourceType`、`collectionSource`、`sourceName` 三个相关字段
- 数据冗余，不一致风险

---

## 二、设计目标

1. **统一数据源标识** - 解决命名不一致问题
2. **可配置管理** - 支持界面化管理数据源
3. **关联采集器** - 数据源关联登录URL、认证方式等
4. **支持分类映射** - 数据源作为分类映射的输入维度

---

## 三、数据模型

### 3.1 DataSource 实体

```sql
CREATE TABLE t_data_source (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,     -- 数据源编码: liangxin, mysteel, chinagrain
    name VARCHAR(100) NOT NULL,            -- 显示名称: 粮信网, 我的钢铁网
    description VARCHAR(500),              -- 描述信息
    logo_url VARCHAR(255),                -- logo URL
    enabled TINYINT(1) DEFAULT 1,          -- 启用状态
    sort_order INT DEFAULT 0,            -- 排序
    config JSON,                          -- 配置信息（登录URL、认证方式等）
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_code (code)
);
```

### 3.2 config 字段结构

```json
{
  "loginUrl": "https://my.chinagrain.cn/jinnong/a/login",
  "authType": "cookie",                  // cookie / token / basic
  "headers": {
    "User-Agent": "Mozilla/5.0..."
  },
  "verifySelectors": ["body:has-text('您好')"],
  "timeout": 30000
}
```

### 3.3 初始化数据

```sql
INSERT INTO t_data_source (code, name, description, config) VALUES
('liangxin', '粮信网', '中国粮食网玉米晨报', '{"loginUrl": "https://my.chinagrain.cn/jinnong/a/login", "authType": "cookie"}'),
('mysteel', '我的钢铁网', '我的钢铁网价格数据', '{"loginUrl": "https://login.mysteel.com.cn", "authType": "cookie"}'),
('chinagrain', '中华粮网', '中华粮网市场数据', '{"loginUrl": "https://www.chinagrain.cn/login", "authType": "cookie"}'),
('usda', 'USDA', '美国农业部数据', '{"authType": "api", "apiKey": "xxx"}'),
('market', '市场数据', '第三方市场数据', '{"authType": "api"}');
```

---

## 四、接口设计

### 4.1 后端接口

| 接口 | 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|------|
| `/datasource` | GET | - | `List<DataSource>` | 获取数据源列表 |
| `/datasource/{code}` | GET | - | `DataSource` | 获取数据源详情 |
| `/datasource` | POST | `{code, name, description, config}` | `DataSource` | 创建数据源 |
| `/datasource/{code}` | PUT | `{name, description, config, enabled}` | `DataSource` | 更新数据源 |
| `/datasource/{code}` | DELETE | - | - | 删除数据源 |
| `/datasource/{code}/enable` | POST | - | - | 启用数据源 |
| `/datasource/{code}/disable` | POST | - | - | 禁用数据源 |

### 4.2 请求/响应示例

**创建数据源：**
```json
POST /datasource
{
  "code": "custom_source",
  "name": "自定义数据源",
  "description": "用于采集第三方数据",
  "config": {
    "loginUrl": "https://example.com/login",
    "authType": "cookie"
  }
}
```

**响应：**
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "code": "custom_source",
    "name": "自定义数据源",
    "enabled": true,
    ...
  }
}
```

---

## 五、前端设计

### 5.1 数据源管理页面

```
路径：/system/datasource

┌─────────────────────────────────────────────────────────────┐
│ 数据源管理                                          [+ 新增] │
├─────────────────────────────────────────────────────────────┤
│ 筛选: [全部 ▼]  [搜索...]                                   │
├─────────────────────────────────────────────────────────────┤
│ ┌────┬────────┬────────┬────────┬────────┬──────────────────┐ │
│ │ 标识 │ 名称    │ 描述    │ 状态    │ 配置    │ 操作          │ │
│ ├────┼────────┼────────┼────────┼────────┼──────────────────┤ │
│ │ liangxin │ 粮信网   │ 中国粮食网│ ● 启用  │ cookie │ [编辑] [删除] │ │
│ │ mysteel  │ 我的钢铁网│ ...     │ ● 启用  │ cookie │ [编辑] [删除] │ │
│ │ chinagrain│ 中华粮网 │ ...     │ ○ 禁用  │ cookie │ [编辑] [删除] │ │
│ └────┴────────┴────────┴────────┴────────┴──────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 新增/编辑弹窗

```
┌─────────────────────────────────────────────────────────────┐
│ 新增数据源                                              [X]  │
├─────────────────────────────────────────────────────────────┤
│ 数据源标识 * │ [liangxin________________________________]     │
│              │ 建议使用英文小写，用于系统内部标识             │
│ 名称 *       │ [粮信网____________________________________]   │
│ 描述         │ [__________________________________________]  │
│ 登录URL      │ [https://my.chinagrain.cn/jinnong/a/login___] │
│ 认证方式     │ [cookie ▼]                                   │
│              │ ○ cookie  ○ token  ○ basic  ○ api           │
│ 启用状态     │ [●] 启用  ○ 禁用                             │
├─────────────────────────────────────────────────────────────┤
│                               [取消]  [保存]                 │
└─────────────────────────────────────────────────────────────┘
```

---

## 七、采集脚本管理

### 7.1 脚本上传

#### 场景

新增数据源时，需要上传对应的 Python 采集脚本文件。

#### 前端实现

```vue
<!-- DataSource.vue 上传组件 -->
<el-upload
    :action="/api/datasource/upload-collector"
    :before-upload="validatePythonFile"
    :on-success="handleUploadSuccess"
    :on-error="handleUploadError"
    :headers="{ Authorization: getToken() }"
    :data="{ code: form.code, name: form.name }"
>
    <el-button type="primary">上传采集脚本</el-button>
</el-upload>

<script>
function validatePythonFile(file) {
    // 只能上传 .py 文件
    if (!file.name.endsWith('.py')) {
        ElMessage.error('只能上传 .py 文件')
        return false
    }
    // 限制文件大小 100KB
    if (file.size > 100 * 1024) {
        ElMessage.error('文件不能超过 100KB')
        return false
    }
    return true
}
</script>
```

#### 后端接口

| 接口 | 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|------|
| `/datasource/upload-collector` | POST | `code, name, file` | `DataSource` | 上传采集脚本 |
| `/datasource/{code}/script` | GET | - | `String` | 查看脚本内容 |
| `/datasource/{code}/exists` | GET | - | `boolean` | 检查脚本是否存在 |

**上传请求：**
```
POST /datasource/upload-collector
Content-Type: multipart/form-data

file: <Python文件>
code: mysteel
name: 我的钢铁网
```

**响应：**
```json
{
  "code": 200,
  "data": {
    "id": 2,
    "code": "mysteel",
    "name": "我的钢铁网",
    "status": "active"
  }
}
```

### 7.2 脚本查看

#### 用途

管理员可查看当前数据源的采集脚本内容（只读），用于排查问题和审计。

#### 后端接口

```java
@GetMapping("/datasource/{code}/script")
public Result<String> getScriptSource(@PathVariable String code) {
    String path = pythonCollectorDir + "/" + code + ".py";
    String source = Files.readString(Path.of(path));
    return Result.success(source);
}
```

#### 前端弹窗

```vue
<el-dialog title="查看采集脚本" v-model="viewDialogVisible" width="80%">
    <el-input 
        type="textarea" 
        :value="currentSourceCode" 
        readonly 
        :rows="20"
        class="code-textarea"
    />
    <template #footer>
        <el-button @click="viewDialogVisible = false">关闭</el-button>
    </template>
</el-dialog>
```

### 7.3 同名文件处理

#### 策略

覆盖更新 + 版本备份

#### 前端交互

```
1. 用户上传 code=mysteel 的脚本
2. 后端检查：mysteel.py 已存在
3. 返回：{ code: 400, message: "数据源已存在，请确认是否覆盖" }
4. 前端弹窗确认
5. 用户点击"确认覆盖"
6. 后端：备份旧文件到 mysteel.py.bak.1715000000000，保存新文件
7. 通知 Python 重载
```

#### 备份文件清理

启动时清理 7 天前的备份文件：

```java
@PostConstruct
public void cleanupOldBackups() {
    Path collectorDir = Path.of(pythonCollectorDir);
    try (Stream<Path> files = Files.list(collectorDir)) {
        files.filter(f -> f.toString().endsWith(".bak."))
             .filter(f -> Files.getLastModifiedTime(f).toMillis() 
                          < System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L)
             .forEach(Files::delete);
    }
}
```

### 7.4 安全限制

| 限制项 | 说明 |
|-------|------|
| 文件类型 | 仅 .py 文件 |
| 文件大小 | ≤100KB |
| 安全扫描 | 禁止危险操作 |

**危险操作检测：**
```java
private boolean validateScriptSafety(String source) {
    String[] dangerous = {"import os", "import subprocess", "import sys", 
                           "eval(", "exec(", "open(", "os.system"};
    for (String pattern : dangerous) {
        if (source.contains(pattern)) {
            return false;
        }
    }
    return source.contains("BaseCollector");
}
```

---

## 九、文件变更清单

| 文件路径 | 操作 | 说明 |
|---------|------|------|
| `backend/src/main/java/com/scfx/entity/DataSource.java` | 新增 | 数据源实体 |
| `backend/src/main/java/com/scfx/mapper/DataSourceMapper.java` | 新增 | 数据源 Mapper |
| `backend/src/main/java/com/scfx/service/DataSourceService.java` | 新增 | 数据源 Service |
| `backend/src/main/java/com/scfx/controller/DataSourceController.java` | 新增 | 数据源 Controller |
| `backend/DataSourceController.java` | 修改 | 添加 uploadCollector、getScript 接口 |
| `schema.sql` | 修改 | 添加 t_data_source、t_script_version、t_script_operation_log 表 |
| `frontend/src/api/datasource.ts` | 新增 | 数据源 API |
| `frontend/src/views/system/DataSource.vue` | 新增 | 数据源管理页面（包含上传、查看脚本） |
| `frontend/src/components/SourceTag.vue` | 修改 | 改为动态从API获取 |
| `frontend/src/router/index.ts` | 修改 | 添加路由 |
| `python-collector-sdk/collectorsdk/collectors/__init__.py` | 修改 | 添加 watchdog 监听 |
| `python-collector-sdk/collectorsdk/base.py` | 修改 | 添加 META 元数据支持 |

---

## 八、脚本版本管理

### 8.1 操作日志

记录每次脚本上传/修改的操作，便于追溯：

```sql
CREATE TABLE t_script_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_code VARCHAR(50) NOT NULL,
    operation_type VARCHAR(20) NOT NULL,    -- UPLOAD / UPDATE / DELETE / ROLLBACK
    operator VARCHAR(100),
    operate_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    file_md5 VARCHAR(32) NOT NULL,
    file_size INT,
    backup_path VARCHAR(255),
    remark VARCHAR(500),
    INDEX idx_datasource_code (datasource_code),
    INDEX idx_operate_time (operate_time)
);
```

**接口：**
| 接口 | 方法 | 说明 |
|------|------|------|
| `/datasource/{code}/operations` | GET | 获取操作历史 |

**响应：**
```json
{
  "code": 200,
  "data": [
    {
      "id": 3,
      "operationType": "UPLOAD",
      "operator": "admin",
      "operateTime": "2026-05-15 14:30:00",
      "fileMd5": "a1b2c3d4e5f6",
      "version": 3,
      "remark": "覆盖旧版本 v2"
    }
  ]
}
```

### 8.2 版本管理

每次上传生成新版本，旧版本存档：

```sql
CREATE TABLE t_script_version (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_code VARCHAR(50) NOT NULL,
    version INT NOT NULL,                   -- 版本号递增
    file_path VARCHAR(255) NOT NULL,
    file_md5 VARCHAR(32) NOT NULL,
    file_size INT,
    is_current TINYINT(1) DEFAULT 0,        -- 是否当前版本
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    UNIQUE INDEX idx_datasource_current (datasource_code, is_current),
    INDEX idx_datasource_version (datasource_code, version)
);
```

**文件目录结构：**
```
python-collector-sdk/collectorsdk/collectors/
├── __init__.py
├── liangxin.py                 # 当前版本
├── mysteel.py                  # 当前版本
│
└── versions/                   # 版本存档目录
    ├── liangxin/
    │   ├── v1.py
    │   ├── v2.py
    │   └── v3.py               # 当前版本
    │
    └── mysteel/
        ├── v1.py
        └── v2.py
```

**上传流程：**
```
1. 读取文件内容，计算 MD5
2. 检查是否有相同 MD5 的最新版本 → 是则跳过
3. 计算新版本号 SELECT MAX(version) + 1
4. 保存文件到版本目录 /versions/{code}/v{version}.py
5. 写入 t_script_version (is_current=1)
6. 更新旧版本 is_current=0
7. 备份旧版本到 /versions/{code}/v{old}.py.bak.{timestamp}
8. 写入 t_script_operation_log
```

### 8.3 版本比较

```java
// 判断是否最新版本 + 文件是否被外部修改
@GetMapping("/datasource/{code}/current-version")
public Result<Map<String, Object>> getCurrentVersion(@PathVariable String code) {
    ScriptVersion current = scriptService.getCurrentVersion(code);
    String currentFileMd5 = calculateMd5(current.getFilePath());

    return Result.success(Map.of(
        "version", current.getVersion(),
        "md5", currentFileMd5(),
        "isLatest", currentFileMd5.equals(current.getFileMd5())  // 文件是否被外部修改
    ));
}
```

### 8.4 回滚功能

```java
@PostMapping("/datasource/{code}/rollback")
public Result<Void> rollback(
    @PathVariable String code,
    @RequestParam int version
) {
    // 1. 检查版本是否存在
    // 2. 复制版本文件到当前版本路径
    // 3. 更新 is_current 标记
    // 4. 写入操作日志（类型=ROLLBACK）
}
```

---

## 十、迁移计划

### 10.1 现有数据迁移

将硬编码的 5 个数据源写入数据库：
```sql
INSERT INTO t_data_source (code, name, description, config)
VALUES
('liangxin', '粮信网', '中国粮食网玉米晨报', '{"authType": "cookie"}'),
('mysteel', '我的钢铁网', '我的钢铁网价格数据', '{"authType": "cookie"}'),
('chinagrain', '中华粮网', '中华粮网市场数据', '{"authType": "cookie"}'),
('usda', 'USDA', '美国农业部数据', '{"authType": "api"}'),
('market', '市场数据', '第三方市场数据', '{"authType": "api"}');
```

### 10.2 代码改造

1. **移除硬编码映射** - CollectorController.getSourceName() 改为从数据库查询
2. **SourceTag.vue** - 改为从 API 获取数据源列表
3. **CategoryMapping** - sourceType 关联到 t_data_source.code

---

## 十一、验证方案

1. **CRUD 测试** - 创建/编辑/删除数据源
2. **前端显示** - SourceTag 正确显示所有数据源
3. **分类映射** - 验证数据源与分类映射正确关联
4. **脚本关联** - 验证脚本可以使用新增的数据源

---

## 十二、优先级

| 功能 | 优先级 | 说明 |
|------|--------|------|
| 数据源 CRUD | P0 | 核心功能 |
| 数据源列表 API | P0 | 前端依赖 |
| 迁移现有数据源 | P0 | 消除硬编码 |
| 脚本上传功能 | P0 | 新增数据源必需 |
| 脚本版本管理 | P1 | 操作记录、版本追溯 |
| 脚本查看功能 | P1 | 排查问题、审计 |
| 前端管理页面 | P1 | 提升可维护性 |
| 回滚功能 | P1 | 版本管理必需 |
| 采集器配置关联 | P2 | 后续扩展 |