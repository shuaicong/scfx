<template>
  <div class="message-content-wrapper">
    <!-- 回答内容 -->
    <div class="answer-content" v-html="renderedContent"></div>

    <!-- 来源列表 -->
    <div v-if="effectiveSources.length > 0" class="sources-panel">
      <div class="sources-header">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
          <path d="M8 1L10 6H15L11 9.5L12.5 14.5L8 11.5L3.5 14.5L5 9.5L1 6H6L8 1Z" fill="currentColor"/>
        </svg>
        <span>知识来源</span>
        <span class="source-count">{{ effectiveSources.length }}</span>
      </div>

      <div class="sources-grid">
        <div
          v-for="(source, index) in effectiveSources"
          :key="index"
          class="source-card"
          :class="source.type || 'web'"
          @click="handleSourceClick(source)"
        >
          <div class="source-icon">
            <span v-if="(source.type || 'web') === 'web'" class="icon-web">🌐</span>
            <span v-else-if="source.type === 'doc'" class="icon-doc">📄</span>
            <span v-else class="icon-db">📊</span>
          </div>

          <div class="source-info">
            <div class="source-title">{{ source.title || source.name || source.source || '未知来源' }}</div>
            <div class="source-type">
              <span v-if="(source.type || 'web') === 'web'" class="type-tag internet">🌐 互联网</span>
              <span v-else-if="source.type === 'doc'" class="type-tag knowledge">📚 知识库</span>
              <span v-else-if="source.type === 'db'" class="type-tag database">📊 数据</span>
            </div>
          </div>

          <div class="source-arrow">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M5 3L9 7L5 11" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'

interface Source {
  type?: 'web' | 'doc' | 'db'
  name?: string
  title?: string
  source?: string
  url?: string
  publish_time?: string
  kb_id?: number
}

interface ContentBlock {
  type: 'text' | 'chart'
  content?: string
  chartType?: 'line' | 'bar' | 'pie'
  chartTitle?: string
  chartData?: any
  chartSource?: string
}

const props = defineProps<{
  content: string
  sources?: Source[]
}>()

const emit = defineEmits<{
  (e: 'source-click', source: Source): void
  (e: 'preview', url: string, title?: string): void
}>()

// 合并解析出来的来源和 props 来源
const effectiveSources = computed(() => {
  return props.sources || []
})

// 判断是否是外部链接
const isExternalUrl = (url: string) => {
  if (!url) return false
  return url.startsWith('http://') || url.startsWith('https://')
}

// 处理来源点击
const handleSourceClick = (source: Source) => {
  // 优先冒泡给父组件处理（kb_id 跳转知识详情等）
  emit('source-click', source)

  // 父组件不处理且有 url 时，本地处理预览
  if (source.url) {
    if (isExternalUrl(source.url)) {
      window.open(source.url, '_blank', 'noopener,noreferrer')
    } else {
      emit('preview', source.url, source.title || source.name)
    }
  }
}

// 渲染 Markdown 内容
const renderedContent = computed(() => {
  if (!props.content) return ''

  // 解析 chart 标记
  const regex = /<chart\s+type="([^"]+)"\s+title="([^"]+)"\s+data='([^']+)'(?:\s+source="([^"]*)")?\s*\/>/g
  let content = props.content.replace(regex, '')

  // 简单处理 Markdown
  try {
    return marked(content, { breaks: true, gfm: true })
  } catch {
    return content.replace(/\n/g, '<br>')
  }
})

// 解析内容块（保留 chart 标记用于单独渲染）
const contentBlocks = computed(() => {
  const blocks: ContentBlock[] = []
  const regex = /<chart\s+type="([^"]+)"\s+title="([^"]+)"\s+data='([^']+)'(?:\s+source="([^"]*)")?\s*\/>/g

  const processedContent = props.content.replace(regex, (_fullMatch, type, title, dataJson, source) => {
    return `\x00CHART\x00${type}\x00${title}\x00${dataJson}\x00${source || ''}\x00CHART\x00`
  })

  const parts = processedContent.split('\x00CHART\x00')

  for (const part of parts) {
    if (!part.trim()) continue
    if (part === 'CHART') continue

    const chartMatch = part.match(/^(line|bar|pie)\x00(.+?)\x00(.+?)\x00(.*)$/s)

    if (chartMatch) {
      const [, chartType, chartTitle, dataJson, chartSource] = chartMatch
      try {
        const chartData = JSON.parse(dataJson)
        blocks.push({
          type: 'chart',
          chartType: chartType as 'line' | 'bar' | 'pie',
          chartTitle: chartTitle,
          chartData: chartData,
          chartSource: chartSource || undefined
        })
      } catch {
        blocks.push({ type: 'text', content: part })
      }
    } else {
      blocks.push({ type: 'text', content: part })
    }
  }

  return blocks
})
</script>

