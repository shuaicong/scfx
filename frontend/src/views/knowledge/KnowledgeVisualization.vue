<template>
  <div class="knowledge-viz">
    <!-- Header -->
    <div class="viz-header">
      <div class="viz-header-left">
        <el-button text @click="goBack" class="back-btn">← 返回</el-button>
        <h2>向量可视化</h2>
      </div>
      <div class="viz-actions">
        <el-select v-model="selectedCategoryId" placeholder="选择分类" style="width:220px" @change="onCategoryChange">
          <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
        </el-select>
        <el-button type="primary" :disabled="!selectedCategoryId || loading" :loading="loading" @click="loadData">刷新</el-button>
        <el-button :disabled="!selectedCategoryId || loading || recomputing" :loading="recomputing" @click="recomputeCurrent">重算</el-button>
      </div>
    </div>

    <!-- 1. Empty: no categories -->
    <el-empty v-if="categories.length === 0" description="暂无分类">
      <template #description><p>请先在知识管理页面创建分类并录入知识。</p></template>
    </el-empty>

    <!-- 2. Empty: no vector data -->
    <el-empty v-else-if="noVectorData" description="该分类暂未向量化">
      <template #description><p>该分类下的知识尚未完成向量化。</p></template>
    </el-empty>

    <!-- Loading skeleton -->
    <div v-else-if="loading && points.length === 0" class="viz-loading">
      <el-skeleton :rows="6" animated />
    </div>

    <template v-else-if="selectedCategoryId">
      <!-- Stats Bar -->
      <div class="viz-stats">
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">
            已向量化
            <el-tooltip content="当前分类全局数据" placement="top"><el-icon style="margin-left:2px"><InfoFilled /></el-icon></el-tooltip>
          </div>
          <div class="stat-value">{{ vectorizedCount }}</div>
        </el-card>
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">失败</div>
          <div class="stat-value" style="color:#f85149">{{ failedCount }}</div>
        </el-card>
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">图文</div>
          <div class="stat-value" style="color:#d29922">{{ multimodalCount }}</div>
        </el-card>
        <el-card shadow="never" class="stat-card">
          <div class="stat-label">纯文本</div>
          <div class="stat-value" style="color:#3fb950">{{ textCount }}</div>
        </el-card>
        <div class="stats-scope">全分类共 {{ total }} 条知识，当前图表展示 {{ points.length }} 条</div>
      </div>

      <!-- Computation hint (MDS first compute) -->
      <el-alert v-if="computationHint && showHint" :title="computationHint" type="info" show-icon :closable="true" size="small" style="margin-bottom:8px" @close="showHint = false" />

      <!-- MDS Progress -->
      <div v-if="recomputing && algorithm === 'mds' && mdsProgress > 0" class="mds-progress">
        <el-progress :percentage="mdsProgress" :stroke-width="6" :format="mdsProgressFormat" />
      </div>

      <!-- Controls Bar -->
      <div class="viz-controls">
        <div class="viz-controls-group">
          <el-select v-model="algorithm" style="width:110px" size="small" @change="onAlgorithmChange">
            <el-option label="PCA" value="pca" />
            <el-option label="MDS" value="mds" :disabled="mdsDisabled" />
          </el-select>
          <el-popover placement="bottom" :width="260" trigger="hover">
            <template #reference>
              <span style="display:inline-flex;align-items:center;cursor:pointer;color:#999;font-size:12px;margin-left:2px">
                <el-icon><InfoFilled /></el-icon>
                <span style="margin-left:2px">说明</span>
              </span>
            </template>
            <div class="algo-info">
              <div v-if="algorithm === 'pca'">
                <strong>PCA（主成分分析）</strong>
                <p style="margin:4px 0">线性降维，通过最大方差方向投影保留全局结构。</p>
                <ul style="margin:4px 0;padding-left:16px;font-size:12px">
                  <li>✅ 支持大数据量（万级）</li>
                  <li>✅ 计算速度快</li>
                  <li>✅ 支持增量更新</li>
                  <li>⚠️ 对非线性结构保留较差</li>
                </ul>
              </div>
              <div v-else>
                <strong>MDS（多维缩放）</strong>
                <p style="margin:4px 0">非线性降维，通过保持点对距离保留局部结构。</p>
                <ul style="margin:4px 0;padding-left:16px;font-size:12px">
                  <li>✅ 擅长发现非线性模式</li>
                  <li>✅ 小样本聚类效果好</li>
                  <li>⚠️ 上限 500 条</li>
                  <li>⚠️ 计算较慢 O(n²)</li>
                  <li>⚠️ 不支持增量</li>
                </ul>
              </div>
            </div>
          </el-popover>
          <p v-if="mdsDisabled && mdsDisabledReason" class="mds-disabled-hint">{{ mdsDisabledReason }}</p>
        </div>

        <div class="viz-controls-group">
          <el-radio-group v-model="viewMode" size="small" @change="renderChart">
            <el-tooltip content="二维散点图，支持形状+颜色双编码区分纯文本和图文" placement="top" :show-after="300">
              <el-radio-button value="2d">2D</el-radio-button>
            </el-tooltip>
            <el-tooltip :content="webglSupported ? '三维散点图，可拖拽旋转查看分布' : '当前环境不支持 3D（WebGL 不可用）'" placement="top" :show-after="300">
              <el-radio-button value="3d" :disabled="!webglSupported">3D</el-radio-button>
            </el-tooltip>
          </el-radio-group>
          <el-tag v-if="viewMode === '3d'" size="small" type="warning" effect="plain" style="margin-left:4px">3D 形状统一，颜色区分类型</el-tag>
          <el-button v-if="viewMode === '3d'" size="small" @click="resetView" style="margin-left:4px">重置视角</el-button>
        </div>

        <div class="viz-controls-group">
          <el-checkbox v-model="sampleMode" size="small" @change="onSampleChange">随机抽样</el-checkbox>
          <el-select v-model="size" size="small" style="width:90px" @change="loadData">
            <el-option label="50 条" :value="50" />
            <el-option label="100 条" :value="100" />
            <el-option label="200 条" :value="200" />
            <el-option label="500 条" :value="500" />
          </el-select>
          <el-button v-if="sampleMode" size="small" @click="loadData">重新抽样</el-button>
        </div>

        <div class="viz-controls-group">
          <el-checkbox v-model="showSelectedOnly" size="small" @change="renderChart">仅显示选中点的相似连线</el-checkbox>
          <span class="viz-label">
            相似阈值
            <el-tooltip content="数值越高，仅保留相似度越高的连线" placement="top" :show-after="300">
              <el-icon style="color:#999;cursor:pointer;margin-left:2px"><InfoFilled /></el-icon>
            </el-tooltip>
          </span>
          <el-slider v-model="similarityThreshold" :min="0.5" :max="1" :step="0.01" style="width:100px" @input="onThresholdInput" @change="onThresholdChange" />
          <span class="slider-value">{{ similarityThreshold.toFixed(2) }}</span>
        </div>
      </div>

      <!-- Pagination -->
      <div v-if="!sampleMode && total > size" class="viz-pagination">
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="prev, pager, next" small @current-change="loadData" />
      </div>

      <!-- 3. Empty: no data for current algorithm -->
      <el-empty v-if="!hasData && points.length === 0 && !loading" description="暂无降维数据">
        <template #description>
          <p>请点击「重算」为该分类生成降维坐标</p>
          <el-button type="primary" :loading="recomputing" size="small" style="margin-top:8px" @click="recomputeCurrent">立即重算</el-button>
        </template>
      </el-empty>

      <!-- 4. Empty: current page/sample has no matching points -->
      <el-empty v-else-if="points.length === 0 && !loading && hasData" description="当前筛选条件下无数据">
        <template #description><p>请调整分页或关闭随机抽样。</p></template>
      </el-empty>

      <!-- Sampling hint -->
      <el-alert v-if="sampleMode && points.length > 0" :title="'当前为随机抽样数据（共 ' + size + ' 条），不代表全量分布'" type="warning" show-icon :closable="true" size="small" style="margin-bottom:4px" />

      <!-- Similarity lines overflow alert -->
      <el-alert v-if="similarityOverflow" :title="similarityOverflowMsg" type="info" show-icon :closable="false" size="small" style="margin-bottom:4px" />

      <!-- Chart Area (only rendered when data exists) -->
      <div v-if="points.length > 0" class="viz-chart" ref="chartRef" />

      <!-- Color/Shape Legend Guide -->
      <div v-if="points.length > 0" class="chart-legend-guide">
        <span class="legend-item"><span class="legend-dot" style="background:#3fb950" />已向量化</span>
        <span class="legend-item"><span class="legend-dot" style="background:#d29922" />等待中</span>
        <span class="legend-item"><span class="legend-dot" style="background:#f85149" />失败</span>
        <span class="legend-divider" />
        <span class="legend-item"><span class="legend-shape" style="color:#3fb950">●</span>纯文本</span>
        <span class="legend-item"><span class="legend-shape" style="color:#58a6ff">◆</span>图文</span>
        <span class="legend-item"><span class="legend-line">──</span>相似连线</span>
      </div>

      <!-- Two-point hint -->
      <el-alert v-if="points.length === 2 && !loading" title="当前仅 2 条向量，坐标呈对称分布" type="info" show-icon :closable="true" size="small" style="margin-top:4px" />

      <!-- Detail Drawer -->
      <el-drawer v-model="detailVisible" :title="selectedPoint?.title || '详情'" size="420px" @close="onDrawerClose">
        <template #default>
          <div v-if="!pointDetail" class="drawer-loading">
            <el-skeleton :rows="4" animated />
          </div>
          <template v-else>
            <!-- Zero vector alert -->
            <el-alert v-if="pointDetail.isZeroVector" type="warning" :closable="false" show-icon style="margin-bottom:12px">
              该知识为全零向量，图中坐标无实际意义。
            </el-alert>

            <!-- Metadata -->
            <el-descriptions :column="2" size="small" border>
              <el-descriptions-item label="ID">{{ pointDetail.id }}</el-descriptions-item>
              <el-descriptions-item label="类型">{{ pointDetail.contentType === 'multimodal' ? '图文' : '纯文本' }}</el-descriptions-item>
              <el-descriptions-item label="检索">{{ vizStatusText(pointDetail.vectorStatus) }}</el-descriptions-item>
              <el-descriptions-item label="可视化">{{ vizStatusText(pointDetail.vizStatus) }}</el-descriptions-item>
            </el-descriptions>

            <!-- Coordinates -->
            <h4 class="drawer-section">降维坐标（{{ algorithm }}）</h4>
            <code class="drawer-coords">({{ pointDetail.coords.x.toFixed(4) }}, {{ pointDetail.coords.y.toFixed(4) }}, {{ pointDetail.coords.z.toFixed(4) }})</code>

            <!-- Semantic Fingerprint Summary -->
            <div v-if="pointDetail.fullVector && pointDetail.fullVector.length > 0" class="vector-summary">
              <h4 class="drawer-section">
                语义指纹
                <el-tooltip :content="vectorFingerprintTooltip" placement="right" :show-after="300">
                  <el-icon style="color:#999;cursor:pointer;margin-left:4px"><InfoFilled /></el-icon>
                </el-tooltip>
              </h4>
              <div class="vector-summary-stats">
                <div class="summary-stat">
                  <div class="summary-stat-value" style="color:#3fb950">{{ vectorSummary.positiveCount }}</div>
                  <div class="summary-stat-label">正向维</div>
                </div>
                <div class="summary-stat">
                  <div class="summary-stat-value" style="color:#f85149">{{ vectorSummary.negativeCount }}</div>
                  <div class="summary-stat-label">负向维</div>
                </div>
                <div class="summary-stat">
                  <div class="summary-stat-value">{{ vectorSummary.magnitude.toFixed(2) }}</div>
                  <div class="summary-stat-label">向量强度</div>
                </div>
                <div class="summary-stat">
                  <div class="summary-stat-value">{{ vectorSummary.activationRate }}%</div>
                  <div class="summary-stat-label">激活率</div>
                </div>
              </div>
              <p class="vector-summary-desc">
                <strong>{{ vectorSummary.positiveCount }}</strong> 个维度正向激活，<strong>{{ vectorSummary.negativeCount }}</strong> 个维度负向激活，整体强度 <strong>{{ vectorSummary.magnitude.toFixed(2) }}</strong>。
                绿色（正向）表示语义相关 / 激活的特征，红色（负向）表示语义无关 / 抑制的特征。
                数值越大，该维度的特征越显著。
              </p>

              <!-- Full-768-dimension heatmap strip -->
              <div class="vector-heatmap">
                <div class="heatmap-gradient">
                  <span style="color:#f85149">负</span>
                  <canvas ref="heatmapCanvas" width="768" height="16" class="heatmap-canvas"></canvas>
                  <span style="color:#3fb950">正</span>
                </div>
                <div class="heatmap-ticks">
                  <span>0</span>
                  <span>256</span>
                  <span>512</span>
                  <span>768</span>
                </div>
              </div>
            </div>

            <!-- Vector Preview -->
            <h4 class="drawer-section">
              <span>向量预览</span>
              <el-select v-model="previewDimCount" size="small" style="width:80px">
                <el-option label="前 5 维" :value="5" />
                <el-option label="前 10 维" :value="10" />
                <el-option label="前 20 维" :value="20" />
              </el-select>
              <el-radio-group v-model="normMode" size="small">
                <el-radio-button value="per-vector">局部</el-radio-button>
                <el-radio-button value="global">全局</el-radio-button>
              </el-radio-group>
              <el-tooltip content="局部：按当前向量最大值归一化；全局：按全分类所有向量的全局最大值归一化" placement="top">
                <el-icon style="color:#999;cursor:pointer"><InfoFilled /></el-icon>
              </el-tooltip>
              <el-tooltip content="复制全部" placement="top">
                <el-button size="small" text @click="copyFullVector">📋</el-button>
              </el-tooltip>
            </h4>

            <div class="vector-bars">
              <el-tooltip v-for="(v, i) in vectorPreviewSlice" :key="i" placement="left" :show-after="300">
                <template #content>
                  <div style="font-size:12px;line-height:1.6">
                    <div><strong>dim {{ i }}</strong>: {{ v.toFixed(6) }}</div>
                    <div style="margin-top:4px">
                      <span :style="{ color: v >= 0 ? '#3fb950' : '#f85149' }">{{ v >= 0 ? '正向' : '负向' }}</span>
                      · 条宽 {{ normPercent(v) }}%
                    </div>
                    <div style="margin-top:2px;color:#8b949e;font-size:11px">
                      {{ normMode === 'per-vector' ? '局部：按当前向量最大值归一化，条宽反映维度间相对强弱' : '全局：按全分类所有向量最大值归一化，条宽可跨条目对比' }}
                    </div>
                    <div style="color:#8b949e;font-size:11px">
                      绿色 → 语义相关 / 激活 &nbsp; 红色 → 语义无关 / 抑制
                    </div>
                  </div>
                </template>
                <div class="vector-bar-item">
                  <span class="dim-label">dim {{ i }}</span>
                  <div class="bar">
                    <div class="bar-fill" :class="v >= 0 ? 'positive' : 'negative'" :style="{ width: normPercent(v) + '%' }" />
                  </div>
                  <span class="dim-value">{{ v.toFixed(4) }}</span>
                  <el-tooltip content="复制" placement="right">
                    <el-button size="small" text @click="copyDimValue(i, v)" style="padding:0 2px;height:20px">
                      <el-icon><CopyDocument /></el-icon>
                    </el-button>
                  </el-tooltip>
                </div>
              </el-tooltip>
            </div>
            <el-button v-if="!pointDetail.isZeroVector" text type="primary" size="small" @click="fullVectorVisible = true">查看完整向量</el-button>

            <!-- Content Preview -->
            <h4 class="drawer-section">内容预览</h4>
            <p class="content-preview">{{ pointDetail.content }}</p>

            <!-- Similar Neighbors -->
            <h4 class="drawer-section">相似条目</h4>
            <div v-for="n in sortedNeighbors" :key="n.id" class="neighbor-item"
                 :class="{ 'neighbor-zero': n.isZeroVector, 'neighbor-highlighted': highlightedNeighborId === n.id }"
                 @click="onNeighborClick(n.id)"
                 @mouseenter="onNeighborHover(n.id)"
                 @mouseleave="onNeighborHover(null)">
              <span class="neighbor-title">{{ n.title || `#${n.id}` }}</span>
              <el-tag v-if="n.isZeroVector" size="small" type="info" effect="plain">空向量</el-tag>
              <el-progress v-else :percentage="Math.round(n.score * 100)" :stroke-width="6" style="flex:1;max-width:120px" />
            </div>
            <div v-if="sortedNeighbors.length === 0" class="neighbor-empty">暂无相似条目</div>
          </template>
        </template>
      </el-drawer>

      <!-- Full Vector Dialog -->
      <el-dialog v-model="fullVectorVisible" title="完整向量（768 维）" width="80%" top="5vh">
        <div class="full-vector-grid">
          <div v-for="(v, i) in pointDetail?.fullVector" :key="i" class="vector-cell">
            <span class="cell-index">{{ i }}</span>
            <span class="cell-value" :class="v >= 0 ? 'pos' : 'neg'">{{ v.toFixed(6) }}</span>
          </div>
        </div>
        <template #footer>
          <el-button @click="copyFullVector">复制全部</el-button>
          <el-button type="primary" @click="fullVectorVisible = false">关闭</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { knowledgeApi } from '@/api/knowledge'
