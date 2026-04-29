<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside width="220px" class="sidebar">
      <div class="logo">
        <div class="logo-icon">
          <svg viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M20 4L4 14V28L20 36L36 28V14L20 4Z" fill="url(#grain-gradient)" stroke="#d4a574" stroke-width="1.5"/>
            <path d="M20 4V36M4 14L36 28M36 14L4 28" stroke="#d4a574" stroke-width="1" opacity="0.5"/>
            <defs>
              <linearGradient id="grain-gradient" x1="4" y1="4" x2="36" y2="36">
                <stop offset="0%" stop-color="#f5e6d3"/>
                <stop offset="100%" stop-color="#d4a574"/>
              </linearGradient>
            </defs>
          </svg>
        </div>
        <div class="logo-text">
          <span class="logo-title">粮讯分析</span>
          <span class="logo-subtitle">智能采集平台</span>
        </div>
      </div>

      <el-menu
        :default-active="activeMenu"
        router
        class="sidebar-menu"
        background-color="transparent"
        text-color="#c8d1dc"
        active-text-color="#f5c87a"
        :ellipsis="false"
      >
        <el-menu-item index="/dashboard" class="menu-item">
          <el-icon class="menu-icon"><DataAnalysis /></el-icon>
          <span class="menu-text">仪表板</span>
          <div class="menu-indicator"></div>
        </el-menu-item>
        <el-menu-item index="/collection" class="menu-item">
          <el-icon class="menu-icon"><Collection /></el-icon>
          <span class="menu-text">采集管理</span>
          <div class="menu-indicator"></div>
        </el-menu-item>
        <el-menu-item index="/sdk" class="menu-item">
          <el-icon class="menu-icon"><Aim /></el-icon>
          <span class="menu-text">SDK管理</span>
          <div class="menu-indicator"></div>
        </el-menu-item>
        <el-menu-item index="/scripts" class="menu-item">
          <el-icon class="menu-icon"><Document /></el-icon>
          <span class="menu-text">脚本管理</span>
          <div class="menu-indicator"></div>
        </el-menu-item>
        <el-menu-item index="/logs" class="menu-item">
          <el-icon class="menu-icon"><Tickets /></el-icon>
          <span class="menu-text">日志查看</span>
          <div class="menu-indicator"></div>
        </el-menu-item>
        <div class="menu-divider"></div>
        <el-menu-item index="/settings" class="menu-item">
          <el-icon class="menu-icon"><Setting /></el-icon>
          <span class="menu-text">系统设置</span>
          <div class="menu-indicator"></div>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer">
        <div class="version-info">v1.0.0</div>
      </div>
    </el-aside>

    <!-- 主内容区 -->
    <el-container class="main-wrapper">
      <el-header class="header">
        <div class="header-left">
          <h1 class="page-title">{{ pageTitle }}</h1>
          <div class="breadcrumb" v-if="route.path !== '/dashboard'">
            <span class="breadcrumb-item">首页</span>
            <span class="breadcrumb-separator">/</span>
            <span class="breadcrumb-item active">{{ pageTitle }}</span>
          </div>
        </div>
        <div class="header-right">
          <div class="header-time">
            <span class="time-label">采集服务器</span>
            <span class="time-value">{{ currentTime }}</span>
          </div>
          <el-button type="primary" class="collect-btn" @click="handleCollect" :loading="collecting">
            <el-icon class="btn-icon"><Refresh /></el-icon>
            <span class="btn-text">立即采集</span>
          </el-button>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { collectLiangxinwang } from '@/api/dashboard'
import { DataAnalysis, Collection, Document, Setting, Refresh, Aim, Tickets } from '@element-plus/icons-vue'

const route = useRoute()
const collecting = ref(false)
const currentTime = ref('')
let timeInterval: ReturnType<typeof setInterval>

const activeMenu = computed(() => route.path)

const pageTitle = computed(() => {
  const titleMap: Record<string, string> = {
    '/dashboard': '数据仪表板',
    '/collection': '采集管理',
    '/sdk': 'SDK管理',
    '/scripts': '脚本管理',
    '/logs': '日志查看',
    '/settings': '系统设置'
  }
  return titleMap[route.path] || '仪表板'
})

const updateTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

const handleCollect = async () => {
  try {
    collecting.value = true
    await collectLiangxinwang()
    ElMessage.success('采集任务已启动，请稍后查看日志')
  } catch (error) {
    ElMessage.error('启动采集失败')
  } finally {
    collecting.value = false
  }
}

onMounted(() => {
  updateTime()
  timeInterval = setInterval(updateTime, 1000)
})

onUnmounted(() => {
  clearInterval(timeInterval)
})
</script>

<style scoped>
.layout-container {
  height: 100vh;
  background: linear-gradient(135deg, #1a1f2e 0%, #252b3d 100%);
}

.sidebar {
  background: linear-gradient(180deg, #1e2433 0%, #161b26 100%);
  display: flex;
  flex-direction: column;
  position: relative;
  overflow: hidden;
}

.sidebar::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 100 100' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noise'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noise)'/%3E%3C/svg%3E");
  opacity: 0.03;
  pointer-events: none;
}

.logo {
  height: 80px;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 20px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  position: relative;
  z-index: 1;
}

.logo-icon {
  width: 40px;
  height: 40px;
  flex-shrink: 0;
}

.logo-icon svg {
  width: 100%;
  height: 100%;
}

.logo-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.logo-title {
  color: #f5f7fa;
  font-size: 16px;
  font-weight: 600;
  letter-spacing: 0.5px;
}

.logo-subtitle {
  color: #7a8599;
  font-size: 11px;
  letter-spacing: 1px;
}

.sidebar-menu {
  flex: 1;
  border: none;
  padding: 16px 12px;
  position: relative;
  z-index: 1;
}

.menu-item {
  height: 48px;
  margin-bottom: 4px;
  border-radius: 10px;
  position: relative;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

.menu-item:hover {
  background: rgba(255, 255, 255, 0.05);
}

.menu-item.is-active {
  background: linear-gradient(90deg, rgba(245, 200, 122, 0.15) 0%, rgba(245, 200, 122, 0.05) 100%);
}

.menu-item.is-active .menu-indicator {
  opacity: 1;
  transform: scaleY(1);
}

.menu-icon {
  width: 20px;
  height: 20px;
  margin-right: 12px;
  color: #7a8599;
  transition: color 0.3s;
}

.menu-item.is-active .menu-icon {
  color: #f5c87a;
}

.menu-text {
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.3px;
}

.menu-indicator {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%) scaleY(0);
  width: 3px;
  height: 24px;
  background: linear-gradient(180deg, #f5c87a 0%, #d4a574 100%);
  border-radius: 0 2px 2px 0;
  opacity: 0;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.menu-divider {
  height: 1px;
  background: linear-gradient(90deg, transparent 0%, rgba(255,255,255,0.08) 50%, transparent 100%);
  margin: 12px 8px;
}

.sidebar-footer {
  padding: 16px 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  position: relative;
  z-index: 1;
}

.version-info {
  color: #4a5568;
  font-size: 11px;
  letter-spacing: 0.5px;
}

.main-wrapper {
  flex-direction: column;
  background: #f0f2f5;
}

.header {
  height: 72px;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  position: relative;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: baseline;
  gap: 16px;
}

.page-title {
  font-size: 22px;
  font-weight: 600;
  color: #1a202c;
  margin: 0;
  letter-spacing: -0.3px;
}

.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.breadcrumb-item {
  color: #718096;
}

.breadcrumb-item.active {
  color: #4a5568;
  font-weight: 500;
}

.breadcrumb-separator {
  color: #cbd5e0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 24px;
}

.header-time {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
}

.time-label {
  font-size: 11px;
  color: #a0aec0;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.time-value {
  font-size: 14px;
  font-weight: 600;
  color: #4a5568;
  font-variant-numeric: tabular-nums;
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;
}

.collect-btn {
  height: 40px;
  padding: 0 20px;
  background: linear-gradient(135deg, #d4a574 0%, #c49464 100%);
  border: none;
  border-radius: 8px;
  font-weight: 500;
  letter-spacing: 0.3px;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 2px 8px rgba(212, 165, 116, 0.3);
}

.collect-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(212, 165, 116, 0.4);
}

.collect-btn:active {
  transform: translateY(0);
}

.btn-icon {
  margin-right: 6px;
}

.main-content {
  flex: 1;
  padding: 24px;
  overflow-y: auto;
  background: linear-gradient(180deg, #f0f2f5 0%, #e8eaef 100%);
}

/* Page transition */
.page-fade-enter-active,
.page-fade-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.page-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
