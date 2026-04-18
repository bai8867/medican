<script setup>
import { computed, onMounted, onUnmounted } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import NavSidebar from '@/components/common/NavSidebar.vue'
import CampusMobileTabbar from '@/components/campus/CampusMobileTabbar.vue'
import {
  maybeProbeCampusAccountStatus,
  notifyCampusTabForeground,
} from '@/utils/campusAccountProbe'

const route = useRoute()
const isAdminRoute = computed(
  () => route.path.startsWith('/admin') || route.path === '/account-disabled',
)
const isCampusFullBleed = computed(
  () => route.path === '/' || route.path === '/mode' || route.path === '/campus/login',
)

function onVisibilityForCampusProbe() {
  if (document.visibilityState !== 'visible') return
  notifyCampusTabForeground()
  maybeProbeCampusAccountStatus()
}

onMounted(() => {
  document.addEventListener('visibilitychange', onVisibilityForCampusProbe)
})
onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityForCampusProbe)
})
</script>

<template>
  <div v-if="isAdminRoute" class="app-shell app-shell--admin">
    <RouterView />
  </div>
  <div v-else-if="isCampusFullBleed" class="app-shell app-shell--campus-entry">
    <RouterView />
  </div>
  <div v-else class="app-shell app-shell--campus-student">
    <NavSidebar />
    <div class="app-shell__body">
      <main class="app-shell__main">
        <RouterView v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" :key="route.fullPath" />
          </transition>
        </RouterView>
      </main>
      <CampusMobileTabbar />
    </div>
  </div>
</template>

<style scoped>
.app-shell--admin,
.app-shell--campus-entry {
  max-width: none;
  margin: 0;
}

/* 覆盖 .app-shell 的横向 flex，使登录/门户子页面占满视口宽度，flex 居中才相对整屏生效 */
.app-shell--campus-entry,
.app-shell--admin {
  display: block;
}

/* 布局与主题背景见 src/style/global.css */

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(6px);
}
</style>
