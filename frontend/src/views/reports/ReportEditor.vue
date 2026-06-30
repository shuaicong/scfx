<template>
  <div class="editor-container">
    <!-- Header -->
    <div class="editor-header">
      <input
        v-model="reportTitle"
        class="title-input"
        placeholder="输入报告标题..."
        :disabled="isPreview"
      />
      <div class="header-actions">
        <el-button
          :class="isPreview ? 'btn-primary' : 'btn-secondary'"
          @click="togglePreview"
        >
          {{ isPreview ? '编辑' : '预览' }}
        </el-button>
        <el-button
          class="btn-secondary"
          :loading="saving"
          @click="handleSave"
        >
          保存
        </el-button>
        <el-button
          class="btn-primary"
          :loading="exporting"
          @click="handleExport"
        >
          导出
        </el-button>
      </div>
    </div>

    <!-- Toolbar -->
    <div class="toolbar" :class="{ 'toolbar-preview': isPreview }">
      <div class="tb-grp">
        <span class="tb-label">H</span>
        <button
          class="tb-btn"
          :class="{ active: editor?.isActive('heading', { level: 1 }) }"
          :disabled="isPreview"
          @click="editor?.chain().focus().toggleHeading({ level: 1 }).run()"
        >H1</button>
        <button
          class="tb-btn"
          :class="{ active: editor?.isActive('heading', { level: 2 }) }"
          :disabled="isPreview"
          @click="editor?.chain().focus().toggleHeading({ level: 2 }).run()"
        >H2</button>
        <button
          class="tb-btn"
          :class="{ active: editor?.isActive('heading', { level: 3 }) }"
          :disabled="isPreview"
          @click="editor?.chain().focus().toggleHeading({ level: 3 }).run()"
        >H3</button>
      </div>

      <div class="tb-divider"></div>

      <div class="tb-grp">
        <button
          class="tb-btn"
          :class="{ active: editor?.isActive('bold') }"
          :disabled="isPreview"
          @click="editor?.chain().focus().toggleBold().run()"
        ><b>B</b></button>
        <button
          class="tb-btn"
          :class="{ active: editor?.isActive('italic') }"
          :disabled="isPreview"
          @click="editor?.chain().focus().toggleItalic().run()"
        ><i>I</i></button>
        <button
          class="tb-btn"
          :class="{ active: editor?.isActive('underline') }"
          :disabled="isPreview"
          @click="editor?.chain().focus().toggleUnderline().run()"
        ><u>U</u></button>
      </div>

      <div class="tb-divider"></div>

      <div class="tb-grp">
        <button
          class="tb-btn red"
          :class="{ active: editor?.isActive('redMark') }"
          :disabled="isPreview"
          @click="editor?.chain().focus().toggleRedMark().run()"
        ><span style="color: var(--red); font-weight: 700;">红</span></button>
        <button
          class="tb-btn green"
          :class="{ active: editor?.isActive('greenMark') }"
          :disabled="isPreview"
          @click="editor?.chain().focus().toggleGreenMark().run()"
        ><span style="color: var(--green); font-weight: 700;">绿</span></button>
      </div>

      <div class="tb-divider"></div>

      <div class="tb-grp">
        <button
          class="tb-btn"
          title="插入表格"
          :disabled="isPreview"
          @click="insertTable"
        ><span style="font-size: 16px;">&#x229E;</span></button>
        <button
          class="tb-btn"
          title="合并单元格"
          :disabled="isPreview"
          @click="editor?.chain().focus().mergeCells().run()"
        ><span style="font-size: 16px;">&#x229F;</span></button>
        <button
          class="tb-btn"
          title="拆分单元格"
          :disabled="isPreview"
          @click="editor?.chain().focus().splitCell().run()"
        ><span style="font-size: 16px; transform: rotate(45deg); display: inline-block;">&#x229F;</span></button>
      </div>

      <div class="tb-divider"></div>

      <div class="tb-grp">
        <button
          class="tb-btn"
          style="color: var(--accent);"
          :disabled="isPreview"
          @click="handleInsertChart"
        >图表</button>
      </div>

      <div class="tb-divider"></div>

      <div class="tb-grp">
        <button
          class="tb-btn"
          title="插入分页符"
          :disabled="isPreview"
          @click="editor?.chain().focus().setPageBreak().run()"
        >分页</button>
      </div>
    </div>

    <!-- Editor Layout -->
    <div class="editor-layout">
      <!-- Main Editor Area -->
      <div class="editor-main">
        <div v-if="loading" class="editor-loading">
          <div class="loading-spinner"></div>
          <span>加载中...</span>
        </div>
        <EditorContent
          v-else
          :editor="editor"
          class="editor-content"
        />
      </div>

      <!-- Right Sidebar -->
      <div class="editor-sidebar">
        <div class="panel-title">生成规则</div>

        <div class="panel-section">
          <div class="panel-section-title">品种设置</div>
          <div class="field">
            <label>关联品种</label>
            <select v-model="config.variety" :disabled="isPreview">
              <option value="corn">玉米</option>
              <option value="rice">稻米</option>
              <option value="wheat">小麦</option>
            </select>
          </div>
          <div class="field">
            <label>数据天数</label>
            <select v-model="config.dateRange" :disabled="isPreview">
              <option value="7">近 7 天</option>
              <option value="30">近 30 天</option>
              <option value="90">近 90 天</option>
            </select>
          </div>
        </div>

        <div class="panel-section">
          <div class="panel-section-title">价格数据</div>
          <label class="check-row">
            <input type="checkbox" v-model="config.includePortPrice" :disabled="isPreview" />
            <span>港口价格对比</span>
          </label>
          <label class="check-row">
            <input type="checkbox" v-model="config.includeRegionPrice" :disabled="isPreview" />
            <span>产区价格</span>
          </label>
          <label class="check-row">
            <input type="checkbox" v-model="config.includeProcessPrice" :disabled="isPreview" />
            <span>深加工收购价</span>
          </label>
        </div>

        <div class="panel-section">
          <div class="panel-section-title">走势图</div>
          <div class="chart-list">
            <div v-for="(chart, idx) in config.charts" :key="idx" class="chart-item">
              <span>{{ chart.label }}</span>
              <button class="chart-del" @click="removeChart(idx)" :disabled="isPreview">✕</button>
            </div>
            <div v-if="config.charts.length === 0" class="chart-empty">暂未添加走势图</div>
          </div>
          <button class="btn btn-sm add-chart-btn" :disabled="isPreview" @click="addChart">＋ 添加走势图</button>
        </div>

        <div class="panel-section">
          <div class="panel-section-title">知识库检索</div>
          <div class="field">
            <label>搜索分类</label>
            <select v-model="config.knowledgeCategories" multiple :disabled="isPreview">
              <option value="weekly">玉米周报</option>
              <option value="morning">玉米晨报</option>
              <option value="daily">玉米日报</option>
            </select>
          </div>
        </div>

        <div class="panel-section">
          <div class="panel-section-title">数据源状态</div>
          <div class="ds-status-list">
            <span class="ds-item">
              <span class="ds-indicator ready"></span>
              <span class="ds-name">粮达网价格</span>
              <span class="ds-badge ready">就绪</span>
            </span>
            <span class="ds-item">
              <span class="ds-indicator pending"></span>
              <span class="ds-name">我的钢铁</span>
              <span class="ds-badge planned">待建设</span>
            </span>
            <span class="ds-item">
              <span class="ds-indicator pending"></span>
              <span class="ds-name">USDA/CONAB/BAGE</span>
              <span class="ds-badge planned">待建设</span>
            </span>
            <span class="ds-item">
              <span class="ds-indicator manual"></span>
              <span class="ds-name">海南粮批</span>
              <span class="ds-badge manual">手动</span>
            </span>
            <span class="ds-item">
              <span class="ds-indicator ready"></span>
              <span class="ds-name">中央气象台</span>
              <span class="ds-badge ready">就绪</span>
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- Status Bar -->
    <div class="status-bar">
      <span class="status-left">
        版本 v{{ currentVersion }}
        <span class="status-sep">·</span>
        约 {{ wordCount }} 字
        <span class="status-sep">·</span>
        <span :class="saveStatusClass">{{ saveStatusText }}</span>
      </span>
      <span class="status-right">
        <span class="status-time">{{ lastSaveDisplay }}</span>
      </span>
    </div>
  </div>
