<template>
  <el-dialog
    v-model="dialogVisible"
    title="脚本详情"
    width="700px"
    @close="handleClose"
  >
    <el-descriptions :column="2" border>
      <el-descriptions-item label="脚本名称">{{ script?.scriptName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="数据源">
        <el-tag v-if="script?.source === 'liangxin'" type="success">粮信网</el-tag>
        <el-tag v-else-if="script?.source === 'mysteel'" type="primary">我的钢铁网</el-tag>
        <el-tag v-else-if="script?.source === 'chinagrain'" type="warning">中华粮网</el-tag>
        <el-tag v-else>{{ script?.source || '-' }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="触发方式">
        <el-tag v-if="script?.triggerType === 'manual'" type="info">手动</el-tag>
        <el-tag v-else-if="script?.triggerType === 'single'" type="warning">单次</el-tag>
        <el-tag v-else-if="script?.triggerType === 'repeat'">周期</el-tag>
        <el-tag v-else-if="script?.triggerType === 'cron'" type="primary">Cron</el-tag>
        <el-tag v-else>{{ script?.triggerType || '-' }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag v-if="script?.status === 'enabled'" type="success">启用</el-tag>
        <el-tag v-else type="danger">禁用</el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="描述" :span="2">{{ script?.description || '-' }}</el-descriptions-item>
      <el-descriptions-item label="执行次数">{{ script?.executionCount || 0 }}</el-descriptions-item>
      <el-descriptions-item label="成功次数">{{ script?.successCount || 0 }}</el-descriptions-item>
      <el-descriptions-item label="失败次数">{{ script?.failedCount || 0 }}</el-descriptions-item>
      <el-descriptions-item label="最后执行时间">
        {{ script?.lastExecutionTime ? formatTime(script.lastExecutionTime) : '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="下次执行时间">
        {{ script?.nextExecutionTime ? formatTime(script.nextExecutionTime) : '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="创建时间">
        {{ script?.createdAt ? formatTime(script.createdAt) : '-' }}
      </el-descriptions-item>
    </el-descriptions>

    <div class="script-content-section">
      <div class="section-title">脚本内容</div>
      <el-input
        type="textarea"
        :model-value="script?.scriptContent"
        readonly
        :rows="10"
        placeholder="加载中..."
      />
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">关闭</el-button>
      <el-button type="primary" @click="handleEdit">编辑</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { scriptApi, CollectionScript } from '@/api'

const props = defineProps<{
  visible: boolean
  scriptId?: number
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'edit': [script: CollectionScript]
}>()

const script = ref<CollectionScript | null>(null)
const loading = ref(false)

const dialogVisible = ref(props.visible)

watch(() => props.visible, (val) => {
  dialogVisible.value = val
  if (val && props.scriptId && props.scriptId > 0) {
    loadScriptDetail()
  }
})

watch(dialogVisible, (val) => {
  emit('update:visible', val)
})

onMounted(() => {
  if (props.visible && props.scriptId) {
    loadScriptDetail()
  }
})

async function loadScriptDetail() {
  if (!props.scriptId) return

  loading.value = true
  try {
    const res: any = await scriptApi.getById(props.scriptId)
    script.value = res.data
  } catch (e) {
    console.error('加载脚本详情失败', e)
  } finally {
    loading.value = false
  }
}

function formatTime(time: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

function handleClose() {
  script.value = null
}

function handleEdit() {
  if (script.value) {
    emit('edit', script.value)
  }
}
</script>

<style scoped>
.script-content-section {
  margin-top: 20px;
}

.section-title {
  font-weight: bold;
  margin-bottom: 10px;
  color: #1a202c;
}

.script-content-section :deep(textarea) {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.6;
  background: #f5f7fa;
}
</style>