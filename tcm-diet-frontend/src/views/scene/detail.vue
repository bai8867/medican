<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Tab, Tabs, Button as VanButton, Loading as VanLoading, Empty as VanEmpty } from 'vant'
import SceneStoryCard from '@/components/scene/SceneStoryCard.vue'
import { fetchSceneSolution } from '@/api/scenes'
import { getSceneSeed, pickSceneRecipes } from '@/data/sceneTherapySeed'
import { getUnifiedRecipeMockStore } from '@/data/unifiedRecipeMockStore'
import { useUserStore } from '@/stores/user'
import { compareSceneRecipeRows } from '@/utils/campusMealPreferences'
import {
  fetchResolvedWeeklyCalendar,
  buildSceneCalendarPresentation,
  resolveRecipeDetailNavId,
} from '@/utils/sceneCalendarBinding'
import { getWeeklyGoalForScene, getLifestyleLinesForScene } from '@/data/sceneCalendarCopy'
import { recipeSchematicCoverUrl } from '@/utils/recipeCoverPlaceholder'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const error = ref('')
const solution = ref(null)
const activeTab = ref('week')
const calendar = ref({
  published: false,
  weekId: '',
  weekTitle: '',
  mondayYmd: '',
  sundayYmd: '',
  slots: [],
})

async function refreshWeekCalendar() {
  calendar.value = await fetchResolvedWeeklyCalendar()
}

onMounted(() => {
  refreshWeekCalendar()
})

const sceneId = computed(() => Number(route.params.id) || 0)

watch(sceneId, () => {
  refreshWeekCalendar()
})

const timeBucket = computed(() => {
  const h = new Date().getHours()
  if (h >= 5 && h < 11) return 'morning'
  if (h >= 11 && h < 14) return 'noon'
  if (h >= 14 && h < 18) return 'afternoon'
  if (h >= 18 && h < 23) return 'evening'
  return 'night'
})

const sceneSeed = computed(() => getSceneSeed(sceneId.value))

const recipeStore = computed(() =>
  getUnifiedRecipeMockStore().filter((r) => r.status !== 'off_shelf'),
)

const presentation = computed(() => {
  const seed = sceneSeed.value
  if (!seed) return null
  return buildSceneCalendarPresentation(
    seed,
    calendar.value,
    recipeStore.value,
    timeBucket.value,
  )
})

const weeklyGoalText = computed(() => getWeeklyGoalForScene(sceneId.value))

const lifestyleLines = computed(() => {
  const seed = sceneSeed.value
  const extra = getLifestyleLinesForScene(sceneId.value)
  const tips = (seed?.teas || []).filter((t) => t.type === 'tip').map((t) => t.body)
  return [...extra, ...tips]
})

function buildFallbackSolution(id) {
  const seed = getSceneSeed(id)
  if (!seed) return null
  const pool = getUnifiedRecipeMockStore().filter((r) => r.status !== 'off_shelf')
  const picked = pickSceneRecipes(seed, pool).slice(0, 3)
  const recipes = picked.map(({ r, matched }) => ({
    id: String(r.id),
    name: r.name,
    coverUrl: recipeSchematicCoverUrl(r.name),
    collectCount: Number(r.collectCount) || 0,
    efficacySummary: r.summary || r.effect || '',
    whyFit:
      `你的困扰涉及「${(matched?.length ? matched : seed.painTags.slice(0, 2)).join('、')}」。与「${(r.effectTags || []).slice(0, 4).join('、') || r.effect || '调养'}」方向相近（离线示例）。`,
    matchedPainTags: matched?.length ? matched : null,
  }))
  return {
    scene: {
      id: seed.id,
      name: seed.name,
      icon: seed.icon,
      description: seed.description,
      tagline: seed.tagline,
      painTags: seed.painTags,
      recipeCount: recipes.length,
      tags: seed.tags,
    },
    recipes,
    teas: seed.teas,
    ingredientInsight: seed.ingredientInsight,
    forbidden: seed.forbidden,
  }
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const data = await fetchSceneSolution(sceneId.value)
    solution.value = data
  } catch (e) {
    solution.value = buildFallbackSolution(sceneId.value)
    if (!solution.value) {
      error.value = e?.message || '加载失败'
    }
  } finally {
    loading.value = false
  }
}

