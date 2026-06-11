# Flyway 数据库迁移集成方案

> **Goal:** 引入 Flyway 管理数据库 schema 版本，消除「表不存在」类运行时错误，使 schema 变更与代码版本绑定。

**Architecture:** Spring Boot + Flyway Community Edition + MySQL 8.0。Flyway 在应用启动时自动检测并执行未应用的迁移脚本，通过 `flyway_schema_history` 表记录已执行记录。

**Core decision: baseline-version=5，不创建 V1～V4。** 当前数据库已有全量表结构（来自 schema.sql），直接基线标记为 Version 5，已有脚本全部跳过。未来新 DDL 从 V6 开始，由 Flyway 严格管理。这是官方推荐的现有数据库接入方式。

**Tech Stack:** flyway-core, flyway-mysql, Spring Boot 自动配置

---

### Task 1: 整合 Flyway 依赖与配置

**Files:**
- Modify: `backend/pom.xml` — 添加 Flyway 依赖（锁定版本）
- Modify: `backend/src/main/resources/application.yml` — 添加 Flyway 配置（含多环境隔离）

**改动细节：**

1. **pom.xml** 在 `<dependencies>` 中添加（显式锁定版本，防止父依赖升级兼容问题）：

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <version>9.22.3</version>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
    <version>9.22.3</version>
</dependency>
```

2. **application.yml** — 公共配置 + 多环境 profile 覆盖：

```yaml
# ========== 公共配置（默认生效） ==========
spring:
  sql:
    init:
      mode: never              # 彻底关闭 Spring Boot 原生 SQL 执行，仅 Flyway 接管
  flyway:
    enabled: true
    locations: classpath:db/migration
    encoding: UTF-8            # 统一编码，避免中文乱码
    validate-on-migrate: true  # 启动时校验已执行脚本是否被修改、版本是否合法
    fail-on-missing-locations: true  # 脚本目录缺失直接报错，防止静默跳过
    repair-on-migration: false # 禁止自动修复，生产环境脚本被改应手动介入
    baseline-on-migrate: false # 默认关闭，各环境按需开启

---
# ========== 开发环境 ==========
spring:
  profiles: dev
  flyway:
    clean-disabled: false      # 允许 clean，方便本地清空测试库重建
    baseline-on-migrate: true   # 首次启动自动基线
    baseline-version: 5

---
# ========== 测试/生产环境 ==========
spring:
  profiles: test, prod
  flyway:
    clean-disabled: true       # 严格禁止清空库
    baseline-on-migrate: true
    baseline-version: 5
