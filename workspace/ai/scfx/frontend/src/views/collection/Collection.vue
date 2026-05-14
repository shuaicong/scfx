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
              <span>采集任务 <span class="shortcut-hint">Ctrl+R 刷新</span></span>
              <div style="display: flex; gap: 8px;">
                <el-button type="success" @click="showCreateTaskDialog">
                  <el-icon><Plus /></el-icon>
                  新建任务
                </el-button>
                <el-button type="primary" @click="handleCollect" :loading="collecting">
                  <el-icon><Refresh /></el-icon>
                  立即采集
                </el-button>
              </div>
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
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button type="primary" link size="small" @click="executeTask(row)">执行</el-button>
                <el-button type="info" link size="small" @click="viewDetail(row)">详情</el-button>
                <el-button type="warning" link size="small" @click="editTask(row)">编辑</el-button>
                <el-button type="danger" link size="small" @click="deleteTask(row)">删除</el-button>
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
              <span>采集脚本管理 <span class="shortcut-hint">Ctrl+R 刷新</span></span>
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
              <span>向量化任务 <span class="shortcut-hint">Ctrl+R 刷新</span></span>
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
              <span>执行记录 <span class="shortcut-hint">Ctrl+R 刷新</span></span>
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

    <!-- 新建任务弹窗 -->
    <el-dialog v-model="createTaskDialogVisible" title="新建采集任务" width="500px">
      <el-form :model="createTaskForm" label-width="100px">
        <el-form-item label="任务名称" required>
          <el-input v-model="createTaskForm.taskName" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="数据源" required>
          <el-select v-model="createTaskForm.sourceName" placeholder="请选择数据源" style="width: 100%;">
            <el-option label="粮信网" value="liangxinwang" />
            <el-option label="我的钢铁网" value="mysteel" />
            <el-option label="中华粮网" value="china_grain" />
            <el-option label="USDA" value="usda" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据源URL">
          <el-input v-model="createTaskForm.sourceUrl" placeholder="请输入采集URL" />
        </el-form-item>
        <el-form-item label="任务类型" required>
          <el-select v-model="createTaskForm.taskType" placeholder="请选择任务类型" style="width: 100%;">
            <el-option label="定时任务" value="scheduled" />
            <el-option label="手动任务" value="manual" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createTaskDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleCreateTask" :loading="creatingTask">创建</el-button>
      </template>
    </el-dialog>

    <!-- 任务详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="任务详情" width="600px">
      <el-descriptions :column="2" border v-if="currentTask">
        <el-descriptions-item label="任务ID">{{ currentTask.id }}</el-descriptions-item>
        <el-descriptions-item label="任务名称">{{ currentTask.taskName }}</el-descriptions-item>
        <el-descriptions-item label="数据源">{{ getSourceName(currentTask.sourceName) }}</el-descriptions-item>
        <el-descriptions-item label="数据源URL">{{ currentTask.sourceUrl || '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务类型">{{ currentTask.taskType === 'scheduled' ? '定时任务' : '手动任务' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(currentTask.status)">{{ getStatusText(currentTask.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="成功次数">{{ currentTask.successCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="失败次数">{{ currentTask.failedCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="最后执行">{{ currentTask.lastExecutionTime ? currentTask.lastExecutionTime.substring(0, 19) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="下次执行">{{ currentTask.nextExecutionTime ? currentTask.nextExecutionTime.substring(0, 19) : '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">{{ currentTask.createdAt ? currentTask.createdAt.substring(0, 19) : '-' }}</el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button type="warning" @click="startEditFromDetail">编辑</el-button>
      </template>
    </el-dialog>

    <!-- 编辑任务弹窗 -->
    <el-dialog v-model="editDialogVisible" :title="isEditing ? '编辑任务' : '新建任务'" width="500px">
      <el-form :model="editTaskForm" label-width="100px">
        <el-form-item label="任务名称" required>
          <el-input v-model="editTaskForm.taskName" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="数据源" required>
          <el-select v-model="editTaskForm.sourceName" placeholder="请选择数据源" style="width: 100%;">
            <el-option label="粮信网" value="liangxinwang" />
            <el-option label="我的钢铁网" value="mysteel" />
            <el-option label="中华粮网" value="china_grain" />
            <el-option label="USDA" value="usda" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据源URL">
          <el-input v-model="editTaskForm.sourceUrl" placeholder="请输入采集URL" />
        </el-form-item>
        <el-form-item label="任务类型" required>
          <el-select v-model="editTaskForm.taskType" placeholder="请选择任务类型" style="width: 100%;">
            <el-option label="定时任务" value="scheduled" />
            <el-option label="手动任务" value="manual" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch
            v-model="editTaskForm.status"
            active-value="enabled"
            inactive-value="disabled"
            active-text="启用"
            inactive-text="禁用"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveTask" :loading="savingTask">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Upload, Plus } from '@element-plus/icons-vue'
import { getTasks, collectLiangxinwang, createTask, updateTaskStatus, deleteTask as removeTask } from '@/api/dashboard'
import { scriptApi, executionApi, CollectionScript, type TaskExecution } from '@/api'
import { vectorizationApi } from '@/api/vectorization'
import CollectionProgress from '@/components/CollectionProgress.vue'
import TriggerBadge from '@/components/TriggerBadge.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { useShortcut } from '@/composables/useShortcut'

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
const creatingTask = ref(false)
const savingTask = ref(false)
const createTaskDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const editDialogVisible = ref(false)
const isEditing = ref(false)
const currentTask = ref<any>(null)
const createTaskForm = reactive({
  taskName: '',
  sourceName: '',
  sourceUrl: '',
  taskType: 'scheduled'
})
const editTaskForm = reactive({
  id: null as number | null,
  taskName: '',
  sourceName: '',
  sourceUrl: '',
  taskType: 'scheduled',
  status: 'enabled'
})
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
    ElMessage.error('加载任务列表失败，请稍后重试')
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
    })
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
    })
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
  ElMessageBox.confirm(`确定取消执行 #${row.scriptId} 吗？`, '提示', {
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
  const scriptId = row.id
  if (!scriptId) {
    ElMessage.error('脚本ID无效')
    return
  }
  ElMessageBox.confirm(`确定执行脚本"${row.scriptName}"吗？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info'
  }).then(async () => {
    try {
      const res: any = await scriptApi.execute(scriptId)
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

const showCreateTaskDialog = () => {
  createTaskForm.taskName = ''
  createTaskForm.sourceName = ''
  createTaskForm.sourceUrl = ''
  createTaskForm.taskType = 'scheduled'
  createTaskDialogVisible.value = true
}

const viewDetail = (row: any) => {
  currentTask.value = row
  detailDialogVisible.value = true
}

const editTask = (row: any) => {
  isEditing.value = true
  editTaskForm.id = row.id
  editTaskForm.taskName = row.taskName
  editTaskForm.sourceName = row.sourceName
  editTaskForm.sourceUrl = row.sourceUrl || ''
  editTaskForm.taskType = row.taskType || 'scheduled'
  editTaskForm.status = row.status || 'enabled'
  editDialogVisible.value = true
}

const startEditFromDetail = () => {
  if (!currentTask.value) return
  detailDialogVisible.value = false
  editTask(currentTask.value)
}

const handleSaveTask = async () => {
  if (!editTaskForm.taskName || !editTaskForm.sourceName) {
    ElMessage.warning('请填写必填项')
    return
  }
  try {
    savingTask.value = true
    // 更新任务状态
    await updateTaskStatus(editTaskForm.id!, editTaskForm.status)
    ElMessage.success('保存成功')
    editDialogVisible.value = false
    loadTasks()
    loadTaskStats()
  } catch (error) {
    ElMessage.error('保存失败')
  } finally {
    savingTask.value = false
  }
}

const deleteTask = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定删除任务"${row.taskName}"吗？`, '警告', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await removeTask(row.id)
    ElMessage.success('删除成功')
    loadTasks()
    loadTaskStats()
  } catch (error: any) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const handleCreateTask = async () => {
  if (!createTaskForm.taskName || !createTaskForm.sourceName) {
    ElMessage.warning('请填写必填项')
    return
  }
  try {
    creatingTask.value = true
    await createTask({
      taskName: createTaskForm.taskName,
      sourceName: createTaskForm.sourceName,
      sourceUrl: createTaskForm.sourceUrl,
      taskType: createTaskForm.taskType
    })
    ElMessage.success('任务创建成功')
    createTaskDialogVisible.value = false
    loadTasks()
    loadTaskStats()
  } catch (error) {
    ElMessage.error('创建失败')
  } finally {
    creatingTask.value = false
  }
  }
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

// Keyboard shortcuts
const refreshCurrentTab = () => {
  if (activeTab.value === 'tasks') {
    loadTasks()
    loadTaskStats()
  } else if (activeTab.value === 'scripts') {
    loadScripts()
    loadScriptStats()
  } else if (activeTab.value === 'executions') {
    loadExecutions()
  } else if (activeTab.value === 'vectorization') {
    loadVectorStats()
    loadVectorTasks()
  }
  ElMessage.info('已刷新')
}

const executeFirstTask = () => {
  if (tasks.value.length > 0 && activeTab.value === 'tasks') {
    executeTask(tasks.value[0])
  }
}

useShortcut('Enter', executeFirstTask, { ctrl: true })
useShortcut('r', refreshCurrentTab, { ctrl: true })
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

.shortcut-hint {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
  font-weight: normal;
}

.btn-shortcut {
  font-size: 10px;
  color: #909399;
  margin-left: 2px;
}
</style>