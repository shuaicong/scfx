<template>
  <span class="source-tag">{{ label }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ source: string }>()
const labelMap: Record<string, string> = {
  liangxin: '粮信网',
  mysteel: '我的钢铁网',
  chinagrain: '中华粮网',
  usda: 'USDA',
  market: '市场数据'
}
const label = computed(() => labelMap[props.source] || props.source)
</script>

<style scoped>
.source-tag {
  display: inline-block;
  padding: 3px 8px;
  background: rgba(88, 166, 255, 0.1);
  border: 1px solid rgba(88, 166, 255, 0.2);
  border-radius: 4px;
  font-size: 11px;
  color: var(--accent-blue);
}
</style>