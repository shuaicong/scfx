<template>
  <div class="knowledge-viz">
    <!-- Header -->
    <div class="viz-header">
      <h2>向量可视化</h2>
      <div class="viz-actions">
        <el-select
          v-model="selectedCategoryId"
          placeholder="选择分类"
          style="width: 220px"
          @change="loadData"
        >
          <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
        </el-select>
        <el-button type="primary" :disabled="!selectedCategoryId || loading" @click="loadData">
          刷新
        </el-button>
        <el-button :disabled="!selectedCategoryId || loading" @click="recomputePCA">
          重算 PCA
        </el-button>
      </div>
    </div>

    <div v-if="loading" class="viz-loading"><el-skeleton :rows="6" animated /></div>

    <template v-else-if="!selectedCategoryId">
      <el-empty description="请先选择一个分类" />
    </template>

    <template v-else-if="points.length === 0 && total === 0">
      <el-empty description="该分类暂无可视化数据">
        <template #description>
          <p>该分类下的知识还未完成向量化，或 PCA 尚未计算。</p>
          <p>请先向量化知识，然后点击「重算 PCA」生成可视化坐标。</p>
        </template>
      </el-empty>
    </template>

    <template v-else>
      <!-- Stats bar -->
      <div class="viz-stats">
        <el-tag type="info" effect="plain">
          {{ sampleMode
            ? `随机抽样 ${points.length} / 共 ${total} 条`
            : `第 ${page} 页 / 共 ${Math.ceil(total / size)} 页（${total} 条）`
          }}
        </el-tag>
        <el-tag type="success">已向量化 {{ vectorizedCount }}</el-tag>
        <el-tag type="danger">失败 {{ failedCount }}</el-tag>
        <el-tag type="warning">
          <span class="dot dot-multimodal" />图文 {{ multimodalCount }}
        </el-tag>
        <el-tag>
          <span class="dot dot-text" />纯文本 {{ textCount }}
        </el-tag>
        <div style="flex:1" />
        <el-tag v-if="similarityLinesCount > 0" type="info" effect="plain">
          相似连线 {{ similarityLinesCount }}
        </el-tag>
      </div>

      <!-- Controls -->
      <div class="viz-controls">
        <div class="viz-controls-group">
          <el-checkbox v-model="sampleMode" @change="loadData" size="small">
            随机抽样
          </el-checkbox>
          <el-select v-model="size" size="small" style="width: 100px" @change="loadData">
            <el-option label="50 条" :value="50" />
            <el-option label="100 条" :value="100" />
            <el-option label="200 条" :value="200" />
            <el-option label="500 条" :value="500" />
          </el-select>
          <el-button v-if="sampleMode" size="small" @click="loadData" title="重新随机抽样">
            重新抽样
          </el-button>
        </div>
        <div class="viz-controls-group">
          <el-button disabled size="small" title="3D 视图开发中">
            3D
          </el-button>
          <span class="viz-label">相似阈值</span>
          <el-slider
            v-model="similarityThreshold"
            :min="0.5"
            :max="1"
            :step="0.01"
            style="width: 120px"
            @change="renderChart"
          />
        </div>
      </div>

      <!-- Pagination -->
      <div v-if="!sampleMode && total > size" class="viz-pagination">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="prev, pager, next"
          small
          @current-change="loadData"
        />
      </div>

      <!-- Chart -->
      <div class="viz-chart" ref="chartRef" />

      <!-- Detail panel -->
      <div v-if="selectedPoint" class="viz-detail">
        <div class="viz-detail-header">
          <h4>选中条目</h4>
          <el-button size="small" text @click="selectedPoint = null">✕</el-button>
        </div>
        <div class="viz-detail-body">
          <div class="viz-detail-info">
            <p><strong>标题:</strong> {{ selectedPoint.title }}</p>
            <p><strong>ID:</strong> {{ selectedPoint.id }}
              <el-tag
                :type="selectedPoint.contentType === 'multimodal' ? 'warning' : 'info'"
                size="small" style="margin-left:8px"
              >
                {{ selectedPoint.contentType === 'multimodal' ? '图文' : '纯文本' }}
              </el-tag>
            </p>
            <p>
              <strong>检索:</strong>
              <el-tag :type="selectedPoint.vectorStatus === 'vectorized' ? 'success' : 'danger'" size="small">
                {{ selectedPoint.vectorStatus }}
              </el-tag>
              &nbsp;
              <strong>可视化:</strong>
              <el-tag :type="selectedPoint.vizStatus === 'vectorized' ? 'success' : selectedPoint.vizStatus === 'failed' ? 'danger' : 'warning'" size="small">
                {{ selectedPoint.vizStatus }}
              </el-tag>
            </p>
          </div>
          <div v-if="selectedPointNeighbors.length" class="viz-detail-similar">
            <h5>相似条目</h5>
            <div v-for="n in selectedPointNeighbors" :key="n.id" class="viz-similar-item" @click="highlightSimilarPair(selectedPoint.id, n.id)">
              <span class="viz-similar-title">{{ getPointTitle(n.id) || `#${n.id}` }}</span>
              <el-progress
                :percentage="Math.round(n.score * 100)"
                :status="n.score > 0.9 ? 'success' : n.score > 0.8 ? '' : 'warning'"
                :stroke-width="6"
                style="width:100px"
              />
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { knowledgeApi } from '@/api/knowledge'
import { categoryApi } from '@/api/category'
import { ElMessage } from 'element-plus'

