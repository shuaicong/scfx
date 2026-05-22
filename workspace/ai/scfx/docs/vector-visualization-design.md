# 向量可视化方案设计

## 1. 背景

知识库当前同时使用两种向量模型：

| 服务 | 模型 | 维度 | 场景 | 费用 |
|------|------|------|------|------|
| 硅基流动 | BGE-M3 | 1024 | 文本检索 | 免费 |
| 阿里云 DashScope | 通义多模态向量 Flash | 768 | 图文混合 | ¥0.00015/1K tokens |

如果只用其中一个模型做可视化，会面临选择困境：

- **只用 BGE-M3**：无法处理含图片的知识，且不适合多模态场景
- **只用 DashScope**：检索场景成本高（每次搜索调用都要花钱）
- **混用**：两个模型的向量空间不同（1024 vs 768），无法直接降维可视化

## 2. 方案选择

### 方案 A：双向量入库（选定方案）

入库时同时计算两个向量，各司其职：

- **BGE-M3 1024d** → 存 `vector_id`（检索用，免费）
- **DashScope 768d** → 存 `t_knowledge_viz` 表（可视化用，统一空间）

**优点**：
- 检索保持免费，不增加额外成本
- 可视化空间统一（全是 768 维）
- 前端读 x/y 坐标，零延迟

**代价**：
- 入库时多一次 API 调用（~200ms/条，后台异步可接受）
- 需存储 768 维向量（~3KB/条，万条约 30MB）

### 方案 B：仅用 DashScope 做所有事

检索和可视化全都走 DashScope 768d。

**优点**：架构简单，只需维护一个模型。

**缺点**：每次搜索都产生费用（¥0.00015/1K tokens），失去了 BGE-M3 的免费额度。

### 方案 C：可视化时实时调用 API

只存 BGE-M3 vector_id，打开可视化页面时临时调 DashScope 接口获取 768d 向量再降维。

**优点**：不多占存储。

**缺点**：打开可视化页面要等所有 API 返回，延迟不可控，且每次打开都产生费用。

### 结论：选方案 A

双向量入库的额外成本可接受（一次入库多 200ms + 3KB 存储），换来检索免费 + 前端零延迟。

## 3. 架构设计

```
VectorTaskServiceImpl.processVectorization(kb)
  │
  ├─ 1. vectorClient.embed()           → BGE-M3 1024d → 存 vector_id
  │                                             （检索用）
  │
  ├─ 2. vectorClient.embedVisualization() → DashScope 768d → 存 t_knowledge_viz
  │                                             （可视化用）
  │
  └─ 3. 批次处理完毕 → recomputePCA(categoryId)
                    ↓
               t_knowledge_base.viz_x
               t_knowledge_base.viz_y
```

### 3.1 VectorClient 接口

```java
interface VectorClient {
    // BGE-M3 文本向量化（检索用）
    VectorResult embed(String text);
    
    // DashScope 768d 向量化（可视化用，统一空间）
    default VectorResult embedVisualization(String text, String contentHtml, Long knowledgeId) {
        return embed(text, contentHtml, knowledgeId);
    }
}
```

### 3.2 VectorClientRouter 路由

```java
@Component @Primary
class VectorClientRouter {
    embed()                   → SiliconFlowEmbeddingClient (BGE-M3)
    embedVisualization()      → DashScopeMultimodalEmbeddingClient (768d, 恒走)
}
```

### 3.3 异常处理

- **BGE-M3 失败** → 整个条目标记为 `failed`（检索是所有功能的前提）
- **DashScope 失败** → 仅 log warning，主流程继续（只是少了该条目的可视化点）

### 3.4 异步解耦写入链路

入库写两个向量时，BGE-M3 和 DashScope 的耗时不同，阻塞方式也不同：

| 步骤 | 耗时 | 阻塞方式 |
|------|------|----------|
| BGE-M3 （检索向量） | ~200ms | **同步阻塞** — 入库线程等结果，因为检索是核心功能 |
| DashScope（可视化向量） | ~200ms | **异步** — 抛线程池处理，不阻塞入库 |
| PCA 降维 | ~100ms（全量） | **异步** — 批次处理完毕后触发 |

**流程**：

```
入库请求
  │
  ├─ 同步: BGE-M3 API 调用 → 存 vector_id → 返回入库成功
  │
  └─ 异步（@Async）:
       ├─ DashScope API 调用 → 存 t_knowledge_viz
       └─ 批次结束 → recomputePCA()
```

