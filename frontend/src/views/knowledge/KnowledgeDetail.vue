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
        <!-- 来源 -->
        <span class="meta-item" v-if="knowledge.sourceName || knowledge.sourceType">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M7 1C3.69 1 1 3.69 1 7C1 10.31 3.69 13 7 13C10.31 13 13 10.31 13 7C13 3.69 10.31 1 7 1Z" stroke="currentColor" stroke-width="1.5"/>
            <path d="M7 4V7L9 9" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
          </svg>
          {{ knowledge.sourceName || knowledge.sourceType }}
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
      </div>

      <div class="detail-body">
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
      </div>

    </div>

    <!-- 切片列表 - 侧滑抽屉 -->
    <el-drawer
      v-model="showChunks"
      :title="`切片列表（${chunks.length} 个）`"
      size="520px"
      destroy-on-close
    >
      <div v-if="chunksLoading" class="chunks-loading">加载中...</div>
      <div v-else-if="chunks.length === 0" class="chunks-empty">暂无切片数据</div>
      <div v-else class="chunks-list">
        <div v-for="(chunk, i) in chunks" :key="i" class="chunk-card">
          <div class="chunk-card-header">
            <span class="chunk-index">#{{ i + 1 }}</span>
            <span :class="'chunk-type-tag tag-' + (chunk.chunkType || 'text')">
              {{ chunk.chunkType === 'table' ? '📊 表格' : '📝 文本' }}
            </span>
            <span class="chunk-tokens" v-if="chunk.tokenCount">{{ chunk.tokenCount }} tokens</span>
            <span class="chunk-tokens" v-else>{{ (chunk.content || '').length }} 字符</span>
          </div>
          <div class="chunk-card-content">{{ (chunk.content || '').slice(0, 300) }}...</div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { marked } from 'marked'
import request from '@/api'

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const knowledge = ref<any>(null)

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
        while (cleaned.length < headers.length) cleaned.push('')
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

  const flushText = (textParts: string[]) => {
    const raw = textParts.join('\n').trim()
    if (raw) blocks.push({ type: 'text', html: marked(raw, { breaks: true, gfm: true }) })
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
  background: #0d1117;
  color: #c9d1d9;
}

.page-header {
  display: flex;
  align-items: center;
  padding: 16px 24px;
  background: rgba(255, 255, 255, 0.02);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  gap: 16px;
  flex-shrink: 0;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  color: #8b949e;
  cursor: pointer;
  transition: all 0.2s;
  font-size: 14px;
}

.back-btn:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #e6edf3;
}

.loading-text {
  color: #6e7681;
  font-size: 14px;
}

.loading-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  color: #6e7681;
}

.loading-spinner {
  width: 36px;
  height: 36px;
  border: 3px solid rgba(245, 200, 122, 0.2);
  border-top-color: #f5c87a;
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
  color: #f5c87a;
  margin: 0 0 20px 0;
  line-height: 1.3;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 28px;
  padding-bottom: 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #6e7681;
}

.detail-body {
  font-size: 15px;
  line-height: 1.9;
  color: #c9d1d9;
}

.detail-body :deep(p) {
  margin: 0 0 14px 0;
}

.detail-body :deep(h2) {
  font-size: 20px;
  font-weight: 600;
  color: #f5c87a;
  margin: 28px 0 14px 0;
  padding-bottom: 8px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.detail-body :deep(h3) {
  font-size: 17px;
  font-weight: 600;
  color: #e6edf3;
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
  color: #e6edf3;
  font-weight: 600;
}

.detail-body :deep(a) {
  color: #58a6ff;
  text-decoration: none;
}

.detail-body :deep(a:hover) {
  text-decoration: underline;
}

.detail-body :deep(blockquote) {
  margin: 14px 0;
  padding: 12px 18px;
  background: rgba(245, 200, 122, 0.06);
  border-left: 3px solid #f5c87a;
  border-radius: 0 6px 6px 0;
  color: #c9d1d9;
}

.detail-body :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 16px 0;
  background: #161b22;
  border-radius: 8px;
  overflow: hidden;
}

.detail-body :deep(th) {
  background: rgba(245, 200, 122, 0.15);
  color: #f5c87a;
  font-weight: 600;
  padding: 10px 14px;
  text-align: left;
  border-bottom: 2px solid rgba(255, 255, 255, 0.08);
}

.detail-body :deep(td) {
  padding: 8px 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.04);
}

.detail-body :deep(tr:hover td) {
  background: rgba(245, 200, 122, 0.03);
}

.detail-body :deep(hr) {
  border: none;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
  margin: 24px 0;
}

.detail-body :deep(code) {
  background: rgba(255, 255, 255, 0.06);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'Fira Code', monospace;
}

.meta-item.status-pending { color: #e6c384; }
.meta-item.status-processing { color: #58a6ff; }
.meta-item.status-vectorized { color: #3fb950; }
.meta-item.status-failed { color: #f85149; }

.enhanced-table-wrapper {
  margin: 16px 0;
  background: #161b22;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.06);
}
.table-caption {
  padding: 10px 14px;
  font-size: 14px;
  font-weight: 600;
  color: #f5c87a;
  background: rgba(245, 200, 122, 0.08);
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.table-footer {
  padding: 6px 14px;
  font-size: 12px;
  color: #6e7681;
  background: rgba(255, 255, 255, 0.02);
  border-top: 1px solid rgba(255, 255, 255, 0.04);
}
.enhanced-table-wrapper :deep(.el-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: rgba(245, 200, 122, 0.1);
  --el-table-row-hover-bg-color: rgba(245, 200, 122, 0.05);
  --el-table-border-color: rgba(255, 255, 255, 0.08);
  --el-table-text-color: #c9d1d9;
  --el-table-header-text-color: #f5c87a;
}

/* 切片抽屉 */
.chunks-loading, .chunks-empty {
  color: #6e7681;
  font-size: 14px;
  padding: 20px 0;
  text-align: center;
}
.chunks-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.chunk-card {
  background: rgba(255,255,255,0.02);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 8px;
  padding: 14px 16px;
}
.chunk-card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.chunk-index {
  font-size: 12px;
  font-weight: 600;
  color: #6e7681;
  background: rgba(255,255,255,0.05);
  padding: 2px 8px;
  border-radius: 4px;
}
.chunk-type-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
}
.tag-text { background: rgba(88,166,255,0.15); color: #58a6ff; }
.tag-table { background: rgba(245,200,122,0.15); color: #f5c87a; }
.chunk-tokens {
  font-size: 11px;
  color: #6e7681;
  margin-left: auto;
}
.chunk-card-content {
  font-size: 13px;
  line-height: 1.7;
  color: #8b949e;
}
</style>