watch(
  () => route.params.id,
  () => load(),
  { immediate: true },
)

const headerScene = computed(() => solution.value?.scene || null)

function enrichRecipeRow(row) {
  const full = getUnifiedRecipeMockStore().find((r) => String(r.id) === String(row.id))
  const name = row.name || full?.name || '药膳'
  const base = full ? { ...row } : { ...row }
  return {
    ...base,
    name,
    coverUrl: recipeSchematicCoverUrl(name),
    ingredients: full?.ingredients ?? row.ingredients,
    summary: full?.summary ?? row.summary,
    effect: full?.effect ?? row.effect,
    collectCount: full?.collectCount ?? row.collectCount,
  }
}

const sortedSceneRecipes = computed(() => {
  const list = solution.value?.recipes || []
  const prefs = userStore.preferences || {}
  return [...list]
    .map((r) => enrichRecipeRow(r))
    .sort((a, b) => compareSceneRecipeRows(a, b, prefs))
})

function openRecipe(id) {
  router.push({ path: `/recipe/${encodeURIComponent(resolveRecipeDetailNavId(id))}` })
}

function goCampusCalendar() {
  router.push({ path: '/calendar', query: { from: 'scene', apply: '1' } })
}

function goGeneralWellness() {
  const name = headerScene.value?.name || sceneSeed.value?.name || '校园场景'
  router.push({
    path: '/home',
    query: { scene_tag: encodeURIComponent(name) },
  })
}

function goAiTailor() {
  const s = headerScene.value
  if (!s) return
  const desc = (s.description || '').trim()
  const pains = (s.painTags || []).filter(Boolean).join('、')
  const tags = (s.tags || []).filter(Boolean).join('、')
  const lines = [`【校园场景：${s.name}】`]
  if (desc) {
    lines.push(`我目前的身体不适与场景描述相近：${desc}。`)
  }
  if (pains) {
    lines.push(desc ? `还可概括为：${pains}。` : `我目前的不适主要有：${pains}。`)
  }
  if (tags) {
    lines.push(`希望结合场景调养方向（${tags}），得到更精准的药膳搭配与日常作息建议。`)
  } else {
    lines.push('希望得到更精准的药膳搭配与日常作息建议。')
  }
  const text = lines.join('')
  router.push({
    path: '/ai',
    query: { scene_context: text },
  })
}
</script>

