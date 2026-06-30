<template>
  <el-dialog
    :model-value="visible"
    title="插入图表"
    width="700px"
    :close-on-click-modal="false"
    append-to-body
    class="dark-dialog"
    @update:model-value="$emit('update:visible', $event)"
  >
    <div class="dialog-body">
      <div class="config-grid">
        <!-- Variety -->
        <div class="config-item">
          <label class="config-label">品种</label>
          <el-select v-model="form.variety" class="config-select">
            <el-option label="玉米" value="corn" />
            <el-option label="稻米" value="rice" />
            <el-option label="小麦" value="wheat" />
          </el-select>
        </div>

        <!-- Region -->
        <div class="config-item">
          <label class="config-label">港口/区域</label>
          <el-select v-model="form.region" class="config-select">
            <el-option label="锦州港" value="jinzhou" />
            <el-option label="蛇口港" value="shekou" />
            <el-option label="海口港" value="haikou" />
            <el-option label="鲅鱼圈" value="bayuquan" />
          </el-select>
        </div>

        <!-- Date Range -->
        <div class="config-item">
          <label class="config-label">时间范围</label>
          <el-select v-model="form.dateRange" class="config-select">
            <el-option label="近 7 天" value="7" />
            <el-option label="近 30 天" value="30" />
            <el-option label="近 90 天" value="90" />
          </el-select>
        </div>

        <!-- Chart Type -->
        <div class="config-item">
          <label class="config-label">图表类型</label>
          <el-select v-model="form.chartType" class="config-select">
            <el-option label="折线图" value="line" />
            <el-option label="数据表格" value="table" />
          </el-select>
        </div>
      </div>

      <!-- Preview Area -->
      <div class="preview-section">
        <div class="preview-title">预览</div>
        <div class="preview-area">
          <div v-if="form.chartType === 'line'" class="chart-placeholder">
            <div class="echarts-preview" ref="chartPreviewRef">
              <EChartsLine
                :title="`${getVarietyLabel(form.variety)} - ${getRegionLabel(form.region)} 价格走势`"
                :x-axis="previewData.xAxis"
                :series="previewData.series"
                :height="260"
              />
            </div>
          </div>
          <div v-else class="table-preview">
            <table class="preview-table">
              <thead>
                <tr>
                  <th>日期</th>
                  <th>{{ getRegionLabel(form.region) }} (元/吨)</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(date, idx) in previewData.xAxis" :key="date">
                  <td>{{ date }}</td>
                  <td>{{ previewData.series[0]?.data[idx] || '--' }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <!-- Placeholder Preview -->
      <div class="placeholder-preview">
        <span class="ph-label">将插入：</span>
        <code class="ph-code">{{ placeholderText }}</code>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" @click="handleInsert">插入到文档</el-button>
    </template>
  </el-dialog>
</template>

<style>
@import '@/styles/dark-theme.css';
</style>

<script setup lang="ts">
import { reactive, computed, watch } from 'vue'
import EChartsLine from '@/views/ai-chat/components/EChartsLine.vue'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'insert': [placeholder: string]
}>()

const form = reactive({
  variety: 'corn',
  region: 'jinzhou',
  dateRange: '30',
  chartType: 'line',
})

// Preview data (mock)
const previewData = reactive({
  xAxis: [] as string[],
  series: [{ name: '', data: [] as number[] }],
})

function generatePreviewData() {
  const days = parseInt(form.dateRange)
  const now = new Date()
  const dates: string[] = []
  const values: number[] = []

  for (let i = days - 1; i >= 0; i--) {
    const d = new Date(now)
    d.setDate(d.getDate() - i)
    dates.push(`${d.getMonth() + 1}/${d.getDate()}`)
    values.push(Math.round(2200 + Math.random() * 300))
  }

  previewData.xAxis = dates
  previewData.series = [{ name: getRegionLabel(form.region), data: values }]
}

// Generate on mount and when form changes
watch(() => [form.variety, form.region, form.dateRange, form.chartType], () => {
  generatePreviewData()
}, { immediate: true })

function getVarietyLabel(v: string): string {
  const map: Record<string, string> = { corn: '玉米', rice: '稻米', wheat: '小麦' }
  return map[v] || v
}

function getRegionLabel(r: string): string {
  const map: Record<string, string> = {
    jinzhou: '锦州港',
    shekou: '蛇口港',
    haikou: '海口港',
    bayuquan: '鲅鱼圈',
  }
  return map[r] || r
}

const placeholderText = computed(() => {
  return `{{PRICE_CHART:${form.variety},${form.region},${form.dateRange}d,${form.chartType}}}`
})

function handleCancel() {
  emit('update:visible', false)
}

function handleInsert() {
  emit('insert', placeholderText.value)
  emit('update:visible', false)
}
</script>

<style scoped>
.dialog-body {
  padding: 4px 0;
}

/* Config Grid */
.config-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 16px;
}

.config-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.config-label {
  font-size: 11px;
  color: var(--text-muted);
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.config-select {
  width: 100%;
}

/* Preview Section */
.preview-section {
  background: var(--bg-primary);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
}

.preview-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 8px;
}

.preview-area {
  min-height: 200px;
}

.chart-placeholder {
  width: 100%;
}

.echarts-preview {
  width: 100%;
}

/* Table Preview */
.table-preview {
  max-height: 280px;
  overflow-y: auto;
}

.preview-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.preview-table th {
  padding: 8px 12px;
  text-align: left;
  background: var(--bg-tertiary);
  color: var(--text-primary);
  font-weight: 600;
  border-bottom: 1px solid var(--border-color);
}

.preview-table td {
  padding: 6px 12px;
  color: var(--text-secondary);
  border-bottom: 1px solid var(--border-color);
}

/* Placeholder Preview */
.placeholder-preview {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg-tertiary);
  border-radius: 4px;
}

.ph-label {
  font-size: 11px;
  color: var(--text-muted);
  flex-shrink: 0;
}

.ph-code {
  font-size: 12px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  color: var(--accent);
  background: rgba(245, 200, 122, 0.1);
  padding: 2px 6px;
  border-radius: 3px;
}

/* Dark Dialog Overrides */
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
</style>
