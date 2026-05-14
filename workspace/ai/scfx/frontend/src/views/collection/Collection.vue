<template>
  <div class="collection">
    <CollectionProgress ref="progressDrawer" />
    <el-tabs v-model="activeTab" class="collection-tabs">
      <el-tab-pane label="采集任务" name="tasks">
        <!-- 统计卡片 -->
        <el-row :gutter="20" class="stats-cards">
          <el-col :span="6">
            <div class="stat-card stat-card-purple">
              <div class="stat-value">{{ taskStats.total }}</div>
              <div class="stat-label">任务总数</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card stat-card-green">
              <div class="stat-value">{{ taskStats.enabled }}</div>
              <div class="stat-label">启用中</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card stat-card-orange">
              <div class="stat-value">{{ taskStats.todayExec }}</div>
              <div class="stat-label">今日执行</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card stat-card-red">
              <div class="stat-value">{{ taskStats.failed }}</div>
              <div class="stat-label">失败数</div>
            </div>
          </el-col>
        </el-row>

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
        <!-- 统计卡片 -->
        <el-row :gutter="20" class="stats-cards">
          <el-col :span="8">
            <div class="stat-card stat-card-purple">
              <div class="stat-value">{{ scriptStats.total }}</div>
              <div class="stat-label">脚本总数</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="stat-card stat-card-green">
              <div class="stat-value">{{ scriptStats.enabled }}</div>
              <div class="stat-label">启用中</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="stat-card stat-card-red">
              <div class="stat-value">{{ scriptStats.disabled }}</div>
              <div class="stat-label">已禁用</div>
            </div>
          </el-col>
        </el-row>

        <el-card>
          <template #header>
            <div class="card-header">
              <span>采集脚本管理</span>
              <div class="header-buttons">
                <el-button type="primary" @click="showUploadDialog">
                  <el-icon><Upload /></el-icon>
                  上传文件
                </el-button>
                <el-button type="success" @click="showCreateDialog">
                  <el-icon><Plus /></el-icon>
                  新建脚本
                </el-button>
              </div>
            </div>
          </template>

          <el-table :data="scriptList" v-loading="scriptLoading" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="scriptName" label="脚本名称" min-width="150" />
            <el-table-column prop="triggerType" label="触发方式" width="120">
              <template #default="{ row }">
                <TriggerBadge :type="row.triggerType" />
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="row.status === 'enabled' ? 'success' : 'info'">
                  {{ row.status === 'enabled' ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="executionCount" label="执行次数" width="100">
              <template #default="{ row }">
                {{ row.executionCount || 0 }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="executeScript(row)">执行</el-button>
                <el-button type="warning" link size="small" @click="editScript(row)">编辑</el-button>
                <el-button type="info" link size="small" @click="showScriptDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="scriptPagination.page"
            v-model:page-size="scriptPagination.size"
            :total="scriptPagination.total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleScriptSizeChange"
            @current-change="handleScriptPageChange"
            style="margin-top: 20px; justify-content: flex-end;"
          />
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="向量化监控" name="vectorization">
        <!-- 统计卡片 -->
        <el-row :gutter="20" class="stats-cards">
          <el-col :span="6">
            <div class="stat-card stat-card-purple">
              <div class="stat-value">{{ vectorStats.pending }}</div>
              <div class="stat-label">待处理</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card stat-card-blue">
              <div class="stat-value">{{ vectorStats.processing }}</div>
              <div class="stat-label">处理中</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card stat-card-green">
              <div class="stat-value">{{ vectorStats.vectorized }}</div>
              <div class="stat-label">已完成</div>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="stat-card stat-card-red">
              <div class="stat-value">{{ vectorStats.failed }}</div>
              <div class="stat-label">失败</div>
            </div>
          </el-col>
        </el-row>

        <el-card>
          <template #header>
            <div class="card-header">
              <span>向量化任务</span>
              <div class="header-buttons">
                <el-button type="primary" @click="triggerAllPending" :loading="vectorLoading">
                  <el-icon><Refresh /></el-icon>
                  触发全部待处理
                </el-button>
                <el-button type="warning" @click="retryAllFailed" :loading="vectorLoading">
                  <el-icon><Refresh /></el-icon>
                  重试全部失败
                </el-button>
              </div>
            </div>
          </template>

          <el-table :data="vectorTasks" v-loading="vectorLoading" stripe>
            <el-table-column prop="categoryId" label="分类" width="100" />
            <el-table-column prop="totalCount" label="总数" width="80" />
            <el-table-column prop="processedCount" label="已处理" width="80">
              <template #default="{ row }">
                <span style="color: #67c23a;">{{ row.processedCount }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="failedCount" label="失败" width="80">
              <template #default="{ row }">
                <span style="color: #f56c6c;">{{ row.failedCount }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="getVectorStatusType(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="160">
              <template #default="{ row }">
                {{ row.createdAt ? row.createdAt.substring(0, 19) : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="triggerVectorization(row)">触发</el-button>
                <el-button type="warning" link size="small" @click="retryVectorization(row)">重试</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="执行记录" name="executions">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>执行记录</span>
            </div>
          </template>

          <el-table :data="executionList" v-loading="executionLoading" stripe>
            <el-table-column prop="scriptName" label="脚本名称" min-width="150" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <StatusBadge :status="row.status" />
              </template>
            </el-table-column>
            <el-table-column prop="startTime" label="开始时间" width="180">
              <template #default="{ row }">
                {{ row.startTime ? formatTime(row.startTime) : '-' }}
              </template>
            </el-table-column>
            <el-table-column prop="durationMs" label="耗时" width="120">
              <template #default="{ row }">
                {{ row.durationMs ? `${row.durationMs}ms` : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="viewExecutionLogs(row)">查看日志</el-button>
                <el-button
                  v-if="row.status === 'running' || row.status === 'pending'"
                  type="danger"
                  link
                  size="small"
                  @click="cancelExecution(row)"
                >
                  取消
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
            v-model:current-page="executionPagination.page"
            v-model:page-size="executionPagination.size"
            :total="executionPagination.total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleExecutionSizeChange"
            @current-change="handleExecutionPageChange"
            style="margin-top: 20px; justify-content: flex-end;"
          />
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Upload, Plus } from '@element-plus/icons-vue'
import { getTasks, collectLiangxinwang } from '@/api/dashboard'
import { scriptApi, executionApi, CollectionScript, type TaskExecution } from '@/api'
import { vectorizationApi } from '@/api/vectorization'
import CollectionProgress from '@/components/CollectionProgress.vue'
import TriggerBadge from '@/components/TriggerBadge.vue'
import StatusBadge from '@/components/StatusBadge.vue'

interface TaskStats {
  total: number
  enabled: number
  todayExec: number
  failed: number
}

interface ScriptStats {
  total: number
  enabled: number
  disabled: number
}

const loading = ref(false)
const collecting = ref(false)
const tasks = ref<any[]>([])
const progressDrawer = ref<InstanceType<typeof CollectionProgress>>()

const activeTab = ref('tasks')

const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

const taskStats = reactive<TaskStats>({
  total: 0,
  enabled: 0,
  todayExec: 0,
  failed: 0
})

// 脚本管理相关
const scriptLoading = ref(false)
const scriptList = ref<CollectionScript[]>([])
const scriptStats = reactive<ScriptStats>({
  total: 0,
  enabled: 0,
  disabled: 0
})
const scriptPagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

// 执行记录相关
const executionLoading = ref(false)
const executionList = ref<TaskExecution[]>([])
const executionPagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

// 向量化监控相关
const vectorLoading = ref(false)
const vectorStats = reactive({
  pending: 0,
  processing: 0,
  vectorized: 0,
  failed: 0,
  total: 0
})
const vectorTasks = ref<VectorTask[]>([])

interface VectorTask {
  id?: number
  categoryId: number
  status: string
  totalCount?: number
  processedCount?: number
  failedCount?: number
  createdAt?: string
}

const loadTaskStats = async () => {
  try {
    const res: any = await scriptApi.stats()
    if (res.code === 200) {
      taskStats.total = res.data.total || 0
      taskStats.enabled = res.data.enabled || 0
      taskStats.todayExec = res.data.todayExec || 0
      taskStats.failed = res.data.failed || 0
    }
  } catch (error) {
    console.error('加载任务统计失败', error)
  }
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

const loadScriptStats = async () => {
  try {
    const res: any = await scriptApi.stats()
    if (res.code === 200 && res.data) {
      scriptStats.total = res.data.total || 0
      scriptStats.enabled = res.data.enabled || 0
      scriptStats.disabled = res.data.disabled || 0
    }
  } catch (error) {
    console.error('加载脚本统计失败', error)
  }
}

let loadScriptsController: AbortController | null = null

const loadScripts = async () => {
  loadScriptsController?.abort()
  loadScriptsController = new AbortController()
  scriptLoading.value = true
  try {
    const res: any = await scriptApi.list({
      page: scriptPagination.page,
      size: scriptPagination.size
    }, { signal: loadScriptsController.signal })
    if (res.code === 200) {
      scriptList.value = res.data.records || []
      scriptPagination.total = res.data.total || 0
    }
  } catch (error) {
    if ((error as Error).name !== 'CanceledError') {
      console.error('加载脚本列表失败', error)
      ElMessage.error('加载脚本列表失败，请稍后重试')
    }
  } finally {
    scriptLoading.value = false
  }
}

let loadExecutionsController: AbortController | null = null

const loadExecutions = async () => {
  loadExecutionsController?.abort()
  loadExecutionsController = new AbortController()
  executionLoading.value = true
  try {
    const res: any = await executionApi.list(0, {
      page: executionPagination.page,
      size: executionPagination.size
    }, { signal: loadExecutionsController.signal })
    if (res.code === 200) {
      executionList.value = res.data.records || []
      executionPagination.total = res.data.total || 0
    }
  } catch (e: any) {
    if (e?.message?.includes('Cancel')) return
    console.error('加载执行记录失败', e)
    ElMessage.error('加载执行记录失败，请稍后重试')
  } finally {
    executionLoading.value = false
  }
}

// 向量化监控
const loadVectorStats = async () => {
  try {
    const res: any = await vectorizationApi.getStats()
    if (res.code === 200 && res.data) {
      vectorStats.pending = res.data.pending || 0
      vectorStats.processing = res.data.processing || 0
      vectorStats.vectorized = res.data.vectorized || 0
      vectorStats.failed = res.data.failed || 0
      vectorStats.total = res.data.total || 0
    }
  } catch (error) {
    console.error('加载向量化统计失败', error)
  }
}

const loadVectorTasks = async () => {
  try {
    vectorLoading.value = true
    const res: any = await vectorizationApi.getTasks(1, 100)
    if (res.code === 200) {
      vectorTasks.value = res.data || []
    }
  } catch (error) {
    console.error('加载向量化任务失败', error)
    ElMessage.error('加载向量化任务失败')
  } finally {
    vectorLoading.value = false
  }
}

const triggerAllPending = async () => {
  try {
    vectorLoading.value = true
    const res: any = await vectorizationApi.triggerBatch([])
    if (res.code === 200) {
      ElMessage.success('已触发全部待处理任务')
      loadVectorStats()
      loadVectorTasks()
    }
  } catch (error) {
    ElMessage.error('触发失败')
  } finally {
    vectorLoading.value = false
  }
}

const retryAllFailed = async () => {
  try {
    vectorLoading.value = true
    // Retry failed tasks by triggering their category
    const failedTasks = vectorTasks.value.filter(t => t.status === 'failed')
    for (const task of failedTasks) {
      await vectorizationApi.retry(task.id!)
    }
    ElMessage.success('已重试全部失败任务')
    loadVectorStats()
    loadVectorTasks()
  } catch (error) {
    ElMessage.error('重试失败')
  } finally {
    vectorLoading.value = false
  }
}

const triggerVectorization = async (row: VectorTask) => {
  try {
    const res: any = await vectorizationApi.trigger(row.categoryId)
    if (res.code === 200) {
      ElMessage.success('已触发向量化')
      loadVectorStats()
      loadVectorTasks()
    } else {
      ElMessage.error(res.message || '触发失败')
    }
  } catch (error) {
    console.error('触发向量化失败', error)
    ElMessage.error('触发失败')
  }
}

const retryVectorization = async (row: VectorTask) => {
  try {
    const res: any = await vectorizationApi.retry(row.id!)
    if (res.code === 200) {
      ElMessage.success('已重试')
      loadVectorStats()
      loadVectorTasks()
    } else {
      ElMessage.error(res.message || '重试失败')
    }
  } catch (error) {
    console.error('重试向量化失败', error)
    ElMessage.error('重试失败')
  }
}

const getVectorStatusType = (status: string) => {
  const map: Record<string, string> = {
    pending: 'info',
    processing: 'primary',
    completed: 'success',
    failed: 'danger'
  }
  return map[status] || 'info'
}

const formatTime = (time: string) => {
  if (!time) return '-'
  return time.substring(0, 19)
}

const viewExecutionLogs = (row: TaskExecution) => {
  ElMessage.info(`查看执行日志: ${row.executionId}`)
}

const cancelExecution = (row: TaskExecution) => {
  ElMessageBox.confirm(`确定取消执行"${row.scriptName}"吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const res: any = await executionApi.cancel(row.executionId)
      if (res.code === 200) {
        ElMessage.success('已取消执行')
        loadExecutions()
      }
    } catch (error) {
      ElMessage.error('取消执行失败')
    }
  }).catch(() => {})
}

const handleExecutionSizeChange = (size: number) => {
  executionPagination.size = size
  loadExecutions()
}

const handleExecutionPageChange = (page: number) => {
  executionPagination.page = page
  loadExecutions()
}

async function executeScript(row: CollectionScript) {
  ElMessageBox.confirm(`确定执行脚本"${row.scriptName}"吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info'
  }).then(async () => {
    try {
      const res: any = await scriptApi.execute(row.id)
      if (res.code === 200) {
        ElMessage.success('脚本执行已触发')
        loadScripts()
      }
    } catch (error) {
      ElMessage.error('执行失败')
    }
  }).catch(() => {})
}

const showUploadDialog = () => {
  ElMessage.info('上传功能开发中')
}

const showCreateDialog = () => {
  ElMessage.info('创建功能开发中')
}

const editScript = (row: any) => {
  ElMessage.info('编辑功能开发中')
}

const showScriptDetail = (row: any) => {
  ElMessage.info('详情功能开发中')
}

const handleScriptSizeChange = (size: number) => {
  scriptPagination.size = size
  loadScripts()
}

const handleScriptPageChange = (page: number) => {
  scriptPagination.page = page
  loadScripts()
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

interface TaskRow {
  id: number
  taskName: string
  executionId?: string
}

async function executeTask(row: TaskRow) {
  ElMessageBox.confirm(`确定执行任务"${row.taskName}"吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info'
  }).then(async () => {
    try {
      const res: any = await scriptApi.execute(row.id)
      if (res.code === 200 && res.data?.executionId) {
        ElMessage.success('任务已触发执行')
        progressDrawer.value?.open(res.data.executionId)
      }
    } catch (error) {
      ElMessage.error('触发执行失败')
    }
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
  loadTaskStats()
})

watch(activeTab, (newTab) => {
  if (newTab === 'scripts') {
    loadScripts()
    loadScriptStats()
  } else if (newTab === 'executions') {
    loadExecutions()
  } else if (newTab === 'vectorization') {
    loadVectorStats()
    loadVectorTasks()
  }
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

.header-buttons {
  display: flex;
  gap: 10px;
}

.vectorization-placeholder {
  padding: 60px 0;
  text-align: center;
}

.stats-cards {
  margin-bottom: 20px;
}

.stat-card {
  padding: 20px;
  border-radius: 8px;
  color: #fff;
  text-align: center;
}

.stat-card-purple {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.stat-card-green {
  background: linear-gradient(135deg, #48bb78 0%, #38a169 100%);
}

.stat-card-orange {
  background: linear-gradient(135deg, #ed8936 0%, #dd6b20 100%);
}

.stat-card-blue {
  background: linear-gradient(135deg, #4299e1 0%, #3182ce 100%);
}

.stat-card-red {
  background: linear-gradient(135deg, #e53e3e 0%, #c53030 100%);
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  opacity: 0.9;
}
</style>