<template>
  <div class="scene-detail page">
    <div v-if="loading" class="state state--center">
      <VanLoading type="spinner" color="var(--color-primary)" />
      <p class="state__text">正在为你匹配方案…</p>
    </div>
    <template v-else-if="headerScene && sceneSeed">
      <header class="head-block">
        <SceneStoryCard :scene="headerScene" compact />
      </header>

      <!-- 日历未发布 -->
      <section v-if="!calendar.published" class="section section--unpublished">
        <VanEmpty description="本周校园药膳日历尚未发布，推荐菜品暂时无法与具体餐段对齐。" />
        <p class="unpub-hint">
          你仍可先查看<strong>通用养生推荐</strong>，或浏览本场景的茶饮解读与禁忌提醒。
        </p>
        <VanButton type="primary" round block class="btn-primary" @click="goGeneralWellness">
          查看通用养生建议
        </VanButton>
      </section>

      <template v-else>
        <section class="section section--goal">
          <h3 class="section__title">本周场景目标</h3>
          <p class="goal-text">{{ weeklyGoalText }}</p>
        </section>

        <section class="section">
          <h3 class="section__title">今日优先推荐</h3>
          <p v-if="presentation?.todayFallbackNote" class="hint hint--amber">
            {{ presentation.todayFallbackNote }}
          </p>
          <div class="hero-recipes">
            <button
              v-for="r in presentation?.todayPrimary || []"
              :key="r.id"
              type="button"
              class="recipe-hero"
              @click="openRecipe(r.id)"
            >
              <div
                class="recipe-hero__cover"
                :style="r.coverUrl ? { backgroundImage: `url(${r.coverUrl})` } : undefined"
              />
              <div class="recipe-hero__body">
                <p class="recipe-hero__slot">{{ r.slotLabel }} · 本周日历</p>
                <p v-if="r.calendarLine" class="recipe-hero__cal">{{ r.calendarLine }}</p>
                <p v-if="r.stopped" class="recipe-hero__stop">本餐段暂停供应：{{ r.stopReason || '详见日历说明' }}</p>
                <h4 class="recipe-hero__title">{{ r.name }}</h4>
                <p class="recipe-hero__sum">{{ r.efficacySummary }}</p>
                <p v-if="r.whyFit" class="recipe-hero__why">{{ r.whyFit }}</p>
              </div>
            </button>
            <p v-if="!(presentation?.todayPrimary || []).length" class="muted">
              本周日历中暂无可解析的今日菜品，请稍后在日历页确认排期是否已更新。
            </p>
          </div>
        </section>

        <section class="section">
          <h3 class="section__title">本周组合路径</h3>
          <p class="hint">以下餐段均来自本周已发布日历，按时间顺序串联，便于你提前安排。</p>
          <div class="timeline" role="list">
            <div
              v-for="(step, i) in presentation?.weekPath || []"
              :key="`${step.dateStr}-${step.mealLabel}-${i}`"
              class="timeline__item"
              role="listitem"
            >
              <div class="timeline__rail" aria-hidden="true">
                <span class="timeline__dot" />
                <span v-if="i < (presentation?.weekPath?.length || 0) - 1" class="timeline__line" />
              </div>
              <div class="timeline__content">
                <div class="timeline__meta">
                  <span class="timeline__date">{{ step.dateStr }}</span>
                  <span class="timeline__meal">{{ step.dowLabel }} {{ step.mealLabel }}</span>
                </div>
                <p v-if="step.calendarLine" class="timeline__window">{{ step.calendarLine }}</p>
                <button type="button" class="timeline__dish" @click="openRecipe(step.id)">
                  {{ step.name }}
                </button>
              </div>
            </div>
          </div>
        </section>

        <section class="section section--avoid">
          <h3 class="section__title section__title--avoid">
            <span class="section__warn-ico" aria-hidden="true">⚠️</span>
            本周尽量少选
          </h3>
          <p class="hint">从本周日历中挑选相对不那么贴合本场景目标的选项，语气柔和仅供参考。</p>
          <ul class="avoid-list">
            <li v-for="(a, i) in presentation?.avoidList || []" :key="i">
              <button type="button" class="avoid-list__name" @click="openRecipe(a.id)">
                {{ a.name }}
              </button>
              <span class="avoid-list__reason">{{ a.reason }}</span>
            </li>
          </ul>
        </section>

        <section class="section section--life" aria-labelledby="life-advice-title">
          <h3 id="life-advice-title" class="section__title">生活配合建议</h3>
          <p class="life-badge">生活建议</p>
          <blockquote class="life-quote">
            <p v-for="(line, i) in lifestyleLines" :key="i" class="life-line">{{ line }}</p>
          </blockquote>
          <p class="life-footnote">以上内容为日常起居配合提示，不与具体餐段或菜品窗口绑定。</p>
        </section>

        <section v-if="presentation?.aiNarrative" class="section section--ai">
          <p class="ai-text">{{ presentation.aiNarrative }}</p>
        </section>
      </template>

      <section class="section">
        <Tabs v-model:active="activeTab" class="scene-tabs" swipeable line-width="28px">
          <Tab title="茶饮 / 小方" name="teas">
            <div class="tab-panel">
              <div v-for="(t, i) in solution.teas" :key="i" class="tea-card" :data-type="t.type">
                <div class="tea-card__badge">{{ t.type === 'tip' ? '小妙招' : '茶饮' }}</div>
                <h4 class="tea-card__title">{{ t.title }}</h4>
                <p class="tea-card__body">{{ t.body }}</p>
              </div>
              <p v-if="!solution.teas?.length" class="muted">暂无茶饮方数据</p>
            </div>
          </Tab>
          <Tab title="参考药膳" name="ref">
            <div class="tab-panel">
              <p class="hint">以下为场景经典匹配（药膳库），不等同于本周日历排期。</p>
              <div class="recipe-cards">
                <button
                  v-for="r in sortedSceneRecipes"
                  :key="r.id"
                  type="button"
                  class="recipe-block"
                  @click="openRecipe(r.id)"
                >
                  <div
                    class="recipe-block__cover"
                    :style="r.coverUrl ? { backgroundImage: `url(${r.coverUrl})` } : undefined"
                  />
                  <div class="recipe-block__body">
                    <h4 class="recipe-block__title">{{ r.name }}</h4>
                    <p class="recipe-block__sum">{{ r.efficacySummary }}</p>
                    <p v-if="r.whyFit" class="recipe-block__why">{{ r.whyFit }}</p>
                  </div>
                </button>
              </div>
            </div>
          </Tab>
        </Tabs>
      </section>

      <section v-if="solution.ingredientInsight" class="section section--prose">
        <h3 class="section__title">核心食材解读</h3>
        <p class="prose">{{ solution.ingredientInsight }}</p>
      </section>

      <section v-if="solution.forbidden?.length" class="section">
        <h3 class="section__title warn">禁忌提醒</h3>
        <ul class="forbid-list">
          <li v-for="(f, i) in solution.forbidden" :key="i">{{ f }}</li>
        </ul>
      </section>

      <footer class="footer-spacer" />
    </template>
    <div v-else class="state">
      <p>{{ error || '场景不存在' }}</p>
      <VanButton type="primary" round block @click="router.push('/scenes')">返回场景页</VanButton>
    </div>

    <div v-if="headerScene" class="bottom-bar">
      <VanButton round block type="primary" class="bottom-bar__primary" @click="goCampusCalendar">
        去日历看看
      </VanButton>
      <VanButton round block plain type="primary" @click="goAiTailor">
        方案不够精准？让 AI 为你量身定制
      </VanButton>
    </div>
  </div>
