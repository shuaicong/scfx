<template>
  <span class="source-tag">{{ label }}</span>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { datasourceApi } from '@/api/datasource'

const props = defineProps<{ source?: string }>()

const datasourceMap = ref<Record<string, string>>({})

const label = computed(() => {
  if (!props.source) return '-'
  return datasourceMap.value[props.source] || props.source
})

onMounted(async () => {
  try {
    const res = await datasourceApi.list()
    const map: Record<string, string> = {}
    for (const ds of res.data) {
      map[ds.code] = ds.name
    }
    datasourceMap.value = map
  } catch {
    // 使用默认映射
    datasourceMap.value = {
      liangxin: '粮信网',
      mysteel: '我的钢铁网',
      chinagrain: '中华粮网',
      usda: 'USDA',
      market: '市场数据'
    }
  }
})
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