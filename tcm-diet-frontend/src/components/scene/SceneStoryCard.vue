<script setup>
defineProps({
  scene: {
    type: Object,
    required: true,
  },
  /** 详情页略紧凑 */
  compact: {
    type: Boolean,
    default: false,
  },
})
</script>

<template>
  <article class="story-card" :class="{ 'is-compact': compact }">
    <div class="story-card__cover" :aria-label="scene.name">
      <span class="story-card__emoji" aria-hidden="true">{{ scene.icon }}</span>
      <div class="story-card__text">
        <h2 class="story-card__title">{{ scene.name }}</h2>
        <p v-if="scene.tagline" class="story-card__tagline">{{ scene.tagline }}</p>
        <p v-if="scene.description" class="story-card__desc">{{ scene.description }}</p>
      </div>
    </div>
    <div v-if="scene.painTags?.length" class="story-card__tags" aria-label="痛点标签">
      <span v-for="(t, i) in scene.painTags" :key="i" class="pain-chip">{{ t }}</span>
    </div>
  </article>
</template>

<style scoped>
.story-card {
  border-radius: 20px;
  background: linear-gradient(
    145deg,
    color-mix(in srgb, var(--color-primary) 12%, var(--color-bg-elevated)),
    var(--color-bg-elevated)
  );
  border: 1px solid color-mix(in srgb, var(--color-primary) 22%, var(--color-border));
  overflow: hidden;
  box-shadow: 0 12px 40px color-mix(in srgb, var(--color-text-primary) 6%, transparent);
}

.story-card.is-compact {
  border-radius: 16px;
  box-shadow: none;
}

.story-card__cover {
  display: flex;
  gap: 14px;
  padding: 18px 18px 12px;
  align-items: flex-start;
}

.story-card.is-compact .story-card__cover {
  padding: 14px 14px 10px;
}

.story-card__emoji {
  font-size: 42px;
  line-height: 1;
  flex-shrink: 0;
}

.story-card.is-compact .story-card__emoji {
  font-size: 34px;
}

.story-card__text {
  min-width: 0;
}

.story-card__title {
  margin: 0 0 6px;
  font-size: var(--font-size-lg);
  font-weight: 700;
  line-height: 1.3;
}

.story-card.is-compact .story-card__title {
  font-size: var(--font-size-md);
}

.story-card__tagline {
  margin: 0 0 6px;
  font-size: var(--font-size-sm);
  color: var(--color-primary);
  font-weight: 600;
  line-height: 1.45;
}

.story-card__desc {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.story-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0 18px 16px;
}

.story-card.is-compact .story-card__tags {
  padding: 0 14px 12px;
}

.pain-chip {
  display: inline-block;
  padding: 4px 10px;
  font-size: 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-text-primary) 6%, transparent);
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
}
</style>
