<template>
  <div class="datasource-container">
    <!-- Header -->
    <div class="page-header">
      <h2 class="page-title">数据源管理</h2>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>
        新增数据源
      </el-button>
    </div>

    <!-- Filter Area -->
    <div class="filter-area">
      <el-select v-model="filterStatus" placeholder="状态筛选" clearable style="width: 120px;">
        <el-option label="启用" value="1" />
        <el-option label="禁用" value="0" />
      </el-select>
      <el-input
        v-model="searchKey"
        placeholder="搜索标识或名称"
        clearable
        style="width: 240px;"
        @clear="loadData"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button @click="loadData">搜索</el-button>
    </div>

    <!-- Table -->
    <el-table :data="list" v-loading="loading" border stripe>
      <el-table-column prop="code" label="标识" min-width="120" />
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
      <el-table-column prop="enabled" label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.enabled === 1 ? 'success' : 'danger'" size="small">
            {{ row.enabled === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="authType" label="认证方式" width="120" align="center">
        <template #default="{ row }">
          {{ getAuthTypeText(row.authType) }}
        </template>
      </el-table-column>
      <el-table-column prop="hasScript" label="脚本" width="80" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.hasScript" type="success" size="small">已上传</el-tag>
          <el-tag v-else type="info" size="small">无</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEditDialog(row)">编辑</el-button>
          <el-button link type="primary" size="small" @click="openUploadDialog(row)">上传脚本</el-button>
          <el-button link type="primary" size="small" @click="viewScript(row)" :disabled="!row.hasScript">查看脚本</el-button>
          <el-button link :type="row.enabled === 1 ? 'warning' : 'success'" size="small" @click="toggleEnabled(row)">
            {{ row.enabled === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Pagination -->
    <div class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :page-sizes="[10, 20, 50, 100]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next"
        @size-change="loadData"
        @current-change="loadData"
      />
    </div>

    <!-- Create/Edit Dialog -->
    <el-dialog
      v-model="formDialogVisible"
      :title="isEditing ? '编辑数据源' : '新增数据源'"
      width="500px"
      @close="resetForm"
    >
      <el-form :model="form" :rules="formRules" ref="formRef" label-width="100px">
        <el-form-item label="标识" prop="code">
          <el-input v-model="form.code" :disabled="isEditing" placeholder="唯一标识，如 mysteel" />
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="显示名称，如 我的钢铁" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="数据源描述" />
        </el-form-item>
        <el-form-item label="登录URL" prop="loginUrl">
          <el-input v-model="form.loginUrl" placeholder="登录页面URL" />
        </el-form-item>
        <el-form-item label="认证方式" prop="authType">
          <el-select v-model="form.authType" placeholder="选择认证方式" style="width: 100%;">
            <el-option label="无认证" value="none" />
            <el-option label="Cookie认证" value="cookie" />
            <el-option label="登录表单" value="form" />
            <el-option label="OAuth" value="oauth" />
            <el-option label="API密钥" value="apikey" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用" prop="enabled">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <!-- Upload Script Dialog -->
    <el-dialog v-model="uploadDialogVisible" title="上传脚本" width="500px">
      <div class="upload-info" v-if="currentRow">
        <span>数据源：{{ currentRow.name }} ({{ currentRow.code }})</span>
      </div>
      <el-upload
        ref="uploadRef"
        class="script-uploader"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".py,.js,.ts,.sh,.bash"
        @change="handleFileChange"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          拖拽文件到此处或 <em>点击上传</em>
        </div>
        <template #tip>
          <div class="el-upload__tip">支持 .py, .js, .ts, .sh, .bash 文件</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleUpload" :loading="uploading">上传</el-button>
      </template>
    </el-dialog>

    <!-- View Script Dialog -->
    <el-dialog v-model="scriptDialogVisible" title="查看脚本" width="700px">
      <div class="script-info" v-if="currentRow">
        <span>数据源：{{ currentRow.name }} ({{ currentRow.code }})</span>
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
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadInstance } from 'element-plus'
import { Plus, Search, UploadFilled } from '@element-plus/icons-vue'
import { datasourceApi, type DataSource } from '@/api/datasource'

// List state
const list = ref<DataSource[]>([])
const loading = ref(false)

// Filter state
const filterStatus = ref<'' | '0' | '1'>('')
const searchKey = ref('')

// Pagination
const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

// Form dialog state
const formDialogVisible = ref(false)
const isEditing = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()

// Form data
const form = reactive({
  code: '',
  name: '',
  description: '',
  loginUrl: '',
  authType: 'none',
  enabled: true
})

// Form validation rules
const formRules: FormRules = {
  code: [
    { required: true, message: '请输入标识', trigger: 'blur' },
    { pattern: /^[a-zA-Z0-9_-]+$/, message: '标识只能包含字母、数字、下划线和连字符', trigger: 'blur' }
  ],
  name: [
    { required: true, message: '请输入名称', trigger: 'blur' }
  ],
  authType: [
    { required: true, message: '请选择认证方式', trigger: 'change' }
  ]
}

// Upload dialog state
const uploadDialogVisible = ref(false)
const uploading = ref(false)
const uploadRef = ref<UploadInstance>()
const currentRow = ref<DataSource | null>(null)
const uploadFile = ref<File | null>(null)

// Script view dialog state
const scriptDialogVisible = ref(false)
const scriptContent = ref('')

// Auth type mapping
const authTypeMap: Record<string, string> = {
  none: '无认证',
  cookie: 'Cookie认证',
  form: '登录表单',
  oauth: 'OAuth',
  apikey: 'API密钥'
}

function getAuthTypeText(type?: string): string {
  return authTypeMap[type || 'none'] || type || '无认证'
}

// Load data
async function loadData() {
  loading.value = true
  try {
    const res = await datasourceApi.list()
    let data = res.data || []

    // Filter by status
    if (filterStatus.value !== '') {
      data = data.filter((item: DataSource) => item.enabled === (filterStatus.value === '1' ? 1 : 0))
    }

    // Filter by search key
    if (searchKey.value) {
      const key = searchKey.value.toLowerCase()
      data = data.filter((item: DataSource) =>
        item.code.toLowerCase().includes(key) ||
        item.name.toLowerCase().includes(key)
      )
    }

    // Check script existence for each item
    for (const item of data) {
      try {
        const existsRes = await datasourceApi.checkScriptExists(item.code)
        item.hasScript = existsRes.data?.exists || false
      } catch {
        item.hasScript = false
      }
    }

    list.value = data
    pagination.total = data.length
  } catch (e) {
    console.error('加载数据源列表失败', e)
    ElMessage.error('加载数据源列表失败')
  } finally {
    loading.value = false
  }
}

// Open create dialog
function openCreateDialog() {
  isEditing.value = false
  formDialogVisible.value = true
}

// Open edit dialog
function openEditDialog(row: DataSource) {
  isEditing.value = true
  form.code = row.code
  form.name = row.name
  form.description = row.description || ''
  form.loginUrl = row.loginUrl || ''
  form.authType = row.authType || 'none'
  form.enabled = row.enabled === 1
  formDialogVisible.value = true
}

// Reset form
function resetForm() {
  form.code = ''
  form.name = ''
  form.description = ''
  form.loginUrl = ''
  form.authType = 'none'
  form.enabled = true
  formRef.value?.resetFields()
}

// Submit form
async function handleSubmit() {
  if (!formRef.value) return

  await formRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      const data: Partial<DataSource> = {
        code: form.code,
        name: form.name,
        description: form.description,
        loginUrl: form.loginUrl,
        authType: form.authType,
        enabled: form.enabled ? 1 : 0
      }

      if (isEditing.value) {
        await datasourceApi.update(form.code, data)
        ElMessage.success('更新成功')
      } else {
        await datasourceApi.create(data)
        ElMessage.success('创建成功')
      }

      formDialogVisible.value = false
      loadData()
    } catch (e: any) {
      console.error('保存失败', e)
      ElMessage.error(e.message || '保存失败')
    } finally {
      submitting.value = false
    }
  })
}

