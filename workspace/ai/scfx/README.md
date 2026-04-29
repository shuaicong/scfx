# 海南储备集团粮食市场智能分析平台

[![Java Version](https://img.shields.io/badge/Java-17-orange)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)](https://spring.io/projects/spring-boot)
[![Vue.js](https://img.shields.io/badge/Vue%203-4.0-blue)](https://vuejs.org/)

粮食市场数据采集、分析和智能问答平台。

## 功能特性

- 多数据源自动化采集（粮信网、我的钢铁网、中华粮网等）
- 企业级知识库构建
- AI 智能问答（价格、走势、供需分析）
- 自动生成周报、月报（带图表）
- 实时监控告警

## 技术栈

| 层级 | 技术 |
|------|------|
| 后端 | Java 17 + Spring Boot 3.2 + MyBatis-Plus |
| 前端 | Vue 3 + TypeScript + Element Plus + ECharts |
| 采集器 | Python 3.10+ + Playwright |
| 数据库 | H2（开发）/ MySQL（生产） |
| 缓存 | Redis（可选） |

## 项目结构

```
scfx/
├── backend/                    # Java 后端
│   ├── src/main/java/com/scfx/
│   │   ├── controller/         # 控制器
│   │   ├── service/           # 服务层
│   │   ├── mapper/           # 数据访问层
│   │   ├── entity/           # 实体类
│   │   └── common/           # 公共类
│   └── src/main/resources/
│       ├── application.yml    # 配置文件
│       └── schema.sql        # 数据库初始化
├── frontend/                  # Vue 前端
│   ├── src/
│   │   ├── api/             # API 接口
│   │   ├── views/          # 页面视图
│   │   ├── router/         # 路由配置
│   │   └── assets/         # 静态资源
│   └── package.json
├── docs/                      # 项目文档
│   ├── requirements.md       # 需求文档
│   ├── architecture.md       # 架构文档
│   ├── db-design.md         # 数据库设计
│   ├── api.md               # API 文档
│   ├── python-collector.md   # 采集器指南
│   ├── deploy.md            # 部署文档
│   └── dev-guide.md         # 开发规范
├── data/                      # 数据目录
└── python-collector/          # Python 采集器
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- Node.js 18+
- npm 9+

### 1. 启动后端

```bash
cd backend

# 编译
mvn clean compile -DskipTests

# 启动开发服务器
mvn spring-boot:run -DskipTests

# 或打包后运行
mvn clean package -DskipTests
java -jar target/grain-platform-1.0.0.jar
```

后端启动后：
- API 地址：http://localhost:8080/api
- H2 Console：http://localhost:8080/api/h2-console

### 2. 启动前端

```bash
cd frontend

# 安装依赖
npm install

# 开发模式
npm run dev

# 生产构建
npm run build
```

前端启动后：http://localhost:3000

### 3. 默认账号

| 系统 | 账号 | 密码 |
|------|------|------|
| H2 Console | sa | （空） |

## 数据源

### 已支持

| 数据源 | 代码 | 品种 |
|--------|------|------|
| 粮信网 | liangxinwang | 玉米、小麦 |

### 待接入

- 我的钢铁网 (mysteel)
- 中华粮网 (china_grain)
- 中国玉米市场网 (corn_market)

## 配置

### 后端配置

配置文件：`backend/src/main/resources/application.yml`

```yaml
# 数据源
spring:
  datasource:
    url: jdbc:h2:file:./data/grain_platform
    username: sa
    password:

# 采集器配置
app:
  collection:
    sources:
      liangxinwang:
        name: 粮信网
        enabled: true
        username: ${LXW_USERNAME:33022}
        password: ${LXW_PASSWORD:qlp707}
```

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| LXW_USERNAME | 粮信网用户名 | 33022 |
| LXW_PASSWORD | 粮信网密码 | qlp707 |

## 文档目录

| 文档 | 说明 |
|------|------|
| [docs/requirements.md](docs/requirements.md) | 需求文档 |
| [docs/architecture.md](docs/architecture.md) | 系统架构 |
| [docs/db-design.md](docs/db-design.md) | 数据库设计 |
| [docs/api.md](docs/api.md) | API 接口文档 |
| [docs/python-collector.md](docs/python-collector.md) | 采集器开发指南 |
| [docs/deploy.md](docs/deploy.md) | 部署文档 |
| [docs/dev-guide.md](docs/dev-guide.md) | 开发规范 |

## 版本历史

详见 [docs/changelog.md](docs/changelog.md)

## 许可证

Private - 海南储备集团内部使用
