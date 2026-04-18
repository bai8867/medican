<script setup>
import { defineAsyncComponent, h } from 'vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'

/** 与路由级懒加载叠加：推荐主视图单独 chunk，减轻首包解析压力 */
const RecommendView = defineAsyncComponent({
  loader: () => import('@/views/Recommend.vue'),
  loadingComponent: {
    name: 'RecommendPageLoading',
    setup() {
      return () => h(LoadingSkeleton, { rows: 8 })
    },
  },
  delay: 0,
})
</script>

<template>
  <RecommendView />
</template>
