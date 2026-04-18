<script setup>
import { useRouter } from 'vue-router'
import { Button as VanButton } from 'vant'

const router = useRouter()

function goMode() {
  router.replace({ path: '/mode' })
}
</script>

<template>
  <div class="not-found page">
    <div class="page-card not-found__card">
      <h1 class="not-found__title">页面不存在</h1>
      <p class="not-found__hint">您访问的地址有误或页面已被移除（404）。</p>
      <div class="not-found__actions">
        <van-button type="primary" @click="goMode">返回模式选择</van-button>
        <van-button @click="router.replace({ path: '/home' })">去推荐首页</van-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  max-width: 520px;
  margin: 0 auto;
  padding: var(--space-xl) var(--space-lg);
}

.not-found__card {
  padding: var(--space-xl);
  text-align: center;
}

.not-found__title {
  margin: 0 0 var(--space-sm);
  font-size: var(--font-size-xl);
  font-weight: 600;
}

.not-found__hint {
  margin: 0 0 var(--space-lg);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.55;
}

.not-found__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  justify-content: center;
}
</style>
