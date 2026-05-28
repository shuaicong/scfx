# 任务详情页 Monaco Editor 集成计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为任务详情页集成 Monaco Editor，实现 Python 脚本的在线编辑功能

**Architecture:** 使用 CDN 方式加载 Monaco Editor，通过 Vue composable 管理编辑器实例，封装为独立组件用于任务详情页

**Tech Stack:** Vue 3 + Monaco Editor (CDN) + Element Plus

---

## 文件结构

```
frontend/src/
├── composables/
│   └── useMonaco.ts              # Monaco Editor 加载和初始化 hook
├── components/
│   └── ScriptEditor.vue          # Monaco Editor 封装组件
├── views/scripts/
│   ├── TaskDetail.vue            # 任务详情页（修改）
│   └── components/
│       └── TriggerConfig.vue     # 已有触发配置组件
└── api/
    └── index.ts                  # API 定义（修改）
```

---

## Task 1: 创建 Monaco Editor Hook

**Files:**
- Create: `frontend/src/composables/useMonaco.ts`

```typescript
import { ref, onMounted, onUnmounted, type Ref } from 'vue'

// CDN 配置
const MONACO_CDN = 'https://cdn.jsdelivr.net/npm/monaco-editor@0.45.0/min/vs'

declare global {
  interface Window {
    monaco: any
    require: {
      config: (options: { paths: { vs: string } }) => void
      (modules: string[], callback: () => void): void
    }
  }
}

let monacoLoaded = false
let loadPromise: Promise<void> | null = null

function loadMonaco(): Promise<void> {
  if (monacoLoaded) return Promise.resolve()
  if (loadPromise) return loadPromise

  loadPromise = new Promise((resolve, reject) => {
    // 动态加载 loader
    const script = document.createElement('script')
    script.src = `${MONACO_CDN}/loader.js`
    script.onload = () => {
      window.require.config({ paths: { vs: MONACO_CDN } })
      window.require(['vs/editor/editor.main'], () => {
        monacoLoaded = true
        resolve()
      })
    }
    script.onerror = reject
    document.head.appendChild(script)
  })

  return loadPromise
}

export interface EditorOptions {
  value?: string
  language?: string
  readOnly?: boolean
  theme?: string
  height?: string | number
}

export function useMonaco(containerRef: Ref<HTMLElement | null>, options: EditorOptions = {}) {
  const editor = ref<any>(null)
  const loading = ref(false)
  const error = ref<string | null>(null)

  async function initEditor() {
    if (!containerRef.value) return

    loading.value = true
    try {
      await loadMonaco()

      const defaultOptions = {
        value: options.value || '',
        language: options.language || 'python',
        theme: options.theme || 'vs-dark',
        readOnly: options.readOnly || false,
        automaticLayout: true,
        minimap: { enabled: true },
        fontSize: 14,
        fontFamily: "'JetBrains Mono', 'Fira Code', Consolas, monospace",
        lineNumbers: 'on',
        scrollBeyondLastLine: false,
        wordWrap: 'on',
        tabSize: 4,
        insertSpaces: true,
        formatOnPaste: true,
        formatOnType: true,
        renderLineHighlight: 'all',
        cursorBlinking: 'smooth',
        smoothScrolling: true,
      }

      editor.value = window.monaco.editor.create(containerRef.value, {
        ...defaultOptions,
        value: options.value,
        language: options.language,
        theme: options.theme,
      })
    } catch (e) {
      error.value = 'Failed to load Monaco Editor'
      console.error(e)
    } finally {
      loading.value = false
    }
  }

  function setValue(value: string) {
    if (editor.value) {
      editor.value.setValue(value)
    }
  }

  function getValue(): string {
    return editor.value ? editor.value.getValue() : ''
  }

  function dispose() {
    if (editor.value) {
      editor.value.dispose()
      editor.value = null
    }
  }

  onMounted(() => {
    initEditor()
  })

  onUnmounted(() => {
    dispose()
  })

  return {
    editor,
    loading,
    error,
    setValue,
    getValue,
    dispose,
    initEditor,
  }
}
```

---

## Task 2: 创建 ScriptEditor 组件

**Files:**
- Create: `frontend/src/components/ScriptEditor.vue`

```vue
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
import { ref, watch, onMounted, onUnmounted } from 'vue'
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
```

---

## Task 3: 集成到 TaskDetail.vue

**Files:**
- Modify: `frontend/src/views/scripts/TaskDetail.vue`

主要修改：
1. 引入 ScriptEditor 组件
2. 在模板中添加脚本编辑区域
3. 添加保存脚本的逻辑

