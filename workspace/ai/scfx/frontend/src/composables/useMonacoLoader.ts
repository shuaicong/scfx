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

export function useMonacoLoader() {
  function loadMonaco(): Promise<void> {
    if (monacoLoaded) return Promise.resolve()
    if (loadPromise) return loadPromise

    loadPromise = new Promise((resolve, reject) => {
      // If monaco is already loaded
      if (window.monaco) {
        monacoLoaded = true
        resolve()
        return
      }

      // If require exists, configure and load
      if (typeof window.require === 'function') {
        window.require.config({ paths: { vs: MONACO_CDN } })
        window.require(['vs/editor/editor.main'], () => {
          monacoLoaded = true
          resolve()
        })
        return
      }

      // Load loader script
      const script = document.createElement('script')
      script.src = `${MONACO_CDN}/loader.js`
      script.onload = () => {
        window.require.config({ paths: { vs: MONACO_CDN } })
        window.require(['vs/editor/editor.main'], () => {
          monacoLoaded = true
          resolve()
        })
      }
      script.onerror = () => {
        reject(new Error('Failed to load monaco loader'))
      }
      document.head.appendChild(script)
    })

    return loadPromise
  }

  return {
    loadMonaco,
    isLoaded: () => monacoLoaded
  }
}