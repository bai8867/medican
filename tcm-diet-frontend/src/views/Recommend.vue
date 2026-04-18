<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  Tag as VanTag,
  Button as VanButton,
  Empty as VanEmpty,
  Icon as VanIcon,
} from 'vant'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import RecommendFilterPanel from '@/components/recommend/RecommendFilterPanel.vue'
import RecommendFeedList from '@/components/recommend/RecommendFeedList.vue'
import { useUserStore, CONSTITUTION_TYPES } from '@/stores/user'
import { useCollectStore } from '@/stores/collect'
import { EFFECT_FILTER_OPTIONS, EFFICACY_FILTER_ALL } from '@/data/recommendMock'
import {
  CONSTITUTION_FILTER_ALL,
  loadConstitutionFilterPreference,
  loadEfficacyPreference,
  loadSortPreference,
} from '@/composables/useRecommendPreferences'
import { isEfficacyAll } from '@/composables/useRecommendFeedFilters'
import { useRecommendPage } from '@/composables/useRecommendPage'
import { useRecommendSceneRouteQuery } from '@/composables/useRecommendSceneRouteQuery'
import { useRecommendWeeklyMenu } from '@/composables/useRecommendWeeklyMenu'
import { useRecommendWeeklyOffer } from '@/composables/useRecommendWeeklyOffer'
import { useRecommendFeedReload } from '@/composables/useRecommendFeedReload'
import { useRecommendFeedVisibility } from '@/composables/useRecommendFeedVisibility'
import { useRecommendMergedStream } from '@/composables/useRecommendMergedStream'
import { useRecommendRemoteFeed } from '@/composables/useRecommendRemoteFeed'
import { useRecommendFeedScroll } from '@/composables/useRecommendFeedScroll'
import { useRecommendFeedInteractions } from '@/composables/useRecommendFeedInteractions'
import { useRecommendPageNav } from '@/composables/useRecommendPageNav'
import {
  buildRecommendCalendarAriaLabel,
  buildRecommendCalendarEntrySub,
  buildRecommendCalendarEntryTitle,
  buildRecommendEmptyListHint,
  buildRecommendHeadlineReason,
  buildRecommendReasonCardText,
  buildRecommendSceneStripAroundKeyword,
  computeRecipeSearchFallbackToKeywordOnly,
  recommendConstitutionFocusPhrase,
  recommendSeasonFromDate,
  RECOMMEND_BTN_EDIT_CONSTITUTION,
  RECOMMEND_CALENDAR_KICKER,
  RECOMMEND_CALENDAR_UNPUBLISHED_DEFAULT_NOTICE,
  RECOMMEND_CALENDAR_UNPUBLISHED_TITLE,
  RECOMMEND_COLD_START_CTA,
  RECOMMEND_CONSTITUTION_FILTER_ALL_LABEL,
  RECOMMEND_DEFAULT_CONSTITUTION_CODE,
  RECOMMEND_DEFAULT_CONSTITUTION_LABEL,
  RECOMMEND_LOAD_MORE_HINT,
  RECOMMEND_LOAD_MORE_LOADING,
  RECOMMEND_SCENE_CLEAR,
  RECOMMEND_SCENE_STRIP_NO_PROFILE_TAIL,
  RECOMMEND_TAG_CONSTITUTION_PREFIX,
  RECOMMEND_TAG_SEASON_PREFIX,
} from '@/utils/recommendPageCopy'

const route = useRoute()
const { goCalendar, goConstitution, goAi } = useRecommendPageNav()
const { sceneTagQuery, sceneLabelQuery, clearSceneQuery } = useRecommendSceneRouteQuery()
const userStore = useUserStore()
const collectStore = useCollectStore()

const recipeSearchInput = ref('')
const recipeSearchKeyword = ref('')

const effectFilter = ref(loadEfficacyPreference())
const constitutionFilter = ref(loadConstitutionFilterPreference())
const sortBy = ref(loadSortPreference())
const visibleCount = ref(12)

