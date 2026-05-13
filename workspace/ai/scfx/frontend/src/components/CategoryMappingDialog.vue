<template>
  <el-dialog v-model="visible" title="分类映射规则" width="800px">
    <div class="mapping-list">
      <el-button type="primary" @click="handleCreate">新增规则</el-button>
    </div>

    <el-table :data="mappings" stripe style="margin-top: 16px;">
      <el-table-column prop="sourceType" label="来源" width="120" />
      <el-table-column prop="variety" label="品种" width="100" />
      <el-table-column prop="reportType" label="报告类型" width="100" />
      <el-table-column prop="categoryId" label="目标分类" min-width="150">
        <template #default="{ row }">
          {{ getCategoryName(row.categoryId) }}
        </template>
      </el-table-column>
      <el-table-column prop="priority" label="优先级" width="80" />
      <el-table-column prop="enabled" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'">
            {{ row.enabled ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleEdit(row)">编辑</el-button>
          <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="editVisible" title="编辑映射规则" width="500px" append-to-body>
      <el-form :model="form" label-width="100px">
        <el-form-item label="来源">
          <el-select v-model="form.sourceType" placeholder="选择来源">
            <el-option label="粮信网" value="liangxinwang" />
            <el-option label="我的钢铁网" value="mysteel" />
            <el-option label="中华粮网" value="china_grain" />
          </el-select>
        </el-form-item>
        <el-form-item label="品种">
          <el-select v-model="form.variety" placeholder="选择品种" clearable>
            <el-option label="玉米" value="corn" />
            <el-option label="小麦" value="wheat" />
            <el-option label="稻谷" value="rice" />
            <el-option label="大豆" value="soybean" />
          </el-select>
        </el-form-item>
        <el-form-item label="报告类型">
          <el-select v-model="form.reportType" placeholder="选择类型" clearable>
            <el-option label="日报" value="日报" />
            <el-option label="周报" value="周报" />
            <el-option label="月报" value="月报" />
            <el-option label="晨报" value="晨报" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标分类">
          <el-input-number v-model="form.categoryId" :min="1" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="form.priority" :min="0" :max="10" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listCategoryMappings, createCategoryMapping, updateCategoryMapping, deleteCategoryMapping, type CategoryMapping } from '@/api/category-mapping'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits(['update:modelValue'])

const visible = ref(false)
const editVisible = ref(false)
const mappings = ref<CategoryMapping[]>([])
const form = ref<CategoryMapping>({ sourceType: '', categoryId: 1, priority: 5, enabled: 1 })

watch(() => props.modelValue, (val) => {
  visible.value = val
  if (val) loadMappings()
})

watch(visible, (val) => emit('update:modelValue', val))

function loadMappings() {
  listCategoryMappings().then((res: any) => {
    if (res.code === 200) mappings.value = res.data
  })
}

function getCategoryName(id: number) {
  return `分类${id}`
}

function handleCreate() {
  form.value = { sourceType: 'liangxinwang', categoryId: 1, priority: 5, enabled: 1 }
  editVisible.value = true
}

function handleEdit(row: CategoryMapping) {
  form.value = { ...row }
  editVisible.value = true
}

async function handleSave() {
  try {
    if (form.value.id) {
      await updateCategoryMapping(form.value.id, form.value)
    } else {
      await createCategoryMapping(form.value)
    }
    editVisible.value = false
    loadMappings()
    ElMessage.success('保存成功')
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

async function handleDelete(row: CategoryMapping) {
  if (!row.id) return
  try {
    await deleteCategoryMapping(row.id)
    loadMappings()
    ElMessage.success('删除成功')
  } catch (e) {
    ElMessage.error('删除失败')
  }
}
</script>