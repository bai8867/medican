<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { fetchScenes } from '@/api/scenes.js'
import { SCENE_THERAPY_SEED } from '@/data/sceneTherapySeed.js'
import { getUnifiedRecipeMockStore } from '@/data/unifiedRecipeMockStore.js'
import {
  fetchResolvedWeeklyCalendar,
  buildSceneWeekMatchCounts,
} from '@/utils/sceneCalendarBinding.js'

const router = useRouter()
const scenes = ref([])
const loading = ref(true)
const weatherHint = ref('')
const calendarPublished = ref(true)

const heroTitle = '今天，你的身体在经历什么？'

const timeBucket = computed(() => {
  const h = new Date().getHours()
  if (h >= 5 && h < 11) return 'morning'
  if (h >= 11 && h < 14) return 'noon'
  if (h >= 14 && h < 18) return 'afternoon'
  if (h >= 18 && h < 23) return 'evening'
  return 'night'
})

const timeLines = {
  morning: [
    '清晨的一杯温水，是唤醒肠胃的序曲。',
    '早八路上，记得给身体留一口温柔的热气。',
    '晨光里慢慢吃一口热食，比多睡五分钟更提神。',
  ],
  noon: ['午间七分饱，给下午的大脑留一点轻盈。', '再忙也留十分钟认真咀嚼，胃会谢谢你。'],
  afternoon: ['午后犯困时，先试试起身拉伸，而不是第三杯咖啡。', '三点一刻，用一小碗温润替代冰饮的突袭。'],
  evening: ['傍晚把屏幕亮度调低一点，也是在给眼睛放假。', '晚餐清淡一点，夜读与睡眠都会更听话。'],
  night: ['夜深了，把「再刷五分钟」换成「合上眼深呼吸」。', '月亮升起来时，肝血也需要回家休息。'],
}

const weatherLines = {
  rain: ['窗外雨声淅沥，温热汤水正适合被凉意包裹的你。', '雨天湿气偏重，脾胃更需要一点干爽温柔的照料。'],
  clear: ['今日阳光不错，记得补水与遮阳，别让热情晒伤耐心。'],
}

const heroSubtitle = computed(() => {
  if (weatherHint.value) return weatherHint.value
  const list = timeLines[timeBucket.value] || timeLines.morning
  const idx = new Date().getDate() % list.length
  return list[idx]
})

function goDetail(id) {
  router.push({ path: `/scene/${id}` })
}

function goCalendar() {
  router.push('/campus-calendar')
}

function weekLine(s) {
  if (!calendarPublished.value) return '本周日历待发布，联动计数暂不可用'
  return `本周有 ${s.weekMatchCount ?? 0} 道相关药膳（已对照本周校园药膳日历）`
}

async function tryWeatherLine() {
  try {
    const url =
      'https://api.open-meteo.com/v1/forecast?latitude=31.23&longitude=121.47&current_weather=true'
    const ctrl =
      typeof AbortSignal !== 'undefined' && typeof AbortSignal.timeout === 'function'
        ? AbortSignal.timeout(3200)
        : undefined
    const res = await fetch(url, ctrl ? { signal: ctrl } : undefined)
    if (!res.ok) return
    const data = await res.json()
    const code = data?.current_weather?.weathercode
    const n = Number(code)
    if (Number.isFinite(n) && n >= 51 && n <= 99) {
      const arr = weatherLines.rain
      weatherHint.value = arr[new Date().getHours() % arr.length]
    } else if (Number.isFinite(n)) {
      weatherHint.value = weatherLines.clear[0]
    }
  } catch {
    /* 无网络或非浏览器环境时忽略 */
  }
}

async function attachWeekMatchCounts(list) {
  const cal = await fetchResolvedWeeklyCalendar()
  calendarPublished.value = !!cal.published
  const store = getUnifiedRecipeMockStore().filter((r) => r.status !== 'off_shelf')
  const counts = buildSceneWeekMatchCounts(SCENE_THERAPY_SEED, cal, store)
  return list.map((s) => ({
    ...s,
    weekMatchCount: counts[s.id] ?? 0,
  }))
}

async function loadScenes() {
  loading.value = true
  try {
    const data = await fetchScenes()
    const list = data?.list
    const base = Array.isArray(list) && list.length ? list : mapSeedToApi()
    scenes.value = await attachWeekMatchCounts(base)
  } catch {
    scenes.value = await attachWeekMatchCounts(mapSeedToApi())
  } finally {
    loading.value = false
  }
}

function mapSeedToApi() {
  return SCENE_THERAPY_SEED.map((s) => ({
    id: s.id,
    name: s.name,
    icon: s.icon,
    description: s.description,
    tagline: s.tagline,
    painTags: s.painTags,
    recipeCount: 4,
    tags: s.tags,
  }))
}

onMounted(() => {
  tryWeatherLine()
  loadScenes()
})
</script>