import { categoryApi } from '@/api/category'
import { ElMessage } from 'element-plus'

// ======================== Types ========================

interface VizPoint {
  id: number; title: string; x: number; y: number; z: number
  vectorStatus: string; vizStatus: string; contentType: string
  isZeroVector?: boolean
}

interface SimilarNeighbor { id: number; score: number }

interface PointDetail {
  id: number; title: string; content: string; contentHtml: string
  algorithm: string
  coords: { x: number; y: number; z: number }
  vectorPreview: number[]; fullVector: number[]; globalMaxAbs: number
  vectorStatus: string; vizStatus: string; contentType: string
  isZeroVector: boolean
  neighbors: Array<{ id: number; title: string; score: number; isZeroVector: boolean }>
}

interface Category { id: number; name: string }

// ======================== State ========================

const router = useRouter()
const categories = ref<Category[]>([])
const selectedCategoryId = ref<number | null>(null)
const points = ref<VizPoint[]>([])
const loading = ref(false)
const recomputing = ref(false)
const selectedPoint = ref<VizPoint | null>(null)
const pointDetail = ref<PointDetail | null>(null)
const detailVisible = ref(false)
const chartRef = ref<HTMLElement | null>(null)
const heatmapCanvas = ref<HTMLCanvasElement | null>(null)
let chart: echarts.ECharts | null = null