</template>

<style scoped>
.page {
  max-width: 720px;
  padding-bottom: calc(120px + env(safe-area-inset-bottom, 0px));
}

.state {
  padding: var(--space-xl);
  color: var(--color-text-secondary);
}
.state--center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  gap: 12px;
}
.state__text {
  margin: 0;
}

.head-block {
  margin-bottom: var(--space-lg);
}

.section {
  margin-bottom: var(--space-lg);
}

.section__title {
  margin: 0 0 12px;
  font-size: var(--font-size-md);
  font-weight: 700;
}
.section__title.warn {
  color: #b45309;
}

.section__title--avoid {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #9a3412;
}

.section__warn-ico {
  font-size: 18px;
}

.section--goal .goal-text {
  margin: 0;
  padding: 14px 16px;
  border-radius: 14px;
  font-size: var(--font-size-sm);
  line-height: 1.65;
  color: var(--color-text-primary);
  background: linear-gradient(
    120deg,
    color-mix(in srgb, var(--color-primary) 14%, var(--color-bg-elevated)),
    var(--color-bg-elevated)
  );
  border: 1px solid color-mix(in srgb, var(--color-primary) 28%, var(--color-border));
}

.section--unpublished {
  padding: 8px 0 16px;
}

.unpub-hint {
  margin: 0 0 16px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-secondary);
  text-align: center;
}

