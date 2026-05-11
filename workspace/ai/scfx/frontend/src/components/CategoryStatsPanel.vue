<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { categoryApi } from '@/api/category'

const stats = ref<any>({})
const loading = ref(false)

const loadStats = async () => {
  loading.value = true
  try {
    const res = await categoryApi.stats()
    stats.value = res.data.data || {}
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadStats()
})
</script>

<template>
  <div class="stats-panel">
    <div class="stats-header">
      <h3>分类统计</h3>
      <button @click="loadStats">刷新</button>
    </div>

    <div class="stats-content">
      <div class="stat-card">
        <div class="stat-label">总分类数</div>
        <div class="stat-value">{{ stats.totalCategories || 0 }}</div>
      </div>

      <div class="stat-card">
        <div class="stat-label">知识数量排行榜</div>
        <div class="stat-list">
          <div
            v-for="(cat, index) in (stats.topCategories || [])"
            :key="cat.id"
            class="stat-item"
          >
            <span class="rank">{{ index + 1 }}</span>
            <span class="name">{{ cat.icon }} {{ cat.name }}</span>
            <span class="count">{{ cat.knowledge_count }}</span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.stats-panel {
  padding: 16px;
}

.stats-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.stats-header h3 {
  margin: 0;
}

.stat-card {
  background: var(--bg-tertiary);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
}

.stat-label {
  color: var(--text-secondary);
  font-size: 12px;
  margin-bottom: 8px;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  color: var(--accent);
}

.stat-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.rank {
  width: 20px;
  height: 20px;
  background: var(--accent-bg);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.name {
  flex: 1;
}

.count {
  color: var(--text-muted);
}
</style>