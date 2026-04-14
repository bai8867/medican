<script setup>
import { ref, computed, watch, onMounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NoticeBar as VanNoticeBar,
  PullRefresh as VanPullRefresh,
  Popup as VanPopup,
  Empty as VanEmpty,
  Tag as VanTag,
} from 'vant'
import { fetchWeeklyCalendar } from '@/api/campusCalendar.ts'
import { buildWeeklyCalendarMockPayload } from '@/data/buildCampusCalendarMock.ts'
import { formatYmd } from '@/utils/campusWeekCalendar.js'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const MEAL_ORDER = [
  ['breakfast', '早餐'],
  ['lunch', '午餐'],
  ['dinner', '晚餐'],
  ['midnightSnack', '夜宵'],
]

const loading = ref(true)
const refreshing = ref(false)
const payload = ref(/** @type {import('@/data/buildCampusCalendarMock').WeeklyCalendarPayload | null} */ (null))
/** 接口请求的食堂 id（「全部食堂」演示为 north-1） */
const fetchCanteenId = ref('north-1')
/** 是否选中「全部食堂」标签（与北苑数据相同，仅占位） */
const canteenAllSelected = ref(true)
const selectedDateStr = ref('')
const filterFitMe = ref(false)
const sheetDish = ref(/** @type {import('@/data/buildCampusCalendarMock').CalendarDish | null} */ (null))
const bannerDismissed = ref(false)
const dateStripRef = ref(/** @type {HTMLElement | null} */ (null))

const applyFromHome = computed(() => String(route.query.apply || '') === '1')
const todayYmd = computed(() => formatYmd(new Date()))

function clampDateToPayload() {
  const days = payload.value?.days
  if (!days?.length) return
  const ids = new Set(days.map((d) => d.date))
  if (!selectedDateStr.value || !ids.has(selectedDateStr.value)) {
    selectedDateStr.value = ids.has(todayYmd.value) ? todayYmd.value : days[0].date
  }
}

async function load() {
  loading.value = true
  try {
    payload.value = await fetchWeeklyCalendar(fetchCanteenId.value)
  } catch {
    payload.value = buildWeeklyCalendarMockPayload(fetchCanteenId.value)
  } finally {
    loading.value = false
    clampDateToPayload()
  }
}

async function onRefresh() {
  refreshing.value = true
  bannerDismissed.value = false
  try {
    if (!loading.value) {
      try {
        payload.value = await fetchWeeklyCalendar(fetchCanteenId.value)
      } catch {
        payload.value = buildWeeklyCalendarMockPayload(fetchCanteenId.value)
      }
      clampDateToPayload()
    }
  } finally {
    refreshing.value = false
  }
}

onMounted(() => load())

watch(fetchCanteenId, () => load())

const canteenTabs = computed(() => {
  const list = payload.value?.canteens?.length
    ? [...payload.value.canteens]
    : [{ id: 'north-1', campusName: '', name: '第一食堂' }]
  return [{ id: '__all__', campusName: '', name: '全部食堂' }, ...list]
})

function selectCanteenTab(tab) {
  if (tab.id === '__all__') {
    canteenAllSelected.value = true
    fetchCanteenId.value = 'north-1'
    return
  }
  canteenAllSelected.value = false
  fetchCanteenId.value = tab.id
}

function isCanteenTabActive(tab) {
  if (tab.id === '__all__') return canteenAllSelected.value
  return !canteenAllSelected.value && fetchCanteenId.value === tab.id
}

const selectedDay = computed(() => {
  const days = payload.value?.days
  if (!days?.length) return null
  return days.find((d) => d.date === selectedDateStr.value) || days[0]
})

const wellnessBannerText = computed(() => {
  if (bannerDismissed.value) return ''
  return selectedDay.value?.wellnessBanner?.trim() || ''
})

function dayNum(ymd) {
  const p = String(ymd).split('-')
  return p[2] ? String(Number(p[2])) : ''
}

