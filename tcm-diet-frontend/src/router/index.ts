// @ts-nocheck
import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { useUserStore } from '@/stores/user'
import { isUserDisabled } from '@/utils/adminUserStatus'
import {
  hasPortalCampusOk,
  hasPortalAdminOk,
  stashCampusRedirect,
  stashAdminRedirect,
} from '@/utils/portalGate'
import { maybeProbeCampusAccountStatus } from '@/utils/campusAccountProbe'

function campusRedirectPath(raw) {
  if (typeof raw !== 'string' || !raw.startsWith('/') || raw.startsWith('//')) return '/home'
  if (raw.startsWith('/admin')) return '/home'
  return raw
}

const routes = [
  {
    path: '/',
    name: 'EntrySelect',
    component: () => import('@/views/EntrySelect.vue'),
    meta: { title: '选择模式' },
  },
  /** 与首页同内容，便于 E2E 与固定入口访问 */
  {
    path: '/mode',
    name: 'EntrySelectMode',
    component: () => import('@/views/EntrySelect.vue'),
    meta: { title: '选择模式' },
  },
  {
    path: '/campus/login',
    name: 'CampusLogin',
    component: () => import('@/views/CampusLogin.vue'),
    meta: { title: '校园端登录' },
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('@/views/recommend/RecommendPage.vue'),
    meta: { title: '首页' },
  },
  {
    path: '/recommend',
    redirect: '/home',
  },
  {
    path: '/calendar',
    name: 'CampusWeeklyCalendar',
    component: () => import('@/views/calendar/CampusWeeklyCalendarPage.vue'),
    meta: { title: '本周药膳日历' },
  },
  {
    path: '/constitution',
    name: 'Constitution',
    component: () => import('@/views/constitution/ConstitutionPage.vue'),
    meta: { title: '体质采集' },
  },
  /** 兼容列表/收藏等使用 /recipe?recipe_id= 的跳转，归一为 /recipe/:id */
  {
    path: '/recipe',
    redirect: (to) => {
      const raw = to.query.recipe_id ?? to.query.recipeId
      const id = raw != null ? String(raw).trim() : ''
      if (id) return { path: `/recipe/${encodeURIComponent(id)}`, replace: true }
      return { path: '/home', replace: true }
    },
  },
  {
    path: '/recipe/:id',
    name: 'RecipeDetail',
    component: () => import('@/views/recipe/RecipeDetailPage.vue'),
    meta: { title: '菜谱详情' },
  },
  {
    path: '/ai',
    name: 'AIGenerate',
    component: () => import('@/views/ai/AIGeneratePage.vue'),
    meta: { title: 'AI 生成' },
  },
  {
    path: '/ai/plan/:id',
    name: 'AIPlanDetail',
    component: () => import('@/views/ai/AIPlanDetailPage.vue'),
    meta: { title: 'AI 方案详情' },
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/profile/ProfilePage.vue'),
    meta: { title: '我的' },
  },
  {
    path: '/scenes',
    name: 'CampusScenes',
    component: () => import('@/views/scenes/CampusScenesPage.vue'),
    meta: { title: '场景食疗' },
  },
  {
    path: '/campus-calendar',
    redirect: '/calendar',
  },
  {
    path: '/scene/:id',
    name: 'SceneDetail',
    component: () => import('@/views/scene/detail.vue'),
    meta: { title: '场景方案' },
  },
  {
    path: '/settings',
    name: 'CampusSettings',
    component: () => import('@/views/CampusSettings.vue'),
    meta: { title: '设置' },
  },
  {
    path: '/settings/profile',
    name: 'SettingsProfile',
    component: () => import('@/views/settings/profile.vue'),
    meta: { title: '个人资料' },
  },
  {
    path: '/settings/password',
    name: 'SettingsChangePassword',
    component: () => import('@/views/settings/change-password.vue'),
    meta: { title: '修改密码' },
  },
  {
    path: '/settings/security',
    name: 'SettingsAccountSecurity',
    component: () => import('@/views/settings/account-security.vue'),
    meta: { title: '账户安全' },
  },
  {
    path: '/settings/dislikes',
    name: 'SettingsDislikes',
    component: () => import('@/views/settings/dislikes.vue'),
    meta: { title: '不感兴趣管理' },
  },
  {
    path: '/settings/privacy',
    name: 'SettingsPrivacy',
    component: () => import('@/views/settings/privacy.vue'),
    meta: { title: '隐私政策' },
  },
  {
    path: '/settings/preference',
    name: 'SettingsPreference',
    component: () => import('@/views/settings/preference.vue'),
    meta: { title: '饮食偏好' },
  },
  {
    path: '/account-disabled',
    name: 'AccountDisabled',
    component: () => import('@/views/AccountDisabled.vue'),
    meta: { title: '账号不可用' },
  },
  {
    path: '/admin/login',
    name: 'AdminLogin',
    component: () => import('@/views/admin/AdminLogin.vue'),
    meta: { title: '后台登录', adminPublic: true },
  },
  {
    path: '/admin',
    component: () => import('@/components/admin/AdminLayout.vue'),
    meta: { requiresAdminAuth: true },
    redirect: '/admin/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'AdminDashboard',
        component: () => import('@/views/admin/AdminDashboard.vue'),
        meta: { title: '数据看板' },
      },
      {
        path: 'campus-calendar',
        name: 'AdminCampusWeeklyCalendar',
        component: () => import('@/views/admin/AdminCampusWeeklyCalendar.vue'),
        meta: { title: '本周药膳日历', adminRoles: ['admin', 'canteen'] },
      },
      {
        path: 'recipe',
        name: 'AdminRecipe',
        component: () => import('@/views/admin/recipe/AdminRecipePage.vue'),
        meta: { title: '药膳管理', adminRoles: ['admin', 'canteen'] },
      },
      {
        path: 'ingredient',
        name: 'AdminIngredient',
        component: () => import('@/views/admin/AdminIngredient.vue'),
        meta: { title: '食材管理', adminRoles: ['admin', 'canteen'] },
      },
      {
        path: 'user',
        name: 'UserManage',
        component: () => import('../views/admin/UserManage.vue'),
        meta: { title: '用户管理', adminRoles: ['admin'] },
      },
      {
        path: 'system/settings',
        name: 'SystemSettings',
        component: () => import('@/views/system/settings/index.vue'),
        meta: { title: '系统设置', adminRoles: ['admin'] },
      },
      {
        path: 'ai-quality',
        name: 'AdminAiQuality',
        component: () => import('@/views/admin/AdminAiQuality.vue'),
        meta: { title: 'AI 质量治理', adminRoles: ['admin'] },
      },
    ],
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'NotFound',
    component: () => import('@/views/NotFound.vue'),
    meta: { title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior() {
    return { top: 0 }
  },
})

