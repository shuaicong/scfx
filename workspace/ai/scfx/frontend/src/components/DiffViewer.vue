<template>
  <div class="diff-viewer">
    <div v-if="loading" class="diff-loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载对比编辑器...</span>
    </div>
    <div v-if="error" class="diff-error">
      <el-icon><WarningFilled /></el-icon>
      <span>{{ error }}</span>
    </div>
    <div ref="containerRef" class="diff-container" :style="{ height: height }"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onUnmounted } from 'vue'
import { Loading, WarningFilled } from '@element-plus/icons-vue'
import * as monaco from 'monaco-editor'

const props = withDefaults(defineProps<{
  originalValue?: string
  modifiedValue?: string
  language?: string
  height?: string
}>(), {
  originalValue: '',
  modifiedValue: '',
  language: 'python',
  height: '400px',
})

const containerRef = ref<HTMLElement | null>(null)
const loading = ref(true)
const error = ref('')
let diffEditor: monaco.editor.IStandaloneDiffEditor | null = null

function initDiffEditor() {
  if (!containerRef.value) return

  try {
    diffEditor = monaco.editor.createDiffEditor(containerRef.value, {
      theme: 'vs-dark',
      automaticLayout: true,
      readOnly: true,
      renderSideBySide: true,
      originalEditable: false,
      scrollBeyondLastLine: false,
    })

    const originalModel = monaco.editor.createModel(props.originalValue, props.language)
    const modifiedModel = monaco.editor.createModel(props.modifiedValue, props.language)

    diffEditor.setModel({
      original: originalModel,
      modified: modifiedModel,
    })

    loading.value = false
  } catch (e) {
    error.value = '编辑器初始化失败'
    loading.value = false
  }
}

function dispose() {
  if (diffEditor) {
    diffEditor.dispose()
    diffEditor = null
  }
}

watch(() => props.originalValue, () => {
  if (diffEditor) {
    const model = diffEditor.getModel()
    if (model) {
      model.original.setValue(props.originalValue)
    }
  }
})

watch(() => props.modifiedValue, () => {
  if (diffEditor) {
    const model = diffEditor.getModel()
    if (model) {
      model.modified.setValue(props.modifiedValue)
    }
  }
})

onUnmounted(() => {
  dispose()
})

// Initialize on mount
watch(containerRef, (el) => {
  if (el && !diffEditor) {
    initDiffEditor()
  }
}, { immediate: true })
</script>

<style scoped>
.diff-viewer {
  position: relative;
  width: 100%;
  border: 1px solid #30363d;
  border-radius: 6px;
  overflow: hidden;
}

.diff-container {
  width: 100%;
}

.diff-loading,
.diff-error {
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

.diff-error {
  color: #f85149;
}

.diff-error .el-icon {
  font-size: 24px;
}
</style>