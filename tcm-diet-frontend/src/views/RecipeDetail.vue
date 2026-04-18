<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, Button as VanButton, Tag as VanTag, Empty as VanEmpty, Icon as VanIcon } from 'vant'
import IngredientsList from '@/components/recipe/IngredientsList.vue'
import StepsList from '@/components/recipe/StepsList.vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import {
  fetchRecipeDetail,
  getDemoRecipe,
  shouldUseLocalDemoRecipeDetailFallback,
  normalizeRecipeDetail,
  unwrapDetail,
  setRecipeFavorite,
  postRecipeContentFeedback,
  RECIPE_DETAIL_FOOTER_LEGAL,
} from '@/api/recipe'
import { useCollectStore } from '@/stores/collect'
import { looksLikeBearerJwt } from '@/utils/authToken'
import { readCampusToken } from '@/utils/storedTokens'
import { SEASON_OPTIONS } from '@/utils/season'

const route = useRoute()
const router = useRouter()
const collectStore = useCollectStore()

const loading = ref(true)
const notFound = ref(false)
const recipe = ref(null)
const imageBroken = ref(false)
const feedback = ref('')

const coverImgSrc = ref('')
const coverUsedFallback = ref(false)

const id = computed(() => route.query.recipe_id || route.params.id)

const collected = computed(() =>
  recipe.value ? collectStore.isCollected(recipe.value.id) : false,
)

const seasonLine = computed(() => {
  const codes = recipe.value?.seasonFit
  if (!codes?.length) return '—'
  if (codes.includes('all')) return '四季皆宜'
  return codes
    .map((c) => SEASON_OPTIONS.find((s) => s.code === c)?.label || c)
    .join('、')
})

const hasIngredients = computed(() =>
  (recipe.value?.ingredientGroups || []).some((g) => g.items?.length),
)

const hasSteps = computed(() => (recipe.value?.steps || []).length > 0)

const showCoverImg = computed(() => Boolean(coverImgSrc.value) && !imageBroken.value)

function feedbackStorageKey(rid) {
  return `tcm_recipe_detail_feedback_${rid}`
}

function onImgError() {
  const base = import.meta.env.BASE_URL || '/'
  const normalizedBase = base.endsWith('/') ? base : `${base}/`
  const fallback = `${normalizedBase}default-recipe.png`
  if (!coverUsedFallback.value && coverImgSrc.value !== fallback) {
    coverUsedFallback.value = true
    coverImgSrc.value = fallback
    return
  }
  imageBroken.value = true
}

async function setFeedback(v) {
  if (feedback.value === v) return
  if (!recipe.value?.id) return
  const rid = String(recipe.value.id)
  feedback.value = v
  try {
    localStorage.setItem(feedbackStorageKey(rid), v)
  } catch {
    /* ignore */
  }
  try {
    await postRecipeContentFeedback(rid, v)
  } catch {
    /* 后端未就绪时仅本地记录 */
  }
  showToast({ type: 'success', message: '感谢反馈' })
}

function goAiCustomize() {
  if (!recipe.value) return
  const tags = recipe.value.effectTags || []
  router.push({
    path: '/ai',
    query: {
      recipeId: String(recipe.value.id),
      recipeName: recipe.value.name || '',
      effectTags: tags.map((t) => encodeURIComponent(String(t))).join(','),
    },
  })
}

async function onToggleFavorite() {
  if (!recipe.value) return
  if (import.meta.env.VITE_USE_MOCK !== 'true') {
    const tk = readCampusToken()
    if (!looksLikeBearerJwt(tk)) {
      showToast('请先登录后再收藏')
      router.push({
        path: '/campus/login',
        query: { redirect: route.fullPath },
      })
      return
    }
  }
  const rid = recipe.value.id
  const was = collectStore.isCollected(rid)
  const prevCount = recipe.value.collectCount ?? 0
  collectStore.toggleCollect(rid)
  recipe.value.collectCount = Math.max(0, prevCount + (was ? -1 : 1))
  try {
    const data = await setRecipeFavorite(rid, !was)
    if (data != null && typeof data.collectCount === 'number') {
      recipe.value.collectCount = data.collectCount
    }
  } catch (e) {
    collectStore.toggleCollect(rid)
    recipe.value.collectCount = prevCount
    if (import.meta.env.VITE_USE_MOCK !== 'true' && (e?.code === 401 || /登录/.test(String(e?.message || '')))) {
      showToast(typeof e?.message === 'string' ? e.message : '请先登录后再收藏')
      router.push({
        path: '/campus/login',
        query: { redirect: route.fullPath },
      })
    }
  }
}