// Pagination & sampling
const page = ref(1)
const size = ref(200)
const total = ref(0)
const sampleMode = ref(false)
const similarityThreshold = ref(0.85)

// Algorithm & view
const algorithm = ref('pca')
const viewMode = ref<'2d' | '3d'>('2d')
const noVectorData = ref(false)
const hasData = ref(false)

// Similarities data
const similarities = ref<Record<number, SimilarNeighbor[]>>({})

// Neighbor → chart highlight
const highlightedNeighborId = ref<number | null>(null)
const pointIndexMap = new Map<number, { seriesIdx: number; dataIdx: number }>()

// Timers & resources
let pollTimer: ReturnType<typeof setInterval> | null = null
let pollRetryCount = 0
let debounceTimer: ReturnType<typeof setTimeout> | null = null
let mdsProgressTimer: ReturnType<typeof setInterval> | null = null
const resizeHandler = () => chart?.resize()

// MDS progress
const mdsProgress = ref(0)
const showHint = ref(true)

// Vector preview
const previewDimCount = ref(10)
const normMode = ref<'per-vector' | 'global'>('per-vector')
const fullVectorVisible = ref(false)
const showSelectedOnly = ref(false)

const vectorFingerprintTooltip = '向量是知识的“语义指纹”，768 个维度编码了知识的语义特征。正负值表示该维度上的“激活”方向，绝对值越大表示该特征越显著。'