const seasonCtx = computed(() => recommendSeasonFromDate())

const effectiveConstitutionCode = computed(
  () => userStore.constitutionCode || RECOMMEND_DEFAULT_CONSTITUTION_CODE,
)

const effectiveConstitutionLabel = computed(() => {
  const hit = CONSTITUTION_TYPES.find((c) => c.code === effectiveConstitutionCode.value)
  return hit?.label || RECOMMEND_DEFAULT_CONSTITUTION_LABEL
})

const constitutionFilterOptions = computed(() => [
  { value: CONSTITUTION_FILTER_ALL, label: RECOMMEND_CONSTITUTION_FILTER_ALL_LABEL },
  ...CONSTITUTION_TYPES.map((c) => ({ value: c.label, label: c.label })),
])

const sceneStripAroundKeyword = computed(() =>
  buildRecommendSceneStripAroundKeyword({
    sceneLabelQuery: sceneLabelQuery.value,
  }),
)

const calendarAriaLabel = computed(() => buildRecommendCalendarAriaLabel(calendarFitCount.value))

const calendarEntryTitle = computed(() => buildRecommendCalendarEntryTitle(calendarFitCount.value))

const calendarEntrySub = computed(() => buildRecommendCalendarEntrySub(calendarWeekHint.value))

const {
  loading,
  loadingMore,
  recipePool,
  feedPage,
  hasRemoteMore,
  loadPool,
  lastFeedLoadedAt,
} = useRecommendRemoteFeed({
  userStore,
  effectiveConstitutionCode,
  seasonCtx,
  effectFilter,
  constitutionFilter,
  sortBy,
  sceneTagQuery,
  recipeSearchKeyword,
})

const { onSearchEnter, onRecipeSearchClear } = useRecommendPage({
  recipeSearchInput,
  recipeSearchKeyword,
  onSearchCommit: () => {
    feedPage.value = 1
    visibleCount.value = 12
    loadPool()
  },
})

const {
  poolFiltered,
  initDismissedFromStorage,
  syncServerFavoritesToCollectStore,
  onPatchCollect,
  onNotInterested,
  onOpenRecipeDetail,
} = useRecommendFeedInteractions({ recipePool, collectStore })

const { weeklyMenu, refreshWeeklyMenu, calendarPublished, calendarWeekHint } =
  useRecommendWeeklyMenu()

const { weekOfferRecipes, poolFilteredForGeneral, calendarFitCount } = useRecommendWeeklyOffer({
  userStore,
  poolFiltered,
  recipeSearchKeyword,
  effectFilter,
  constitutionFilter,
  calendarPublished,
  weeklyMenu,
  sceneTagQuery,
  seasonCtx,
  effectiveConstitutionLabel,
})

const { mergedStream } = useRecommendMergedStream({
  userStore,
  poolFilteredForGeneral,
  recipeSearchKeyword,
  effectFilter,
  constitutionFilter,
  sortBy,
  seasonCtx,
  effectiveConstitutionLabel,
})

const { sentinelEl, setupObserver } = useRecommendFeedScroll({
  mergedStream,
  loading,
  loadingMore,
  hasRemoteMore,
  loadPool,
  visibleCount,
})

const constitutionFocusPhrase = computed(() =>
  recommendConstitutionFocusPhrase(effectiveConstitutionCode.value),
)

const visibleSlice = computed(() => mergedStream.value.slice(0, visibleCount.value))

const hasMoreLocal = computed(() => visibleCount.value < mergedStream.value.length)

const hasMore = computed(() => hasMoreLocal.value || hasRemoteMore.value)

const recipeSearchFallbackToKeywordOnly = computed(() =>
  computeRecipeSearchFallbackToKeywordOnly({
    keywordTrimmed: recipeSearchKeyword.value.trim(),
    poolRows: poolFilteredForGeneral.value,
    allergyTags: userStore.preferences?.allergyTags || [],
    effectFilterValue: effectFilter.value,
    constitutionFilterValue: constitutionFilter.value,
  }),
)

