<template>
  <div v-if="visualization" class="visualization-renderer">
    <EChartsLine
      v-if="visualization.type === 'line'"
      :title="visualization.title"
      :x-axis="visualization.data.xAxis || []"
      :series="visualization.data.series || []"
      :unit="visualization.data.unit"
    />
    <PriceTable
      v-if="visualization.type === 'table'"
      :title="visualization.title"
      :rows="visualization.data.rows || []"
    />
    <EChartsHeatmap
      v-if="visualization.type === 'heatmap'"
      :title="visualization.title"
      :data="visualization.data as any"
    />
  </div>
</template>

<script setup lang="ts">
import type { VisualizationBlock } from '../types'
import EChartsLine from './EChartsLine.vue'
import EChartsHeatmap from './EChartsHeatmap.vue'
import PriceTable from './PriceTable.vue'

defineProps<{
  visualization: VisualizationBlock | null
}>()
</script>

<style scoped>
.visualization-renderer {
  margin: 16px 0;
}
</style>