interface VizPoint {
  id: number
  title: string
  x: number
  y: number
  z: number
  vectorStatus: string
  vizStatus: string
  contentType: string
}

interface SimilarNeighbor {
  id: number
  score: number
}

interface Category {
  id: number
  name: string
}

// ======================== State ========================

const categories = ref<Category[]>([])
const selectedCategoryId = ref<number | null>(null)
const points = ref<VizPoint[]>([])
const loading = ref(false)
const selectedPoint = ref<VizPoint | null>(null)
const chartRef = ref<HTMLElement | null>(null)
let chart: echarts.ECharts | null = null

// Pagination & sampling
const page = ref(1)
const size = ref(200)
const total = ref(0)
const sampleMode = ref(false)
const similarityThreshold = ref(0.85)

// Similarities data from backend
const similarities = ref<Record<number, SimilarNeighbor[]>>({})

// ======================== Computed ========================

const vectorizedCount = computed(() =>
  points.value.filter(p => p.vectorStatus === 'vectorized').length
)
const failedCount = computed(() =>
  points.value.filter(p => p.vectorStatus === 'failed').length
)
const multimodalCount = computed(() =>
  points.value.filter(p => p.contentType === 'multimodal').length
)
const textCount = computed(() =>
  points.value.filter(p => p.contentType === 'text').length
)

const similarityLinesCount = computed(() => {
  const coordMap = new Map(points.value.map(p => [p.id, true]))
  let count = 0
  for (const [idStr, neighbors] of Object.entries(similarities.value)) {
    const sourceId = Number(idStr)
    for (const n of neighbors) {
      if (n.score < similarityThreshold.value) continue
      if (sourceId >= n.id) continue
      if (coordMap.has(sourceId) && coordMap.has(n.id)) {
        count++
      }
    }
  }
  return count
})

const selectedPointNeighbors = computed(() => {
  if (!selectedPoint.value) return []
  return (similarities.value[selectedPoint.value.id] || [])
    .filter(n => n.score >= similarityThreshold.value)
})

// ======================== Lifecycle ========================

onMounted(async () => {
  try {
    const res: any = await categoryApi.tree()
    const flatten = (cats: any[]): any[] => {
      const result: any[] = []
      for (const c of cats) {
        result.push(c)
        if (c.children && c.children.length) {
          result.push(...flatten(c.children))
        }
      }
      return result
    }
    categories.value = flatten(res.data || [])
  } catch {
    // ignore
  }
})

onUnmounted(() => {
  chart?.dispose()
})

// ======================== Data Loading ========================