```

3. **配置项说明：**

| 配置 | 作用 |
|------|------|
| `encoding: UTF-8` | 迁移脚本统一 UTF-8，防止中文乱码导致执行失败 |
| `validate-on-migrate: true` | 每次启动校验 `flyway_schema_history` 中已执行脚本的 checksum 是否匹配文件，发现篡改直接报错停服 |
| `fail-on-missing-locations: true` | `db/migration/` 目录不存在时直接报错，避免误配置后静默通过 |
| `repair-on-migration: false` | 禁止自动修复 checksum 不匹配问题，防止生产静默跳过合法性校验 |
| `clean-disabled: true` (test/prod) | 防止任何人误调用 `clean` 清空全库 |
| `clean-disabled: false` (dev) | 开发人员可手动 `mvn flyway:clean` 重建测试库 |

---

### Task 2: 整理迁移脚本目录

**核心原则：不动现有文件，不创建 V1～V4。零散旧脚本归档到 `archive/` 子目录，禁止删除。**

已有 `V5__create_chat_history.sql` 保留在 `db/migration/` 目录中。Flyway 基线版本设为 5，V5 不会被重新执行（当做基线前已应用），仅作为参考文件存在。未来新迁移脚本从 V6 开始。

**Files:**
- Create: `backend/src/main/resources/db/migration/archive/` — 归档目录
- Move: `docs/superpowers/migrations/migration-V2-chunk-fields.sql` → `db/migration/archive/`
- Move: `docs/superpowers/migrations/migration-V3-chunk-type.sql` → `db/migration/archive/`
- Move: `docs/superpowers/migrations/migration-V3-knowledge-task.sql` → `db/migration/archive/`
- Move: `database/migration_001.sql` → `db/migration/archive/`
- Move: `database/migration_002_table_meta.sql` → `db/migration/archive/`
- **保留**: `backend/src/main/resources/db/migration/V5__create_chat_history.sql`
- **不创建**: V1、V2、V3、V4
- **保留**: `backend/src/main/resources/schema.sql` — 不作为 Flyway 脚本，仅用于参考/裸数据库快速初始化

归档后的每个 `.sql` 文件头部标注归档元信息：

```sql
-- ============================================================
-- ARCHIVED: migration-V3-knowledge-task.sql
-- 归档原因: 被 Flyway V5 baseline 覆盖，纳入基线管理
-- 归档日期: 2026-06-11
-- 对应版本: baseline-5
-- 说明: 该表已通过 schema.sql 创建，不再作为独立迁移执行
-- ============================================================
```

**变更对照：**

| 文件 | 操作 | 说明 |
|------|------|------|
| `db/migration/V5__create_chat_history.sql` | 不动 | 保留在原位，V5 = baseline，不会被 Flyway 执行 |
| `db/migration/archive/*.sql` | 移入归档 | 零散旧迁移脚本，禁止删除，保留历史追溯 |
| `db/migration/V{m}__*.sql` (m ≥ 6) | 新建 | 未来所有 DDL 变更从这里开始 |
| `schema.sql` | 保留，不纳入 Flyway | 用于裸数据库初始化/灾备，后续不维护 |

---

### Task 3: 生产级验证（5 项）

**Files:** 无新增，运行验证

#### 3.1 语法校验 — `mvn flyway:validate`

编译阶段提前拦截 SQL 语法错误，无需启动服务：

```bash
cd backend
mvn flyway:validate -P dev
```

预期输出：`BUILD SUCCESS`，无语法错误提示。

如果使用 Flyway 的 Maven 插件需要先在 pom.xml `<build><plugins>` 中添加：

```xml
<plugin>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-maven-plugin</artifactId>
    <version>9.22.3</version>
</plugin>
```

#### 3.2 首次基线校验

启动后端（dev profile），验证基线记录正确：

```bash
# 启动后端（使用 dev 配置）
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

观察启动日志：

```
INFO: Creating schema history table `grain_platform`.`flyway_schema_history`
INFO: Baseline `grain_platform` with schema version 5
INFO: Successfully applied 0 migrations (execution time 00:00.012s)
```

验证基线记录：

```bash
docker exec scfx-mysql mysql -uroot -pScfx@2024 grain_platform \
  -e "SELECT version, description, type, installed_on, success FROM flyway_schema_history;"
```

预期：一条 `version=5, type=BASELINE, success=1` 的记录。

#### 3.3 增量迁移 + 重复启动校验

创建 `V6__test_verify.sql`：

```sql
-- V6__test_verify.sql
CREATE TABLE IF NOT EXISTS t_flyway_test (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    note VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Flyway 集成验证表';
```

重启后端，检查 `flyway_schema_history` 新增 V6 记录：

```bash
SELECT version, type, success, installed_on FROM flyway_schema_history;
-- 预期: V5 BASELINE + V6 SQL_MIGRATION
```

**重复启动 3 次**，每次确认：

- 日志没有 `applying migration` 输出（仅基线 + V6 首次执行）
- `flyway_schema_history` 记录数不变（始终 2 条）
- `t_flyway_test` 表数据未重复插入（空表）

#### 3.4 跨环境同步校验

依次以 dev → test → prod profile 启动（可本地切换 datasource 指向不同库模拟）：

```bash
# 每个 profile 启动后检查版本一致性
for env in dev test prod; do
    echo "=== $env ==="
    # 启动命令略（各环境连接不同 DB）
    docker exec scfx-mysql mysql -uroot -pScfx@2024 grain_platform \
      -e "SELECT version, type, file, checksum FROM flyway_schema_history ORDER BY version;"
done
```

**核对项：**

| 检查点 | 预期 |
|--------|------|
| 三个环境的 `flyway_schema_history` 版本序列完全一致 | V5 BASELINE, V6 SQL_MIGRATION |
| checksum 值一致 | 相同脚本的 checksum 在所有环境相同 |
| `success` 全部为 1 | 无失败记录 |

#### 3.5 超大表兼容性测试（选做）

针对数据量大的表（如 `t_knowledge_chunk`、`t_task_execution`），验证 ALTER 是否锁表：

```sql
-- 先模拟大表数据
INSERT INTO t_flyway_test (note)
SELECT CONCAT('test_', n) FROM (
    WITH RECURSIVE seq(n) AS (
        SELECT 1 UNION ALL SELECT n+1 FROM seq WHERE n < 100000
    ) SELECT n FROM seq
) tmp;

-- 执行类 ALTER 操作（如 V7 迁移内容）
ALTER TABLE t_flyway_test ADD COLUMN IF NOT EXISTS batch_note VARCHAR(200);

-- 检查执行时长
SELECT version, installed_on, execution_time_ms
FROM flyway_schema_history;
```

MySQL 8.0 对 `ADD COLUMN` 支持 **即时 DDL**（Instant DDL），仅修改元数据不锁表。`MODIFY COLUMN`、`ADD INDEX` 等仍是 `INPLACE` 或 `COPY` 模式，大表下需关注执行窗口。<br>
验证标准：单次迁移执行时间 < 业务可接受的停机窗口（建议：线上 < 30s）。

---

### Task 4: 写入项目 DDL 规范

**Files:** 修改 `CLAUDE.md` 或新建 `CONTRIBUTING.md`

在项目文档中添加：

```markdown
## 数据库迁移规范

Flyway 已集成（baseline-version=5），管理入口：`backend/src/main/resources/db/migration/`

### 核心约束

1. **新增 DDL 变更**：在 `db/migration/` 下新建 `V{版本}__{描述}.sql`，版本号从 V6 开始递增
2. **迁移脚本必须全语句幂等**：每条语句在任何状态下重复执行都不会报错或产生副作用。详见「幂等写法参考表」
3. **禁止直接改生产 schema**：任何 DDL 变更必须通过 Flyway 迁移脚本，随代码提交
4. **不做版本号重排**：已有 V5 是基线，后续脚本版本号只增不减
5. **回滚原则**：禁止线上直接删字段/删表。优先新增字段+废弃旧字段，非必要时不做破坏性变更。详细流程见「标准回滚流程」

### 强约束（4 条）

#### ① 已上线脚本绝对禁止修改

- 线上/测试环境已执行的 `V{n}.sql`，**只新增、不编辑、不重命名**
- Flyway 通过 checksum 校验脚本完整性，改已执行脚本 → 启动报错 `ValidateException: Migration checksum mismatch`
- 修复已上线脚本的 bug → 通过新版本 `V{n+1}.sql` 补充修复

#### ② 版本号规则

- **全局单调递增**：所有环境共用同一套版本序列，V1 → V2 → V3 → … → Vn
- **不跳号**：不留空缺，如果 PR 按顺序合并但某个版本临时被撤，用 `V{n+1}` 跳过已撤版本，不能回填
- **不重复**：同一版本只出现一次，不可在多分支同时写相同版本号
- **不回退**：已合并的版本号永不降级

#### ③ 脚本内容约束

| 允许 | 禁止 |
|------|------|
| DDL：建表/改表/加索引/约束 | DML 批量 INSERT/UPDATE/DELETE（少量字典初始化除外） |
| 少量字典初始化（INSERT IGNORE） | 业务数据迁移脚本 |
| 存储过程/视图（需幂等包装） | 与业务逻辑耦合的数据修正 |

- **批量业务数据初始化** → 拆分到独立脚本，**不在 `db/migration/` 下执行**，通过专门的 data-migration 流程处理
- **单脚本不宜过大**，按功能拆分（如：V6__add_knowledge_tags.sql、V7__create_user_settings.sql）

#### ④ 合并 & 上线前置检查（CI 卡点）

```yaml
# CI pipeline 步骤
check-flyway:
  stage: validate
  script:
    - cd backend
    - mvn flyway:validate                          # 语法 + checksum 校验
    - bash scripts/check-flyway-version.sh          # 自定义：检查版本号是否跳号/重复
```

```bash
#!/usr/bin/env bash
# scripts/check-flyway-version.sh
# CI 卡点：检查新增迁移脚本版本号是否连续无跳号
set -euo pipefail

MIGRATION_DIR="backend/src/main/resources/db/migration"
LATEST=$(git ls-files "$MIGRATION_DIR/V*.sql" | sed 's/.*\/V//;s/__.*//' | sort -n | tail -1)
NEW_FILES=$(git diff --name-only --cached | grep "$MIGRATION_DIR/V.*\.sql" || true)

