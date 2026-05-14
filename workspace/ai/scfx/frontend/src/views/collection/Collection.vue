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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getTasks, collectLiangxinwang } from '@/api/dashboard'
import { scriptApi, executionApi } from '@/api'
import CollectionProgress from '@/components/CollectionProgress.vue'

interface TaskStats {
  total: number
  enabled: number
  todayExec: number
  failed: number
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