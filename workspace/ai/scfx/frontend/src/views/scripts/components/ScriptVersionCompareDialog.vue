<template>
  <el-dialog
    v-model="visible"
    title="对比脚本版本"
    width="1000px"
    :close-on-click-modal="false"
  >
    <!-- Selection Phase -->
    <div v-if="phase === 'select'" class="compare-select">
      <div class="select-row">
        <el-select v-model="versionId1" placeholder="选择第一个版本" style="width: 100%">
          <el-option
            v-for="v in versionList"
            :key="v.id"
            :label="formatVersionLabel(v)"
            :value="v.id"
          />
        </el-select>
        <span class="compare-divider">VS</span>
        <el-select v-model="versionId2" placeholder="选择第二个版本" style="width: 100%">
          <el-option
            v-for="v in versionList"
            :key="v.id"
            :label="formatVersionLabel(v)"
            :value="v.id"
          />
        </el-select>
      </div>
      <div class="select-actions">
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" :disabled="!versionId1 || !versionId2" @click="handleCompare">
          开始对比
        </el-button>
      </div>
    </div>

    <!-- Result Phase -->
    <div v-if="phase === 'result'" class="compare-result">
      <div class="result-header">
        <el-button @click="phase = 'select'" :icon="ArrowLeft">返回选择</el-button>
        <span class="result-title">版本对比</span>
      </div>

      <!-- Diff Stats -->
      <div class="diff-stats">
        <span class="stat-item stat-added">
          <span class="stat-badge">+{{ diffStats.added }}</span>
          <span>新增行</span>
        </span>
        <span class="stat-item stat-deleted">
          <span class="stat-badge">-{{ diffStats.deleted }}</span>
          <span>删除行</span>
        </span>
        <span class="stat-item stat-modified">
          <span class="stat-badge">~{{ diffStats.modified }}</span>
          <span>修改行</span>
        </span>
      </div>

      <!-- Diff View -->
      <div class="diff-view">
        <div class="diff-header">
          <div class="diff-header-left">
            <span class="version-label">v{{ version1?.versionNum }} - {{ formatDateTime(version1?.createdAt) }}</span>
          </div>
          <div class="diff-header-right">
            <span class="version-label">v{{ version2?.versionNum }} - {{ formatDateTime(version2?.createdAt) }}</span>
          </div>
        </div>

        <div class="diff-content">
          <div
            v-for="(line, idx) in diffLines"
            :key="idx"
            :class="['diff-line', `diff-${line.type}`]"
          >
            <span class="line-number line-num-left">{{ line.lineNum1 || '' }}</span>
            <span class="line-content">{{ line.content }}</span>
            <span class="line-number line-num-right">{{ line.lineNum2 || '' }}</span>
          </div>
        </div>
      </div>
    </div>

    <template #footer v-if="phase === 'select'">
      <span></span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import type { ScriptVersion } from '@/api'

