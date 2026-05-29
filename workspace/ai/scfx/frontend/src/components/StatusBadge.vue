<template>
  <span class="status-badge" :class="status || 'disabled'">
    <span class="dot"></span>
    <span>{{ labelMap[status || 'disabled'] }}</span>
  </span>
</template>

<script setup lang="ts">
const props = defineProps<{
  status?: 'enabled' | 'disabled' | 'pending' | 'running' | 'success' | 'failed' | 'cancelled'
}>()
const labelMap: Record<string, string> = {
  enabled: '启用',
  disabled: '禁用',
  pending: '等待',
  running: '运行中',
  success: '成功',
  failed: '失败',
  cancelled: '已取消'
}
</script>

<style scoped>
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 20px;
  font-size: 12px;
  font-weight: 500;
}
.status-badge .dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}
.status-badge.enabled {
  background: rgba(63, 185, 80, 0.15);
  color: var(--accent-green);
}
.status-badge.disabled {
  background: rgba(139, 148, 158, 0.15);
  color: var(--text-muted);
}
</style>