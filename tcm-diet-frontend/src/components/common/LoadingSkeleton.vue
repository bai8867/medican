<script setup>
defineProps({
  rows: {
    type: Number,
    default: 6,
  },
})
</script>

<template>
  <div class="skel">
    <el-skeleton animated :count="rows">
      <template #template>
        <div style="padding: 8px 0">
          <el-skeleton-item variant="image" style="width: 100%; height: 160px" />
          <div style="padding: 14px 0 0">
            <el-skeleton-item variant="h3" style="width: 50%" />
            <el-skeleton-item variant="text" style="margin-top: 12px" />
            <el-skeleton-item variant="text" style="margin-top: 8px" />
          </div>
        </div>
      </template>
    </el-skeleton>
  </div>
</template>

<style scoped>
.skel {
  width: 100%;
}
</style>
