<template>
  <el-dialog
    :model-value="visible"
    title="AI 生成报告"
    width="560px"
    :close-on-click-modal="false"
    :close-on-press-escape="!isGenerating"
    append-to-body
    class="dark-dialog"
    @update:model-value="$emit('update:visible', $event)"
  >
    <!-- Step 1: Configuration -->
    <div v-if="step === 'config'" class="dialog-body">
      <div class="dialog-field">
        <label>选择模板</label>
        <el-select v-model="form.templateId" placeholder="请选择模板" class="dialog-select" @change="handleTemplateChange">
          <el-option
            v-for="tpl in templates"
            :key="tpl.id"
            :label="tpl.name"
            :value="tpl.id"
          >
            <span class="tpl-option">{{ getVarietyEmoji(tpl.variety) }} {{ tpl.name }}</span>
          </el-option>
        </el-select>
      </div>

      <div class="dialog-field">
        <label>报告标题</label>
        <el-input v-model="form.title" placeholder="输入报告标题" maxlength="200" />
      </div>

      <div class="dialog-field">
        <label>数据时间范围</label>
        <el-select v-model="form.dateRange" class="dialog-select">
          <el-option label="近 7 天" value="7" />
          <el-option label="近 30 天" value="30" />
          <el-option label="近 90 天" value="90" />
          <el-option label="自定义" value="custom" />
        </el-select>
      </div>

      <div class="dialog-field">
        <label>补充说明（可选）</label>
        <el-input
          v-model="form.instructions"
          type="textarea"
          :rows="4"
          placeholder="可输入额外要求，如：重点关注东北产区天气对玉米价格的影响"
          maxlength="500"
        />
      </div>
    </div>

    <!-- Step 2: Progress -->
    <div v-else-if="step === 'progress'" class="dialog-body">
      <div class="progress-title">正在生成报告...</div>

      <div class="progress-steps">
        <div
          v-for="(item, idx) in progressSteps"
          :key="idx"
          class="progress-step"
          :class="{
            active: item.status === 'active',
            done: item.status === 'done',
            error: item.status === 'error',
          }"
        >
          <div class="step-indicator">
            <span v-if="item.status === 'active'" class="step-spinner"></span>
            <span v-else-if="item.status === 'done'" class="step-check">✓</span>
            <span v-else-if="item.status === 'error'" class="step-x">✕</span>
            <span v-else class="step-num">{{ idx + 1 }}</span>
          </div>
          <div class="step-content">
            <div class="step-label">{{ item.label }}</div>
            <div class="step-desc">{{ item.description }}</div>
          </div>
        </div>
      </div>

      <div v-if="progressError" class="progress-error">
        {{ progressError }}
      </div>
    </div>

    <template #footer>
      <template v-if="step === 'config'">
        <el-button @click="handleCancel">取消</el-button>
        <el-button type="primary" :loading="isGenerating" @click="handleStart">
          开始生成
        </el-button>
      </template>
      <template v-else>
        <el-button v-if="progressError" @click="step = 'config'">返回修改</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<style>
@import '@/styles/dark-theme.css';
</style>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { reportApi } from '@/api/report'

const props = defineProps<{
  visible: boolean
  templateId?: number | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'created': [reportId: number]
}>()

const router = useRouter()

// ── State ──
const step = ref<'config' | 'progress'>('config')
const isGenerating = ref(false)
const progressError = ref('')
const templates = ref<any[]>([])
let pollTimer: ReturnType<typeof setInterval> | null = null

const form = reactive({
  templateId: props.templateId || null as number | null,
  title: '',
  dateRange: '30',
  instructions: '',
})

// ── Progress Steps ──
interface ProgressStep {
  label: string
  description: string
  status: 'pending' | 'active' | 'done' | 'error'
}

const progressSteps = ref<ProgressStep[]>([
  { label: '价格数据采集', description: '从数据源获取最新价格数据', status: 'pending' },
  { label: '知识库检索', description: '搜索关联文档和政策信息', status: 'pending' },
  { label: 'HTML 生成', description: 'AI 生成报告正文', status: 'pending' },
  { label: '图表渲染', description: '渲染走势图和对比图', status: 'pending' },
])

let createdReportId: number | null = null

// ── Methods ──
function getVarietyEmoji(variety: string): string {
  if (variety === 'corn') return '🌽'
  if (variety === 'rice') return '🌾'
  if (variety === 'wheat') return '🌾'
  return '📄'
}

