<script setup>
import { computed, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useCollectStore } from '@/stores/collect'
import { setRecipeFavorite } from '@/api/recipe.js'
import { looksLikeBearerJwt } from '@/utils/authToken.js'
import { readCampusToken } from '@/utils/storedTokens.js'

const props = defineProps({
  recipe: {
    type: Object,
    required: true,
  },
  /** 本周日历窗口角标（半透明黑底白字） */
  cornerBadge: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['patch-collect', 'not-interested'])

const router = useRouter()
const collectStore = useCollectStore()

let longPressTimer = null
const longPressMs = 650

const effectTags = computed(() => {
  const raw = props.recipe.effectTags
  if (Array.isArray(raw) && raw.length) return raw.slice(0, 3)
  const text = props.recipe.effect || ''
  if (!text) return []
  return text
    .split(/[,，、;；]/)
    .map((s) => s.trim())
    .filter(Boolean)
    .slice(0, 3)
})

const suitLabels = computed(() => {
  const list = props.recipe.suitConstitutions
  if (Array.isArray(list) && list.length) return list
  if (props.recipe.suitConstitution) return [props.recipe.suitConstitution]
  return []
})

const collectCount = computed(() => {
  const n = Number(props.recipe.collectCount)
  return Number.isFinite(n) ? n : 0
})

const reasonLine = computed(
  () => props.recipe.recommendReason || props.recipe.summary || props.recipe.effect || '',
)

function openDetail() {
  const r = props.recipe
  collectStore.pushHistory(r.id, {
    name: r.name || '药膳',
    coverUrl: typeof r.coverUrl === 'string' ? r.coverUrl : '',
    subtitle: r.efficacySummary || r.effect || r.summary || '',
  })
  router.push({ path: `/recipe/${encodeURIComponent(String(r.id))}` })
}

async function onCollect(e) {
  e.stopPropagation()
  if (import.meta.env.VITE_USE_MOCK !== 'true') {
    const tk = readCampusToken()
    if (!looksLikeBearerJwt(tk)) {
      ElMessage.warning('请先登录后再收藏')
      router.push({
        path: '/campus/login',
        query: { redirect: router.currentRoute.value.fullPath },
      })
      return
    }
  }
  const rid = props.recipe.id
  const was = collectStore.isCollected(rid)
  const prevCount = Number(props.recipe.collectCount) || 0
  collectStore.toggleCollect(rid)
  emit('patch-collect', {
    id: rid,
    collectCount: Math.max(0, prevCount + (was ? -1 : 1)),
  })
  try {
    const data = await setRecipeFavorite(rid, !was)
    if (data != null && typeof data.collectCount === 'number') {
      emit('patch-collect', { id: rid, collectCount: data.collectCount })
    }
  } catch (e) {
    collectStore.toggleCollect(rid)
    emit('patch-collect', { id: rid, collectCount: prevCount })
    if (import.meta.env.VITE_USE_MOCK !== 'true' && (e?.code === 401 || /登录/.test(String(e?.message || '')))) {
      ElMessage.warning(typeof e?.message === 'string' ? e.message : '请先登录后再收藏')
      router.push({
        path: '/campus/login',
        query: { redirect: router.currentRoute.value.fullPath },
      })
    }
  }
}

function clearLongPress() {
  if (longPressTimer) {
    clearTimeout(longPressTimer)
    longPressTimer = null
  }
}

function onPointerDown(e) {
  if (e.target?.closest?.('button')) return
  if (e.button !== undefined && e.button !== 0) return
  clearLongPress()
  longPressTimer = setTimeout(() => {
    longPressTimer = null
    if (window.confirm('标记为不感兴趣？该药膳将在推荐流中隐藏。')) {
      emit('not-interested', props.recipe.id)
    }
  }, longPressMs)
}

function onPointerUp() {
  clearLongPress()
}

function onContextMenu(e) {
  e.preventDefault()
  if (window.confirm('标记为不感兴趣？该药膳将在推荐流中隐藏。')) {
    emit('not-interested', props.recipe.id)
  }
}

function onDismissClick(e) {
  e.stopPropagation()
  if (window.confirm('标记为不感兴趣？该药膳将在推荐流中隐藏。')) {
    emit('not-interested', props.recipe.id)
  }
}

onUnmounted(() => {
  clearLongPress()
})
</script>

<template>
  <article
    class="card"
    @click="openDetail"
    @pointerdown="onPointerDown"
    @pointerup="onPointerUp"
    @pointerleave="onPointerUp"
    @pointercancel="onPointerUp"
    @contextmenu="onContextMenu"
  >
    <div class="card__cover">
      <span v-if="cornerBadge" class="card__window-badge">{{ cornerBadge }}</span>
      <img
        v-if="recipe.coverUrl"
        :src="recipe.coverUrl"
        :alt="recipe.name"
        loading="lazy"
      />
      <div v-else class="card__placeholder">{{ recipe.name?.slice(0, 1) }}</div>
      <button
        type="button"
        class="card__fav"
        :aria-pressed="collectStore.isCollected(recipe.id)"
        aria-label="收藏"
        @click="onCollect"
      >
        <svg
          class="card__heart"
          viewBox="0 0 24 24"
          width="22"
          height="22"
          aria-hidden="true"
        >
          <path
            v-if="collectStore.isCollected(recipe.id)"
            fill="currentColor"
            d="M12 21s-6.716-4.438-9.33-8.15C.55 10.702.5 6.5 3.23 4.36 4.83 3.09 7.21 2.8 9 4c.78.58 1.38 1.4 2 2.2.62-.8 1.22-1.62 2-2.2 1.79-1.2 4.17-.91 5.77.36 2.73 2.14 2.82 6.34-.47 8.49C18.72 16.56 12 21 12 21Z"
          />
          <path
            v-else
            fill="none"
            stroke="currentColor"
            stroke-width="1.75"
            d="M12 21s-6.716-4.438-9.33-8.15C.55 10.702.5 6.5 3.23 4.36 4.83 3.09 7.21 2.8 9 4c.78.58 1.38 1.4 2 2.2.62-.8 1.22-1.62 2-2.2 1.79-1.2 4.17-.91 5.77.36 2.73 2.14 2.82 6.34-.47 8.49C18.72 16.56 12 21 12 21Z"
          />
        </svg>
      </button>
    </div>
    <div class="card__body">
      <h3 class="card__title">{{ recipe.name }}</h3>
      <div v-if="effectTags.length" class="card__tags card__tags--fx">
        <el-tag
          v-for="(tag, idx) in effectTags"
          :key="'fx-' + idx"
          size="small"
          type="warning"
          effect="plain"
        >
          {{ tag }}
        </el-tag>
      </div>
      <div v-if="suitLabels.length" class="card__tags">
        <el-tag
          v-for="(lab, idx) in suitLabels"
          :key="'sc-' + idx"
          size="small"
          type="success"
          effect="light"
        >
          宜 {{ lab }}
        </el-tag>
      </div>
      <div class="card__stats">
        <span class="card__fav-count">收藏 {{ collectCount }}</span>
        <span v-if="recipe.cookTime" class="card__meta">{{ recipe.cookTime }}</span>
      </div>
      <p v-if="reasonLine" class="card__reason">{{ reasonLine }}</p>
      <button type="button" class="card__dismiss" @click="onDismissClick">不感兴趣</button>
    </div>
  </article>
</template>

<style scoped>
.card {
  background: var(--color-bg-surface);
  border-radius: var(--radius-lg);
  overflow: hidden;
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-card);
  cursor: pointer;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
  display: flex;
  flex-direction: column;
  break-inside: avoid;
  margin-bottom: var(--space-md);
}

