# 开发规范

## 1. 开发环境搭建

### 1.1 必要工具

| 工具 | 版本 | 用途 |
|------|------|------|
| JDK | 17+ | Java 运行环境 |
| Maven | 3.8+ | Java 构建工具 |
| Node.js | 18+ | 前端运行环境 |
| IDE | - | 开发工具（IntelliJ IDEA / VS Code） |
| Git | 2.30+ | 版本控制 |

### 1.2 IDE 配置

**IntelliJ IDEA:**
- 安装 Lombok 插件
- 设置 JDK 17
- 启用注解处理器

**VS Code (前端):**
- Volar 插件（Vue 3 支持）
- ESLint 插件
- Prettier 插件

---

## 2. 代码规范

### 2.1 Java 编码规范

**命名规范：**

| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | UpperCamelCase | `CollectionTaskService` |
| 方法名 | lowerCamelCase | `getTaskById` |
| 变量名 | lowerCamelCase | `taskId`, `sourceName` |
| 常量 | UPPER_SNAKE_CASE | `MAX_RETRY_TIMES` |
| 包名 | lowercase | `com.scfx.service` |

**类结构顺序：**

```java
public class ExampleClass {

    // 1. 常量
    private static final Logger log = LoggerFactory.getLogger(ExampleClass.class);

    // 2. 成员变量
    private final String name;
    private int age;

    // 3. 构造方法
    public ExampleClass(String name) {
        this.name = name;
    }

    // 4. 公共方法
    public void doSomething() { }

    // 5. 私有方法
    private void helper() { }
}
```

**注解使用：**
- `@Service` / `@Repository` / `@Controller` 放在类上
- `@Autowired` 尽量使用构造器注入
- `@Transactional` 放在 public 方法上

### 2.2 前端编码规范

**命名规范：**

| 类型 | 规范 | 示例 |
|------|------|------|
| 组件名 | PascalCase | `Dashboard.vue` |
| 方法名 | camelCase | `handleCollect` |
| 事件名 | kebab-case | `click-handler` |
| CSS 类名 | kebab-case | `.stat-card` |
| 常量 | UPPER_SNAKE_CASE | `API_BASE_URL` |

**Vue SFC 结构：**

```vue
<template>
  <!-- 模板内容 -->
</template>

<script setup lang="ts">
// 导入
import { ref, computed } from 'vue'

// 常量
const BASE_URL = '/api'

// 响应式数据
const data = ref()

// 计算属性
const computedData = computed(() => { })

// 方法
function handleClick() { }

// 生命周期
onMounted(() => { })
</script>

<style scoped>
/* 样式 */
</style>
```

---

## 3. Git 使用规范

### 3.1 分支管理

```
main          # 主分支（生产环境）
├── develop   # 开发分支
├── feature/* # 功能分支
├── fix/*     # 修复分支
└── release/* # 发布分支
```

**命名规范：**

| 分支类型 | 命名示例 | 说明 |
|----------|----------|------|
| 功能分支 | feature/dashboard-stats | 新功能 |
| 修复分支 | fix/login-error | Bug 修复 |
| 发布分支 | release/v1.0.0 | 正式发布 |

### 3.2 提交规范

格式：

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Type 类型：**

| type | 说明 |
|------|------|
| feat | 新功能 |
| fix | 修复 Bug |
| docs | 文档更新 |
| style | 代码格式（不影响功能） |
| refactor | 重构 |
| perf | 性能优化 |
| test | 测试相关 |
| chore | 构建/工具相关 |

**示例：**

```
feat(collection): 添加粮信网采集器

- 实现登录模块
- 实现页面爬取模块
- 实现数据解析模块

Closes #123
```

### 3.3 Pull Request 流程

1. 从 `develop` 创建功能分支
2. 完成开发并提交
3. 推送到远程仓库
4. 创建 Pull Request
5. 代码 Review
6. 合并到 `develop`

---

## 4. 数据库规范

### 4.1 表设计规范

