<template>
  <div class="logs">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>采集日志</span>
          <el-space>
            <el-select v-model="filters.level" placeholder="日志级别" clearable style="width: 120px;">
              <el-option label="全部" value="" />
              <el-option label="ERROR" value="ERROR" />
              <el-option label="WARN" value="WARN" />
              <el-option label="INFO" value="INFO" />
              <el-option label="DEBUG" value="DEBUG" />
            </el-select>
            <el-button @click="loadLogs" :loading="loading">刷新</el-button>
          </el-space>
        </div>
      </template>

      <el-table :data="logs" v-loading="loading" stripe max-height="600">
        <el-table-column prop="createdAt" label="时间" width="180">
          <template #default="{ row }">
            {{ row.createdAt ? row.createdAt.substring(0, 19) : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="level" label="级别" width="100">
          <template #default="{ row }">
            <el-tag :type="getLevelType(row.level)" size="small">{{ row.level }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="taskName" label="任务" width="150" />
        <el-table-column prop="source" label="来源" width="120" />
        <el-table-column prop="message" label="日志内容" min-width="400" show-overflow-tooltip />
      </el-table>

      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[50, 100, 200, 500]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
        style="margin-top: 20px; justify-content: flex-end;"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getLogs } from '@/api/dashboard'

const loading = ref(false)
const logs = ref<any[]>([])

const filters = reactive({
  level: ''
})

const pagination = reactive({
  page: 1,
  size: 50,
  total: 0
})

const loadLogs = async () => {
  try {
    loading.value = true
    const res: any = await getLogs({
      page: pagination.page,
      size: pagination.size,
      level: filters.level || undefined
    })
    if (res.code === 200) {
      logs.value = res.data.records || []
      pagination.total = res.data.total || 0
    }
  } catch (error) {
    console.error('加载日志失败', error)
  } finally {
    loading.value = false
  }
}

const getLevelType = (level: string) => {
  const map: Record<string, string> = {
    ERROR: 'danger',
    WARN: 'warning',
    INFO: 'primary',
    DEBUG: 'info'
  }
  return map[level] || 'info'
}

const handleSizeChange = (size: number) => {
  pagination.size = size
  loadLogs()
}

const handlePageChange = (page: number) => {
  pagination.page = page
  loadLogs()
}

onMounted(() => {
  loadLogs()
})
</script>
