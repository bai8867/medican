<script setup>
import { defineAsyncComponent, h } from 'vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'

/** 与路由级懒加载叠加：场景食疗列表单独 chunk */
const CampusScenesView = defineAsyncComponent({
  loader: () => import('@/views/CampusScenes.vue'),
  loadingComponent: {
    name: 'CampusScenesPageLoading',
    setup() {
      return () => h(LoadingSkeleton, { rows: 8 })
    },
  },
  delay: 0,
})
</script>

<template>
  <CampusScenesView />
</template>
