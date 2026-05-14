import { onMounted, onUnmounted } from 'vue'

export function useShortcut(key: string, callback: () => void, modifiers: { ctrl?: boolean; alt?: boolean; shift?: boolean; meta?: boolean } = {}) {
  const handler = (e: KeyboardEvent) => {
    const ctrlMatch = modifiers.ctrl ? (e.ctrlKey || e.metaKey) : !e.ctrlKey && !e.metaKey
    const altMatch = modifiers.alt ? e.altKey : !e.altKey
    const shiftMatch = modifiers.shift ? e.shiftKey : !e.shiftKey

    if (e.key.toLowerCase() === key.toLowerCase() && ctrlMatch && altMatch && shiftMatch) {
      e.preventDefault()
      callback()
    }
  }

  onMounted(() => document.addEventListener('keydown', handler))
  onUnmounted(() => document.removeEventListener('keydown', handler))
}