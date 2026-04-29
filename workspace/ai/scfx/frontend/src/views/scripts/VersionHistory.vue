<template>
  <div class="version-history">
    <div class="timeline">
      <div v-for="version in versions" :key="version.id" class="timeline-item">
        <div class="timeline-marker" :class="{ current: version.isCurrent }"></div>
        <div class="timeline-content">
          <div class="version-header">
            <span class="version-num">v{{ version.versionNum }}</span>
            <span v-if="version.isCurrent" class="current-badge">当前</span>
            <span class="version-time">{{ formatTime(version.createdAt) }}</span>
          </div>
          <div class="version-desc">{{ version.changeDescription }}</div>
          <div class="version-meta">
            <span>触发: {{ getTriggerText(version) }}</span>
            <span>操作人: {{ version.createdBy }}</span>
          </div>
          <div class="version-actions">
            <el-button link type="primary" @click="showVersionDetail(version)">查看</el-button>
            <el-button link type="primary" @click="showCompare(version)">对比</el-button>
            <el-button v-if="!version.isCurrent" link type="success" @click="handleRestore(version)">恢复</el-button>
          </div>
        </div>
      </div>
    </div>

    <el-dialog v-model="showDetailDialog" :title="`v${currentVersion?.versionNum} 版本详情`" width="900px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="版本号">v{{ currentVersion?.versionNum }}</el-descriptions-item>
        <el-descriptions-item label="修改时间">{{ formatTime(currentVersion?.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="触发类型">{{ getTriggerText(currentVersion!) }}</el-descriptions-item>
        <el-descriptions-item label="操作人">{{ currentVersion?.createdBy }}</el-descriptions-item>
      </el-descriptions>

      <div class="script-content">
        <div class="content-title">脚本内容</div>
        <pre><code>{{ currentVersion?.scriptContent }}</code></pre>
      </div>

      <template #footer>
        <el-button @click="showDetailDialog = false">关闭</el-button>
        <el-button v-if="!currentVersion?.isCurrent" type="primary" @click="handleRestore(currentVersion!)">恢复此版本</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showCompareDialog" title="版本对比" width="1000px">
      <div class="compare-container">
        <div class="compare-side">
          <div class="compare-header">v{{ compareVersion1?.versionNum }} (较早)</div>
          <div class="compare-config">
            <div><strong>触发:</strong> {{ getTriggerText(compareVersion1!) }}</div>
          </div>
          <pre class="compare-code"><code>{{ compareVersion1?.scriptContent }}</code></pre>
        </div>
        <div class="compare-side">
          <div class="compare-header">v{{ compareVersion2?.versionNum }} (较新)</div>
          <div class="compare-config">
            <div><strong>触发:</strong> {{ getTriggerText(compareVersion2!) }}</div>
          </div>
          <pre class="compare-code"><code>{{ compareVersion2?.scriptContent }}</code></pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { versionApi } from '@/api'
import type { ScriptVersion } from '@/api'

const props = defineProps<{ scriptId: number }>()

const versions = ref<ScriptVersion[]>([])
const showDetailDialog = ref(false)
const showCompareDialog = ref(false)
const currentVersion = ref<ScriptVersion>()
const compareVersion1 = ref<ScriptVersion>()
const compareVersion2 = ref<ScriptVersion>()

onMounted(() => loadVersions())

async function loadVersions() {
  try {
    const res = await versionApi.list(props.scriptId)
    versions.value = res.data
  } catch (e) {
    ElMessage.error('加载版本失败')
  }
}

function showVersionDetail(version: ScriptVersion) {
  currentVersion.value = version
  showDetailDialog.value = true
}

function showCompare(version: ScriptVersion) {
  const current = versions.value.find(v => v.isCurrent)
  if (current) {
    compareVersion1.value = version
    compareVersion2.value = current
    showCompareDialog.value = true
  }
}

async function handleRestore(version: ScriptVersion) {
  try {
    await ElMessageBox.confirm('确定要恢复到此版本吗？', '恢复版本', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const changeDesc = `恢复至 v${version.versionNum}`
    await versionApi.restore(props.scriptId, version.id!, changeDesc)
    ElMessage.success('版本已恢复')
    loadVersions()
    showDetailDialog.value = false
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('恢复失败')
  }
}

function formatTime(time?: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

function getTriggerText(version: ScriptVersion) {
  if (version.triggerType === 'repeat') {
    if (version.repeatType === 'daily') return `每日 ${version.repeatTime}`
    if (version.repeatType === 'weekly') return `每周 ${version.weeklyDays} ${version.repeatTime}`
    if (version.repeatType === 'monthly') return `每月 ${version.monthlyDay}日`
  }
  if (version.triggerType === 'cron') return `Cron: ${version.cronExpression}`
  return '手动'
}
</script>

<style scoped>
.timeline {
  position: relative;
  padding-left: 30px;
}
.timeline::before {
  content: '';
  position: absolute;
  left: 10px;
  top: 0;
  bottom: 0;
  width: 2px;
  background: #e4e7ed;
}
.timeline-item {
  position: relative;
  padding-bottom: 24px;
}
.timeline-marker {
  position: absolute;
  left: -24px;
  top: 4px;
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: #c0c4cc;
  border: 2px solid #fff;
}
.timeline-marker.current {
  background: #409eff;
}
.timeline-content {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
}
.version-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.version-num {
  font-weight: bold;
  font-size: 16px;
}
.current-badge {
  background: #409eff;
  color: #fff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}
.version-time {
  color: #999;
  font-size: 12px;
}
.version-desc {
  margin-bottom: 8px;
}
.version-meta {
  font-size: 12px;
  color: #666;
  margin-bottom: 8px;
}
.script-content {
  margin-top: 16px;
}
.content-title {
  font-weight: bold;
  margin-bottom: 8px;
}
pre {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
  max-height: 300px;
}
.compare-container {
  display: flex;
  gap: 16px;
}
.compare-side {
  flex: 1;
}
.compare-header {
  background: #f5f7fa;
  padding: 8px 12px;
  font-weight: bold;
  margin-bottom: 8px;
}
.compare-code {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  border-radius: 4px;
  overflow-x: auto;
  max-height: 400px;
}
</style>