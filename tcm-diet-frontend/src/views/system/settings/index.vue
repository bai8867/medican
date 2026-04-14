<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import SystemBasic from './SystemBasic.vue'
import SecuritySettings from './SecuritySettings.vue'
import PrivacySettings from './PrivacySettings.vue'

const router = useRouter()
const activeTab = ref('basic')

function onBack() {
  if (window.history.length > 1) router.back()
  else router.replace('/admin/dashboard')
}
</script>

<template>
  <div class="system-settings-page">
    <el-page-header class="settings-page-header" @back="onBack">
      <template #title>
        <div class="settings-breadcrumb-title">
          <span class="crumb-muted">系统管理</span>
          <span class="crumb-sep">/</span>
          <span class="crumb-current">系统设置</span>
        </div>
      </template>
    </el-page-header>

    <el-tabs v-model="activeTab" class="settings-tabs" type="border-card">
      <el-tab-pane label="基本设置" name="basic" lazy>
        <SystemBasic />
      </el-tab-pane>
      <el-tab-pane label="安全设置" name="security" lazy>
        <SecuritySettings />
      </el-tab-pane>
      <el-tab-pane label="数据与隐私" name="privacy" lazy>
        <PrivacySettings />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.system-settings-page {
  max-width: 1080px;
}

.settings-page-header {
  margin-bottom: 20px;
}

.settings-breadcrumb-title {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
}

.crumb-muted {
  color: var(--el-text-color-secondary);
  font-weight: 500;
}

.crumb-sep {
  margin: 0 8px;
  color: var(--el-text-color-placeholder);
  font-weight: 400;
}

.crumb-current {
  color: var(--el-text-color-primary);
}

.settings-tabs :deep(.el-tabs__content) {
  padding: 20px;
  background: var(--el-fill-color-blank);
}
</style>
