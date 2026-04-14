<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  showToast,
  showConfirmDialog,
  showDialog,
  Cell as VanCell,
  CellGroup as VanCellGroup,
  Switch as VanSwitch,
  ActionSheet as VanActionSheet,
  Button as VanButton,
} from 'vant'
import { useUserStore, CONSTITUTION_TYPES } from '@/stores/user'
import { SEASON_OPTIONS, getSeasonLabel } from '@/utils/season.js'
import { APP_VERSION, APP_DISPLAY_NAME } from '@/config/app-version'
import {
  updateUserPreferences,
  exportUserPersonalData,
} from '@/api/userSettings'
import {
  USER_AGREEMENT_TEXT,
  MEDICAL_DISCLAIMER_TEXT,
  ABOUT_US_LINES,
} from '@/content/legal'
import {
  LS_ADMIN_TOKEN,
  LS_CAMPUS_TOKEN,
  LS_LEGACY_TOKEN,
} from '@/utils/storedTokens.js'

const router = useRouter()
const userStore = useUserStore()

const seasonSheetVisible = ref(false)
const personalizedLoading = ref(false)
const exportLoading = ref(false)

const seasonActions = SEASON_OPTIONS.map((o) => ({
  name: `${o.label}季`,
}))

const constitutionName = computed(() => {
  const hit = CONSTITUTION_TYPES.find((c) => c.code === userStore.constitutionCode)
  return hit?.label || '未设置'
})

const seasonDisplay = computed(() => `${getSeasonLabel(userStore.seasonCode)}季`)

const cacheSizeText = ref('—')