router.beforeEach(async (to) => {
  const campusPaths = !to.path.startsWith('/admin') && to.path !== '/account-disabled'
  if (campusPaths) {
    const userStore = useUserStore()
    userStore.ensureUserId()

    if (to.name === 'CampusLogin' && userStore.campusSignedIn === true) {
      const q = to.query.redirect
      const path = campusRedirectPath(typeof q === 'string' ? q : '/home')
      return { path, replace: true }
    }

    /** 校园登录页：须先在门户点击「学生端」，不可直接粘贴 /campus/login 绕过 */
    if (to.name === 'CampusLogin' && !hasPortalCampusOk()) {
      const q = to.query.redirect
      if (typeof q === 'string') stashCampusRedirect(q)
      return { path: '/', replace: true }
    }

    const campusPublic =
      to.name === 'EntrySelect' ||
      to.name === 'EntrySelectMode' ||
      to.name === 'CampusLogin' ||
      to.name === 'AccountDisabled' ||
      to.name === 'NotFound'
    if (!campusPublic && userStore.campusSignedIn !== true) {
      if (!hasPortalCampusOk()) {
        stashCampusRedirect(to.fullPath)
        return { path: '/', replace: true }
      }
      return {
        path: '/campus/login',
        query: { redirect: to.fullPath },
        replace: true,
      }
    }

    /** 后端禁用学生后，仅靠本机 tcm_admin_user_status 无法同步；用 JWT 调 profile 触发 4031 */
    if (!campusPublic) {
      const disabled = await maybeProbeCampusAccountStatus()
      if (disabled) {
        return { path: '/account-disabled', replace: true }
      }
    }

    /**
     * U-01：无本地体质画像时，首页强制进入体质采集（与 Recommend 冷启动引导一致）。
     * 若从「场景食疗」带 scene_tag 进入推荐，则允许先浏览场景相关药膳，再引导测评。
     */
    const sceneTagQ = to.query?.scene_tag ?? to.query?.sceneTag
    const hasSceneTag =
      typeof sceneTagQ === 'string' && sceneTagQ.trim().length > 0
    if (to.name === 'Home' && !userStore.hasProfile && !hasSceneTag) {
      return { path: '/constitution', replace: true }
    }
    const uid = userStore.userId
    /** 门户 / 校园登录页允许切到后台，不因校园端禁用而拦截 */
    if (uid && isUserDisabled(uid) && to.name !== 'EntrySelect' && to.name !== 'CampusLogin') {
      const k = `tcm_disabled_toast_${uid}`
      if (!sessionStorage.getItem(k)) {
        sessionStorage.setItem(k, '1')
        ElMessage.error('该账号已被禁用，请重新注册或联系管理员恢复')
      }
      return { path: '/account-disabled', replace: true }
    }
  }

  if (!to.path.startsWith('/admin')) return true

  const adminAuth = useAdminAuthStore()

  if (to.meta.adminPublic) {
    if (adminAuth.isLoggedIn) {
      return { path: '/admin/dashboard', replace: true }
    }
    /** 后台登录：须先在门户点击「后台管理」，不可直接粘贴 /admin/login 绕过 */
    if (to.name === 'AdminLogin' && !hasPortalAdminOk()) {
      const q = to.query.redirect
      if (typeof q === 'string') stashAdminRedirect(q)
      return { path: '/', replace: true }
    }
    return true
  }

  if (!adminAuth.isLoggedIn) {
    if (!hasPortalAdminOk()) {
      stashAdminRedirect(to.fullPath)
      return { path: '/', replace: true }
    }
    return {
      path: '/admin/login',
      query: { redirect: to.fullPath },
      replace: true,
    }
  }

  const roles = to.meta.adminRoles
  if (Array.isArray(roles) && roles.length && !roles.includes(adminAuth.role)) {
    ElMessage.warning('当前账号无权访问该页面')
    return { path: '/admin/dashboard', replace: true }
  }

  return true
})

router.afterEach((to) => {
  const base = import.meta.env.VITE_APP_TITLE || '校园药膳推荐'
  document.title = to.meta.title ? `${to.meta.title} · ${base}` : base
})

export default router