function scrollDateIntoView() {
  const el = dateStripRef.value
  if (!el) return
  const active = el.querySelector('[data-date-active="1"]')
  active?.scrollIntoView({ behavior: 'smooth', inline: 'center', block: 'nearest' })
}

watch(selectedDateStr, () => nextTick(() => scrollDateIntoView()))

/**
 * @param {import('@/data/buildCampusCalendarMock').CalendarDish} dish
 */
function dishFitMeta(dish) {
  const label = userStore.constitutionLabel
  const hasProfile = userStore.hasProfile
  const prefs = userStore.preferences || {}

  if (!hasProfile) {
    return { score: 5, recommended: false, mismatch: false, note: '' }
  }

  let score = 4
  let mismatch = false

  if (dish.avoidConstitutionLabels?.includes(label)) {
    score = 0
    mismatch = true
  } else if (dish.suitConstitutionLabels?.includes(label)) {
    score = 10
  }

  if (prefs.avoidSpicy && dish.tags?.includes('辛辣')) {
    score -= 4
    if (score < 2) mismatch = true
  }
  if (prefs.avoidCold && dish.tags?.includes('生冷')) {
    score -= 4
    if (score < 2) mismatch = true
  }

  const recommended = score >= 8 && !mismatch
  return {
    score,
    recommended,
    mismatch,
    note: !hasProfile ? '' : `当前档案：${label}`,
  }
}

/**
 * @param {import('@/data/buildCampusCalendarMock').CalendarDish[]} dishes
 */
function sortedDishesForMeal(dishes) {
  const list = dishes || []
  if (!filterFitMe.value) return list
  return [...list].sort((a, b) => {
    const sa = a.stopped ? 1 : 0
    const sb = b.stopped ? 1 : 0
    if (sa !== sb) return sa - sb
    return dishFitMeta(b).score - dishFitMeta(a).score
  })
}

const filterEmpty = computed(() => {
  if (!filterFitMe.value || !selectedDay.value) return false
  let any = false
  let ok = false
  for (const [key] of MEAL_ORDER) {
    const arr = selectedDay.value.meals?.[key] || []
    for (const d of arr) {
      if (d.stopped) continue
      any = true
      if (!dishFitMeta(d).mismatch) ok = true
    }
  }
  return any && !ok
})

function goRecipe(recipeId) {
  router.push({ path: `/recipe/${encodeURIComponent(String(recipeId))}` })
}

function openUnsuitable(dish, e) {
  e?.stopPropagation?.()
  sheetDish.value = dish
}

function closeSheet() {
  sheetDish.value = null
}

function onCardClick(dish) {
  if (dish.stopped) return
  goRecipe(dish.recipeId)
}

function goScenes() {
  router.push('/scenes')
}

function goRecipeFromSheet() {
  const id = sheetDish.value?.recipeId
  closeSheet()
  if (id != null) goRecipe(id)
}

function estimatedPublishLine() {
  const t = payload.value?.estimatedPublishNote?.trim()
  if (t) return t
  const mon = payload.value?.weekTitle
  if (mon) return `本周日历正在筹备中，请稍后再试。${mon}`
  return '本周日历正在筹备中，请稍后再试。'
}
</script>