**好处**：
- 入库接口响应时间不受可视化链路影响
- DashScope 偶尔超时不拖慢入库
- PCA 在后台安静计算，用户无感知

### 3.5 失败重试与降级策略

DashScope API 调用可能因网络抖动、限流、服务端错误而失败。分级处理：

**重试机制**：

| 异常类型 | 行为 | 重试间隔 |
|----------|------|----------|
| 网络超时 / 5xx | 最多重试 3 次 | 指数退避：2s → 4s → 8s |
| 429 限流（Rate Limit） | 标记降级，暂存任务 | 下次批次触发时补偿执行 |
| 4xx 客户端错误（如鉴权失败） | 不重试，立即标记 failed | — |

**降级策略**：

```
DashScope 调用失败
  ├─ 重试次数内成功 → 正常写入 viz 记录
  ├─ 重试耗尽 → 标记 viz_status = failed
  │    ├─ 已有旧向量 → 保留，仅改状态
  │    └─ 首次向量化 → 无向量数据，下次重试补偿
  └─ 429 限流 → 跳过本次，记录到待补偿队列
       └─ 下次 triggerCategory 时自动重试所有 failed 条目
```

**补偿执行**：
- `viz_status = failed` 的条目会在下次批量处理时自动重试
- 用户也可手动触发单条 revectorize 补偿
- 全量重算 PCA 时，也会重新处理所有已存在的向量

## 4. 存储设计

### 4.1 t_knowledge_base（已有表，新增列）

```sql
ALTER TABLE t_knowledge_base
  ADD COLUMN viz_x DOUBLE NULL COMMENT 'PCA 降维 X 坐标',
  ADD COLUMN viz_y DOUBLE NULL COMMENT 'PCA 降维 Y 坐标';
```

这两列在 PCA 计算后写入，前端直接读取，无需 join。

### 4.2 t_knowledge_viz（新增表）

```sql
CREATE TABLE t_knowledge_viz (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    knowledge_id  BIGINT NOT NULL,          -- FK → t_knowledge_base
    vector_768    VARBINARY(3072) NULL,     -- DashScope 768 维向量（3072 字节固定长度）
    viz_status    VARCHAR(20) DEFAULT 'pending',  -- 可视化状态：vectorized / failed / pending
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP,
    UNIQUE INDEX idx_viz_knowledge (knowledge_id)  -- 每条知识至多一条可视化向量
);
```

**设计要点**：

| 改动 | 原因 |
|------|------|
| `category_id` → 移除，联表查询 | 通过 `t_knowledge_base.category_id` JOIN 获取，减少冗余字段维护成本 |
| `MEDIUMBLOB` → `VARBINARY(3072)` | 768 维 float 固定 3072 字节，固定长度减少存储碎片 |
| 新增 `viz_status` 枚举 | `vectorized`/`failed`/`pending`，前端可直接按状态区分样式，也便于排查异常数据 |
| `knowledge_id` 唯一索引 | 防止同一条知识重复生成多条可视化向量，杜绝脏数据 |

**TypeHandler 校验**：`FloatArrayTypeHandler` 写入时检查数组是否为 768 维，不足补 0，超长截断；读取时校验字节长度是否为 3072。

**为什么不用 Qdrant？** Qdrant 容器当前 unhealthy，MySQL BLOB 够用（3072 字节/条）。将来数据量 > 10 万条或需要用 768 向量做检索时，再同步到 Qdrant，MySQL 只存元数据和向量 ID。

## 5. PCA 降维

### 5.1 算法

使用**幂迭代**（Power Iteration）求前 2 个主成分，不依赖第三方 ML 库：

```
1. 中心化: X_centered = X - μ
2. 求 PC1: v₁ = powerIteration(X_centered, deflation=null)
3. 求 PC2: v₂ = powerIteration(X_centered, deflation=v₁)  // Gram-Schmidt 正交化
4. 投影: x_i = X_centered[i] · v₁,  y_i = X_centered[i] · v₂
```

为避免显式构造 768×768 协方差矩阵，每次迭代计算 `C*v = Xᵀ(X*v)/N`：

```
temp    = X * v        (N × 1, O(N·D))
v_new   = Xᵀ * temp    (D × 1, O(N·D))
v_new  /= N
```

每轮迭代 O(N·D)，100 轮收敛，1000 条 × 768 维 ≈ 77M 次浮点运算，< 100ms。

### 5.3 归一化