.btn-primary {
  max-width: 360px;
  margin: 0 auto;
}

.hint {
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.hint--amber {
  color: #92400e;
  background: color-mix(in srgb, #fbbf24 12%, transparent);
  padding: 8px 10px;
  border-radius: 10px;
}

.hero-recipes {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.recipe-hero {
  display: flex;
  gap: 16px;
  text-align: left;
  width: 100%;
  padding: 16px;
  border-radius: 18px;
  border: 1px solid color-mix(in srgb, var(--color-primary) 35%, var(--color-border));
  background: linear-gradient(
    145deg,
    color-mix(in srgb, var(--color-primary) 10%, var(--color-bg-elevated)),
    var(--color-bg-elevated)
  );
  cursor: pointer;
  font: inherit;
  color: inherit;
  box-shadow: 0 10px 28px color-mix(in srgb, var(--color-primary) 12%, transparent);
  transition:
    transform 0.15s ease,
    border-color 0.15s ease,
    box-shadow 0.15s ease;
}
.recipe-hero:hover {
  transform: translateY(-2px);
  border-color: var(--color-border-hover-primary);
  box-shadow: var(--shadow-card-hover-float);
}

.recipe-hero__cover {
  width: 112px;
  min-height: 120px;
  flex-shrink: 0;
  border-radius: 14px;
  background: color-mix(in srgb, var(--color-primary) 12%, var(--color-bg));
  background-size: cover;
  background-position: center;
}

.recipe-hero__slot {
  margin: 0 0 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-primary);
  letter-spacing: 0.02em;
}

.recipe-hero__cal {
  margin: 0 0 6px;
  font-size: 11px;
  color: var(--color-text-muted);
  line-height: 1.4;
}

.recipe-hero__stop {
  margin: 0 0 8px;
  font-size: 11px;
  line-height: 1.45;
  color: #b45309;
  padding: 6px 8px;
  border-radius: 8px;
  background: color-mix(in srgb, #fbbf24 18%, transparent);
}

.recipe-hero__title {
  margin: 0 0 8px;
  font-size: 1.05rem;
  font-weight: 700;
}

.recipe-hero__sum {
  margin: 0 0 8px;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.45;
}

.recipe-hero__why {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-primary);
  padding: 8px 10px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--color-primary) 7%, transparent);
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.timeline__item {
  display: flex;
  gap: 12px;
  align-items: stretch;
}

.timeline__rail {
  width: 22px;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 4px;
}

.timeline__dot {
  width: 12px;
  height: 12px;
  border-radius: 50%;
  background: var(--color-primary);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--color-primary) 22%, transparent);
  flex-shrink: 0;
}

.timeline__line {
  flex: 1;
  width: 2px;
  margin-top: 4px;
  min-height: 12px;
  background: color-mix(in srgb, var(--color-primary) 35%, var(--color-border));
  border-radius: 99px;
}

.timeline__content {
  flex: 1;
  padding-bottom: 16px;
  min-width: 0;
}

.timeline__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: baseline;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.timeline__window {
  margin: 0 0 6px;
  font-size: 11px;
  color: var(--color-text-muted);
  line-height: 1.4;
}

.timeline__meal {
  font-weight: 600;
  color: var(--color-text-secondary);
}

.timeline__dish {
  display: block;
  width: 100%;
  text-align: left;
  padding: 10px 12px;
  border-radius: 12px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-elevated);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-primary);
  cursor: pointer;
  transition: border-color 0.15s ease;
}
.timeline__dish:hover {
  border-color: color-mix(in srgb, var(--color-primary) 45%, var(--color-border));
}