function handleTemplateChange(id: number) {
  const tpl = templates.value.find((t: any) => t.id === id)
  if (tpl && !form.title) {
    form.title = `${tpl.name} - ${new Date().toLocaleDateString('zh-CN')}`
  }
}

async function loadTemplates() {
  try {
    const res = await reportApi.templateList()
    templates.value = (res as any).data || []
  } catch {
    templates.value = []
  }
}

function resetProgress() {
  progressSteps.value = [
    { label: '价格数据采集', description: '从数据源获取最新价格数据', status: 'pending' },
    { label: '知识库检索', description: '搜索关联文档和政策信息', status: 'pending' },
    { label: 'HTML 生成', description: 'AI 生成报告正文', status: 'pending' },
    { label: '图表渲染', description: '渲染走势图和对比图', status: 'pending' },
  ]
  progressError.value = ''
  createdReportId = null
}

async function handleStart() {
  if (!form.templateId) {
    ElMessage.warning('请选择模板')
    return
  }
  if (!form.title.trim()) {
    ElMessage.warning('请输入报告标题')
    return
  }

  isGenerating.value = true
  step.value = 'progress'
  resetProgress()
  progressSteps.value[0].status = 'active'

  try {
    // Step 1: Create report first
    const res = await reportApi.create({
      templateId: form.templateId,
      title: form.title.trim(),
    })
    const created = (res as any).data || {}
    createdReportId = created.id

    // Step 2: Start generation
    progressSteps.value[0].status = 'done'
    progressSteps.value[1].status = 'active'

    await reportApi.generate(createdReportId, {
      dateRange: form.dateRange,
      instructions: form.instructions,
    })

    progressSteps.value[1].status = 'done'
    progressSteps.value[2].status = 'active'

    // Start polling for generation status
    startPolling(createdReportId)
  } catch (e: any) {
    progressError.value = e.message || '启动生成失败'
    progressSteps.value[0].status = 'error'
    isGenerating.value = false
  }
}

async function startPolling(reportId: number) {
  if (pollTimer) clearInterval(pollTimer)

  pollTimer = setInterval(async () => {
    try {
      const statusRes = await reportApi.generationStatus(reportId)
      const status = (statusRes as any).data || {}

      // Map backend status to our steps
      if (status.currentStep === 'collecting_data' || status.currentStep === 'price_data') {
        progressSteps.value[0].status = 'done'
        progressSteps.value[1].status = 'active'
      } else if (status.currentStep === 'knowledge_search' || status.currentStep === 'knowledge') {
        progressSteps.value[0].status = 'done'
        progressSteps.value[1].status = 'done'
        progressSteps.value[2].status = 'active'
      } else if (status.currentStep === 'generating_html' || status.currentStep === 'html_generate') {
        progressSteps.value[0].status = 'done'
        progressSteps.value[1].status = 'done'
        progressSteps.value[2].status = 'done'
        progressSteps.value[3].status = 'active'
      } else if (status.currentStep === 'rendering_charts' || status.currentStep === 'charts') {
        progressSteps.value[0].status = 'done'
        progressSteps.value[1].status = 'done'
        progressSteps.value[2].status = 'done'
        progressSteps.value[3].status = 'done'
      }

      // Check completion
      if (status.status === 'published' || status.status === 'completed' || status.progress === 100) {
        progressSteps.value.forEach(s => { if (s.status === 'active') s.status = 'done' })
        completeGeneration(reportId)
      }

      // Check error
      if (status.status === 'failed' || status.status === 'error') {
        const errorStep = progressSteps.value.find(s => s.status === 'active')
        if (errorStep) errorStep.status = 'error'
        progressError.value = status.error || status.message || '生成过程出错'
        stopPolling()
        isGenerating.value = false
      }
    } catch (e: any) {
      // Ignore polling errors, continue
    }
  }, 2000) // Poll every 2s
}

function completeGeneration(reportId: number) {
  stopPolling()
  isGenerating.value = false
  ElMessage.success('报告生成完成')
  emit('created', reportId)
  emit('update:visible', false)
  // Navigate to editor
  router.push(`/reports/editor/${reportId}`)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function handleCancel() {
  emit('update:visible', false)
}

// ── Lifecycle ──
onMounted(() => {
  loadTemplates()
  if (props.templateId) {
    form.templateId = props.templateId
  }
})

onBeforeUnmount(() => {
  stopPolling()
})
</script>

<style scoped>
.dialog-body {
  padding: 4px 0;
}

.dialog-field {
  margin-bottom: 16px;
}

.dialog-field label {
  display: block;
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 6px;
  font-weight: 500;
}

.dialog-select {
  width: 100%;
}

.tpl-option {
  font-size: 13px;
}

/* ── Progress ── */
.progress-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 20px;
  text-align: center;
}

