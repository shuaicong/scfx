<template>
  <div class="reasoning-panel">
    <div class="reasoning-header" @click="$emit('toggle')" role="button" tabindex="0"
      @keydown.enter="$emit('toggle')" @keydown.space.prevent="$emit('toggle')">
      <span class="reasoning-icon">💭</span>
      <span class="reasoning-title">深度思考</span>
      <span class="collapse-icon">
        <svg :class="{ rotated: !collapsed }" width="14" height="14" viewBox="0 0 14 14" fill="none">
          <path d="M4 5L7 8L10 5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </span>
    </div>
    <div class="reasoning-body" :class="{ collapsed }">
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

/* 标题栏 — 简洁的金色标签 */
.reasoning-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s;
  outline: none;
}
.reasoning-header:hover {
  background: rgba(245, 200, 122, 0.08);
}
.reasoning-header:focus-visible {
  background: rgba(245, 200, 122, 0.12);
}

.reasoning-icon {
  font-size: 14px;
  line-height: 1;
}
.reasoning-title {
  flex: 1;
  font-size: 13px;
  font-weight: 600;
  color: #f5c87a;
  letter-spacing: 0.02em;
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

/* 推理内容区域 — 左侧金色竖线用 ::before 伪元素实现，消除列感 */
.reasoning-body {
  position: relative;
  padding: 2px 20px 16px 36px;
  transition: all 0.3s ease;
  max-height: 400px;
  opacity: 1;
  overflow-y: auto;
}
.reasoning-body::before {
  content: '';
  position: absolute;
  left: 20px;
  top: 4px;
  bottom: 16px;
  width: 2px;
  background: linear-gradient(180deg, #f5c87a, #d4a574);
  border-radius: 1px;
  pointer-events: none;
}
.reasoning-body.collapsed {
  max-height: 0;
  opacity: 0;
  padding: 0 16px;
  overflow: hidden;
}
.reasoning-body.collapsed::before {
  display: none;
}

/* 推理文本 — 舒适阅读宽度 */
.reasoning-content {
  font-size: 13px;
  line-height: 1.8;
  color: #8b949e;
  white-space: pre-wrap;
  word-break: break-word;
  max-width: 72ch;
}
</style>
