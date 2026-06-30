<template>
  <div class="editor-container">
    <!-- Header -->
    <div class="editor-header">
      <button class="back-btn" @click="goBack" title="返回模板列表">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <input
        v-model="templateName"
        class="title-input"
        placeholder="输入模板名称..."
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
        <el-dropdown trigger="click" @command="handlePlaceholderInsert">
          <button
            class="tb-btn"
            style="color: var(--accent);"
            :disabled="isPreview"
          >
            插入占位符 ▾
          </button>
          <template #dropdown>
            <el-dropdown-menu class="dark-dropdown">
              <el-dropdown-item command="PRICE_TABLE">
                <span class="ph-item">{{ ph('PRICE_TABLE') }}</span>
                <span class="ph-desc">价格数据表格</span>
              </el-dropdown-item>
              <el-dropdown-item command="PRICE_CHART">
                <span class="ph-item">{{ ph('PRICE_CHART') }}</span>
                <span class="ph-desc">价格走势图</span>
              </el-dropdown-item>
              <el-dropdown-item command="PRICE_COMPARE">
                <span class="ph-item">{{ ph('PRICE_COMPARE') }}</span>
                <span class="ph-desc">港口价格对比</span>
              </el-dropdown-item>
              <el-dropdown-item command="REGION_PRICE">
                <span class="ph-item">{{ ph('REGION_PRICE') }}</span>
                <span class="ph-desc">产区价格</span>
              </el-dropdown-item>
              <el-dropdown-item command="PROCESS_PRICE">
                <span class="ph-item">{{ ph('PROCESS_PRICE') }}</span>
                <span class="ph-desc">深加工收购价</span>
              </el-dropdown-item>
              <el-dropdown-item command="WEATHER">
                <span class="ph-item">{{ ph('WEATHER') }}</span>
                <span class="ph-desc">天气概况</span>
              </el-dropdown-item>
              <el-dropdown-item command="KNOWLEDGE">
                <span class="ph-item">{{ ph('KNOWLEDGE') }}</span>
                <span class="ph-desc">知识库检索结果</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
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
        <div class="panel-title">模板配置</div>

        <div class="panel-section">
          <div class="panel-section-title">基本信息</div>
          <div class="field">
            <label>关联品种</label>
            <select v-model="config.variety" :disabled="isPreview">
              <option value="corn">玉米</option>
              <option value="rice">稻米</option>
              <option value="wheat">小麦</option>
            </select>
          </div>
          <div class="field">
            <label>描述</label>
            <el-input
              v-model="config.description"
              type="textarea"
              :rows="2"
              placeholder="模板描述"
              :disabled="isPreview"
              maxlength="200"
              class="compact-textarea"
            />
          </div>
        </div>

        <div class="panel-section">
          <div class="panel-section-title">生成配置 (JSON)</div>
          <div class="field">
            <label>generation_config</label>
            <textarea
              v-model="configJsonText"
              class="json-editor"
              :disabled="isPreview"
              spellcheck="false"
              rows="12"
            ></textarea>
            <div v-if="jsonParseError" class="json-error">{{ jsonParseError }}</div>
          </div>
        </div>

        <div class="panel-section">
          <div class="panel-section-title">版本信息</div>
          <div class="version-info">
            <div class="version-row">
              <span class="version-label">当前版本</span>
              <span class="version-value">v{{ currentVersion }}</span>
            </div>
            <div class="version-row">
              <span class="version-label">占位符</span>
              <span class="version-value">{{ placeholderCount }} 个</span>
            </div>
            <div class="version-row">
              <span class="version-label">最近保存</span>
              <span class="version-value time">{{ lastSaveDisplay }}</span>
            </div>
          </div>
          <button class="btn btn-sm" :disabled="isPreview" @click="showVersionHistory = true">
            版本历史
          </button>
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
        <span class="status-placeholder">{{ placeholderCount }} 个占位符</span>
        <span class="status-sep">·</span>
        <span :class="saveStatusClass">{{ saveStatusText }}</span>
      </span>
      <span class="status-right">
        <span class="status-time">{{ lastSaveTimeDisplay }}</span>
      </span>
    </div>

    <!-- Version History Dialog -->
    <VersionHistoryDialog
      v-if="showVersionHistory"
      :report-id="templateId"
      :is-template="true"
      :current-version="currentVersion"
      @close="showVersionHistory = false"
      @restore="handleRestoreVersion"
    />
  </div>
</template>

