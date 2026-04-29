<template>
  <div class="dashboard">
    <!-- 数据源卡片 -->
    <div v-for="source in sourceStats" :key="source.sourceName" class="source-section">
      <div class="source-header">
        <div class="source-info">
          <div class="source-badge">
            <el-icon><Connection /></el-icon>
          </div>
          <div class="source-text">
            <span class="source-name">{{ source.displayName || source.sourceName }}</span>
            <span class="source-status" :class="getSourceStatusClass(source)">
              {{ getSourceStatus(source) }}
            </span>
          </div>
        </div>
        <el-button type="primary" class="collect-btn" @click="handleCollect(source.sourceName)">
          <el-icon class="btn-icon"><Refresh /></el-icon>
          立即采集
        </el-button>
      </div>

      <el-row :gutter="20" class="stat-row">
        <el-col :span="6">
          <div class="stat-card success">
            <div class="stat-inner">
              <div class="stat-icon">
                <el-icon><SuccessFilled /></el-icon>
              </div>
              <div class="stat-data">
                <div class="stat-value">{{ source.todaySuccess || 0 }}</div>
                <div class="stat-label">今日成功</div>
              </div>
              <div class="stat-trend up">
                <el-icon><CaretTop /></el-icon>
                <span>{{ source.successRate || 100 }}%</span>
              </div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card danger">
            <div class="stat-inner">
              <div class="stat-icon">
                <el-icon><CircleCloseFilled /></el-icon>
              </div>
              <div class="stat-data">
                <div class="stat-value">{{ source.todayFailed || 0 }}</div>
                <div class="stat-label">今日失败</div>
              </div>
              <div class="stat-trend down" v-if="source.todayFailed > 0">
                <el-icon><CaretBottom /></el-icon>
                <span>异常</span>
              </div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card primary">
            <div class="stat-inner">
              <div class="stat-icon">
                <el-icon><Document /></el-icon>
              </div>
              <div class="stat-data">
                <div class="stat-value">{{ source.todayReports || 0 }}</div>
                <div class="stat-label">报告数</div>
              </div>
              <div class="stat-sparkline">
                <svg viewBox="0 0 60 24" fill="none">
                  <path d="M0 18 L10 14 L20 16 L30 10 L40 12 L50 6 L60 8" stroke="currentColor" stroke-width="2" fill="none"/>
                </svg>
              </div>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card warning">
            <div class="stat-inner">
              <div class="stat-icon">
                <el-icon><Clock /></el-icon>
              </div>
              <div class="stat-data">
                <div class="stat-value time">{{ formatTime(source.lastCollectTime) || '--:--' }}</div>
                <div class="stat-label">最后采集</div>
              </div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 系统概览 -->
    <el-row :gutter="20" style="margin-top: 24px;">
      <el-col :span="14">
        <el-card class="card log-card">
          <template #header>
            <div class="card-header">
              <div class="header-title">
                <el-icon class="title-icon"><Tickets /></el-icon>
                <span>实时日志</span>
              </div>
              <el-button type="primary" link @click="$router.push('/logs')">查看全部</el-button>
            </div>
          </template>
          <div class="log-list">
            <div v-for="log in recentLogs" :key="log.id" class="log-item" :class="getLogClass(log.level)">
              <div class="log-time">{{ formatLogTime(log.createdAt) }}</div>
              <div class="log-level" :class="log.level.toLowerCase()">
                <span class="level-badge">{{ log.level }}</span>
              </div>
              <div class="log-message">{{ log.message }}</div>
            </div>
            <el-empty v-if="!recentLogs.length" description="暂无日志" />
          </div>
        </el-card>
      </el-col>

      <el-col :span="10">
        <!-- 告警统计 -->
        <el-card class="card alert-card">
          <template #header>
            <div class="card-header">
              <div class="header-title">
                <el-icon class="title-icon"><WarningFilled /></el-icon>
                <span>告警统计</span>
              </div>
              <div class="alert-summary" :class="getAlertSummaryClass()">
                {{ getAlertTotal() }} 个待处理
              </div>
            </div>
          </template>
          <div class="alert-list">
            <div class="alert-item critical">
              <div class="alert-icon">
                <el-icon><CircleCloseFilled /></el-icon>
              </div>
              <div class="alert-content">
                <span class="alert-label">严重</span>
                <span class="alert-count">{{ alertStats.critical || 0 }}</span>
              </div>
              <div class="alert-bar">
                <div class="bar-fill critical" :style="{ width: getAlertPercent('critical') + '%' }"></div>
              </div>
            </div>
            <div class="alert-item error">
              <div class="alert-icon">
                <el-icon><CloseBold /></el-icon>
              </div>
              <div class="alert-content">
                <span class="alert-label">错误</span>
                <span class="alert-count">{{ alertStats.error || 0 }}</span>
              </div>
              <div class="alert-bar">
                <div class="bar-fill error" :style="{ width: getAlertPercent('error') + '%' }"></div>
              </div>
            </div>
            <div class="alert-item warning">
              <div class="alert-icon">
                <el-icon><WarningFilled /></el-icon>
              </div>
              <div class="alert-content">
                <span class="alert-label">警告</span>
                <span class="alert-count">{{ alertStats.warning || 0 }}</span>
              </div>
              <div class="alert-bar">
                <div class="bar-fill warning" :style="{ width: getAlertPercent('warning') + '%' }"></div>
              </div>
            </div>
            <div class="alert-item info">
              <div class="alert-icon">
                <el-icon><InfoFilled /></el-icon>
              </div>
              <div class="alert-content">
                <span class="alert-label">信息</span>
                <span class="alert-count">{{ alertStats.info || 0 }}</span>
              </div>
              <div class="alert-bar">
                <div class="bar-fill info" :style="{ width: getAlertPercent('info') + '%' }"></div>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 快速操作 -->
        <el-card class="card action-card">
          <template #header>
            <div class="card-header">
              <div class="header-title">
                <el-icon class="title-icon"><Grid /></el-icon>
                <span>快速操作</span>
              </div>
            </div>
          </template>
          <div class="action-grid">
            <div class="action-item" @click="$router.push('/collection')">
              <div class="action-icon">
                <el-icon><List /></el-icon>
              </div>
              <span class="action-label">采集管理</span>
            </div>
            <div class="action-item" @click="$router.push('/scripts')">
              <div class="action-icon">
                <el-icon><Document /></el-icon>
              </div>
              <span class="action-label">脚本管理</span>
            </div>
            <div class="action-item" @click="$router.push('/sdk')">
              <div class="action-icon">
                <el-icon><Aim /></el-icon>
              </div>
              <span class="action-label">SDK状态</span>
            </div>
            <div class="action-item" @click="$router.push('/settings')">
              <div class="action-icon">
                <el-icon><Setting /></el-icon>
              </div>
              <span class="action-label">系统设置</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getDashboard, collectLiangxinwang } from '@/api/dashboard'