for f in $NEW_FILES; do
  VER=$(echo "$f" | sed 's/.*\/V//;s/__.*//')
  if [ "$VER" -le "$LATEST" ]; then
    echo "ERROR: Migration version $VER <= latest $LATEST"
    echo "  Version must be strictly greater than $LATEST"
    exit 1
  fi
done
```

> **CI 卡点说明**：`flyway:validate` 拦截语法错误和 checksum 篡改；自定义脚本拦截版本号跳号/重复合并。两个都在代码合并前执行，**不通过不能合并**。

### 幂等写法参考表

每个操作类型必须使用对应的幂等方式，禁止裸写无保护的 DDL/DML：

| 操作类型 | 安全写法 | 不安全写法（禁止） |
|----------|----------|-------------------|
| **建表** | `CREATE TABLE IF NOT EXISTS t_foo (...)` | `CREATE TABLE t_foo (...)` |
| **删表** | `DROP TABLE IF EXISTS t_foo` | `DROP TABLE t_foo` |
| **加列** | `ALTER TABLE t_foo ADD COLUMN IF NOT EXISTS bar VARCHAR(50)` | `ALTER TABLE t_foo ADD COLUMN bar VARCHAR(50)` |
| **删列** | `ALTER TABLE t_foo DROP COLUMN IF EXISTS bar` | `ALTER TABLE t_foo DROP COLUMN bar` |
| **改列类型** | `ALTER TABLE t_foo MODIFY COLUMN bar VARCHAR(100)`（幂等需配合前置列存在性检查，见下文） | 直接 `MODIFY` 在新表上无影响，但在增量迁移中建议用存储过程守卫 |
| **加索引** | `CREATE INDEX IF NOT EXISTS idx_foo ON t_foo(col)` | `CREATE INDEX idx_foo ON t_foo(col)` / `ALTER TABLE t_foo ADD INDEX idx_foo (col)` |
| **删索引** | `DROP INDEX IF EXISTS idx_foo ON t_foo` | `DROP INDEX idx_foo ON t_foo` |
| **加唯一约束** | MySQL 8.0 不支持 `ALTER TABLE ... ADD CONSTRAINT IF NOT EXISTS`，改用存储过程守卫 | `ALTER TABLE t_foo ADD UNIQUE KEY uk_bar (bar)` |
| **建存储过程/函数** | `CREATE PROCEDURE IF NOT EXISTS sp_foo(...)` 或 `DROP PROCEDURE IF EXISTS sp_foo; CREATE PROCEDURE ...` | `CREATE PROCEDURE sp_foo(...)` |
| **建视图** | `CREATE OR REPLACE VIEW v_foo AS SELECT ...` | `CREATE VIEW v_foo AS SELECT ...` |
| **INSERT 初始化数据** | `INSERT IGNORE INTO t_dict (code, name) VALUES ('a', 'A')` 或 `INSERT ... ON DUPLICATE KEY UPDATE name=VALUES(name)` | `INSERT INTO t_dict (code, name) VALUES ('a', 'A')` |

### 列变更幂等存储过程模板

`MODIFY COLUMN`、`ALTER TABLE ADD CONSTRAINT` 等没有标准 `IF NOT EXISTS` 的操作，使用存储过程守卫：

```sql
-- 修改列类型（仅当列不存在目标类型时执行）
DROP PROCEDURE IF EXISTS sp_ensure_col_type;
DELIMITER //
CREATE PROCEDURE sp_ensure_col_type()
BEGIN
    DECLARE col_type VARCHAR(100);
    SELECT DATA_TYPE INTO col_type FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 't_foo' AND COLUMN_NAME = 'bar';
    IF col_type IS NULL THEN
        ALTER TABLE t_foo ADD COLUMN bar VARCHAR(100);
    ELSEIF col_type != 'varchar' THEN
        ALTER TABLE t_foo MODIFY COLUMN bar VARCHAR(100);
    END IF;
