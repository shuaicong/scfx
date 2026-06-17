# 粮信网日报图片采集、存储与展示设计方案

> **阶段一版本**：采集 → MinIO 存储 → 前端展示。检索向量化保持纯文本 BGE-M3，多模态向量化留到阶段二。

---

## 一、采集器改造（Python / liangxin.py）

### 1.1 提取图片外链

- 正则 `<img[^>]+src\s*=\s*['"]?([^\s'">]+)['"]?` 匹配 `<img src>` 标签，兼容无引号 `src=xxx` 格式
- **只匹配白名单域名 `cms.jinnong.cn`** 来源图片，防止第三方广告外链被批量下载
- **去重（本次采集内）**：基于原始 URL 做 MD5 缓存键，同一 URL 只下载上传一次。仅单次采集任务内内存级去重，不做全局跨任务去重
- 若后续大量重复素材频繁采集，可基于 `md5(source_url)` 做 Redis 全局缓存，本次暂不实现
- **已知限制**：`srcset`、`data-src` 等懒加载属性本次不处理，后续按需扩展

### 1.2 Playwright 下载图片

- 复用当前浏览器上下文的 cookie/UA/Referer
- 单图超时 30 秒
- 继承页面请求头（User-Agent、Referer）降低防盗链概率
- **并发控制**：`ThreadPoolExecutor(max_workers=5)`，最大 5 个并发
- **全局总超时**：120 秒，超时后未完成的图片全部记为失败，进入下一流程

### 1.3 校验图片

- 大小上限：`10 * 1024 * 1024` 字节（≈10MB），超限跳过
- HTTP 响应 `Content-Type` 初筛（`image/jpeg`、`image/png`、`image/webp`）
- **二进制魔数二次校验**（MIME 与魔数必须双向匹配）：
  - JPEG: `FF D8 FF`
  - PNG: `89 50 4E 47`
  - WebP: `52 49 46 46` + `WEBP`
- 魔数不匹配或不一致跳过并记录
- **HTTPS 校验**：如果源站图片使用 HTTP 协议，可配置是否允许拉取，防止中间人恶意资源注入（默认允许）

### 1.4 生成 MinIO 存储路径

```
liangxin/{yyyyMMdd}/{uuid8}_{安全文件名}
```

安全文件名：中文、特殊符号（`# & / \ ? *` 等）统一替换为下划线，仅保留字母、数字、点、短横线、下划线：
```python
re.sub(r'[^\w.-]', '_', 原文件名)
```

### 1.5 上传 MinIO

- 桶 `knowledge-img` 不存在则自动创建 + 设置公开读权限
- 附元数据：`Source-Url`（原始外链）、`Collect-Date`（采集日期）、`Script`（liangxin-daily）

### 1.6 替换 HTML 中的图片 URL

- 原始 `<img src="原始外链">` → `<img src="MinIO 公开地址">`
- **未下载成功（超时/403/格式非法）的图片保留原始外链不变**，不阻塞整条入库

### 1.7 临时文件清理

- 使用 `tempfile.NamedTemporaryFile(delete=True)` 或 `try/finally` 确保图片临时文件始终被删除
- **进程异常崩溃场景**：残留临时文件，建议系统层面添加定时清理 `/tmp` 目录的脚本

### 1.8 MinIO 不可用降级

任何 MinIO 操作（连接失败、桶创建权限不足、上传超时）触发降级：
- 放弃所有图片处理
- `imageCount=0`，保留原始 HTML
- 不阻断知识库入库
- 输出 ERROR 告警日志

### 1.9 日志与统计

日志包含结构化信息，方便链路追踪：

```json
{
  "total": 5,              // 待处理图片总数
  "success": 4,            // 成功下载+上传
  "failed": 1,             // 失败数
  "failures": [
    {"url": "...", "reason": "timeout/403/format/too_large"}
  ],
  "imageCount": 4,         // 上报用
  "knowledgeId": 123,      // 关联知识库 ID
  "executionId": "..."     // 关联执行 ID
}
```

### 1.10 异常兜底

- 单图失败 → 保留原始外链，继续处理下一张
- 全局 120 秒超时 → 未完成标记失败
- 全部失败 → `imageCount=0`，HTML 维持原始外链，告警
- MinIO 不可用 → 降级跳过图片处理，不阻塞入库
- 任何异常不阻断上游文章入库流程

---

## 二、MinIO 侧配置

### 2.1 Python 端配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `MINIO_ENDPOINT` | `http://localhost:9000` | MinIO 服务地址 |
| `MINIO_ACCESS_KEY` | `admin` | Access Key |
| `MINIO_SECRET_KEY` | `password` | Secret Key |
| `MINIO_BUCKET` | `knowledge-img` | 图片存储桶 |
| `MINIO_REGION` | `us-east-1` | S3 兼容区域 |
| `IMAGE_MAX_BYTES` | `10 * 1024 * 1024` | 单图大小上限 |
| `ALLOW_HTTP_SOURCE` | `true` | 是否允许拉取 HTTP 源站图片 |

### 2.2 桶初始化与安全策略

Python 自动创建桶后，同步执行以下配置。

