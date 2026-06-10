<template>
  <div v-if="thoughts.length > 0" class="thought-process">
    <div v-for="(t, i) in thoughts" :key="i" class="thought-item">
      <span class="thought-icon">🔍</span>
      <span class="thought-text">{{ t }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{ thoughts: string[] }>()
</script>

<style scoped>
.thought-process {
  padding: 12px 16px;
  background: rgba(255,255,255,0.02);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 12px;
  margin-bottom: 8px;
}
.thought-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 0;
  font-size: 13px;
  color: #8b949e;
}
.thought-text {
  color: #c9d1d9;
}
</style>
