<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  exportUserStats,
  clearRecommendCache,
  getRedisCacheStatus,
  getComplianceDisclaimer,
  saveComplianceDisclaimer,
  anonymizeHistoricalData,
  type RedisCacheStatusDTO,
} from '@/api/system/settings'

const cache = reactive<RedisCacheStatusDTO>({
  used_memory: '—',
  max_memory: '—',
  connected: false,
})

const disclaimerModel = reactive({ content: '' })
const disclaimerFormRef = ref<FormInstance>()
const disclaimerRules: FormRules = {
  content: [
    { required: true, message: '请输入免责声明内容', trigger: 'blur' },
    { min: 10, message: '内容过短，请补充完整声明', trigger: 'blur' },
  ],
}

const initLoading = ref(true)
const cacheLoading = ref(false)
const saveDisclaimerLoading = ref(false)
const exportLoading = ref(false)
const clearCacheLoading = ref(false)
const anonymizeLoading = ref(false)

async function refreshCacheStatus() {
  cacheLoading.value = true
  try {
    const data = await getRedisCacheStatus()
    cache.used_memory = data.used_memory
    cache.max_memory = data.max_memory
    cache.connected = data.connected
  } finally {
    cacheLoading.value = false
  }
}

async function loadDisclaimer() {
  const res = await getComplianceDisclaimer()
  disclaimerModel.content = res.content || ''
}

onMounted(async () => {
  try {
    await Promise.all([refreshCacheStatus(), loadDisclaimer()])
  } finally {
    initLoading.value = false
  }
})

async function onExportStats() {
  try {
    await ElMessageBox.confirm(
      '将导出用户体质统计数据，可能包含敏感字段。是否继续？',
      '导出确认',
      { type: 'warning', confirmButtonText: '开始导出', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  exportLoading.value = true
  try {
    await exportUserStats()
    ElMessage.success('导出任务已开始，文件已下载或保存到本地')
  } finally {
    exportLoading.value = false
  }
}

async function onAnonymize() {
  try {
    await ElMessageBox.confirm(
      '匿名化处理历史数据为高危操作，执行后不可轻易恢复。请确认您已备份数据。',
      '高危操作确认',
      { type: 'error', confirmButtonText: '我已知晓风险', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  try {
    const { value } = await ElMessageBox.prompt('请输入当前管理员密码以验证身份', '身份验证', {
      confirmButtonText: '提交',
      cancelButtonText: '取消',
      inputType: 'password',
      inputPlaceholder: '管理员密码',
    })
    if (!value || !String(value).trim()) {
      ElMessage.warning('请输入管理员密码')
      return
    }
    anonymizeLoading.value = true
    try {
      await anonymizeHistoricalData(String(value).trim())
      ElMessage.success('匿名化任务已提交，请稍后在任务中心查看进度')
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '操作失败'
      ElMessage.warning(msg)
    } finally {
      anonymizeLoading.value = false
    }
  } catch {
    /* 用户取消 prompt */
  }
}

async function onSaveDisclaimer() {
  if (!disclaimerFormRef.value) return
  await disclaimerFormRef.value.validate(async (valid) => {
    if (!valid) return
    saveDisclaimerLoading.value = true
    try {
      await saveComplianceDisclaimer(disclaimerModel.content)
      ElMessage.success('免责声明已保存，前台菜谱详情页将展示该文案')
    } finally {
      saveDisclaimerLoading.value = false
    }
  })
}

async function onClearRecommendCache() {
  try {
    await ElMessageBox.confirm(
      '将清空 Redis 中首页推荐列表相关缓存，短期内推荐结果可能重新计算。是否继续？',
      '清空缓存',
      { type: 'warning', confirmButtonText: '清空', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  clearCacheLoading.value = true
  try {
    await clearRecommendCache()
    ElMessage.success('首页推荐缓存已清理')
    await refreshCacheStatus()
  } finally {
    clearCacheLoading.value = false
  }
}
</script>

<template>
  <div v-loading="initLoading" class="settings-stack">
    <el-card class="settings-card" shadow="never">
      <template #header>
        <span class="settings-card-title">用户数据管理</span>
      </template>
      <div class="action-row">
        <el-button type="primary" plain :loading="exportLoading" @click="onExportStats">
          导出用户体质统计数据
        </el-button>
        <el-button type="danger" plain :loading="anonymizeLoading" @click="onAnonymize">
          匿名化处理历史数据
        </el-button>
      </div>
      <p class="action-hint">
        导出与匿名化均为敏感操作，已启用二次确认；匿名化需输入管理员密码。
      </p>
    </el-card>

    <el-card class="settings-card" shadow="never">
      <template #header>
        <span class="settings-card-title">内容合规声明配置</span>
      </template>
      <el-form
        ref="disclaimerFormRef"
        :model="disclaimerModel"
        :rules="disclaimerRules"
        label-position="top"
      >
        <el-form-item label="药膳详情页统一免责声明（支持 HTML）" prop="content">
          <el-input
            v-model="disclaimerModel.content"
            type="textarea"
            :rows="10"
            placeholder="将展示在菜谱详情页底部，建议包含医疗免责与合规提示"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saveDisclaimerLoading" @click="onSaveDisclaimer">
            保存声明
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-loading="cacheLoading" class="settings-card" shadow="never">
      <template #header>
        <span class="settings-card-title">缓存管理</span>
      </template>
      <el-descriptions :column="1" border class="cache-desc">
        <el-descriptions-item label="Redis 连接">
          <el-tag :type="cache.connected ? 'success' : 'info'" size="small">
            {{ cache.connected ? '已连接' : '未连接 / 未知' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="已用内存">
          {{ cache.used_memory }}
        </el-descriptions-item>
        <el-descriptions-item label="配置最大内存">
          {{ cache.max_memory }}
        </el-descriptions-item>
      </el-descriptions>
      <div class="cache-actions">
        <el-button :loading="clearCacheLoading" type="warning" plain @click="onClearRecommendCache">
          清空首页推荐缓存
        </el-button>
        <el-button text type="primary" @click="refreshCacheStatus">刷新状态</el-button>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.settings-stack {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.settings-card :deep(.el-card__header) {
  padding: 16px 20px;
}

.settings-card :deep(.el-card__body) {
  padding: 20px;
}

.settings-card-title {
  font-size: 16px;
  font-weight: 600;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.action-hint {
  margin-top: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.cache-desc {
  max-width: 520px;
  margin-bottom: 16px;
}

.cache-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