import {
  Connection, Refresh, SuccessFilled, CircleCloseFilled, Document, Clock,
  Tickets, WarningFilled, CloseBold, InfoFilled, Grid, List, Setting, Aim,
  CaretTop, CaretBottom
} from '@element-plus/icons-vue'

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

const getSourceStatusClass = (source: any) => {
  const lastTime = source.lastCollectTime
  if (!lastTime) return 'offline'
  const diff = Date.now() - new Date(lastTime).getTime()
  if (diff < 3600000) return 'online'
  if (diff < 86400000) return 'idle'
  return 'offline'
}

const getSourceStatus = (source: any) => {
  const cls = getSourceStatusClass(source)
  const map: Record<string, string> = { online: '采集中', idle: '空闲', offline: '离线' }
  return map[cls] || '未知'
}

const getLogClass = (level: string) => {
  return `log-${level.toLowerCase()}`
}

const formatTime = (time: string) => {
  if (!time) return ''
  return time.substring(11, 16)
}

const formatLogTime = (time: string) => {
  if (!time) return ''
  return time.substring(11, 19)
}

const getAlertTotal = () => {
  return (alertStats.value.critical || 0) + (alertStats.value.error || 0)
}

const getAlertSummaryClass = () => {
  const total = getAlertTotal()
  if (total > 0) return 'danger'
  return 'safe'
}

