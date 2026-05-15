<template>
  <div class="cron-input">
    <div class="quick-select">
      <el-select v-model="selectedTemplate" placeholder="快速选择" clearable @change="handleTemplateChange">
        <el-option label="每天9:30" value="0 30 9 * * ?" />
        <el-option label="每天18:00" value="0 0 18 * * ?" />
        <el-option label="每周一9:00" value="0 0 9 ? * MON" />
        <el-option label="每月1日9:00" value="0 0 9 1 * ?" />
        <el-option label="工作日9:30" value="0 30 9 * * MON-FRI" />
      </el-select>
    </div>

    <div class="cron-input-wrapper">
      <el-input
        v-model="cronExpression"
        placeholder="0 30 9 * * ?"
        @input="handleCronInput"
        @blur="handleCronBlur"
      />
      <el-button @click="handleValidate">校验</el-button>
    </div>

    <div class="validation-result" v-if="validationResult">
      <div v-if="validationResult.valid" class="valid">
        <span class="icon">✓</span>
        <span class="description">{{ validationResult.description }}</span>
        <div class="next-executions">
          <p>未来5次触发时间：</p>
          <ul>
            <li v-for="(time, index) in validationResult.nextExecutions" :key="index">{{ time }}</li>
          </ul>
        </div>
      </div>
      <div v-else class="invalid">
        <span class="icon">✗</span>
        <span class="error">{{ validationResult.error }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { scriptApi } from '@/api/scripts'

const props = defineProps<{
  modelValue: string
}>()

const emit = defineEmits(['update:modelValue'])

const cronExpression = ref(props.modelValue)
const selectedTemplate = ref('')
const validationResult = ref<any>(null)
let debounceTimer: number | null = null

watch(() => props.modelValue, (val) => {
  cronExpression.value = val
})

function handleTemplateChange(template: string) {
  if (template) {
    cronExpression.value = template
    emit('update:modelValue', template)
    validateCron()
  }
}

function handleCronInput() {
  // 防抖 300ms
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = window.setTimeout(() => {
    emit('update:modelValue', cronExpression.value)
    validateCron()
  }, 300)
}

function handleCronBlur() {
  if (debounceTimer) {
    clearTimeout(debounceTimer)
    emit('update:modelValue', cronExpression.value)
    validateCron()
  }
}

async function handleValidate() {
  await validateCron()
}

async function validateCron() {
  if (!cronExpression.value) {
    validationResult.value = null
    return
  }

  try {
    const res = await scriptApi.validateCron(cronExpression.value)
    validationResult.value = res.data
  } catch {
    validationResult.value = { valid: false, error: '校验失败' }
  }
}
</script>

<style scoped>
.cron-input {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.quick-select {
  margin-bottom: 10px;
}

.cron-input-wrapper {
  display: flex;
  gap: 10px;
}

.validation-result {
  padding: 10px;
  border-radius: 4px;
}

.validation-result.valid {
  background: #f6ffed;
  border: 1px solid #b7eb8f;
}

.validation-result.invalid {
  background: #fff2f0;
  border: 1px solid #ffbb96;
}

.icon {
  font-weight: bold;
  margin-right: 8px;
}

.valid .icon { color: #52c41a; }
.invalid .icon { color: #ff4d4f; }

.next-executions ul {
  margin: 5px 0 0 20px;
}
</style>