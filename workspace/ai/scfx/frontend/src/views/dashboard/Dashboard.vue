<template>
  <div class="dashboard">
    <!-- 数据源统计 -->
    <div v-for="source in sourceStats" :key="source.sourceName" class="source-section">
      <div class="source-header">
        <span class="source-name">{{ source.displayName || source.sourceName }}</span>
        <el-button type="primary" size="small" @click="handleCollect(source.sourceName)">
          <el-icon><Refresh /></el-icon>
          立即采集
        </el-button>
      </div>
      <el-row :gutter="20" class="stat-row">
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-icon" style="background: #67c23a;">
              <el-icon :size="24"><SuccessFilled /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-number text-success">{{ source.todaySuccess || 0 }}</div>
              <div class="stat-label">今日成功</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-icon" style="background: #f56c6c;">
              <el-icon :size="24"><CircleCloseFilled /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-number text-danger">{{ source.todayFailed || 0 }}</div>
              <div class="stat-label">今日失败</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-icon" style="background: #409eff;">
              <el-icon :size="24"><Document /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-number text-primary">{{ source.todayReports || 0 }}</div>
              <div class="stat-label">报告数</div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-icon" style="background: #e6a23c;">
              <el-icon :size="24"><DataLine /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-number" style="color: #e6a23c;">{{ source.successRate || 100 }}%</div>
              <div class="stat-label">成功率</div>
            </div>
          </div>
        </el-col>
      </el-row>
      <div class="source-footer">
        <span class="last-time">最后采集: {{ formatTime(source.lastCollectTime) || '从未采集' }}</span>
      </div>
    </div>

    <!-- 系统概览 -->
    <el-row :gutter="20" style="margin-top: 30px;">
      <el-col :span="16">
        <el-card class="card">
          <template #header>
            <div class="card-header">
              <span>最近日志</span>
              <el-button type="primary" link @click="$router.push('/logs')">查看更多</el-button>
            </div>
          </template>
          <el-timeline>
            <el-timeline-item
              v-for="log in recentLogs"
              :key="log.id"
              :type="getLogType(log.level)"
              :timestamp="formatLogTime(log.createdAt)"
              placement="top"
            >
              <p>{{ log.message }}</p>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-if="!recentLogs.length" description="暂无日志" />
        </el-card>
      </el-col>

      <el-col :span="8">
        <!-- 告警统计 -->
        <el-card class="card">
          <template #header>
            <div class="card-header">
              <span>告警统计</span>
            </div>
          </template>
          <div class="alert-stats">
            <div class="alert-item">
              <span class="alert-label">严重</span>
              <el-tag type="danger">{{ alertStats.critical || 0 }}</el-tag>
            </div>
            <div class="alert-item">
              <span class="alert-label">错误</span>
              <el-tag type="warning">{{ alertStats.error || 0 }}</el-tag>
            </div>
            <div class="alert-item">
              <span class="alert-label">警告</span>
              <el-tag type="info">{{ alertStats.warning || 0 }}</el-tag>
            </div>
            <div class="alert-item">
              <span class="alert-label">信息</span>
              <el-tag>{{ alertStats.info || 0 }}</el-tag>
            </div>
          </div>
        </el-card>

        <!-- 快速操作 -->
        <el-card class="card" style="margin-top: 20px;">
          <template #header>
            <span>快速操作</span>
          </template>
          <el-space direction="vertical" :size="10" style="width: 100%;">
            <el-button style="width: 100%;" @click="$router.push('/collection')">
              <el-icon><List /></el-icon>
              采集管理
            </el-button>
            <el-button style="width: 100%;">
              <el-icon><Setting /></el-icon>
              系统设置
            </el-button>
          </el-space>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getDashboard, collectLiangxinwang } from '@/api/dashboard'

const sourceStats = ref<any[]>([])
const recentLogs = ref<any[]>([])
const alertStats = ref<any>({})

const loadDashboard = async () => {
  try {
    const res: any = await getDashboard()
    if (res.code === 200) {
      const data = res.data
      sourceStats.value = data.sourceStats || []
      recentLogs.value = data.recentLogs || []
      alertStats.value = data.pendingAlerts || {}
    }
  } catch (error) {
    console.error('加载仪表板数据失败', error)
  }
}

const handleCollect = async (sourceName: string) => {
  try {
    await collectLiangxinwang()
    ElMessage.success('采集任务已启动')
    setTimeout(loadDashboard, 3000)
  } catch (error) {
    ElMessage.error('启动采集失败')
  }
}

const getLogType = (level: string) => {
  const map: Record<string, string> = {
    ERROR: 'danger',
    WARN: 'warning',
    INFO: 'primary',
    DEBUG: 'info'
  }
  return map[level] || 'info'
}

const formatTime = (time: string) => {
  if (!time) return ''
  return time.substring(0, 16).replace('T', ' ')
}

const formatLogTime = (time: string) => {
  if (!time) return ''
  return time.substring(11, 19)
}

onMounted(() => {
  loadDashboard()
})
</script>

<style scoped>
.source-section {
  background: white;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.source-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
  padding-bottom: 10px;
  border-bottom: 1px solid #eee;
}

.source-name {
  font-size: 1.1rem;
  font-weight: bold;
  color: #303133;
}

.source-footer {
  margin-top: 10px;
  text-align: right;
  color: #909399;
  font-size: 0.85rem;
}

.stat-row {
  margin-bottom: 10px;
}

.stat-card {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 15px;
  display: flex;
  align-items: center;
  gap: 15px;
}

.stat-icon {
  width: 50px;
  height: 50px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.stat-content {
  flex: 1;
}

.stat-number {
  font-size: 1.5rem;
  font-weight: bold;
  line-height: 1.2;
}

.stat-label {
  color: #909399;
  font-size: 0.85rem;
  margin-top: 3px;
}

.text-primary {
  color: #409eff;
}

.text-success {
  color: #67c23a;
}

.text-danger {
  color: #f56c6c;
}

.card {
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.alert-stats {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.alert-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.alert-label {
  color: #606266;
}
</style>