<template>
  <div class="page calendar-page campus-guide">
    <div class="calendar-shell">
      <VanNoticeBar
        v-if="applyFromHome"
        left-icon="info-o"
        text="本页与「场景食疗」使用同一套本周日历数据；点击菜品可查看详情。"
        wrapable
        class="calendar-page__notice"
      />

      <template v-if="loading">
        <div class="calendar-skeleton" aria-busy="true">
          <div class="sk-row sk-row--hero" />
          <div class="sk-row sk-row--tabs" />
          <div class="sk-row sk-row--dates" />
          <div v-for="n in 3" :key="n" class="sk-card">
            <div class="sk-line sk-line--lg" />
            <div class="sk-line" />
            <div class="sk-line sk-line--sm" />
          </div>
        </div>
      </template>

      <template v-else-if="!payload?.published">
        <div class="calendar-empty-wrap">
          <VanEmpty image="search" :description="estimatedPublishLine()" />
          <button type="button" class="btn btn--secondary calendar-empty-wrap__cta" @click="goScenes">
            去看场景食疗
          </button>
        </div>
      </template>

      <template v-else>
        <VanNoticeBar
          v-if="payload?.studentDemoFallback"
          left-icon="info-o"
          text="校方尚未正式发布本周药膳排期，当前为演示数据供浏览；管理端发布后此处将自动同步真实菜单。"
          wrapable
          class="calendar-page__notice calendar-page__notice--demo"
        />
        <header class="calendar-sticky">
          <div class="calendar-page__hero">
            <div class="calendar-page__hero-text">
              <p class="hero__eyebrow">校园导览 · 本周排期</p>
              <h1 class="calendar-page__title font-heading">本周药膳日历</h1>
              <p class="calendar-page__lead">按食堂与日期查看供应；点击菜品进入详情。</p>
            </div>
            <p v-if="payload.weekTitle" class="calendar-page__week-range">{{ payload.weekTitle }}</p>
          </div>

          <div class="calendar-toolbar">
            <div class="calendar-canteen" role="tablist" aria-label="校区与食堂">
              <div class="calendar-canteen__scroll">
                <button
                  v-for="tab in canteenTabs"
                  :key="tab.id"
                  type="button"
                  role="tab"
                  class="canteen-chip"
                  :class="{ 'canteen-chip--active': isCanteenTabActive(tab) }"
                  @click="selectCanteenTab(tab)"
                >
                  {{ tab.name }}
                </button>
              </div>
            </div>
            <button
              type="button"
              class="filter-chip"
              :class="{ 'filter-chip--on': filterFitMe }"
              :aria-pressed="filterFitMe"
              @click="filterFitMe = !filterFitMe"
            >
              {{ filterFitMe ? '已筛选' : '适合我的' }}
            </button>
          </div>

          <p v-if="filterFitMe" class="calendar-filter-hint" role="status">已按当前档案与饮食偏好排序并弱化不匹配项</p>

          <div class="calendar-dates" role="tablist" aria-label="选择日期">
            <div ref="dateStripRef" class="calendar-dates__scroll">
              <button
                v-for="d in payload.days"
                :key="d.date"
                type="button"
                role="tab"
                class="date-pill"
                :class="{
                  'date-pill--today': d.date === todayYmd,
                  'date-pill--selected': d.date === selectedDateStr,
                }"
                :data-date-active="d.date === selectedDateStr ? '1' : '0'"
                @click="selectedDateStr = d.date"
              >
                <span class="date-pill__wd">{{ d.weekdayLabel }}</span>
                <span class="date-pill__num">{{ dayNum(d.date) }}</span>
                <span v-if="d.date === todayYmd" class="date-pill__dot" aria-hidden="true" />
              </button>
            </div>
          </div>

          <div v-if="wellnessBannerText" class="wellness-banner">
            <span class="wellness-banner__icon" aria-hidden="true">✦</span>
            <p class="wellness-banner__text">{{ wellnessBannerText }}</p>
            <button type="button" class="wellness-banner__close" aria-label="关闭" @click="bannerDismissed = true">
              <span class="wellness-banner__close-icon" aria-hidden="true" />
            </button>
          </div>
        </header>

        <VanPullRefresh v-model="refreshing" class="calendar-pull" @refresh="onRefresh">
          <div v-if="!selectedDay" class="calendar-scroll-body calendar-scroll-body--empty">
            <VanEmpty description="暂无本周排期" />
          </div>
          <div v-else class="calendar-scroll-body">
            <div v-if="filterEmpty" class="filter-empty">
              <VanEmpty
                description="没有符合条件的菜品，试试切换食堂或关闭筛选"
                image="error"
              />
            </div>

            <template v-else>
              <p v-if="filterFitMe" class="calendar-dim-legend">灰色卡片表示与当前档案或偏好需谨慎，仍可点开查看说明。</p>
              <section v-for="[mealKey, mealLabel] in MEAL_ORDER" :key="mealKey" class="meal-block">
                <div class="meal-block__head">
                  <h2 class="meal-block__title font-heading">{{ mealLabel }}</h2>
                  <span class="meal-block__count">共 {{ (selectedDay.meals?.[mealKey] || []).length }} 道</span>
                </div>

                <div v-if="!(selectedDay.meals?.[mealKey]?.length)" class="meal-empty">
                  <VanEmpty image="default" description="这一天该餐次暂无排期，试试换一天或换个食堂。" />
                </div>

                <div v-else class="dish-list">
                  <article
                    v-for="dish in sortedDishesForMeal(selectedDay.meals[mealKey])"
                    :key="dish.id"
                    class="dish-card ui-card"
                    :class="{
                      'ui-card--static': dish.stopped,
                      'dish-card--stopped': dish.stopped,
                      'dish-card--dim': filterFitMe && dishFitMeta(dish).mismatch && !dish.stopped,
                      'dish-card--clickable': !dish.stopped,
                    }"
                    role="button"
                    :tabindex="dish.stopped ? -1 : 0"
                    @click="onCardClick(dish)"
                    @keydown.enter.prevent="onCardClick(dish)"
                  >
                    <span v-if="filterFitMe && dishFitMeta(dish).recommended && !dish.stopped" class="dish-badge"
                      >荐</span
                    >

                    <div class="dish-card__top">
                      <h3 class="dish-card__name font-heading">{{ dish.name }}</h3>
                    </div>
                    <div v-if="dish.stopped || dish.limited" class="dish-card__tags">
                      <VanTag v-if="dish.stopped" type="danger" round class="tag-stop">今日停供</VanTag>
                      <VanTag v-else-if="dish.limited" type="danger" plain round class="tag-limit">限量供应</VanTag>
                    </div>
                    <p class="dish-card__meta">
                      <span class="dish-card__window">{{ dish.window }}</span>
                      <span class="dish-card__meta-sep" aria-hidden="true">·</span>
                      <span class="dish-card__time">{{ dish.supplyTimeLabel }}</span>
                    </p>
                    <div class="dish-card__row">
                      <p class="dish-card__price">¥{{ dish.priceYuan }}</p>
                      <button
                        v-if="!dish.stopped"
                        type="button"
                        class="dish-card__unsuit"
                        @click="openUnsuitable(dish, $event)"
                      >
                        不适合我
                      </button>
                    </div>
                  </article>
                </div>
              </section>
            </template>

            <div class="calendar-footer">
              <button type="button" class="btn btn--secondary" @click="goScenes">去看十大场景</button>
            </div>
          </div>
        </VanPullRefresh>
      </template>
    </div>

    <button
      v-if="payload?.published && !loading"
      type="button"
      class="calendar-fab"
      :class="{ 'calendar-fab--on': filterFitMe }"
      :aria-pressed="filterFitMe"
      aria-label="切换「适合我的」筛选"
      @click="filterFitMe = !filterFitMe"
    >
      <span class="calendar-fab__icon" aria-hidden="true">✓</span>
      <span class="calendar-fab__txt">{{ filterFitMe ? '已筛选' : '适合我的' }}</span>
    </button>

    <VanPopup
      :show="!!sheetDish"
      position="bottom"
      round
      closeable
      class="unsuit-popup"
      @update:show="(v) => !v && closeSheet()"
    >
      <div v-if="sheetDish" class="unsuit-popup__inner">
        <h3 class="unsuit-popup__title font-heading">{{ sheetDish.name }}</h3>
        <p class="unsuit-popup__sub">禁忌人群与注意事项</p>
        <p class="unsuit-popup__body">{{ sheetDish.contraindicationNote }}</p>
        <p v-if="sheetDish.avoidConstitutionLabels?.length" class="unsuit-popup__meta">
          <strong>不宜体质：</strong>{{ sheetDish.avoidConstitutionLabels.join('、') }}
        </p>
        <p v-if="sheetDish.suitConstitutionLabels?.length" class="unsuit-popup__meta">
          <strong>更适宜：</strong>{{ sheetDish.suitConstitutionLabels.join('、') }}
        </p>
        <div class="unsuit-popup__actions">
          <button type="button" class="btn btn--primary unsuit-popup__btn" @click="goRecipeFromSheet">
            仍查看菜谱详情
          </button>
          <button type="button" class="btn btn--secondary unsuit-popup__btn" @click="closeSheet">返回</button>
        </div>
      </div>
    </VanPopup>
  </div>
