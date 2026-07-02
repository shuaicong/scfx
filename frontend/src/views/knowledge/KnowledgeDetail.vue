<template>
  <div class="knowledge-detail-page">
    <div class="page-header">
      <button class="back-btn" @click="goBack">
        <svg width="20" height="20" viewBox="0 0 20 20" fill="none">
          <path d="M12.5 15L7.5 10L12.5 5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
        <span>返回</span>
      </button>
      <div v-if="loading" class="loading-text">加载中...</div>
    </div>

    <div v-if="loading" class="loading-state">
      <div class="loading-spinner"></div>
      <span>正在加载知识详情...</span>
    </div>

    <div v-else-if="error" class="error-state">
      <svg width="48" height="48" viewBox="0 0 48 48" fill="none">
        <circle cx="24" cy="24" r="20" stroke="#f85149" stroke-width="2"/>
        <path d="M24 16V28M24 32V34" stroke="#f85149" stroke-width="2" stroke-linecap="round"/>
      </svg>
      <p>{{ error }}</p>
    </div>

    <div v-else-if="knowledge" class="detail-content">
      <h1 class="detail-title">{{ knowledge.title }}</h1>
      <div class="detail-meta">
        <!-- 时间 -->
        <span class="meta-item" v-if="knowledge.publishTime">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <circle cx="7" cy="7" r="6" stroke="currentColor" stroke-width="1.5"/>
            <path d="M7 4V7H10" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          {{ formatDate(knowledge.publishTime) }}
        </span>
        <!-- 来源（文章来源网站） -->
        <span class="meta-item" v-if="knowledge.sourceType">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M7 1C3.69 1 1 3.69 1 7C1 10.31 3.69 13 7 13C10.31 13 13 10.31 13 7C13 3.69 10.31 1 7 1Z" stroke="currentColor" stroke-width="1.5"/>
            <path d="M7 4V7L9 9" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          {{ { liangxin: '粮信网', mysteel: '我的钢铁网', chinagrain: '中华粮网', usda: 'USDA', upload: '人工录入' }[knowledge.sourceType] || knowledge.sourceType }}
        </span>
        <!-- 数据源（采集任务来源） -->
        <span class="meta-item" v-if="knowledge.sourceName && knowledge.sourceName !== knowledge.sourceType">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <rect x="1" y="2" width="12" height="10" rx="1" stroke="currentColor" stroke-width="1.5"/>
            <path d="M5 2V12" stroke="currentColor" stroke-width="1.5"/>
          </svg>
          {{ knowledge.sourceName }}
        </span>
        <!-- 作者 -->
        <span class="meta-item" v-if="knowledge.author">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M7 8C8.6 8 9.67 6.93 9.67 5.33C9.67 3.73 8.6 2.67 7 2.67C5.4 2.67 4.33 3.73 4.33 5.33C4.33 6.93 5.4 8 7 8Z" stroke="currentColor" stroke-width="1.5"/>
            <path d="M2.33 13C2.33 10.05 4.6 7.67 7.5 7.67C10.4 7.67 12.67 10.05 12.67 13" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          {{ knowledge.author }}
        </span>
        <!-- 分类 -->
        <span class="meta-item" v-if="knowledge.categoryName">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M5.67 2.33H2.33C1.6 2.33 1 2.93 1 3.67V11C1 11.74 1.6 12.33 2.33 12.33H11.67C12.4 12.33 13 11.74 13 11V4.33C13 3.6 12.4 3 11.67 3H7.67L5.67 2.33Z" stroke="currentColor" stroke-width="1.5"/>
          </svg>
          {{ knowledge.categoryName }}
        </span>
        <!-- 切片数 -->
        <span class="meta-item" v-if="knowledge.chunkCount !== undefined && knowledge.chunkCount !== null">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M2 2H6V6H2V2Z" stroke="currentColor" stroke-width="1.5"/>
            <path d="M8 2H12V6H8V2Z" stroke="currentColor" stroke-width="1.5"/>
            <path d="M2 8H6V12H2V8Z" stroke="currentColor" stroke-width="1.5"/>
            <path d="M8 8H12V12H8V8Z" stroke="currentColor" stroke-width="1.5"/>
          </svg>
          <span style="cursor:pointer;text-decoration:underline;text-decoration-style:dotted;" @click="toggleChunks" title="点击查看切片列表">
            {{ knowledge.chunkCount }} 个切片
          </span>
        </span>
        <!-- 向量状态 -->
        <span class="meta-item" v-if="knowledge.vectorStatus" :class="'status-' + knowledge.vectorStatus">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <circle cx="7" cy="7" r="6" stroke="currentColor" stroke-width="1.5"/>
            <circle cx="7" cy="7" r="3" fill="currentColor"/>
          </svg>
          {{ { pending: '未向量化', processing: '处理中', vectorized: '已向量化', failed: '失败' }[knowledge.vectorStatus] || knowledge.vectorStatus }}
        </span>
        <!-- 品种 -->
        <span class="meta-item" v-if="knowledge.collectionVariety">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M7 1C3.69 1 1 3.69 1 7C1 10.31 3.69 13 7 13C10.31 13 13 10.31 13 7C13 3.69 10.31 1 7 1Z" stroke="currentColor" stroke-width="1.5"/>
            <path d="M7 4V7L9 9" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          {{ knowledge.collectionVariety }}
        </span>
        <!-- 报告类型 -->
        <span class="meta-item" v-if="knowledge.collectionReportType">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M2 2H12V12H2V2Z" stroke="currentColor" stroke-width="1.5"/>
            <path d="M5 2V12" stroke="currentColor" stroke-width="1.5"/>
          </svg>
          {{ knowledge.collectionReportType }}
        </span>
        <!-- 原文地址 -->
        <span class="meta-item" v-if="knowledge.originalUrl">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M7 1L13 7L7 13" stroke="currentColor" stroke-width="1.5"/>
            <path d="M1 7H13" stroke="currentColor" stroke-width="1.5"/>
          </svg>
          <a :href="knowledge.originalUrl" target="_blank" rel="noopener noreferrer" class="original-link">查看原文</a>
        </span>
      </div>

      <!-- 来源小字 -->
      <div class="source-line" v-if="knowledge.publishTime || knowledge.sourceName || knowledge.sourceType">
        {{ formatDate(knowledge.publishTime) }} 来源：{{ knowledge.sourceName || knowledge.sourceType }}
      </div>

      <!-- docx-preview 容器（在 detail-body 之外，避免 :deep 暗色样式渗透） -->
      <div v-show="isDocx && !docxError" class="docx-preview-wrapper">
        <div class="docx-render-area" ref="docxPreviewRef"></div>
      </div>
      <!-- PDF 预览 -->
      <div v-show="isPdf" class="pdf-preview-wrapper">
        <iframe
          :src="pdfPreviewUrl"
          class="pdf-iframe"
          frameborder="0"
        ></iframe>
        <div class="pdf-toolbar">
          <a :href="pdfPreviewUrl" target="_blank" rel="noopener noreferrer" class="pdf-open-link">
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M7 1L13 7L7 13" stroke="currentColor" stroke-width="1.5"/>
              <path d="M1 7H13" stroke="currentColor" stroke-width="1.5"/>
            </svg>
            在新标签页打开 PDF
          </a>
        </div>
      </div>
      <!-- docx 渲染失败提示条 + 原始 HTML 内容（含图片和文本，TABLE_MARKER 区域会被过滤） -->
      <div class="detail-body" v-show="(!isDocx && !isPdf) || docxError">
        <div v-if="docxError" class="docx-error-bar">
          <span class="docx-error-text">Word 文档渲染失败，已降级显示文本内容</span>
        </div>
        <div v-if="sanitizedHtml" class="html-content" v-html="sanitizedHtml" @click="onContentClick"></div>
        <!-- 无 contentHtml 时降级使用 content 解析 -->
        <template v-else>
          <div v-for="(block, i) in renderedBlocks" :key="i">
            <div v-if="block.type === 'table'" class="enhanced-table-wrapper">
              <div v-if="block.caption" class="table-caption">{{ block.caption }}</div>
              <el-table
                :data="block.tableData"
                border
                stripe
                size="small"
                style="width: 100%"
                @sort-change="(e) => handleSortChange(i, e)"
              >
                <el-table-column
                  v-for="(h, j) in block.columns"
                  :key="j"
                  :prop="'col' + j"
                  :label="h"
                  sortable="custom"
                  show-overflow-tooltip
                />
              </el-table>
              <div class="table-footer" v-if="block.rows">{{ block.rows }} 行数据</div>
            </div>
            <div v-else v-html="block.html" class="text-block"></div>
          </div>
        </template>
      </div>

    </div>

    <!-- 图片预览遮罩 -->
    <div v-if="previewImageUrl" class="image-preview-overlay" @click="closePreview">
      <!-- 工具栏 -->
      <div class="preview-toolbar" @click.stop>
        <button class="preview-btn" @click="zoomOut" :disabled="previewScale <= 0.25" title="缩小">−</button>
        <span class="preview-zoom-level">{{ Math.round(previewScale * 100) }}%</span>
        <button class="preview-btn" @click="zoomIn" :disabled="previewScale >= 5" title="放大">+</button>
        <span class="preview-sep"></span>
        <button class="preview-btn" @click="previewScale = 1" title="重置">⊡</button>
        <span class="preview-sep"></span>
        <a :href="previewImageUrl" target="_blank" rel="noopener" class="preview-btn" title="新窗口打开">↗</a>
        <span class="preview-sep"></span>
        <button class="preview-btn preview-close-btn" @click="closePreview" title="关闭">✕</button>
      </div>
      <!-- 图片容器（支持滚轮缩放 + 拖拽平移） -->
      <div
        class="preview-img-wrap"
        @click.stop
        @wheel="onPreviewWheel"
        @mousedown="onPreviewMouseDown"
        @mousemove="onPreviewMouseMove"
        @mouseup="onPreviewMouseUp"
        @mouseleave="onPreviewMouseUp"
      >
        <img
          :src="previewImageUrl"
          class="image-preview-img"
          :style="{ transform: 'translate(' + previewOffsetX + 'px, ' + previewOffsetY + 'px) scale(' + previewScale + ')' }"
          draggable="false"
          @click.stop
        >
      </div>
      <!-- 底部提示 -->
      <div class="preview-footer" @click.stop>
        <span class="preview-tip">滚轮缩放 · 拖拽平移</span>
      </div>
    </div>

    <!-- 切片列表 - 侧滑抽屉 -->
    <el-drawer
      v-model="showChunks"
      :title="`切片列表（${chunks.length} 个）`"
      size="560px"
      destroy-on-close
      class="chunk-drawer-dark"
    >
      <!-- 概览统计 -->
      <div class="chunk-summary" v-if="chunks.length > 0">
        <div class="chunk-summary-item">
          <span class="summary-value">{{ chunks.length }}</span>
          <span class="summary-label">切片数</span>
        </div>
        <div class="chunk-summary-item">
          <span class="summary-value">{{ chunks.filter((c: any) => c.chunkType === 'table').length }}</span>
          <span class="summary-label">表格切片</span>
        </div>
        <div class="chunk-summary-item">
          <span class="summary-value">{{ chunks.reduce((s: number, c: any) => s + ((c as any).content || '').length, 0) }}</span>
          <span class="summary-label">总字符</span>
        </div>
      </div>

      <div v-if="chunksLoading" class="chunks-loading">加载中...</div>
      <div v-else-if="chunks.length === 0" class="chunks-empty">暂无切片数据</div>
      <div v-else class="chunks-list">
        <div v-for="(chunk, i) in chunks" :key="i" class="chunk-card">
          <div class="chunk-card-header">
            <div class="chunk-card-header-left">
              <span class="chunk-index">#{{ i + 1 }}</span>
              <span :class="'chunk-type-tag tag-' + (chunk.chunkType || 'text')">
                {{ chunk.chunkType === 'table' ? '📊 表格' : '📝 文本' }}
              </span>
            </div>
            <div class="chunk-card-header-right">
              <span class="chunk-tokens" v-if="chunk.tokenCount">{{ chunk.tokenCount }} tokens</span>
              <span class="chunk-tokens" v-else>{{ (chunk.content || '').length }} 字符</span>
              <span class="chunk-order" v-if="chunks.length > 1">{{ i + 1 }}/{{ chunks.length }}</span>
            </div>
          </div>
          <div class="chunk-card-body">
            <pre class="chunk-card-content">{{ chunk.content || '' }}</pre>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { marked } from 'marked'
