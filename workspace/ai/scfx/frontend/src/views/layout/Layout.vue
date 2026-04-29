<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside width="200px" class="sidebar">
      <div class="logo">
        <h3>粮食分析平台</h3>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        class="sidebar-menu"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>仪表板</span>
        </el-menu-item>
        <el-menu-item index="/collection">
          <el-icon><Collection /></el-icon>
          <span>采集管理</span>
        </el-menu-item>
        <el-menu-item index="/logs">
          <el-icon><Document /></el-icon>
          <span>日志查看</span>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon><Setting /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <!-- 主内容区 -->
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <span class="page-title">{{ pageTitle }}</span>
        </div>
        <div class="header-right">
          <el-button type="primary" @click="handleCollect" :loading="collecting">
            <el-icon><Refresh /></el-icon>
            立即采集
          </el-button>
        </div>
      </el-header>
      <el-main class="main-content">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { collectLiangxinwang } from '@/api/dashboard'

const route = useRoute()
const collecting = ref(false)

const activeMenu = computed(() => route.path)

const pageTitle = computed(() => {
  return (route.meta.title as string) || '仪表板'
})

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
  console.log('Layout mounted')
})
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

.sidebar {
  background-color: #304156;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #2b3a4a;
}

.logo h3 {
  color: #fff;
  font-size: 16px;
  margin: 0;
}

.sidebar-menu {
  border-right: none;
}

.header {
  background-color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.header-left {
  font-size: 18px;
  font-weight: 500;
}

.main-content {
  background-color: #f0f2f5;
  padding: 20px;
}
</style>
