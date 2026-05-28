<template>
  <div class="source-link" :class="type" @click="handleClick">
    <span class="source-icon">
      <svg v-if="type === 'web'" width="14" height="14" viewBox="0 0 14 14" fill="none">
        <circle cx="7" cy="7" r="6" stroke="currentColor" stroke-width="1.5"/>
        <path d="M4 7C4 5.5 5.5 4 7 4C8.5 4 10 5.5 10 7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        <path d="M10 7C10 8.5 8.5 10 7 10C5.5 10 4 8.5 4 7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        <path d="M7 4V2M7 10V12M4 7H2M10 7H12" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
      </svg>
      <svg v-else-if="type === 'doc'" width="14" height="14" viewBox="0 0 14 14" fill="none">
        <path d="M3 2H8.5L11 4.5V12H3V2Z" stroke="currentColor" stroke-width="1.5"/>
        <path d="M8.5 2V5H11" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
        <path d="M5 7H9M5 9H7" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
      </svg>
      <svg v-else-if="type === 'db'" width="14" height="14" viewBox="0 0 14 14" fill="none">
        <ellipse cx="7" cy="4" rx="5" ry="2" stroke="currentColor" stroke-width="1.5"/>
        <path d="M2 4V10C2 11.5 4.5 13 7 13C9.5 13 12 11.5 12 10V4" stroke="currentColor" stroke-width="1.5"/>
        <path d="M2 7C2 8.5 4.5 10 7 10C9.5 10 12 8.5 12 7" stroke="currentColor" stroke-width="1.5"/>
      </svg>
    </span>
    <span class="source-text">{{ title }}</span>
    <span v-if="showArrow" class="arrow-icon">
      <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
        <path d="M4 2L8 6L4 10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    </span>
  </div>
</template>

<script setup lang="ts">
interface Props {
  type: 'web' | 'doc' | 'db'
  title: string
  url?: string
  showArrow?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showArrow: true
})

const emit = defineEmits<{
  click: [url: string]
}>()

const handleClick = () => {
  if (props.url) {
    emit('click', props.url)
    window.open(props.url, '_blank')
  }
}
</script>

<style scoped>
.source-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
  font-size: 12px;
  color: #8b949e;
  cursor: pointer;
  transition: all 0.2s;
}

.source-link:hover {
  background: rgba(245, 200, 122, 0.1);
  border-color: rgba(245, 200, 122, 0.3);
  color: #f5c87a;
}

.source-icon {
  display: flex;
  align-items: center;
  justify-content: center;
}

.source-text {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.arrow-icon {
  display: flex;
  align-items: center;
  opacity: 0.6;
}
</style>