投影后 min-max 归一化到 [-1, 1]，保证不同分类坐标范围一致：

```
x[i] = -1 + 2 * (x[i] - x_min) / (x_max - x_min)
y[i] = -1 + 2 * (y[i] - y_min) / (y_max - y_min)
```

极值相同时坐标置 0。归一化写入 `viz_x` / `viz_y`，前端直接读取。

### 5.4 增量计算 + 基线缓存

**问题**：每次批量入库后全量重算 PCA（O(N·D²)），随数据量增长越来越慢。

**优化**：缓存 PCA 基线（均值向量 μ、主成分 v₁/v₂、归一化边界），新增数据直接投影复用。

```
首次批量: fullPCA() → 保存基线到 t_pca_baseline
后续批量: incrementalPCA() → 新向量 id > baseline.lastVizId?
  ├─ 是: 中心化 → 投影到缓存主成分 → 扩展归一化边界 → 写坐标
  └─ 否: 跳过
手动重算: recomputePCAFull() → 全量重算，刷新基线
```

**t_pca_baseline 表结构**：

```sql
CREATE TABLE t_pca_baseline (
    category_id   BIGINT PRIMARY KEY,
    last_viz_id   BIGINT NOT NULL DEFAULT 0,    -- 已处理的 max viz id
    vector_count  INT NOT NULL DEFAULT 0,
    mean_vector   VARBINARY(3072),              -- 均值向量
    pc1           VARBINARY(3072),              -- 第一主成分
    pc2           VARBINARY(3072),              -- 第二主成分
    x_min/x_max   DOUBLE,                       -- 归一化边界
    y_min/y_max   DOUBLE,
    version       INT DEFAULT 1,
    created_at    TIMESTAMP,
    updated_at    TIMESTAMP
);
```

**增量投影流程**：

1. 查询 `t_knowledge_viz` 中 `id > last_viz_id` 的向量
2. 对每个新向量做中心化：`x_centered = v - μ`
3. 投影：`px = dot(x_centered, v₁)`, `py = dot(x_centered, v₂)`
4. 若原始投影值超出当前边界，扩展边界
5. 归一化到 [-1, 1]，写 viz_x/viz_y
6. 更新 baseline：last_viz_id、vector_count、扩展后的边界

**复杂度对比**：

| 模式 | 全量 | 增量 |
|------|------|------|
| 计算量 | O(N·D·iter) | O(M·D) |
| 1000 条 × 768 维 | ~77M ops | — |
| 追加 10 条 | ~77M ops（全量） | ~7.7K ops（仅 10 条） |

增量模式下，旧点不受影响，新点融入同一空间。

### 5.5 迭代参数可配置

```yaml
pca:
  max-iterations: 100           # 幂迭代收敛轮数（小数据少迭代提速）
  convergence-threshold: 1.0E-6 # 精度阈值（大数据多迭代保证精度）
```

### 5.6 触发时机

| 场景 | 方式 | 模式 |
|------|------|------|
| 单条重新向量化 | `processSingle(kb.id)` | **不触 PCA** — 仅更新该条 `vector_768`，标注脏数据 |
| 批量入库 | `triggerCategory()` → `recomputePCA()` | **增量** — 投影新向量，复用基线 |
| 脏数据累计 N 条 | 自动触发增量 PCA（可选） | **增量** — 批量刷新脏点坐标 |
| 用户手动重算 | `POST /knowledge/{categoryId}/visualization/recompute` | **全量** — 清理基线，重新构建 |

**单条更新兜底逻辑**：

```
用户触发单条 revectorize
  │
  ├─ 1. 计算 BGE-M3 新向量 → 更新 vector_id
  ├─ 2. 计算 DashScope 新向量 → 更新 t_knowledge_viz
  ├─ 3. 仅写该条自身 viz_x / viz_y
  │     └─ 使用当前分类的 PCA 基线投影（若基线不存在则不写）
  └─ 4. 标注该条为脏数据（递增分类脏数据计数器）
        └─ 下次批量入库 → 检测脏数据 → 增量 PCA 刷新全局
```

**脏数据计数器**：
- 每个分类维护一个内存计数器 `dirtyCount[categoryId]`
- 单条更新 +1，批量 PCA 后归零
- 达到阈值（可选配置，如 20 条）可自动触发增量 PCA
- 不持久化，服务重启后丢失（下次批量会覆盖）

