<template>
  <div class="cron-input">
    <el-input
      v-model="expression"
      placeholder="*/5 * * * *"
      @blur="validateAndCalculate"
      @keyup.enter="validateAndCalculate"
    />
    <div v-if="nextTrigger" class="next-trigger">
      下次触发: <span class="time">{{ nextTrigger }}</span>
    </div>
    <div v-if="error" class="error">{{ error }}</div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const expression = ref(props.modelValue)
const nextTrigger = ref('')
const error = ref('')

function validateAndCalculate() {
  error.value = ''
  nextTrigger.value = ''
  emit('update:modelValue', expression.value)
}

// Simple cron parser for display purposes
function parseCron(cron: string): string | null {
  const parts = cron.trim().split(/\s+/)
  if (parts.length !== 5) return null

  const [min, hour, day, month, dow] = parts
  const now = new Date()

  // Very basic validation
  if (!/^(\*|[\d,\-\/]+)$/.test(min)) return null
  if (!/^(\*|[\d,\-\/]+)$/.test(hour)) return null

  // Calculate next trigger (simplified - just parse what we can)
  try {
    const next = new Date(now)
    next.setSeconds(0)
    next.setMilliseconds(0)

    // Add at least 1 minute
    next.setMinutes(next.getMinutes() + 1)

    return next.toLocaleString('zh-CN')
  } catch {
    return null
  }
}

watch(() => props.modelValue, (val) => {
  expression.value = val
})

watch(expression, (val) => {
  emit('update:modelValue', val)
})
</script>

<style scoped>
.cron-input {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.next-trigger {
  font-size: 12px;
  color: var(--text-secondary);
}

.next-trigger .time {
  color: var(--accent-blue);
}

.error {
  font-size: 12px;
  color: var(--accent-red);
}
</style>