</template>

<style scoped>
.campus-guide {
  max-width: 1100px;
}

.calendar-page {
  min-height: 100%;
  padding-bottom: calc(88px + env(safe-area-inset-bottom, 0px));
}

.hero__eyebrow {
  margin: 0 0 8px;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--color-text-muted);
  font-weight: 600;
}

@media (min-width: 901px) {
  .calendar-page {
    padding-bottom: calc(24px + env(safe-area-inset-bottom, 0px));
  }
}

.calendar-shell {
  max-width: 1100px;
  margin: 0 auto;
  box-sizing: border-box;
}

@media (min-width: 901px) {
  .calendar-shell {
    padding: 0;
  }
}

.calendar-page__notice {
  margin: 8px 0 0;
}

.calendar-page__notice--demo {
  margin-bottom: 4px;
}

.calendar-sticky {
  position: sticky;
  top: 0;
  z-index: 4;
  background: var(--color-bg-main);
  padding-bottom: var(--space-xs);
  border-bottom: 1px solid var(--color-border);
  box-shadow: 0 6px 18px rgb(44 44 42 / 4%);
}

.calendar-page__hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-md);
  padding: var(--space-lg) 0 var(--space-sm);
}

.calendar-page__hero-text {
  min-width: 0;
}

.calendar-page__title {
  margin: 0 0 8px;
  font-size: clamp(1.35rem, 2.8vw, 1.75rem);
  font-weight: 800;
  line-height: 1.25;
  letter-spacing: -0.02em;
}