**设计原则**：
- **单条变化不移动全局点**：用户看到散点图不会因一次更新而位移
- **批量归并刷新**：积累的脏数据在下次批量入库时统一刷新
- **手动全量**：用户主动要求时清理基线重新构建，解决所有累积偏移
- **计算成本可控**：单条 O(D) 投影 + O(1) 写库，不触发 PCA 迭代

### 5.7 计算范围

按 categoryId 分组降维，每个分类内独立计算。同一分类下的知识才有可比性，跨分类的向量在检索时本就不在同一个语义空间。

## 6. API 设计

```
GET  /knowledge/{categoryId}/visualization
  ?page=1 & size=200 & sample=false
  → {
      points: [{ id, title, x, y, vectorStatus, vizStatus, contentType }],
      total: 500,
      page: 1,
      size: 200,
      sample: false,
      similarities: {
        "123": [{ id: 456, score: 0.92 }, { id: 789, score: 0.87 }, ...],
        ...
      }
    }

POST /knowledge/{categoryId}/visualization/recompute
  → { success: true }
```

### 6.1 分页与抽样

| 参数 | 类型 | 说明 |
|------|------|------|
| `page` | int | 页码，从 1 开始。仅 `sample=false` 时有效 |
| `size` | int | 每页条数，默认 200 |
| `sample` | bool | `true` 时忽略 page，随机抽取 size 条 |

**`sample=true` 场景**：数据量大时，随机抽取子集保留聚类特征，前端渲染压力可控。每次请求返回新随机样本，前端提供「重新抽样」按钮。

**`sample=false` 场景**：分页浏览全部数据，适合精细查看局部区域。

### 6.2 相似度辅助

响应中返回 `similarities` 字段，对当前返回点集内计算两两**余弦相似度**（利用 `t_knowledge_viz.vector_768`），每个点返回 top-5 邻居：

```
similarities: {
  "123": [{ "id": 456, "score": 0.92 }, { "id": 789, "score": 0.87 }],
  ...
}
```

| 项 | 说明 |
|----|------|
| 算法 | 余弦相似度 `cos(a,b) = a·b / (|a||b|)` |
| 计算范围 | 当前 page/sample 点集内，保证 O(N²·D) 可控 |
| 耗时 | 200 点 × 768 维 ≈ < 50ms |
| 用途 | 前端高亮关联点位、相似连线、匹配对比面板 |

### 6.3 内容类型检测

响应中 `points[].contentType` 标记每个点的数据类型：

| 值 | 含义 | 判定方式 |
|----|------|----------|
| `text` | 纯文本 | contentHtml 为空或不含 `<img` |
| `multimodal` | 图文混合 | contentHtml 含 `<img` 标签 |

## 7. 前端渲染

- **框架**：ECharts scatter + lines
- **数据**：`GET /visualization` 返回结构化 `{ points[], total, similarities }`
- **交互**：悬停显示标题和双状态（检索/可视化），点击选中查看详情面板
- **延迟**：零 API 调用延迟，x/y 已预计算

### 7.1 样式分层

| 维度 | 纯文本 (text) | 图文混合 (multimodal) |
|------|--------------|----------------------|
| ECharts 系列 | scatter（系列一） | scatter（系列二） |
| 形状 | `circle` | `diamond` |
| 大小 | 12px | 14px（更醒目） |
| 图例 | 纯文本 | 图文 |

| `vizStatus` | 颜色 |
|-------------|------|
| `vectorized` | 绿色 `#3fb950` |
| `failed` | 红色 `#f85149` |
| `pending` | 橙色 `#d29922` |

两个 scatter 系列分层绘制，同坐标系，图例可以分别控制显隐。

### 7.2 相似连线

> 可视化条目间语义关联，用户直观识别聚类结构。

- 使用独立的 `lines` 系列绘制（z 轴低于散点）
- 为每对高相似条目绘制连接线，默认阈值 0.85
- 连线透明度随相似度单调变化（0.85→0.2，1.0→0.6）
- 右侧提供相似阈值滑块（0.5–1.0），实时过滤/增加连线密度
- 去重：只绘制 `sourceId < targetId` 的连线，避免 A↔B 重复
- hover 连线显示相似度百分比

### 7.3 分页 / 抽样控制

栏顶部控制区域：

```
┌─────────────────────────────────────────────────────────────┐
│ ☐ 随机抽样  [200条 ▾]  [重新抽样]     相似阈值 [═══●═══]    │
│ 第 1 页 / 共 3 页（500 条）  [‹] [1] [2] [3] [›]            │
└─────────────────────────────────────────────────────────────┘
```