function bytesToLabel(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 KB'
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

function measureLocalStorage(): number {
  let total = 0
  try {
    for (let i = 0; i < localStorage.length; i += 1) {
      const k = localStorage.key(i)
      if (!k) continue
      const v = localStorage.getItem(k) ?? ''
      total += k.length + v.length
    }
  } catch {
    return 0
  }
  return total * 2
}

function refreshCacheSize() {
  cacheSizeText.value = bytesToLabel(measureLocalStorage())
}

onMounted(() => {
  refreshCacheSize()
})

function openSeasonSheet() {
  seasonSheetVisible.value = true
}

async function onSeasonSelect(action: { name?: string }) {
  seasonSheetVisible.value = false
  const code = SEASON_OPTIONS.find((o) => `${o.label}季` === action.name)?.code || ''
  if (!code || code === userStore.seasonCode) return
  const prev = userStore.seasonCode
  userStore.updateSeason(code)
  try {
    await updateUserPreferences({ seasonCode: code })
    showToast('季节偏好已保存')
  } catch {
    userStore.updateSeason(prev)
  }
}

async function onPersonalizedChange(value: boolean) {
  if (personalizedLoading.value) return
  const prev = userStore.personalizedRecommendEnabled
  userStore.$patch({ personalizedRecommendEnabled: value })
  personalizedLoading.value = true
  try {
    await updateUserPreferences({ personalizedRecommendEnabled: value })
    showToast(value ? '已开启个性化推荐' : '已关闭个性化推荐')
  } catch {
    userStore.$patch({ personalizedRecommendEnabled: prev })
  } finally {
    personalizedLoading.value = false
  }
}

async function onClearCache() {
  try {
    await showConfirmDialog({
      title: '清理缓存',
      message: '将清除本机本地缓存（保留登录令牌与用户画像）。部分列表可能需刷新后重新加载，是否继续？',
    })
  } catch {
    return
  }
  const keep = new Set([
    LS_LEGACY_TOKEN,
    LS_CAMPUS_TOKEN,
    LS_ADMIN_TOKEN,
    'tcm_user_profile',
    'tcm_admin_auth',
  ])
  try {
    const keys: string[] = []
    for (let i = 0; i < localStorage.length; i += 1) {
      const k = localStorage.key(i)
      if (k) keys.push(k)
    }
    for (const k of keys) {
      if (!keep.has(k)) localStorage.removeItem(k)
    }
  } catch {
    showToast('清理失败')
    return
  }
  showToast('缓存已清理')
  refreshCacheSize()
  window.location.reload()
}

function onCheckUpdate() {
  showToast('当前已是最新版本')
}

function onExportData() {
  if (exportLoading.value) return
  exportLoading.value = true
  exportUserPersonalData()
    .then((data) => {
      const blob = new Blob([JSON.stringify(data, null, 2)], {
        type: 'application/json;charset=utf-8',
      })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `我的药膳数据-${Date.now()}.json`
      a.click()
      URL.revokeObjectURL(url)
      showToast('导出成功')
    })
    .catch(() => {
      /* 全局错误提示 */
    })
    .finally(() => {
      exportLoading.value = false
    })
}

function showUserAgreement() {
  showDialog({
    title: '用户协议',
    message: USER_AGREEMENT_TEXT,
    messageAlign: 'left',
    confirmButtonText: '我知道了',
    className: 'settings-legal-dialog',
  })
}

function showMedicalDisclaimer() {
  showDialog({
    title: '药膳免责声明',
    message: MEDICAL_DISCLAIMER_TEXT,
    messageAlign: 'left',
    confirmButtonText: '我知道了',
    className: 'settings-legal-dialog',
  })
}

function showAbout() {
  const lines = ABOUT_US_LINES.map((l) =>
    l.startsWith('版本：') ? `版本：${APP_VERSION}` : l,
  )
  showDialog({
    title: '关于我们',
    message: lines.join('\n'),
    messageAlign: 'left',
    confirmButtonText: '关闭',
    className: 'settings-legal-dialog',
  })
}

async function onLogout() {
  try {
    await showConfirmDialog({
      title: '退出登录',
      message: '确定要退出当前账号吗？',
      confirmButtonText: '退出',
      confirmButtonColor: '#ee0a24',
    })
  } catch {
    return
  }
  userStore.logoutCampus()
  showToast('已退出')
  await router.replace({ path: '/campus/login', query: { redirect: '/settings' } })
}

function go(path: string) {
  router.push(path)
}
</script>

<template>
  <div class="page campus-settings">
    <header class="page-head page-card">
      <h1 class="page-title">设置</h1>
      <p class="page-subtitle">通用偏好、账号与数据管理</p>
    </header>

    <section class="settings-section">
      <h2 class="settings-section__title">通用设置</h2>
      <van-cell-group inset>
        <van-cell title="季节偏好" :value="seasonDisplay" is-link @click="openSeasonSheet" />
        <van-cell title="个性化推荐" label="根据体质推荐（合规开关）" center>
          <template #right-icon>
            <van-switch
              :model-value="userStore.personalizedRecommendEnabled"
              :loading="personalizedLoading"
              size="20px"
              @update:model-value="onPersonalizedChange"
            />
          </template>
        </van-cell>
        <van-cell
          title="清理缓存"
          :value="cacheSizeText"
          is-link
          @click="onClearCache"
        />
        <van-cell
          title="检查更新"
          :value="`v${APP_VERSION}`"
          is-link
          @click="onCheckUpdate"
        />
        <van-cell
          title="饮食偏好"
          label="常去食堂、预算、忌口（联动首页与药膳日历）"
          is-link
          @click="go('/settings/preference')"
        />
      </van-cell-group>
    </section>

    <section class="settings-section">
      <h2 class="settings-section__title">账号管理</h2>
      <van-cell-group inset>
        <van-cell title="个人资料" is-link @click="go('/settings/profile')" />
        <van-cell
          title="体质管理"
          :value="constitutionName"
          is-link
          @click="go('/constitution')"
        />
        <van-cell title="修改密码" is-link @click="go('/settings/password')" />
        <van-cell title="账户安全" is-link @click="go('/settings/security')" />
      </van-cell-group>
    </section>

    <section class="settings-section">
      <h2 class="settings-section__title">数据管理</h2>
      <van-cell-group inset>
        <van-cell title="不感兴趣管理" is-link @click="go('/settings/dislikes')" />
        <van-cell title="浏览历史" is-link @click="go('/profile?tab=history')" />
        <van-cell title="收藏管理" is-link @click="go('/profile?tab=favorites')" />
        <van-cell
          title="导出我的数据"
          :value="exportLoading ? '导出中…' : ''"
          is-link
          @click="onExportData"
        />
      </van-cell-group>
    </section>

    <section class="settings-section">
      <h2 class="settings-section__title">服务协议</h2>
      <van-cell-group inset>
        <van-cell title="用户协议" is-link @click="showUserAgreement" />
        <van-cell title="隐私政策" is-link @click="go('/settings/privacy')" />
        <van-cell title="药膳免责声明" is-link @click="showMedicalDisclaimer" />
        <van-cell title="关于我们" is-link @click="showAbout" />
      </van-cell-group>
    </section>

    <div class="settings-footer">
      <van-button block round plain type="danger" class="logout-btn" @click="onLogout">
        退出登录
      </van-button>
      <p class="settings-footer__hint">{{ APP_DISPLAY_NAME }} · v{{ APP_VERSION }}</p>
    </div>

    <van-action-sheet
      v-model:show="seasonSheetVisible"
      :actions="seasonActions"
      cancel-text="取消"
      close-on-click-action
      @select="onSeasonSelect"
    />
  </div>
</template>

<style scoped>
.page-title {
  margin: 0 0 8px;
  font-size: var(--font-size-xl);
  font-weight: 700;
}

.page-subtitle {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.55;
}

.settings-section {
  margin-bottom: 12px;
}

.settings-section__title {
  margin: 0 0 8px 12px;
  font-size: 15px;
  font-weight: 700;
  text-align: left;
}

.settings-footer {
  margin: 24px 16px 32px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.logout-btn {
  font-weight: 600;
}

.settings-footer__hint {
  margin: 0;
  text-align: center;
  font-size: 12px;
  color: var(--color-text-muted);
}
</style>

<style>
.settings-legal-dialog .van-dialog__message {
  max-height: 55vh;
  overflow-y: auto;
  white-space: pre-wrap;
}
</style>
