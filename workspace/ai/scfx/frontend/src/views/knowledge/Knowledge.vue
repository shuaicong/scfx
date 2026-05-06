<template>
  <div class="knowledge-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>知识库管理</span>
          <el-button type="primary" @click="showAddDialog">
            <el-icon><Plus /></el-icon> 添加知识
          </el-button>
        </div>
      </template>

      <!-- 统计卡片 -->
      <el-row :gutter="20" class="stats-row">
        <el-col :span="6">
          <div class="stat-card total">
            <div class="stat-value">{{ stats.total }}</div>
            <div class="stat-label">知识总数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card by-source">
            <div class="stat-value">{{ Object.keys(stats.bySource || {}).length }}</div>
            <div class="stat-label">数据来源</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card status">
            <div class="stat-value">{{ stats.byStatus?.vectorized || 0 }} / {{ stats.total || 0 }}</div>
            <div class="stat-label">已向量化</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card chunks">
            <div class="stat-value">{{ stats.totalChunks || 0 }}</div>
            <div class="stat-label">文本块数</div>
          </div>
        </el-col>
      </el-row>

      <!-- 筛选 -->
      <el-form :inline="true" class="filter-form">
        <el-form-item label="来源类型">
          <el-select v-model="filters.sourceType" placeholder="全部" clearable style="width: 150px">
            <el-option label="粮信网" value="liangxin" />
            <el-option label="我的钢铁" value="mysteel" />
            <el-option label="中华粮网" value="chinagrain" />
            <el-option label="USDA" value="usda" />
            <el-option label="气象数据" value="weather" />
            <el-option label="人工录入" value="manual" />
          </el-select>
        </el-form-item>
        <el-form-item label="向量化状态">
          <el-select v-model="filters.vectorStatus" placeholder="全部" clearable style="width: 150px">
            <el-option label="已向量化" value="vectorized" />
            <el-option label="未向量化" value="pending" />
            <el-option label="向量化中" value="processing" />
            <el-option label="失败" value="failed" />
          </el-select>
        </el-form-item>
        <el-form-item label="搜索">
          <el-input v-model="filters.search" placeholder="搜索标题" clearable style="width: 200px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 列表 -->
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="sourceName" label="来源" width="120" />
        <el-table-column prop="sourceType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.sourceType === 'liangxin'" type="success">粮信网</el-tag>
            <el-tag v-else-if="row.sourceType === 'mysteel'" type="primary">我的钢铁</el-tag>
            <el-tag v-else-if="row.sourceType === 'chinagrain'" type="warning">中华粮网</el-tag>
            <el-tag v-else-if="row.sourceType === 'usda'" type="info">USDA</el-tag>
            <el-tag v-else-if="row.sourceType === 'manual'" type="info">人工录入</el-tag>
            <el-tag v-else>{{ row.sourceType || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="块数" width="80" />
        <el-table-column prop="vectorStatus" label="向量化" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.vectorStatus === 'vectorized'" type="success">已向量化</el-tag>
            <el-tag v-else-if="row.vectorStatus === 'pending'" type="warning">未向量化</el-tag>
            <el-tag v-else-if="row.vectorStatus === 'processing'" type="info">处理中</el-tag>
            <el-tag v-else-if="row.vectorStatus === 'failed'" type="danger">失败</el-tag>
            <el-tag v-else>{{ row.vectorStatus || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button type="info" size="small" @click="viewDetail(row)">查看</el-button>
            <el-button type="primary" size="small" @click="handleRevectorize(row)" :loading="row.revectorizing">
              重向量化
            </el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
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

    <!-- 添加知识对话框 -->
    <el-dialog v-model="addDialogVisible" title="添加知识" width="700px" :close-on-click-modal="false">
      <el-tabs v-model="addTab">
        <el-tab-pane label="上传文档" name="upload">
          <el-form :model="uploadForm" label-width="100px" class="upload-form">
            <el-form-item label="标题" required>
              <el-input v-model="uploadForm.title" placeholder="请输入文档标题" />
            </el-form-item>
            <el-form-item label="来源">
              <el-input v-model="uploadForm.source" placeholder="如：粮信网" />
            </el-form-item>
            <el-form-item label="作者">
              <el-input v-model="uploadForm.author" placeholder="可选" />
            </el-form-item>
            <el-form-item label="选择文件" required>
              <el-upload
                ref="uploadRef"
                :auto-upload="false"
                :limit="1"
                accept=".pdf,.doc,.docx,.txt,.md"
                :on-change="handleFileChange"
                drag
              >
                <el-icon class="upload-icon"><UploadFilled /></el-icon>
                <div class="upload-text">将文件拖到此处，或<em>点击上传</em></div>
                <template #tip>
                  <div class="el-upload__tip">支持 PDF、Word、TXT、Markdown 格式</div>
                </template>
              </el-upload>
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="addDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="handleUpload" :loading="uploading">上传</el-button>
          </template>
        </el-tab-pane>

        <el-tab-pane label="人工录入" name="manual">
          <el-form :model="manualForm" label-width="100px" class="manual-form">
            <el-form-item label="标题" required>
              <el-input v-model="manualForm.title" placeholder="请输入知识标题" />
            </el-form-item>
            <el-form-item label="内容" required>
              <el-input
                v-model="manualForm.content"
                type="textarea"
                :rows="8"
                placeholder="请输入知识内容"
              />
            </el-form-item>
            <el-form-item label="来源">
              <el-input v-model="manualForm.source" placeholder="可选，如：内部文档" />
            </el-form-item>
            <el-form-item label="作者">
              <el-input v-model="manualForm.author" placeholder="可选" />
            </el-form-item>
            <el-form-item label="发布时间">
              <el-date-picker
                v-model="manualForm.publishTime"
                type="datetime"
                placeholder="选择日期时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-form>
          <template #footer>
            <el-button @click="addDialogVisible = false">取消</el-button>
            <el-button type="primary" @click="handleManualAdd" :loading="submitting">提交</el-button>
          </template>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="知识详情" width="700px">
      <el-descriptions :column="2" border v-if="detailRow.id">
        <el-descriptions-item label="ID">{{ detailRow.id }}</el-descriptions-item>
        <el-descriptions-item label="标题">{{ detailRow.title }}</el-descriptions-item>
        <el-descriptions-item label="来源名称">{{ detailRow.sourceName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源类型">
          <el-tag v-if="detailRow.sourceType === 'liangxin'" type="success">粮信网</el-tag>
          <el-tag v-else-if="detailRow.sourceType === 'mysteel'" type="primary">我的钢铁</el-tag>
          <el-tag v-else-if="detailRow.sourceType === 'chinagrain'" type="warning">中华粮网</el-tag>
          <el-tag v-else-if="detailRow.sourceType === 'manual'" type="info">人工录入</el-tag>
          <el-tag v-else>{{ detailRow.sourceType || '-' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="作者">{{ detailRow.author || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发布时间">{{ detailRow.publishTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="文本块数">{{ detailRow.chunkCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="向量化状态">
          <el-tag v-if="detailRow.vectorStatus === 'vectorized'" type="success">已向量化</el-tag>
          <el-tag v-else-if="detailRow.vectorStatus === 'pending'" type="warning">未向量化</el-tag>
          <el-tag v-else-if="detailRow.vectorStatus === 'processing'" type="info">处理中</el-tag>
          <el-tag v-else-if="detailRow.vectorStatus === 'failed'" type="danger">失败</el-tag>
          <el-tag v-else>{{ detailRow.vectorStatus || '-' }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">
          {{ detailRow.createdAt ? formatTime(detailRow.createdAt) : '-' }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, UploadFilled } from '@element-plus/icons-vue'
import { knowledgeApi } from '@/api/knowledge'

interface KnowledgeItem {
  id?: number
  title: string
  sourceName?: string
  sourceType?: string
  chunkCount?: number
  vectorStatus?: string
  author?: string
  publishTime?: string
  createdAt?: string
  content?: string
  revectorizing?: boolean
}

interface KnowledgeStats {
  total: number
  bySource?: Record<string, number>
  byStatus?: { vectorized?: number; pending?: number; processing?: number; failed?: number }
  totalChunks?: number
}

const loading = ref(false)
const uploading = ref(false)
const submitting = ref(false)
const list = ref<KnowledgeItem[]>([])
const stats = ref<KnowledgeStats>({ total: 0 })
const addDialogVisible = ref(false)
const detailVisible = ref(false)
const addTab = ref('upload')
const uploadRef = ref()

const filters = reactive({
  sourceType: '',
  vectorStatus: '',
  search: ''
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const detailRow = ref<KnowledgeItem>({})
const uploadForm = reactive({
  title: '',
  source: '',
  author: '',
  file: null as File | null
})

const manualForm = reactive({
  title: '',
  content: '',
  source: '',
  author: '',
  publishTime: ''
})

function formatTime(time: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

async function loadStats() {
  try {
    const res: any = await knowledgeApi.stats()
    stats.value = res.data || { total: 0 }
  } catch (e) {
    console.error('加载统计失败', e)
    ElMessage.error('加载统计失败')
  }
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await knowledgeApi.list({
      page: pagination.page,
      size: pagination.size,
      sourceType: filters.sourceType || undefined,
      vectorStatus: filters.vectorStatus || undefined,
      search: filters.search || undefined
    })
    const pageData = res.data
    list.value = pageData.records || []
    pagination.total = pageData.total || 0
    await loadStats()
  } catch (e) {
    console.error('加载列表失败', e)
    ElMessage.error('加载列表失败')
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.sourceType = ''
  filters.vectorStatus = ''
  filters.search = ''
  pagination.page = 1
  loadData()
}

function showAddDialog() {
  addTab.value = 'upload'
  uploadForm.title = ''
  uploadForm.source = ''
  uploadForm.author = ''
  uploadForm.file = null
  manualForm.title = ''
  manualForm.content = ''
  manualForm.source = ''
  manualForm.author = ''
  manualForm.publishTime = ''
  addDialogVisible.value = true
}

function handleFileChange(file: any) {
  uploadForm.file = file.raw
  if (!uploadForm.title && file.name) {
    uploadForm.title = file.name.replace(/\.(pdf|doc|docx|txt|md)$/i, '')
  }
}

async function handleUpload() {
  if (!uploadForm.title) {
    ElMessage.warning('请输入文档标题')
    return
  }
  if (!uploadForm.file) {
    ElMessage.warning('请选择文件')
    return
  }

  uploading.value = true
  try {
    const formData = new FormData()
    formData.append('title', uploadForm.title)
    formData.append('source', uploadForm.source || '')
    formData.append('author', uploadForm.author || '')
    formData.append('file', uploadForm.file)
    await knowledgeApi.upload(formData)
    ElMessage.success('上传成功')
    addDialogVisible.value = false
    loadData()
  } catch (e) {
    console.error('上传失败', e)
    ElMessage.error('上传失败')
  } finally {
    uploading.value = false
  }
}

async function handleManualAdd() {
  if (!manualForm.title) {
    ElMessage.warning('请输入标题')
    return
  }
  if (!manualForm.content) {
    ElMessage.warning('请输入内容')
    return
  }

  submitting.value = true
  try {
    await knowledgeApi.manualAdd({
      title: manualForm.title,
      content: manualForm.content,
      source: manualForm.source || undefined,
      author: manualForm.author || undefined,
      publishTime: manualForm.publishTime || undefined
    })
    ElMessage.success('添加成功')
    addDialogVisible.value = false
    loadData()
  } catch (e) {
    console.error('添加失败', e)
    ElMessage.error('添加失败')
  } finally {
    submitting.value = false
  }
}

async function viewDetail(row: KnowledgeItem) {
  try {
    const res: any = await knowledgeApi.getById(row.id!)
    detailRow.value = res.data || row
    detailVisible.value = true
  } catch (e) {
    console.error('加载详情失败', e)
    detailRow.value = row
    detailVisible.value = true
  }
}

async function handleRevectorize(row: KnowledgeItem) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm('确定要重新向量化该知识吗？', '确认', { type: 'info' })
    row.revectorizing = true
    await knowledgeApi.revectorize(row.id)
    ElMessage.success('已发起重向量化任务')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') {
      console.error('重向量化失败', e)
      ElMessage.error('重向量化失败')
    }
  } finally {
    row.revectorizing = false
  }
}

async function handleDelete(row: KnowledgeItem) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm('确定删除该知识吗？删除后无法恢复！', '警告', { type: 'warning' })
    await knowledgeApi.delete(row.id)
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
.knowledge-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
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
.stat-card.by-source { background: linear-gradient(135deg, #48bb78 0%, #38a169 100%); }
.stat-card.status { background: linear-gradient(135deg, #ed8936 0%, #dd6b20 100%); }
.stat-card.chunks { background: linear-gradient(135deg, #4299e1 0%, #3182ce 100%); }

.stat-value {
  font-size: 28px;
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

.upload-form,
.manual-form {
  max-width: 500px;
}

.upload-icon {
  font-size: 40px;
  color: #8c939d;
  margin-bottom: 10px;
}

.upload-text {
  color: #8c939d;
}

.upload-text em {
  color: #409eff;
  font-style: normal;
}
</style>