<template>
  <el-dialog
    :model-value="visible"
    title="版本历史"
    width="600px"
    :close-on-click-modal="false"
    append-to-body
    class="dark-dialog"
    @update:model-value="$emit('update:visible', $event)"
  >
    <div class="dialog-body">
      <!-- Loading -->
      <div v-if="loading" class="loading-state">
        <div class="loading-spinner"></div>
        <span>加载版本历史...</span>
      </div>

      <!-- Empty -->
      <div v-else-if="versions.length === 0" class="empty-state">
        <el-icon class="empty-icon" :size="40"><Clock /></el-icon>
        <p class="empty-text">暂无版本记录</p>
      </div>

      <!-- Timeline -->
      <div v-else class="timeline">
        <div
          v-for="(ver, idx) in versions"
          :key="ver.version || idx"
          class="timeline-item"
          :class="{
            current: (ver.version || 0) === currentVersion,
          }"
        >
          <div class="timeline-marker">
            <div class="marker-dot"></div>
            <div v-if="idx < versions.length - 1" class="marker-line"></div>
          </div>
          <div class="timeline-card">
            <div class="timeline-header">
              <div class="tl-left">
                <span class="tl-version">v{{ ver.version }}</span>
                <span v-if="(ver.version || 0) === currentVersion" class="tl-badge">当前</span>
              </div>
              <div class="tl-right">
                <span class="tl-time">{{ formatTime(ver.createdAt || ver.createTime) }}</span>
              </div>
            </div>
            <div class="tl-meta">
              <span class="tl-editor">{{ ver.editor || ver.createdBy || '--' }}</span>
              <span v-if="ver.changeSummary" class="tl-summary">{{ ver.changeSummary }}</span>
            </div>
            <div class="tl-actions">
              <button class="tl-action-btn" @click="handleView(ver)" title="查看此版本内容">
                查看
              </button>
              <button
                class="tl-action-btn"
                :disabled="(ver.version || 0) === currentVersion"
                @click="handleRestore(ver)"
                title="回滚到此版本"
              >
                回滚
              </button>
              <button class="tl-action-btn" @click="handleExport(ver)" title="导出此版本">
                导出此版本
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style>
@import '@/styles/dark-theme.css';
</style>

<script setup lang="ts">
import { ref, watch, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Clock } from '@element-plus/icons-vue'
import { reportApi } from '@/api/report'

const props = defineProps<{
  visible: boolean
  reportId: number
  isTemplate?: boolean
  currentVersion?: number
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'restore': [version: number]
  'view': [version: any]
}>()

// ── State ──
const loading = ref(false)
const versions = ref<any[]>([])

// ── Load Versions ──
async function loadVersions() {
  if (!props.reportId) return
  loading.value = true
  try {
    const apiMethod = props.isTemplate ? reportApi.templateVersions : reportApi.versions
    const res = await apiMethod(props.reportId)
    versions.value = ((res as any).data || []).sort((a: any, b: any) => {
      return (b.version || 0) - (a.version || 0)
    })
  } catch (e: any) {
    console.error('加载版本历史失败:', e)
    ElMessage.error(e.message || '加载版本历史失败')
    versions.value = []
  } finally {
    loading.value = false
  }
}

watch(() => props.visible, (val) => {
  if (val) {
    loadVersions()
  }
})

// ── Actions ──
function handleView(ver: any) {
  emit('view', ver)
}

async function handleRestore(ver: any) {
  try {
    await ElMessageBox.confirm(
      `确定回滚到版本 v${ver.version} 吗？当前未保存的更改将丢失。`,
      '回滚确认',
      { type: 'warning', confirmButtonText: '回滚', cancelButtonText: '取消' }
    )
    if (props.isTemplate) {
      await reportApi.restoreTemplate(props.reportId, ver.version)
    } else {
      await reportApi.restore(props.reportId, ver.version)
    }
    ElMessage.success(`已回滚到 v${ver.version}`)
    emit('restore', ver.version)
    emit('update:visible', false)
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.message || '回滚失败')
    }
  }
}

async function handleExport(ver: any) {
  try {
    if (props.isTemplate) {
      ElMessage.info('模板版本导出功能即将上线')
      return
    }
    await reportApi.export(props.reportId, ver.version)
    ElMessage.success(`导出请求已提交 (v${ver.version})`)
  } catch (e: any) {
    ElMessage.error(e.message || '导出失败')
  }
}

function handleClose() {
  emit('update:visible', false)
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
</script>

<style scoped>
.dialog-body {
  padding: 4px 0;
  min-height: 200px;
  max-height: 480px;
  overflow-y: auto;
}

/* Loading */
.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 200px;
  gap: 12px;
  color: var(--text-muted);
  font-size: 14px;
}

.loading-spinner {
  width: 28px;
  height: 28px;
  border: 3px solid var(--border-color);
  border-top-color: var(--accent-blue);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* Empty */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px;
  color: var(--text-muted);
}

.empty-icon {
  margin-bottom: 8px;
  opacity: 0.4;
}

.empty-text {
  font-size: 14px;
  color: var(--text-secondary);
}

/* ── Timeline ── */
.timeline {
  display: flex;
  flex-direction: column;
  padding-left: 8px;
}

.timeline-item {
  display: flex;
  gap: 16px;
  position: relative;
}

/* Marker */
.timeline-marker {
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 16px;
  flex-shrink: 0;
  padding-top: 6px;
}

.marker-dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--border-color);
  border: 2px solid var(--bg-secondary);
  flex-shrink: 0;
  z-index: 1;
}

.timeline-item.current .marker-dot {
  background: var(--accent-blue);
  box-shadow: 0 0 6px rgba(88, 166, 255, 0.4);
}

.marker-line {
  width: 2px;
  flex: 1;
  background: var(--border-color);
  margin-top: 2px;
}

/* Card */
.timeline-card {
  flex: 1;
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 12px;
  transition: border-color 0.15s ease;
}

.timeline-item.current .timeline-card {
  border-color: rgba(88, 166, 255, 0.3);
  background: rgba(88, 166, 255, 0.05);
}

.timeline-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.tl-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tl-version {
  font-size: 14px;
  font-weight: 700;
  color: var(--text-primary);
  font-family: 'JetBrains Mono', monospace;
}

.tl-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  background: rgba(88, 166, 255, 0.2);
  color: var(--accent-blue);
  font-weight: 600;
}

.tl-right {
  display: flex;
  align-items: center;
}

.tl-time {
  font-size: 11px;
  color: var(--text-muted);
  font-family: 'JetBrains Mono', monospace;
}

.tl-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-bottom: 8px;
}

.tl-editor {
  font-size: 12px;
  color: var(--text-secondary);
}

.tl-summary {
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.5;
}

.tl-actions {
  display: flex;
  gap: 6px;
  padding-top: 6px;
  border-top: 1px solid var(--border-color);
}

.tl-action-btn {
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  border: 1px solid var(--border-color);
  background: transparent;
  color: var(--text-secondary);
  transition: all 0.12s ease;
}

.tl-action-btn:hover:not(:disabled) {
  background: var(--bg-hover);
  color: var(--text-primary);
  border-color: var(--accent-blue);
}

.tl-action-btn:disabled {
  opacity: 0.4;
  cursor: default;
}

/* ── Dark Dialog ── */
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
</style>
