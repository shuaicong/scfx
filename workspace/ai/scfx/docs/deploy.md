# 部署文档

## 1. 环境准备

### 1.1 服务器要求

| 项目 | 开发环境 | 生产环境 |
|------|----------|----------|
| CPU | 2核 | 4核+ |
| 内存 | 4GB | 8GB+ |
| 磁盘 | 20GB | 100GB+ |
| OS | macOS/Ubuntu | CentOS 7+/Ubuntu 18+ |

### 1.2 软件依赖

| 软件 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 后端运行环境 |
| Maven | 3.8+ | 后端构建工具 |
| Node.js | 18+ | 前端构建工具 |
| MySQL | 8.0+ | 生产数据库 |
| Redis | 6.0+ | 缓存（可选） |
| Nginx | 1.18+ | 反向代理 |

---

## 2. 开发环境部署

### 2.1 后端部署

```bash
cd backend

# 编译打包
mvn clean package -DskipTests

# 运行
java -jar target/grain-platform-1.0.0.jar
```

### 2.2 前端部署

```bash
cd frontend

# 安装依赖
npm install

# 开发模式
npm run dev

# 生产构建
npm run build
```

---

## 3. 生产环境部署

### 3.1 数据库配置

```sql
-- 创建数据库
CREATE DATABASE grain_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 创建用户
CREATE USER 'grain'@'%' IDENTIFIED BY 'YourPassword123';
GRANT ALL PRIVILEGES ON grain_platform.* TO 'grain'@'%';
FLUSH PRIVILEGES;
```

### 3.2 后端配置

创建 `application-prod.yml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/grain_platform?useUnicode=true&characterEncoding=utf8
    username: grain
    password: YourPassword123
    driver-class-name: com.mysql.cj.jdbc.Driver

  # 关闭H2
  sql:
    init:
      mode: never

app:
  collection:
    sources:
      liangxinwang:
        enabled: true
```

启动后端：

```bash
java -jar grain-platform-1.0.0.jar --spring.profiles.active=prod
```

### 3.3 Nginx 配置

```nginx
upstream backend {
    server 127.0.0.1:8080;
}

upstream frontend {
    server 127.0.0.1:3000;
}

server {
    listen 80;
    server_name your-domain.com;

    # 前端静态文件
    location / {
        proxy_pass http://frontend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }

    # API 代理
    location /api {
        proxy_pass http://backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2)$ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }
}
```

### 3.4 Systemd 服务

创建 `/etc/systemd/system/grain-platform.service`:

```ini
[Unit]
Description=Grain Platform Backend Service
After=network.target mysql.service

[Service]
Type=simple
User=app
Group=app
WorkingDirectory=/opt/grain-platform
ExecStart=/usr/bin/java -jar grain-platform-1.0.0.jar --spring.profiles.active=prod
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

启动服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable grain-platform
sudo systemctl start grain-platform
sudo systemctl status grain-platform
```

---

## 4. Docker 部署（推荐）

### 4.1 Docker Compose 配置

创建 `docker-compose.yml`:

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: grain-mysql
    environment:
      MYSQL_ROOT_PASSWORD: root_password
      MYSQL_DATABASE: grain_platform
      MYSQL_USER: grain
      MYSQL_PASSWORD: grain_password
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql
    restart: unless-stopped

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: grain-backend
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: mysql
      DB_PORT: 3306
      DB_NAME: grain_platform
      DB_USERNAME: grain
      DB_PASSWORD: grain_password
    ports:
      - "8080:8080"
    depends_on:
      - mysql
    restart: unless-stopped

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: grain-frontend
    ports:
      - "3000:80"
    depends_on:
      - backend
    restart: unless-stopped

  nginx:
    image: nginx:alpine
    container_name: grain-nginx
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    ports:
      - "80:80"
    depends_on:
      - backend
      - frontend
    restart: unless-stopped

volumes:
  mysql_data:
```

### 4.2 构建并启动

```bash
# 构建并启动所有服务
docker-compose up -d --build

# 查看服务状态
docker-compose ps

# 查看日志
docker-compose logs -f

# 停止服务
docker-compose down
```

### 4.3 Nginx 配置（Docker）

```nginx
events {
    worker_connections 1024;
}

http {
    upstream backend {
        server backend:8080;
    }

    upstream frontend {
        server frontend:80;
    }

    server {
        listen 80;
        server_name localhost;

        location /api/ {
            proxy_pass http://backend/;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }

        location / {
            proxy_pass http://frontend/;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }
    }
}
```

---

## 5. 采集器部署

### 5.1 Python 环境

```bash
# 安装 Python 3.10+
python3 --version

# 创建虚拟环境
python3 -m venv venv
source venv/bin/activate

# 安装依赖
pip install playwright requests
playwright install chromium
```

### 5.2 配置

创建 `.env` 文件：

```bash
API_BASE=http://your-backend:8080/api
TASK_ID=1
LXW_USERNAME=your_username
LXW_PASSWORD=your_password
```

### 5.3 定时任务

```bash
crontab -e

# 每天早上8点执行采集
0 8 * * * cd /opt/python-collector && ./venv/bin/python main.py >> /var/log/collector.log 2>&1
```

---

## 6. 监控与告警

### 6.1 健康检查

```bash
curl http://localhost:8080/api/actuator/health
```

### 6.2 日志位置

| 组件 | 日志位置 |
|------|----------|
| Java 后端 | 应用日志输出到控制台 |
| Nginx | /var/log/nginx/ |
| MySQL | Docker 日志 |

### 6.3 告警配置

支持的通知渠道：
- 邮件（待实现）
- 钉钉（待实现）
- 微信（待实现）

---

## 7. 备份恢复

### 7.1 数据库备份

```bash
# 备份
mysqldump -u grain -p grain_platform > backup_$(date +%Y%m%d).sql

# 恢复
mysql -u grain -p grain_platform < backup_20260428.sql
```

### 7.2 文件备份

```bash
# 备份数据目录
tar -czf data_backup_$(date +%Y%m%d).tar.gz data/
```

---

## 8. 常见问题

### Q: 端口被占用？

```bash
# 查找占用端口的进程
lsof -i :8080

# 或
netstat -tlnp | grep 8080
```

### Q: 内存不足？

```bash
# 增加 JVM 堆内存
java -Xms512m -Xmx2048m -jar grain-platform-1.0.0.jar
```

### Q: 数据库连接失败？

1. 检查 MySQL 是否启动
2. 检查用户名密码是否正确
3. 检查防火墙是否开放 3306 端口
