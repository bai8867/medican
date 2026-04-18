<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Tabbar as VanTabbar, TabbarItem as VanTabbarItem } from 'vant'

const route = useRoute()

function onHomeTabClick() {
  if (route.path === '/home' || route.path === '/recommend') {
    window.dispatchEvent(new CustomEvent('campus-home-refresh'))
  }
}

const showBar = computed(
  () =>
    !route.path.startsWith('/admin') &&
    route.path !== '/account-disabled' &&
    route.path !== '/' &&
    route.path !== '/mode' &&
    route.path !== '/campus/login',
)
</script>

<template>
  <VanTabbar
    v-if="showBar"
    route
    fixed
    placeholder
    safe-area-inset-bottom
    class="campus-mobile-tabbar"
  >
    <VanTabbarItem replace to="/home" icon="wap-home-o" @click="onHomeTabClick">首页</VanTabbarItem>
    <VanTabbarItem replace to="/calendar" icon="calendar-o">日历</VanTabbarItem>
    <VanTabbarItem replace to="/scenes" icon="guide-o">场景</VanTabbarItem>
    <VanTabbarItem replace to="/profile" icon="user-o">我的</VanTabbarItem>
  </VanTabbar>
</template>

<style scoped>
.campus-mobile-tabbar {
  display: none;
}

@media (max-width: 900px) {
  .campus-mobile-tabbar {
    display: block;
  }

  :deep(.van-tabbar) {
    border-top: 1px solid var(--color-border);
    background: color-mix(in srgb, var(--color-bg-elevated) 86%, transparent);
    box-shadow: 0 -10px 30px rgba(44, 44, 42, 0.06);
    backdrop-filter: blur(12px) saturate(140%);
  }

  :deep(.van-tabbar-item) {
    color: var(--color-text-secondary);
    transition: color var(--duration-fast) var(--ease-out);
  }

  :deep(.van-tabbar-item--active) {
    color: var(--color-primary);
    font-weight: 600;
  }
}
</style>
