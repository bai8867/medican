<script setup>
import { defineAsyncComponent, h } from 'vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'

/** 与路由级懒加载叠加：体质采集页单独 chunk */
const ConstitutionView = defineAsyncComponent({
  loader: () => import('@/views/Constitution.vue'),
  loadingComponent: {
    name: 'ConstitutionPageLoading',
    setup() {
      return () => h(LoadingSkeleton, { rows: 10 })
    },
  },
  delay: 0,
})
</script>

<template>
  <ConstitutionView />
</template>