// WebGL detection
const webglSupported = checkWebGL()

const POLL_INTERVAL = 3000
const MAX_POLL_RETRIES = 20

// ======================== Computed ========================

const vectorizedCount = computed(() => points.value.filter(p => p.vectorStatus === 'vectorized').length)
const failedCount = computed(() => points.value.filter(p => p.vectorStatus === 'failed').length)
const multimodalCount = computed(() => points.value.filter(p => p.contentType === 'multimodal').length)
const textCount = computed(() => points.value.filter(p => p.contentType === 'text').length)

const mdsDisabled = computed(() => {
  if (total.value < 2) return true
  if (total.value > 500) return true
  return false
})

const mdsDisabledReason = computed(() => {
  if (total.value < 2) return 'MDS 需要至少 2 条向量'
  if (total.value > 500) return 'MDS 上限 500 条，当前共 ' + total.value + ' 条'
  return ''
})

const computationHint = computed(() => {
  if (algorithm.value !== 'mds') return ''
  const n = total.value
  if (n > 400) return 'MDS 计算 ' + n + ' 条向量预计需要 2-3 分钟，请耐心等待'
  if (n > 200) return 'MDS 计算 ' + n + ' 条向量预计需要 1-2 分钟'
  if (n > 50) return 'MDS 计算 ' + n + ' 条向量预计需要 30-60 秒'
  return ''
})

const vectorPreviewSlice = computed(() => {
  return pointDetail.value?.vectorPreview?.slice(0, previewDimCount.value) || []
})

const vectorSummary = computed(() => {
  const vec = pointDetail.value?.fullVector
  if (!vec || vec.length === 0) return { positiveCount: 0, negativeCount: 0, magnitude: 0, activationRate: '0' }

  let positiveCount = 0
  let negativeCount = 0
  let sumSq = 0
  let activeCount = 0

  for (const v of vec) {
    if (v > 0) positiveCount++
    else if (v < 0) negativeCount++
    sumSq += v * v
    if (Math.abs(v) > 1e-6) activeCount++
  }

  const magnitude = Math.sqrt(sumSq)
  const activationRate = ((activeCount / vec.length) * 100).toFixed(1)
  return { positiveCount, negativeCount, magnitude, activationRate }
})

const globalMaxAbs = computed(() => pointDetail.value?.globalMaxAbs || 1.0)

function isZeroVector(vec: number[]): boolean {
  return vec.every(v => v === 0)
}

function normPercent(v: number): number {
  const vec = pointDetail.value?.fullVector || pointDetail.value?.vectorPreview
  if (!vec || vec.length === 0 || isZeroVector(vec)) return 0

  let maxAbs: number
  if (normMode.value === 'global') {
    maxAbs = globalMaxAbs.value
  } else {
    // per-vector: 用完整 768 维向量内绝对最大值归一化
    let m = 0
    for (const d of vec) { const a = Math.abs(d); if (a > m) m = a }
    maxAbs = Math.max(m, 1e-8)
  }
  return parseFloat((Math.abs(v) / maxAbs * 100).toFixed(1))
}

const sortedNeighbors = computed(() => {
  const list = pointDetail.value?.neighbors || []
  return [...list]
    .filter(n => !isMojibake(n.title))
    .sort((a, b) => {
      if (a.isZeroVector && !b.isZeroVector) return 1
      if (!a.isZeroVector && b.isZeroVector) return -1
      return b.score - a.score
    })
})

// Similarity lines count and overflow
const similarityLinesCount = computed(() => {
  const coordMap = new Map(points.value.map(p => [p.id, true]))
  let count = 0
  for (const [idStr, neighbors] of Object.entries(similarities.value)) {
    const sourceId = Number(idStr)
    for (const n of neighbors) {
      if (n.score < similarityThreshold.value) continue
      if (sourceId >= n.id) continue
      if (coordMap.has(sourceId) && coordMap.has(n.id)) count++
    }
  }
  return count
})

const maxLines = computed(() => {
  const n = points.value.length
  const baseCap = viewMode.value === '3d' ? 150 : 300
  if (n <= 10) return Math.min(baseCap, n * 2)
  return Math.min(baseCap * 2, Math.max(baseCap, Math.round(n * 0.6)))
})

const similarityOverflow = computed(() => similarityLinesCount.value > maxLines.value * 1.2)

const similarityOverflowMsg = computed(() => {
  if (!similarityOverflow.value) return ''
  const totalCandidates = similarityLinesCount.value
  return `相似连线共 ${totalCandidates} 条候选，已显示相似度最高的 ${maxLines.value} 条。调高阈值可减少连线。`
})

// ======================== Lifecycle ========================

onMounted(async () => {
  window.addEventListener('resize', resizeHandler)
  try {
    const res: any = await categoryApi.tree()
    const flatten = (cats: any[]): any[] => {
      const result: any[] = []
      for (const c of cats) {
        result.push(c)
        if (c.children?.length) result.push(...flatten(c.children))
      }
      return result
    }
    categories.value = flatten(res.data?.data || [])
    if (categories.value.length > 0) {
      selectedCategoryId.value = categories.value[0].id
      await loadData()
    }
  } catch { /* ignore */ }
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
  if (debounceTimer) clearTimeout(debounceTimer)
  if (mdsProgressTimer) clearInterval(mdsProgressTimer)
  window.removeEventListener('resize', resizeHandler)
  chart?.dispose()
  chart = null
})

// ======================== Data Loading ========================

async function loadData() {
  if (!selectedCategoryId.value) return
  loading.value = true
  noVectorData.value = false
  try {
    const res: any = await knowledgeApi.getVisualization(selectedCategoryId.value, {
      page: sampleMode.value ? 1 : page.value,
      size: size.value,
      sample: sampleMode.value,
      algorithm: algorithm.value,
    })
    const data = res.data || {}
    points.value = (data.points || []) as VizPoint[]
    total.value = data.total || 0
    hasData.value = data.hasData !== false
    similarities.value = data.similarities || {}
  } catch (e: any) {
    // Check for NO_VECTOR_DATA error from backend
    if (e?.response?.data?.errorCode === 'NO_VECTOR_DATA') {
      noVectorData.value = true
    }
    ElMessage.error('加载可视化数据失败')
  } finally {
    loading.value = false
    await nextTick()
    renderChart()
  }
}

