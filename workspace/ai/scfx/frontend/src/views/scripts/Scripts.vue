<template>
  <div class="scripts-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>采集脚本管理</span>
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon> 新建脚本
          </el-button>
        </div>
      </template>

      <!-- 统计卡片 -->
      <el-row :gutter="20" class="stats-row">
        <el-col :span="8">
          <div class="stat-card total">
            <div class="stat-value">{{ stats.total }}</div>
            <div class="stat-label">脚本总数</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-card enabled">
            <div class="stat-value">{{ stats.enabled }}</div>
            <div class="stat-label">启用中</div>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="stat-card disabled">
            <div class="stat-value">{{ stats.disabled }}</div>
            <div class="stat-label">已禁用</div>
          </div>
        </el-col>
      </el-row>

      <!-- 筛选 -->
      <el-form :inline="true" class="filter-form">
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 120px">
            <el-option label="全部" value="" />
            <el-option label="启用" value="enabled" />
            <el-option label="禁用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item label="数据源">
          <el-select v-model="filters.source" placeholder="全部" clearable style="width: 150px">
            <el-option label="粮信网" value="liangxin" />
            <el-option label="我的钢铁网" value="mysteel" />
            <el-option label="中华粮网" value="chinagrain" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <!-- 列表 -->
      <el-table :data="list" border stripe v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="scriptName" label="脚本名称" min-width="150" />
        <el-table-column prop="source" label="数据源" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.source === 'liangxin'" type="success">粮信网</el-tag>
            <el-tag v-else-if="row.source === 'mysteel'" type="primary">我的钢铁网</el-tag>
            <el-tag v-else-if="row.source === 'chinagrain'" type="warning">中华粮网</el-tag>
            <el-tag v-else>{{ row.source || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="triggerType" label="触发方式" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.triggerType === 'manual'" type="info">手动</el-tag>
            <el-tag v-else-if="row.triggerType === 'single'" type="warning">单次</el-tag>
            <el-tag v-else-if="row.triggerType === 'repeat'">周期</el-tag>
            <el-tag v-else-if="row.triggerType === 'cron'" type="primary">Cron</el-tag>
            <el-tag v-else>{{ row.triggerType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reportIntervalSeconds" label="上报频率" width="90">
          <template #default="{ row }">
            {{ row.reportIntervalSeconds }}秒
          </template>
        </el-table-column>
        <el-table-column prop="executionCount" label="执行次数" width="90" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.status === 'enabled'" type="success">启用</el-tag>
            <el-tag v-else type="danger">禁用</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastExecutionTime" label="最后执行" width="160">
          <template #default="{ row }">
            {{ row.lastExecutionTime ? formatTime(row.lastExecutionTime) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 'disabled'" type="warning" size="small" @click="handleDisable(row)">
              禁用
            </el-button>
            <el-button v-else type="success" size="small" @click="handleEnable(row)">
              启用
            </el-button>
            <el-button type="primary" size="small" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.page"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialogVisible" title="编辑脚本" width="900px" :close-on-click-modal="false">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="basic">
          <el-form :model="currentRow" label-width="100px" class="script-form">
            <el-form-item label="脚本名称" required>
              <el-input v-model="currentRow.scriptName" placeholder="请输入脚本名称" />
            </el-form-item>
            <el-form-item label="描述">
              <el-input v-model="currentRow.description" type="textarea" :rows="2" placeholder="请输入描述" />
            </el-form-item>
            <el-form-item label="数据源">
              <el-select v-model="currentRow.source" style="width: 100%">
                <el-option label="粮信网" value="liangxin" />
                <el-option label="我的钢铁网" value="mysteel" />
                <el-option label="中华粮网" value="chinagrain" />
              </el-select>
            </el-form-item>
            <el-form-item label="采集主体">
              <el-select v-model="currentRow.subject" style="width: 100%">
                <el-option label="玉米" value="corn" />
                <el-option label="小麦" value="wheat" />
                <el-option label="稻米" value="rice" />
                <el-option label="大豆" value="soybean" />
              </el-select>
            </el-form-item>
            <el-form-item label="上报频率">
              <el-input-number v-model="currentRow.reportIntervalSeconds" :min="10" :max="3600" />
              <span class="unit">秒</span>
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="触发配置" name="trigger">
          <el-form :model="currentRow" label-width="100px" class="script-form">
            <el-form-item label="触发方式">
              <el-radio-group v-model="currentRow.triggerType">
                <el-radio label="manual">手动触发</el-radio>
                <el-radio label="single">单次触发</el-radio>
                <el-radio label="repeat">周期触发</el-radio>
                <el-radio label="cron">Cron表达式</el-radio>
              </el-radio-group>
            </el-form-item>

            <!-- 单次触发 -->
            <template v-if="currentRow.triggerType === 'single'">
              <el-form-item label="触发时间">
                <el-date-picker
                  v-model="singleTime"
                  type="datetime"
                  placeholder="选择触发时间"
                  style="width: 100%"
                />
              </el-form-item>
              <el-form-item label="结束时间">
                <el-date-picker
                  v-model="currentRow.endTime"
                  type="datetime"
                  placeholder="选择结束时间（可选）"
                  style="width: 100%"
                />
              </el-form-item>
            </template>

            <!-- 周期触发 -->
            <template v-if="currentRow.triggerType === 'repeat'">
              <el-form-item label="重复周期">
                <el-radio-group v-model="currentRow.repeatType">
                  <el-radio label="daily">每天</el-radio>
                  <el-radio label="weekly">每周</el-radio>
                  <el-radio label="monthly">每月</el-radio>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="执行时间">
                <el-time-picker
                  v-model="repeatTime"
                  placeholder="选择时间"
                  style="width: 100%"
                  format="HH:mm:ss"
                  value-format="HH:mm:ss"
                />
              </el-form-item>
              <el-form-item label="结束时间">
                <el-date-picker
                  v-model="currentRow.endTime"
                  type="datetime"
                  placeholder="选择结束时间（可选）"
                  style="width: 100%"
                />
              </el-form-item>
            </template>

            <!-- Cron表达式 -->
            <template v-if="currentRow.triggerType === 'cron'">
              <el-form-item label="Cron表达式">
                <el-input v-model="currentRow.cronExpression" placeholder="0 0 8 * * ?" />
                <div class="cron-hint">
                  格式: 秒 分 时 日 月 周<br/>
                  示例: "0 0 8 * * ?" 每天8点执行<br/>
                  "0 30 14 * * ?" 每天14:30执行<br/>
                  "0 0 9 * * MON" 每周一9点执行
                </div>
              </el-form-item>
              <el-form-item label="结束时间">
                <el-date-picker
                  v-model="currentRow.endTime"
                  type="datetime"
                  placeholder="选择结束时间（可选）"
                  style="width: 100%"
                />
              </el-form-item>
            </template>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="脚本内容" name="content">
          <div class="editor-container">
            <el-input
              v-model="currentRow.scriptContent"
              type="textarea"
              :rows="20"
              placeholder="请输入Python脚本内容..."
              class="code-editor"
            />
          </div>
        </el-tab-pane>
      </el-tabs>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="脚本详情" width="800px">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="ID">{{ detailRow.id }}</el-descriptions-item>
        <el-descriptions-item label="名称">{{ detailRow.scriptName }}</el-descriptions-item>
        <el-descriptions-item label="数据源">{{ detailRow.source }}</el-descriptions-item>
        <el-descriptions-item label="触发方式">{{ detailRow.triggerType }}</el-descriptions-item>
        <el-descriptions-item label="上报频率">{{ detailRow.reportIntervalSeconds }}秒</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="detailRow.status === 'enabled' ? 'success' : 'danger'">
            {{ detailRow.status === 'enabled' ? '启用' : '禁用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="执行次数">{{ detailRow.executionCount }}</el-descriptions-item>
        <el-descriptions-item label="成功次数">{{ detailRow.successCount }}</el-descriptions-item>
        <el-descriptions-item label="失败次数">{{ detailRow.failedCount }}</el-descriptions-item>
        <el-descriptions-item label="最后执行" :span="2">
          {{ detailRow.lastExecutionTime ? formatTime(detailRow.lastExecutionTime) : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="Cron" :span="2">{{ detailRow.cronExpression || '-' }}</el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">{{ detailRow.description || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div class="script-content-preview">
        <h4>脚本内容</h4>
        <pre>{{ detailRow.scriptContent }}</pre>
      </div>

      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="primary" @click="handleEditFromDetail">编辑脚本</el-button>
        <el-button type="success" @click="handleExecute">立即执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { scriptApi, CollectionScript, ScriptStats } from '../../api'

const loading = ref(false)
const list = ref<CollectionScript[]>([])
const stats = ref<ScriptStats>({ total: 0, enabled: 0, disabled: 0 })
const dialogVisible = ref(false)
const detailVisible = ref(false)
const activeTab = ref('basic')
const singleTime = ref<Date | null>(null)
const repeatTime = ref<Date | null>(null)

const filters = reactive({
  status: '',
  source: ''
})

const pagination = reactive({
  page: 1,
  size: 20,
  total: 0
})

const currentRow = ref<any>({})
const detailRow = ref<any>({})

function formatTime(time: string) {
  if (!time) return '-'
  return new Date(time).toLocaleString('zh-CN')
}

async function loadStats() {
  try {
    const res = await scriptApi.stats()
    stats.value = (res as any).data
  } catch (e) {
    console.error('加载统计失败', e)
  }
}

async function loadData() {
  loading.value = true
  try {
    const res: any = await scriptApi.list({
      page: pagination.page,
      size: pagination.size,
      status: filters.status || undefined,
      source: filters.source || undefined
    })
    const pageData = res.data
    list.value = pageData.records || []
    pagination.total = pageData.total || 0
    await loadStats()
  } catch (e) {
    console.error('加载列表失败', e)
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.status = ''
  filters.source = ''
  pagination.page = 1
  loadData()
}

function handleCreate() {
  currentRow.value = {
    scriptName: '',
    description: '',
    scriptContent: '# -*- coding: utf-8 -*-\n"""采集脚本"""\n',
    source: 'liangxin',
    subject: 'corn',
    triggerType: 'manual',
    reportIntervalSeconds: 60,
    status: 'enabled'
  }
  activeTab.value = 'basic'
  dialogVisible.value = true
}

function handleEdit(row: CollectionScript) {
  currentRow.value = { ...row }
  activeTab.value = 'basic'
  dialogVisible.value = true
}

function handleEditFromDetail() {
  currentRow.value = { ...detailRow.value }
  detailVisible.value = false
  dialogVisible.value = true
}

async function handleSave() {
  try {
    if (currentRow.value.id) {
      await scriptApi.update(currentRow.value.id, currentRow.value as any)
      ElMessage.success('保存成功')
    } else {
      await scriptApi.create(currentRow.value as any)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e) {
    console.error('保存失败', e)
  }
}

async function handleEnable(row: CollectionScript) {
  if (!row.id) return
  try {
    await scriptApi.enable(row.id)
    ElMessage.success('已启用')
    loadData()
  } catch (e) {
    console.error('启用失败', e)
  }
}

async function handleDisable(row: CollectionScript) {
  if (!row.id) return
  try {
    await scriptApi.disable(row.id)
    ElMessage.success('已禁用')
    loadData()
  } catch (e) {
    console.error('禁用失败', e)
  }
}

async function handleDelete(row: CollectionScript) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm('确定删除该脚本？删除后无法恢复！', '警告', { type: 'warning' })
    await scriptApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e: any) {
    if (e !== 'cancel') {
      console.error('删除失败', e)
    }
  }
}

async function handleExecute() {
  if (!detailRow.value.id) return
  try {
    await scriptApi.execute(detailRow.value.id)
    ElMessage.success('脚本已触发执行，请查看日志')
    detailVisible.value = false
    loadData()
  } catch (e) {
    console.error('执行失败', e)
  }
}

async function showDetail(row: CollectionScript) {
  detailRow.value = { ...row }
  detailVisible.value = true
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.scripts-container {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stats-row {
  margin-bottom: 20px;
}

.stat-card {
  padding: 20px;
  border-radius: 8px;
  text-align: center;
  color: #fff;
}

.stat-card.total { background: #409EFF; }
.stat-card.enabled { background: #67C23A; }
.stat-card.disabled { background: #909399; }

.stat-value {
  font-size: 28px;
  font-weight: bold;
}

.stat-label {
  font-size: 14px;
  margin-top: 8px;
}

.filter-form {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.script-form {
  max-width: 600px;
}

.unit {
  margin-left: 10px;
  color: #999;
}

.cron-hint {
  font-size: 12px;
  color: #999;
  margin-top: 5px;
  line-height: 1.6;
}

.editor-container {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.code-editor :deep(textarea) {
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 13px;
}

.script-content-preview {
  margin-top: 20px;
}

.script-content-preview h4 {
  margin-bottom: 10px;
}

.script-content-preview pre {
  background: #f5f7fa;
  padding: 15px;
  border-radius: 4px;
  max-height: 300px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