async function loadData() {
  if (!selectedCategoryId.value) return
  loading.value = true
  try {
    const res: any = await knowledgeApi.getVisualization(selectedCategoryId.value, {
      page: page.value,
      size: size.value,
      sample: sampleMode.value,
    })
    const data = res.data || {}
    points.value = (data.points || []) as VizPoint[]
    total.value = data.total || 0
    similarities.value = data.similarities || {}
    await nextTick()
    renderChart()
  } catch {
    ElMessage.error('加载可视化数据失败')
  } finally {
    loading.value = false
  }
}

async function recomputePCA() {
  if (!selectedCategoryId.value) return
  loading.value = true
  try {
    await knowledgeApi.recomputeVisualization(selectedCategoryId.value)
    ElMessage.success('PCA 重算完成')
    await loadData()
  } catch {
    ElMessage.error('PCA 重算失败')
  } finally {
    loading.value = false
  }
}

// ======================== Chart Rendering ========================

const CONTENT_TYPE_COLORS: Record<string, string> = {
  vectorized: '#3fb950',
  failed: '#f85149',
  pending: '#d29922',
}

function renderChart() {
  if (!chartRef.value) return
  chart?.dispose()
  chart = echarts.init(chartRef.value)

  // Split points by content type
  const textPoints = points.value.filter(p => p.contentType !== 'multimodal')
  const multimodalPoints = points.value.filter(p => p.contentType === 'multimodal')

  // Build coordinate map
  const coordMap = new Map<number, { x: number; y: number }>()
  for (const p of points.value) {
    coordMap.set(p.id, { x: p.x, y: p.y })
  }

  // Build lines data from similarities (avoid A→B / B→A duplicates)
  const linesData: Array<{ coords: [[number, number], [number, number]]; score: number }> = []
  if (Object.keys(similarities.value).length > 0) {
    for (const [idStr, neighbors] of Object.entries(similarities.value)) {
      const sourceId = Number(idStr)
      const source = coordMap.get(sourceId)
      if (!source) continue
      for (const n of neighbors) {
        if (n.score < similarityThreshold.value) continue
        if (sourceId >= n.id) continue
        const target = coordMap.get(n.id)
        if (!target) continue
        linesData.push({
          coords: [[source.x, source.y], [target.x, target.y]],
          score: n.score,
        })
      }
    }
  }

  const series: echarts.EChartsOption['series'] = []

  // Series 0: Similarity lines (below scatter)
  if (linesData.length > 0) {
    series.push({
      type: 'lines',
      coordinateSystem: 'cartesian2d',
      data: linesData.map(d => ({
        coords: d.coords,
        score: d.score,
      })),
      lineStyle: {
        width: 1.5,
        opacity: 0.25,
        color: '#58A6FF',
      },
      tooltip: {
        formatter: (params: any) => {
          const d = params.data
          return `相似度: ${(d.score * 100).toFixed(1)}%`
        },
      },
      z: 1,
    })
  }

  // Helper to build a scatter series
  function makeScatterSeries(
    name: string,
    dataPoints: VizPoint[],
    symbol: string,
    symbolSize: number,
  ): any {
    return {
      type: 'scatter',
      name,
      data: dataPoints.map(p => ({
        value: [p.x, p.y],
        id: p.id,
        title: p.title,
        contentType: p.contentType,
        vectorStatus: p.vectorStatus,
        vizStatus: p.vizStatus,
      })),
      symbol,
      symbolSize,
      itemStyle: {
        color: (params: any) => {
          const status = params.data.vizStatus === 'vectorized'
            ? 'vectorized'
            : params.data.vizStatus === 'failed'
              ? 'failed'
              : 'pending'
          return CONTENT_TYPE_COLORS[status]
        },
        opacity: 0.85,
        borderColor: '#fff',
        borderWidth: 1,
      },
      emphasis: {
        itemStyle: { opacity: 1, borderWidth: 3 },
        label: {
          show: true,
          formatter: (params: any) => params.data.title,
          position: 'top',
          fontSize: 12,
          color: '#fff',
        },
      },
      z: 2,
    }
  }

  if (textPoints.length > 0) {
    series.push(makeScatterSeries('纯文本', textPoints, 'circle', 12))
  }
  if (multimodalPoints.length > 0) {
    series.push(makeScatterSeries('图文', multimodalPoints, 'diamond', 14))
  }

  const option: echarts.EChartsOption = {
    backgroundColor: '#1a1a2e',
    grid: { left: 60, right: 40, top: 40, bottom: 40 },
    legend: {
      data: ['相似连线', '纯文本', '图文'],
      textStyle: { color: '#999' },
      icon: 'circle',
      top: 0,
      selected: { '相似连线': linesData.length > 0 },
    },
    xAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#2a2a4a' } },
      axisLabel: { color: '#999' },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#2a2a4a' } },
      axisLabel: { color: '#999' },
    },
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        if (params.seriesName === '相似连线') {
          const d = params.data
          return `相似度: ${(d.score * 100).toFixed(1)}%`
        }
        const d = params.data
        const typeLabel = d.contentType === 'multimodal' ? '图文' : '纯文本'
        return `<strong>${d.title}</strong><br/>
ID: ${d.id}<br/>
(${d.x.toFixed(4)}, ${d.y.toFixed(4)})<br/>
类型: ${typeLabel}<br/>
检索: ${d.vectorStatus} | 可视化: ${d.vizStatus}`
      },
    },
    series,
  }

  chart.setOption(option)
  chart.on('click', (params: any) => {
    if (params.componentType !== 'series' || params.seriesName === '相似连线') return
    selectedPoint.value = {
      id: params.data.id,
      title: params.data.title,
      x: params.data.value[0],
      y: params.data.value[1],
      z: params.data.z ?? 0,
      vectorStatus: params.data.vectorStatus,
      vizStatus: params.data.vizStatus,
      contentType: params.data.contentType,
    }
  })
}