- 表名使用小写，单词间用下划线分隔
- 主键统一使用 `BIGINT AUTO_INCREMENT`
- 必须创建索引
- 敏感字段加密存储
- 软删除优先（deleted 字段）

### 4.2 字段命名

| 字段类型 | 后缀 | 示例 |
|----------|------|------|
| 时间 | _time | create_time, update_time |
| 计数 | _count | success_count |
| 状态 | _status | order_status |
| 标识 | _id | task_id, user_id |
| 金额 | _amount | order_amount |

### 4.3 SQL 编写规范

- 使用参数化查询，防止 SQL 注入
- 避免 SELECT *，明确列出字段
- 批量操作使用 INSERT ... VALUES (), ()
- 重要更新操作使用事务

---

## 5. API 设计规范

### 5.1 URL 规范

```
/资源/主键/子资源?参数=值
```

| 操作 | 方法 | URL 示例 |
|------|------|----------|
| 获取列表 | GET | /tasks?page=1&size=20 |
| 获取详情 | GET | /tasks/1 |
| 创建 | POST | /tasks |
| 更新 | PUT | /tasks/1 |
| 删除 | DELETE | /tasks/1 |

### 5.2 状态码

| HTTP 方法 | 成功状态码 | 说明 |
|-----------|------------|------|
| GET | 200 | 查询成功 |
| POST | 201 | 创建成功 |
| PUT | 200 | 更新成功 |
| DELETE | 200 | 删除成功 |

### 5.3 分页响应

```json
{
  "code": 200,
  "data": {
    "records": [...],
    "total": 100,
    "size": 20,
    "current": 1,
    "pages": 5
  }
}
```

---

## 6. 日志规范

### 6.1 日志级别

| 级别 | 使用场景 |
|------|----------|
| ERROR | 系统异常、采集失败 |
| WARN | 重试、配置警告 |
| INFO | 任务开始/结束、重要步骤 |
| DEBUG | 详细调试信息 |

### 6.2 日志格式

```
[TIMESTAMP] [LEVEL] [SOURCE] [EXECUTION_ID] Message
2026-04-28 10:30:00 INFO liangxinwang abc123 开始登录粮信网
```

### 6.3 敏感信息

**禁止记录：**
- 密码
- Token
- 身份证号
- 银行卡号

---

## 7. 配置管理

### 7.1 配置文件结构

```
src/main/resources/
├── application.yml          # 默认配置
├── application-dev.yml      # 开发环境
├── application-prod.yml     # 生产环境
└── schema.sql              # 数据库初始化
```

### 7.2 敏感配置

使用环境变量：

```yaml
database:
  password: ${DB_PASSWORD}
```

### 7.3 多数据源配置

```yaml
app:
  collection:
    sources:
      liangxinwang:
        enabled: true
      mysteel:
        enabled: false
```

---

## 8. 安全规范

### 8.1 输入校验

- 所有用户输入必须校验
- SQL 参数化查询
- XSS 过滤

### 8.2 权限控制

- 接口权限验证
- 数据权限隔离
- 操作日志审计

### 8.3 敏感数据

- 密码加密存储
- 配置文件不提交到 Git
- 定期更换密钥

---

## 9. 目录结构

### 9.1 后端结构

```
backend/
├── src/main/java/com/scfx/
│   ├── controller/      # 控制器
│   ├── service/        # 服务层
│   ├── mapper/         # 数据访问层
│   ├── entity/         # 实体类
│   ├── dto/           # 数据传输对象
│   ├── vo/            # 视图对象
│   ├── config/        # 配置类
│   └── common/       # 公共类
└── src/main/resources/
    ├── application.yml
    └── schema.sql
```

### 9.2 前端结构

```
frontend/
├── src/
│   ├── api/           # API 调用
│   ├── assets/        # 静态资源
│   ├── components/   # 公共组件
│   ├── router/       # 路由配置
│   ├── utils/        # 工具函数
│   ├── views/        # 页面组件
│   ├── App.vue
│   └── main.ts
├── public/
└── package.json
```
