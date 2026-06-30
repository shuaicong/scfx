<template>
  <div class="template-list-container">
    <!-- Header -->
    <div class="header">
      <div class="header-title">
        <span>模板管理</span>
        <span class="header-subtitle">管理报告模板和占位符配置</span>
      </div>
      <div class="header-actions">
        <el-button class="btn-secondary" @click="loadTemplates()">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
        <el-button class="btn-primary" @click="showCreateDialog">
          <el-icon><Plus /></el-icon>
          新建模板
        </el-button>
      </div>
    </div>

    <!-- Page Container -->
    <div class="page-content">
      <!-- Loading State -->
      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <span>加载模板列表...</span>
      </div>

      <!-- Template Grid -->
      <div v-else-if="templates.length > 0" class="template-grid">
        <div
          v-for="tpl in templates"
          :key="tpl.id"
          class="template-card"
          @click="goToEditor(tpl)"
        >
          <div class="card-icon">
            {{ getVarietyEmoji(tpl.variety) }}
          </div>
          <div class="card-body">
            <h3 class="card-title">{{ tpl.name }}</h3>
            <p class="card-desc">{{ tpl.description || '暂无描述' }}</p>
            <div class="card-meta">
              <span class="meta-item">
                <span class="meta-label">版本</span>
                <span class="meta-value">v{{ tpl.version || 1 }}</span>
              </span>
              <span class="meta-divider"></span>
              <span class="meta-item">
                <span class="meta-label">占位符</span>
                <span class="meta-value">{{ tpl.placeholderCount || 0 }}</span>
              </span>
              <span class="meta-divider"></span>
              <span class="meta-item">
                <span class="meta-label">品种</span>
                <span class="meta-value">{{ getVarietyLabel(tpl.variety) }}</span>
              </span>
            </div>
            <div class="card-footer">
              <span class="card-time">{{ formatTime(tpl.updatedAt || tpl.createdAt) }}</span>
              <span class="card-arrow">→</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Empty State -->
      <div v-else class="empty-state">
        <el-icon class="empty-icon" :size="48"><Folder /></el-icon>
        <p class="empty-text">暂无模板数据</p>
        <p class="empty-hint">点击「新建模板」创建第一个报告模板</p>
      </div>
    </div>

    <!-- 新建模板 Dialog -->
    <el-dialog
      v-model="createDialog.visible"
      title="新建模板"
      width="460px"
      :close-on-click-modal="false"
      append-to-body
      class="dark-dialog"
    >
      <div class="dialog-body">
        <div class="dialog-field">
          <label>模板名称 <span class="field-required">*</span></label>
          <el-input v-model="createDialog.name" placeholder="输入模板名称" maxlength="100" />
        </div>
        <div class="dialog-field">
          <label>描述</label>
          <el-input
            v-model="createDialog.description"
            placeholder="简要描述模板用途"
            maxlength="200"
            type="textarea"
            :rows="3"
          />
        </div>
        <div class="dialog-field">
          <label>关联品种 <span class="field-required">*</span></label>
          <el-select v-model="createDialog.variety" placeholder="选择品种" class="dialog-select">
            <el-option label="🌽 玉米" value="corn" />
            <el-option label="🌾 稻米" value="rice" />
            <el-option label="🌾 小麦" value="wheat" />
          </el-select>
        </div>
      </div>
      <template #footer>
        <el-button @click="createDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="confirmCreate" :loading="creating">创建模板</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style>
@import '@/styles/dark-theme.css';
</style>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh, Plus, Folder } from '@element-plus/icons-vue'
import { reportApi } from '@/api/report'

const router = useRouter()
const loading = ref(true)
const creating = ref(false)
const templates = ref<any[]>([])

