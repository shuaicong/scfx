<template>
  <div class="weekday-selector">
    <el-checkbox-group v-model="selected" @change="emit('update:modelValue', $event)">
      <el-checkbox v-for="day in weekdays" :key="day.value" :label="day.value">
        {{ day.label }}
      </el-checkbox>
    </el-checkbox-group>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

const props = defineProps<{
  modelValue: number[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number[]]
}>()

const weekdays = [
  { label: '周一', value: 1 },
  { label: '周二', value: 2 },
  { label: '周三', value: 3 },
  { label: '周四', value: 4 },
  { label: '周五', value: 5 },
  { label: '周六', value: 6 },
  { label: '周日', value: 0 }
]

const selected = ref<number[]>([...props.modelValue])

watch(() => props.modelValue, (val) => {
  selected.value = [...val]
})
</script>

<style scoped>
.weekday-selector {
  display: inline-flex;
  gap: 8px;
}

.weekday-selector :deep(.el-checkbox) {
  margin-right: 0;
}

.weekday-selector :deep(.el-checkbox__label) {
  color: var(--text-primary);
}

.weekday-selector :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
  background-color: var(--accent-blue);
  border-color: var(--accent-blue);
}
</style>