- checkbox 切换「分页/抽样」模式
- 条数选择器：50 / 100 / 200 / 500
- 统计栏展示：总量、已向量化、失败、图文、纯文本数量
- 相似连线计数

### 7.4 详情面板

点击散点后底部展开：

```
┌─ 选中条目 ────────────────────────── [✕] ─┐
│ 标题: XXXXX                                    │
│ ID: 123  [图文]                                │
│ 检索: ✅vectorized  可视化: ✅vectorized        │
│ ─────────────────                           │
│ 相似条目                                        │
│ ┌────────────────────────────────┐  [92.0%]  │
│ │ YYYYYY                          │ [█████]  │
│ └────────────────────────────────┘           │
│ ┌────────────────────────────────┐  [87.3%]  │
│ │ ZZZZZZ                          │ [████]   │
│ └────────────────────────────────┘           │
└──────────────────────────────────────────────┘
```

- 展示条目基本信息 + 双状态 tags
- 相似条目列表：标题 + 相似度进度条
- 点击相似条目跳转定位到对应散点

## 8. 后续可选扩展

- **热力图**：在散点图基础上叠加核密度估计（KDE）热力层，ECharts 自带 `heatmap` 系列
- **检索对比面板**：选择某个点后，利用已有相似度数据，高亮搜索结果中与其语义相近的其他点（当前详情面板已展示 top-5 相似条目，可扩展为搜索入口）
- **Qdrant 集成**：当数据量 > 10 万条、或需要用 768 向量做检索时，将 `vector_768` 同步到 Qdrant，MySQL 只存元数据和向量 ID

## 9. 模型与路由优化

### 9.1 模型配置中心化管理

**问题**：两个模型的地址、密钥、纬度、超时参数分散在各自 Client 类的 `@Value` 注解中，切换环境需改代码。

**方案**：抽取 `VectorProperties` `@ConfigurationProperties` 配置类，将两个模型的全部参数统一管理：

```yaml
vector:
  enabled: true

  siliconflow:
    api-key: sk-xxx
    api-url: https://api.siliconflow.cn/v1/embeddings
    model: BAAI/bge-m3
    dimensions: 1024
    connect-timeout: 5000
    read-timeout: 30000

  dashscope:
    api-key: sk-xxx
    api-url: https://dashscope.aliyuncs.com/api/v1/services/embeddings/multimodal-embedding/multimodal-embedding
    model: tongyi-embedding-vision-flash-2026-03-06
    dimensions: 768
    connect-timeout: 5000
    read-timeout: 30000
```

```java
@ConfigurationProperties(prefix = "vector")
@Component
@Data
public class VectorProperties {
    private boolean enabled = true;
    private SiliconFlow siliconflow = new SiliconFlow();
    private DashScope dashscope = new DashScope();
    // ...
}
```

各 Client 类注入 `VectorProperties` 而不是每个字段单独 `@Value`，改配置无需动业务代码。

### 9.2 路由参数适配

**问题**：`embedVisualization` 统一走 DashScope 768d，但纯文本和图文混合的入参格式不同：
- 纯文本 → `contents: [{ text: "..." }]`
- 图文混合 → `contents: [{ text: "..." }, { image: "http://..." }]`

**方案**：在 `VectorClientRouter.embedVisualization()` 中根据内容类型做分支适配：

```java
embedVisualization(text, contentHtml, knowledgeId):
  ├─ 纯文本（无 <img） → dashScopeClient.embedText(text)
  │   仅构建 { text } content，RequestBody 最小化
  ├─ 图文混合 → dashScopeClient.embed(text, contentHtml, knowledgeId)
  │   提取 image URLs，构建 { text, image } contents
  └─ 异常（空入参）→ log warning，返回空结果
```

**边界处理**：

| 场景 | 行为 |
|------|------|
| `text` 为空，`contentHtml` 有 `<img` | 仅发图片，跳过 text content |
| `text` 有内容，`contentHtml` 无图 | 仅发 text，不发空 contentHtml |
| `text` 和 `contentHtml` 均为空 | log warning，返回 `ds_empty` 标记 |
| 图片 URL 提取失败（非 http(s)） | 过滤无效 URL，仅发合法图片 |

### 9.3 调用耗时监控埋点

**方案**：在 `VectorMetrics` 组件中维护两类模型 API 调用的实时统计数据：

