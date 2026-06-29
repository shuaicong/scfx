<template>
  <div v-if="thoughts.length > 0" class="thought-process">
    <div class="thought-header" @click="$emit('toggle')">
      <span class="thought-toggle-icon">{{ collapsed ? '▶' : '▼' }}</span>
      <span class="thought-label">思考过程（{{ thoughts.length }}步）</span>
    </div>
    <div v-show="!collapsed" class="thought-body">
      <div v-for="(t, i) in thoughts" :key="i" class="thought-item">
        <span class="thought-icon">🔍</span>
        <span class="thought-text">{{ t }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  thoughts: string[]
  collapsed?: boolean
}>()

defineEmits<{
  toggle: []
}>()
</script>

<style scoped>
.thought-process {
  padding: 0;
  background: rgba(255,255,255,0.02);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 12px;
  margin-bottom: 8px;
  overflow: hidden;
}
.thought-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
}
.thought-header:hover {
  background: rgba(255,255,255,0.04);
}
.thought-toggle-icon {
  font-size: 10px;
  color: #8b949e;
  width: 12px;
  text-align: center;
}
.thought-label {
  font-size: 13px;
  color: #8b949e;
  font-weight: 500;
}
.thought-body {
  padding: 0 16px 12px;
  border-top: 1px solid rgba(255,255,255,0.06);
}
.thought-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 5px 0;
  font-size: 13px;
  color: #8b949e;
}
.thought-item + .thought-item {
  border-top: none;
}
.thought-text {
  color: #c9d1d9;
}
</style>