function recordLocalBrowseHistory() {
  const r = recipe.value
  if (!r?.id) return
  collectStore.pushHistory(r.id, {
    name: r.name || '药膳',
    coverUrl: r.coverUrl || '',
    subtitle: r.efficacySummary || r.effect || r.summary || '',
  })
}

async function load() {
  loading.value = true
  notFound.value = false
  recipe.value = null
  imageBroken.value = false
  coverImgSrc.value = ''
  coverUsedFallback.value = false
  try {
    const data = await fetchRecipeDetail(id.value)
    recipe.value = normalizeRecipeDetail(unwrapDetail(data))
    if (!recipe.value) {
      notFound.value = true
      showToast('未找到该菜谱')
    } else if (typeof recipe.value.favorited === 'boolean') {
      if (recipe.value.favorited) collectStore.addCollect(recipe.value.id)
      else collectStore.removeCollect(recipe.value.id)
    }
  } catch {
    const allowDemo = shouldUseLocalDemoRecipeDetailFallback(id.value)
    const demo = allowDemo ? getDemoRecipe(id.value) : null
    recipe.value = demo ? normalizeRecipeDetail(demo) : null
    if (!recipe.value) {
      notFound.value = true
      showToast(
        allowDemo ? '未找到该菜谱' : '药膳详情需从服务器加载，请确认后端已启动或该药膳已上架。',
      )
    } else if (typeof recipe.value.favorited === 'boolean') {
      if (recipe.value.favorited) collectStore.addCollect(recipe.value.id)
      else collectStore.removeCollect(recipe.value.id)
    }
  } finally {
    loading.value = false
  }
  if (recipe.value && !notFound.value) {
    recordLocalBrowseHistory()
  }
}

watch(id, () => {
  feedback.value = ''
  load()
}, { immediate: true })

watch(
  () => recipe.value?.id,
  (rid) => {
    imageBroken.value = false
    coverUsedFallback.value = false
    coverImgSrc.value = recipe.value?.coverUrl || ''
    if (!rid) return
    try {
      const v = localStorage.getItem(feedbackStorageKey(String(rid)))
      feedback.value = v === 'up' || v === 'down' ? v : ''
    } catch {
      feedback.value = ''
    }
  },
)
</script>