const emptyListHint = computed(() =>
  buildRecommendEmptyListHint(recipeSearchKeyword.value),
)

const reasonCardText = computed(() =>
  buildRecommendReasonCardText({
    constitutionLabel: effectiveConstitutionLabel.value,
    seasonLabel: seasonCtx.value.label,
    focusPhrase: constitutionFocusPhrase.value,
    personalizedRecommendEnabled: userStore.personalizedRecommendEnabled,
    hasProfile: userStore.hasProfile,
    efficacyIsAll: isEfficacyAll(effectFilter.value),
  }),
)

const headlineReason = computed(() =>
  buildRecommendHeadlineReason({
    constitutionLabel: effectiveConstitutionLabel.value,
    seasonLabel: seasonCtx.value.label,
    focusPhrase: constitutionFocusPhrase.value,
    personalizedRecommendEnabled: userStore.personalizedRecommendEnabled,
    hasProfile: userStore.hasProfile,
    efficacyIsAll: isEfficacyAll(effectFilter.value),
  }),
)

useRecommendFeedVisibility({
  route,
  loading,
  loadingMore,
  lastFeedLoadedAt,
  feedPage,
  visibleCount,
  loadPool,
})

useRecommendFeedReload({
  userStore,
  effectFilter,
  constitutionFilter,
  sortBy,
  sceneTagQuery,
  feedPage,
  visibleCount,
  loadPool,
})

onMounted(() => {
  initDismissedFromStorage()
  syncServerFavoritesToCollectStore()
  refreshWeeklyMenu()
  loadPool().then(() => setupObserver())
})
</script>

