<template>
  <div class="sdk-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>SDK 管理</span>
          <el-button type="primary" @click="loadData">
            <el-icon><Refresh /></el-icon> 刷新
          </el-button>
        </div>
      </template>

      <!-- 统计卡片 -->
      <el-row :gutter="20" class="stats-row">
        <el-col :span="6">
          <div class="stat-card total">
            <div class="stat-value">{{ stats.total }}</div>
            <div class="stat-label">总数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card online">
            <div class="stat-value">{{ stats.online }}</div>
            <div class="stat-label">在线</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card offline">
            <div class="stat-value">{{ stats.offline }}</div>
            <div class="stat-label">离线</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card disabled">
            <div class="stat-value">{{ stats.disabled }}</div>
            <div class="stat-label">禁用</div>
          </div>
        </el-col>
      </el-row>

      <!-- 筛选 -->
      <el-form :inline="true" class="filter-form">
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="全部" value="" />
            <el-option label="在线" value="online" />
            <el-option label="离线" value="offline" />
            <el-option label="禁用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="来源">
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
        <el-table-column prop="collectorName" label="采集器名称" min-width="150" />
        <el-table-column prop="sdkVersion" label="SDK版本" width="100" />
        <el-table-column prop="source" label="来源" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.source === 'liangxin'" type="success">粮信网</el-tag>
            <el-tag v-else-if="row.source === 'mysteel'" type="primary">我的钢铁网</el-tag>
            <el-tag v-else-if="row.source === 'chinagrain'" type="warning">中华粮网</el-tag>
            <el-tag v-else>{{ row.source }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="subject" label="采集主体" width="80" />
        <el-table-column prop="collType" label="采集类型" width="100" />
        <el-table-column prop="collObject" label="采集对象" width="100" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'online'" type="success">在线</el-tag>
            <el-tag v-else-if="row.status === 'offline'" type="info">离线</el-tag>
            <el-tag v-else-if="row.status === 'disabled'" type="danger">禁用</el-tag>
            <el-tag v-else>{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="instanceCount" label="实例数" width="70" />
        <el-table-column prop="lastHeartbeat" label="最后心跳" width="160">
          <template #default="{ row }">
            {{ row.lastHeartbeat ? formatTime(row.lastHeartbeat) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'disabled'" type="danger" size="small" @click="handleDisable(row)">
              禁用
            </el-button>
            <el-button v-else type="success" size="small" @click="handleEnable(row)">
              启用
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

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" title="编辑采集器" width="500px">
      <el-form :model="currentRow" label-width="100px">
        <el-form-item label="采集器名称">
          <el-input v-model="currentRow.collectorName" />
        </el-form-item>
        <el-form-item label="来源">
          <el-select v-model="currentRow.source" style="width: 100%">
            <el-option label="粮信网" value="liangxin" />
            <el-option label="我的钢铁网" value="mysteel" />
            <el-option label="中华粮网" value="chinagrain" />
          </el-select>
        </el-form-item>
        <el-form-item label="采集主体">
          <el-select v-model="currentRow.subject" style="width: 100%">
            <el-option label="玉米" value="corn" />
            <el-option label="小麦" value="wheat" />
            <el-option label="稻米" value="rice" />
            <el-option label="大豆" value="soybean" />
          </el-select>
        </el-form-item>
        <el-form-item label="采集类型">
          <el-select v-model="currentRow.collType" style="width: 100%">
            <el-option label="模拟登录爬取" value="login_crawl" />
            <el-option label="公开页面爬取" value="public_crawl" />
            <el-option label="接口采集" value="api_collect" />
            <el-option label="文件下载" value="file_download" />
          </el-select>
        </el-form-item>
        <el-form-item label="采集对象">
          <el-select v-model="currentRow.collObject" style="width: 100%">
            <el-option label="日报" value="daily_report" />
            <el-option label="周报" value="weekly_report" />
            <el-option label="月报" value="monthly_report" />
            <el-option label="价格" value="price" />
            <el-option label="资讯" value="news" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="currentRow.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { collectorApi, CollectorInfo, CollectorStats } from '../../api'

const loading = ref(false)
const list = ref<CollectorInfo[]>([])
const stats = ref<CollectorStats>({ total: 0, online: 0, offline: 0, disabled: 0 })

const filters = reactive({
  status: '',
  source: ''
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const dialogVisible = ref(false)
const currentRow = ref<Partial<CollectorInfo>>({})

function formatTime(time: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

async function loadStats() {
  try {
    const res = await collectorApi.stats()
    stats.value = (res as any).data as CollectorStats
  } catch (e) {
    console.error('加载统计失败', e)
  }
}

async function loadData() {
  loading.value = true
  try {
    const res = await collectorApi.list({
      page: pagination.page,
      size: pagination.size,
      status: filters.status || undefined,
      source: filters.source || undefined
    })
    list.value = (res as any).data || []
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

function handleEdit(row: CollectorInfo) {
  currentRow.value = { ...row }
  dialogVisible.value = true
}

async function handleSave() {
  if (!currentRow.value.id) return
  try {
    await collectorApi.update(currentRow.value.id, currentRow.value as any)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadData()
  } catch (e) {
    console.error('保存失败', e)
  }
}

async function handleEnable(row: CollectorInfo) {
  if (!row.id) return
  try {
    await collectorApi.enable(row.id)
    ElMessage.success('已启用')
    loadData()
  } catch (e) {
    console.error('启用失败', e)
  }
}

async function handleDisable(row: CollectorInfo) {
  if (!row.id) return
  try {
    await collectorApi.disable(row.id)
    ElMessage.success('已禁用')
    loadData()
  } catch (e) {
    console.error('禁用失败', e)
  }
}

async function handleDelete(row: CollectorInfo) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm('确定删除该采集器？', '提示', {
      type: 'warning'
    })
    await collectorApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') {
      console.error('删除失败', e)
    }
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.sdk-container {
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
  border-radius: 8px;
  text-align: center;
  color: #fff;
}

.stat-card.total { background: #409EFF; }
.stat-card.online { background: #67C23A; }
.stat-card.offline { background: #909399; }
.stat-card.disabled { background: #F56C6C; }

.stat-value {
  font-size: 28px;
  font-weight: bold;
}

.stat-label {
  font-size: 14px;
  margin-top: 8px;
}

.filter-form {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
