<template>
  <div class="source-card" :class="source.type || 'knowledge'" @click="handleClick">
    <div class="source-header">
      <span class="source-title">{{ source.title }}</span>
      <span v-if="source.relevance !== undefined" class="source-score">
        {{ (source.relevance * 100).toFixed(0) }}%
      </span>
    </div>
    <div class="source-meta">
      <span>{{ source.source }}</span>
      <span v-if="source.date">| {{ source.date }}</span>
    </div>
    <div class="source-snippet">{{ source.content?.slice(0, 80) }}...</div>
  </div>
</template>

<script setup lang="ts">
import type { Source } from '@/api/ai-chat'

const props = defineProps<{ source: Source }>()

const handleClick = () => {
  // P1: 点击展开详细内容（P2 支持跳转外部链接）
}
</script>

<style scoped>
.source-card {
  background: rgba(255,255,255,0.03);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 10px;
  padding: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.source-card:hover {
  background: rgba(245,200,122,0.08);
  border-color: rgba(245,200,122,0.2);
}
.source-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 6px;
}
.source-title {
  font-size: 13px;
  font-weight: 500;
  color: #f5c87a;
}
.source-score {
  font-size: 11px;
  color: #3fb950;
}
.source-meta {
  font-size: 11px;
  color: #6e7681;
  margin-bottom: 6px;
}
.source-snippet {
  font-size: 12px;
  color: #8b949e;
  line-height: 1.4;
}
</style>
