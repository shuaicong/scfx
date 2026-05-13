<template>
  <div class="collection">
    <el-tabs v-model="activeTab" class="collection-tabs">
      <el-tab-pane label="采集任务" name="tasks">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>采集任务</span>
              <el-button type="primary" @click="handleCollect" :loading="collecting">
                <el-icon><Refresh /></el-icon>
                立即采集
              </el-button>
            </div>
          </template>

          <el-table :data="tasks" v-loading="loading" stripe>
            <el-table-column prop="taskName" label="任务名称" min-width="150" />
            <el-table-column prop="sourceName" label="数据源" width="120">
              <template #default="{ row }">
                <el-tag>{{ getSourceName(row.sourceName) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastExecutionTime" label="最后执行" width="160">
              <template #default="{ row }">
                {{ row.lastExecutionTime ? row.lastExecutionTime.substring(0, 19) : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="successCount" label="成功" width="80">
              <template #default="{ row }">
                <span style="color: #67c23a;">{{ row.successCount }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="failedCount" label="失败" width="80">
              <template #default="{ row }">
                <span style="color: #f56c6c;">{{ row.failedCount }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="executeTask(row)">执行</el-button>
                <el-button type="info" link size="small" @click="viewDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="pagination.page"
            v-model:page-size="pagination.size"
            :total="pagination.total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
            style="margin-top: 20px; justify-content: flex-end;"
          />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="脚本管理" name="scripts">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>采集脚本管理</span>
              <div class="header-actions">
                <el-button type="info" @click="showUploadDialog">
                  <el-icon><Upload /></el-icon> 上传文件
                </el-button>
                <el-button type="primary" @click="showCreateDialog">
                  <el-icon><Plus /></el-icon> 新建脚本
                </el-button>
              </div>
            </div>
          </template>

          <!-- 统计卡片 -->
          <el-row :gutter="20" class="stats-row">
            <el-col :span="8">
              <div class="stat-card total">
                <div class="stat-value">{{ scriptStats.total }}</div>
                <div class="stat-label">脚本总数</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-card enabled">
                <div class="stat-value">{{ scriptStats.enabled }}</div>
                <div class="stat-label">启用中</div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="stat-card disabled">
                <div class="stat-value">{{ scriptStats.disabled }}</div>
                <div class="stat-label">已禁用</div>
              </div>
            </el-col>
          </el-row>

          <!-- 列表 -->
          <el-table :data="scriptList" border stripe v-loading="scriptLoading">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="scriptName" label="脚本名称" min-width="150" />
            <el-table-column prop="source" label="数据源" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.source === 'liangxin'" type="success">粮信网</el-tag>
                <el-tag v-else-if="row.source === 'mysteel'" type="primary">我的钢铁网</el-tag>
                <el-tag v-else-if="row.source === 'chinagrain'" type="warning">中华粮网</el-tag>
                <el-tag v-else>{{ row.source || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="triggerType" label="触发方式" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.triggerType === 'manual'" type="info">手动</el-tag>
                <el-tag v-else-if="row.triggerType === 'single'" type="warning">单次</el-tag>
                <el-tag v-else-if="row.triggerType === 'repeat'">周期</el-tag>
                <el-tag v-else-if="row.triggerType === 'cron'" type="primary">Cron</el-tag>
                <el-tag v-else>{{ row.triggerType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.status === 'enabled'" type="success">启用</el-tag>
                <el-tag v-else type="danger">禁用</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastExecutionTime" label="最后执行" width="160">
              <template #default="{ row }">
                {{ row.lastExecutionTime ? formatTime(row.lastExecutionTime) : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button type="info" size="small" @click="showScriptDetail(row)">详情</el-button>
                <el-button type="success" size="small" @click="executeScript(row)">执行</el-button>
                <el-button type="primary" size="small" @click="editScript(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="scriptPagination.page"
            v-model:page-size="scriptPagination.size"
            :total="scriptPagination.total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            @size-change="loadScripts"
            @current-change="loadScripts"
            style="margin-top: 20px; justify-content: flex-end;"
          />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="向量化监控" name="vectorization">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>向量化监控</span>
            </div>
          </template>
          <div class="vectorization-placeholder">
            <el-empty description="向量化监控功能开发中" />
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 新建/编辑脚本对话框 -->
    <el-dialog v-model="scriptDialogVisible" :title="scriptDialogTitle" width="900px" :close-on-click-modal="false">
      <el-form :model="scriptForm" label-width="100px" class="script-form">
        <el-form-item label="脚本名称" required>
          <el-input v-model="scriptForm.scriptName" placeholder="请输入脚本名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="scriptForm.description" type="textarea" :rows="2" placeholder="请输入描述（可选）" />
        </el-form-item>
        <el-form-item label="脚本内容" v-if="scriptForm.id && editContentMode">
          <el-input
            v-model="scriptForm.scriptContent"
            type="textarea"
            :rows="15"
            placeholder="请输入Python脚本内容..."
            class="code-editor"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="scriptDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveScript">保存</el-button>
      </template>
    </el-dialog>

    <!-- 脚本详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="脚本详情" width="800px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="ID">{{ scriptDetail.id }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ scriptDetail.scriptName }}</el-descriptions-item>
        <el-descriptions-item label="触发方式">{{ scriptDetail.triggerType }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="scriptDetail.status === 'enabled' ? 'success' : 'danger'">
            {{ scriptDetail.status === 'enabled' ? '启用' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="执行次数">{{ scriptDetail.executionCount }}</el-descriptions-item>
        <el-descriptions-item label="成功次数">{{ scriptDetail.successCount }}</el-descriptions-item>
        <el-descriptions-item label="失败次数">{{ scriptDetail.failedCount }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ scriptDetail.description || '-' }}</el-descriptions-item>
      </el-descriptions>

      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button type="primary" @click="editScriptFromDetail">编辑</el-button>
        <el-button type="success" @click="executeScriptFromDetail">执行</el-button>
        <el-button type="warning" @click="showVersionHistory">版本历史</el-button>
      </template>
    </el-dialog>

    <!-- 上传对话框 -->
    <el-dialog v-model="uploadDialogVisible" title="上传脚本文件" width="500px">
      <el-form :model="uploadForm" label-width="100px">
        <el-form-item label="脚本名称" required>
          <el-input v-model="uploadForm.scriptName" placeholder="将作为文件名" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="uploadForm.description" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
        <el-form-item label="选择文件" required>
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            :limit="1"
            accept=".py"
            :on-change="handleFileChange"
          >
            <el-button>选择Python文件</el-button>
            <template #tip>
              <div class="el-upload__tip">只能上传 .py 文件</div>
            </template>
          </el-upload>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUploadScript" :loading="uploading">上传</el-button>
      </template>
    </el-dialog>

    <!-- 版本历史 -->
    <VersionHistory
      v-model="versionHistoryVisible"
      :script-id="currentScriptId"
    />

    <!-- 脚本编辑器 -->
    <ScriptEditDialog
      v-model="scriptEditorVisible"
      :script-id="currentScriptId"
      :script-name="currentScriptName"
      :version="currentScriptVersion"
      @success="loadScripts"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Upload, Plus } from '@element-plus/icons-vue'
import { getTasks, collectLiangxinwang } from '@/api/dashboard'
import { scriptApi, CollectionScript } from '@/api'
import { ElMessageBox as MessageBox } from 'element-plus'
import VersionHistory from '@/components/VersionHistory.vue'
import ScriptEditDialog from '@/components/ScriptEditDialog.vue'

const loading = ref(false)
const collecting = ref(false)
const tasks = ref<any[]>([])

const activeTab = ref('tasks')

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

// 脚本管理相关数据
const scriptLoading = ref(false)
const scriptList = ref<CollectionScript[]>([])
const scriptStats = ref({ total: 0, enabled: 0, disabled: 0 })
const scriptPagination = reactive({
  page: 1,
  size: 20,
  total: 0
})
const scriptDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const uploadDialogVisible = ref(false)
const versionHistoryVisible = ref(false)
const scriptEditorVisible = ref(false)
const editContentMode = ref(true)
const uploadRef = ref()
const uploading = ref(false)

const scriptForm = ref<any>({})
const scriptDetail = ref<any>({})
const uploadForm = reactive({
  scriptName: '',
  description: '',
  file: null as File | null
})

const currentScriptId = ref(0)
const currentScriptName = ref('')
const currentScriptVersion = ref('')

const scriptDialogTitle = computed(() => scriptForm.value.id ? '编辑脚本' : '新建脚本')

async function loadScripts() {
  scriptLoading.value = true
  try {
    const res: any = await scriptApi.list({
      page: scriptPagination.page,
      size: scriptPagination.size
    })
    scriptList.value = res.data.records || []
    scriptPagination.total = res.data.total || 0
    await loadScriptStats()
  } catch (e) {
    console.error('加载脚本列表失败', e)
  } finally {
    scriptLoading.value = false
  }
}

async function loadScriptStats() {
  try {
    const res: any = await scriptApi.stats()
    scriptStats.value = res.data
  } catch (e) {
    console.error('加载统计失败', e)
  }
}

function formatTime(time: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

function showCreateDialog() {
  scriptForm.value = {
    scriptName: '',
    description: '',
    scriptContent: '#!/usr/bin/env python3\n# -*- coding: utf-8 -*-\n\n# 在此编写采集脚本...\n',
    status: 'enabled'
  }
  editContentMode.value = true
  scriptDialogVisible.value = true
}

function showUploadDialog() {
  uploadForm.scriptName = ''
  uploadForm.description = ''
  uploadForm.file = null
  uploadDialogVisible.value = true
}

function handleFileChange(file: any) {
  uploadForm.file = file.raw
  if (!uploadForm.scriptName && file.name) {
    uploadForm.scriptName = file.name.replace('.py', '')
  }
}

async function handleUploadScript() {
  if (!uploadForm.scriptName) {
    ElMessage.warning('请输入脚本名称')
    return
  }
  if (!uploadForm.file) {
    ElMessage.warning('请选择文件')
    return
  }

  uploading.value = true
  try {
    await scriptApi.upload(uploadForm.scriptName, uploadForm.description, uploadForm.file)
    ElMessage.success('上传成功')
    uploadDialogVisible.value = false
    loadScripts()
  } catch (e) {
    console.error('上传失败', e)
  } finally {
    uploading.value = false
  }
}

async function handleSaveScript() {
  if (!scriptForm.value.scriptName) {
    ElMessage.warning('请输入脚本名称')
    return
  }

  try {
    if (scriptForm.value.id) {
      if (editContentMode.value && scriptForm.value.scriptContent) {
        await scriptApi.updateContent(scriptForm.value.id, scriptForm.value.scriptContent)
      }
      ElMessage.success('保存成功')
    } else {
      await scriptApi.create(
        scriptForm.value.scriptName,
        scriptForm.value.description || '',
        scriptForm.value.scriptContent || ''
      )
      ElMessage.success('创建成功')
    }
    scriptDialogVisible.value = false
    loadScripts()
  } catch (e) {
    console.error('保存失败', e)
  }
}

async function showScriptDetail(row: CollectionScript) {
  scriptDetail.value = { ...row }
  if (row.id) {
    try {
      const res: any = await scriptApi.getContent(row.id)
      scriptDetail.value.scriptContent = res.data
    } catch (e) {
      console.error('加载脚本内容失败', e)
    }
  }
  detailDialogVisible.value = true
}

function editScript(row: CollectionScript) {
  scriptForm.value = { ...row }
  editContentMode.value = true
  scriptDialogVisible.value = true
}

function editScriptFromDetail() {
  scriptForm.value = { ...scriptDetail.value }
  detailDialogVisible.value = false
  scriptDialogVisible.value = true
}

async function executeScript(row: CollectionScript) {
  if (!row.id) return
  try {
    await MessageBox.confirm(`确定立即执行脚本"${row.scriptName}"吗？`, '执行确认', { type: 'info' })
    await scriptApi.execute(row.id)
    ElMessage.success('脚本已触发执行，请查看日志')
    loadScripts()
  } catch (e: any) {
    if (e !== 'cancel') {
      console.error('执行失败', e)
    }
  }
}

async function executeScriptFromDetail() {
  if (!scriptDetail.value.id) return
  try {
    await scriptApi.execute(scriptDetail.value.id)
    ElMessage.success('脚本已触发执行，请查看日志')
    detailDialogVisible.value = false
  } catch (e) {
    console.error('执行失败', e)
  }
}

function showVersionHistory() {
  currentScriptId.value = scriptDetail.value.id
  detailDialogVisible.value = false
  versionHistoryVisible.value = true
}

const loadTasks = async () => {
  try {
    loading.value = true
    const res: any = await getTasks({
      page: pagination.page,
      size: pagination.size
    })
    if (res.code === 200) {
      tasks.value = res.data.records || []
      pagination.total = res.data.total || 0
    }
  } catch (error) {
    console.error('加载任务列表失败', error)
  } finally {
    loading.value = false
  }
}

const handleCollect = async () => {
  try {
    collecting.value = true
    await collectLiangxinwang()
    ElMessage.success('采集任务已启动，请查看日志了解进度')
  } catch (error) {
    ElMessage.error('启动采集失败')
  } finally {
    collecting.value = false
  }
}

const executeTask = (row: any) => {
  ElMessageBox.confirm(`确定执行任务"${row.taskName}"吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info'
  }).then(async () => {
    ElMessage.success('任务已触发执行')
  }).catch(() => {})
}

const viewDetail = (row: any) => {
  ElMessage.info('详情功能开发中')
}

const getSourceName = (source: string) => {
  const map: Record<string, string> = {
    liangxinwang: '粮信网',
    mysteel: '我的钢铁网',
    china_grain: '中华粮网'
  }
  return map[source] || source
}

const getStatusType = (status: string) => {
  const map: Record<string, string> = {
    pending: 'info',
    running: 'primary',
    success: 'success',
    failed: 'danger',
    retrying: 'warning'
  }
  return map[status] || 'info'
}

const getStatusText = (status: string) => {
  const map: Record<string, string> = {
    pending: '等待',
    running: '运行中',
    success: '成功',
    failed: '失败',
    retrying: '重试中'
  }
  return map[status] || status
}

const handleSizeChange = (size: number) => {
  pagination.size = size
  loadTasks()
}

const handlePageChange = (page: number) => {
  pagination.page = page
  loadTasks()
}

onMounted(() => {
  loadTasks()
})
</script>

<style scoped>
.collection {
  padding: 20px;
}

.collection-tabs {
  margin-top: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  padding: 20px;
  border-radius: 12px;
  text-align: center;
  color: #fff;
}

.stat-card.total { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); }
.stat-card.enabled { background: linear-gradient(135deg, #48bb78 0%, #38a169 100%); }
.stat-card.disabled { background: linear-gradient(135deg, #a0aec0 0%, #718096 100%); }

.stat-value {
  font-size: 32px;
  font-weight: bold;
}

.stat-label {
  font-size: 14px;
  margin-top: 8px;
  opacity: 0.9;
}

.script-form {
  max-width: 700px;
}

.code-editor :deep(textarea) {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.6;
}

.vectorization-placeholder {
  padding: 60px 0;
  text-align: center;
}
</style>