// ======================== Detail helpers ========================

function getPointTitle(id: number): string {
  const p = points.value.find(p => p.id === id)
  return p ? p.title : ''
}

function highlightSimilarPair(id1: number, id2: number) {
  // Find and select the target point on chart
  const target = points.value.find(p => p.id === id2)
  if (target) {
    selectedPoint.value = target
  }
}

// ======================== Style helpers ========================

function statusColor(status: string): string {
  switch (status) {
    case 'vectorized': return '#3fb950'
    case 'failed': return '#f85149'
    default: return '#d29922'
  }
}
</script>

<style scoped>
.knowledge-viz {
  padding: 20px;
  color: #e0e0e0;
}
.viz-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.viz-header h2 {
  margin: 0;
  font-size: 20px;
}
.viz-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.viz-loading {
  padding: 40px;
}

/* Stats */
.viz-stats {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
  flex-wrap: wrap;
  align-items: center;
}

/* Controls */
.viz-controls {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  gap: 12px;
  flex-wrap: wrap;
}
.viz-controls-group {
  display: flex;
  gap: 8px;
  align-items: center;
}
.viz-label {
  font-size: 12px;
  color: #999;
  white-space: nowrap;
}

/* Pagination */
.viz-pagination {
  display: flex;
  justify-content: center;
  margin-bottom: 12px;
}

/* Chart */
.viz-chart {
  width: 100%;
  height: 600px;
  border-radius: 8px;
  overflow: hidden;
}

/* Detail panel */
.viz-detail {
  margin-top: 16px;
  background: #1e1e3a;
  border-radius: 8px;
  overflow: hidden;
}
.viz-detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #252550;
}
.viz-detail-header h4 {
  margin: 0;
  font-size: 14px;
}
.viz-detail-body {
  padding: 12px 16px;
}
.viz-detail-info p {
  margin: 6px 0;
}
.viz-detail-similar {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #333;
}
.viz-detail-similar h5 {
  margin: 0 0 8px 0;
  font-size: 13px;
  color: #999;
}
.viz-similar-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}
.viz-similar-item:hover {
  background: #2a2a50;
}
.viz-similar-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 13px;
}

/* Dots for legend in stats */
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
  vertical-align: middle;
}
.dot-text {
  background: #3fb950;
}
.dot-multimodal {
  background: #d29922;
}
</style>
