<script setup>
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { HomeFilled, Management } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import {
  clearPortalGateFlags,
  setPortalCampusOk,
  setPortalAdminOk,
  takeCampusRedirectPending,
  takeAdminRedirectPending,
} from '@/utils/portalGate.js'
import '@/style/admin.css'

const router = useRouter()
const userStore = useUserStore()

/** 从门户重新进入时要求再走校园登录（避免本地持久化 campusSignedIn 导致跳过登录页） */
onMounted(() => {
  userStore.$patch({ campusSignedIn: false })
  clearPortalGateFlags()
})

function goCampus() {
  setPortalCampusOk()
  const pending = takeCampusRedirectPending()
  router.push({
    path: '/campus/login',
    query:
      pending && pending.startsWith('/') && !pending.startsWith('/admin')
        ? { redirect: pending }
        : {},
  })
}

function goAdmin() {
  setPortalAdminOk()
  const pending = takeAdminRedirectPending()
  router.push({
    path: '/admin/login',
    query: pending && pending.startsWith('/admin') ? { redirect: pending } : {},
  })
}
</script>

<template>
  <div class="admin-login-page entry-select">
    <div class="admin-login-card entry-select__card">
      <h1 class="entry-select__title">校园药膳推荐系统</h1>
      <p class="entry-select__hint">请选择进入模式</p>
      <div class="entry-select__actions">
        <el-button type="primary" size="large" class="entry-select__btn" @click="goCampus">
          <span class="entry-select__btn-inner">
            <el-icon class="entry-select__icon"><HomeFilled /></el-icon>
            <span class="entry-select__label">学生端 · 校园端（推荐 / 体质 / AI）</span>
          </span>
        </el-button>
        <el-button size="large" class="entry-select__btn" @click="goAdmin">
          <span class="entry-select__btn-inner">
            <el-icon class="entry-select__icon"><Management /></el-icon>
            <span class="entry-select__label">后台管理</span>
          </span>
        </el-button>
      </div>
      <p class="entry-select__sub">
        须在本页选择进入方式：直接打开或收藏内页、登录页地址将无法绕过此步骤。后台需具备权限的账号。
      </p>
    </div>
  </div>
</template>

<style scoped>
.entry-select__card {
  max-width: 440px;
}

.entry-select__title {
  margin: 0 0 var(--space-sm);
  font-size: var(--font-size-xl);
  color: var(--color-text-primary);
  font-family: var(--font-serif);
  font-weight: 600;
  text-align: center;
}

.entry-select__hint {
  margin: 0 0 var(--space-lg);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  text-align: center;
}

.entry-select__actions {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.entry-select__actions :deep(.entry-select__btn.el-button) {
  width: 100%;
  margin: 0;
  min-height: 48px;
  display: inline-flex;
  justify-content: flex-start;
  padding-left: 18px;
  padding-right: 18px;
}

/* 默认插槽外层 span，占满按钮内宽以便栅格对齐 */
.entry-select__actions :deep(.entry-select__btn > span) {
  flex: 1;
  min-width: 0;
  display: block;
  width: 100%;
}

.entry-select__btn-inner {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr);
  column-gap: 10px;
  align-items: center;
  box-sizing: border-box;
}

.entry-select__icon {
  margin: 0;
  font-size: 18px;
  justify-self: center;
}

.entry-select__label {
  text-align: left;
  line-height: 1.35;
  min-width: 0;
}

.entry-select__sub {
  margin: var(--space-lg) 0 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  line-height: 1.5;
  text-align: center;
}
</style>
