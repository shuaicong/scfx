<template>
  <div class="price-table-container">
    <div class="table-title">{{ title }}</div>
    <el-table
      :data="rows"
      stripe
      size="small"
      style="width: 100%"
      :header-cell-style="{ background: 'rgba(255,255,255,0.05)', color: '#ccc' }"
    >
      <el-table-column prop="region" label="区域" />
      <el-table-column prop="province" label="省份" />
      <el-table-column prop="price" label="价格">
        <template #default="{ row }">
          <span class="price-value">{{ row.price }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="change" label="涨跌">
        <template #default="{ row }">
          <span :class="changeClass(row.change)">{{ row.change }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" />
    </el-table>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  rows: { region: string; province?: string; price: number; change: string; remark: string }[]
}>()

function changeClass(change: string) {
  if (change === '持平') return 'change-flat'
  if (change.startsWith('+') || /^\d/.test(change)) return 'change-up'
  if (change.startsWith('-')) return 'change-down'
  return 'change-flat'
}
</script>

<style scoped>
.price-table-container {
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 8px;
  padding: 12px;
}
.table-title {
  font-size: 14px;
  color: #ccc;
  margin-bottom: 8px;
}
.price-value {
  font-variant-numeric: tabular-nums;
  font-family: 'SF Mono', 'Fira Code', monospace;
}
.change-up {
  color: #d32f2f;
}
.change-down {
  color: #2e7d32;
}
.change-flat {
  color: #888;
}
</style>