**最小权限公开读策略（仅允许匿名读取文件）：**

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Principal": "*",
            "Action": "s3:GetObject",
            "Resource": "arn:aws:s3:::knowledge-img/*"
        }
    ]
}
```

约束说明：仅允许匿名读取文件，**禁止**删除、上传、修改桶配置、列举文件。

**CORS 跨域配置：**

允许前端业务域名跨域访问图片资源：

```json
{
    "CORSRules": [
        {
            "AllowedOrigins": ["http://localhost:3000", "http://localhost:9528"],
            "AllowedMethods": ["GET"],
            "AllowedHeaders": ["*"]
        }
    ]
}
```

多环境部署时需添加开发、测试、生产所有前端域名到 `AllowedOrigins`。

### 2.3 桶初始化代码

```python
s3 = boto3.client('s3',
    endpoint_url=minio_endpoint,
    aws_access_key_id=access_key,
    aws_secret_access_key=secret_key,
    config=Config(signature_version='s3v4'),
)

# 桶不存在则自动创建
try:
    s3.head_bucket(Bucket=bucket_name)
except ClientError:
    s3.create_bucket(Bucket=bucket_name)
    # 最小权限公开读策略
    s3.put_bucket_policy(Bucket=bucket_name, Policy=公开读策略JSON)
    # CORS 配置
    s3.put_bucket_cors(Bucket=bucket_name, CORSConfiguration=CORS规则JSON)
```

### 2.4 存储路径规范

```
knowledge-img/
  liangxin/
    20260616/
      a1b2c3d4_384.png
      e5f6g7h8_567.jpg
    20260617/
      ...
```

---

## 三、上报与存储（reporter.py / Java 端）

### 3.1 reporter.py 改动

| 字段 | 当前 | 改动后 |
|------|------|--------|
| `contentHtml` | 原始 HTML（含外链图片） | 替换后 HTML（含 MinIO 本地链接） |
| `imageCount` | 无 | 新增，传成功替换的图片数 |

### 3.2 Java 后端

**知识库入库：零改动。** `KnowledgeBase.contentHtml` 和 `ExecutionItem.imageCount` 字段已存在，直接入库。

**新增：图片明细表（`t_knowledge_image`）— 用于知识库删除时同步清理 MinIO 图片。**

```sql
CREATE TABLE `t_knowledge_image` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `knowledge_id` bigint NOT NULL COMMENT '关联知识库ID',
  `source_url` varchar(2048) NOT NULL COMMENT '原始图片URL',
  `minio_path` varchar(512) NOT NULL COMMENT 'MinIO对象路径（相对桶路径）',
  `minio_bucket` varchar(128) NOT NULL DEFAULT 'knowledge-img' COMMENT 'MinIO桶名称',
  `file_size` int NOT NULL DEFAULT 0 COMMENT '文件大小（字节）',
  `file_type` varchar(32) NOT NULL DEFAULT '' COMMENT '文件类型 image/jpeg',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_id` (`knowledge_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库图片明细';
```

**删除知识库时的清理流程：**

```
删除知识库请求
  → 查询 t_knowledge_image WHERE knowledge_id = ?
  → 遍历 MinIO 删除每个对象的路径（保留桶结构和空目录）
  → 物理删除  t_knowledge_image 记录
  → 物理/逻辑删除知识库
```

**删除性能说明：**
- 本次采用**同步删除**方案。适用于日报单篇图片数量少的场景（通常 ≤10 张），删除请求在接口线程内同步完成，简单可靠
- 若后续单篇图片几十张、批量删除多条知识库，建议迭代改为**异步任务批量删除**，避免接口响应超时

---

## 四、前端展示

**零改动。**

- `Knowledge.vue:487` 已用 `v-html="contentHtml"` 渲染 HTML 内容
- `.content-html img` CSS 样式已存在，支持图片自适应
- MinIO 公开 URL 前端浏览器直连加载
- 失败（保留原始外链）的图片浏览器按常规方式请求，该裂图则裂图，不特殊处理

---

## 五、向量化说明（阶段二）

**阶段一不做多模态向量化。原因：**
- 本地开发环境 MinIO 图片无法被 DashScope API 服务器访问
- 日报文章纯文本已能支持语义检索

**可视化图谱兜底：**
- `embedVisualization` 检测到 `<img src="内网 MinIO 地址">` 时，DashScope API 请求会超时/失败
- **必须捕获异常自动降级为纯文本向量化**（仅传 `kb.getContent()`），避免该知识条目整体向量化失败
- 降级逻辑已内置在 `VectorClientRouter.embedVisualization()` 中，但需确认异常能被正确捕获

**阶段二需要解决的问题：**
- 内网穿透或公网 MinIO 域名，使图片 URL 可被 DashScope 访问
- 或在 Java 端增加 MinIO → base64 转换层

---

## 六、不需要的功能（明确排除）

- ❌ 图片水印、资源防盗签名 URL（MinIO 公开桶已足够，不做 PreSignedURL）
- ❌ 图片压缩、格式转码，仅原样上传源文件
- ❌ 前端图片懒加载优化（依赖浏览器原生 `loading="lazy"`）
- ❌ 图片裁剪/缩略图/WebP 自动转换
