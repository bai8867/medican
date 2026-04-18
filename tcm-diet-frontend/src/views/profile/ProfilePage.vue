<script setup>
import { defineAsyncComponent, h } from 'vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'

/** 与路由级懒加载叠加：个人中心单独 chunk */
const ProfileView = defineAsyncComponent({
  loader: () => import('@/views/Profile.vue'),
  loadingComponent: {
    name: 'ProfilePageLoading',
    setup() {
      return () => h(LoadingSkeleton, { rows: 8 })
    },
  },
  delay: 0,
})
</script>

<template>
  <ProfileView />
</template>