async function recomputeCurrent() {
  if (!selectedCategoryId.value) return
  recomputing.value = true
  showHint.value = true

  // Front-end MDS check
  if (algorithm.value === 'mds') {
    const currentCount = sampleMode.value ? points.value.length : total.value
    if (currentCount > 500) {
      ElMessage.warning('当前数据 ' + currentCount + ' 条超出 MDS 上限 500 条')
      recomputing.value = false
      return
    }
    if (currentCount < 2) {
      ElMessage.warning('MDS 至少需要 2 条向量')
      recomputing.value = false
      return
    }
    startMDSProgress()
  }

  try {
    const res: any = await knowledgeApi.recomputeVisualization(selectedCategoryId.value, { algorithm: algorithm.value })
    if (res.data?.status === 'computing') {
      ElMessage.info('已有重算任务进行中...')
      pollRetryCount = 0
      pollTimer = setInterval(pollRecomputeStatus, POLL_INTERVAL)
    } else {
      await loadData()
    }
  } catch (e: any) {
    const errorCode = e?.response?.data?.errorCode
    if (errorCode === 'MDS_TOO_MANY_POINTS') {
      ElMessage.warning('MDS 仅支持最多 500 条向量，是否切换 PCA？')
    } else if (errorCode === 'MDS_TOO_FEW_POINTS') {
      ElMessage.warning('MDS 至少需要 2 条向量')
    } else if (errorCode === 'MDS_ALL_IDENTICAL') {
      ElMessage.warning('所有向量完全相同，MDS 无法计算')
    } else if (errorCode === 'MDS_ALL_ZERO') {
      ElMessage.warning('所有向量均为空向量，无法执行降维')
    } else if (errorCode === 'DR_COMPUTE_FAILED') {
      ElMessage.error('降维计算失败，请重试')
    } else {
      ElMessage.error('重算失败')
    }
  } finally {
    recomputing.value = false
    stopMDSProgress()
  }
}

async function pollRecomputeStatus() {
  pollRetryCount++
  if (pollRetryCount > MAX_POLL_RETRIES) {
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
    ElMessage.error('重算超时，请重试')
    return
  }
  try {
    const res: any = await knowledgeApi.getVisualization(selectedCategoryId.value!, {
      algorithm: algorithm.value,
      page: 1, size: 1, sample: false,
    })
    if (res.data?.status === 'done' || res.data?.hasData) {
      if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
      await loadData()
    }
  } catch { /* retry */ }
}

// ======================== MDS Progress ========================

function startMDSProgress() {
  mdsProgress.value = 0
  if (mdsProgressTimer) clearInterval(mdsProgressTimer)
  mdsProgressTimer = setInterval(() => {
    mdsProgress.value = Math.min(90, mdsProgress.value + 5)
    if (mdsProgress.value >= 90 && mdsProgressTimer) {
      clearInterval(mdsProgressTimer)
      mdsProgressTimer = null
    }
  }, 3000)
}

function stopMDSProgress() {
  if (mdsProgressTimer) { clearInterval(mdsProgressTimer); mdsProgressTimer = null }
  mdsProgress.value = 100
  setTimeout(() => { mdsProgress.value = 0 }, 800)
}

function mdsProgressFormat(p: number) {
  return p >= 100 ? '计算完成' : '计算中 ' + p + '%'
}

// ======================== Event Handlers ========================

function onCategoryChange(newId: number) {
  detailVisible.value = false
  selectedPoint.value = null
  pointDetail.value = null
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null; pollRetryCount = 0 }
  if (mdsProgressTimer) { clearInterval(mdsProgressTimer); mdsProgressTimer = null }
  page.value = 1
  selectedCategoryId.value = newId
  loadData()
}

function onAlgorithmChange(newAlg: string) {
  detailVisible.value = false
  selectedPoint.value = null
  pointDetail.value = null
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null; pollRetryCount = 0 }
  algorithm.value = newAlg
  loadData()
}

function onSampleChange(val: boolean) {
  page.value = 1
  if (val) {
    loadData()
  } else {
    loadData()
  }
}

function onThresholdInput(val: number) {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    similarityThreshold.value = val
    renderChart()
  }, 150)
}

function onThresholdChange(val: number) {
  if (debounceTimer) { clearTimeout(debounceTimer); debounceTimer = null }
  similarityThreshold.value = val
  renderChart()
}

function onDrawerClose() {
  selectedPoint.value = null
  pointDetail.value = null
  highlightedNeighborId.value = null
  chart?.dispatchAction({ type: 'downplay' })
}

// ======================== Neighbor → Chart Highlight ========================

function onNeighborClick(neighborId: number) {
  const entry = pointIndexMap.get(neighborId)
  if (!entry || !chart) return
  // Downplay all, then highlight the specific point
  chart.dispatchAction({ type: 'downplay' })
  chart.dispatchAction({ type: 'highlight', seriesIndex: entry.seriesIdx, dataIndex: entry.dataIdx })
  highlightedNeighborId.value = neighborId
}

function onNeighborHover(neighborId: number | null) {
  if (!chart) return
  if (neighborId === null) {
    // Restore clicked highlight if any, otherwise downplay all
    chart.dispatchAction({ type: 'downplay' })
    if (highlightedNeighborId.value !== null) {
      const entry = pointIndexMap.get(highlightedNeighborId.value)
      if (entry) {
        chart.dispatchAction({ type: 'highlight', seriesIndex: entry.seriesIdx, dataIndex: entry.dataIdx })
      }
    }
    return
  }
  const entry = pointIndexMap.get(neighborId)
  if (!entry) return
  chart.dispatchAction({ type: 'downplay' })
  chart.dispatchAction({ type: 'highlight', seriesIndex: entry.seriesIdx, dataIndex: entry.dataIdx })
}

// ======================== Chart Rendering ========================

const CONTENT_TYPE_COLORS: Record<string, string> = {
  vectorized: '#3fb950', failed: '#f85149', pending: '#d29922',
}

const PALETTES = {
  text: { vectorized: '#3fb950', failed: '#f85149', pending: '#d29922' },
  multimodal: { vectorized: '#58a6ff', failed: '#f0883e', pending: '#db6d28' },
}

function renderChart() {
  if (!chartRef.value || points.value.length === 0) return
  chart?.dispose()
  chart = echarts.init(chartRef.value)
  if (viewMode.value === '2d') render2D(chart)
  else render3D(chart)
}

