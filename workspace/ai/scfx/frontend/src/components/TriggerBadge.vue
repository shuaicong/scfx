<template>
  <span class="trigger-badge" :class="badgeClass">{{ label }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{ type?: string; executed?: boolean }>()
const labelMap: Record<string, string> = {
  cron: 'Cron 表达式',
  cycle: '周期触发',
  once: '单次触发',
  manual: '手动',
  single: '单次触发',
  repeat: '周期触发'
}
const label = computed(() => {
  const base = labelMap[props.type || ''] || props.type || '-'
  if (props.type === 'once') {
    return props.executed ? '单次 (已执行)' : '单次 (待执行)'
  }
  return base
})
const badgeClass = computed(() => {
  if (props.type === 'once') {
    return props.executed ? 'once executed' : 'once'
  }
  return props.type
})
</script>

<style scoped>
.trigger-badge {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 11px;
}
.trigger-badge.cron {
  background: rgba(163, 113, 247, 0.1);
  border: 1px solid rgba(163, 113, 247, 0.2);
  color: var(--accent-purple);
}
.trigger-badge.cycle {
  background: rgba(88, 166, 255, 0.1);
  border: 1px solid rgba(88, 166, 255, 0.2);
  color: var(--accent-blue);
}
.trigger-badge.once {
  background: rgba(240, 136, 62, 0.1);
  border: 1px solid rgba(240, 136, 62, 0.2);
  color: var(--accent-orange);
}
.trigger-badge.once.executed {
  background: rgba(110, 118, 129, 0.1);
  border: 1px solid rgba(110, 118, 129, 0.2);
  color: var(--text-muted);
}
</style>