// Toggle enabled
async function toggleEnabled(row: DataSource) {
  try {
    if (row.enabled === 1) {
      await datasourceApi.disable(row.code)
      ElMessage.success('已禁用')
    } else {
      await datasourceApi.enable(row.code)
      ElMessage.success('已启用')
    }
    loadData()
  } catch (e) {
    console.error('切换状态失败', e)
    ElMessage.error('操作失败')
  }
}

// Open upload dialog
function openUploadDialog(row: DataSource) {
  currentRow.value = row
  uploadFile.value = null
  uploadDialogVisible.value = true
}

// Handle file change
function handleFileChange(file: any) {
  uploadFile.value = file.raw
}

// Handle upload
async function handleUpload() {
  if (!uploadFile.value || !currentRow.value) {
    ElMessage.warning('请选择文件')
    return
  }

  uploading.value = true
  try {
    await datasourceApi.uploadScript(uploadFile.value, currentRow.value.code)
    ElMessage.success('脚本上传成功')
    uploadDialogVisible.value = false
    loadData()
  } catch (e) {
    console.error('上传失败', e)
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

// View script
async function viewScript(row: DataSource) {
  currentRow.value = row
  scriptContent.value = '加载中...'
  scriptDialogVisible.value = true

  try {
    const res = await datasourceApi.getScriptContent(row.code)
    scriptContent.value = res.data || '无脚本内容'
  } catch (e) {
    console.error('加载脚本失败', e)
    scriptContent.value = '加载失败'
  }
}

// Handle delete
async function handleDelete(row: DataSource) {
  try {
    await ElMessageBox.confirm(`确定删除数据源「${row.name}」吗？删除后无法恢复！`, '警告', {
      type: 'warning'
    })
    await datasourceApi.delete(row.code)
    ElMessage.success('删除成功')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') {
      console.error('删除失败', e)
      ElMessage.error('删除失败')
    }
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.datasource-container {
  padding: 20px;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.filter-area {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.upload-info,
.script-info {
  padding: 10px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  margin-bottom: 10px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.script-uploader {
  width: 100%;
}

.script-uploader .el-icon--upload {
  font-size: 48px;
  color: var(--el-text-color-placeholder);
  margin-bottom: 10px;
}

.script-uploader .el-upload__text {
  font-size: 14px;
  color: var(--el-text-color-regular);
}

.script-uploader .el-upload__text em {
  color: var(--el-color-primary);
  font-style: normal;
}

.script-uploader .el-upload__tip {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  margin-top: 8px;
}
</style>