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
  DropdownMenu as VanDropdownMenu,
  DropdownItem as VanDropdownItem,
  Button as VanButton,
} from 'vant'
import { useUserStore, CONSTITUTION_TYPES } from '@/stores/user'
import { SEASON_OPTIONS } from '@/utils/season'
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
} from '@/utils/storedTokens'

const router = useRouter()
const userStore = useUserStore()

const personalizedLoading = ref(false)
const exportLoading = ref(false)

const constitutionName = computed(() => {
  const hit = CONSTITUTION_TYPES.find((c) => c.code === userStore.constitutionCode)
  return hit?.label || '未设置'
})

const seasonPickerOptions = computed(() =>
  SEASON_OPTIONS.map((o) => ({ text: `${o.label}季`, value: o.code })),
)

const seasonModel = computed({
  get: () => userStore.seasonCode,
  set: (code: string) => {
    void onSeasonPick(code)
  },
})

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

async function onSeasonPick(code: string) {
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
      <van-cell-group inset title="通用设置">
        <van-cell title="季节偏好" center class="season-preference-cell">
          <template #value>
            <div class="season-cell-dd" @click.stop>
              <van-dropdown-menu :overlay="false">
                <van-dropdown-item
                  class="campus-settings-season-dd"
                  v-model="seasonModel"
                  :options="seasonPickerOptions"
                  teleport="body"
                />
              </van-dropdown-menu>
            </div>
          </template>
        </van-cell>
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
      <van-cell-group inset title="账号管理">
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
      <van-cell-group inset title="数据管理">
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
      <van-cell-group inset title="服务协议">
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

  </div>
</template>

<style scoped>
/* 与 van-cell-group inset 左右边距一致，避免顶栏卡片比下方「季节偏好」等列表更宽 */
.campus-settings .page-head.page-card {
  margin-inline: var(--van-padding-md, 16px);
  box-sizing: border-box;
}

.settings-section {
  margin-bottom: var(--space-md);
}

.settings-section :deep(.van-cell-group__title) {
  text-align: left;
}

.settings-footer {
  margin: var(--space-xl) var(--van-padding-md, 16px) var(--space-xl);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.logout-btn {
  font-weight: 600;
}

.settings-footer__hint {
  margin: 0;
  text-align: center;
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

/* 与 RecommendFilterPanel 一致：包裹层挂变量，保证 scoped 命中 */
.season-cell-dd {
  display: inline-flex;
  justify-content: flex-end;
  max-width: 100%;
  --van-dropdown-menu-height: 30px;
  --van-dropdown-menu-title-font-size: var(--font-size-sm);
  --van-dropdown-menu-title-line-height: var(--line-height-tight);
}

.season-cell-dd :deep(.van-dropdown-menu__bar) {
  width: fit-content;
  max-width: 100%;
  height: var(--van-dropdown-menu-height);
  box-shadow: none;
  border-radius: var(--radius-sm);
  border: 1px solid var(--color-border);
  background: var(--color-bg-elevated);
}

.season-cell-dd :deep(.van-dropdown-menu__bar--opened) {
  border-color: color-mix(in srgb, var(--color-primary) 40%, var(--color-border));
  box-shadow: 0 0 0 1px color-mix(in srgb, var(--color-primary) 22%, transparent);
}

.season-cell-dd :deep(.van-dropdown-menu__title) {
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--font-size-sm);
  line-height: var(--line-height-tight);
  color: var(--color-text-primary);
  padding: 0 var(--space-xs);
}

.season-cell-dd :deep(.van-dropdown-menu__title--active) {
  color: var(--color-primary);
  font-weight: 600;
}

/* 约 8 个汉字 + 下拉箭头与内边距（与下方弹出层宽度量级一致） */
.season-cell-dd :deep(.van-dropdown-menu__item) {
  flex: 0 0 auto;
  min-width: min(10.5rem, calc(100vw - 48px));
  max-width: min(13rem, calc(100vw - 32px));
}

.season-preference-cell :deep(.van-cell__value) {
  flex: 0 1 auto;
  min-width: 0;
}
</style>

<style>
.settings-legal-dialog .van-dialog__message {
  max-height: 55vh;
  overflow-y: auto;
  white-space: pre-wrap;
}

/* 季节偏好 DropdownItem teleport 到 body，需非 scoped 命中弹出层 */
.campus-settings-season-dd.van-dropdown-item .van-dropdown-item__content.van-popup--top {
  width: fit-content !important;
  min-width: min(10.5rem, calc(100vw - 48px));
  max-width: min(13rem, calc(100vw - 32px));
  left: auto !important;
  right: max(var(--van-padding-md, 16px), env(safe-area-inset-right, 0px)) !important;
}

.campus-settings-season-dd .van-dropdown-item__option.van-cell {
  padding: 8px 12px;
  font-size: var(--font-size-sm, 13px);
}
</style>
