<template>
  <div class="echarts-line-container">
    <div class="chart-title">{{ title }}</div>
    <div ref="chartRef" class="chart-canvas" :style="{ height: height + 'px' }"></div>
    <div v-if="unit" class="chart-unit">{{ unit }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

const props = defineProps<{
  title: string
  xAxis: string[]
  series: { name: string; data: number[]; type?: string }[]
  unit?: string
  height?: number
}>()

const chartRef = ref<HTMLDivElement>()
let chartInstance: echarts.ECharts | null = null

function renderChart() {
  if (!chartRef.value) return
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value)
  }
  chartInstance.setOption({
    title: { show: false },
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v: number) => `${v} ${props.unit || ''}`,
    },
    legend: {
      type: 'scroll',
      bottom: 0,
      textStyle: { color: '#999' },
    },
    grid: {
      left: 60,
      right: 20,
      top: 20,
      bottom: 40,
    },
    xAxis: {
      type: 'category',
      data: props.xAxis,
      axisLine: { lineStyle: { color: '#444' } },
      axisLabel: { color: '#999' },
    },
    yAxis: {
      type: 'value',
      splitLine: { lineStyle: { color: '#333' } },
      axisLabel: { color: '#999' },
    },
    series: props.series.map(s => ({
      name: s.name,
      type: s.type || 'line',
      data: s.data,
      smooth: true,
      symbol: 'none',
      lineStyle: { width: 2 },
      emphasis: { focus: 'series' },
    })),
    animationDuration: 800,
  }, true)
}

onMounted(() => {
  nextTick(renderChart)
})

watch(() => [props.xAxis, props.series], () => {
  nextTick(renderChart)
}, { deep: true })
</script>

<style scoped>
.echarts-line-container {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 12px;
}
.chart-title {
  font-size: 14px;
  color: #ccc;
  margin-bottom: 8px;
}
.chart-canvas {
  width: 100%;
}
.chart-unit {
  font-size: 12px;
  color: #666;
  margin-top: 4px;
  text-align: right;
}
</style>
