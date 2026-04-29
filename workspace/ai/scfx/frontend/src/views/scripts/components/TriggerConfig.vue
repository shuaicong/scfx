<template>
  <div class="trigger-config">
    <el-tabs v-model="triggerType" @tab-change="handleTriggerTypeChange">
      <el-tab-pane label="简单配置" name="simple">
        <div v-if="triggerType === 'simple'" class="simple-config">
          <el-form-item label="触发方式">
            <el-radio-group v-model="repeatType">
              <el-radio value="daily">每日</el-radio>
              <el-radio value="weekly">每周</el-radio>
              <el-radio value="monthly">每月</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="执行时间">
            <el-time-picker
              v-model="repeatTime"
              format="HH:mm:ss"
              value-format="HH:mm:ss"
              placeholder="选择时间"
            />
          </el-form-item>

          <el-form-item v-if="repeatType === 'weekly'" label="选择星期">
            <el-checkbox-group v-model="selectedWeekdays">
              <el-checkbox :value="1">周一</el-checkbox>
              <el-checkbox :value="2">周二</el-checkbox>
              <el-checkbox :value="3">周三</el-checkbox>
              <el-checkbox :value="4">周四</el-checkbox>
              <el-checkbox :value="5">周五</el-checkbox>
              <el-checkbox :value="6">周六</el-checkbox>
              <el-checkbox :value="7">周日</el-checkbox>
            </el-checkbox-group>
          </el-form-item>

          <el-form-item v-if="repeatType === 'monthly'" label="执行日期">
            <el-radio-group v-model="monthlyOption">
              <el-radio value="day">每月 <el-input-number v-model="monthlyDay" :min="1" :max="31" size="small" /> 日</el-radio>
              <el-radio value="last">每月最后一天</el-radio>
            </el-radio-group>
          </el-form-item>
        </div>
      </el-tab-pane>

      <el-tab-pane label="Cron 表达式" name="cron">
        <div v-if="triggerType === 'cron'" class="cron-config">
          <el-form-item label="Cron 表达式">
            <el-input v-model="cronExpression" placeholder="0 8 * * *" style="width: 200px" />
            <el-button @click="validateCron">校验</el-button>
            <el-button @click="showTemplates = true">常用模板</el-button>
          </el-form-item>
          <el-form-item v-if="cronDescription" label="表达式说明">
            <span>{{ cronDescription }}</span>
          </el-form-item>

          <div v-if="showTemplates" class="templates">
            <el-tag v-for="t in cronTemplates" :key="t.value" class="template-tag" @click="selectTemplate(t)">
              {{ t.label }}
            </el-tag>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-form-item label="结束时间" class="end-time">
      <el-radio-group v-model="endType">
        <el-radio value="never">永不结束</el-radio>
        <el-radio value="date">于指定日结束</el-radio>
        <el-radio value="count">重复 N 次后结束</el-radio>
      </el-radio-group>
    </el-form-item>
    <el-form-item v-if="endType === 'date'" label="">
      <el-date-picker v-model="endTime" type="date" placeholder="选择日期" />
    </el-form-item>
    <el-form-item v-if="endType === 'count'" label="">
      <el-input-number v-model="repeatCount" :min="1" /> 次
    </el-form-item>

    <div v-if="nextExecutions.length > 0" class="preview">
      <div class="preview-title">预览未来5次触发时间</div>
      <div v-for="(time, idx) in nextExecutions" :key="idx" class="preview-item">
        {{ idx + 1 }}. {{ time }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { cronApi } from '@/api'

const emit = defineEmits(['update:modelValue'])

const props = defineProps<{
  modelValue: {
    triggerType: string
    repeatType?: string
    repeatTime?: string
    weeklyDays?: string
    monthlyDay?: number
    monthlyLastDay?: boolean
    cronExpression?: string
    endType?: string
    endTime?: string
    repeatCount?: number
  }
}>()

const triggerType = ref(props.modelValue?.triggerType || 'simple')
const repeatType = ref(props.modelValue?.repeatType || 'daily')
const repeatTime = ref(props.modelValue?.repeatTime || '08:00:00')
const selectedWeekdays = ref<number[]>([])
const monthlyOption = ref('day')
const monthlyDay = ref(props.modelValue?.monthlyDay || 1)
const cronExpression = ref(props.modelValue?.cronExpression || '0 8 * * *')
const endType = ref(props.modelValue?.endType || 'never')
const endTime = ref(props.modelValue?.endTime)
const repeatCount = ref(props.modelValue?.repeatCount)
const cronDescription = ref('')
const showTemplates = ref(false)
const nextExecutions = ref<string[]>([])

const cronTemplates = [
  { label: '每5分钟', value: '*/5 * * * *' },
  { label: '每15分钟', value: '*/15 * * * *' },
  { label: '每30分钟', value: '*/30 * * * *' },
  { label: '每小时', value: '0 * * * *' },
  { label: '每天8点', value: '0 8 * * *' },
  { label: '每天9点', value: '0 9 * * *' },
  { label: '每周一9点', value: '0 9 * * 1' },
  { label: '每月1号8点', value: '0 8 1 * *' },
]

function handleTriggerTypeChange() {
  nextExecutions.value = []
}

function selectTemplate(t: { value: string }) {
  cronExpression.value = t.value
  validateCron()
}

async function validateCron() {
  try {
    const res = await cronApi.validate(cronExpression.value)
    if (res.data.valid) {
      cronDescription.value = res.data.description || ''
      nextExecutions.value = res.data.nextExecutions || []
    } else {
      cronDescription.value = '表达式错误: ' + (res.data.error || '')
      nextExecutions.value = []
    }
  } catch (e) {
    cronDescription.value = '校验失败'
  }
}

watch([triggerType, repeatType, repeatTime, selectedWeekdays, monthlyOption, monthlyDay, cronExpression, endType, endTime, repeatCount], () => {
  emit('update:modelValue', {
    triggerType: triggerType.value === 'simple' ? 'repeat' : 'cron',
    repeatType: repeatType.value,
    repeatTime: repeatTime.value,
    weeklyDays: selectedWeekdays.value.join(','),
    monthlyDay: monthlyDay.value,
    monthlyLastDay: monthlyOption.value === 'last',
    cronExpression: cronExpression.value,
    endType: endType.value,
    endTime: endTime.value,
    repeatCount: repeatCount.value
  })
})
</script>

<style scoped>
.template-tag {
  margin: 4px;
  cursor: pointer;
}
.preview {
  margin-top: 16px;
  padding: 12px;
  background: #f5f7fa;
  border-radius: 4px;
}
.preview-title {
  font-weight: bold;
  margin-bottom: 8px;
}
.preview-item {
  font-size: 14px;
  color: #666;
}
</style>