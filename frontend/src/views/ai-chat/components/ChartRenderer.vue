<template>
  <div class="chart-renderer" ref="chartContainer"></div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import * as echarts from 'echarts'

interface Props {
  type: 'line' | 'bar' | 'pie'
  title: string
  data: any
  source?: string
}

const props = defineProps<Props>()
const chartContainer = ref<HTMLElement | null>(null)
let chartInstance: echarts.ECharts | null = null

const getChartOptions = () => {
  const baseTheme = {
    backgroundColor: 'transparent',
    title: {
      text: props.title,
      left: 'center',
      top: 10,
      textStyle: {
        color: '#e6edf3',
        fontSize: 14,
        fontWeight: 500
      }
    },
    grid: {
      left: 50,
      right: 20,
      top: 50,
      bottom: 30
    },
    textStyle: {
      color: '#8b949e'
    }
  }

  if (props.type === 'line') {
    return {
      ...baseTheme,
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(22, 27, 34, 0.95)',
        borderColor: 'rgba(245, 200, 122, 0.3)',
        textStyle: { color: '#e6edf3' }
      },
      xAxis: {
        type: 'category',
        data: props.data.dates || props.data.labels || [],
        axisLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.1)' } },
        axisTick: { show: false },
        axisLabel: { color: '#8b949e' }
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.06)' } },
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#8b949e' }
      },
      series: [{
        type: 'line',
        data: props.data.prices || props.data.values || [],
        smooth: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color: '#f5c87a', width: 2 },
        itemStyle: { color: '#f5c87a' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(245, 200, 122, 0.3)' },
            { offset: 1, color: 'rgba(245, 200, 122, 0.02)' }
          ])
        }
      }]
    }
  }

  if (props.type === 'bar') {
    return {
      ...baseTheme,
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'rgba(22, 27, 34, 0.95)',
        borderColor: 'rgba(245, 200, 122, 0.3)',
        textStyle: { color: '#e6edf3' }
      },
      xAxis: {
        type: 'category',
        data: props.data.dates || props.data.labels || [],
        axisLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.1)' } },
        axisTick: { show: false },
        axisLabel: { color: '#8b949e' }
      },
      yAxis: {
        type: 'value',
        splitLine: { lineStyle: { color: 'rgba(255, 255, 255, 0.06)' } },
        axisLine: { show: false },
        axisTick: { show: false },
        axisLabel: { color: '#8b949e' }
      },
      series: [{
        type: 'bar',
        data: props.data.prices || props.data.values || [],
        barWidth: '60%',
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#f5c87a' },
            { offset: 1, color: '#d4a574' }
          ]),
          borderRadius: [4, 4, 0, 0]
        }
      }]
    }
  }

  if (props.type === 'pie') {
    return {
      ...baseTheme,
      tooltip: {
        trigger: 'item',
        backgroundColor: 'rgba(22, 27, 34, 0.95)',
        borderColor: 'rgba(245, 200, 122, 0.3)',
        textStyle: { color: '#e6edf3' }
      },
      legend: {
        bottom: 10,
        textStyle: { color: '#8b949e' }
      },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['50%', '50%'],
        data: props.data.values || [],
        label: {
          color: '#e6edf3',
          formatter: '{b}: {c}'
        },
        itemStyle: {
          color: (params: any) => {
            const colors = ['#f5c87a', '#d4a574', '#e8a87c', '#c49a6c', '#f7c59f']
            return colors[params.dataIndex % colors.length]
          }
        }
      }]
    }
  }

  return {}
}

const initChart = () => {
  if (!chartContainer.value) return

  chartInstance = echarts.init(chartContainer.value)
  chartInstance.setOption(getChartOptions())
}

const updateChart = () => {
  if (chartInstance) {
    chartInstance.setOption(getChartOptions(), { notMerge: true })
  }
}

const handleResize = () => {
  chartInstance?.resize()
}

onMounted(async () => {
  await nextTick()
  initChart()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartInstance?.dispose()
})

watch(() => props.data, updateChart, { deep: true })
</script>

<style scoped>
.chart-renderer {
  width: 100%;
  height: 280px;
  margin: 16px 0;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.06);
}
</style>
