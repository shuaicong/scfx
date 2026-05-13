<template>
  <el-dialog v-model="visible" title="版本历史" width="700px">
    <el-table :data="versions" stripe>
      <el-table-column prop="version" label="版本" width="100" />
      <el-table-column prop="createdAt" label="日期" width="160">
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'active' ? 'success' : 'info'">
            {{ row.status === 'active' ? '当前' : '已弃用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="changelog" label="变更说明" />
      <el-table-column label="操作" width="180">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleView(row)">查看</el-button>
          <el-button v-if="row.status !== 'active'" type="warning" link size="small" @click="handleRollback(row)">回滚</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="compare-section">
      <span>版本对比:</span>
      <el-select v-model="fromVersion" placeholder="从版本" style="width: 120px; margin-left: 8px;">
        <el-option v-for="v in versions" :key="v.version" :label="v.version" :value="v.version" />
      </el-select>
      <span style="margin: 0 8px;">vs</span>
      <el-select v-model="toVersion" placeholder="到版本" style="width: 120px;">
        <el-option v-for="v in versions" :key="v.version" :label="v.version" :value="v.version" />
      </el-select>
      <el-button style="margin-left: 8px;" @click="handleCompare">对比</el-button>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { versionApi } from '@/api'

const props = defineProps<{ modelValue: boolean; scriptId: number }>()
const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
const versions = ref<any[]>([])
const fromVersion = ref('')
const toVersion = ref('')

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) loadVersions()
})

watch(visible, (val) => emit('update:modelValue', val))

function loadVersions() {
  versionApi.list(props.scriptId).then((res: any) => {
    versions.value = res.data || []
  }).catch((e) => {
    console.error('加载版本失败', e)
  })
}

function formatDate(dateStr: string) {
  return dateStr?.substring(0, 16).replace('T', ' ') || ''
}

function handleView(row: any) {
  // TODO: 打开查看弹窗
  ElMessage.info('查看功能待实现')
}

async function handleRollback(row: any) {
  try {
    await versionApi.restore(props.scriptId, row.id, `回滚至 v${row.versionNum}`)
    ElMessage.success('回滚成功')
    loadVersions()
  } catch (e) {
    console.error('回滚失败', e)
  }
}

function handleCompare() {
  // TODO: 打开diff视图
  ElMessage.info('对比功能待实现')
}
</script>

<style scoped>
.compare-section {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #e0e0e0;
  display: flex;
  align-items: center;
}
</style>