<template>
  <div class="page recipe-detail">
    <LoadingSkeleton v-if="loading" :rows="3" />

    <div v-else-if="notFound" class="recipe-detail__missing page-card">
      <van-empty image="search" description="药膳不存在或已下架">
        <van-button type="primary" round class="recipe-detail__missing-btn" @click="router.push({ name: 'Home' })">
          返回推荐首页
        </van-button>
      </van-empty>
    </div>

    <template v-else-if="recipe">
      <header class="top-nav page-card">
        <van-button
          class="top-nav__back"
          round
          plain
          hairline
          icon="arrow-left"
          type="default"
          aria-label="返回"
          @click="router.back()"
        />
        <h1 class="top-nav__title">{{ recipe.name }}</h1>
        <div class="top-nav__actions">
          <span v-if="recipe.collectCount != null" class="top-nav__count">
            {{ recipe.collectCount }} 收藏
          </span>
          <van-button
            round
            plain
            hairline
            class="top-nav__fav"
            :class="{ 'top-nav__fav--on': collected }"
            :aria-label="collected ? '取消收藏' : '收藏'"
            @click="onToggleFavorite"
          >
            <svg
              class="top-nav__heart"
              viewBox="0 0 24 24"
              width="22"
              height="22"
              aria-hidden="true"
            >
              <path
                v-if="collected"
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
          </van-button>
        </div>
      </header>

      <section class="main-columns page-card">
        <div class="col-left">
          <div class="cover">
            <img
              v-if="showCoverImg"
              :src="coverImgSrc"
              :alt="recipe.name"
              loading="lazy"
              @error="onImgError"
            >
            <div v-else class="cover__placeholder">
              <span class="cover__ph-text">{{ recipe.name?.slice(0, 1) || '膳' }}</span>
              <span class="cover__ph-hint">药膳示意图</span>
            </div>
          </div>
          <div class="meta-strip">
            <span v-if="recipe.difficulty" class="meta-pill">难度 · {{ recipe.difficulty }}</span>
            <span v-if="recipe.cookTime" class="meta-pill">时长 · {{ recipe.cookTime }}</span>
          </div>
          <div v-if="recipe.effectTags?.length" class="effect-tags">
            <span class="effect-tags__label">核心功效</span>
            <div class="effect-tags__list">
              <van-tag
                v-for="(tag, i) in recipe.effectTags"
                :key="i"
                type="success"
                plain
                size="medium"
              >
                {{ tag }}
              </van-tag>
            </div>
          </div>
        </div>

        <div class="col-right">
          <IngredientsList v-if="hasIngredients" :groups="recipe.ingredientGroups" />
          <p v-else class="missing-hint">数据暂未完善</p>

          <div v-if="hasSteps" class="steps-wrap">
            <StepsList :steps="recipe.steps || []" />
          </div>
          <p v-else class="missing-hint missing-hint--steps">数据暂未完善</p>
        </div>
      </section>

      <section class="info-block page-card">
        <h2 class="info-block__heading">基础信息</h2>
        <dl class="info-dl">
          <div class="info-dl__row">
            <dt>适用体质</dt>
            <dd>
              <template v-if="recipe.suitConstitutions?.length">
                <van-tag
                  v-for="(c, i) in recipe.suitConstitutions"
                  :key="i"
                  class="tag-spaced"
                  type="success"
                  plain
                  size="medium"
                >
                  {{ c }}
                </van-tag>
              </template>
              <span v-else>—</span>
            </dd>
          </div>
          <div class="info-dl__row">
            <dt>适用季节</dt>
            <dd>{{ seasonLine }}</dd>
          </div>
        </dl>

        <div class="taboo">
          <div class="taboo__body">
            <span class="taboo__label">⚠️ 禁忌提醒</span>
            <p class="taboo__text">
              {{ recipe.taboo }}
            </p>
          </div>
        </div>

        <p class="disclaimer">
          {{ recipe.disclaimer }}
        </p>
      </section>

      <footer class="feedback-bar page-card">
        <div class="feedback-bar__left">
          <span class="feedback-bar__q">这篇内容对你有帮助吗？</span>
          <van-button
            :type="feedback === 'up' ? 'primary' : 'default'"
            plain
            hairline
            round
            size="small"
            :disabled="!!feedback"
            @click="setFeedback('up')"
          >
            有用
          </van-button>
          <van-button
            :type="feedback === 'down' ? 'primary' : 'default'"
            plain
            hairline
            round
            size="small"
            :disabled="!!feedback"
            @click="setFeedback('down')"
          >
            没用
          </van-button>
        </div>
        <van-button type="success" round class="feedback-bar__ai" @click="goAiCustomize">
          <span class="feedback-bar__ai-inner">
            <van-icon name="guide-o" class="feedback-bar__ai-icon" />
            AI定制更适合你的方案
          </span>
        </van-button>
      </footer>
      <p class="recipe-legal-note page-card" role="note">
        {{ RECIPE_DETAIL_FOOTER_LEGAL }}
      </p>
    </template>
  </div>
</template>

<style scoped>
.recipe-detail {
  padding-bottom: var(--space-xl);
}

.recipe-detail__missing-btn {
  margin-top: var(--space-md);
}

.top-nav {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: var(--space-sm) var(--space-md);
  margin-bottom: var(--space-lg);
  position: sticky;
  top: 0;
  z-index: 20;
  box-shadow: var(--shadow-soft);
}

.top-nav__back {
  flex-shrink: 0;
  color: var(--color-text);
  width: 40px;
  min-width: 40px;
  height: 40px;
  padding: 0;
  border-color: var(--color-border);
}

.top-nav__back :deep(.van-icon) {
  font-size: 18px;
}