</template>

<style scoped>
.editor-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--bg-primary);
  color: var(--text-primary);
  overflow: hidden;
}

/* ── Header ── */
.editor-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 10px 20px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.title-input {
  flex: 1;
  background: transparent;
  border: none;
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 600;
  outline: none;
  padding: 4px 0;
}

.title-input::placeholder {
  color: var(--text-muted);
}

.title-input:disabled {
  opacity: 0.7;
}

.header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* ── Toolbar ── */
.toolbar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 20px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
  flex-wrap: wrap;
}

.toolbar-preview {
  opacity: 0.6;
  pointer-events: none;
}

.tb-grp {
  display: flex;
  align-items: center;
  gap: 2px;
}

.tb-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  margin-right: 2px;
  letter-spacing: 0.5px;
}

.tb-btn {
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 12px;
  border: none;
  background: transparent;
  color: var(--text-secondary);
  cursor: pointer;
  transition: all 0.12s ease;
  white-space: nowrap;
}

.tb-btn:hover:not(:disabled) {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.tb-btn.active {
  background: rgba(88, 166, 255, 0.15);
  color: var(--accent-blue);
}

.tb-btn:disabled {
  opacity: 0.4;
  cursor: default;
}

.tb-btn.red.active {
  background: rgba(248, 81, 73, 0.15);
}

.tb-btn.green.active {
  background: rgba(63, 185, 80, 0.15);
}

.tb-divider {
  width: 1px;
  height: 20px;
  background: var(--border-color);
  margin: 0 4px;
}

/* ── Editor Layout ── */
.editor-layout {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.editor-main {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
}

.editor-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 12px;
  color: var(--text-muted);
  font-size: 14px;
}

.loading-spinner {
  width: 32px;
  height: 32px;
  border: 3px solid var(--border-color);
  border-top-color: var(--accent-blue);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ── Editor Content ── */
.editor-content {
  flex: 1;
  padding: 40px 60px;
  max-width: 900px;
  margin: 0 auto;
  width: 100%;
  box-sizing: border-box;
  outline: none;
}

.editor-content :deep(.ProseMirror) {
  min-height: 400px;
  outline: none;
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-primary);
}

.editor-content :deep(.ProseMirror > *) {
  margin-bottom: 8px;
}

.editor-content :deep(.ProseMirror h1) {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.4;
  margin-bottom: 16px;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-color);
  padding-bottom: 12px;
}

