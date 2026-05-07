<template>
  <div class="script-editor">
    <div v-if="loading" class="editor-loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载编辑器中...</span>
    </div>
    <div v-if="error" class="editor-error">
      <el-icon><WarningFilled /></el-icon>
      <span>{{ error }}</span>
      <el-button size="small" @click="retry">重试</el-button>
    </div>
    <div ref="containerRef" class="editor-container" :style="{ height: height }"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue'
import { Loading, WarningFilled } from '@element-plus/icons-vue'
import { useMonaco } from '@/composables/useMonaco'

const props = withDefaults(defineProps<{
  modelValue?: string
  language?: string
  readOnly?: boolean
  height?: string
}>(), {
  modelValue: '',
  language: 'python',
  readOnly: false,
  height: '400px',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const containerRef = ref<HTMLElement | null>(null)
const { editor, loading, error, setValue, getValue, initEditor, dispose } = useMonaco(containerRef, {
  value: props.modelValue,
  language: props.language,
  readOnly: props.readOnly,
  theme: 'vs-dark',
})

// 监听外部值变化
watch(() => props.modelValue, (newVal) => {
  if (editor.value && newVal !== getValue()) {
    setValue(newVal)
  }
})

// 内容变化时同步到父组件
watch(editor, (monacoEditor) => {
  if (monacoEditor) {
    monacoEditor.onDidChangeModelContent(() => {
      emit('update:modelValue', getValue())
    })
  }
})

function retry() {
  dispose()
  initEditor()
}

onUnmounted(() => {
  dispose()
})

// 暴露方法给父组件
defineExpose({
  setValue,
  getValue,
  getEditor: () => editor.value,
})
</script>

<style scoped>
.script-editor {
  position: relative;
  width: 100%;
  border: 1px solid #30363d;
  border-radius: 6px;
  overflow: hidden;
}

.editor-container {
  width: 100%;
}

.editor-loading,
.editor-error {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  background: #1e1e1e;
  color: #ccc;
  z-index: 10;
}

.editor-error {
  color: #f85149;
}

.editor-error .el-icon {
  font-size: 24px;
}
</style>
