<template>
  <div class="echarts-heatmap-container">
    <div class="heatmap-header">
      <div class="heatmap-title">{{ title }}</div>
      <el-switch
        v-model="globalScale"
        size="small"
        active-text="全局色阶"
        inactive-text="单列色阶"
      />
    </div>
    <div ref="chartRef" class="chart-canvas" style="height: 300px"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{
  title: string
  data: {
    xAxis: string[]
    yAxis: string[]
    data: [number, number, number][]
    unit?: string
  }
}>()

const globalScale = ref(false)
const chartRef = ref<HTMLDivElement>()
let chartInstance: echarts.ECharts | null = null

function renderChart() {
  if (!chartRef.value) return
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }

  const maxValue = globalScale.value
    ? Math.max(...props.data.data.map(d => d[2]))
    : undefined

  chartInstance.setOption({
    title: { show: false },
    tooltip: {
      formatter: (p: any) => {
        const [x, y, v] = p.data
        return `${props.data.yAxis[x]} - ${props.data.xAxis[y]}: ${v} ${props.data.unit || ''}`
      },
    },
    grid: {
      left: 80,
      right: 40,
      top: 10,
      bottom: 40,
    },
    xAxis: {
      type: 'category',
      data: props.data.xAxis,
      splitArea: { show: true },
      axisLabel: { color: '#999', rotate: 45 },
    },
    yAxis: {
      type: 'category',
      data: props.data.yAxis,
      splitArea: { show: true },
      axisLabel: { color: '#999' },
    },
    visualMap: {
      min: 0,
      max: maxValue,
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      inRange: {
        color: ['#2e7d32', '#8bc34a', '#ffeb3b', '#ff9800', '#d32f2f'],
      },
    },
    series: [{
      type: 'heatmap',
      data: props.data.data,
      label: { show: false },
      emphasis: {
        itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0,0,0,0.5)' },
      },
    }],
    animationDuration: 800,
  }, true)
}

onMounted(() => {
  nextTick(renderChart)
})

watch(() => [props.data, globalScale.value], () => {
  nextTick(renderChart)
}, { deep: true })
</script>

<style scoped>
.echarts-heatmap-container {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 12px;
}
.heatmap-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.heatmap-title {
  font-size: 14px;
  color: #ccc;
}
.chart-canvas {
  width: 100%;
}
</style>