const props = defineProps<{
  modelValue: boolean
  versionList: ScriptVersion[]
  scriptId: number
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  compare: [versionId1: number, versionId2: number]
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const phase = ref<'select' | 'result'>('select')
const versionId1 = ref<number>()
const versionId2 = ref<number>()
const version1 = ref<ScriptVersion>()
const version2 = ref<ScriptVersion>()
const diffLines = ref<Array<{ lineNum1?: number; lineNum2?: number; content: string; type: 'same' | 'added' | 'deleted' | 'modified' }>>([])

function formatVersionLabel(v: ScriptVersion) {
  return `v${v.versionNum} - ${formatDateTime(v.createdAt)}`
}

function formatDateTime(time?: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

interface DiffStats {
  added: number
  deleted: number
  modified: number
}

const diffStats = computed<DiffStats>(() => {
  let added = 0
  let deleted = 0
  let modified = 0
  for (const line of diffLines.value) {
    if (line.type === 'added') added++
    else if (line.type === 'deleted') deleted++
    else if (line.type === 'modified') modified++
  }
  return { added, deleted, modified }
})

function computeDiff(content1: string, content2: string) {
  const lines1 = content1.split('\n')
  const lines2 = content2.split('\n')
  const result: typeof diffLines.value = []

  // Simple line-by-line diff algorithm
  let i = 0
  let j = 0
  let lineNum1 = 1
  let lineNum2 = 1

  while (i < lines1.length || j < lines2.length) {
    if (i >= lines1.length) {
      // Remaining lines in version2 are additions
      result.push({ lineNum1: undefined, lineNum2: lineNum2++, content: lines2[j], type: 'added' })
      j++
    } else if (j >= lines2.length) {
      // Remaining lines in version1 are deletions
      result.push({ lineNum1: lineNum1++, lineNum2: undefined, content: lines1[i], type: 'deleted' })
      i++
    } else if (lines1[i] === lines2[j]) {
      // Same line
      result.push({ lineNum1: lineNum1++, lineNum2: lineNum2++, content: lines1[i], type: 'same' })
      i++
      j++
    } else {
      // Check if it's a modification or add/delete
      const nextMatchIn1 = lines1.slice(i + 1).indexOf(lines2[j])
      const nextMatchIn2 = lines2.slice(j + 1).indexOf(lines1[i]);

      if (nextMatchIn1 === -1 && nextMatchIn2 === -1) {
        // Both lines are different, treat as modification
        result.push({ lineNum1: lineNum1++, lineNum2: lineNum2++, content: lines1[i], type: 'modified' })
        result.push({ lineNum1: lineNum1++, lineNum2: lineNum2++, content: lines2[j], type: 'modified' })
        i++
        j++
      } else if (nextMatchIn1 !== -1 && (nextMatchIn2 === -1 || nextMatchIn1 < nextMatchIn2)) {
        // Lines in version1 between match are deletions
        for (let k = 0; k <= nextMatchIn1; k++) {
          result.push({ lineNum1: lineNum1++, lineNum2: undefined, content: lines1[i + k], type: 'deleted' })
        }
        i += nextMatchIn1 + 1
      } else {
        // Lines in version2 between match are additions
        for (let k = 0; k <= nextMatchIn2; k++) {
          result.push({ lineNum1: undefined, lineNum2: lineNum2++, content: lines2[j + k], type: 'added' })
        }
        j += nextMatchIn2 + 1
      }
    }
  }

  return result
}

async function handleCompare() {
  if (!versionId1.value || !versionId2.value) {
    ElMessage.warning('请选择两个版本')
    return
  }

  if (versionId1.value === versionId2.value) {
    ElMessage.warning('请选择不同的版本')
    return
  }

  const v1 = props.versionList.find(v => v.id === versionId1.value)
  const v2 = props.versionList.find(v => v.id === versionId2.value)

  if (v1 && v2) {
    version1.value = v1
    version2.value = v2

    // Generate diff
    const content1 = v1.scriptContent || ''
    const content2 = v2.scriptContent || ''
    diffLines.value = computeDiff(content1, content2)

    phase.value = 'result'
    emit('compare', versionId1.value, versionId2.value)
  }
}

function handleCancel() {
  visible.value = false
}

watch(() => props.modelValue, (val) => {
  if (val) {
    phase.value = 'select'
    versionId1.value = undefined
    versionId2.value = undefined
    version1.value = undefined
    version2.value = undefined
    diffLines.value = []
  }
})
</script>

<style scoped>
.compare-select {
  padding: 20px 0;
}

.select-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  margin-bottom: 24px;
}

.compare-divider {
  font-weight: 600;
  font-size: 18px;
  color: #909399;
}

.select-actions {
  display: flex;
  justify-content: center;
  gap: 12px;
}

.compare-result {
  padding: 0;
}

.result-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #dcdfe6;
}

.result-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.diff-stats {
  display: flex;
  gap: 24px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 8px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.stat-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 600;
}

.stat-added .stat-badge {
  background: #f0f9eb;
  color: #67c23a;
}

.stat-deleted .stat-badge {
  background: #fef0f0;
  color: #f56c6c;
}

.stat-modified .stat-badge {
  background: #fef9e6;
  color: #e6a23c;
}

.diff-view {
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  overflow: hidden;
}

.diff-header {
  display: flex;
  background: #f5f7fa;
  border-bottom: 1px solid #dcdfe6;
}

.diff-header-left,
.diff-header-right {
  flex: 1;
  padding: 8px 12px;
  font-size: 12px;
  font-weight: 500;
  color: #606266;
}

.diff-header-left {
  border-right: 1px solid #dcdfe6;
}

.version-label {
  font-family: 'Consolas', 'Monaco', monospace;
}

.diff-content {
  max-height: 450px;
  overflow-y: auto;
  font-family: 'Consolas', 'Monaco', monospace;
  font-size: 13px;
}

.diff-line {
  display: flex;
  min-height: 24px;
  line-height: 24px;
}

.diff-line.diff-same {
  background: #fff;
}

.diff-line.diff-added {
  background: #f0f9eb;
}

.diff-line.diff-deleted {
  background: #fef0f0;
}

.diff-line.diff-modified {
  background: #fef9e6;
}

.line-number {
  width: 50px;
  padding: 0 12px;
  text-align: right;
  color: #909399;
  background: rgba(0, 0, 0, 0.02);
  border-right: 1px solid #dcdfe6;
  flex-shrink: 0;
  user-select: none;
}

.line-content {
  flex: 1;
  padding: 0 12px;
  white-space: pre;
  overflow: hidden;
  text-overflow: ellipsis;
}

.line-num-left {
  border-right: 1px solid #dcdfe6;
}

.line-num-right {
  border-left: 1px solid #dcdfe6;
}

.diff-same .line-content {
  color: #606266;
}

.diff-added .line-content {
  color: #67c23a;
}

.diff-deleted .line-content {
  color: #f56c6c;
}

.diff-modified .line-content {
  color: #e6a23c;
}
</style>