.editor-content :deep(.ProseMirror h2) {
  font-size: 20px;
  font-weight: 600;
  line-height: 1.4;
  margin-top: 24px;
  margin-bottom: 12px;
  color: var(--accent);
}

.editor-content :deep(.ProseMirror h3) {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
  margin-top: 20px;
  margin-bottom: 8px;
  color: var(--text-primary);
}

.editor-content :deep(.ProseMirror p) {
  margin-bottom: 8px;
}

.editor-content :deep(.ProseMirror table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 13px;
}

.editor-content :deep(.ProseMirror th) {
  border: 1px solid var(--border-color);
  padding: 8px 12px;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  font-weight: 600;
  text-align: left;
}

.editor-content :deep(.ProseMirror td) {
  border: 1px solid var(--border-color);
  padding: 8px 12px;
  color: var(--text-secondary);
}

.editor-content :deep(.ProseMirror th.ProseMirror-selectednode),
.editor-content :deep(.ProseMirror td.ProseMirror-selectednode) {
  outline: 2px solid var(--accent-blue);
}

.editor-content :deep(.ProseMirror .subtitle) {
  color: var(--text-muted);
  font-size: 13px;
  margin-bottom: 20px;
}

.editor-content :deep(.ProseMirror .ds) {
  font-size: 11px;
  color: var(--text-muted);
  margin-top: -4px;
  margin-bottom: 12px;
  font-style: italic;
}

.editor-content :deep(.ProseMirror p.is-editor-empty:first-child::before) {
  color: var(--text-muted);
  content: attr(data-placeholder);
  float: left;
  height: 0;
  pointer-events: none;
}

.editor-content :deep(.ProseMirror table .selectedCell) {
  background: rgba(88, 166, 255, 0.1);
}

/* ── Right Sidebar ── */
.editor-sidebar {
  width: 280px;
  flex-shrink: 0;
  background: var(--bg-secondary);
  border-left: 1px solid var(--border-color);
  overflow-y: auto;
  padding: 16px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 16px;
  color: var(--text-primary);
}

.panel-section {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border-color);
}

.panel-section:last-child {
  border-bottom: none;
}

.panel-section-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 10px;
}

.field {
  margin-bottom: 10px;
}

.field label {
  display: block;
  font-size: 11px;
  color: var(--text-secondary);
  margin-bottom: 4px;
}

.field select {
  width: 100%;
  padding: 6px 8px;
  border-radius: 4px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-primary);
  font-size: 12px;
  outline: none;
}

.field select:focus {
  border-color: var(--accent-blue);
}

.field select[multiple] {
  height: 60px;
}

.check-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
  cursor: pointer;
}

.check-row:hover {
  color: var(--text-primary);
}

.check-row input[type="checkbox"] {
  accent-color: var(--accent-blue);
}