.calendar-page__lead {
  margin: 0;
  font-size: var(--font-size-sm);
  line-height: 1.45;
  color: var(--color-text-muted);
}

.calendar-page__week-range {
  margin: 4px 0 0;
  flex-shrink: 0;
  max-width: 42%;
  text-align: right;
  font-size: var(--font-size-xs);
  line-height: 1.4;
  color: var(--color-text-muted);
  padding: 4px 8px;
  border-radius: var(--radius-md);
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
}

@media (max-width: 520px) {
  .calendar-page__hero {
    flex-direction: column;
    align-items: stretch;
  }

  .calendar-page__week-range {
    max-width: none;
    text-align: left;
    align-self: flex-start;
  }
}

.calendar-toolbar {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 0 0 4px 0;
}

.calendar-canteen {
  flex: 1;
  min-width: 0;
  padding: 6px 0 4px;
}

.calendar-canteen__scroll {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
  scrollbar-width: none;
}

.calendar-canteen__scroll::-webkit-scrollbar {
  display: none;
}

.filter-chip {
  flex-shrink: 0;
  margin-right: 0;
  border: 2px solid color-mix(in srgb, var(--color-primary) 42%, var(--color-border-strong));
  background: #fff;
  color: var(--color-primary);
  font-size: var(--font-size-sm);
  font-weight: 700;
  padding: 10px 14px;
  border-radius: 999px;
  cursor: pointer;
  box-shadow:
    0 1px 0 rgb(255 255 255 / 80%) inset,
    0 2px 8px rgb(74 124 89 / 12%),
    0 0 0 1px rgb(255 255 255 / 60%) inset;
  transition:
    background 0.2s ease,
    color 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.15s ease;
}