<style scoped>
.message-content-wrapper {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 回答内容样式 */
.answer-content {
  font-size: 14px;
  line-height: 1.8;
  color: #e6edf3;
}

.answer-content :deep(p) {
  margin: 0 0 12px 0;
}

.answer-content :deep(ul),
.answer-content :deep(ol) {
  margin: 8px 0;
  padding-left: 20px;
}

.answer-content :deep(li) {
  margin: 4px 0;
}

.answer-content :deep(strong) {
  color: #f5c87a;
  font-weight: 600;
}

.answer-content :deep(a) {
  color: #58a6ff;
  text-decoration: none;
  transition: color 0.2s;
}

.answer-content :deep(a:hover) {
  color: #79b8ff;
  text-decoration: underline;
}

.answer-content :deep(h1),
.answer-content :deep(h2),
.answer-content :deep(h3) {
  color: #f5c87a;
  margin: 16px 0 8px 0;
}

.answer-content :deep(code) {
  background: rgba(255, 255, 255, 0.08);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Fira Code', monospace;
  font-size: 13px;
}

.answer-content :deep(blockquote) {
  margin: 12px 0;
  padding: 12px 16px;
  background: rgba(245, 200, 122, 0.08);
  border-left: 3px solid #f5c87a;
  border-radius: 0 8px 8px 0;
  color: #c9d1d9;
}

.answer-content :deep(hr) {
  border: none;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  margin: 16px 0;
}

/* 来源面板 */
.sources-panel {
  background: rgba(255, 255, 255, 0.02);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 16px;
}

.sources-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: #f5c87a;
  font-size: 13px;
  font-weight: 600;
}

.source-count {
  background: rgba(245, 200, 122, 0.2);
  color: #f5c87a;
  padding: 2px 8px;
  border-radius: 10px;
  font-size: 11px;
  margin-left: auto;
}

/* 来源网格 */
.sources-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 10px;
}

/* 来源卡片 */
.source-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.06);
  border-radius: 10px;
  cursor: pointer;
  transition: all 0.25s ease;
  position: relative;
  overflow: hidden;
}

.source-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 3px;
  transition: background 0.25s ease;
}

.source-card.web::before {
  background: linear-gradient(180deg, #58a6ff, #1f6feb);
}

.source-card.doc::before {
  background: linear-gradient(180deg, #f5c87a, #d4a574);
}

.source-card.db::before {
  background: linear-gradient(180deg, #3fb950, #238636);
}

.source-card:hover {
  background: rgba(255, 255, 255, 0.06);
  border-color: rgba(255, 255, 255, 0.12);
  transform: translateX(4px);
}

.source-card:hover::before {
  width: 4px;
}

.source-icon {
  font-size: 18px;
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 8px;
}

.source-info {
  flex: 1;
  min-width: 0;
}

.source-title {
  font-size: 13px;
  color: #e6edf3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 4px;
}

.source-type {
  display: flex;
  gap: 6px;
}

.type-tag {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  font-weight: 500;
}

.type-tag.internet {
  background: rgba(88, 166, 255, 0.15);
  color: #58a6ff;
}

.type-tag.knowledge {
  background: rgba(245, 200, 122, 0.15);
  color: #f5c87a;
}

.type-tag.database {
  background: rgba(63, 185, 80, 0.15);
  color: #3fb950;
}

.source-arrow {
  flex-shrink: 0;
  color: #6e7681;
  transition: all 0.2s;
}

.source-card:hover .source-arrow {
  color: #f5c87a;
  transform: translateX(3px);
}
</style>