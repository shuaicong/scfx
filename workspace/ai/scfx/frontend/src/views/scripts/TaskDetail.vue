<template>
  <div class="task-detail" :class="{ 'fullscreen-mode': route.meta.hideSidebar }">
    <el-page-header v-if="!isCreateMode" @back="goBack" :content="script?.scriptName || '任务详情'" />
    <div v-else class="create-header">
      <h2>创建采集任务</h2>
    </div>

    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-label">执行次数</div>
            <div class="stat-value">{{ stats.executionCount }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-label">成功</div>
            <div class="stat-value success">{{ stats.successCount }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-label">失败</div>
            <div class="stat-value danger">{{ stats.failedCount }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-label">成功率</div>
            <div class="stat-value">{{ successRate }}%</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-tabs v-model="activeTab" class="detail-tabs">
      <el-tab-pane label="基本信息" name="info">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="数据源">{{ script?.source }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="script?.status === 'enabled' ? 'success' : 'info'">
              {{ script?.status === 'enabled' ? '已启用' : '已禁用' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="触发方式">{{ triggerDescription }}</el-descriptions-item>
          <el-descriptions-item label="下次执行">{{ script?.nextExecutionTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="最后执行">{{ script?.lastExecutionTime || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-card class="script-card" shadow="never">
          <template #header>
            <div class="script-header">
              <span>脚本配置</span>
              <div class="script-actions">
                <el-button size="small" @click="formatScript">格式化</el-button>
                <el-button type="primary" size="small" :disabled="!hasChanges" @click="saveScript">
                  保存脚本 Ctrl+S
                </el-button>
              </div>
            </div>
          </template>
          <ScriptEditor
            v-model="scriptContent"
            :read-only="false"
            height="400px"
            @update:modelValue="onScriptChange"
          />
          <div class="script-status">
            <span v-if="hasChanges" class="modified-indicator">已修改</span>
            <span class="cursor-position">
              行 {{ cursorLine }}, 列 {{ cursorColumn }}
            </span>
          </div>
        </el-card>

        <div class="action-buttons">
          <el-button type="primary" @click="handleExecute" :loading="executing">
            {{ executing ? '执行中...' : '立即执行' }}
          </el-button>
          <el-button @click="handleToggleStatus">
            {{ script?.status === 'enabled' ? '禁用' : '启用' }}
          </el-button>
          <el-button type="warning" @click="showEditDialog = true">编辑</el-button>
        </div>
      </el-tab-pane>

      <el-tab-pane label="执行记录" name="executions">
        <ExecutionList :script-id="scriptId" />
      </el-tab-pane>

      <el-tab-pane label="脚本版本" name="versions">
        <VersionHistory :script-id="scriptId" />
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="showExecutionDialog" title="执行中" width="600px" :close-on-click-modal="false">
      <div class="execution-progress">
        <el-icon class="is-loading" :size="32"><Loading /></el-icon>
        <p>正在执行，请稍候...</p>
        <p>执行时长: {{ executionDuration }}</p>
      </div>
      <ExecutionLogViewer v-if="currentExecutionId" :execution-id="currentExecutionId" :logs="executionLogs" />
      <template #footer>
        <el-button @click="handleCancelExecution">取消执行</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showEditDialog" title="编辑任务" width="800px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="任务名称">
          <el-input v-model="editForm.scriptName" />
        </el-form-item>
        <el-form-item label="数据源">
          <el-select v-model="editForm.source" placeholder="请选择数据源" style="width: 100%">
            <el-option label="粮信网" value="liangxin" />
            <el-option label="我的钢铁网" value="mysteel" />
            <el-option label="中华粮网" value="chinagrain" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发配置">
          <TriggerConfig v-model="editForm.triggerConfig" />
        </el-form-item>
        <el-form-item label="修改说明">
          <el-input v-model="editForm.changeDescription" type="textarea" placeholder="请填写修改说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { scriptApi, executionApi } from '@/api'
import ExecutionList from './ExecutionList.vue'
import VersionHistory from './VersionHistory.vue'
import TriggerConfig from './components/TriggerConfig.vue'
import ExecutionLogViewer from '@/components/ExecutionLogViewer.vue'
import ScriptEditor from '@/components/ScriptEditor.vue'
import type { CollectionScript, ExecutionLog } from '@/api'

interface TriggerConfig {
  triggerType: string
  repeatType?: string
  repeatTime?: string
  weeklyDays?: string
  monthlyDay?: number
  monthlyLastDay?: boolean
  cronExpression?: string
  endType?: string
  endTime?: string
  repeatCount?: number
}

const route = useRoute()
const router = useRouter()

const scriptId = computed(() => Number(route.params.id))
const isCreateMode = computed(() => route.path.endsWith('/create'))
const script = ref<CollectionScript>()
const activeTab = ref('info')
const showExecutionDialog = ref(false)
const showEditDialog = ref(false)
const executing = ref(false)
const currentExecutionId = ref('')
const executionLogs = ref<ExecutionLog[]>([])
const executionDuration = ref('00:00:00')
const scriptContent = ref('')
const originalContent = ref('')
const hasChanges = computed(() => scriptContent.value !== originalContent.value)
const cursorLine = ref(1)
const cursorColumn = ref(1)
const editorInstance = ref<any>(null)

const stats = computed(() => ({
  executionCount: script.value?.executionCount || 0,
  successCount: script.value?.successCount || 0,
  failedCount: script.value?.failedCount || 0,
}))

const successRate = computed(() => {
  const total = stats.value.executionCount
  if (total === 0) return 0
  return Math.round((stats.value.successCount / total) * 100)
})

const triggerDescription = computed(() => {
  if (!script.value) return '-'
  if (script.value.triggerType === 'repeat') {
    const repeatType = script.value.repeatType || 'daily'
    const time = script.value.repeatTime || '08:00'
    if (repeatType === 'daily') return `每日 ${time}`
    if (repeatType === 'weekly') return `每周 ${script.value.weeklyDays} ${time}`
    if (repeatType === 'monthly') return `每月 ${script.value.monthlyDay}日 ${time}`
  }
  if (script.value.triggerType === 'cron') {
    return `Cron: ${script.value.cronExpression}`
  }
  return '手动触发'
})

const editForm = ref({
  scriptName: '',
  source: '',
  triggerConfig: {
    triggerType: 'repeat'
  },
  changeDescription: ''
})

let durationTimer: number | null = null
let pollingTimer: number | null = null

onMounted(async () => {
  if (!isCreateMode.value) {
    await loadScript()
    await loadScriptContent()
  }
  document.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  if (durationTimer) clearInterval(durationTimer)
  if (pollingTimer) clearInterval(pollingTimer)
  document.removeEventListener('keydown', handleKeyDown)
})

async function loadScript() {
  if (isCreateMode.value) return
  try {
    const res = await scriptApi.getById(scriptId.value)
    script.value = (res as unknown as { data: CollectionScript }).data
  } catch (e) {
    ElMessage.error('加载任务失败')
  }
}

function goBack() {
  router.back()
}

async function handleExecute() {
  try {
    executing.value = true
    const res = await executionApi.execute(scriptId.value)
    currentExecutionId.value = res.data.executionId
    showExecutionDialog.value = true
    startPolling()
  } catch (e) {
    ElMessage.error('执行失败')
    executing.value = false
  }
}

function startPolling() {
  const startTime = Date.now()
  durationTimer = window.setInterval(() => {
    const elapsed = Math.floor((Date.now() - startTime) / 1000)
    const h = Math.floor(elapsed / 3600)
    const m = Math.floor((elapsed % 3600) / 60)
    const s = elapsed % 60
    executionDuration.value = `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
  }, 1000)

  pollingTimer = window.setInterval(async () => {
    try {
      const logsRes = await executionApi.logs(currentExecutionId.value)
      executionLogs.value = logsRes.data

      const execRes = await executionApi.get(currentExecutionId.value)
      if (execRes.data.status === 'success' || execRes.data.status === 'failed' || execRes.data.status === 'cancelled') {
        finishExecution()
      }
    } catch (e) {
      // ignore
    }
  }, 2000)
}

function finishExecution() {
  if (durationTimer) clearInterval(durationTimer)
  if (pollingTimer) clearInterval(pollingTimer)
  executing.value = false
  showExecutionDialog.value = false
  loadScript()
  ElMessage.success('执行完成')
}

async function handleCancelExecution() {
  try {
    await executionApi.cancel(currentExecutionId.value)
    finishExecution()
  } catch (e) {
    ElMessage.error('取消失败')
  }
}

async function handleToggleStatus() {
  try {
    if (script.value?.status === 'enabled') {
      await scriptApi.disable(scriptId.value)
    } else {
      await scriptApi.enable(scriptId.value)
    }
    await loadScript()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function handleSaveEdit() {
  try {
    await scriptApi.update(scriptId.value, {
      ...script.value!,
      scriptName: editForm.value.scriptName,
      ...editForm.value.triggerConfig
    } as CollectionScript)
    showEditDialog.value = false
    await loadScript()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

async function loadScriptContent() {
  try {
    const res = await scriptApi.getContent(scriptId.value)
    scriptContent.value = res.data
    originalContent.value = res.data
  } catch (e) {
    ElMessage.error('加载脚本内容失败')
  }
}

function onScriptChange(value: string) {
  scriptContent.value = value
  updateCursorPosition()
}

function updateCursorPosition() {
  const editor = editorInstance.value
  if (editor) {
    const position = editor.getPosition()
    if (position) {
      cursorLine.value = position.lineNumber
      cursorColumn.value = position.column
    }
  }
}

async function saveScript() {
  try {
    await scriptApi.updateContent(scriptId.value, scriptContent.value)
    originalContent.value = scriptContent.value
    ElMessage.success('脚本保存成功')
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

function formatScript() {
  const editor = editorInstance.value
  if (editor) {
    editor.getAction('editor.action.formatDocument')?.run()
  }
}

function handleKeyDown(e: KeyboardEvent) {
  if (e.ctrlKey && e.key === 's') {
    e.preventDefault()
    saveScript()
  }
}
</script>

<style scoped>
.task-detail {
  padding: 24px;
  background: #fff;
  min-height: 100%;
}
.task-detail.fullscreen-mode {
  padding: 0;
  background: #fff;
  min-height: 100vh;
}
.create-header {
  padding: 20px 24px;
  border-bottom: 1px solid #e8e8e8;
  background: linear-gradient(135deg, #1a1f2e 0%, #252b3d 100%);
}
.create-header h2 {
  margin: 0;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
}
.stats-row {
  margin: 20px 0;
}
.stat-item {
  text-align: center;
}
.stat-label {
  color: #999;
  font-size: 14px;
}
.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #409eff;
}
.stat-value.success { color: #67c23a; }
.stat-value.danger { color: #f56c6c; }
.action-buttons {
  margin-top: 20px;
}
.script-card {
  margin-top: 20px;
  background: #1e1e1e;
  border: 1px solid #30363d;
}
.script-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.script-actions {
  display: flex;
  gap: 8px;
}
.script-status {
  margin-top: 8px;
  font-size: 12px;
  color: #8b949e;
  display: flex;
  justify-content: space-between;
}
.modified-indicator {
  color: #f0883e;
}
.execution-progress {
  text-align: center;
  padding: 20px;
}
</style>