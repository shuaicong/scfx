<template>
  <el-dialog
    v-model="visible"
    title="脚本编辑"
    fullscreen
    :before-close="handleClose">

    <div class="editor-header">
      <div class="file-info">
        <span class="script-name">{{ scriptName }}</span>
        <span class="version-badge">v{{ version }}</span>
        <span class="new-version" v-if="newVersion">→ v{{ newVersion }}</span>
      </div>
      <div class="editor-actions">
        <el-button @click="handleFormat" :disabled="!isDirty">
          <el-icon><MagicStick /></el-icon>
          格式化
        </el-button>
        <el-button @click="handleValidate" :disabled="!isDirty">
          <el-icon><CircleCheck /></el-icon>
          校验
        </el-button>
      </div>
    </div>

    <div ref="editorContainer" class="monaco-container"></div>

    <div class="editor-footer">
      <div class="footer-left">
        <span class="status-indicator" :class="statusClass">{{ statusText }}</span>
        <span class="cursor-position" v-if="cursorPosition">
          行 {{ cursorPosition.line }}, 列 {{ cursorPosition.column }}
        </span>
      </div>
      <div class="footer-right">
        <span class="change-hint" v-if="isDirty">有未保存的更改</span>
        <el-input
          v-model="changeDesc"
          placeholder="变更说明"
          style="width: 200px; margin-right: 12px;"
          clearable />
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :disabled="!isDirty">
          保存 v{{ newVersion }}
        </el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MagicStick, CircleCheck } from '@element-plus/icons-vue'
import * as monaco from 'monaco-editor'
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker'
import jsonWorker from 'monaco-editor/esm/vs/language/json/json.worker?worker'
import cssWorker from 'monaco-editor/esm/vs/language/css/css.worker?worker'
import htmlWorker from 'monaco-editor/esm/vs/language/html/html.worker?worker'
import tsWorker from 'monaco-editor/esm/vs/language/typescript/ts.worker?worker'
import { getScriptContent, saveScript, validateScript } from '@/api/collection-script'

// 注册 workers
self.MonacoEnvironment = {
  getWorker(_, label) {
    if (label === 'json') return new jsonWorker()
    if (label === 'css' || label === 'scss' || label === 'less') return new cssWorker()
    if (label === 'html' || label === 'handlebars' || label === 'razor') return new htmlWorker()
    if (label === 'typescript' || label === 'javascript') return new tsWorker()
    return new editorWorker()
  }
}

const props = defineProps<{
  modelValue: boolean
  scriptId: number
  scriptName: string
  version: string
}>()

const emit = defineEmits(['update:modelValue', 'success'])

// 状态
const visible = ref(false)
const editorContainer = ref<HTMLElement>()
const originalContent = ref('')
const currentContent = ref('')
const isDirty = ref(false)
const changeDesc = ref('')
const statusClass = ref('saved')
const statusText = ref('已保存')
const cursorPosition = ref<{ line: number; column: number } | null>(null)

let editor: monaco.editor.IStandaloneCodeEditor | null = null

// 计算新版本号
const newVersion = computed(() => {
  const parts = props.version.split('.')
  const last = parseInt(parts[parts.length - 1]) + 1
  return parts.slice(0, -1).concat([last.toString()]).join('.')
})

watch(() => props.modelValue, async (val) => {
  visible.value = val
  if (val) {
    await loadScript()
    await nextTick()
    initEditor()
  } else {
    disposeEditor()
  }
})

watch(visible, (val) => emit('update:modelValue', val))

async function loadScript() {
  try {
    const res: any = await getScriptContent(props.scriptId)
    if (res.code === 200) {
      originalContent.value = res.data
      currentContent.value = res.data
      isDirty.value = false
      statusText.value = '已保存'
      statusClass.value = 'saved'
    }
  } catch (error) {
    ElMessage.error('加载脚本失败')
  }
}

