<script setup>
import { defineAsyncComponent, h } from 'vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'

/** 与路由级懒加载叠加：AI 食疗生成页单独 chunk，减轻首包体积 */
const AIGenerateView = defineAsyncComponent({
  loader: () => import('@/views/AIGenerate.vue'),
  loadingComponent: {
    name: 'AIGeneratePageLoading',
    setup() {
      return () => h(LoadingSkeleton, { rows: 10 })
    },
  },
  delay: 0,
})
</script>

<template>
  <AIGenerateView />
</template>
