<template>
  <div class="settings">
    <el-card>
      <template #header>
        <span>系统设置</span>
      </template>

      <el-form :model="form" label-width="120px">
        <el-form-item label="粮信网账号">
          <el-input v-model="form.username" placeholder="请输入账号" />
        </el-form-item>
        <el-form-item label="粮信网密码">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="采集间隔">
          <el-input-number v-model="form.interval" :min="1" :max="24" />
          <span style="margin-left: 10px;">小时</span>
        </el-form-item>
        <el-form-item label="Cookie有效期">
          <el-input-number v-model="form.cookieExpire" :min="1" :max="72" />
          <span style="margin-left: 10px;">小时</span>
        </el-form-item>
        <el-form-item label="最大重试次数">
          <el-input-number v-model="form.maxRetries" :min="0" :max="10" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSave">保存设置</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card style="margin-top: 20px;">
      <template #header>
        <span>系统信息</span>
      </template>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="系统版本">1.0.0</el-descriptions-item>
        <el-descriptions-item label="数据库状态">
          <el-tag type="success">正常</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="Playwright版本">1.41.2</el-descriptions-item>
        <el-descriptions-item label="最后采集时间">-</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'

const form = reactive({
  username: '33022',
  password: '',
  interval: 1,
  cookieExpire: 24,
  maxRetries: 3
})

const handleSave = () => {
  ElMessage.success('设置已保存')
}

const handleReset = () => {
  form.username = '33022'
  form.password = ''
  form.interval = 1
  form.cookieExpire = 24
  form.maxRetries = 3
  ElMessage.info('已重置为默认值')
}
</script>