.filter-chip:hover {
  border-color: color-mix(in srgb, var(--color-primary) 58%, transparent);
  box-shadow:
    0 1px 0 rgb(255 255 255 / 80%) inset,
    0 4px 14px rgb(74 124 89 / 18%),
    0 0 0 1px rgb(255 255 255 / 60%) inset;
}

.filter-chip:active {
  transform: scale(0.98);
}

.filter-chip--on {
  color: #fff;
  border-color: var(--color-primary-dark);
  background: linear-gradient(
    155deg,
    color-mix(in srgb, var(--color-primary) 88%, #fff),
    var(--color-primary)
  );
  box-shadow:
    0 2px 0 rgb(255 255 255 / 22%) inset,
    0 6px 16px color-mix(in srgb, var(--color-primary) 38%, transparent);
}

.filter-chip--on:hover {
  border-color: var(--color-primary-dark);
  box-shadow:
    0 2px 0 rgb(255 255 255 / 22%) inset,
    0 8px 20px color-mix(in srgb, var(--color-primary) 45%, transparent);
}

.calendar-filter-hint {
  margin: 0 0 6px;
  padding: 6px 10px;
  font-size: var(--font-size-xs);
  line-height: 1.4;
  color: var(--color-text-secondary);
  background: color-mix(in srgb, var(--color-primary) 8%, var(--color-bg-elevated));
  border-radius: var(--radius-sm);
  border: 1px solid color-mix(in srgb, var(--color-primary) 14%, transparent);
}

.canteen-chip {
  flex-shrink: 0;
  border: 1px solid var(--color-border);
  background: var(--color-bg-elevated);
  color: var(--color-text-secondary);
  font-size: var(--font-size-md);
  padding: 8px 14px;
  border-radius: 20px;
  cursor: pointer;
  box-shadow: var(--shadow-soft);
}

.canteen-chip--active {
  color: var(--color-primary);
  font-weight: 700;
  background: #fff;
  border-color: color-mix(in srgb, var(--color-primary) 35%, transparent);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--color-primary) 22%, transparent);
}

.calendar-dates {
  padding: 4px 0 8px;
}

.calendar-dates__scroll {
  --date-strip-gap: clamp(4px, 1.5vw, 10px);
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  column-gap: var(--date-strip-gap);
  row-gap: 0;
  align-items: stretch;
  overflow-x: hidden;
  padding: 4px 0 8px;
  box-sizing: border-box;
}

.calendar-dates__scroll::-webkit-scrollbar {
  display: none;
}

.date-pill {
  position: relative;
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
  min-height: 64px;
  border-radius: var(--radius-md);
  border: 2px solid transparent;
  background: var(--color-bg-elevated);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  box-shadow: var(--shadow-soft);
  color: var(--color-text-secondary);
  font-size: 13px;
}

.date-pill--today:not(.date-pill--selected) {
  background: color-mix(in srgb, var(--color-primary) 12%, #fff);
  color: var(--color-primary);
  font-weight: 600;
}

.date-pill--selected {
  border-color: color-mix(in srgb, var(--color-primary) 50%, transparent);
  color: var(--color-primary);
  font-weight: 700;
  background: #fff;
}

.date-pill__num {
  font-size: 17px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.date-pill--today .date-pill__num,
.date-pill--selected .date-pill__num {
  color: var(--color-primary);
}

.date-pill__dot {
  position: absolute;
  bottom: 6px;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--color-primary);
}

.date-pill--selected .date-pill__dot {
  background: var(--color-accent);
}

.wellness-banner {
  margin: 0 0 8px;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--color-primary) 10%, #fff);
  display: flex;
  align-items: flex-start;
  gap: 8px;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  border: 1px solid color-mix(in srgb, var(--color-primary) 18%, transparent);
}