const getAlertPercent = (type: string) => {
  const total = getAlertTotal() || 1
  return Math.min(100, ((alertStats.value[type] || 0) / total) * 100)
}

onMounted(() => {
  loadDashboard()
})
</script>

<style scoped>
.dashboard {
  padding-bottom: 24px;
}

.source-section {
  background: #ffffff;
  border-radius: 16px;
  padding: 24px;
  margin-bottom: 20px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 4px 12px rgba(0, 0, 0, 0.03);
  border: 1px solid rgba(0, 0, 0, 0.04);
  animation: slideUp 0.4s ease-out;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.source-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.source-info {
  display: flex;
  align-items: center;
  gap: 14px;
}

.source-badge {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, #d4a574 0%, #c49464 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 20px;
  box-shadow: 0 4px 12px rgba(212, 165, 116, 0.35);
}

.source-text {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.source-name {
  font-size: 17px;
  font-weight: 600;
  color: #1a202c;
}

.source-status {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  width: fit-content;
}

.source-status.online {
  background: rgba(72, 187, 120, 0.1);
  color: #38a169;
}

.source-status.idle {
  background: rgba(245, 158, 11, 0.1);
  color: #d69e2e;
}

.source-status.offline {
  background: rgba(160, 174, 192, 0.1);
  color: #a0aec0;
}

.collect-btn {
  height: 38px;
  padding: 0 18px;
  background: linear-gradient(135deg, #d4a574 0%, #c49464 100%);
  border: none;
  border-radius: 8px;
  font-weight: 500;
  color: white;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(212, 165, 116, 0.25);
}

.collect-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(212, 165, 116, 0.35);
}

.btn-icon {
  margin-right: 6px;
}

.stat-row {
  margin-bottom: 8px;
}

.stat-card {
  border-radius: 12px;
  padding: 18px;
  transition: all 0.3s ease;
  position: relative;
  overflow: hidden;
}

.stat-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  opacity: 0;
  transition: opacity 0.3s;
}

.stat-card:hover::before {
  opacity: 1;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.stat-card.success::before { background: linear-gradient(90deg, #48bb78, #38a169); }
.stat-card.danger::before { background: linear-gradient(90deg, #fc8181, #f56565); }
.stat-card.primary::before { background: linear-gradient(90deg, #63b3ed, #4299e1); }
.stat-card.warning::before { background: linear-gradient(90deg, #f6ad55, #ed8936); }

.stat-inner {
  display: flex;
  align-items: center;
  gap: 14px;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}

.stat-card.success .stat-icon {
  background: rgba(72, 187, 120, 0.1);
  color: #48bb78;
}

.stat-card.danger .stat-icon {
  background: rgba(252, 129, 129, 0.1);
  color: #fc8181;
}

.stat-card.primary .stat-icon {
  background: rgba(99, 179, 237, 0.1);
  color: #63b3ed;
}

.stat-card.warning .stat-icon {
  background: rgba(246, 173, 85, 0.1);
  color: #f6ad55;
}

.stat-data {
  flex: 1;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1a202c;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}

.stat-value.time {
  font-size: 20px;
  font-family: 'SF Mono', 'Monaco', monospace;
}

.stat-label {
  font-size: 13px;
  color: #718096;
  margin-top: 4px;
}

.stat-trend {
  display: flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 8px;
  border-radius: 6px;
}

.stat-trend.up {
  background: rgba(72, 187, 120, 0.1);
  color: #38a169;
}

.stat-trend.down {
  background: rgba(252, 129, 129, 0.1);
  color: #f56565;
}

.stat-sparkline {
  width: 60px;
  height: 24px;
  color: #63b3ed;
}

.stat-sparkline svg {
  width: 100%;
  height: 100%;
}

/* Cards */
.card {
  border-radius: 16px;
  border: none;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04), 0 4px 12px rgba(0, 0, 0, 0.03);
  overflow: hidden;
}

.card :deep(.el-card__header) {
  padding: 18px 24px;
  border-bottom: 1px solid rgba(0, 0, 0, 0.04);
}

.card :deep(.el-card__body) {
  padding: 20px 24px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 15px;
  font-weight: 600;
  color: #1a202c;
}

.title-icon {
  font-size: 18px;
  color: #d4a574;
}

/* Log card */
.log-card {
  animation: slideUp 0.4s ease-out 0.1s both;
}

.log-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 320px;
  overflow-y: auto;
}

.log-item {
  display: grid;
  grid-template-columns: 80px 60px 1fr;
  gap: 12px;
  align-items: center;
  padding: 12px 14px;
  background: #f7f8fa;
  border-radius: 8px;
  transition: all 0.2s;
}

.log-item:hover {
  background: #f0f2f5;
  transform: translateX(4px);
}

.log-time {
  font-size: 12px;
  color: #718096;
  font-family: 'SF Mono', monospace;
}

.level-badge {
  font-size: 10px;
  font-weight: 600;
  padding: 2px 6px;
  border-radius: 4px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.log-error .level-badge {
  background: rgba(252, 129, 129, 0.15);
  color: #e53e3e;
}

.log-warn .level-badge {
  background: rgba(246, 173, 85, 0.15);
  color: #dd6b20;
}

.log-info .level-badge {
  background: rgba(99, 179, 237, 0.15);
  color: #3182ce;
}

.log-debug .level-badge {
  background: rgba(160, 174, 192, 0.15);
  color: #718096;
}

.log-message {
  font-size: 13px;
  color: #4a5568;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Alert card */
.alert-card {
  animation: slideUp 0.4s ease-out 0.15s both;
}

.alert-summary {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 6px;
  font-weight: 500;
}

.alert-summary.danger {
  background: rgba(252, 129, 129, 0.1);
  color: #e53e3e;
}

.alert-summary.safe {
  background: rgba(72, 187, 120, 0.1);
  color: #38a169;
}

.alert-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.alert-item {
  display: flex;
  align-items: center;
  gap: 14px;
}

.alert-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
}

.alert-item.critical .alert-icon {
  background: rgba(252, 129, 129, 0.1);
  color: #fc8181;
}

.alert-item.error .alert-icon {
  background: rgba(246, 173, 85, 0.1);
  color: #f6ad55;
}

.alert-item.warning .alert-icon {
  background: rgba(237, 137, 54, 0.1);
  color: #ed8936;
}

.alert-item.info .alert-icon {
  background: rgba(99, 179, 237, 0.1);
  color: #63b3ed;
}

.alert-content {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 70px;
}

.alert-label {
  font-size: 13px;
  color: #4a5568;
}

.alert-count {
  font-size: 15px;
  font-weight: 700;
  color: #1a202c;
}

.alert-bar {
  flex: 1;
  height: 6px;
  background: #edf2f7;
  border-radius: 3px;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 3px;
  transition: width 0.5s ease;
}

.bar-fill.critical { background: linear-gradient(90deg, #fc8181, #f56565); }
.bar-fill.error { background: linear-gradient(90deg, #f6ad55, #ed8936); }
.bar-fill.warning { background: linear-gradient(90deg, #ed8936, #dd6b20); }
.bar-fill.info { background: linear-gradient(90deg, #63b3ed, #4299e1); }

/* Action card */
.action-card {
  margin-top: 20px;
  animation: slideUp 0.4s ease-out 0.2s both;
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 20px 16px;
  background: #f7f8fa;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.25s ease;
}

.action-item:hover {
  background: linear-gradient(135deg, rgba(212, 165, 116, 0.08) 0%, rgba(212, 165, 116, 0.04) 100%);
  transform: translateY(-2px);
}

.action-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, #d4a574 0%, #c49464 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 20px;
  box-shadow: 0 4px 12px rgba(212, 165, 116, 0.3);
}

.action-label {
  font-size: 13px;
  font-weight: 500;
  color: #4a5568;
}
</style>