```java
@Component
public class VectorMetrics {
    // 按模型分类的调用统计
    private ConcurrentHashMap<String, ModelMetrics> metrics = new ConcurrentHashMap<>();

    // 每次调用后记录
    void record(String model, long durationMs, boolean success) { ... }

    // 查询统计摘要
    Map<String, Object> getStats() {
        return {
            siliconflow: { totalCalls, avgDuration, successRate, ... },
            dashscope:   { totalCalls, avgDuration, successRate, ... }
        };
    }
}
```

| 指标 | 类型 | 用途 |
|------|------|------|
| `totalCalls` | long | 总调用次数，评估成本 |
| `successCount` / `failCount` | long | 成功率，排查服务稳定性 |
| `totalDurationMs` / `avgDurationMs` | long/double | 平均响应时间，发现性能瓶颈 |
| `maxDurationMs` | long | 最慢单次调用，定位超时问题 |
| `lastCalledAt` | timestamp | 最后调用时间，判断最近是否在用 |

**埋点位置**：在 `SiliconFlowEmbeddingClient` 和 `DashScopeMultimodalEmbeddingClient` 的 embed 方法中包裹 `VectorMetrics.record()`：

```java
VectorResult embed(String text) {
    long start = System.currentTimeMillis();
    try {
        VectorResult result = doEmbed(text);
        metrics.record("siliconflow", System.currentTimeMillis() - start, true);
        return result;
    } catch (Exception e) {
        metrics.record("siliconflow", System.currentTimeMillis() - start, false);
        throw e;
    }
}
```

**查询接口**（可选）：`GET /api/vector/metrics` 返回 JSON 格式的统计数据，用于 Grafana 看板或日常排查。

## 10. 数据一致性优化

### 10.1 双向量生命周期联动

**问题**：知识库条目删除（软删除 `deleted=1`）时，`t_knowledge_viz` 的可视化向量记录和 `t_knowledge_base.viz_x/viz_y` 坐标不会自动清理，随数据积累产生大量脏数据。

**方案**：删除知识条目时联动清理可视化相关数据：

```
DELETE /knowledge/{id}
  ├─ 1. 查询知识条目是否存在（含 categoryId）
  ├─ 2. 删除 t_knowledge_viz 对应记录（WHERE knowledge_id = ?）
  ├─ 3. 重置 viz_x = NULL, viz_y = NULL（软删除记录保留空坐标）
  └─ 4. 执行原软删除（deleted = 1）
```

| 场景 | 清理范围 | 说明 |
|------|----------|------|
| 单条删除 `DELETE /knowledge/{id}` | 该条 `t_knowledge_viz` + 坐标 | 最频繁操作 |
| 批量删除 | 批量对应 viz 记录 | 后续可选 |
| 分类删除 | 分类下全部知识条目的 viz 数据 | 分类管理侧处理 |

Cleanup 后，PCA 缓存基线中的数据量与实际 `t_knowledge_viz` 记录数不一致。下次全量重算 PCA 时自动修正（增量模式不受影响，因为已删除条目的 viz_id 不会超过 last_viz_id）。

## 11. 版本快照记录

### 11.1 问题

PCA 重算后坐标变化，用户无法确定某次重算的原因和参数。出现聚类异常时难以回溯排查。

### 11.2 方案

新增 `t_pca_calculation_record` 表，记录每次 PCA 重算的元数据：

```sql
CREATE TABLE t_pca_calculation_record (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id   BIGINT NOT NULL COMMENT '分类ID',
    version       INT NOT NULL COMMENT '版本号（同一分类递增）',
    trigger_type  VARCHAR(20) NOT NULL COMMENT 'trigger: manual_full / incremental / manual_single / auto_incremental',
    point_count   INT NOT NULL COMMENT '参与本次计算的向量数',
    before_count  INT DEFAULT 0 COMMENT '计算前向量数',
    computation_cost_ms BIGINT COMMENT '计算耗时(ms)',
    remark        VARCHAR(500) COMMENT '备注（如触发源、异常信息）',
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_calc_version (category_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='PCA计算记录表';
```

| 字段 | 用途 |
|------|------|
| `version` | 同一分类递增，每次重算 +1，区分先后顺序 |
| `trigger_type` | 区分全量/增量/手动/自动，便于筛选异常场景 |
| `point_count` / `before_count` | 追踪向量数量的变化趋势 |
| `computation_cost_ms` | 发现性能退化 |
| `remark` | 记录异常信息（如「DashScope 限流，3 条失败」） |

### 11.3 记录点位

