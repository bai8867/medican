<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import {
  getSystemSettings,
  updateSystemSettings,
  updateFeatureFlags,
  type DefaultSeason,
  type SystemSettingsUpdatePayload,
} from '@/api/system/settings'

const formRef = ref<FormInstance>()
const pageLoading = ref(false)
const saveLoading = ref(false)
const flagBusy = reactive({
  ai_generate_enabled: false,
  recommend_switch: false,
  maintenance_mode: false,
})

const form = reactive<SystemSettingsUpdatePayload>({
  site_name: '',
  contact_email: '',
  default_season: 'auto',
  items_per_page: 20,
})

const flags = reactive({
  ai_generate_enabled: true,
  recommend_switch: true,
  maintenance_mode: false,
})

const seasonOptions: { label: string; value: DefaultSeason }[] = [
  { label: '春', value: 'spring' },
  { label: '夏', value: 'summer' },
  { label: '秋', value: 'autumn' },
  { label: '冬', value: 'winter' },
  { label: '自动跟随系统时间', value: 'auto' },
]

const rules: FormRules = {
  site_name: [{ required: true, message: '请输入网站名称', trigger: 'blur' }],
  contact_email: [
    { required: true, message: '请输入管理员联系邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  default_season: [{ required: true, message: '请选择默认季节', trigger: 'change' }],
  items_per_page: [
    { required: true, message: '请输入分页条数', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        const n = Number(value)
        if (Number.isNaN(n) || n < 10 || n > 100) {
          callback(new Error('分页条数需在 10–100 之间'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function loadSettings() {
  pageLoading.value = true
  try {
    const data = await getSystemSettings()
    form.site_name = data.site_name
    form.contact_email = data.contact_email
    form.default_season = data.default_season
    form.items_per_page = data.items_per_page
    flags.ai_generate_enabled = data.ai_generate_enabled
    flags.recommend_switch = data.recommend_switch
    flags.maintenance_mode = data.maintenance_mode
  } finally {
    pageLoading.value = false
  }
}

async function onSaveBasic() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saveLoading.value = true
    try {
      await updateSystemSettings({ ...form })
      ElMessage.success('设置已保存')
    } finally {
      saveLoading.value = false
    }
  })
}

type FlagKey = 'ai_generate_enabled' | 'recommend_switch' | 'maintenance_mode'

async function onFlagChange(key: FlagKey, value: boolean) {
  const prev = flags[key]
  flags[key] = value
  flagBusy[key] = true
  try {
    await updateFeatureFlags({ [key]: value })
    ElMessage.success('开关已同步到服务器')
  } catch {
    flags[key] = prev
  } finally {
    flagBusy[key] = false
  }
}

onMounted(loadSettings)
</script>

<template>
  <div v-loading="pageLoading" class="settings-stack">
    <el-card class="settings-card" shadow="never">
      <template #header>
        <span class="settings-card-title">系统参数配置</span>
      </template>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="160px"
        class="settings-form"
      >
        <el-form-item label="网站名称" prop="site_name">
          <el-input v-model="form.site_name" placeholder="请输入网站名称" clearable />
        </el-form-item>
        <el-form-item label="管理员联系邮箱" prop="contact_email">
          <el-input v-model="form.contact_email" placeholder="name@example.com" clearable />
        </el-form-item>
        <el-form-item label="默认推荐季节" prop="default_season">
          <el-select v-model="form.default_season" placeholder="请选择" style="width: 100%">
            <el-option
              v-for="opt in seasonOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="前端分页默认条数" prop="items_per_page">
          <el-input-number
            v-model="form.items_per_page"
            :min="10"
            :max="100"
            :step="5"
            controls-position="right"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="saveLoading" @click="onSaveBasic">
            保存设置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="settings-card" shadow="never">
      <template #header>
        <span class="settings-card-title">功能开关</span>
      </template>
      <div class="switch-list">
        <div class="switch-row">
          <div class="switch-meta">
            <div class="switch-label">AI 食疗方案生成</div>
            <div class="switch-desc">是否开放 AI 食疗方案生成功能</div>
          </div>
          <el-switch
            :model-value="flags.ai_generate_enabled"
            :disabled="flagBusy.ai_generate_enabled"
            @change="(v: string | number | boolean) => onFlagChange('ai_generate_enabled', Boolean(v))"
          />
        </div>
        <el-divider class="switch-divider" />
        <div class="switch-row">
          <div class="switch-meta">
            <div class="switch-label">个性化推荐总开关</div>
            <div class="switch-desc">对应 PRD 合规要求的全局推荐开关</div>
          </div>
          <el-switch
            :model-value="flags.recommend_switch"
            :disabled="flagBusy.recommend_switch"
            @change="(v: string | number | boolean) => onFlagChange('recommend_switch', Boolean(v))"
          />
        </div>
        <el-divider class="switch-divider" />
        <div class="switch-row">
          <div class="switch-meta">
            <div class="switch-label">系统维护模式</div>
            <div class="switch-desc">开启后前台展示维护页面</div>
          </div>
          <el-switch
            :model-value="flags.maintenance_mode"
            :disabled="flagBusy.maintenance_mode"
            @change="(v: string | number | boolean) => onFlagChange('maintenance_mode', Boolean(v))"
          />
        </div>
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

.switch-list {
  max-width: 720px;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.switch-meta {
  flex: 1;
  min-width: 0;
}

.switch-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.switch-desc {
  margin-top: 4px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.5;
}

.switch-divider {
  margin: 16px 0;
}
</style>
