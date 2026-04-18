<script setup>
import RecipeCard from '@/components/common/RecipeCard.vue'

defineProps({
  calendarPublished: { type: Boolean, default: true },
  weekOfferRecipes: { type: Array, default: () => [] },
  visibleSlice: { type: Array, default: () => [] },
})

const emit = defineEmits(['patchCollect', 'notInterested', 'openRecipeDetail', 'goAi'])
</script>

<template>
  <section v-if="calendarPublished" class="week-offer">
    <div class="week-offer__head">
      <span class="week-offer__dot" aria-hidden="true" />
      <h2 class="week-offer__title">本周可点推荐</h2>
    </div>
    <p v-if="!weekOfferRecipes.length" class="week-offer__empty">
      当前筛选下暂无与本周日历匹配的菜品，可尝试调整功效或体质筛选，或查看下方「通用养生推荐」。
    </p>
    <div v-else class="masonry masonry--week">
      <RecipeCard
        v-for="r in weekOfferRecipes"
        :key="'wk-' + r.id"
        :recipe="r"
        :corner-badge="r.campusWindowLabel || '本周供应'"
        @patch-collect="emit('patchCollect', $event)"
        @not-interested="emit('notInterested', $event)"
        @open-detail="emit('openRecipeDetail', $event)"
      />
    </div>
  </section>

  <div class="general-head">
    <h2 class="general-head__title">通用养生推荐</h2>
    <p class="general-head__sub">无固定校园窗口的知识类药膳与调养内容，可按体质与季节浏览。</p>
  </div>

  <div class="masonry">
    <template
      v-for="(item, idx) in visibleSlice"
      :key="item.kind === 'ai' ? item.id : item.recipe.id + '-' + idx"
    >
      <RecipeCard
        v-if="item.kind === 'recipe'"
        :recipe="item.recipe"
        @patch-collect="emit('patchCollect', $event)"
        @not-interested="emit('notInterested', $event)"
        @open-detail="emit('openRecipeDetail', $event)"
      />
      <button
        v-else
        type="button"
        class="ai-tile page-card"
        @click="emit('goAi')"
      >
        <span class="ai-tile__kicker">AI 助手</span>
        <span class="ai-tile__title">生成我的药膳方案</span>
        <span class="ai-tile__desc">结合体质、忌口与当季食材，一键获得搭配建议</span>
        <span class="ai-tile__cta">立即体验 →</span>
      </button>
    </template>
  </div>
</template>

<style scoped>
.page-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.week-offer {
  margin-bottom: var(--space-lg);
}

.week-offer__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: var(--space-sm);
}

.week-offer__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.25);
  flex-shrink: 0;
}

.week-offer__title {
  margin: 0;
  font-size: var(--font-size-md);
  font-weight: 700;
  color: var(--color-text-primary);
}

.week-offer__empty {
  margin: 0 0 var(--space-md);
  padding: var(--space-md) var(--space-lg);
  font-size: var(--font-size-sm);
  line-height: 1.55;
  color: var(--color-text-secondary);
  background: var(--color-bg-surface);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-lg);
}

.general-head {
  margin-bottom: var(--space-md);
}

.general-head__title {
  margin: 0 0 6px;
  font-size: var(--font-size-md);
  font-weight: 700;
  color: var(--color-text-primary);
}

.general-head__sub {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
  line-height: 1.5;
}

.masonry {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-lg);
  align-items: start;
}

@media (min-width: 900px) {
  .masonry {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.masonry :deep(.card),
.masonry .ai-tile {
  margin-bottom: 0;
}

.masonry--week {
  margin-bottom: var(--space-md);
}

.ai-tile {
  width: 100%;
  text-align: left;
  border: 1px dashed var(--color-border-strong);
  background: linear-gradient(145deg, #faf9f6, #f0eee8);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  transition:
    transform 0.15s ease,
    box-shadow 0.15s ease,
    border-color 0.15s ease;
}

.ai-tile:hover {
  transform: translateY(-2px);
  border-color: var(--color-border-hover-primary);
  box-shadow: var(--shadow-card-hover-float);
}

.ai-tile__kicker {
  font-size: var(--font-size-xs);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-ai);
}

.ai-tile__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-text-primary);
}

.ai-tile__desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.55;
}

.ai-tile__cta {
  margin-top: var(--space-xs);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-primary);
}
</style>