.wellness-banner__icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--color-primary);
}

.wellness-banner__text {
  margin: 0;
  flex: 1;
  line-height: 1.45;
}

.wellness-banner__close {
  flex-shrink: 0;
  border: none;
  background: transparent;
  width: 32px;
  height: 32px;
  margin: -6px -6px -6px 0;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-muted);
  cursor: pointer;
  border-radius: var(--radius-sm);
}

.wellness-banner__close:hover {
  background: rgb(0 0 0 / 5%);
}

.wellness-banner__close-icon {
  position: relative;
  width: 14px;
  height: 14px;
}

.wellness-banner__close-icon::before,
.wellness-banner__close-icon::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 14px;
  height: 1.5px;
  background: currentcolor;
  border-radius: 1px;
}

.wellness-banner__close-icon::before {
  transform: translate(-50%, -50%) rotate(45deg);
}

.wellness-banner__close-icon::after {
  transform: translate(-50%, -50%) rotate(-45deg);
}

.calendar-pull {
  min-height: 120px;
}

.calendar-scroll-body {
  padding: var(--space-md) 0 var(--space-xl);
}

.calendar-dim-legend {
  margin: 0 0 var(--space-md);
  padding: 8px 12px;
  font-size: var(--font-size-xs);
  line-height: 1.45;
  color: var(--color-text-secondary);
  background: var(--color-bg-elevated);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.meal-block {
  margin-bottom: var(--space-xl);
  padding-left: 10px;
  border-left: 3px solid color-mix(in srgb, var(--color-primary) 35%, transparent);
}

.meal-block__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: var(--space-sm);
  margin-bottom: var(--space-sm);
}

.meal-block__title {
  margin: 0;
  font-size: var(--font-size-lg);
  font-weight: 600;
  letter-spacing: 0.02em;
}

.meal-block__count {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  white-space: nowrap;
}

.meal-empty {
  padding: 12px 0;
}

.meal-empty :deep(.van-empty__image) {
  width: 72px;
  height: 72px;
}

.dish-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.dish-card.ui-card {
  position: relative;
  padding: var(--space-md) var(--space-md) 14px;
  outline: none;
  background: #fff;
}

.dish-card--clickable.ui-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

.dish-card--clickable.ui-card:active {
  transform: translateY(0);
}

.dish-card--clickable {
  cursor: pointer;
}

.dish-card--stopped {
  opacity: 0.58;
  filter: grayscale(0.2);
}

.dish-card--dim {
  opacity: 0.5;
}

.dish-badge {
  position: absolute;
  top: 12px;
  right: 12px;
  background: var(--color-primary);
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  width: 24px;
  height: 24px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
}

.dish-card__top {
  padding-right: 28px;
}

.dish-card:has(.dish-badge) .dish-card__top {
  padding-right: 36px;
}

.dish-card__name {
  margin: 0 0 6px;
  font-size: var(--font-size-lg);
  font-weight: 600;
  line-height: 1.35;
}

.dish-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.tag-stop,
.tag-limit {
  font-size: 11px !important;
  padding: 2px 8px !important;
}

.dish-card__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0 6px;
  margin: 0 0 10px;
  font-size: var(--font-size-sm);
  line-height: 1.45;
  color: var(--color-text-muted);
}

.dish-card__window,
.dish-card__time {
  min-width: 0;
}

.dish-card__meta-sep {
  opacity: 0.65;
}

.dish-card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
}

.dish-card__price {
  margin: 0;
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-accent);
}