END //
DELIMITER ;
CALL sp_ensure_col_type();
DROP PROCEDURE IF EXISTS sp_ensure_col_type;
```

```sql
-- 加唯一约束（仅当不存在时添加）
DROP PROCEDURE IF EXISTS sp_ensure_unique;
DELIMITER //
CREATE PROCEDURE sp_ensure_unique()
BEGIN
    DECLARE cnt INT;
    SELECT COUNT(*) INTO cnt FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 't_foo' AND CONSTRAINT_TYPE = 'UNIQUE'
          AND CONSTRAINT_NAME = 'uk_bar';
    IF cnt = 0 THEN
        ALTER TABLE t_foo ADD UNIQUE KEY uk_bar (bar);
    END IF;
END //
DELIMITER ;
CALL sp_ensure_unique();
DROP PROCEDURE IF EXISTS sp_ensure_unique;
```

> **注：** 存储过程模板会自清理（末尾 `DROP PROCEDURE`），不会残留到生产环境。这段模板可以提取为内部工具脚本，写新迁移时直接粘贴调整。
```

---

### 标准回滚流程（社区版通用）

Flyway 社区版不支持 `flyway undo`，所有回滚通过手动 SQL + 版本记录清理完成。以下流程适用于生产环境紧急回滚。

#### 原则

