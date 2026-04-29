<template>
  <div class="execution-list">
    <el-table :data="executions" v-loading="loading" stripe>
      <el-table-column prop="executionId" label="执行ID" width="120" show-overflow-tooltip />
      <el-table-column prop="startTime" label="开始时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.startTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)">{{ getStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="durationMs" label="时长" width="100">
        <template #default="{ row }">
          {{ formatDuration(row.durationMs) }}
        </template>
      </el-table-column>
      <el-table-column prop="triggerType" label="触发方式" width="100">
        <template #default="{ row }">
          {{ getTriggerText(row.triggerType) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }">
          <el-button link type="primary" @click="showDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-if="total > 0"
      layout="prev, pager, next"
      :total="total"
      :page-size="pageSize"
      :current-page="currentPage"
      @current-change="loadData"
      style="margin-top: 16px; justify-content: center"
    />

    <el-dialog v-model="showDialog" title="执行详情" width="800px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="执行ID">{{ currentExecution?.executionId }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusType(currentExecution?.status || '')">
            {{ getStatusText(currentExecution?.status || '') }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ formatTime(currentExecution?.startTime) }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ formatTime(currentExecution?.endTime) }}</el-descriptions-item>
        <el-descriptions-item label="执行时长">{{ formatDuration(currentExecution?.durationMs) }}</el-descriptions-item>
        <el-descriptions-item label="触发方式">{{ getTriggerText(currentExecution?.triggerType || '') }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="currentExecution?.errorMessage" class="error-message">
        <div class="error-title">错误信息</div>
        <pre>{{ currentExecution.errorMessage }}</pre>
      </div>

      <ExecutionLogViewer v-if="currentExecution?.executionId" :execution-id="currentExecution.executionId" :logs="logs" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { executionApi } from '@/api'
import ExecutionLogViewer from '@/components/ExecutionLogViewer.vue'
import type { TaskExecution, ExecutionLog } from '@/api'

const props = defineProps<{ scriptId: number }>()

const executions = ref<TaskExecution[]>([])
const logs = ref<ExecutionLog[]>([])
const loading = ref(false)
const showDialog = ref(false)
const currentExecution = ref<TaskExecution>()
const total = ref(0)
const currentPage = ref(1)
const pageSize = 10

onMounted(() => loadData())

async function loadData() {
  loading.value = true
  try {
    const res = await executionApi.list(props.scriptId, { page: currentPage.value, size: pageSize })
    executions.value = res.data.records || []
    total.value = res.data.total || 0
  } finally {
    loading.value = false
  }
}

async function showDetail(row: TaskExecution) {
  currentExecution.value = row
  try {
    const logsRes = await executionApi.logs(row.executionId)
    logs.value = logsRes.data
  } catch (e) {
    logs.value = []
  }
  showDialog.value = true
}

function formatTime(time?: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

function formatDuration(ms?: number) {
  if (!ms) return '-'
  const s = Math.floor(ms / 1000)
  if (s < 60) return `${s}秒`
  const m = Math.floor(s / 60)
  return `${m}分${s % 60}秒`
}

function getStatusType(status: string) {
  switch (status) {
    case 'success': return 'success'
    case 'failed': return 'danger'
    case 'running': return 'warning'
    case 'cancelled': return 'info'
    default: return ''
  }
}

function getStatusText(status: string) {
  switch (status) {
    case 'success': return '成功'
    case 'failed': return '失败'
    case 'running': return '进行中'
    case 'cancelled': return '已取消'
    case 'pending': return '等待'
    default: return status
  }
}

function getTriggerText(trigger: string) {
  switch (trigger) {
    case 'manual': return '手动'
    case 'scheduled': return '定时'
    case 'api': return 'API'
    default: return trigger
  }
}
</script>

<style scoped>
.error-message {
  margin: 16px 0;
  padding: 12px;
  background: #fff2f0;
  border-radius: 4px;
}
.error-title {
  font-weight: 500;
  margin-bottom: 8px;
}
</style>