.card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-card-hover);
}

.card__cover {
  position: relative;
  aspect-ratio: 4 / 3;
  background: var(--color-bg-elevated);
}

.card__window-badge {
  position: absolute;
  top: 8px;
  left: 8px;
  z-index: 2;
  max-width: calc(100% - 56px);
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 11px;
  line-height: 1.35;
  font-weight: 600;
  color: #fff;
  background: rgba(0, 0, 0, 0.55);
  pointer-events: none;
}

.card__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card__placeholder {
  height: 100%;
  display: grid;
  place-items: center;
  font-size: 36px;
  font-weight: 700;
  color: var(--color-primary-light);
  background: linear-gradient(135deg, #ecfdf5, #d8f3dc);
}

.card__fav {
  position: absolute;
  right: 10px;
  bottom: 10px;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 2px 10px rgba(44, 44, 42, 0.12);
  cursor: pointer;
  color: #c75c5c;
  transition: transform 0.12s ease, background 0.12s ease;
}

.card__fav:hover {
  transform: scale(1.06);
  background: #fff;
}

.card__fav[aria-pressed='true'] {
  color: #b84444;
  background: rgba(255, 236, 236, 0.95);
}

.card__heart {
  display: block;
}

.card__body {
  padding: var(--space-md);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.card__title {
  margin: 0;
  font-size: var(--font-size-lg);
  line-height: 1.35;
}

.card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.card__tags--fx :deep(.el-tag) {
  border-radius: var(--radius-sm);
}

.card__stats {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-md);
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
}

.card__fav-count {
  color: var(--color-text-secondary);
  font-weight: 500;
}

.card__meta {
  color: var(--color-text-muted);
}

.card__reason {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card__dismiss {
  align-self: flex-start;
  margin-top: var(--space-xs);
  padding: 0;
  border: none;
  background: none;
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
  text-decoration: underline;
  cursor: pointer;
}

.card__dismiss:hover {
  color: var(--color-text-secondary);
}
</style>