1. **禁止线上直接删字段/删表**：优先反向操作（ADD → DROP 是加一个废弃前缀，如 `col_obsolete_xxx`），等下一个正 式版迁移统一清理
2. **不改已有脚本**：回滚后的修复通过新版本（V{n+1}）解决，不修改已发布的 V{n}
3. **双版本灰度**：重大 DDL 变更（改列类型、拆表、合表）建议先以新增兼容字段/表的方式灰度，确认稳定后再发新迁移清理旧结构

#### 回滚流程

```
Step 1: 评估影响
├── 确认回滚范围（单个版本 / 连续多个版本）
├── 确认反向 SQL 的正确性（本地先执行验证）
└── 确认没有其他版本依赖被回滚的内容

Step 2: 编写并执行反向 SQL
├── 建表 → DROP TABLE IF EXISTS t_foo
├── 加列 → ALTER TABLE t_foo DROP COLUMN IF EXISTS bar
├── 删列 → ALTER TABLE t_foo ADD COLUMN bar VARCHAR(50)
├── 加索引 → DROP INDEX IF EXISTS idx_foo ON t_foo
├── INSERT 初始化 → DELETE FROM t_dict WHERE code IN ('a', 'b')
└── 存储过程/视图 → DROP PROCEDURE IF EXISTS sp_foo / DROP VIEW IF EXISTS v_foo

Step 3: 删除 flyway_schema_history 中对应版本记录
  DELETE FROM flyway_schema_history WHERE version = 6;

Step 4: 通知团队该版本已回滚，禁止后续版本引用其新增的表/列
```

#### 临时回滚 vs 永久修复

| 场景 | 操作 |
|------|------|
| 迁移脚本有 bug，需紧急恢复 | 执行反向 SQL + 删除历史记录（如上流程） |
| 业务需求变更，不需要某张表/列了 | 新建 V{n+1} 清理脚本，不做回滚 |
| 迁移冲突（两个分支同时改了 schema） | 保留先合并的版本，后合并的分支调整版本号重新提交 |

#### 反向 SQL 对照表

每写一个新迁移脚本，应同步准备其反向 SQL。以下为常见操作的逆操作：

| 正向操作（迁移） | 反向操作（回滚） |
|----------------|----------------|
| `CREATE TABLE t_foo (...)` | `DROP TABLE IF EXISTS t_foo` |
| `ALTER TABLE t ADD COLUMN c INT` | `ALTER TABLE t DROP COLUMN IF EXISTS c` |
| `ALTER TABLE t DROP COLUMN c` | `ALTER TABLE t ADD COLUMN c INT` |
| `ALTER TABLE t MODIFY c VARCHAR(100)` | `ALTER TABLE t MODIFY c VARCHAR(旧长度)`（注意备份原定义） |
| `CREATE INDEX idx ON t(c)` | `DROP INDEX IF EXISTS idx ON t` |
| `CREATE PROCEDURE sp_foo` | `DROP PROCEDURE IF EXISTS sp_foo` |
| `INSERT INTO t_dict VALUES ('a','A')` | `DELETE FROM t_dict WHERE code = 'a'` |

---

### 风险与说明

- **flyway-core 社区版不支持 undo 迁移脚本**：Flyway Teams/Enterprise 支持自动回滚，社区版只能手动处理。标准流程见下方「标准回滚流程」
- **为什么 baseline-version=5 最安全**：
  - `baseline-version: 0` → Flyway 执行 V1～V5 全部脚本，`ALTER TABLE`/索引/约束等可能因重复执行告警或报错
  - `baseline-version: 5` → 跳过全部已有脚本，不碰现有 schema，零风险
  - 新增脚本从 V6 开始，首次 Flyway 实际执行的即是新脚本，可观察性高
- **V5__create_chat_history.sql 不会被执行**：因为 V5 ≤ baseline(5)，Flyway 视为已基线前已应用，不会报错也不会重跑。该文件仅作为版本占位存在
- **schema.sql 独立于 Flyway**：仅用于裸数据库首次建表，后续不再维护。未来所有变更都走 Flyway
- **Spring Boot 版本兼容**：SB 3.2.x 的父 POM 管理的 Flyway 版本直接可用，无需额外指定 `<version>`
