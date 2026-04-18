<script setup>
import { defineAsyncComponent, h } from 'vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'

/** 与路由级懒加载叠加：AI 方案详情单独 chunk */
const AIPlanDetailView = defineAsyncComponent({
  loader: () => import('@/views/AIPlanDetail.vue'),
  loadingComponent: {
    name: 'AIPlanDetailPageLoading',
    setup() {
      return () => h(LoadingSkeleton, { rows: 8 })
    },
  },
  delay: 0,
})
</script>

<template>
  <AIPlanDetailView />
</template>
