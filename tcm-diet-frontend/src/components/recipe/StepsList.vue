<script setup>
defineProps({
  steps: {
    type: Array,
    default: () => [],
  },
})
</script>

<template>
  <section class="block">
    <h3 class="block__title">制作步骤</h3>
    <ol v-if="steps.length" class="steps">
      <li v-for="(s, idx) in steps" :key="idx" class="steps__item">
        <div class="steps__idx">{{ idx + 1 }}</div>
        <div class="steps__body">
          <p class="steps__text">{{ typeof s === 'string' ? s : s.text }}</p>
          <p v-if="s && s.tip" class="steps__tip">{{ s.tip }}</p>
        </div>
      </li>
    </ol>
    <el-empty v-else description="数据暂未完善" :image-size="72" />
  </section>
</template>

<style scoped>
.block__title {
  margin: 0 0 var(--space-md);
  font-size: var(--font-size-lg);
}

.steps {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  counter-reset: none;
}

.steps__item {
  display: flex;
  gap: var(--space-md);
  align-items: flex-start;
}

.steps__idx {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  border-radius: 999px;
  background: var(--color-primary);
  color: #fff;
  font-size: var(--font-size-sm);
  font-weight: 600;
  display: grid;
  place-items: center;
}

.steps__body {
  flex: 1;
  padding-top: 2px;
}

.steps__text {
  margin: 0;
  line-height: 1.6;
  color: var(--color-text);
}

.steps__tip {
  margin: var(--space-xs) 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}
</style>