<style>
@import '@/styles/dark-theme.css';
</style>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
const ph = (name: string) => `{{${name}}}`
import { useEditor, EditorContent } from '@tiptap/vue-3'
import { Mark, Node } from '@tiptap/core'
import { StarterKit } from '@tiptap/starter-kit'
import { Table } from '@tiptap/extension-table'
import { TableRow } from '@tiptap/extension-table-row'
import { TableCell } from '@tiptap/extension-table-cell'
import { TableHeader } from '@tiptap/extension-table-header'
import ImageExt from '@tiptap/extension-image'
import { TextAlign } from '@tiptap/extension-text-align'
import { Underline } from '@tiptap/extension-underline'
import { Placeholder } from '@tiptap/extension-placeholder'
import { reportApi } from '@/api/report'
import VersionHistoryDialog from './components/VersionHistoryDialog.vue'

// ── Route ──
const route = useRoute()
const router = useRouter()
const templateId = computed(() => Number(route.params.id))

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

// ── Placeholder Types ──
const PLACEHOLDER_TYPES: Record<string, { label: string; params?: string }> = {
  PRICE_TABLE: { label: '价格数据表格' },
  PRICE_CHART: { label: '价格走势图' },
  PRICE_COMPARE: { label: '港口价格对比' },
  REGION_PRICE: { label: '产区价格' },
  PROCESS_PRICE: { label: '深加工收购价' },
  WEATHER: { label: '天气概况' },
  KNOWLEDGE: { label: '知识库检索结果' },
}

const PLACEHOLDER_PATTERN = /\{\{([A-Z_]+)(?::([^}]+))?\}\}/g

// ── State ──
const loading = ref(true)
const saving = ref(false)
const isPreview = ref(false)
const templateName = ref('')
const currentVersion = ref(1)
const lastSaveTime = ref('')
const isDirty = ref(false)
const showVersionHistory = ref(false)

const config = reactive({
  variety: 'corn',
  description: '',
})

const configJsonText = ref('{\n  "dateRange": 30,\n  "includePortPrice": true,\n  "includeRegionPrice": true,\n  "includeProcessPrice": false,\n  "knowledgeCategories": []\n}')

const jsonParseError = ref('')

// ── Editor ──
const editor = useEditor({
  content: '',
  extensions: [
    StarterKit.configure({
      heading: { levels: [1, 2, 3] },
      underline: false,
    }),
    Table.configure({ resizable: true }),
    TableRow,
    TableCell,
    TableHeader,
    ImageExt,
    TextAlign.configure({ types: ['heading', 'paragraph'] }),
    Underline,
    Placeholder.configure({
      placeholder: '编辑模板内容，插入占位符（如 {{PRICE_TABLE}}）...',
    }),
    RedMark,
    GreenMark,
    PageBreak,
  ],
  onUpdate: () => {
    isDirty.value = true
    countPlaceholders()
  },
  editable: true,
})

// ── Computed ──
const wordCount = computed(() => {
  const text = editor.value?.getText() || ''
  return text.replace(/\s/g, '').length
})

const placeholderCount = ref(0)

function countPlaceholders() {
  const text = editor.value?.getText() || ''
  const matches = text.match(PLACEHOLDER_PATTERN)
  placeholderCount.value = matches ? matches.length : 0
}

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

const lastSaveTimeDisplay = computed(() => {
  if (lastSaveTime.value) {
    return `最后保存 ${lastSaveTime.value}`
  }
  return ''
})

const lastSaveDisplay = computed(() => {
  if (lastSaveTime.value) return lastSaveTime.value
  return '--'
})

// ── Methods ──
function goBack() {
  router.push('/reports/templates')
}

function togglePreview() {
  isPreview.value = !isPreview.value
  editor.value?.setEditable(!isPreview.value)
}

function insertTable() {
  editor.value?.chain().focus().insertTable({ rows: 3, cols: 4, withHeaderRow: true }).run()
}

function handlePlaceholderInsert(type: string) {
  const ph = PLACEHOLDER_TYPES[type]
  if (!ph) return
  const text = `{{${type}}}`
  editor.value?.chain().focus().insertContent(text).run()
  ElMessage.info(`已插入占位符 ${text}`)
}

