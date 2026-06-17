# 粮信网日报图片采集、存储与展示设计方案

> **阶段一版本**：采集 → MinIO 存储 → 前端展示。检索向量化保持纯文本 BGE-M3，多模态向量化留到阶段二。

---

## 一、采集器改造（Python / liangxin.py）

### 1.1 处理流程

采集完整文章 HTML 后，执行以下步骤：

1. **提取图片外链**
   - 正则 `<img[^>]+src\s*=\s*['"]([^'"]+)['"]` 匹配所有标准 `<img src>` 标签
   - 过滤仅保留指向第三方域名（非 `localhost`）的外链
   - 去重（本次采集内）
   - **已知限制**：`srcset`、`data-src` 等懒加载属性本次不处理，后续按需扩展

2. **Playwright 下载图片**
   - 复用当前浏览器上下文的 cookie/UA/Referer
   - 单图超时 30 秒
   - 继承页面请求头（User-Agent、Referer）降低防盗链概率
   - 并发控制：最大 5 个并发下载（`ThreadPoolExecutor(max_workers=5)`）

3. **校验图片**
   - 大小上限：`10 * 1024 * 1024` 字节（≈10MB），超限跳过
   - HTTP 响应 `Content-Type` 初筛（`image/jpeg`、`image/png`、`image/webp`）
   - 二进制魔数二次校验（JPEG: `FF D8 FF`、PNG: `89 50 4E 47`、WebP: `52 49 46 46`...）
   - 魔数不匹配跳过并记录

4. **生成 MinIO 存储路径**
   ```
   liangxin/{yyyyMMdd}/{uuid8}_{安全文件名}
   ```
   安全文件名：`re.sub(r'[^\w.-]', '_', 原文件名)`

5. **上传 MinIO**
   - 桶 `knowledge-img` 不存在则自动创建 + 设置公开读权限
   - 附元数据：`Source-Url`（原始外链）、`Collect-Date`（采集日期）、`Script`（liangxin-daily）

6. **替换 HTML 中的图片 URL**
   - 原始 `<img src="原始外链">` → `<img src="MinIO 公开地址">`
   - **未下载成功（超时/403/格式非法）的图片保留原始外链不变**，不阻塞整条入库

7. **临时文件清理**
   - 使用 `tempfile.NamedTemporaryFile(delete=True)` 或 `try/finally` 确保图片临时文件始终被删除

### 1.2 日志与统计

每次批量图片处理完成后输出一行结构化日志：

```json
{
  "total": 5,            // 待处理图片总数
  "success": 4,          // 成功下载+上传
  "failed": 1,           // 失败数
  "failures": [
    {"url": "...", "reason": "timeout/403/format/too_large"}
  ],
  "imageCount": 4        // 上报用
}
```

### 1.3 异常兜底

- 单图失败 → 保留原始外链，继续处理下一张
- 全部失败 → `imageCount=0`，HTML 维持原始外链，日志报警
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

### 2.2 桶初始化（Python 端自动完成）

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
    s3.put_bucket_policy(Bucket=bucket_name, Policy=公开读策略JSON)
```

### 2.3 存储路径规范

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

**零改动**。`KnowledgeBase.contentHtml` 和 `ExecutionItem.imageCount` 字段已存在，直接入库。

---

## 四、前端展示

**零改动**。

- `Knowledge.vue:487` 已用 `v-html="contentHtml"` 渲染 HTML 内容
- `.content-html img` CSS 样式已存在，支持图片自适应
- MinIO 公开 URL 前端浏览器直连加载
- 失败（保留原始外链）的图片浏览器按常规方式请求，该裂图则裂图，不特殊处理

---

## 五、向量化说明（阶段二）

**阶段一不做多模态向量化。原因：**
- 本地开发环境 MinIO 图片无法被 DashScope API 服务器访问
- 日报文章纯文本已能支持语义检索
- 可视化图谱（`embedVisualization`）目前也暂不接入 MinIO 图片

**阶段二需要解决的问题：**
- 内网穿透或公网 MinIO 域名，使图片 URL 可被 DashScope 访问
- 或在 Java 端增加 MinIO → base64 转换层

---

## 六、不需要的功能（明确排除）

- ❌ 知识库内容修改/删除时同步清理 MinIO 图片（阶段性的低频垃圾，手动或定时任务清理即可）
- ❌ 前端图片懒加载优化（依赖浏览器原生 loading="lazy"）
- ❌ 图片裁剪/缩略图/WebP 自动转换