import { renderAsync } from 'docx-preview'
import request from '@/api'
import { knowledgeApi } from '@/api/knowledge'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const knowledge = ref<any>(null)

// docx-preview
const docxPreviewRef = ref<HTMLDivElement | null>(null)
const docxLoading = ref(false)
const docxError = ref(false)
const isDocx = computed(() => knowledge.value?.fileType === 'docx')
const isPdf = computed(() => knowledge.value?.fileType === 'pdf')
const pdfPreviewUrl = computed(() => {
  if (!knowledge.value) return ''
  // 优先使用 original_url（MinIO 公开链接），否则用 file_path 拼接
  return knowledge.value.originalUrl || `http://localhost:9000/reports/${knowledge.value.filePath}`
})
const docxRendered = ref(false)

// 知识数据加载完成后，如果 fileType=docx 则触发渲染
watch(knowledge, async (val) => {
  if (!val || val.fileType !== 'docx' || docxRendered.value) return
  console.log('[docx] fileType:', val.fileType, 'id:', val.id)
  docxLoading.value = true
  docxError.value = false
  // 等待 v-show 让容器进入 DOM，ref 绑定完成
  await nextTick()
  if (!docxPreviewRef.value) {
    // 首次 nextTick ref 可能还未绑定，再等一次
    await nextTick()
  }
  const id = Number(route.params.id)
  if (!id || !docxPreviewRef.value) {
    console.warn('[docx] cannot get ref after waiting, id:', id)
    docxLoading.value = false
    return
  }
  try {
    const url = knowledgeApi.getDownloadUrl(id)
    console.log('[docx] fetching:', url)
    const resp = await fetch(url)
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`)
    const blob = await resp.blob()
    console.log('[docx] blob received:', blob.size, 'bytes')
    await renderAsync(blob, docxPreviewRef.value)
    console.log('[docx] render complete')
    docxRendered.value = true
  } catch (e) {
    console.error('[docx] preview failed', e)
    docxError.value = true
  } finally {
    docxLoading.value = false
  }
})

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  try {
    const d = new Date(dateStr)
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
  } catch {
    return dateStr
  }
}

const goBack = () => {
  router.back()
}

// === 类型定义 ===
interface TableBlock {
  type: 'table'
  caption: string
  columns: string[]
  tableData: Record<string, string>[]
  rows: number
}

interface TextBlock {
  type: 'text'
  html: string
}

type ContentBlock = TableBlock | TextBlock

interface TableMetaEntry {
  headers: string[]
  rows: string[][]
  caption: string
}

const sanitizeTableMeta = (raw: any): TableMetaEntry | null => {
  if (!raw || typeof raw !== 'object') return null
  const headers: string[] = Array.isArray(raw.headers)
    ? raw.headers.filter((h: any) => typeof h === 'string' && h.length > 0)
    : []
  if (headers.length === 0) return null
  let rows: string[][] = []
  if (Array.isArray(raw.rows)) {
    rows = raw.rows
      .filter((r: any) => Array.isArray(r))
      .map((r: any[]) => {
        const cleaned = r.map((c: any) => String(c ?? ''))
        // 短行补齐：金融数据表格中短行通常是缺少首列（如公共日期列），
        // 从左侧补空而非右侧，避免子表头与数据列错位
        while (cleaned.length < headers.length) cleaned.unshift('')
        return cleaned.slice(0, headers.length)
      })
      .filter((r: string[]) => r.some(c => c.length > 0))
  }
  if (rows.length === 0) return null
  return { headers, rows, caption: typeof raw.caption === 'string' ? raw.caption : '' }
}

const parseMixedContent = (content: string, tableMetaJson: string | null): ContentBlock[] => {
  const blocks: ContentBlock[] = []
  if (!content) return blocks

  let tables: TableMetaEntry[] = []
  if (tableMetaJson) {
    try {
      const parsed = JSON.parse(tableMetaJson)
      if (Array.isArray(parsed)) {
        tables = parsed.map(sanitizeTableMeta).filter((t): t is TableMetaEntry => t !== null)
      }
    } catch { tables = [] }
  }

  const lines = content.split('\n')
  const segments: { type: 'text' | 'table'; content: string }[] = []
  let i = 0

  if (content.includes('<!--TABLE_MARKER_')) {
    while (i < lines.length) {
      if (lines[i].includes('<!--TABLE_MARKER_')) {
        const tableLines: string[] = []
        while (i < lines.length && !lines[i].includes('<!--TABLE_MARKER_END_')) {
          tableLines.push(lines[i]); i++
        }
        if (i < lines.length) { tableLines.push(lines[i]); i++ }
        segments.push({ type: 'table', content: tableLines.join('\n') })
      } else {
        segments.push({ type: 'text', content: lines[i] })
        i++
      }
    }
  } else {
    while (i < lines.length) {
      if (lines[i].trim().startsWith('|') && lines[i].trim().endsWith('|')) {
        const pipeLines: string[] = []
        while (i < lines.length) {
          const s = lines[i].trim()
          if (s.startsWith('|') && s.endsWith('|')) { pipeLines.push(lines[i]); i++ }
          else if (s === '') { pipeLines.push(lines[i]); i++ }
          else { break }
        }
        while (pipeLines.length && !pipeLines[pipeLines.length - 1].trim()) pipeLines.pop()
        if (pipeLines.length >= 3) {
          segments.push({ type: 'table', content: pipeLines.join('\n') })
        } else {
          for (const pl of pipeLines) { if (pl.trim()) segments.push({ type: 'text', content: pl }) }
        }
      } else {
        segments.push({ type: 'text', content: lines[i] })
        i++
      }
    }
  }

  let tableIdx = 0
  const hasTables = tables.length > 0

  const renderArticle = (text: string): string => {
    let html = marked(text, { breaks: true, gfm: true })
    // 一、二、三等版块标题 → 黄底高亮
    html = html.replace(/<p>([一二三四五六七八九十]+[、．.][^<]*)<\/p>/g, '<p class="section-title">$1<\/p>')
    // 二级标题（国际期货/国内期货等）
    html = html.replace(/<p>(\s*(国际期货|国内期货|北方港口|南方港口|饲料|加工厂)[：:][^<]*)<\/p>/g, '<p class="sub-title">$1<\/p>')
    // ★ 开头段落：取消首行缩进
    html = html.replace(/<p>(★[^<]*)<\/p>/g, '<p class="star-item">$1<\/p>')
    return html
  }

  const flushText = (textParts: string[]) => {
    const raw = textParts.join('\n').trim()
    if (raw) blocks.push({ type: 'text', html: renderArticle(raw) })
  }

  let textBuffer: string[] = []
  for (const seg of segments) {
    if (seg.type === 'table' && hasTables && tableIdx < tables.length) {
      flushText(textBuffer)
      textBuffer = []
      const meta = tables[tableIdx++]
      const colKey = meta.headers
      const data = meta.rows.map((row) => {
        const item: Record<string, string> = {}
        colKey.forEach((_h, idx) => { item['col' + idx] = row[idx] })
        return item
      })
      blocks.push({ type: 'table', caption: meta.caption, columns: colKey, tableData: data, rows: data.length })
    } else {
      textBuffer.push(seg.content)
    }
  }
  flushText(textBuffer)

  return blocks
}

const renderedBlocks = ref<ContentBlock[]>([])

// 切片查看
const showChunks = ref(false)
const chunks = ref<any[]>([])
const previewImageUrl = ref('')
const previewScale = ref(1)
const previewOffsetX = ref(0)
const previewOffsetY = ref(0)
let isDragging = false
let dragStartX = 0
let dragStartY = 0
let dragOffsetX = 0
let dragOffsetY = 0

function closePreview() {
  previewImageUrl.value = ''
  previewScale.value = 1
  previewOffsetX.value = 0
  previewOffsetY.value = 0
}
function zoomIn() {
  previewScale.value = Math.min(5, Math.round(previewScale.value * 1.5 * 100) / 100)
}
function zoomOut() {
  previewScale.value = Math.max(0.25, Math.round(previewScale.value / 1.5 * 100) / 100)
}
function onPreviewWheel(e: WheelEvent) {
  e.preventDefault()
  const delta = e.deltaY > 0 ? 0.9 : 1.1
  previewScale.value = Math.max(0.25, Math.min(5, Math.round(previewScale.value * delta * 100) / 100))
}

function onPreviewMouseDown(e: MouseEvent) {
  isDragging = true
  dragStartX = e.clientX
  dragStartY = e.clientY
  dragOffsetX = previewOffsetX.value
  dragOffsetY = previewOffsetY.value
  const img = (e.currentTarget as HTMLElement)?.querySelector('img')
  if (img) img.style.cursor = 'grabbing'
  e.preventDefault()
}
function onPreviewMouseMove(e: MouseEvent) {
  if (!isDragging) return
  previewOffsetX.value = dragOffsetX + (e.clientX - dragStartX)
  previewOffsetY.value = dragOffsetY + (e.clientY - dragStartY)
}
function onPreviewMouseUp() {
  isDragging = false
  const img = document.querySelector('.image-preview-img') as HTMLElement | null
  if (img) img.style.cursor = ''
}

/** 清洗后的 HTML 内容（TABLE_MARKER 区域替换为 HTML 表格，保留图片和文本） */
const sanitizedHtml = computed(() => {
  let html = knowledge.value?.contentHtml
  if (!html) return ''

  // 解析 tableMeta
  let tables: TableMetaEntry[] = []
  try {
    const parsed = JSON.parse(knowledge.value?.tableMeta || '[]')
    if (Array.isArray(parsed)) {
      tables = parsed
    }
  } catch { tables = [] }

  // 把 TABLE_MARKER 区域替换为实际 HTML 表格
  // 兼容 HTML 注释 <!--N--> 和 HTML 实体转义 &lt;!--N--&gt; 两种格式
  let markerIdx = 0
  // 处理 HTML 注释格式
  html = html.replace(/<!--TABLE_MARKER_\d+-->[\s\S]*?<!--TABLE_MARKER_END_\d+-->/g, () => {
    return _renderTableHtml(tables[markerIdx++])
  })
  // 处理 HTML 实体转义格式
  html = html.replace(/&lt;!--TABLE_MARKER_\d+--&gt;[\s\S]*?&lt;!--TABLE_MARKER_END_\d+--&gt;/g, () => {
    return _renderTableHtml(tables[markerIdx++])
  })
  return html
})

/** 将 TableMetaEntry 渲染为 HTML 表格字符串（保留原文 colspan 布局） */
function _renderTableHtml(meta: TableMetaEntry | undefined): string {
  if (!meta || !meta.headers || !meta.rows) return ''

  const headers = meta.headers
  const rows = meta.rows
  let tableHtml = `<div class="enhanced-table-wrapper"><table class="html-table">`

  // ── 合并连续重复的表头（还原原文 colspan） ──
  const merged: { text: string; colspan: number }[] = []
  for (const h of headers) {
    const last = merged[merged.length - 1]
    if (last && last.text === h) {
      last.colspan++
    } else {
      merged.push({ text: h, colspan: 1 })
    }
  }

  // ── 判断 rows 前几行是否属于表头 ──
  // 规则1：行长度与表头不一致 → 子表头行（首列被 rowspan 覆盖）
  // 规则2：所有单元格为短文本/单位说明 → 子表头行
  const hasUnit = (s: string) => /[元％%美分美元吨斤公斤]/.test(s)
  const hasNumber = (s: string) => /\d/.test(s)
  let headerRowCount = 0
  for (const row of rows) {
    // 长度不一致 → 子表头行（合约代码行 or 单位行，首列由上行 rowspan 覆盖）
    if (row.length !== headers.length) {
      headerRowCount++
      continue
    }
    const allHeaderLike = row.every((c: string) => {
      const t = c.trim()
      if (!t) return true
      // 短文本或单位说明 → 表头
      if (t.length <= 8 && (!hasNumber(t) || hasUnit(t))) return true
      return false
    })
    if (allHeaderLike && row.length > 0) {
      headerRowCount++
    } else {
      break
    }
  }

  // ── 渲染表头（thead） ──
  tableHtml += '<thead>'
  const hasMultiRow = headerRowCount > 0
  const headerRows = rows.slice(0, headerRowCount)

  // 计算 rowspan 覆盖列数（子表头行比主表头少列时，首列由 rowspan 覆盖）
  let colDiff = 0
  if (hasMultiRow && headerRows.length > 0) {
    const minSubRowLen = Math.min(...headerRows.map(r => r.length))
    colDiff = Math.max(0, headers.length - minSubRowLen)
  }
  const totalHeaderRows = hasMultiRow ? headerRowCount + 1 : 1

  // 主标题行（含 colspan 和 rowspan）
  if (!hasMultiRow || merged.some(m => m.colspan > 1) || colDiff > 0) {
    tableHtml += '<tr>'
    // 前 colDiff 列需要 rowspan 覆盖所有子表头行
    let remainingRowspan = colDiff
    let mergedIdx = 0
    for (; mergedIdx < merged.length && remainingRowspan > 0; mergedIdx++) {
      const m = merged[mergedIdx]
      const take = Math.min(m.colspan, remainingRowspan)
      if (take >= m.colspan) {
        tableHtml += `<th rowspan="${totalHeaderRows}">${_escHtml(m.text)}</th>`
      } else {
        tableHtml += `<th rowspan="${totalHeaderRows}" colspan="${take}">${_escHtml(m.text)}</th>`
        tableHtml += `<th colspan="${m.colspan - take}">${_escHtml(m.text)}</th>`
      }
      remainingRowspan -= take
    }
    // 剩余主表头（无 rowspan）
    for (; mergedIdx < merged.length; mergedIdx++) {
      const m = merged[mergedIdx]
      const colspanAttr = m.colspan > 1 ? ` colspan="${m.colspan}"` : ''
      tableHtml += `<th${colspanAttr}>${_escHtml(m.text)}</th>`
    }
    tableHtml += '</tr>'
  }

  // 子表头行（从 rows 前 N 行提取，带 colspan 合并）
  for (const row of headerRows) {
    tableHtml += '<tr>'
    // 合并连续重复值（如：2609|2609 → colspan=2 的 2609）
    const mergedCells: { text: string; colspan: number }[] = []
    for (const cell of row) {
      const last = mergedCells[mergedCells.length - 1]
      if (last && last.text === cell) {
        last.colspan++
      } else {
        mergedCells.push({ text: cell, colspan: 1 })
      }
    }
    // 补齐缺失列（首列被 rowspan 覆盖时不需要补）
    const expectedCols = headers.length - colDiff
    const rowCellDiff = expectedCols - row.length
    if (rowCellDiff > 0) {
      for (let k = 0; k < rowCellDiff; k++) tableHtml += '<th></th>'
    }
    // 渲染合并后的单元格
    for (const mc of mergedCells) {
      const colspanAttr = mc.colspan > 1 ? ` colspan="${mc.colspan}"` : ''
      tableHtml += `<th${colspanAttr}>${_escHtml(mc.text)}</th>`
    }
    tableHtml += '</tr>'
  }
  tableHtml += '</thead>'

  // ── 渲染数据行（tbody） ──
  const dataRows = rows.slice(headerRowCount)
  if (dataRows.length > 0) {
    tableHtml += '<tbody>'
    for (const row of dataRows) {
      tableHtml += '<tr>'
      const cellDiff = headers.length - row.length
      if (cellDiff > 0) {
        for (let k = 0; k < cellDiff; k++) tableHtml += '<td></td>'
      }
      for (const cell of row) {
        tableHtml += `<td>${_escHtml(cell)}</td>`
      }
      tableHtml += '</tr>'
    }
    tableHtml += '</tbody>'
  }

  tableHtml += '</table></div>'
  return tableHtml
}

function _escHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

/** 点击内容区的图片时弹出预览 */
function onContentClick(e: MouseEvent) {
  const target = e.target as HTMLElement
  if (target.tagName === 'IMG' && target.getAttribute('src')) {
    e.preventDefault()
    previewImageUrl.value = target.getAttribute('src') || ''
  }
}
const chunksLoading = ref(false)

const toggleChunks = async () => {
  showChunks.value = true
  if (chunks.value.length === 0) {
    chunksLoading.value = true
    try {
      const res: any = await request.get(`/knowledge/${knowledge.value.id}/chunks`)
      if (res.code === 200) {
        chunks.value = res.data || []
      }
    } catch { /* ignore */ }
    chunksLoading.value = false
  }
}

const handleSortChange = (tableIdx: number, sortInfo: any) => {
  const blocks = parseMixedContent(knowledge.value?.content || '', knowledge.value?.tableMeta || null)
  let tableFound = 0
  for (const block of blocks) {
    if (block.type === 'table') {
      if (tableFound === tableIdx && sortInfo && sortInfo.prop && sortInfo.order) {
        const prop = sortInfo.prop as string
        const desc = sortInfo.order === 'descending'
        block.tableData.sort((a: Record<string, string>, b: Record<string, string>) => {
          const na = parseFloat(a[prop]), nb = parseFloat(b[prop])
          if (!isNaN(na) && !isNaN(nb)) return desc ? nb - na : na - nb
          return desc ? b[prop].localeCompare(a[prop]) : a[prop].localeCompare(b[prop])
        })
        break
      }
      tableFound++
    }
  }
  renderedBlocks.value = blocks
}

onMounted(async () => {
  const id = Number(route.params.id)
  if (!id) {
    error.value = '无效的知识 ID'
    loading.value = false
    return
  }

  try {
    const res: any = await request.get(`/knowledge/${id}`)
    if (res.code === 200 && res.data) {
      knowledge.value = res.data
      renderedBlocks.value = parseMixedContent(knowledge.value?.content || '', knowledge.value?.tableMeta || null)
        } else {
      error.value = res.message || '获取知识详情失败'
    }
  } catch (e: any) {
    error.value = e.message || '网络错误'
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.knowledge-detail-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  color: #333333;
}

.page-header {
  display: flex;
  align-items: center;
  padding: 16px 24px;
  background: #fafbfc;
  border-bottom: 1px solid #e8eaed;
  gap: 16px;
  flex-shrink: 0;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: #f0f2f5;
  border: 1px solid #d0d5dd;
  border-radius: 8px;
  color: #666666;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 14px;
}

.back-btn:hover {
  background: #e5e8eb;
  color: #222222;
}

.loading-text {
  color: #999999;
  font-size: 14px;
}

.loading-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: #999999;
}

.loading-spinner {
  width: 36px;
  height: 36px;
  border: 3px solid rgba(212, 160, 23, 0.2);
  border-top-color: #d4a017;
  border-radius: 50%;
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.error-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: #f85149;
  font-size: 15px;
}

.detail-content {
  flex: 1;
  overflow-y: auto;
  max-width: 900px;
  margin: 0 auto;
  padding: 32px 24px 48px;
  width: 100%;
}

.detail-title {
  font-size: 26px;
  font-weight: 700;
  color: #1a1a2e;
  margin: 0 0 20px 0;
  line-height: 1.3;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 28px;
  padding-bottom: 20px;
  border-bottom: 1px solid #e8eaed;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #888888;
}

.detail-body {
  font-size: 15px;
  line-height: 1.9;
  color: #333333;
}

.detail-body :deep(p) {
  margin: 0 0 14px 0;
}

.detail-body :deep(h2) {
  font-size: 20px;
  font-weight: 600;
  color: #1a1a2e;
  margin: 28px 0 14px 0;
  padding-bottom: 8px;
  border-bottom: 1px solid #e8eaed;
}

.detail-body :deep(h3) {
  font-size: 17px;
  font-weight: 600;
  color: #222222;
  margin: 24px 0 10px 0;
}

.detail-body :deep(ul),
.detail-body :deep(ol) {
  margin: 10px 0;
  padding-left: 22px;
}

.detail-body :deep(li) {
  margin: 5px 0;
}

.detail-body :deep(strong) {
  color: #222222;
  font-weight: 600;
}

.detail-body :deep(a) {
  color: #1a73e8;
  text-decoration: none;
}

.detail-body :deep(a:hover) {
  text-decoration: underline;
}

.detail-body :deep(blockquote) {
  margin: 14px 0;
  padding: 12px 18px;
  background: #fef8e8;
  border-left: 3px solid #d4a017;
  border-radius: 0 6px 6px 0;
  color: #555555;
}

.detail-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 16px 0;
  background: #ffffff;
  border: 1px solid #d0d5dd;
}

.detail-body :deep(th) {
  background: #f5f0e0;
  color: #333333;
  font-weight: 600;
  padding: 10px 14px;
  text-align: left;
  border: 1px solid #d0d5dd;
}

.detail-body :deep(td) {
  background: #ffffff;
  padding: 8px 14px;
  border: 1px solid #d0d5dd;
}

.detail-body :deep(tr:hover td) {
  background: #fafaf5;
}

.detail-body :deep(hr) {
  border: none;
  border-top: 1px solid #e0e0e0;
  margin: 24px 0;
}

.detail-body :deep(code) {
  background: #f0f0f0;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'Fira Code', monospace;
}

.original-link { color: #1a73e8; text-decoration: none; }
.original-link:hover { text-decoration: underline; color: #1557b0; }
.meta-item.status-pending { color: #b8860b; }
.meta-item.status-processing { color: #1a73e8; }
.meta-item.status-vectorized { color: #2e7d32; }
.meta-item.status-failed { color: #d32f2f; }

/* ====== 文章排版 ====== */
.detail-content { padding: 0 20px; }

/* 主标题居中 */
.detail-title { text-align: center; font-size: 22px; font-weight: 700; margin: 24px 0 8px; }

/* 来源小字 */
.source-line { text-align: center; font-size: 13px; color: #888888; margin-bottom: 24px; }

/* 版块标题（一、今日行情：等）黄底高亮 */
.detail-body :deep(p.section-title) {
  background: #d4a017; color: #ffffff; font-weight: 700; font-size: 15px;
  padding: 4px 10px; display: inline-block; margin: 18px 0 10px; border-radius: 2px;
  text-indent: 0;
}

/* 二级标题（国际期货等）左对齐加粗 */
.detail-body :deep(p.sub-title) {
  font-weight: 700; font-size: 15px; margin: 14px 0 8px; color: #222222; text-indent: 0;
}

/* 正文段落：首行缩进2字符 */
.detail-body :deep(p) {
  text-indent: 2em; line-height: 1.8; margin: 8px 0; font-size: 15px; color: #333333;
}

/* ★ 开头的段落：不缩进 */
.detail-body :deep(p.star-item) {
  text-indent: 0;
}

/* 表格灰色表头 */
.enhanced-table-wrapper {
  margin: 16px 0;
  background: #ffffff;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #d0d5dd;
}
.table-caption {
  padding: 10px 14px;
  font-size: 14px;
  font-weight: 600;
  color: #333333;
  background: #f5f0e0;
  border-bottom: 1px solid #e0e0e0;
}
.table-footer {
  padding: 6px 14px;
  font-size: 12px;
  color: #888888;
  background: #fafbfc;
  border-top: 1px solid #ececec;
}
.enhanced-table-wrapper :deep(.el-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: #f5f0e0;
  --el-table-row-hover-bg-color: #fafaf5;
  --el-table-border-color: #e0e0e0;
  --el-table-text-color: #333333;
  --el-table-header-text-color: #222222;
}


/* 概览统计 */
.chunk-summary {
  display: flex;
  gap: 16px;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e8eaed;
}
.chunk-summary-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  background: #fafbfc;
  border: 1px solid #e8eaed;
  border-radius: 8px;
  padding: 12px 20px;
  flex: 1;
}
.summary-value {
  font-size: 20px;
  font-weight: 700;
  color: #d4a017;
}
.summary-label {
  font-size: 11px;
  color: #888888;
}

.chunks-loading, .chunks-empty {
  color: #888888;
  font-size: 14px;
  padding: 40px 0;
  text-align: center;
}
.chunks-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.chunk-card {
  background: #ffffff;
  border: 1px solid #e8eaed;
  border-radius: 10px;
  overflow: hidden;
}
.chunk-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fafbfc;
  border-bottom: 1px solid #ececec;
}
.chunk-card-header-left, .chunk-card-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.chunk-index {
  font-size: 11px;
  font-weight: 600;
  color: #888888;
  background: #f0f0f0;
  padding: 2px 8px;
  border-radius: 4px;
}
.chunk-type-tag {
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 4px;
}
.tag-text { background: rgba(26,115,232,0.1); color: #1a73e8; }
.tag-table { background: rgba(212,160,23,0.1); color: #b8860b; }
.chunk-tokens {
  font-size: 11px;
  color: #888888;
}
.chunk-order {
  font-size: 10px;
  color: #aaaaaa;
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 3px;
}
.chunk-card-body {
  max-height: 300px;
  overflow-y: auto;
  padding: 12px 16px;
}
.chunk-card-body::-webkit-scrollbar {
  width: 4px;
}
.chunk-card-body::-webkit-scrollbar-thumb {
  background: #d0d0d0;
  border-radius: 2px;
}
.chunk-card-content {
  font-size: 13px;
  line-height: 1.8;
  color: #333333;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  margin: 0;
}

/* docx-preview */
.docx-preview-wrapper {
  background: #fff;
  border-radius: 8px;
  padding: 24px 32px;
  overflow-x: auto;
  color: initial;
}
.docx-render-area {
  min-height: 400px;
}
/* 防止暗色主题 :deep 样式渗透到 docx 内部（表格背景、文字颜色等） */
.docx-preview-wrapper :deep(table),
.docx-preview-wrapper :deep(td),
.docx-preview-wrapper :deep(th),
.docx-preview-wrapper :deep(tr),
.docx-preview-wrapper :deep(p),
.docx-preview-wrapper :deep(div) {
  background: initial;
  color: initial;
  border-color: initial;
}
.docx-error-bar {
  padding: 8px 16px;
  margin-bottom: 16px;
  background: rgba(245,200,122,0.1);
  border: 1px solid rgba(245,200,122,0.3);
  border-radius: 6px;
}
.docx-error-text {
  color: #f5c87a;
  font-size: 13px;
}
</style>

<!-- 全局样式：覆盖 el-drawer 的默认白色背景 -->
<style>
.chunk-drawer-dark {
  background: #0d1117 !important;
  border-left: 1px solid rgba(255,255,255,0.08);
}
.chunk-drawer-dark .el-drawer__header {
  color: #f5c87a !important;
  font-weight: 600;
  font-size: 16px;
  border-bottom: 1px solid rgba(255,255,255,0.06);
  margin-bottom: 0;
  padding: 20px 20px 16px;
}
.chunk-drawer-dark .el-drawer__close-btn {
  color: #6e7681 !important;
}
.chunk-drawer-dark .el-drawer__body {
  padding: 16px 20px;
  color: #c9d1d9 !important;
  background: #0d1117 !important;
}

.pdf-preview-wrapper {
  margin: 16px 0;
  border: 1px solid #d0d5dd;
  border-radius: 8px;
  overflow: hidden;
  background: #fafbfc;
}
.pdf-iframe {
  width: 100%;
  height: 80vh;
  border: none;
  display: block;
}
.pdf-toolbar {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px 16px;
  background: #ffffff;
  border-top: 1px solid #e8eaed;
}
.pdf-open-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #1a73e8;
  text-decoration: none;
  padding: 6px 14px;
  border: 1px solid #d0d5dd;
  border-radius: 6px;
  transition: all 0.2s;
}
.pdf-open-link:hover {
  background: #f0f6ff;
  border-color: #1a73e8;
}

.html-content {
  font-size: 13px;
  line-height: 1.7;
  color: #333333;
}

.html-content img {
  max-width: 100%;
  height: auto;
  border-radius: 4px;
  margin: 8px 0;
  cursor: zoom-in;
}

.html-content .html-table {
  width: 100%;
  border-collapse: collapse;
  border: 1px solid #d0d5dd;
  font-size: 12px;
  margin: 12px 0;
}
.html-content .html-table th,
.html-content .html-table td {
  border: 1px solid #d0d5dd;
  padding: 6px 10px;
  text-align: center;
}
.html-content .html-table th {
  background: #f5f0e0;
  font-weight: 600;
  color: #222222;
}
.html-content .html-table td {
  color: #333333;
}
.html-content .html-table tr:nth-child(even) td {
  background: #fafbfc;
}

.html-content .enhanced-table-wrapper {
  margin: 16px 0;
}

/* 图片预览遮罩 */
.image-preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 9999;
  background: rgba(0,0,0,0.88);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  user-select: none;
}

.preview-toolbar {
  position: fixed;
  top: 16px;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 4px;
  background: rgba(255,255,255,0.12);
  backdrop-filter: blur(8px);
  border-radius: 8px;
  padding: 6px 12px;
  z-index: 10;
}
.preview-btn {
  background: transparent;
  border: none;
  color: rgba(255,255,255,0.8);
  font-size: 18px;
  width: 32px;
  height: 32px;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  text-decoration: none;
  transition: background 0.15s;
}
.preview-btn:hover { background: rgba(255,255,255,0.15); color: #fff; }
.preview-btn:disabled { opacity: 0.3; cursor: default; }
.preview-close-btn { color: #ff6b6b; }
.preview-close-btn:hover { background: rgba(255,107,107,0.2); color: #ff6b6b; }
.preview-zoom-level {
  font-size: 13px;
  color: rgba(255,255,255,0.7);
  min-width: 44px;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.preview-sep {
  width: 1px;
  height: 20px;
  background: rgba(255,255,255,0.15);
  margin: 0 4px;
}

.preview-img-wrap {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  width: 100%;
  height: 100%;
  padding: 80px 40px 56px;
}

.image-preview-img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  border-radius: 4px;
  cursor: grab;
  box-shadow: 0 8px 40px rgba(0,0,0,0.4);
}

.preview-footer {
  position: fixed;
  bottom: 16px;
  left: 50%;
  transform: translateX(-50%);
}
.preview-tip {
  font-size: 12px;
  color: rgba(255,255,255,0.4);
}
</style>
