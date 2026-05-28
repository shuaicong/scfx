<template>
  <div class="alert-rules-page">
    <div class="page-header">
      <h2 class="page-title">告警规则</h2>
      <el-button type="primary" @click="openCreateDialog">新建规则</el-button>
    </div>

    <div class="table-container">
      <table class="data-table">
        <thead>
          <tr>
            <th>规则名称</th>
            <th>类型</th>
            <th>条件</th>
            <th>状态</th>
            <th>更新时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="rule in rules" :key="rule.id" class="table-row">
            <td class="name-cell">{{ rule.ruleName }}</td>
            <td>
              <span class="type-badge">{{ ruleTypeLabel(rule.ruleType) }}</span>
            </td>
            <td class="condition-cell">{{ conditionSummary(rule) }}</td>
            <td>
              <span class="status-badge" :class="rule.enabled ? 'enabled' : 'disabled'">
                {{ rule.enabled ? '启用' : '禁用' }}
              </span>
            </td>
            <td class="time-cell">{{ formatTime(rule.updatedAt) }}</td>
            <td>
              <div class="action-buttons">
                <button class="action-btn" :class="rule.enabled ? 'warning' : 'success'"
                  @click="toggleRule(rule)">
                  {{ rule.enabled ? '禁用' : '启用' }}
                </button>
                <button class="action-btn primary" @click="openEditDialog(rule)">编辑</button>
                <button class="action-btn danger" @click="deleteRule(rule)">删除</button>
              </div>
            </td>
          </tr>
          <tr v-if="!loading && rules.length === 0">
            <td colspan="6" class="empty-cell">暂无告警规则</td>
          </tr>
        </tbody>
      </table>

      <div class="pagination">
        <div class="page-info">共 {{ total }} 条</div>
      </div>
    </div>

    <!-- Create/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingRule ? '编辑规则' : '新建规则'"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form :model="form" label-position="top" class="rule-form">
        <el-form-item label="规则名称">
          <el-input v-model="form.ruleName" placeholder="请输入规则名称" />
        </el-form-item>
        <el-form-item label="规则类型">
          <el-select v-model="form.ruleType" class="full-width" :disabled="!!editingRule">
            <el-option label="连续失败检测" value="CONTINUOUS_FAIL" />
            <el-option label="采集器离线检测" value="SERVICE_OFFLINE" />
          </el-select>
        </el-form-item>
        <el-form-item label="条件配置" v-if="form.ruleType === 'CONTINUOUS_FAIL'">
          <div class="condition-config">
            <span>连续失败</span>
            <el-input-number v-model="threshold" :min="1" :max="20" size="small" />
            <span>次后触发告警</span>
          </div>
        </el-form-item>
        <el-form-item label="条件配置" v-else-if="form.ruleType === 'SERVICE_OFFLINE'">
          <div class="condition-config">
            <span>采集器心跳超过</span>
            <el-input-number v-model="offlineMinutes" :min="1" :max="60" size="small" />
            <span>分钟未更新视为离线</span>
          </div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveRule" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style>
@import '@/styles/dark-theme.css';
</style>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { alertApi } from '@/api/alert'
import type { AlertRule } from '@/api/alert'

const rules = ref<AlertRule[]>([])
const loading = ref(false)
const saving = ref(false)
const total = ref(0)
const dialogVisible = ref(false)
const editingRule = ref<AlertRule | null>(null)
const threshold = ref(3)
const offlineMinutes = ref(2)

const form = reactive<AlertRule>({
  ruleName: '',
  ruleType: 'CONTINUOUS_FAIL',
  condition: '{"threshold":3}',
  enabled: 1,
})

function ruleTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    CONTINUOUS_FAIL: '连续失败检测',
    SERVICE_OFFLINE: '采集器离线检测',
  }
  return labels[type] || type
}

function conditionSummary(rule: AlertRule): string {
  try {
    const cond = JSON.parse(rule.condition)
    if (rule.ruleType === 'CONTINUOUS_FAIL') {
      return `连续失败 ${cond.threshold || 3} 次`
    }
    if (rule.ruleType === 'SERVICE_OFFLINE') {
      return `离线阈值 ${cond.timeoutMinutes || 2} 分钟`
    }
  } catch (_) {}
  return rule.condition
}

function formatTime(time?: string): string {
  if (!time) return '--'
  return time.substring(0, 16).replace('T', ' ')
}