```vue
<!-- 在 template 中替换或添加脚本编辑区域 -->
<!-- 在 info tab 中添加脚本编辑功能 -->

<template>
  <!-- 其他内容保持不变 -->

  <el-tabs v-model="activeTab" class="detail-tabs">
    <el-tab-pane label="基本信息" name="info">
      <!-- 原有 el-descriptions 保持 -->

      <!-- 新增：脚本配置区域 -->
      <el-card class="script-card" shadow="never">
        <template #header>
          <div class="script-header">
            <span>脚本配置</span>
            <div class="script-actions">
              <el-button size="small" @click="formatScript">格式化</el-button>
              <el-button type="primary" size="small" :disabled="!hasChanges" @click="saveScript">
                保存脚本 Ctrl+S
              </el-button>
            </div>
          </div>
        </template>
        <ScriptEditor
          v-model="scriptContent"
          :read-only="false"
          height="400px"
          @update:modelValue="onScriptChange"
        />
        <div class="script-status">
          <span v-if="hasChanges" class="modified-indicator">已修改</span>
          <span class="cursor-position" v-if="editorInstance">
            行 {{ cursorLine }}, 列 {{ cursorColumn }}
          </span>
        </div>
      </el-card>

      <div class="action-buttons">
        <!-- 原有按钮保持 -->
      </div>
    </el-tab-pane>

    <!-- 其他 tabs 保持不变 -->
  </el-tabs>
</template>

<script setup lang="ts">
// 在原有 import 中添加
import ScriptEditor from '@/components/ScriptEditor.vue'

// 添加新的 ref 和函数
const scriptContent = ref('')
const originalContent = ref('')
const hasChanges = computed(() => scriptContent.value !== originalContent.value)
const cursorLine = ref(1)
const cursorColumn = ref(1)
const editorInstance = ref<any>(null)

// 加载脚本内容
async function loadScriptContent() {
  try {
    const res = await scriptApi.getContent(scriptId.value)
    scriptContent.value = res.data
    originalContent.value = res.data
  } catch (e) {
    ElMessage.error('加载脚本内容失败')
  }
}

// 脚本内容变化
function onScriptChange(value: string) {
  scriptContent.value = value
  updateCursorPosition()
}

// 更新光标位置
function updateCursorPosition() {
  const editor = editorInstance.value
  if (editor) {
    const position = editor.getPosition()
    if (position) {
      cursorLine.value = position.lineNumber
      cursorColumn.value = position.column
    }
  }
}

// 保存脚本
async function saveScript() {
  try {
    await scriptApi.updateContent(scriptId.value, scriptContent.value)
    originalContent.value = scriptContent.value
    ElMessage.success('脚本保存成功')
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

// 格式化脚本
function formatScript() {
  // Monaco Editor 内置格式化
  const editor = editorInstance.value
  if (editor) {
    editor.getAction('editor.action.formatDocument')?.run()
  }
}

// 键盘快捷键
function handleKeyDown(e: KeyboardEvent) {
  if (e.ctrlKey && e.key === 's') {
    e.preventDefault()
    saveScript()
  }
}

onMounted(async () => {
  if (!isCreateMode.value) {
    await loadScript()
    await loadScriptContent()
  }
  document.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeyDown)
})
</script>

<style scoped>
/* 添加样式 */
.script-card {
  margin-top: 20px;
  background: #1e1e1e;
  border: 1px solid #30363d;
}

.script-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.script-actions {
  display: flex;
  gap: 8px;
}

.script-status {
  margin-top: 8px;
  font-size: 12px;
  color: #8b949e;
  display: flex;
  justify-content: space-between;
}

.modified-indicator {
  color: #f0883e;
}
</style>
```

---

## Task 4: 更新 API 定义

**Files:**
- Modify: `frontend/src/api/index.ts`

```typescript
// 在 scriptApi 中添加 updateContent 方法（如果不存在）
// 已有定义：
// updateContent: (id: number, scriptContent: string) =>
//   request.put<{ data: CollectionScript }>(`/scripts/${id}/content`, { scriptContent }),
```

---

## Task 5: 验证和测试

**Files:**
- Test: 浏览器访问 `http://localhost:3000/#/scripts/1`

### 测试步骤

1. **页面加载**
   - [ ] Monaco Editor 正确加载（显示深色主题）
   - [ ] Python 语法高亮正常

2. **编辑功能**
   - [ ] 可以修改脚本内容
   - [ ] 显示"已修改"指示器
   - [ ] Ctrl+S 保存成功

3. **其他功能**
   - [ ] 触发配置正常显示
   - [ ] 执行记录正常显示
   - [ ] 版本历史正常显示

---

## 实现顺序

1. ✅ Task 1: 创建 useMonaco hook
2. ✅ Task 2: 创建 ScriptEditor 组件
3. ✅ Task 3: 集成到 TaskDetail.vue
4. ✅ Task 4: 更新 API 定义（检查是否已存在）
5. ✅ Task 5: 验证和测试

---

## 注意事项

1. Monaco Editor 加载需要网络访问 CDN
2. 如果离线环境，需要将 Monaco Editor 打包到本地
3. Python 语法高亮是内置支持的，不需要额外配置
4. `vs-dark` 主题与设计文档的深色主题一致

---

## 后续扩展（可选）

- 添加代码自动补全
- 添加 Python 代码检查（lint）
- 添加版本对比功能
- 集成实时日志面板

---

**Plan complete and saved to `docs/superpowers/plans/2026-05-07-taskdetail-monaco-editor.md`**

---

## 执行选项

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?