.section--avoid {
  padding: 14px 14px 12px;
  border-radius: 16px;
  background: color-mix(in srgb, #fecaca 22%, var(--color-bg-elevated));
  border: 1px solid color-mix(in srgb, #f87171 28%, var(--color-border));
}

.avoid-list {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.avoid-list__name {
  display: inline;
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  font-weight: 700;
  color: #b91c1c;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.avoid-list__reason {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.55;
  color: var(--color-text-secondary);
}

.section--life {
  padding: 14px 14px 12px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--color-text-primary) 4%, var(--color-bg-elevated));
  border: 1px dashed var(--color-border);
}

.life-badge {
  display: inline-block;
  margin: 0 0 10px;
  padding: 2px 10px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--color-text-muted);
  border-radius: 999px;
  border: 1px solid var(--color-border);
  background: var(--color-bg);
}

.life-quote {
  margin: 0;
  padding: 0 0 0 12px;
  border-left: 3px solid color-mix(in srgb, var(--color-text-muted) 55%, transparent);
}

.life-line {
  margin: 0 0 8px;
  font-size: var(--font-size-sm);
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.life-line:last-child {
  margin-bottom: 0;
}

.life-footnote {
  margin: 12px 0 0;
  font-size: 11px;
  color: var(--color-text-muted);
  line-height: 1.5;
}

.section--ai .ai-text {
  margin: 0;
  font-size: 12px;
  line-height: 1.65;
  color: var(--color-text-muted);
}

.scene-tabs :deep(.van-tabs__wrap) {
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid var(--color-border);
  margin-bottom: 12px;
}

.tab-panel {
  padding-top: 4px;
}

.recipe-cards {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.recipe-block {
  display: flex;
  gap: 14px;
  text-align: left;
  width: 100%;
  padding: 12px;
  border-radius: 14px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-elevated);
  cursor: pointer;
  font: inherit;
  color: inherit;
  transition:
    border-color 0.15s ease,
    transform 0.15s ease;
}
.recipe-block:hover {
  border-color: color-mix(in srgb, var(--color-primary) 40%, var(--color-border));
  transform: translateY(-1px);
}

.recipe-block__cover {
  width: 96px;
  height: 96px;
  flex-shrink: 0;
  border-radius: 12px;
  background: color-mix(in srgb, var(--color-primary) 10%, var(--color-bg));
  background-size: cover;
  background-position: center;
}

.recipe-block__title {
  margin: 0 0 6px;
  font-size: var(--font-size-md);
}
.recipe-block__sum {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.45;
}
.recipe-block__why {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-primary);
  padding: 8px 10px;
  border-radius: 8px;
  background: color-mix(in srgb, var(--color-primary) 6%, transparent);
}

.tea-card {
  position: relative;
  padding: 14px 14px 14px 16px;
  margin-bottom: 10px;
  border-radius: 14px;
  border: 1px solid var(--color-border);
  background: var(--color-bg-elevated);
}
.tea-card[data-type='tip'] {
  border-left: 4px solid var(--color-primary-light);
}
.tea-card[data-type='tea'] {
  border-left: 4px solid var(--color-primary);
}
.tea-card__badge {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-muted);
  margin-bottom: 6px;
}
.tea-card__title {
  margin: 0 0 8px;
  font-size: var(--font-size-sm);
}
.tea-card__body {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.55;
}

.section--prose .prose {
  margin: 0;
  font-size: var(--font-size-sm);
  line-height: 1.65;
  color: var(--color-text-secondary);
}

.forbid-list {
  margin: 0;
  padding-left: 1.1rem;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.muted {
  color: var(--color-text-muted);
  font-size: 13px;
}

.footer-spacer {
  height: 8px;
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 12px 16px 16px;
  padding-bottom: calc(12px + env(safe-area-inset-bottom, 0px));
  background: linear-gradient(transparent, var(--color-bg) 40%);
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
  pointer-events: none;
}
.bottom-bar :deep(.van-button) {
  pointer-events: auto;
  max-width: 400px;
  width: 100%;
}
.bottom-bar__primary {
  box-shadow: 0 8px 22px color-mix(in srgb, var(--color-primary) 25%, transparent);
}
</style>
