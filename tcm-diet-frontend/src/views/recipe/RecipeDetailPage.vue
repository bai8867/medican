<script setup>
import { defineAsyncComponent, h } from 'vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'

/** 与路由级懒加载叠加：菜谱详情单独 chunk */
const RecipeDetailView = defineAsyncComponent({
  loader: () => import('@/views/RecipeDetail.vue'),
  loadingComponent: {
    name: 'RecipeDetailPageLoading',
    setup() {
      return () => h(LoadingSkeleton, { rows: 8 })
    },
  },
  delay: 0,
})
</script>

<template>
  <RecipeDetailView />
</template>
