<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { House, MagicStick, Star, EditPen, Compass, Setting, Calendar } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const displayName = computed(() => {
  const n = (userStore.username || '').trim()
  return n || '访客'
})

const avatarChar = computed(() => {
  const s = displayName.value
  return s ? s.slice(0, 1) : '访'
})

const menuItems = computed(() => [
  { path: '/home', label: '推荐（首页）', icon: House },
  { path: '/calendar', label: '本周日历', icon: Calendar },
  { path: '/scenes', label: '场景食疗', icon: Compass },
  { path: '/ai', label: 'AI食疗方案', icon: MagicStick },
  { path: '/profile', label: '我的收藏', icon: Star },
  { path: '/settings', label: '设置', icon: Setting },
])

function isMenuActive(path) {
  if (path === '/calendar') {
    return route.path === '/calendar'
  }
  if (path === '/scenes') {
    return route.path === '/scenes' || route.path.startsWith('/scene/')
  }
  if (path === '/settings') {
    return route.path === '/settings' || route.path.startsWith('/settings/')
  }
  if (path === '/home') {
    return route.path === '/home' || route.path === '/recommend'
  }
  return route.path === path
}

function go(path) {
  if (route.path !== path) {
    router.push(path)
    return
  }
  if (path === '/home' || path === '/recommend') {
    window.dispatchEvent(new CustomEvent('campus-home-refresh'))
  }
}

function goConstitution() {
  if (route.path !== '/constitution') router.push('/constitution')
}

function goProfile() {
  go('/profile')
}
</script>

<template>
  <aside class="nav app-shell__nav" aria-label="主导航">
    <header class="nav__brand">
      <div class="nav__logo" aria-hidden="true">膳</div>
      <div class="nav__brand-text">
        <span class="nav__system-name">校园药膳推荐系统</span>
      </div>
    </header>

    <nav class="nav__menu" aria-label="功能菜单">
      <button
        v-for="item in menuItems"
        :key="item.path"
        type="button"
        class="nav__item"
        :class="{ 'is-active': isMenuActive(item.path) }"
        :aria-current="isMenuActive(item.path) ? 'page' : undefined"
        @click="go(item.path)"
      >
        <el-icon class="nav__icon" :size="18"><component :is="item.icon" /></el-icon>
        <span class="nav__label">{{ item.label }}</span>
      </button>
    </nav>

    <footer class="nav__footer">
      <button
        type="button"
        class="nav__user"
        aria-label="进入我的页面"
        @click="goProfile"
      >
        <el-avatar class="nav__avatar" :size="44" aria-hidden="true">
          {{ avatarChar }}
        </el-avatar>
        <div class="nav__user-meta">
          <span class="nav__user-name">{{ displayName }}</span>
          <el-tag size="small" effect="plain" class="nav__const-tag" round>
            {{ userStore.constitutionLabel }}
          </el-tag>
        </div>
      </button>
      <button type="button" class="nav__const-link" @click="goConstitution">
        <el-icon :size="16"><EditPen /></el-icon>
        <span>修改体质</span>
      </button>
    </footer>
  </aside>
</template>

<style scoped>
.nav {
  width: 100%;
  box-sizing: border-box;
  min-height: 100vh;
  height: 100vh;
  max-height: 100vh;
  position: sticky;
  top: 0;
  align-self: flex-start;
  overflow-y: auto;
  padding: var(--space-lg) var(--space-md);
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
  background: var(--color-bg-elevated);
  border-right: 1px solid var(--color-border);
  box-shadow: var(--shadow-soft);
}

.nav__brand {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-xs) 0;
}

.nav__logo {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-md);
  display: grid;
  place-items: center;
  font-family: var(--font-serif);
  font-weight: 700;
  font-size: var(--font-size-xl);
  color: #fff;
  background: var(--color-primary);
  flex-shrink: 0;
}

.nav__brand-text {
  min-width: 0;
}

.nav__system-name {
  display: block;
  font-family: var(--font-serif);
  font-size: var(--font-size-md);
  font-weight: 600;
  line-height: var(--line-height-tight);
  color: var(--color-primary-dark);
  letter-spacing: 0.04em;
}

.nav__menu {
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  flex: 1;
}

.nav__item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  width: 100%;
  padding: 12px 14px;
  border: 1px solid transparent;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-text-primary);
  font-family: var(--font-sans);
  font-size: var(--font-size-md);
  cursor: pointer;
  text-align: left;
  transition:
    background var(--duration-fast) var(--ease-out),
    color var(--duration-fast) var(--ease-out),
    border-color var(--duration-fast) var(--ease-out),
    transform var(--duration-fast) var(--ease-out);
}

.nav__item:hover {
  background: color-mix(in srgb, var(--color-primary) 8%, transparent);
  color: var(--color-primary);
}

.nav__item:active:not(:disabled) {
  transform: translateY(1px);
}

.nav__item.is-active {
  background: color-mix(in srgb, var(--color-primary) 12%, transparent);
  color: var(--color-primary);
  border-color: color-mix(in srgb, var(--color-primary) 35%, transparent);
  font-weight: 600;
}

.nav__item.is-active .nav__icon {
  color: var(--color-primary);
}

.nav__icon {
  color: var(--color-text-secondary);
  flex-shrink: 0;
}

.nav__item:hover .nav__icon {
  color: var(--color-primary);
}

.nav__label {
  flex: 1;
  line-height: 1.3;
}

.nav__footer {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-border);
}

.nav__user {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  width: 100%;
  margin: 0;
  padding: 8px 6px;
  border: none;
  border-radius: var(--radius-md);
  background: transparent;
  font: inherit;
  text-align: left;
  color: inherit;
  cursor: pointer;
  transition: background 0.15s ease;
}

.nav__user:hover {
  background: color-mix(in srgb, var(--color-primary) 8%, transparent);
}

.nav__user:focus-visible {
  outline: 2px solid var(--color-primary);
  outline-offset: 2px;
}

.nav__avatar {
  flex-shrink: 0;
  background: var(--color-primary);
  color: #fff;
  font-weight: 600;
}

.nav__user-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.nav__user-name {
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nav__const-tag {
  align-self: flex-start;
  border-color: color-mix(in srgb, var(--color-primary) 45%, transparent);
  color: var(--color-primary);
  background: color-mix(in srgb, var(--color-primary) 6%, transparent);
}

.nav__const-link {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 10px 12px;
  border: 1px dashed color-mix(in srgb, var(--color-primary) 45%, transparent);
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--color-primary);
  font-family: var(--font-sans);
  font-size: var(--font-size-sm);
  cursor: pointer;
  transition:
    background 0.15s ease,
    border-color 0.15s ease;
}

.nav__const-link:hover {
  background: color-mix(in srgb, var(--color-primary) 8%, transparent);
  border-color: var(--color-primary);
}

.nav__const-link:focus-visible {
  outline: var(--focus-ring-width) solid var(--focus-ring-color);
  outline-offset: var(--focus-ring-offset);
}

.nav__item:focus-visible {
  outline: var(--focus-ring-width) solid var(--focus-ring-color);
  outline-offset: var(--focus-ring-offset);
}
</style>
