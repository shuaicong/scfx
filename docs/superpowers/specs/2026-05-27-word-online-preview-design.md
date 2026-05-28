# Word 文档在线预览

> **For agentic workers:** Implement task-by-task after user approval.

**Goal:** 上传 .docx 文件后能在浏览器中查看带格式的 HTML 预览，保留标题、列表、表格、图片等排版

**Architecture:** 前端 mammoth.js 解析 .docx → HTML + base64 内嵌图片（含尺寸压缩），上传时附带 `contentHtml` 字段，后端存原始文件 + 创建 KnowledgeBase 记录，预览面板已支持 `v-html` 渲染

**Tech Stack:** mammoth.js (frontend), Spring Boot multipart upload, Canvas API (image compression)

---

## 背景

现有系统已支持 `contentHtml` 字段和预览渲染，但缺失 Word 文档处理：

| 环节 | 现状 |
|------|------|
| `contentHtml` 字段 | 数据库 `t_knowledge_base.content_html`、Entity `contentHtml`、预览面板 `v-html` 均已存在 |
| 预览面板 | `class="content-html" v-html="currentPreview.contentHtml"` — 已就绪 |
| Collector 模式 | Python 端已生成 contentHtml，CollectorController 已存储 |
| 上传模式 | **后端 `/knowledge/upload` 是空壳，不处理文件、不创建知识记录** |

## 流程

```
用户选 .docx
  ├── 校验后缀（拒绝 .doc / 其他格式）
  ├── 校验大小（≤20MB）
  ├── mammoth.convertToHtml({ arrayBuffer, convertImage })
  │    └── 每张图片：
  │         ├── ≤5MB → 原样 base64 内嵌
  │         ├── >5MB → Canvas 压缩到 1920px 宽、quality 0.8
  │         └── base64 >2MB → 跳过该图片（console.warn）
  ├── FormData: file + categoryId + title + idempotentKey + contentHtml
  └── POST /knowledge/upload (multipart)
       ├── 校验文件后缀（拒绝 .doc）
       ├── 存原始文件到 data/upload/{categoryId}/{id}.docx
       ├── 创建 KnowledgeBase(contentHtml=...)
       └── 触发异步向量化 vectorTaskService.processSingle()
```

## 改动清单

### 1. 前端依赖

```bash
npm install mammoth buffer
```

`main.js` 引入 Buffer polyfill（mammoth.js 需要）：

```typescript
import * as buffer from 'buffer'
window.Buffer = buffer.Buffer
```

### 2. 图片压缩 + 工具函数

```typescript
// 前端: src/utils/image-utils.ts

/** ArrayBuffer 转 Base64 DataURI */
function arrayBufferToBase64(buffer: ArrayBuffer): string {
  let binary = ''
  const bytes = new Uint8Array(buffer)
  const len = bytes.byteLength
  for (let i = 0; i < len; i++) {
    binary += String.fromCharCode(bytes[i])
  }
  return 'data:image;base64,' + window.btoa(binary)
}

/** 压缩图片：>5MB 缩到 1920px 宽、quality 0.8，base64 >2MB 丢弃 */
async function compressImage(arrayBuffer: ArrayBuffer): Promise<string | null> {
  const bytes = arrayBuffer.byteLength

  // ≤5MB 直接转 base64
  if (bytes <= 5 * 1024 * 1024) {
    return arrayBufferToBase64(arrayBuffer)
  }

  // >5MB → Canvas 压缩后转 JPEG base64
  try {
    const blob = new Blob([arrayBuffer])
    const url = URL.createObjectURL(blob)
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const i = new Image()
      i.onload = () => resolve(i)
      i.onerror = () => reject(new Error('image decode failed'))
      i.src = url
    })
    URL.revokeObjectURL(url)

    const canvas = document.createElement('canvas')
    let { width, height } = img
    if (width > 1920) {
      height = Math.round(height * (1920 / width))
      width = 1920
    }
    canvas.width = width
    canvas.height = height
    const ctx = canvas.getContext('2d')!
    ctx.drawImage(img, 0, 0, width, height)

    const compressed = canvas.toDataURL('image/jpeg', 0.8)
    if (compressed.length > 2 * 1024 * 1024) {
      console.warn(`image still ${(compressed.length / 1024 / 1024).toFixed(1)}MB after compress, skipping`)
      return null
    }
    return compressed
  } catch (e) {
    console.warn('image compression failed, skipping', e)
    return null
  }
}
```