function initEditor() {
  if (!editorContainer.value || editor) return

  // 配置 Python 语言
  monaco.languages.register({ id: 'python' })

  // Python 语法高亮（简化版）
  monaco.languages.setMonarchTokensProvider('python', {
    tokenizer: {
      root: [
        [/#.*$/, 'comment'],
        [/(?:^|[^"])\b(def|class|if|elif|else|for|while|try|except|finally|with|as|import|from|return|yield|raise|break|continue|pass|lambda|and|or|not|in|is|True|False|None|async|await)\b/, 'keyword'],
        [/"([^"\\]|\\.)*$/, 'string.invalid'],
        [/'([^'\\]|\\.)*$/, 'string.invalid'],
        [/@\w+/, 'annotation'],
        [/\b\d+\.?\d*\b/, 'number'],
        [/[a-zA-Z_]\w*/, 'identifier'],
      ]
    }
  })

  // Python 自动补全
  monaco.languages.registerCompletionItemProvider('python', {
    provideCompletionItems: (model, position) => {
      const suggestions: monaco.languages.CompletionItem[] = [
        { label: 'def', kind: monaco.languages.CompletionItemKind.Keyword, insertText: 'def ${1:name}(${2:self}):', insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet, range: { startLineNumber: position.lineNumber, endLineNumber: position.lineNumber, startColumn: position.column, endColumn: position.column } },
        { label: 'class', kind: monaco.languages.CompletionItemKind.Keyword, insertText: 'class ${1:ClassName}:', insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet, range: { startLineNumber: position.lineNumber, endLineNumber: position.lineNumber, startColumn: position.column, endColumn: position.column } },
        { label: 'if', kind: monaco.languages.CompletionItemKind.Keyword, insertText: 'if ${1:condition}:', insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet, range: { startLineNumber: position.lineNumber, endLineNumber: position.lineNumber, startColumn: position.column, endColumn: position.column } },
        { label: 'for', kind: monaco.languages.CompletionItemKind.Keyword, insertText: 'for ${1:item} in ${2:items}:', insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet, range: { startLineNumber: position.lineNumber, endLineNumber: position.lineNumber, startColumn: position.column, endColumn: position.column } },
        { label: 'print', kind: monaco.languages.CompletionItemKind.Function, insertText: 'print(${1:msg})', insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet, range: { startLineNumber: position.lineNumber, endLineNumber: position.lineNumber, startColumn: position.column, endColumn: position.column } },
        { label: 'requests.get', kind: monaco.languages.CompletionItemKind.Function, insertText: 'requests.get(${1:url})', insertTextRules: monaco.languages.CompletionItemInsertTextRule.InsertAsSnippet, range: { startLineNumber: position.lineNumber, endLineNumber: position.lineNumber, startColumn: position.column, endColumn: position.column } },
      ]
      return { suggestions }
    }
  })

  editor = monaco.editor.create(editorContainer.value, {
    value: currentContent.value,
    language: 'python',
    theme: 'vs-dark',
    fontSize: 14,
    fontFamily: "'JetBrains Mono', 'Fira Code', Consolas, monospace",
    tabSize: 4,
    insertSpaces: true,
    automaticLayout: true,
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    lineNumbers: 'on',
    renderWhitespace: 'selection',
    bracketPairColorization: { enabled: true },
    padding: { top: 10, bottom: 10 },
  })

  // 监听内容变化
  editor.onDidChangeModelContent(() => {
    currentContent.value = editor!.getValue()
    isDirty.value = currentContent.value !== originalContent.value
    statusText.value = isDirty.value ? '未保存' : '已保存'
    statusClass.value = isDirty.value ? 'unsaved' : 'saved'
  })

  // 监听光标位置
  editor.onDidChangeCursorPosition((e) => {
    cursorPosition.value = { line: e.position.lineNumber, column: e.position.column }
  })
}

function disposeEditor() {
  if (editor) {
    editor.dispose()
    editor = null
  }
}

async function handleFormat() {
  if (!editor) return
  ElMessage.info('格式化功能需要后端支持')
}

async function handleValidate() {
  if (!editor) return
  try {
    const res: any = await validateScript(props.scriptId, currentContent.value)
    if (res.code === 200) {
      ElMessage.success('语法校验通过')
    } else {
      ElMessage.error(res.message || '语法错误')
    }
  } catch (error) {
    ElMessage.error('校验失败')
  }
}

async function handleSave() {
  try {
    await saveScript(props.scriptId, {
      version: newVersion.value,
      content: currentContent.value,
      changelog: changeDesc.value || '脚本更新'
    })
    originalContent.value = currentContent.value
    isDirty.value = false
    statusText.value = '已保存'
    statusClass.value = 'saved'
    ElMessage.success('保存成功')
    emit('success')
    visible.value = false
  } catch (error) {
    ElMessage.error('保存失败')
  }
}

function handleClose(done: () => void) {
  if (isDirty.value) {
    ElMessageBox.confirm('有未保存的更改，确定要关闭吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => done()).catch(() => {})
  } else {
    done()
  }
}

onBeforeUnmount(() => {
  disposeEditor()
})
</script>

<style scoped>
.monaco-container {
  height: calc(100vh - 200px);
  min-height: 400px;
  border: 1px solid #e0e0e0;
  border-radius: 4px;
}

.editor-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e0e0e0;
  background: #fafafa;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.script-name {
  font-weight: 600;
  font-size: 16px;
}

.version-badge {
  background: #e3f2fd;
  color: #1976d2;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.new-version {
  color: #67c23a;
  font-size: 14px;
}

.editor-actions {
  display: flex;
  gap: 8px;
}

.editor-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  border-top: 1px solid #e0e0e0;
  background: #fafafa;
}

.footer-left {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 12px;
  color: #666;
}

.status-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
}

.status-indicator::before {
  content: '';
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.status-indicator.saved::before {
  background: #67c23a;
}

.status-indicator.unsaved::before {
  background: #e6a23c;
}

.change-hint {
  color: #e6a23c;
  font-size: 12px;
}
</style>