function buildLinesData() {
  const coordMap = new Map(points.value.map(p => [p.id, { x: p.x, y: p.y, z: p.z }]))
  const allLines: Array<{ coords: [[number, number], [number, number]]; score: number }> = []
  const seen = new Set<string>()

  for (const [idStr, neighbors] of Object.entries(similarities.value)) {
    const sourceId = Number(idStr)
    const source = coordMap.get(sourceId)
    if (!source) continue

    // Selected-only mode
    if (showSelectedOnly.value && selectedPoint.value) {
      if (sourceId !== selectedPoint.value.id &&
          !neighbors.some(n => n.id === selectedPoint.value!.id)) continue
    }

    for (const n of neighbors) {
      if (n.score < similarityThreshold.value) continue
      const key = sourceId < n.id ? sourceId + '-' + n.id : n.id + '-' + sourceId
      if (seen.has(key)) continue
      seen.add(key)
      const target = coordMap.get(n.id)
      if (!target) continue
      allLines.push({ coords: [[source.x, source.y], [target.x, target.y]], score: n.score })
    }
  }

  // Apply adaptive cap
  if (allLines.length > maxLines.value) {
    allLines.sort((a, b) => b.score - a.score)
    return allLines.slice(0, maxLines.value)
  }
  return allLines
}

function render2D(chart: echarts.ECharts) {
  const textPoints = points.value.filter(p => p.contentType !== 'multimodal')
  const multimodalPoints = points.value.filter(p => p.contentType === 'multimodal')
  const linesData = buildLinesData()
  const series: any[] = []
  pointIndexMap.clear()

  // Similarity lines
  if (linesData.length > 0) {
    series.push({
      type: 'lines', coordinateSystem: 'cartesian2d',
      data: linesData.map(d => ({ coords: d.coords, score: d.score })),
      lineStyle: { width: 1.5, opacity: 0.25, color: '#58A6FF' },
      tooltip: { formatter: (p: any) => '相似度: ' + ((p.data.score ?? 0) * 100).toFixed(1) + '%' },
      z: 1,
    })
  }

  function makeSeries(name: string, data: VizPoint[], symbol: string, palette: Record<string, string>, seriesIdx: number) {
    data.forEach((p, i) => pointIndexMap.set(p.id, { seriesIdx, dataIdx: i }))
    return {
      type: 'scatter', name,
      data: data.map(p => ({
        value: [p.x, p.y], knowledgeId: p.id, title: p.title,
        contentType: p.contentType, vectorStatus: p.vectorStatus, vizStatus: p.vizStatus,
        isZeroVector: p.isZeroVector,
      })),
      symbol, symbolSize: name === '图文' ? 14 : 12,
      itemStyle: {
        color: (params: any) => {
          const s = params.data.vizStatus === 'vectorized' ? 'vectorized'
            : params.data.vizStatus === 'failed' ? 'failed' : 'pending'
          return palette[s]
        },
        opacity: 0.85, borderColor: '#fff', borderWidth: 1,
      },
      emphasis: {
        itemStyle: { opacity: 1, borderWidth: 3 },
        label: { show: true, formatter: (p: any) => p.data.title, position: 'top', fontSize: 12, color: '#fff' },
      },
      z: 2,
    }
  }

  if (textPoints.length > 0) series.push(makeSeries('纯文本', textPoints, 'circle', PALETTES.text, series.length))
  if (multimodalPoints.length > 0) series.push(makeSeries('图文', multimodalPoints, 'diamond', PALETTES.multimodal, series.length))

  chart.setOption({
    backgroundColor: '#1a1a2e',
    grid: { left: 60, right: 40, top: 40, bottom: 40 },
    legend: {
      data: ['相似连线', '纯文本', '图文'],
      textStyle: { color: '#999' }, icon: 'circle', top: 0,
      selected: { '相似连线': linesData.length > 0 },
      formatter: (name: string) => {
        if (name === '纯文本') return '纯文本（● 圆圈，颜色表示状态）'
        if (name === '图文') return '图文（◆ 菱形，颜色表示状态）'
        return name + '（蓝色线条）'
      },
    },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#2a2a4a' } }, splitNumber: 4, axisLabel: { color: '#999', formatter: (v: number) => +v.toFixed(3) + '' } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#2a2a4a' } }, splitNumber: 4, axisLabel: { color: '#999', formatter: (v: number) => +v.toFixed(3) + '' } },
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        if (params.seriesName === '相似连线') return '相似度: ' + ((params.data.score ?? 0) * 100).toFixed(1) + '%'
        const d = params.data
        if (d.isZeroVector) return '<strong>' + (d.title || '') + '</strong><br/>⚠️ 空向量（全零）'
        const x = d.value?.[0] ?? 0
        const y = d.value?.[1] ?? 0
        return '<strong>' + d.title + '</strong><br/>ID: ' + (d.knowledgeId ?? d.id) + '<br/>(' + x.toFixed(4) + ', ' + y.toFixed(4) + ')<br/>类型: ' + (d.contentType === 'multimodal' ? '图文' : '纯文本')
      },
    },
    series,
  })

  chart.on('click', onChartClick)
}

function render3D(chart: echarts.ECharts) {
  const linesData = buildLinesData()
  const series: any[] = []
  pointIndexMap.clear()

  if (linesData.length > 0) {
    series.push({
      type: 'lines3D', coordinateSystem: 'cartesian3D',
      data: linesData.map(d => ({ coords: [d.coords[0], d.coords[1]], score: d.score })),
      lineStyle: { width: 1.5, opacity: 0.25, color: '#58A6FF' },
    })
  }

  series.push({
    type: 'scatter3D', coordinateSystem: 'cartesian3D',
    data: points.value.map(p => ({
      value: [p.x, p.y, p.z], knowledgeId: p.id, title: p.title,
      contentType: p.contentType, vectorStatus: p.vectorStatus, vizStatus: p.vizStatus,
      isZeroVector: p.isZeroVector,
    })),
    symbol: 'circle', symbolSize: 8,
    itemStyle: {
      color: (params: any) => {
        const palette = params.data.contentType === 'multimodal' ? PALETTES.multimodal : PALETTES.text
        const s = params.data.vizStatus === 'vectorized' ? 'vectorized'
          : params.data.vizStatus === 'failed' ? 'failed' : 'pending'
        return palette[s]
      },
      opacity: 0.85,
    },
  })

  // Build pointIndexMap for neighbor→chart highlight
  const scatterIdx = series.length - 1
  points.value.forEach((p, i) => pointIndexMap.set(p.id, { seriesIdx: scatterIdx, dataIdx: i }))

  chart.setOption({
    backgroundColor: '#1a1a2e',
    grid3D: {
      boxWidth: 150, boxHeight: 150, boxDepth: 150,
      viewControl: { autoRotate: false, distance: 250 },
      light: { main: { intensity: 1.2 }, ambient: { intensity: 0.6 } },
    },
    xAxis3D: { type: 'value', splitLine: { lineStyle: { color: '#2a2a4a' } }, splitNumber: 4, axisLabel: { color: '#999', formatter: (v: number) => +v.toFixed(3) + '' } },
    yAxis3D: { type: 'value', splitLine: { lineStyle: { color: '#2a2a4a' } }, splitNumber: 4, axisLabel: { color: '#999', formatter: (v: number) => +v.toFixed(3) + '' } },
    zAxis3D: { type: 'value', splitLine: { lineStyle: { color: '#2a2a4a' } }, splitNumber: 4, axisLabel: { color: '#999', formatter: (v: number) => +v.toFixed(3) + '' } },
    legend: {
      data: ['相似连线', '纯文本', '图文'],
      textStyle: { color: '#999' }, top: 0,
      formatter: (name: string) => {
        if (viewMode.value === '3d' && (name === '纯文本' || name === '图文')) return name + ' (3D ●)'
        return name
      },
    },
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        if (params.seriesName === '相似连线') return '相似度: ' + ((params.data.score ?? 0) * 100).toFixed(1) + '%'
        const d = params.data
        if (d.isZeroVector) return '<strong>' + (d.title || '') + '</strong><br/>⚠️ 空向量（全零）'
        return '<strong>' + d.title + '</strong><br/>ID: ' + (d.knowledgeId ?? d.id) + '<br/>('
          + (d.value?.[0] ?? 0).toFixed(4) + ', ' + (d.value?.[1] ?? 0).toFixed(4) + ', ' + (d.value?.[2] ?? 0).toFixed(4) + ')<br/>'
          + '类型: ' + (d.contentType === 'multimodal' ? '图文' : '纯文本')
      },
    },
    series,
  })

  chart.on('click', onChartClick)
}