### 3. mammoth 集成

```typescript
async function parseDocxToHtml(file: File): Promise<string> {
  try {
    const arrayBuffer = await file.arrayBuffer()
    const result = await mammoth.convertToHtml({
      arrayBuffer,
      convertImage: mammoth.images.imgElement(async (image) => {
        const content = await image.read() // ArrayBuffer
        const src = await compressImage(content)
        if (!src) return {} // 跳过该图片
        return { src }
      }),
    })
    return result.value
  } catch (e) {
    console.error('docx parse failed', e)
    return '' // 降级：后端走纯文本提取
  }
}
```

### 4. upload 对话框修改

```
用户选文件 → @change="onFileChange"
  ├── 校验 file.name 后缀（非 .docx → 警告，不阻止）
  ├── 校验 file.size（>20MB → 拒绝）
  ├── 是 .docx → 调用 parseDocxToHtml → 存 uploadForm.contentHtml
  └── 不是 → uploadForm.contentHtml = ''
```

handleUpload 中 contentHtml 加入 FormData：

```typescript
if (uploadForm.contentHtml) {
  fd.append('contentHtml', uploadForm.contentHtml)
}
```

### 5. 后端 upload 端点重写

```java
@PostMapping("/upload")
public Result<KnowledgeBase> upload(
        @RequestParam("file") MultipartFile file,
        @RequestParam("categoryId") Long categoryId,
        @RequestParam("title") String title,
        @RequestParam(value = "contentHtml", required = false) String contentHtml,
        @RequestParam(value = "idempotentKey", required = false) String idempotentKey) {

    // 后端二次校验：文件大小
    long maxSize = 20L * 1024 * 1024;
    if (file.getSize() > maxSize) {
        return Result.error("文件大小不能超过20MB");
    }

    // 后端二次校验：后缀（拒绝 .doc，仅 .docx）
    String filename = file.getOriginalFilename();
    if (filename == null) {
        return Result.error("文件名称不能为空");
    }
    String lowerName = filename.toLowerCase();
    if (!lowerName.endsWith(".docx")) {
        if (lowerName.endsWith(".doc")) {
            return Result.error("请上传 .docx 格式，旧版 .doc 暂不支持");
        }
        return Result.error("仅支持 .docx 格式文档");
    }

    try {
        KnowledgeBase kb = knowledgeBaseService.createFromUpload(file, categoryId, title, contentHtml);
        vectorTaskService.processSingle(kb.getId());
        return Result.success(kb);
    } catch (Exception e) {
        log.error("上传失败: {}", e.getMessage());
        return Result.error("上传失败: " + e.getMessage());
    }
}
```

### 6. XSS 防护 — jsoup HTML 清洗

**风险：** `v-html` 渲染 mammoth 输出的 HTML，文档内可嵌入 `<script>`、`onclick`、`onerror` 等恶意内容。

**方案：** 后端接收 contentHtml 后，用 jsoup 白名单过滤。

**pom.xml 新增依赖：**

```xml
<!-- HTML 防 XSS、标签过滤 -->
<dependency>
    <groupId>org.jsoup</groupId>
    <artifactId>jsoup</artifactId>
    <version>1.17.2</version>
</dependency>
```

**工具类：**

```java
package com.scfx.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public class HtmlSafeUtil {
    // 自定义白名单：仅保留文档排版标签，禁止 script、onclick、iframe 等
    private static final Safelist KB_SAFE_LIST = Safelist.relaxed()
            .addTags("h1","h2","h3","h4","h5","h6","table","tr","td","th")
            .addAttributes("img", "src")
            .removeAttributes(":all", "onclick", "onload", "onerror", "onmouseover",
                    "style", "class", "id");

    public static String clean(String html) {
        if (html == null || html.isBlank()) return "";
        return Jsoup.clean(html, KB_SAFE_LIST);
    }
}
```

在 `createFromUpload` 中调用清洗：

```java
kb.setContentHtml(HtmlSafeUtil.clean(contentHtml));
```

### 7. FileStorageService

```java
@Service
public class FileStorageService {
    @Value("${app.upload.dir:data/upload}")
    private String uploadDir;

    public String store(MultipartFile file, Long categoryId, Long knowledgeId) throws IOException {
        String dir = uploadDir + "/" + categoryId;
        Path dirPath = Path.of(dir);
        Files.createDirectories(dirPath);
        String fileName = knowledgeId + ".docx";
        Path targetPath = dirPath.resolve(fileName);
        file.transferTo(targetPath);
        return targetPath.toString();
    }
}
```

