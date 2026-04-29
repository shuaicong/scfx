<template>
  <div class="scripts-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>采集脚本管理</span>
          <div class="header-actions">
            <el-button type="info" @click="handleUpload">
              <el-icon><Upload /></el-icon> 上传文件
            </el-button>
            <el-button type="primary" @click="handleCreate">
              <el-icon><Plus /></el-icon> 新建脚本
            </el-button>
          </div>
        </div>
      </template>

      <!-- 统计卡片 -->
      <el-row :gutter="20" class="stats-row">
        <el-col :span="8">
          <div class="stat-card total">
            <div class="stat-value">{{ stats.total }}</div>
            <div class="stat-label">脚本总数</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-card enabled">
            <div class="stat-value">{{ stats.enabled }}</div>
            <div class="stat-label">启用中</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-card disabled">
            <div class="stat-value">{{ stats.disabled }}</div>
            <div class="stat-label">已禁用</div>
          </div>
        </el-col>
      </el-row>

      <!-- 筛选 -->
      <el-form :inline="true" class="filter-form">
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="全部" value="" />
            <el-option label="启用" value="enabled" />
            <el-option label="禁用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据源">
          <el-select v-model="filters.source" placeholder="全部" clearable style="width: 150px">
            <el-option label="粮信网" value="liangxin" />
            <el-option label="我的钢铁网" value="mysteel" />
            <el-option label="中华粮网" value="chinagrain" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 列表 -->
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="scriptName" label="脚本名称" min-width="150" />
        <el-table-column prop="scriptPath" label="文件路径" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="script-path">{{ row.scriptPath || '-' }}</span>
          </template>
        </el-table-column>
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
        <el-table-column prop="executionCount" label="执行次数" width="90" />
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
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="info" size="small" @click="showDetail(row)">
              详情
            </el-button>
            <el-button type="success" size="small" @click="triggerExecute(row)">
              执行
            </el-button>
            <el-button type="primary" size="small" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 新建/编辑对话框（简化版） -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="900px" :close-on-click-modal="false">
      <el-form :model="currentRow" label-width="100px" class="script-form">
        <el-form-item label="脚本名称" required>
          <el-input v-model="currentRow.scriptName" placeholder="请输入脚本名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="currentRow.description" type="textarea" :rows="2" placeholder="请输入描述（可选）" />
        </el-form-item>
        <el-form-item label="脚本内容" v-if="currentRow.id">
          <el-switch v-model="editContentMode" active-text="编辑内容" inactive-text="仅保存到文件" />
        </el-form-item>
        <el-form-item label="脚本内容" v-if="!currentRow.id || editContentMode">
          <el-input
            v-model="currentRow.scriptContent"
            type="textarea"
            :rows="15"
            placeholder="请输入Python脚本内容..."
            class="code-editor"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="脚本详情" width="800px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="ID">{{ detailRow.id }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ detailRow.scriptName }}</el-descriptions-item>
        <el-descriptions-item label="文件路径" :span="2">
          <span class="script-path">{{ detailRow.scriptPath || '-' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="触发方式">{{ detailRow.triggerType }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detailRow.status === 'enabled' ? 'success' : 'danger'">
            {{ detailRow.status === 'enabled' ? '启用' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="执行次数">{{ detailRow.executionCount }}</el-descriptions-item>
        <el-descriptions-item label="成功次数">{{ detailRow.successCount }}</el-descriptions-item>
        <el-descriptions-item label="失败次数">{{ detailRow.failedCount }}</el-descriptions-item>
        <el-descriptions-item label="最后执行" :span="2">
          {{ detailRow.lastExecutionTime ? formatTime(detailRow.lastExecutionTime) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ detailRow.description || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="script-content-preview">
        <h4>脚本内容</h4>
        <pre>{{ detailRow.scriptContent || '点击"查看文件"按钮加载' }}</pre>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="info" @click="handleViewFile">查看文件</el-button>
        <el-button type="primary" @click="handleEditFromDetail">编辑</el-button>
        <el-button type="success" @click="handleExecute">立即执行</el-button>
      </template>
    </el-dialog>

    <!-- 上传对话框 -->
    <el-dialog v-model="uploadVisible" title="上传脚本文件" width="500px">
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
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUploadSubmit" :loading="uploading">上传</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Upload } from '@element-plus/icons-vue'
import { scriptApi, CollectionScript, ScriptStats } from '../../api'

const loading = ref(false)
const uploading = ref(false)
const list = ref<CollectionScript[]>([])
const stats = ref<ScriptStats>({ total: 0, enabled: 0, disabled: 0 })
const dialogVisible = ref(false)
const detailVisible = ref(false)
const uploadVisible = ref(false)
const editContentMode = ref(true)
const uploadRef = ref()

const filters = reactive({
  status: '',
  source: ''
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const currentRow = ref<any>({})
const detailRow = ref<any>({})
const uploadForm = reactive({
  scriptName: '',
  description: '',
  file: null as File | null
})

const dialogTitle = computed(() => currentRow.value.id ? '编辑脚本' : '新建脚本')

function formatTime(time: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

async function loadStats() {
  try {
    const res = await scriptApi.stats()
    stats.value = (res as any).data
  } catch (e) {
    console.error('加载统计失败', e)
  }
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await scriptApi.list({
      page: pagination.page,
      size: pagination.size,
      status: filters.status || undefined,
      source: filters.source || undefined
    })
    const pageData = res.data
    list.value = pageData.records || []
    pagination.total = pageData.total || 0
    await loadStats()
  } catch (e) {
    console.error('加载列表失败', e)
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.status = ''
  filters.source = ''
  pagination.page = 1
  loadData()
}

function handleCreate() {
  currentRow.value = {
    scriptName: '',
    description: '',
    scriptContent: '#!/usr/bin/env python3\n# -*- coding: utf-8 -*-\n\n# 在此编写采集脚本...\n',
    status: 'enabled'
  }
  editContentMode.value = true
  dialogVisible.value = true
}

function handleEdit(row: CollectionScript) {
  currentRow.value = { ...row }
  editContentMode.value = true
  dialogVisible.value = true
}

function handleEditFromDetail() {
  currentRow.value = { ...detailRow.value }
  detailVisible.value = false
  dialogVisible.value = true
}

function handleUpload() {
  uploadForm.scriptName = ''
  uploadForm.description = ''
  uploadForm.file = null
  uploadVisible.value = true
}

function handleFileChange(file: any) {
  uploadForm.file = file.raw
  if (!uploadForm.scriptName && file.name) {
    uploadForm.scriptName = file.name.replace('.py', '')
  }
}

async function handleUploadSubmit() {
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
    uploadVisible.value = false
    loadData()
  } catch (e) {
    console.error('上传失败', e)
  } finally {
    uploading.value = false
  }
}

async function handleSave() {
  if (!currentRow.value.scriptName) {
    ElMessage.warning('请输入脚本名称')
    return
  }

  try {
    if (currentRow.value.id) {
      // 更新时，保存内容到文件
      if (editContentMode.value && currentRow.value.scriptContent) {
        await scriptApi.updateContent(currentRow.value.id, currentRow.value.scriptContent)
      }
      ElMessage.success('保存成功')
    } else {
      // 新建时，创建脚本
      await scriptApi.create(
        currentRow.value.scriptName,
        currentRow.value.description || '',
        currentRow.value.scriptContent || ''
      )
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    console.error('保存失败', e)
  }
}

async function handleDelete(row: CollectionScript) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm('确定删除该脚本？删除后无法恢复！', '警告', { type: 'warning' })
    await scriptApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') {
      console.error('删除失败', e)
    }
  }
}

async function showDetail(row: CollectionScript) {
  detailRow.value = { ...row }
  // 加载脚本内容
  if (row.id) {
    try {
      const res: any = await scriptApi.getContent(row.id)
      detailRow.value.scriptContent = res.data
    } catch (e) {
      console.error('加载脚本内容失败', e)
    }
  }
  detailVisible.value = true
}

async function handleViewFile() {
  if (!detailRow.value.id) return
  try {
    const res: any = await scriptApi.getContent(detailRow.value.id)
    detailRow.value.scriptContent = res.data
    ElMessage.success('已重新加载文件内容')
  } catch (e) {
    console.error('加载失败', e)
  }
}

async function handleExecute() {
  if (!detailRow.value.id) return
  try {
    await scriptApi.execute(detailRow.value.id)
    ElMessage.success('脚本已触发执行，请查看日志')
    detailVisible.value = false
    loadData()
  } catch (e) {
    console.error('执行失败', e)
  }
}

async function triggerExecute(row: CollectionScript) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(`确定立即执行脚本"${row.scriptName}"吗？`, '执行确认', { type: 'info' })
    await scriptApi.execute(row.id)
    ElMessage.success('脚本已触发执行，请查看日志')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') {
      console.error('执行失败', e)
    }
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.scripts-container {
  padding: 20px;
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

.filter-form {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.script-form {
  max-width: 700px;
}

.code-editor :deep(textarea) {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.6;
}

.script-content-preview {
  margin-top: 20px;
}

.script-content-preview h4 {
  margin-bottom: 10px;
  color: #1a202c;
}

.script-content-preview pre {
  background: #1e2433;
  color: #c8d1dc;
  padding: 15px;
  border-radius: 8px;
  max-height: 300px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 12px;
}

.script-path {
  font-family: 'Monaco', 'Menlo', monospace;
  font-size: 12px;
  color: #718096;
}
</style>
