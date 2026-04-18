<script setup>
import { defineAsyncComponent, h } from 'vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'

/** 与路由级懒加载叠加：本周药膳日历单独 chunk */
const CampusWeeklyCalendarView = defineAsyncComponent({
  loader: () => import('@/views/CampusWeeklyCalendar.vue'),
  loadingComponent: {
    name: 'CampusWeeklyCalendarPageLoading',
    setup() {
      return () => h(LoadingSkeleton, { rows: 8 })
    },
  },
  delay: 0,
})
</script>

<template>
  <CampusWeeklyCalendarView />
</template>