.chart-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}

.chart-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--text-secondary);
  padding: 4px 6px;
  background: var(--bg-tertiary);
  border-radius: 4px;
}

.chart-del {
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  font-size: 11px;
  padding: 2px 4px;
  border-radius: 3px;
}

.chart-del:hover {
  color: var(--red);
  background: rgba(248, 81, 73, 0.15);
}

.chart-empty {
  font-size: 11px;
  color: var(--text-muted);
  padding: 4px 0;
}

.add-chart-btn {
  width: 100%;
  padding: 6px;
  font-size: 12px;
}

.btn {
  padding: 6px 12px;
  border-radius: 4px;
  border: none;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.12s ease;
  background: var(--bg-card);
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
}

.btn:hover:not(:disabled) {
  background: var(--bg-hover);
  color: var(--text-primary);
}

.btn:disabled {
  opacity: 0.4;
  cursor: default;
}

.btn-sm {
  padding: 4px 8px;
  font-size: 11px;
}

/** Data source status */
.ds-status-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ds-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.ds-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}

.ds-indicator.ready {
  background: var(--accent-green);
  box-shadow: 0 0 4px rgba(63, 185, 80, 0.4);
}

.ds-indicator.pending {
  background: var(--accent-yellow);
  box-shadow: 0 0 4px rgba(210, 153, 34, 0.3);
}

.ds-indicator.manual {
  background: var(--accent-orange);
  box-shadow: 0 0 4px rgba(240, 136, 62, 0.3);
}

.ds-name {
  flex: 1;
  color: var(--text-secondary);
}

.ds-badge {
  font-size: 10px;
  padding: 1px 5px;
  border-radius: 3px;
  font-weight: 500;
}

.ds-badge.ready {
  background: rgba(63, 185, 80, 0.15);
  color: var(--accent-green);
}

.ds-badge.planned {
  background: rgba(210, 153, 34, 0.15);
  color: var(--accent-yellow);
}

.ds-badge.manual {
  background: rgba(240, 136, 62, 0.15);
  color: var(--accent-orange);
}

/* ── Status Bar ── */
.status-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 20px;
  background: var(--bg-secondary);
  border-top: 1px solid var(--border-color);
  flex-shrink: 0;
  font-size: 12px;
}

.status-left {
  color: var(--text-muted);
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-sep {
  color: var(--border-color);
}

.status-saved {
  color: var(--accent-green);
}

.status-unsaved {
  color: var(--accent-yellow);
}

.status-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-time {
  color: var(--text-muted);
  font-size: 11px;
}

/* ── Button Presets ── */
.btn-primary {
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  border: none;
  background: linear-gradient(135deg, var(--accent-blue), var(--accent-purple));
  color: #fff;
  transition: all 0.2s ease;
}

.btn-primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(88, 166, 255, 0.3);
}

.btn-secondary {
  padding: 6px 14px;
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-primary);
  transition: all 0.2s ease;
}

.btn-secondary:hover {
  background: var(--bg-hover);
  border-color: var(--accent-blue);
}
</style>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useEditor, EditorContent } from '@tiptap/vue-3'
import { Mark, Node, mergeAttributes } from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import Table from '@tiptap/extension-table'
import TableRow from '@tiptap/extension-table-row'
import TableCell from '@tiptap/extension-table-cell'
import TableHeader from '@tiptap/extension-table-header'
import ImageExt from '@tiptap/extension-image'
import TextAlign from '@tiptap/extension-text-align'
import Underline from '@tiptap/extension-underline'
import Placeholder from '@tiptap/extension-placeholder'
import { reportApi } from '@/api/report'

// ── Route ──
const route = useRoute()
const reportId = computed(() => Number(route.params.id))

// ── Custom Marks ──
const RedMark = Mark.create({
  name: 'redMark',
  toDOM() {
    return ['span', { style: 'color: #f85149; font-weight: 700' }, 0]
  },
  parseDOM: [{
    tag: 'span',
    getAttrs: (node: HTMLElement) => node.style.color === '#f85149' && null
  }],
})

const GreenMark = Mark.create({
  name: 'greenMark',
  toDOM() {
    return ['span', { style: 'color: #3fb950; font-weight: 700' }, 0]
  },
  parseDOM: [{
    tag: 'span',
    getAttrs: (node: HTMLElement) => node.style.color === '#3fb950' && null
  }],
})