async function loadRules() {
  loading.value = true
  try {
    const res: any = await alertApi.getRules({ page: 1, size: 100 })
    rules.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (e) {
    console.error('加载告警规则失败', e)
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.ruleName = ''
  form.ruleType = 'CONTINUOUS_FAIL'
  form.condition = '{"threshold":3}'
  form.enabled = 1
  form.notifyChannels = undefined
  form.notifyTarget = undefined
  threshold.value = 3
  offlineMinutes.value = 2
}

function openCreateDialog() {
  editingRule.value = null
  resetForm()
  dialogVisible.value = true
}

function openEditDialog(rule: AlertRule) {
  editingRule.value = { ...rule }
  form.ruleName = rule.ruleName
  form.ruleType = rule.ruleType
  form.enabled = rule.enabled
  form.condition = rule.condition
  form.notifyChannels = rule.notifyChannels
  form.notifyTarget = rule.notifyTarget
  try {
    const cond = JSON.parse(rule.condition)
    if (rule.ruleType === 'CONTINUOUS_FAIL') {
      threshold.value = cond.threshold || 3
    } else if (rule.ruleType === 'SERVICE_OFFLINE') {
      offlineMinutes.value = cond.timeoutMinutes || 2
    }
  } catch (_) {}
  dialogVisible.value = true
}

async function saveRule() {
  if (!form.ruleName.trim()) {
    ElMessage.warning('请输入规则名称')
    return
  }
  saving.value = true
  try {
    if (form.ruleType === 'CONTINUOUS_FAIL') {
      form.condition = JSON.stringify({ threshold: threshold.value })
    } else if (form.ruleType === 'SERVICE_OFFLINE') {
      form.condition = JSON.stringify({ timeoutMinutes: offlineMinutes.value })
    }
    if (editingRule.value?.id) {
      await alertApi.updateRule(editingRule.value.id, { ...form })
      ElMessage.success('规则已更新')
    } else {
      await alertApi.createRule({ ...form })
      ElMessage.success('规则已创建')
    }
    dialogVisible.value = false
    loadRules()
  } catch (e) {
    console.error('保存规则失败', e)
  } finally {
    saving.value = false
  }
}

async function toggleRule(rule: AlertRule) {
  try {
    const newEnabled = rule.enabled ? 0 : 1
    await alertApi.updateRule(rule.id!, {
      ...rule,
      enabled: newEnabled,
    })
    rule.enabled = newEnabled
    ElMessage.success(newEnabled ? '规则已启用' : '规则已禁用')
  } catch (e) {
    console.error('切换规则状态失败', e)
  }
}

async function deleteRule(rule: AlertRule) {
  try {
    await ElMessageBox.confirm(`确定删除规则"${rule.ruleName}"吗？`, '确认删除', { type: 'warning' })
    await alertApi.deleteRule(rule.id!)
    ElMessage.success('规则已删除')
    loadRules()
  } catch (e: any) {
    if (e !== 'cancel') console.error(e)
  }
}

onMounted(() => {
  loadRules()
})
</script>

<style scoped>
.alert-rules-page {
  padding: 24px;
  min-height: 100vh;
  background: var(--bg-primary);
  color: var(--text-primary);
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.page-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0;
}

.table-container {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 12px;
  overflow: hidden;
}

.data-table {
  width: 100%;
  border-collapse: collapse;
}

.data-table th {
  text-align: left;
  padding: 12px 16px;
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border-color);
}

.data-table td {
  padding: 14px 16px;
  font-size: 13px;
  border-bottom: 1px solid var(--border-color);
}

.data-table tr:last-child td {
  border-bottom: none;
}

.name-cell {
  font-weight: 600;
}

.condition-cell {
  color: var(--text-secondary);
  font-size: 12px;
}

.time-cell {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: var(--text-secondary);
}

.type-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  background: rgba(88, 166, 255, 0.1);
  color: var(--accent-blue);
}

.status-badge {
  display: inline-block;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 500;
}

.status-badge.enabled {
  background: rgba(63, 185, 80, 0.15);
  color: var(--accent-green);
}

.status-badge.disabled {
  background: rgba(110, 118, 129, 0.1);
  color: var(--text-muted);
}

.action-buttons {
  display: flex;
  gap: 6px;
}

.action-btn {
  padding: 5px 10px;
  border-radius: 5px;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s ease;
  border: none;
  background: transparent;
  color: var(--text-secondary);
}

.action-btn:hover {
  color: var(--text-primary);
  background: var(--bg-hover);
}

.action-btn.primary {
  background: rgba(88, 166, 255, 0.1);
  color: var(--accent-blue);
}

.action-btn.primary:hover {
  background: rgba(88, 166, 255, 0.2);
}

.action-btn.success {
  background: rgba(63, 185, 80, 0.1);
  color: var(--accent-green);
}

.action-btn.success:hover {
  background: rgba(63, 185, 80, 0.2);
}

.action-btn.warning {
  background: rgba(240, 136, 62, 0.1);
  color: var(--accent-orange);
}

.action-btn.warning:hover {
  background: rgba(240, 136, 62, 0.2);
}

.action-btn.danger {
  background: rgba(220, 38, 38, 0.1);
  color: var(--accent-red);
}

.action-btn.danger:hover {
  background: rgba(220, 38, 38, 0.2);
}

.empty-cell {
  text-align: center;
  color: var(--text-muted);
  padding: 40px 16px !important;
  font-size: 14px !important;
}

.pagination {
  display: flex;
  justify-content: flex-end;
  padding: 12px 16px;
  background: var(--bg-secondary);
  border-top: 1px solid var(--border-color);
}

.page-info {
  font-size: 12px;
  color: var(--text-secondary);
}

.rule-form .el-form-item {
  margin-bottom: 18px;
}

.full-width {
  width: 100%;
}

.condition-config {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-primary);
}
</style>