function onChartClick(params: any) {
  if (params.componentType !== 'series' || params.seriesName === '相似连线') return
  const d = params.data
  const pointId = d?.knowledgeId ?? d?.id
  if (!pointId) return  // 防护：非数据点（如坐标轴、背景）点击不触发
  // Clear any neighbor-sourced highlight
  highlightedNeighborId.value = null
  chart?.dispatchAction({ type: 'downplay' })
  selectedPoint.value = {
    id: pointId, title: d.title,
    x: d.value?.[0] ?? 0, y: d.value?.[1] ?? 0, z: d.value?.[2] ?? 0,
    vectorStatus: d.vectorStatus, vizStatus: d.vizStatus,
    contentType: d.contentType, isZeroVector: d.isZeroVector,
  }
  loadPointDetail(pointId)
  if (showSelectedOnly.value) renderChart()
}

function resetView() {
  if (!chart || viewMode.value !== '3d') return
  chart.setOption({
    grid3D: { viewControl: { alpha: 20, beta: 30, distance: 200 } }
  })
}

// ======================== Point Detail ========================

async function loadPointDetail(knowledgeId: number | undefined) {
  if (!knowledgeId) return
  detailVisible.value = true
  pointDetail.value = null
  try {
    const res: any = await knowledgeApi.getPointDetail(knowledgeId!, { algorithm: algorithm.value })
    pointDetail.value = res.data as PointDetail
  } catch {
    ElMessage.error('加载详情失败')
    pointDetail.value = null
  }
}

// ======================== Vector Copy ========================

function copyDimValue(index: number, value: number) {
  const text = value.toFixed(6)
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('dim ' + index + ': ' + text + ' 已复制')
  }).catch(() => {
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('dim ' + index + ': 已复制')
  })
}

function copyFullVector() {
  const vec = pointDetail.value?.fullVector
  if (!vec || vec.length === 0) { ElMessage.warning('无向量数据可复制'); return }
  const text = vec.map((v, i) => 'dim' + i + '\t' + v).join('\n')
  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('已复制 ' + vec.length + ' 维数据')
  }).catch(() => {
    const ta = document.createElement('textarea')
    ta.value = text
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    ElMessage.success('已复制 ' + vec.length + ' 维数据')
  })
}

// ======================== Utilities ========================

function checkWebGL(): boolean {
  try {
    const canvas = document.createElement('canvas')
    return !!(canvas.getContext('webgl') || (canvas.getContext as any)('experimental-webgl'))
  } catch { return false }
}

function vizStatusText(status: string): string {
  switch (status) {
    case 'vectorized': return '已向量化'
    case 'failed': return '失败'
    case 'pending': return '等待中'
    case 'processing': return '处理中'
    default: return status || '-'
  }
}

function goBack() { router.back() }

/** 检测字符串是否含乱码（Extended Latin 充斥中文语境 = mojibake） */
function isMojibake(text: string | null | undefined): boolean {
  if (!text) return false
  // 统计非 ASCII 且非 CJK 的字符占比
  let weird = 0
  for (const ch of text) {
    const code = ch.charCodeAt(0)
    if (code > 0x7f && code < 0x3000) weird++
  }
  // 超过 30% 字符落在 Latin-1 Supplement / Extended Latin 区域 → 极可能是乱码
  return weird > 0 && (weird / text.length) > 0.3
}

function drawHeatmap() {
  const canvas = heatmapCanvas.value
  const vec = pointDetail.value?.fullVector
  if (!canvas || !vec || vec.length < 10) return

  // Resize canvas to actual display width (CSS may scale)
  const rect = canvas.parentElement?.getBoundingClientRect()
  const displayWidth = rect ? Math.min(rect.width, 768) : 768
  const displayHeight = 16
  // Keep source data at 1:1 for crispness, let CSS handle fit
  canvas.width = 768
  canvas.height = displayHeight

  const ctx = canvas.getContext('2d')!
  const w = 768
  const h = displayHeight

  // Find global max abs for color scaling
  let maxAbs = 0
  for (const v of vec) {
    const abs = Math.abs(v)
    if (abs > maxAbs) maxAbs = abs
  }
  if (maxAbs < 1e-8) maxAbs = 1

  // Draw per-pixel columns
  const imageData = ctx.createImageData(w, h)
  for (let x = 0; x < w; x++) {
    const val = x < vec.length ? vec[x] : 0
    const intensity = Math.min(Math.abs(val) / maxAbs, 1)
    // Green for positive, red for negative, dark gray for zero
    let r: number, g: number, b: number
    if (val > 0) {
      r = Math.round(30 * (1 - intensity))
      g = Math.round(63 + 192 * intensity)
      b = Math.round(30 * (1 - intensity))
    } else if (val < 0) {
      r = Math.round(63 + 192 * intensity)
      g = Math.round(30 * (1 - intensity))
      b = Math.round(30 * (1 - intensity))
    } else {
      r = 40; g = 40; b = 60  // dark base
    }

    for (let y = 0; y < h; y++) {
      const idx = (y * w + x) * 4
      imageData.data[idx] = r
      imageData.data[idx + 1] = g
      imageData.data[idx + 2] = b
      imageData.data[idx + 3] = 255
    }
  }
  ctx.putImageData(imageData, 0, 0)
}