// ── Load ──
async function loadTemplates() {
  loading.value = true
  try {
    const res = await reportApi.templateList()
    templates.value = (res as any).data || []
  } catch (e: any) {
    console.error('加载模板列表失败:', e)
    ElMessage.error(e.message || '加载模板列表失败')
    templates.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadTemplates)

// ── Navigation ──
function goToEditor(tpl: any) {
  router.push(`/reports/templates/editor/${tpl.id}`)
}

// ── Display Helpers ──
function getVarietyEmoji(variety: string): string {
  if (variety === 'corn') return '🌽'
  if (variety === 'rice') return '🌾'
  if (variety === 'wheat') return '🌾'
  return '📄'
}

function getVarietyLabel(variety: string): string {
  if (variety === 'corn') return '玉米'
  if (variety === 'rice') return '稻米'
  if (variety === 'wheat') return '小麦'
  return variety || '--'
}

function formatTime(time?: string): string {
  if (!time) return '--'
  try {
    const d = new Date(time)
    const pad = (n: number) => n.toString().padStart(2, '0')
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
  } catch {
    return time.substring(0, 16).replace('T', ' ')
  }
}

// ── Create Dialog ──
const createDialog = reactive({
  visible: false,
  name: '',
  description: '',
  variety: 'corn' as string,
})

function showCreateDialog() {
  createDialog.visible = true
  createDialog.name = ''
  createDialog.description = ''
  createDialog.variety = 'corn'
}

async function confirmCreate() {
  if (!createDialog.name.trim()) {
    ElMessage.warning('请输入模板名称')
    return
  }

  creating.value = true
  try {
    const res = await reportApi.createTemplate({
      name: createDialog.name.trim(),
      description: createDialog.description.trim(),
      variety: createDialog.variety,
    })
    const created = (res as any).data || {}
    createDialog.visible = false
    ElMessage.success('模板创建成功')
    // Navigate to editor
    if (created.id) {
      router.push(`/reports/templates/editor/${created.id}`)
    } else {
      loadTemplates()
    }
  } catch (e: any) {
    ElMessage.error(e.message || '创建模板失败')
  } finally {
    creating.value = false
  }
}
</script>

<style scoped>
.template-list-container {
  min-height: 100vh;
  background: var(--bg-primary);
  color: var(--text-primary);
}

/* Header */
.header {
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
  padding: 14px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-title {
  font-size: 18px;
  font-weight: 600;
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.header-subtitle {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 400;
}

.header-actions {
  margin-left: auto;
  display: flex;
  gap: 8px;
}

/* Page Content */
.page-content {
  padding: 24px;
  min-height: calc(100vh - 80px - 48px);
}

/* Loading */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 300px;
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

/* Template Grid */
.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

/* Template Card */
.template-card {
  display: flex;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s ease;
}

.template-card:hover {
  border-color: var(--accent-blue);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.3);
}

.card-icon {
  width: 72px;
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  background: var(--bg-tertiary);
  flex-shrink: 0;
}

.card-body {
  flex: 1;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-desc {
  font-size: 12px;
  color: var(--text-secondary);
  margin: 0;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 0;
  padding-top: 4px;
}

.meta-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.meta-label {
  font-size: 10px;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.meta-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.meta-divider {
  width: 1px;
  height: 28px;
  background: var(--border-color);
  margin: 0 16px;
  flex-shrink: 0;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 6px;
}

.card-time {
  font-size: 11px;
  color: var(--text-muted);
  font-family: 'JetBrains Mono', monospace;
}

.card-arrow {
  font-size: 16px;
  color: var(--text-muted);
  transition: transform 0.2s ease;
}

.template-card:hover .card-arrow {
  transform: translateX(4px);
  color: var(--accent-blue);
}

/* Empty State */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 80px 20px;
  color: var(--text-muted);
}

.empty-icon {
  margin-bottom: 12px;
  opacity: 0.4;
}

.empty-text {
  font-size: 15px;
  color: var(--text-secondary);
  margin-bottom: 6px;
}

.empty-hint {
  font-size: 12px;
  color: var(--text-muted);
}

/* Buttons */
.btn-primary {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: 6px;
  border: none;
  background: linear-gradient(135deg, var(--accent-blue), var(--accent-purple));
  color: #fff;
}

.btn-primary:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(88, 166, 255, 0.3);
}

.btn-secondary {
  padding: 8px 16px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  align-items: center;
  gap: 6px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  color: var(--text-primary);
}

.btn-secondary:hover {
  background: var(--bg-hover);
  border-color: var(--accent-blue);
}

/* Dialog */
.dialog-body {
  padding: 4px 0;
}

.dialog-field {
  margin-bottom: 16px;
}

.dialog-field label {
  display: block;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
  font-weight: 500;
}

.field-required {
  color: var(--accent-red);
}

.dialog-select {
  width: 100%;
}

/* Dark Dialog Overrides */
:global(.dark-dialog .el-dialog) {
  --el-dialog-bg-color: var(--bg-secondary);
  --el-dialog-title-font-size: 16px;
  --el-dialog-padding-primary: 20px;
}

:global(.dark-dialog .el-dialog__header) {
  border-bottom: 1px solid var(--border-color);
  padding: 16px 20px;
  margin: 0;
}

:global(.dark-dialog .el-dialog__title) {
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 600;
}

:global(.dark-dialog .el-dialog__body) {
  padding: 20px;
}

:global(.dark-dialog .el-dialog__footer) {
  border-top: 1px solid var(--border-color);
  padding: 12px 20px;
}

:global(.dark-dialog .el-dialog__close) {
  color: var(--text-muted);
}

:global(.dark-dialog .el-dialog__close:hover) {
  color: var(--text-primary);
}

:global(.dark-dialog .el-input__wrapper) {
  background: var(--bg-card) !important;
  box-shadow: 0 0 0 1px var(--border-color) inset !important;
}

:global(.dark-dialog .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--accent-blue) inset !important;
}

:global(.dark-dialog .el-input__inner) {
  color: var(--text-primary) !important;
}

:global(.dark-dialog .el-textarea__inner) {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-color) !important;
  color: var(--text-primary) !important;
}
</style>