<template>
  <div class="page campus-scenes">
    <header class="hero">
      <p class="hero__eyebrow">校园十大场景食疗</p>
      <h1 class="hero__title">{{ heroTitle }}</h1>
      <p class="hero__sub">{{ heroSubtitle }}</p>
      <button type="button" class="hero__cal-link" @click="goCalendar">查看本周校园药膳日历</button>
    </header>

    <section class="grid-wrap" aria-label="十大场景">
      <div v-if="loading" class="grid grid--skeleton">
        <div v-for="n in 10" :key="n" class="sk-card" />
      </div>
      <div v-else class="grid">
        <button
          v-for="s in scenes"
          :key="s.id"
          type="button"
          class="scene-card"
          @click="goDetail(s.id)"
        >
          <div class="scene-card__inner">
            <div class="scene-card__top">
              <span class="scene-card__icon" aria-hidden="true">{{ s.icon }}</span>
              <div class="scene-card__headtext">
                <h2 class="scene-card__name">{{ s.name }}</h2>
                <p class="scene-card__tagline">{{ s.tagline }}</p>
              </div>
            </div>
            <p class="scene-card__desc">{{ s.description }}</p>
            <div class="scene-card__tags">
              <span v-for="(t, i) in s.painTags?.slice(0, 4) || []" :key="i" class="chip">{{ t }}</span>
            </div>
            <p class="scene-card__week">{{ weekLine(s) }}</p>
            <div class="scene-card__foot">
              <span class="scene-card__cta">进入场景详情</span>
              <span v-if="s.recipeCount" class="scene-card__count">{{ s.recipeCount }}+ 道参考</span>
            </div>
          </div>
        </button>
      </div>
    </section>

    <p class="legal">
      内容仅供健康教育参考，不替代诊疗；体质与用药特殊者请咨询医师。
    </p>
  </div>
</template>

<style scoped>
.page {
  max-width: 1100px;
}

.hero {
  margin-bottom: var(--space-lg);
  padding: var(--space-lg) 0 var(--space-md);
}

.hero__eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  font-weight: 600;
}

.hero__title {
  margin: 0 0 12px;
  font-size: clamp(1.35rem, 2.8vw, 1.75rem);
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: -0.02em;
}

.hero__sub {
  margin: 0 0 12px;
  max-width: 36rem;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.65;
}

.hero__cal-link {
  margin: 0;
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-primary);
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.grid-wrap {
  margin-bottom: 24px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

@media (min-width: 700px) {
  .grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (min-width: 1000px) {
  .grid {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
}

.scene-card {
  border: none;
  padding: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font: inherit;
  color: inherit;
  min-width: 0;
}

.scene-card__inner {
  height: 100%;
  min-height: 200px;
  border-radius: 18px;
  padding: 14px 14px 12px;
  background: linear-gradient(
    155deg,
    color-mix(in srgb, var(--color-primary) 14%, var(--color-bg-elevated)),
    var(--color-bg-elevated)
  );
  border: 1px solid color-mix(in srgb, var(--color-primary) 25%, var(--color-border));
  box-shadow: 0 12px 32px color-mix(in srgb, var(--color-text-primary) 6%, transparent);
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    border-color 0.18s ease;
}

.scene-card:hover .scene-card__inner {
  transform: translateY(-3px);
  box-shadow: 0 18px 40px color-mix(in srgb, var(--color-primary) 16%, transparent);
  border-color: color-mix(in srgb, var(--color-primary) 45%, var(--color-border));
}

.scene-card__top {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 8px;
}

.scene-card__icon {
  font-size: 32px;
  line-height: 1;
}

.scene-card__name {
  margin: 0 0 4px;
  font-size: 14px;
  font-weight: 700;
  line-height: 1.3;
}

.scene-card__tagline {
  margin: 0;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-primary);
  line-height: 1.35;
}

.scene-card__desc {
  margin: 0 0 8px;
  font-size: 11px;
  color: var(--color-text-secondary);
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.scene-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
}

.chip {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-text-primary) 6%, transparent);
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
}

.scene-card__week {
  margin: 0 0 10px;
  font-size: 11px;
  line-height: 1.45;
  font-weight: 600;
  color: color-mix(in srgb, var(--color-primary) 75%, var(--color-text-secondary));
}

.scene-card__foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
}

.scene-card__cta {
  font-weight: 600;
  color: var(--color-primary);
}

.scene-card__count {
  color: var(--color-text-muted);
}

.grid--skeleton {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

@media (min-width: 700px) {
  .grid--skeleton {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (min-width: 1000px) {
  .grid--skeleton {
    grid-template-columns: repeat(5, minmax(0, 1fr));
  }
}

.sk-card {
  height: 200px;
  border-radius: 18px;
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--color-text-primary) 6%, var(--color-bg-elevated)),
    color-mix(in srgb, var(--color-text-primary) 10%, var(--color-bg-elevated))
  );
  animation: pulse 1.2s ease-in-out infinite alternate;
}

@keyframes pulse {
  to {
    opacity: 0.65;
  }
}

.legal {
  margin: 0;
  font-size: 11px;
  color: var(--color-text-muted);
  line-height: 1.5;
  max-width: 40rem;
}
</style>