| PCA 类型 | 记录时机 | remark 示例 |
|----------|----------|-------------|
| `fullPCA` | 基线构建完成 | "初次全量构建" |
| `incrementalPCA` | 增量投影完成 | "10 条新增向量增量投影" |
| `recomputePCAFull` | 手动全量重算 | "用户手动触发全量重算" |

### 11.4 回溯排查流程

```
观察散点图异常
  └─ 查询 t_pca_calculation_record
       ├─ 最近的 version：触发类型、参与向量数、计算耗时
       ├─ 上几个 version：对比 point_count 突变
       └─ 结合 remark 判断异常原因（如某次增量包含脏数据）
```

核心：版本记录不存大字段，仅记录轻量元数据，辅助开发人员快速定位异常操作的时间点。

## 12. 远期扩展性优化

### 12.1 向量存储抽象层（Qdrant 平滑迁移入口）

**问题**：当前 `t_knowledge_viz` 的读写通过 `KnowledgeVizMapper` 直接操作 MySQL，后续切换到 Qdrant 等专用向量数据库时需要改所有调用方。

**方案**：抽取 `VectorStore` 接口，将 768 维向量的存/取/删操作与 MySQL 解耦：

```java
public interface VectorStore {
    // 向量 CRUD
    void saveOrUpdateVector(Long knowledgeId, float[] vector);
    void deleteVector(Long knowledgeId);
    float[] getVector(Long knowledgeId);

    // 批量查询
    Map<Long, float[]> getVectorsByKnowledgeIds(Collection<Long> knowledgeIds);
    Map<Long, float[]> getVectorsByCategoryId(Long categoryId);

    // 支持判断
    boolean isAvailable();   // false 表示降级 / 未初始化
    String name();           // "MySQL" / "Qdrant"
}
```

**MySQL 实现**（默认）：包装 `KnowledgeVizMapper`，读写 `t_knowledge_viz.vector_768`。

**Qdrant 实现**（预留）：实现同一接口，将向量写入 Qdrant collection，payload 携带 `knowledge_id` 和 `category_id` 用于过滤。

**切换方式**：

```yaml
vector:
  store: mysql          # mysql | qdrant
  qdrant:
    host: localhost
    grpc-port: 6334
    collection: knowledge_viz
```

**调用方不受影响**：`VectorTaskServiceImpl`、`KnowledgeBaseServiceImpl`、`KnowledgeBaseController` 均注入 `VectorStore` 接口，不直接依赖 Mapper。

**数据迁移策略**：

```
MySQL → Qdrant 迁移
  1. 配置 vector.store=qdrant
  2. 启动同步任务（扫描 t_knowledge_viz 全量写入 Qdrant）
  3. 校验数据一致性
  4. 切换默认 store 为 qdrant（MySQL 保留只读备份）
```

### 12.2 降维算法可切换（PCA/UMAP 接口抽象）

**问题**：`PCAService` 直接注入 `VectorTaskServiceImpl`，PCA 逻辑与业务代码紧耦合，后续接入 UMAP 或其他算法需要改业务逻辑。

**方案**：抽取 `DimensionalityReducer` 接口，使降维算法可替换：

```java
public interface DimensionalityReducer {
    String name();              // "PCA" / "UMAP"
    boolean supportsIncremental();

    // 全量降维
    ReductionResult reduce(Map<Long, float[]> vectors);

    // 增量投影（仅 PCA 支持）
    IncrementalResult projectIncremental(
        Map<Long, float[]> newVectors,
        double[] mean, double[] pc1, double[] pc2,
        double xMin, double xMax, double yMin, double yMax);
}
```

**结果类型**：

```java
@Data @AllArgsConstructor
class Point2D {
    Long knowledgeId;
    double x, y;
    double z;     // 预留 3D 坐标，PCA 对应 PC3
}

@Data @AllArgsConstructor
class ReductionResult {
    List<Point2D> points;
    double[] mean, pc1, pc2, pc3;   // 主成分（pc3 为 3D 预留）
    double xMin, xMax, yMin, yMax, zMin, zMax;
}
```

**切换方式**：

```yaml
pca:
  algorithm: pca               # pca | umap（后续扩展）
  max-iterations: 100
  convergence-threshold: 1.0E-6
```

**`@ConditionalOnProperty` Spring 自动装配**：

```java
@Component
@ConditionalOnProperty(value = "pca.algorithm", havingValue = "pca", matchIfMissing = true)
public class PCAStrategy implements DimensionalityReducer { ... }
```

