<template>
  <el-dialog v-model="visible" title="脚本编辑" fullscreen>
    <div class="editor-header">
      <span>{{ scriptName }} {{ version }}</span>
      <div class="editor-actions">
        <el-button @click="handleFormat">格式化</el-button>
      </div>
    </div>

    <div class="editor-container">
      <textarea
        v-model="scriptContent"
        class="code-textarea"
        placeholder="请输入脚本内容..."
      ></textarea>
    </div>

    <div class="editor-footer">
      <span class="version-hint">保存后将创建新版本 {{ nextVersion }}</span>
      <div class="footer-actions">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { scriptApi } from '@/api'

const props = defineProps<{
  modelValue: boolean
  scriptId: number
  scriptName: string
  version: string
}>()

const emit = defineEmits(['update:modelValue', 'success'])

const visible = ref(false)
const scriptContent = ref('')

const nextVersion = ref('')

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) {
    loadScript()
    nextVersion.value = incrementVersion(props.version)
  }
})

watch(visible, (val) => emit('update:modelValue', val))

function loadScript() {
  scriptApi.getContent(props.scriptId).then((res: any) => {
    scriptContent.value = res.data || ''
  }).catch((e) => {
    console.error('加载脚本失败', e)
  })
}

function handleFormat() {
  // TODO: 调用 Black 格式化
  ElMessage.info('格式化功能待实现')
}

async function handleSave() {
  try {
    await scriptApi.updateContent(props.scriptId, scriptContent.value)
    ElMessage.success('保存成功')
    emit('success')
    visible.value = false
  } catch (e) {
    console.error('保存失败', e)
  }
}

function incrementVersion(v: string): string {
  if (!v) return '1.0.0'
  const parts = v.split('.')
  const last = parseInt(parts[parts.length - 1]) + 1
  return parts.slice(0, -1).concat([last.toString()]).join('.')
}
</script>

<style scoped>
.editor-container {
  height: calc(100vh - 200px);
  min-height: 400px;
  border: 1px solid #e0e0e0;
  margin: 10px 0;
}

.code-textarea {
  width: 100%;
  height: 100%;
  min-height: 400px;
  border: none;
  resize: none;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
  line-height: 1.6;
  padding: 12px;
  box-sizing: border-box;
  background: #1e1e1e;
  color: #c8d1dc;
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  border-bottom: 1px solid #e0e0e0;
}

.editor-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  border-top: 1px solid #e0e0e0;
}

.version-hint {
  color: #909399;
  font-size: 12px;
}

.footer-actions {
  display: flex;
  gap: 8px;
}
</style>