<template>
  <div class="page recommend">
    <div class="portrait-bar page-card">
      <div class="portrait-bar__main">
        <van-tag type="success" round>{{ RECOMMEND_TAG_CONSTITUTION_PREFIX }} {{ effectiveConstitutionLabel }}</van-tag>
        <van-tag type="primary" plain round>{{ RECOMMEND_TAG_SEASON_PREFIX }} {{ seasonCtx.label }}</van-tag>
      </div>
      <van-button plain hairline round type="primary" size="small" class="portrait-bar__edit" @click="goConstitution">
        {{ RECOMMEND_BTN_EDIT_CONSTITUTION }}
      </van-button>
    </div>

    <div v-if="sceneTagQuery" class="scene-strip page-card" role="status">
      <van-icon name="info-o" class="scene-strip__icon" />
      <div class="scene-strip__body">
        <p class="scene-strip__text">
          {{ sceneStripAroundKeyword.beforeKeyword }}<strong>{{ sceneTagQuery }}</strong>{{ sceneStripAroundKeyword.afterKeyword }}
          <template v-if="!userStore.hasProfile">{{ RECOMMEND_SCENE_STRIP_NO_PROFILE_TAIL }}</template>
        </p>
        <van-button type="primary" size="small" plain hairline round @click="clearSceneQuery">
          {{ RECOMMEND_SCENE_CLEAR }}
        </van-button>
      </div>
    </div>

    <div v-if="!userStore.hasProfile" class="cold-start page-card" role="link" @click="goConstitution">
      <span>{{ RECOMMEND_COLD_START_CTA }}</span>
      <van-icon name="arrow" class="cold-start__icon" />
    </div>

    <div class="reason-calendar-row page-card">
      <section class="reason-calendar-row__reason" aria-labelledby="recommend-reason-lead">
        <p id="recommend-reason-lead" class="reason-card__lead">{{ headlineReason }}</p>
        <p class="reason-card__detail">{{ reasonCardText }}</p>
      </section>
      <button
        type="button"
        class="calendar-entry"
        :aria-label="calendarAriaLabel"
        @click="goCalendar"
      >
        <div class="calendar-entry__art" aria-hidden="true" />
        <div class="calendar-entry__text">
          <span class="calendar-entry__kicker">{{ RECOMMEND_CALENDAR_KICKER }}</span>
          <span class="calendar-entry__title">{{ calendarEntryTitle }}</span>
          <span class="calendar-entry__sub">{{ calendarEntrySub }}</span>
        </div>
        <span class="calendar-entry__chev" aria-hidden="true">›</span>
      </button>
    </div>

    <div v-if="!calendarPublished" class="calendar-unpublished page-card" role="alert">
      <van-icon name="warning-o" class="calendar-unpublished__icon" />
      <div class="calendar-unpublished__body">
        <p class="calendar-unpublished__title">{{ RECOMMEND_CALENDAR_UNPUBLISHED_TITLE }}</p>
        <p class="calendar-unpublished__desc">
          {{ weeklyMenu.notice || RECOMMEND_CALENDAR_UNPUBLISHED_DEFAULT_NOTICE }}
        </p>
      </div>
    </div>

    <RecommendFilterPanel
      v-model:effect-filter="effectFilter"
      v-model:constitution-filter="constitutionFilter"
      v-model:sort-by="sortBy"
      v-model:recipe-search-input="recipeSearchInput"
      :effect-filter-options="EFFECT_FILTER_OPTIONS"
      :constitution-filter-options="constitutionFilterOptions"
      :efficacy-all-value="EFFICACY_FILTER_ALL"
      :constitution-all-value="CONSTITUTION_FILTER_ALL"
      :recipe-search-fallback-to-keyword-only="recipeSearchFallbackToKeywordOnly"
      :recipe-search-keyword="recipeSearchKeyword"
      @search-enter="onSearchEnter"
      @search-clear="onRecipeSearchClear"
    />

    <LoadingSkeleton v-if="loading" :rows="6" />

    <template v-else>
      <RecommendFeedList
        :calendar-published="calendarPublished"
        :week-offer-recipes="weekOfferRecipes"
        :visible-slice="visibleSlice"
        @patch-collect="onPatchCollect"
        @not-interested="onNotInterested"
        @open-recipe-detail="onOpenRecipeDetail"
        @go-ai="goAi"
      />

      <div ref="sentinelEl" class="sentinel" aria-hidden="true" />

      <p v-if="hasMore" class="load-hint">
        <template v-if="loadingMore">{{ RECOMMEND_LOAD_MORE_LOADING }}</template>
        <template v-else>{{ RECOMMEND_LOAD_MORE_HINT }}</template>
      </p>
      <van-empty
        v-if="!visibleSlice.length && !(calendarPublished && weekOfferRecipes.length)"
        image="search"
        :description="emptyListHint"
      />
    </template>
  </div>
</template>

<style scoped>
.recommend {
  max-width: 1120px;
  margin: 0 auto;
}

.page-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.portrait-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: var(--space-md) var(--space-lg);
  margin-bottom: var(--space-md);
}

.portrait-bar__main {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  align-items: center;
}

.portrait-bar__edit {
  flex-shrink: 0;
  font-weight: 600;
}

.portrait-bar__main :deep(.van-tag) {
  margin-right: 0;
}

.scene-strip {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
  padding: var(--space-md) var(--space-lg);
  margin-bottom: var(--space-md);
  border: 1px solid color-mix(in srgb, var(--color-primary) 22%, var(--color-border));
  background: color-mix(in srgb, var(--color-primary) 6%, var(--color-bg-surface));
}

.scene-strip__icon {
  flex-shrink: 0;
  margin-top: 2px;
  font-size: 18px;
  color: var(--color-primary);
}

.scene-strip__body {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-md);
  flex-wrap: wrap;
}

.scene-strip__text {
  margin: 0;
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-sm);
  line-height: 1.55;
  color: var(--color-text-secondary);
}

.cold-start {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-lg);
  margin-bottom: var(--space-md);
  cursor: pointer;
  color: var(--color-primary-dark);
  background: linear-gradient(90deg, #ecfdf5, #f9f7f4);
  border-color: #c5e6d0;
  transition: box-shadow 0.15s ease, border-color 0.15s ease;
}