// ── Custom PageBreak Node ──
const PageBreak = Node.create({
  name: 'pageBreak',
  group: 'block',
  atom: true,
  toDOM: () => ['div', {
    class: 'page-break',
    style: 'page-break-after: always; border-top: 1px dashed #444; margin: 20px 0; text-align: center; color: #666; font-size: 12px;'
  }, '— 分页 —'],
  parseDOM: [{
    tag: 'div[class="page-break"]'
  }],
})

// ── State ──
const loading = ref(true)
const saving = ref(false)
const exporting = ref(false)
const isPreview = ref(false)
const reportTitle = ref('')
const currentVersion = ref(1)
const lastSaveTime = ref('')
const isDirty = ref(false)

const config = reactive({
  variety: 'corn',
  dateRange: '30',
  includePortPrice: true,
  includeRegionPrice: true,
  includeProcessPrice: false,
  charts: [
    { label: '锦州港 30天' },
    { label: '蛇口港 30天' },
    { label: '海口港 30天' },
  ],
  knowledgeCategories: [] as string[],
})

// ── Editor ──
const editor = useEditor({
  content: '',
  extensions: [
    StarterKit.configure({
      heading: { levels: [1, 2, 3] },
    }),
    Table.configure({ resizable: true }),
    TableRow,
    TableCell,
    TableHeader,
    ImageExt,
    TextAlign.configure({ types: ['heading', 'paragraph'] }),
    Underline,
    Placeholder.configure({
      placeholder: '开始编辑...',
    }),
    RedMark,
    GreenMark,
    PageBreak,
  ],
  onUpdate: () => {
    isDirty.value = true
  },
  editable: true,
})

// ── Computed ──
const wordCount = computed(() => {
  const text = editor.value?.getText() || ''
  return text.replace(/\s/g, '').length
})

const saveStatusText = computed(() => {
  if (saving.value) return '保存中...'
  if (isDirty.value) return '未保存'
  if (lastSaveTime.value) return '已保存'
  return ''
})

const saveStatusClass = computed(() => {
  if (isDirty.value) return 'status-unsaved'
  if (lastSaveTime.value) return 'status-saved'
  return ''
})

const lastSaveDisplay = computed(() => {
  if (lastSaveTime.value) {
    return `最后保存 ${lastSaveTime.value}`
  }
  return ''
})

// ── Methods ──
function togglePreview() {
  isPreview.value = !isPreview.value
  editor.value?.setEditable(!isPreview.value)
}

async function handleSave() {
  if (!reportId.value) return
  saving.value = true
  try {
    const html = editor.value?.getHTML() || ''
    const json = JSON.stringify(editor.value?.getJSON())
    await reportApi.save(reportId.value, {
      title: reportTitle.value,
      richContent: html,
      editorJson: json,
      changeSummary: isDirty.value ? undefined : undefined,
    })
    currentVersion.value++
    lastSaveTime.value = new Date().toLocaleString('zh-CN', { hour12: false })
    isDirty.value = false
    ElMessage.success('保存成功')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleExport() {
  if (!reportId.value) return
  if (isDirty.value) {
    await handleSave()
  }
  exporting.value = true
  try {
    await reportApi.export(reportId.value)
    ElMessage.success('导出请求已提交，完成后可下载')
  } catch (e: any) {
    ElMessage.error(e.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

function insertTable() {
  editor.value?.chain().focus().insertTable({ rows: 3, cols: 4, withHeaderRow: true }).run()
}

function handleInsertChart() {
  ElMessage.info('图表功能即将上线')
}

function addChart() {
  config.charts.push({ label: `走势图 ${config.charts.length + 1}` })
}

function removeChart(index: number) {
  config.charts.splice(index, 1)
}

// ── Lifecycle ──
onMounted(async () => {
  try {
    const res = await reportApi.get(reportId.value)
    const data = (res as any).data || {}
    reportTitle.value = data.title || ''
    currentVersion.value = data.version || 1
    lastSaveTime.value = data.updatedAt || ''

    // Set editor content
    if (data.editorJson) {
      try {
        const parsed = JSON.parse(data.editorJson)
        editor.value?.commands.setContent(parsed)
      } catch {
        // Fallback to rich content
        if (data.richContent) {
          editor.value?.commands.setContent(data.richContent)
        }
      }
    } else if (data.richContent) {
      editor.value?.commands.setContent(data.richContent)
    }

    // Restore config if available
    if (data.config) {
      Object.assign(config, data.config)
    }

    isDirty.value = false
  } catch (e: any) {
    console.error('Failed to load report:', e)
    ElMessage.error(e.message || '加载报告失败')
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  editor.value?.destroy()
})
</script>