async function handleSave() {
  if (!templateId.value) return

  // Validate JSON config
  try {
    JSON.parse(configJsonText.value)
    jsonParseError.value = ''
  } catch (e: any) {
    jsonParseError.value = 'JSON 格式错误：' + e.message
    ElMessage.warning('生成配置 JSON 格式有误，请修正后再保存')
    return
  }

  saving.value = true
  try {
    const html = editor.value?.getHTML() || ''
    const json = JSON.stringify(editor.value?.getJSON())

    await reportApi.saveTemplateVersion(templateId.value, {
      title: templateName.value,
      richContent: html,
      editorJson: json,
      config: JSON.parse(configJsonText.value),
      description: config.description,
      variety: config.variety,
      changeSummary: `版本 v${currentVersion.value + 1}`,
    })
    currentVersion.value++
    lastSaveTime.value = new Date().toLocaleString('zh-CN', { hour12: false })
    isDirty.value = false
    ElMessage.success('模板保存成功')
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleRestoreVersion(version: number) {
  try {
    await reportApi.restoreTemplate(templateId.value, version)
    ElMessage.success(`已回滚到 v${version}`)
    await loadTemplate()
  } catch (e: any) {
    ElMessage.error(e.message || '回滚失败')
  }
}

async function loadTemplate() {
  loading.value = true
  try {
    const res = await reportApi.getTemplate(templateId.value)
    const data = (res as any).data || {}
    templateName.value = data.name || data.title || ''
    currentVersion.value = data.version || 1
    lastSaveTime.value = data.updatedAt || ''

    // Set editor content
    if (data.editorJson) {
      try {
        const parsed = JSON.parse(data.editorJson)
        editor.value?.commands.setContent(parsed)
      } catch {
        if (data.richContent) {
          editor.value?.commands.setContent(data.richContent)
        }
      }
    } else if (data.richContent) {
      editor.value?.commands.setContent(data.richContent)
    }

    // Restore config
    if (data.variety) config.variety = data.variety
    if (data.description) config.description = data.description
    if (data.config) {
      configJsonText.value = JSON.stringify(data.config, null, 2)
    }

    countPlaceholders()
    isDirty.value = false
  } catch (e: any) {
    console.error('Failed to load template:', e)
    ElMessage.error(e.message || '加载模板失败')
  } finally {
    loading.value = false
  }
}

// ── Lifecycle ──
onMounted(loadTemplate)

onBeforeUnmount(() => {
  editor.value?.destroy()
})
</script>

<style scoped>
/* ── Layout ── */
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
  gap: 12px;
  padding: 10px 20px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  flex-shrink: 0;
}

.back-btn {
  background: transparent;
  border: none;
  color: var(--text-secondary);
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  transition: all 0.12s ease;
}

.back-btn:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
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
  width: 300px;
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

/* Compact textarea for element-plus */
.compact-textarea {
  width: 100%;
}

.compact-textarea :deep(.el-textarea__inner) {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-color) !important;
  color: var(--text-primary) !important;
  font-size: 12px !important;
  border-radius: 4px !important;
  resize: vertical !important;
}

/* JSON Editor */
.json-editor {
  width: 100%;
  padding: 8px;
  border-radius: 4px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-primary);
  font-size: 11px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  line-height: 1.6;
  outline: none;
  resize: vertical;
  box-sizing: border-box;
}

.json-editor:focus {
  border-color: var(--accent-blue);
}

.json-editor:disabled {
  opacity: 0.6;
}

.json-error {
  font-size: 11px;
  color: var(--accent-red);
  margin-top: 4px;
}

/* Version Info */
.version-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 10px;
}

.version-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
}

.version-label {
  color: var(--text-muted);
}

.version-value {
  color: var(--text-primary);
  font-weight: 500;
}

.version-value.time {
  font-size: 11px;
  color: var(--text-secondary);
  font-family: 'JetBrains Mono', monospace;
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
  width: 100%;
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

/* Status Bar */
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

.status-placeholder {
  color: var(--accent);
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

/* Button Presets */
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

/* Dark Dropdown */
:global(.dark-dropdown) {
  background: var(--bg-secondary) !important;
  border: 1px solid var(--border-color) !important;
}

:global(.dark-dropdown .el-dropdown-menu__item) {
  color: var(--text-secondary) !important;
  font-size: 12px !important;
  display: flex !important;
  align-items: center !important;
  gap: 8px !important;
  padding: 8px 14px !important;
}

:global(.dark-dropdown .el-dropdown-menu__item:hover) {
  color: var(--text-primary) !important;
  background: var(--bg-hover) !important;
}

.ph-item {
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 11px;
  color: var(--accent);
  min-width: 120px;
}

.ph-desc {
  font-size: 11px;
  color: var(--text-muted);
}
</style>
