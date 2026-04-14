<script setup>
import { useRouter } from 'vue-router'

defineProps({
  plan: {
    type: Object,
    required: true,
  },
})

const router = useRouter()

function goRecipe(recipeId) {
  router.push({
    name: 'RecipeDetail',
    params: { id: String(recipeId) },
  })
}
</script>

<template>
  <div class="plan-view">
    <section class="module page-card">
      <h2 class="module__title">推荐食疗方</h2>
      <p class="module__hint">点击名称查看菜谱详情</p>
      <ul class="recipe-list">
        <li v-for="(r, idx) in plan.recipes" :key="r.recipeId + idx" class="recipe-list__item">
          <button type="button" class="recipe-link" @click="goRecipe(r.recipeId)">
            {{ r.recipeName }}
          </button>
          <p class="recipe-reason">{{ r.matchReason }}</p>
        </li>
      </ul>
    </section>

    <section class="module page-card">
      <h2 class="module__title">核心食材建议</h2>
      <ul class="ingredient-list">
        <li v-for="(it, idx) in plan.coreIngredients" :key="it.name + idx" class="ingredient-list__item">
          <strong>{{ it.name }}</strong>
          <span>{{ it.benefit }}</span>
        </li>
      </ul>
    </section>

    <section class="module page-card">
      <h2 class="module__title">生活调理建议</h2>
      <ul class="bullet-list">
        <li v-for="(line, idx) in plan.lifestyleAdvice" :key="idx">{{ line }}</li>
      </ul>
    </section>

    <section class="module page-card module--caution">
      <h2 class="module__title">禁忌提醒</h2>
      <ul class="bullet-list bullet-list--caution">
        <li v-for="(line, idx) in plan.cautionNotes" :key="idx">{{ line }}</li>
      </ul>
    </section>

    <section class="module page-card">
      <h2 class="module__title">生成原因解读</h2>
      <p class="rationale">{{ plan.rationale }}</p>
      <p v-if="plan.disclaimer" class="disclaimer">{{ plan.disclaimer }}</p>
    </section>
  </div>
</template>

<style scoped>
.plan-view {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.module__title {
  margin: 0 0 var(--space-sm);
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--color-text);
}

.module__hint {
  margin: 0 0 var(--space-md);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.module--caution {
  border-color: rgba(180, 120, 80, 0.35);
  background: rgba(255, 248, 240, 0.55);
}

.recipe-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.recipe-list__item + .recipe-list__item {
  margin-top: var(--space-md);
  padding-top: var(--space-md);
  border-top: 1px solid var(--color-border);
}

.recipe-link {
  display: inline;
  padding: 0;
  border: none;
  background: none;
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-primary);
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.recipe-link:hover {
  color: var(--color-primary-dark);
}

.recipe-reason {
  margin: 6px 0 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.55;
}

.ingredient-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.ingredient-list__item {
  padding: var(--space-sm) 0;
  font-size: var(--font-size-sm);
  line-height: 1.6;
  color: var(--color-text);
  border-bottom: 1px solid var(--color-border);
}

.ingredient-list__item:last-child {
  border-bottom: none;
}

.ingredient-list__item strong {
  display: block;
  margin-bottom: 4px;
  color: var(--color-text);
}

.bullet-list {
  margin: 0;
  padding-left: 1.15rem;
  font-size: var(--font-size-sm);
  line-height: 1.65;
  color: var(--color-text);
}

.bullet-list--caution {
  color: var(--color-text-secondary);
}

.rationale {
  margin: 0;
  font-size: var(--font-size-sm);
  line-height: 1.7;
  color: var(--color-text);
}

.disclaimer {
  margin: var(--space-md) 0 0;
  font-size: var(--font-size-xs);
  line-height: 1.55;
  color: var(--color-text-secondary);
}
</style>