// Redraw heatmap when point detail loads
watch(pointDetail, () => {
  nextTick(() => drawHeatmap())
})
</script>

<style scoped>
.knowledge-viz { padding: 20px; color: #e0e0e0; }
.viz-header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 16px; background: #16213e; padding: 12px 16px; border-radius: 8px;
}
.viz-header-left { display: flex; align-items: center; gap: 12px; }
.viz-header h2 { margin: 0; font-size: 18px; color: #c9d1d9; }
.back-btn { color: #8b949e !important; font-size: 13px; }
.back-btn:hover { color: #58a6ff !important; }
.viz-actions { display: flex; gap: 8px; align-items: center; }
.viz-loading { padding: 40px; }

/* Stats */
.viz-stats { display: flex; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; align-items: center; }
.stat-card { padding: 8px 16px; min-width: 100px; background: #16213e; border: 1px solid #2a2a4a; }
.stat-label { font-size: 12px; color: #999; display: flex; align-items: center; }
.stat-value { font-size: 22px; font-weight: 700; color: #3fb950; }
.stats-scope { font-size: 11px; color: #666; margin-left: auto; }

/* Controls */
.viz-controls {
  display: flex; gap: 8px; margin-bottom: 8px; flex-wrap: wrap; align-items: center;
  background: #16213e; padding: 8px 12px; border-radius: 8px;
}
.viz-controls-group { display: flex; gap: 6px; align-items: center; }
.viz-label { font-size: 12px; color: #999; white-space: nowrap; }
.slider-value { font-size: 12px; color: #ccc; font-family: monospace; min-width: 36px; text-align: right; }
.mds-disabled-hint { font-size: 11px; color: #d29922; margin: 0; }

/* Pagination */
.viz-pagination { display: flex; justify-content: center; margin-bottom: 8px; }

/* Chart */
.viz-chart { width: 100%; height: 600px; border-radius: 8px; overflow: hidden; }
@media (max-width: 768px) { .viz-chart { height: 400px; } }

/* Color/Shape Legend Guide */
.chart-legend-guide {
  display: flex; flex-wrap: wrap; align-items: center; gap: 12px;
  margin-top: 8px; padding: 6px 12px; background: #16213e; border-radius: 6px;
  font-size: 12px; color: #999;
}
.legend-item { display: inline-flex; align-items: center; gap: 4px; white-space: nowrap; }
.legend-dot { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
.legend-shape { font-size: 14px; line-height: 1; }
.legend-line { font-size: 14px; letter-spacing: -2px; color: #58A6FF; }
.legend-divider { width: 1px; height: 14px; background: #2a2a4a; }

/* MDS Progress */
.mds-progress { margin-bottom: 8px; padding: 4px 12px; }

/* Drawer */
.drawer-loading { padding: 20px; }
.drawer-section {
  display: flex; align-items: center; gap: 8px;
  margin: 16px 0 8px; font-size: 13px; color: #999;
}
.drawer-coords { font-size: 12px; font-family: monospace; }
.content-preview { font-size: 13px; line-height: 1.6; color: #c9d1d9; white-space: pre-wrap; max-height: 150px; overflow-y: auto; }

/* Vector bars */
.vector-bars { margin-top: 4px; }
.vector-bar-item {
  display: flex; align-items: center; gap: 6px; height: 22px; font-size: 12px;
}
.dim-label { width: 45px; flex-shrink: 0; color: #999; text-align: right; font-size: 11px; }
.bar { flex: 1; height: 6px; background: #2a2a4a; border-radius: 3px; overflow: hidden; }
.bar-fill { height: 100%; border-radius: 3px; transition: width 0.15s; }
.bar-fill.positive { background: #3fb950; }
.bar-fill.negative { background: #f85149; }
.dim-value { width: 70px; flex-shrink: 0; text-align: right; font-family: monospace; font-size: 11px; color: #c9d1d9; }

/* Full vector grid */
.full-vector-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 3px;
  max-height: 70vh; overflow-y: auto; font-family: monospace; font-size: 12px;
}
.vector-cell { display: flex; gap: 6px; padding: 2px 4px; background: #f6f8fa; border-radius: 2px; }
.cell-index { color: #999; width: 28px; flex-shrink: 0; text-align: right; }
.cell-value.pos { color: #3fb950; }
.cell-value.neg { color: #f85149; }

/* Neighbors */
.neighbor-item {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 8px; border-radius: 4px; transition: background 0.15s;
}
.neighbor-item:hover { background: #2a2a50; }
.neighbor-highlighted { background: #1a3a5c !important; outline: 1px solid #58a6ff; }
.neighbor-zero { opacity: 0.5; }
.neighbor-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.neighbor-empty { color: #666; font-size: 13px; padding: 12px; text-align: center; }

/* Algo info */
.algo-info { font-size: 12px; line-height: 1.5; }
.algo-info ul { margin: 4px 0; padding-left: 16px; }

/* Vector summary (semantic fingerprint) */
.vector-summary { margin-top: 4px; }
.vector-summary-stats { display: flex; gap: 6px; flex-wrap: wrap; }
.summary-stat {
  flex: 1; min-width: 60px;
  background: #16213e; border: 1px solid #2a2a4a; border-radius: 6px;
  padding: 6px 8px; text-align: center;
}
.summary-stat-value { font-size: 18px; font-weight: 700; color: #58a6ff; }
.summary-stat-label { font-size: 10px; color: #999; margin-top: 2px; }
.vector-summary-desc {
  font-size: 12px; line-height: 1.5; color: #8b949e;
  margin: 8px 0 0; padding: 6px 8px; background: #0d1117; border-radius: 4px;
}

/* Heatmap */
.vector-heatmap { margin-top: 8px; }
.heatmap-gradient {
  display: flex; align-items: center; gap: 4px;
  font-size: 11px; color: #999;
}
.heatmap-canvas {
  flex: 1; height: 16px; border-radius: 3px;
  image-rendering: pixelated; image-rendering: crisp-edges;
}
.heatmap-ticks {
  display: flex; justify-content: space-between;
  font-size: 9px; color: #666; margin-top: 2px; padding: 0 2px;
}

/* Responsive */
@media (max-width: 900px) {
  .viz-header { flex-direction: column; align-items: stretch; gap: 8px; }
  .viz-actions { width: 100%; flex-wrap: wrap; }
  .viz-actions .el-select { flex: 1; min-width: 0; }
  .chart-legend-guide { font-size: 11px; gap: 8px; }
}
@media (max-width: 600px) {
  .knowledge-viz { padding: 12px; }
  .viz-controls { flex-direction: column; align-items: stretch; }
  .viz-controls-group { flex-wrap: wrap; }
  .stats-scope { margin-left: 0; width: 100%; text-align: left; }
}
</style>
