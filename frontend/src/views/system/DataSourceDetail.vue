<template>
  <div class="datasource-detail">
    <!-- Page Header -->
    <div class="page-header">
      <el-button @click="goBack">
        <el-icon><Back /></el-icon>
        返回
      </el-button>
      <h2 class="page-title">{{ dataSource?.name || '数据源详情' }}</h2>
    </div>

    <!-- Loading State -->
    <div v-if="loading" class="loading-state">
      <el-icon class="is-loading" :size="32" />
      <span>加载中...</span>
    </div>

    <!-- Content -->
    <div v-else-if="dataSource" class="detail-content">
      <!-- Tabs -->
      <el-tabs v-model="activeTab" class="detail-tabs">
        <!-- Basic Info Tab -->
        <el-tab-pane label="基本信息" name="info">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="标识">{{ dataSource.code }}</el-descriptions-item>
            <el-descriptions-item label="名称">{{ dataSource.name }}</el-descriptions-item>
            <el-descriptions-item label="描述" :span="2">{{ dataSource.description || '-' }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="dataSource.enabled === 1 ? 'success' : 'danger'" size="small">
                {{ dataSource.enabled === 1 ? '启用' : '禁用' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="脚本状态">
              <el-tag v-if="hasScript" type="success" size="small">已上传</el-tag>
              <el-tag v-else type="info" size="small">无</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ formatDate(dataSource.createdAt) }}</el-descriptions-item>
            <el-descriptions-item label="最后更新">{{ formatDate(dataSource.updatedAt) }}</el-descriptions-item>
          </el-descriptions>

          <div class="action-buttons">
            <el-button type="primary" @click="openEditDialog">编辑</el-button>
            <el-button v-if="hasScript" type="info" @click="viewScript">查看脚本</el-button>
            <el-button @click="toggleEnabled">
              {{ dataSource.enabled === 1 ? '禁用' : '启用' }}
            </el-button>
          </div>
        </el-tab-pane>

        <!-- Version History Tab -->
        <el-tab-pane label="版本历史" name="versions">
          <div class="version-header">
            <span class="version-count">共 {{ versions.length }} 个版本</span>
          </div>
          <el-table :data="versions" stripe>
            <el-table-column prop="version" label="版本号" width="100" />
            <el-table-column prop="fileSize" label="文件大小" width="120">
              <template #default="{ row }">
                {{ formatFileSize(row.fileSize) }}
              </template>
            </el-table-column>
            <el-table-column prop="fileMd5" label="MD5" width="180" show-overflow-tooltip />
            <el-table-column prop="createdBy" label="操作人" width="120" />
            <el-table-column prop="createdAt" label="创建时间" width="160">
              <template #default="{ row }">
                {{ formatDate(row.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column prop="isCurrent" label="状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.isCurrent === 1" type="success" size="small">当前</el-tag>
                <el-tag v-else type="info" size="small">历史</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="viewVersionScript(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Linked Tasks Tab -->
        <el-tab-pane label="关联任务" name="tasks">
          <div class="tasks-header">
            <div class="tasks-stats">
              <span class="stat-item">任务总数: <strong>{{ linkedTasks.length }}</strong></span>
              <span class="stat-item">总执行次数: <strong>{{ totalExecutions }}</strong></span>
              <span class="stat-item">累计采集: <strong>{{ totalCollected }}</strong></span>
            </div>
            <div class="tasks-sort">
              <span>排序:</span>
              <el-select v-model="sortBy" style="width: 150px;">
                <el-option label="最近执行" value="lastExecuted" />
                <el-option label="状态" value="status" />
                <el-option label="累计采集" value="totalCollected" />
              </el-select>
            </div>
          </div>

          <el-table :data="sortedTasks" stripe v-loading="tasksLoading">
            <el-table-column prop="scriptName" label="任务名称" min-width="150" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'enabled' ? 'success' : 'info'" size="small">
                  {{ row.status === 'enabled' ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastExecutionTime" label="最后执行" width="160">
              <template #default="{ row }">
                {{ formatDateTime(row.lastExecutionTime) }}
              </template>
            </el-table-column>
            <el-table-column prop="executionCount" label="总执行次数" width="120" align="right" />
            <el-table-column prop="successCount" label="总采集数" width="120" align="right">
              <template #default="{ row }">
                <span class="success-count">{{ row.successCount || 0 }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="viewTask(row)">查看任务</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="!tasksLoading && linkedTasks.length === 0" class="empty-state">
            <el-icon :size="48"><Document /></el-icon>
            <p>暂无关联任务</p>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- Error State -->
    <div v-else class="error-state">
      <el-icon :size="48"><Warning /></el-icon>
      <p>数据源不存在</p>
      <el-button @click="goBack">返回列表</el-button>
    </div>

    <!-- Edit Dialog -->
    <el-dialog v-model="editDialogVisible" title="编辑数据源" width="500px">
      <el-form :model="editForm" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="editForm.name" placeholder="显示名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="数据源描述" />
        </el-form-item>
        <el-form-item label="配置参数">
          <div class="config-editor">
            <div v-for="(item, idx) in editForm.configItems" :key="idx" class="config-row">
              <el-input v-model="item.key" placeholder="参数名" class="config-key" />
              <el-input v-model="item.value" :type="isSecretKey(item.key) ? 'password' : 'text'"
                placeholder="参数值" class="config-value" show-password />
              <el-button @click="removeConfigItem(idx)" type="danger" :icon="Delete" circle size="small" />
            </div>
            <el-button @click="addConfigItem" type="primary" plain size="small">
              + 添加参数
            </el-button>
            <div class="config-tip">示例：采集器需要 username/password 时添加对应参数</div>
          </div>
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="editForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveEdit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <!-- View Script Dialog -->
    <el-dialog v-model="scriptDialogVisible" title="查看脚本" width="700px">
      <div class="script-info" v-if="currentVersion">
        <span>版本: {{ currentVersion.version }} | 大小: {{ formatFileSize(currentVersion.fileSize) }}</span>
      </div>
      <el-input
        v-model="scriptContent"
        type="textarea"
        readonly
        :rows="20"
        placeholder="脚本内容..."
        style="margin-top: 10px;"
      />
      <template #footer>
        <el-button @click="scriptDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Back, Delete, Document, Warning } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { datasourceApi, type DataSource, type ScriptVersion } from '@/api/datasource'
import { scriptApi, type CollectionScript } from '@/api'

const route = useRoute()
const router = useRouter()

// Route params
const datasourceCode = computed(() => route.params.code as string)

// State
const loading = ref(false)
const tasksLoading = ref(false)
const dataSource = ref<DataSource | null>(null)
const hasScript = ref(false)
const versions = ref<ScriptVersion[]>([])
const linkedTasks = ref<CollectionScript[]>([])
const activeTab = ref('info')
const sortBy = ref('lastExecuted')

// Edit dialog
const editDialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editForm = reactive({
  name: '',
  description: '',
  configItems: [] as { key: string; value: string }[],
  enabled: true
})

// Script dialog
const scriptDialogVisible = ref(false)
const currentVersion = ref<ScriptVersion | null>(null)
const scriptContent = ref('')

// Computed
const totalExecutions = computed(() => {
  return linkedTasks.value.reduce((sum, task) => sum + (task.executionCount || 0), 0)
})

const totalCollected = computed(() => {
  return linkedTasks.value.reduce((sum, task) => sum + (task.successCount || 0), 0)
})

const sortedTasks = computed(() => {
  const tasks = [...linkedTasks.value]
  if (sortBy.value === 'lastExecuted') {
    return tasks.sort((a, b) => {
      const aTime = a.lastExecutionTime || ''
      const bTime = b.lastExecutionTime || ''
      return bTime.localeCompare(aTime)
    })
  } else if (sortBy.value === 'status') {
    return tasks.sort((a, b) => {
      if (a.status === 'enabled' && b.status !== 'enabled') return -1
      if (a.status !== 'enabled' && b.status === 'enabled') return 1
      return 0
    })
  } else if (sortBy.value === 'totalCollected') {
    return tasks.sort((a, b) => (b.successCount || 0) - (a.successCount || 0))
  }
  return tasks
})

// Form validation rules
const formRules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

// Methods
function goBack() {
  router.push('/system/datasource')
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return '-'
  return dateStr.substring(0, 16).replace('T', ' ')
}

function formatDateTime(dateStr?: string): string {
  if (!dateStr) return '-'
  return formatDate(dateStr)
}

function formatFileSize(size: number): string {
  if (!size) return '-'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  return (size / (1024 * 1024)).toFixed(1) + ' MB'
}

async function loadDataSource() {
  loading.value = true
  try {
    const res = await datasourceApi.getByCode(datasourceCode.value)
    dataSource.value = res.data
    await checkScriptExists()
  } catch (e) {
    console.error('加载数据源失败', e)
    ElMessage.error('加载数据源失败')
  } finally {
    loading.value = false
  }
}

async function checkScriptExists() {
  try {
    const res = await datasourceApi.checkScriptExists(datasourceCode.value)
    hasScript.value = res.data?.exists || false
  } catch {
    hasScript.value = false
  }
}

async function loadVersions() {
  try {
    const res = await datasourceApi.getVersions(datasourceCode.value)
    versions.value = res.data || []
  } catch (e) {
    console.error('加载版本失败', e)
  }
}

async function loadLinkedTasks() {
  tasksLoading.value = true
  try {
    // Load all scripts filtered by this datasource (source)
    const res: any = await scriptApi.list({
      page: 1,
      size: 100,
      source: datasourceCode.value
    })
    linkedTasks.value = res.data?.records || []
  } catch (e) {
    console.error('加载关联任务失败', e)
  } finally {
    tasksLoading.value = false
  }
}

function openEditDialog() {
  if (!dataSource.value) return
  editForm.name = dataSource.value.name
  editForm.description = dataSource.value.description || ''
  // 解析 config JSON 为键值对列表
  let config: Record<string, any> = {}
  if (dataSource.value.config) {
    if (typeof dataSource.value.config === 'string') {
      try { config = JSON.parse(dataSource.value.config) } catch {}
    } else {
      config = dataSource.value.config as Record<string, any>
    }
  }
  editForm.configItems = Object.entries(config).map(([key, value]) => ({
    key,
    value: String(value ?? '')
  }))
  if (editForm.configItems.length === 0) {
    editForm.configItems = [{ key: '', value: '' }]
  }
  editForm.enabled = dataSource.value.enabled === 1
  editDialogVisible.value = true
}

function isSecretKey(key: string): boolean {
  return /password|secret|token|key/i.test(key)
}

function addConfigItem() {
  editForm.configItems.push({ key: '', value: '' })
}

function removeConfigItem(idx: number) {
  editForm.configItems.splice(idx, 1)
}

async function handleSaveEdit() {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      // 构建 config JSON，只保留非空键值对
      const configObj: Record<string, string> = {}
      for (const item of editForm.configItems) {
        if (item.key.trim()) {
          configObj[item.key.trim()] = item.value
        }
      }

      const data: Partial<DataSource & { config: any }> = {
        name: editForm.name,
        description: editForm.description,
        config: Object.keys(configObj).length > 0 ? JSON.stringify(configObj) : null,
        enabled: editForm.enabled ? 1 : 0
      }

      await datasourceApi.update(datasourceCode.value, data)
      ElMessage.success('更新成功')
      editDialogVisible.value = false
      await loadDataSource()
    } catch (e: any) {
      console.error('保存失败', e)
      ElMessage.error(e.message || '保存失败')
    } finally {
      submitting.value = false
    }
  })
}

async function toggleEnabled() {
  if (!dataSource.value) return

  try {
    if (dataSource.value.enabled === 1) {
      await datasourceApi.disable(datasourceCode.value)
      ElMessage.success('已禁用')
    } else {
      await datasourceApi.enable(datasourceCode.value)
      ElMessage.success('已启用')
    }
    await loadDataSource()
  } catch (e) {
    console.error('操作失败', e)
    ElMessage.error('操作失败')
  }
}

async function viewScript() {
  scriptContent.value = '加载中...'
  currentVersion.value = null
  scriptDialogVisible.value = true

  try {
    // Get current version first
    const versionRes = await datasourceApi.getVersions(datasourceCode.value)
    const versions = versionRes.data || []
    const current = versions.find((v: ScriptVersion) => v.isCurrent === 1)
    currentVersion.value = current || versions[0] || null

    const contentRes = await datasourceApi.getScriptContent(datasourceCode.value)
    scriptContent.value = contentRes.data || '无脚本内容'
  } catch (e) {
    console.error('加载脚本失败', e)
    scriptContent.value = '加载失败'
  }
}

async function viewVersionScript(row: ScriptVersion) {
  currentVersion.value = row
  scriptContent.value = '加载中...'
  scriptDialogVisible.value = true

  try {
    const contentRes = await datasourceApi.getScriptContent(datasourceCode.value, row.version)
    scriptContent.value = contentRes.data || '无脚本内容'
  } catch (e) {
    console.error('加载脚本失败', e)
    scriptContent.value = '加载失败'
  }
}

function viewTask(task: CollectionScript) {
  if (task.id) {
    router.push(`/scripts/${task.id}`)
  }
}

// Tab change handler
function handleTabChange(tabName: string) {
  if (tabName === 'versions' && versions.value.length === 0) {
    loadVersions()
  } else if (tabName === 'tasks' && linkedTasks.value.length === 0) {
    loadLinkedTasks()
  }
}

// Watch activeTab
import { watch } from 'vue'
watch(activeTab, handleTabChange)

onMounted(() => {
  loadDataSource()
})
</script>

<style scoped>
.datasource-detail {
  padding: 20px;
}

.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 20px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.loading-state,
.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  color: var(--el-text-color-secondary);
  gap: 16px;
}

.detail-content {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
}

.detail-tabs {
  margin-top: 0;
}

.action-buttons {
  margin-top: 20px;
  display: flex;
  gap: 12px;
}

/* Version History Tab */
.version-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.version-count {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

/* Linked Tasks Tab */
.tasks-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--el-fill-color-light);
  border-radius: 6px;
}

.tasks-stats {
  display: flex;
  gap: 24px;
}

.config-editor {
  width: 100%;
}

.config-row {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
  align-items: center;
}

.config-key {
  flex: 1;
}

.config-value {
  flex: 2;
}

.config-tip {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  margin-top: 6px;
}

.stat-item {
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.stat-item strong {
  color: var(--el-text-color-primary);
  margin-left: 4px;
}

.tasks-sort {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-secondary);
  font-size: 14px;
}

.success-count {
  color: var(--el-color-success);
  font-weight: 500;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px;
  color: var(--el-text-color-secondary);
}

.empty-state p {
  margin-top: 12px;
}

/* Script Dialog */
.script-info {
  padding: 10px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>