**业务代码**：`VectorTaskServiceImpl` 注入 `DimensionalityReducer` 而非 `PCAService`，换算法只需改配置。

**设计要点**：
- `supportsIncremental()` 区分全量/增量兼容性（UMAP 无增量概念）
- 不强制要求所有算法支持增量，不支持的算法每次降维均全量重算
- 提取的 Point2D 和 ReductionResult 不绑定 PCA 术语

### 12.3 多维度可视化拓展（预留 3D 坐标）

**问题**：当前仅支持 2D 散点（`viz_x` / `viz_y`），若后续需要 3D 展示形态需要重新计算、加列、改 API。

**方案**：从 PCA 起就计算第三主成分（PC3），写入 `viz_z` 字段，API 返回 z 坐标，前端和降维接口均预留 3D 通道。

**数据库变更**：

```sql
-- t_knowledge_base 新增列
ALTER TABLE t_knowledge_base ADD COLUMN viz_z DOUBLE DEFAULT NULL COMMENT 'PCA降维Z坐标（3D预留）';

-- t_pca_baseline 新增列
ALTER TABLE t_pca_baseline ADD COLUMN pc3 VARBINARY(3072) DEFAULT NULL COMMENT '第三主成分（3D预留）';
ALTER TABLE t_pca_baseline ADD COLUMN z_min DOUBLE DEFAULT NULL COMMENT '归一化Z下界';
ALTER TABLE t_pca_baseline ADD COLUMN z_max DOUBLE DEFAULT NULL COMMENT '归一化Z上界';
```

**PC3 计算**：

```java
// 在 PCA 幂迭代中增加第三次迭代
double[] pc1 = powerIteration(X, n, dim, null);
double[] pc2 = powerIteration(X, n, dim, pc1);
double[] pc3 = powerIteration(X, n, dim, pc2);  // 新增 PC3

// 投影时增加 z 坐标
z = dot(X[i], pc3);
```

**增量投影扩展**：增量模式下也计算第三维投影：

```
z_new = dot(x_centered, pc3)
归一化 z 到 [-1, 1]，写入 viz_z
```

**API 变更**：

```json
{
  "points": [
    {
      "id": 1,
      "title": "...",
      "x": 0.5, "y": -0.3,
      "z": 0.1,         // 新增，默认为 0
      "vectorStatus": "vectorized",
      "vizStatus": "vectorized",
      "contentType": "text"
    }
  ]
}
```

**前端 3D 按钮**（渲染层暂保留）：
- 图表区域预留 3D 切换按钮，当前 `disabled` 状态
- 3D 按钮 hover 提示：「3D 视图开发中」
- 数据层面 `z` 字段已返回，后续可直接绑定 ECharts 3D 或 Three.js

**成本**：
- 每次 PCA 迭代 O(N·D)，增加 PC3 多一次幂迭代，总耗时增加 ~30%
- 存储增加：`viz_z` 8 字节 DOUBLE，PC3 3072 字节 VARBINARY
- 增量投影：每条新增点多一次点积运算（O(D)），可忽略不计

### 12.4 文件改动总览

| 操作 | 文件 | 说明 |
|------|------|------|
| 新建 | `service/VectorStore.java` | 向量存储抽象接口 |
| 新建 | `service/impl/MySQLVectorStore.java` | MySQL 向量存储实现 |
| 新建 | `service/DimensionalityReducer.java` | 降维算法抽象接口 |
| 修改 | `PCAService.java` | 实现 `DimensionalityReducer`，增加 PC3 计算，返回 z 坐标 |
| 修改 | `KnowledgeBase.java` | 新增 `vizZ` 字段 |
| 修改 | `PCABaseline.java` | 新增 `pc3` / `zMin` / `zMax` 字段 |
| 修改 | `schema.sql` | 新增 `viz_z` / `pc3` / `z_min` / `z_max` 列 |
| 修改 | `VectorTaskServiceImpl.java` | 注入 `VectorStore` + `DimensionalityReducer` 替代直接 Mapper 调用 |
| 修改 | `KnowledgeBaseServiceImpl.java` | 注入 `VectorStore` 替代 `KnowledgeVizMapper` |
| 修改 | `KnowledgeBaseController.java` | 注入 `VectorStore`，返回值增加 `z` |
| 修改 | `api/knowledge.ts` | TypeScript 类型增加 `z` 字段 |
| 修改 | `KnowledgeVisualization.vue` | 预留 3D 按钮