.progress-steps {
  display: flex;
  flex-direction: column;
  gap: 0;
  position: relative;
  padding-left: 20px;
}

.progress-step {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 12px 0;
  position: relative;
}

.progress-step:not(:last-child)::before {
  content: '';
  position: absolute;
  left: 19px;
  top: 40px;
  bottom: -4px;
  width: 2px;
  background: var(--border-color);
}

.progress-step.done:not(:last-child)::before {
  background: var(--accent-green);
}

.step-indicator {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  border: 2px solid var(--border-color);
  color: var(--text-muted);
  background: var(--bg-card);
  z-index: 1;
}

.progress-step.done .step-indicator {
  border-color: var(--accent-green);
  background: rgba(63, 185, 80, 0.15);
  color: var(--accent-green);
}

.progress-step.active .step-indicator {
  border-color: var(--accent-blue);
  background: rgba(88, 166, 255, 0.15);
  color: var(--accent-blue);
}

.progress-step.error .step-indicator {
  border-color: var(--accent-red);
  background: rgba(248, 81, 73, 0.15);
  color: var(--accent-red);
}

.step-spinner {
  display: inline-block;
  width: 14px;
  height: 14px;
  border: 2px solid var(--accent-blue);
  border-top-color: transparent;
  border-radius: 50%;
  animation: stepSpin 0.7s linear infinite;
}

@keyframes stepSpin {
  to { transform: rotate(360deg); }
}

.step-check {
  font-size: 14px;
}

.step-x {
  font-size: 14px;
}

.step-num {
  font-size: 12px;
}

.step-content {
  flex: 1;
  padding-top: 3px;
}

.step-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 2px;
}

.step-desc {
  font-size: 11px;
  color: var(--text-muted);
}

.progress-step.done .step-label {
  color: var(--accent-green);
}

.progress-step.active .step-label {
  color: var(--accent-blue);
}

.progress-step.error .step-label {
  color: var(--accent-red);
}

.progress-error {
  margin-top: 16px;
  padding: 10px 14px;
  background: rgba(248, 81, 73, 0.1);
  border: 1px solid rgba(248, 81, 73, 0.3);
  border-radius: 6px;
  color: var(--accent-red);
  font-size: 12px;
  line-height: 1.5;
}

/* ── Dark Dialog ── */
:global(.dark-dialog .el-dialog) {
  --el-dialog-bg-color: var(--bg-secondary);
  --el-dialog-title-font-size: 16px;
  --el-dialog-padding-primary: 20px;
}

:global(.dark-dialog .el-dialog__header) {
  border-bottom: 1px solid var(--border-color);
  padding: 16px 20px;
  margin: 0;
}

:global(.dark-dialog .el-dialog__title) {
  color: var(--text-primary);
  font-size: 16px;
  font-weight: 600;
}

:global(.dark-dialog .el-dialog__body) {
  padding: 20px;
}

:global(.dark-dialog .el-dialog__footer) {
  border-top: 1px solid var(--border-color);
  padding: 12px 20px;
}

:global(.dark-dialog .el-dialog__close) {
  color: var(--text-muted);
}

:global(.dark-dialog .el-dialog__close:hover) {
  color: var(--text-primary);
}

:global(.dark-dialog .el-input__wrapper) {
  background: var(--bg-card) !important;
  box-shadow: 0 0 0 1px var(--border-color) inset !important;
}

:global(.dark-dialog .el-input__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--accent-blue) inset !important;
}

:global(.dark-dialog .el-input__inner) {
  color: var(--text-primary) !important;
}

:global(.dark-dialog .el-textarea__inner) {
  background: var(--bg-card) !important;
  border: 1px solid var(--border-color) !important;
  color: var(--text-primary) !important;
}

:global(.dark-dialog .el-select-dropdown) {
  background: var(--bg-secondary) !important;
  border: 1px solid var(--border-color) !important;
}

:global(.dark-dialog .el-select-dropdown .el-select-dropdown__item) {
  color: var(--text-secondary) !important;
}

:global(.dark-dialog .el-select-dropdown .el-select-dropdown__item.hover) {
  background: var(--bg-hover) !important;
  color: var(--text-primary) !important;
}

:global(.dark-dialog .el-select-dropdown .el-select-dropdown__item.selected) {
  color: var(--accent-blue) !important;
}
</style>
