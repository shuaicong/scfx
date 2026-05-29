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