### 8. KnowledgeBaseService.createFromUpload

```java
@Transactional(rollbackFor = Exception.class)
public KnowledgeBase createFromUpload(MultipartFile file, Long categoryId, String title, String contentHtml) throws IOException {
    // 1. 先保存 DB 记录（含 XSS 清洗后的 contentHtml）
    KnowledgeBase kb = new KnowledgeBase();
    kb.setTitle(title);
    kb.setCategoryId(categoryId);
    kb.setSourceType("upload");
    kb.setContent("");  // 占位，后续 pipeline 异步提取
    kb.setContentHtml(HtmlSafeUtil.clean(contentHtml));
    kb.setVectorStatus("pending");
    save(kb);

    try {
        // 2. 存储文件
        String filePath = fileStorageService.store(file, categoryId, kb.getId());
        kb.setFilePath(filePath);
        updateById(kb);
    } catch (IOException e) {
        // 文件写入失败：删除已创建的 DB 记录，避免脏数据
        removeById(kb.getId());
        throw new IOException("文件存储失败，请检查磁盘权限", e);
    }

    return kb;
}
```

### 9. application.yml

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 20MB

app:
  upload:
    dir: data/upload  # 建议生产环境用绝对路径
```

## 降级策略

| 场景 | 行为 |
|------|------|
| 非 .docx 文件 | 正常上传，不解析 HTML，contentHtml='' |
| .doc 文件 | 弹窗警告「请上传 .docx 格式」 |
| mammoth 解析失败 | 静默降级，contentHtml=''，后端异步走纯文本提取 |
| 单图压缩失败 | 跳过该图片，不影响整体 |
| 所有图片均失败 | contentHtml 含纯文本 HTML，无图片 |
| Canvas/Blob 不可用（老旧浏览器） | 所有 base64 原样保留（不压缩，但控制台有 warn） |
| file.arrayBuffer() 不支持 | parseDocxToHtml catch 返回空，走后端纯文本 |
| 后端文件存储失败 | 事务回滚 + 删除已创建的 DB 记录，不产生脏数据 |

## 安全性

- **XSS 防护**：contentHtml 入库前用 jsoup 白名单清洗（`HtmlSafeUtil.clean`），只保留排版标签，剔除 script/事件/属性
- **文件扩展名校验**：前后端双重校验，仅接受 `.docx`
- **文件大小限制**：前后端双重校验 ≤20MB
- **文件路径安全**：使用 categoryId/knowledgeId 而非用户输入，防路径遍历
- **Spring multipart 限制**：配置 `max-file-size` + `max-request-size`

## 一、需确认 & 补充的风险点

### 1.1 临时文件 / 残留文件清理

大文件、中断上传、异常请求可能产生残留文件。需要最低限度的运维策略：
- 建议在 `application.yml` 中配置统一的 upload 目录
- 定期清理 7 天以上未关联知识记录的孤立文件（通过 crontab 或定时任务扫描目录对比知识库记录）

### 1.2 Base64 超大 HTML 兼容

多图文档的 contentHtml 可能很长，需确认：
- `t_knowledge_base.content_html` 字段类型为 `TEXT`（当前 schema 已使用 `TEXT`，通过）
- Spring 请求体大小已配 20MB，覆盖 base64 膨胀场景

### 1.3 异步向量化异常监控

`vectorTaskService.processSingle(kb.getId())` 是异步任务，不会阻塞上传响应。需补充：
- 任务失败日志
- 失败时应有补偿机制（例如管理后台重试入口）

## 降级策略

## 执行顺序

1. `npm install mammoth buffer` + main.js Buffer polyfill
2. 新增 `src/utils/image-utils.ts`（arrayBufferToBase64 + compressImage）
3. 新增 `src/utils/docx-utils.ts`（parseDocxToHtml）
4. 修改 handleUpload（检测 .docx → 解析 → 传 contentHtml）
5. 修改 uploadDocument API（FormData 加 contentHtml 字段）
6. pom.xml 新增 jsoup 依赖
7. 新增 `HtmlSafeUtil.java`（XSS 清洗）
8. 新增 `FileStorageService.java`
9. 修改 `KnowledgeBaseService.createFromUpload`（含清洗 + 异常回滚）
10. 重写 `KnowledgeBaseController.upload`（含双重校验）
11. application.yml 配置 multipart + upload.dir
12. 同步到 worktree + 编译 + 重启 + 测试
