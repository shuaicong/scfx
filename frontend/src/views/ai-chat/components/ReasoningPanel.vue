<template>
  <div class="reasoning-panel">
    <div class="reasoning-header" @click="$emit('toggle')">
      <span class="reasoning-icon">💭</span>
      <span class="reasoning-title">深度思考</span>
      <span class="collapse-icon">
        <svg :class="{ rotated: !collapsed }" width="14" height="14" viewBox="0 0 14 14" fill="none">
          <path d="M4 5L7 8L10 5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </span>
    </div>
    <div class="reasoning-body" :class="{ collapsed }">
      <div class="reasoning-line"></div>
      <div class="reasoning-content">{{ reasoning }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  reasoning: string
  collapsed: boolean
}>()

defineEmits<{
  toggle: []
}>()
</script>

<style scoped>
.reasoning-panel {
  margin-bottom: 16px;
  background: rgba(245, 200, 122, 0.05);
  border: 1px solid rgba(245, 200, 122, 0.15);
  border-radius: 12px;
  overflow: hidden;
}
.reasoning-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  cursor: pointer;
  user-select: none;
  transition: background 0.2s;
}
.reasoning-header:hover {
  background: rgba(245, 200, 122, 0.08);
}
.reasoning-icon {
  font-size: 14px;
}
.reasoning-title {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  color: #f5c87a;
}
.collapse-icon {
  display: flex;
  align-items: center;
  color: #8b949e;
}
.collapse-icon svg {
  transition: transform 0.3s ease;
}
.collapse-icon svg.rotated {
  transform: rotate(180deg);
}
.reasoning-body {
  display: flex;
  gap: 12px;
  padding: 0 16px 12px;
  transition: all 0.3s ease;
  max-height: 400px;
  opacity: 1;
  overflow-y: auto;
}
.reasoning-body.collapsed {
  max-height: 0;
  opacity: 0;
  padding: 0 16px;
  overflow: hidden;
}
.reasoning-line {
  width: 2px;
  flex-shrink: 0;
  background: linear-gradient(180deg, #f5c87a, #d4a574);
  border-radius: 1px;
}
.reasoning-content {
  flex: 1;
  font-size: 13px;
  line-height: 1.7;
  color: #8b949e;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
