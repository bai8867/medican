<script setup lang="ts">
import { useRouter } from 'vue-router'
import { NavBar as VanNavBar } from 'vant'
import { PRIVACY_POLICY_PARAGRAPHS } from '@/content/legal'

const router = useRouter()
</script>

<template>
  <div class="settings-sub settings-sub--scroll">
    <van-nav-bar title="隐私政策" left-arrow fixed placeholder @click-left="router.back()" />
    <article class="legal-article">
      <p v-for="(p, i) in PRIVACY_POLICY_PARAGRAPHS" :key="i" class="legal-article__p">{{ p }}</p>
    </article>
  </div>
</template>

<style scoped>
.legal-article {
  padding: 16px 20px 32px;
  max-width: 720px;
}

.legal-article__p {
  margin: 0 0 14px;
  font-size: 14px;
  line-height: 1.65;
  color: var(--color-text-primary);
  text-align: left;
}
</style>