.dish-card__unsuit {
  flex-shrink: 0;
  border: 1px solid var(--color-border);
  background: var(--color-bg-elevated);
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  font-weight: 500;
  cursor: pointer;
  padding: 6px 12px;
  border-radius: var(--radius-btn);
  transition:
    background 0.2s ease,
    border-color 0.2s ease,
    color 0.2s ease;
}

.dish-card__unsuit:hover {
  border-color: color-mix(in srgb, var(--color-primary) 35%, transparent);
  color: var(--color-primary);
}

.calendar-footer {
  margin-top: var(--space-lg);
  display: flex;
  justify-content: center;
}

.calendar-empty-wrap {
  padding: 48px var(--space-md) var(--space-xl);
  text-align: center;
}

.calendar-empty-wrap__cta {
  margin-top: var(--space-md);
}

.filter-empty {
  padding: 24px 0;
}

.calendar-fab {
  position: fixed;
  right: 16px;
  bottom: calc(16px + env(safe-area-inset-bottom, 0px));
  z-index: 20;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  width: 72px;
  min-height: 72px;
  padding: 10px 8px;
  border: none;
  border-radius: 36px;
  background: linear-gradient(
    145deg,
    color-mix(in srgb, var(--color-primary) 92%, #fff),
    var(--color-primary)
  );
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  line-height: 1.2;
  cursor: pointer;
  box-shadow: 0 6px 16px color-mix(in srgb, var(--color-primary) 40%, transparent);
}

.calendar-fab--on {
  background: color-mix(in srgb, var(--color-primary) 78%, #1a4d2e);
  box-shadow: 0 4px 12px rgb(0 0 0 / 18%);
}

.calendar-fab__icon {
  font-size: 16px;
  line-height: 1;
}

@media (min-width: 901px) {
  .calendar-fab {
    display: none;
  }
}

@media (max-width: 900px) {
  .calendar-fab {
    bottom: calc(52px + 16px + env(safe-area-inset-bottom, 0px));
  }
}

.unsuit-popup__inner {
  padding: 20px 20px calc(20px + env(safe-area-inset-bottom, 0px));
  max-width: 520px;
  margin: 0 auto;
}

.unsuit-popup__title {
  margin: 0 0 6px;
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.unsuit-popup__sub {
  margin: 0 0 12px;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.unsuit-popup__body {
  margin: 0 0 14px;
  font-size: var(--font-size-md);
  line-height: 1.55;
  color: var(--color-text-primary);
}

.unsuit-popup__meta {
  margin: 0 0 8px;
  font-size: var(--font-size-sm);
  line-height: 1.45;
  color: var(--color-text-secondary);
}

.unsuit-popup__actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: var(--space-lg);
}

.unsuit-popup__btn {
  width: 100%;
}

.calendar-skeleton {
  padding: 16px;
}

.sk-row {
  border-radius: var(--radius-md);
  background: linear-gradient(90deg, #eee 25%, #f5f5f5 50%, #eee 75%);
  background-size: 200% 100%;
  animation: sk-shine 1.2s ease-in-out infinite;
  margin-bottom: 12px;
}

.sk-row--hero {
  height: 56px;
}

.sk-row--tabs {
  height: 40px;
}

.sk-row--dates {
  height: 72px;
}

.sk-card {
  border-radius: var(--radius-lg);
  background: var(--color-bg-elevated);
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: var(--shadow-card);
  border: 1px solid var(--color-border);
}

.sk-line {
  height: 12px;
  border-radius: 6px;
  background: linear-gradient(90deg, #eee 25%, #f7f7f7 50%, #eee 75%);
  background-size: 200% 100%;
  animation: sk-shine 1.2s ease-in-out infinite;
  margin-bottom: 10px;
}

.sk-line--lg {
  width: 55%;
  height: 16px;
}

.sk-line--sm {
  width: 35%;
  margin-bottom: 0;
}

@keyframes sk-shine {
  0% {
    background-position: 200% 0;
  }
  100% {
    background-position: -200% 0;
  }
}
</style>