.cold-start:hover {
  border-color: var(--color-border-hover-primary);
  box-shadow: var(--shadow-card-hover-float);
}

.cold-start__icon {
  flex-shrink: 0;
}

.reason-calendar-row {
  display: flex;
  align-items: stretch;
  gap: var(--space-xl);
  padding: var(--space-lg);
  margin-bottom: var(--space-lg);
}

.reason-calendar-row__reason {
  flex: 1 1 0;
  min-width: 0;
  padding-right: var(--space-md);
  border-right: 1px solid var(--color-border);
}

.reason-calendar-row .calendar-entry {
  flex: 0 1 340px;
  max-width: 380px;
  width: auto;
  min-width: 0;
  margin-bottom: 0;
  align-self: stretch;
  border-radius: var(--radius-md);
}

@media (max-width: 768px) {
  .reason-calendar-row {
    flex-direction: column;
    align-items: stretch;
    gap: var(--space-md);
  }

  .reason-calendar-row__reason {
    padding-right: 0;
    border-right: none;
    padding-bottom: var(--space-md);
    border-bottom: 1px solid var(--color-border);
  }

  .reason-calendar-row .calendar-entry {
    flex: 1 1 auto;
    max-width: none;
    width: 100%;
    align-self: stretch;
  }
}

.reason-card__lead {
  margin: 0 0 var(--space-sm);
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--color-text-primary);
}

.reason-card__detail {
  margin: 0;
  font-size: var(--font-size-sm);
  line-height: 1.65;
  color: var(--color-text-secondary);
}

/* 筛选栏、推荐流 masonry / AI 卡样式已迁至 RecommendFilterPanel、RecommendFeedList */

.sentinel {
  height: 1px;
  margin-top: var(--space-lg);
}

.load-hint {
  text-align: center;
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
  margin: var(--space-md) 0 var(--space-xl);
}

.calendar-entry {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  width: 100%;
  margin-bottom: var(--space-md);
  padding: var(--space-md) var(--space-lg);
  text-align: left;
  cursor: pointer;
  border: 1px solid color-mix(in srgb, var(--color-primary) 28%, transparent);
  background: linear-gradient(110deg, #ecfdf5 0%, #d1fae5 42%, #f0fdf4 100%);
  transition: box-shadow 0.15s ease, transform 0.15s ease, border-color 0.15s ease;
}

.calendar-entry:hover {
  border-color: var(--color-border-hover-primary);
  box-shadow: var(--shadow-card-hover-float);
  transform: translateY(-1px);
}

.calendar-entry__art {
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  border-radius: 14px;
  background:
    radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.95), transparent 55%),
    linear-gradient(145deg, #34d399, #059669);
  opacity: 0.95;
}

.calendar-entry__text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.calendar-entry__kicker {
  font-size: var(--font-size-xs);
  letter-spacing: 0.08em;
  color: var(--color-text-muted);
  text-transform: uppercase;
}

.calendar-entry__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-text-primary);
}

.calendar-entry__sub {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  line-height: 1.45;
}

.calendar-entry__chev {
  flex-shrink: 0;
  font-size: 28px;
  font-weight: 300;
  color: var(--color-primary);
  line-height: 1;
}

.calendar-unpublished {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
  padding: var(--space-md) var(--space-lg);
  margin-bottom: var(--space-md);
  border: 1px solid color-mix(in srgb, #f59e0b 35%, var(--color-border));
  background: color-mix(in srgb, #f59e0b 10%, var(--color-bg-surface));
}

.calendar-unpublished__icon {
  flex-shrink: 0;
  margin-top: 2px;
  font-size: 20px;
  color: #d97706;
}

.calendar-unpublished__body {
  flex: 1;
  min-width: 0;
}

.calendar-unpublished__title {
  margin: 0 0 var(--space-xs);
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-text-primary);
}

.calendar-unpublished__desc {
  margin: 0;
  font-size: var(--font-size-sm);
  line-height: 1.55;
  color: var(--color-text-secondary);
}
</style>