.top-nav__title {
  flex: 1;
  margin: 0;
  min-width: 0;
  font-size: var(--font-size-lg);
  font-weight: 600;
  line-height: 1.3;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.top-nav__actions {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 2px;
}

.top-nav__count {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.top-nav__fav {
  color: var(--color-text-secondary);
  width: 40px;
  min-width: 40px;
  height: 40px;
  padding: 0;
  border-color: var(--color-border);
}

.top-nav__fav--on {
  color: var(--color-accent);
}

.top-nav__heart {
  display: block;
}

.main-columns {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) minmax(300px, 1.15fr);
  gap: var(--space-xl);
  padding: var(--space-lg);
  margin-bottom: var(--space-lg);
  align-items: start;
}

.col-left {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.cover {
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--color-bg-elevated);
  aspect-ratio: 4 / 3;
}

.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.cover__placeholder {
  width: 100%;
  height: 100%;
  min-height: 200px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  background: linear-gradient(135deg, #ecfdf5, #e8f5e9);
  color: var(--color-primary);
}

.cover__ph-text {
  font-size: 56px;
  font-weight: 700;
  line-height: 1;
  font-family: var(--font-serif);
}

.cover__ph-hint {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
}

.meta-strip {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}

.meta-pill {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  padding: 6px 12px;
  border-radius: var(--radius-btn);
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
}

.effect-tags__label {
  display: block;
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
  margin-bottom: var(--space-sm);
  font-weight: 600;
  letter-spacing: 0.04em;
}

.effect-tags__list {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}

.col-right {
  min-width: 0;
}

.missing-hint {
  margin: 0 0 var(--space-md);
  padding: var(--space-md);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  text-align: center;
  border-radius: var(--radius-md);
  background: var(--color-bg-elevated);
  border: 1px dashed var(--color-border);
}

.missing-hint--steps {
  margin-top: var(--space-lg);
}

.steps-wrap {
  margin-top: var(--space-xl);
}

.info-block {
  padding: var(--space-lg);
  margin-bottom: var(--space-lg);
}

.info-block__heading {
  margin: 0 0 var(--space-md);
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.info-dl {
  margin: 0 0 var(--space-lg);
}

.info-dl__row {
  display: grid;
  grid-template-columns: 88px 1fr;
  gap: var(--space-md);
  padding: var(--space-sm) 0;
  border-bottom: 1px solid var(--color-border);
  font-size: var(--font-size-sm);
}

.info-dl__row:last-of-type {
  border-bottom: none;
}

.info-dl dt {
  margin: 0;
  color: var(--color-text-secondary);
  font-weight: 500;
}

.info-dl dd {
  margin: 0;
  color: var(--color-text);
}

.tag-spaced {
  margin-right: var(--space-xs);
  margin-bottom: var(--space-xs);
}

.taboo {
  padding: var(--space-md);
  border-radius: var(--radius-md);
  background: rgba(199, 92, 92, 0.08);
  border: 1px solid rgba(199, 92, 92, 0.25);
  margin-bottom: var(--space-lg);
}

.taboo__body {
  min-width: 0;
}

.taboo__label {
  display: block;
  font-weight: 700;
  font-size: var(--font-size-sm);
  color: #c0392b;
  margin-bottom: var(--space-xs);
}

.taboo__text {
  margin: 0;
  font-size: var(--font-size-sm);
  line-height: 1.65;
  color: #c0392b;
}

.disclaimer {
  margin: 0;
  font-size: var(--font-size-xs);
  line-height: 1.6;
  color: var(--color-text-muted);
}

.feedback-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: var(--space-md) var(--space-lg);
}

.feedback-bar__left {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
}

.feedback-bar__q {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-right: var(--space-sm);
}

.feedback-bar__ai {
  flex-shrink: 0;
}

.feedback-bar__ai-inner {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.feedback-bar__ai-icon {
  font-size: 18px;
}

.recipe-legal-note {
  margin: 0 0 var(--space-xl);
  padding: var(--space-md) var(--space-lg);
  font-size: var(--font-size-xs);
  line-height: 1.65;
  color: var(--color-text-muted);
  text-align: center;
  border-style: dashed;
}

@media (max-width: 900px) {
  .main-columns {
    grid-template-columns: 1fr;
  }

  .steps-wrap {
    margin-top: var(--space-lg);
  }
}
</style>
