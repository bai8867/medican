<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Delete, Right } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore, CONSTITUTION_TYPES } from '@/stores/user'
import { useAiPlanStore } from '@/stores/aiPlan'
import { useCollectStore } from '@/stores/collect'
import {
  fetchProfileFavorites,
  normalizeFavoriteRecipes,
  normalizeFavoriteAiPlans,
  deleteRecipeFavorites,
  deleteAiPlanFavorites,
} from '@/api/profile'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const aiLocalStore = useAiPlanStore()
const collectStore = useCollectStore()

const activeTab = ref('favorites')
const loadingFavorites = ref(false)
const recipeFavorites = ref([])
const aiPlanFavorites = ref([])

/** 浏览历史仅本机缓存（Pinia 持久化），与后端无关 */
const historyItems = computed(() =>
  collectStore.browseHistory.map((h) => ({
    historyId: `local:${h.recipeId}`,
    type: 'recipe',
    targetId: h.recipeId,
    name: h.name || '药膳',
    coverUrl: h.coverUrl || '',
    subtitle: h.subtitle || '',
    viewedAt: typeof h.viewedAt === 'number' && Number.isFinite(h.viewedAt) ? h.viewedAt : 0,
  })),
)

const manageFavorites = ref(false)
const selectedRecipeFavoriteIds = ref([])
const selectedAiFavoriteIds = ref([])

const constitutionName = computed(() => {
  const hit = CONSTITUTION_TYPES.find((c) => c.code === userStore.constitutionCode)
  return hit?.label || '未设置'
})

const hasAnyFavorite = computed(
  () => recipeFavorites.value.length > 0 || aiPlanFavorites.value.length > 0,
)

const favoritesEmpty = computed(() => !loadingFavorites.value && !hasAnyFavorite.value)

const historyEmpty = computed(() => historyItems.value.length === 0)

function ymd(d) {
  const x = d instanceof Date ? d : new Date(d)
  if (Number.isNaN(x.getTime())) return ''
  const y = x.getFullYear()
  const m = String(x.getMonth() + 1).padStart(2, '0')
  const day = String(x.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const todayYmd = ymd(new Date())
const yesterdayDate = new Date()
yesterdayDate.setDate(yesterdayDate.getDate() - 1)
const yesterdayYmd = ymd(yesterdayDate)

function historyBucket(viewedAt) {
  const s = ymd(viewedAt)
  if (s && s === todayYmd) return 'today'
  if (s && s === yesterdayYmd) return 'yesterday'
  return 'earlier'
}

const historyGroups = computed(() => {
  const buckets = { today: [], yesterday: [], earlier: [] }
  for (const h of historyItems.value) {
    buckets[historyBucket(h.viewedAt)].push(h)
  }
  return [
    { key: 'today', label: '今天', items: buckets.today },
    { key: 'yesterday', label: '昨天', items: buckets.yesterday },
    { key: 'earlier', label: '更早', items: buckets.earlier },
  ].filter((g) => g.items.length)
})

function favoritedAtTime(row) {
  const t = new Date(row.favoritedAt || row.savedAt || 0).getTime()
  return Number.isFinite(t) ? t : 0
}

function localAiFavoriteRows() {
  return aiLocalStore.savedPlans.map((row) => {
    const firstRecipe = row.snapshot?.recipes?.[0]?.recipeName
    return {
      favoriteId: `local:${row.planId}`,
      planId: row.planId,
      name: (row.symptomSummary || 'AI 食疗方案').slice(0, 36),
      coverUrl: '',
      subtitle: firstRecipe ? `含「${firstRecipe}」等推荐方` : '本机收藏的 AI 方案',
      favoritedAt: row.savedAt,
      localOnly: true,
    }
  })
}

async function loadFavorites() {
  loadingFavorites.value = true
  try {
    const data = await fetchProfileFavorites()
    recipeFavorites.value = normalizeFavoriteRecipes(data)
    for (const row of recipeFavorites.value) {
      if (row.recipeId) collectStore.addCollect(row.recipeId)
    }
    const remoteAi = normalizeFavoriteAiPlans(data)
    aiPlanFavorites.value = [...localAiFavoriteRows(), ...remoteAi].sort(
      (a, b) => favoritedAtTime(b) - favoritedAtTime(a),
    )
  } catch {
    recipeFavorites.value = []
    aiPlanFavorites.value = localAiFavoriteRows().sort(
      (a, b) => favoritedAtTime(b) - favoritedAtTime(a),
    )
  } finally {
    loadingFavorites.value = false
  }
}

function syncTabFromRoute() {
  const t = route.query.tab
  if (t === 'history' || t === 'favorites') activeTab.value = t
}

onMounted(() => {
  syncTabFromRoute()
  loadFavorites()
})

watch(
  () => route.query.tab,
  () => {
    syncTabFromRoute()
    if (activeTab.value === 'favorites') loadFavorites()
  },
)

watch(activeTab, (t) => {
  if (t === 'favorites') loadFavorites()
})

watch(manageFavorites, (on) => {
  if (!on) {
    selectedRecipeFavoriteIds.value = []
    selectedAiFavoriteIds.value = []
  }
})

function isRecipeFavoriteSelected(id) {
  return selectedRecipeFavoriteIds.value.includes(String(id))
}

function isAiFavoriteSelected(id) {
  return selectedAiFavoriteIds.value.includes(String(id))
}

function onRecipeFavoriteCheck(id, checked) {
  const sid = String(id)
  if (checked) {
    if (!selectedRecipeFavoriteIds.value.includes(sid)) {
      selectedRecipeFavoriteIds.value = [...selectedRecipeFavoriteIds.value, sid]
    }
  } else {
    selectedRecipeFavoriteIds.value = selectedRecipeFavoriteIds.value.filter((x) => x !== sid)
  }
}

function onAiFavoriteCheck(id, checked) {
  const sid = String(id)
  if (checked) {
    if (!selectedAiFavoriteIds.value.includes(sid)) {
      selectedAiFavoriteIds.value = [...selectedAiFavoriteIds.value, sid]
    }
  } else {
    selectedAiFavoriteIds.value = selectedAiFavoriteIds.value.filter((x) => x !== sid)
  }
}

function selectAllFavorites() {
  selectedRecipeFavoriteIds.value = recipeFavorites.value.map((x) => x.favoriteId)
  selectedAiFavoriteIds.value = aiPlanFavorites.value.map((x) => x.favoriteId)
}

function clearFavoriteSelection() {
  selectedRecipeFavoriteIds.value = []
  selectedAiFavoriteIds.value = []
}

const batchSelectionCount = computed(
  () => selectedRecipeFavoriteIds.value.length + selectedAiFavoriteIds.value.length,
)

function openRecipeDetail(recipeId) {
  router.push({
    name: 'RecipeDetail',
    params: { id: String(recipeId) },
  })
}

function openAiPlanDetail(planId) {
  router.push({
    name: 'AIPlanDetail',
    params: { id: String(planId) },
  })
}

function openHistoryItem(row) {
  if (row.type === 'ai_plan') openAiPlanDetail(row.targetId)
  else openRecipeDetail(row.targetId)
}

async function onUnfavoriteRecipe(item) {
  try {
    await deleteRecipeFavorites([item.favoriteId])
    recipeFavorites.value = recipeFavorites.value.filter((x) => x.favoriteId !== item.favoriteId)
    if (item.recipeId) collectStore.removeCollect(item.recipeId)
    ElMessage.success('已取消收藏')
  } catch {
    ElMessage.error('取消收藏失败，请稍后重试')
  }
}

async function onUnfavoriteAi(item) {
  if (item.localOnly) {
    aiLocalStore.removePlan(item.planId)
    aiPlanFavorites.value = aiPlanFavorites.value.filter((x) => x.favoriteId !== item.favoriteId)
    ElMessage.success('已取消收藏')
    return
  }
  try {
    await deleteAiPlanFavorites([item.favoriteId])
    aiPlanFavorites.value = aiPlanFavorites.value.filter((x) => x.favoriteId !== item.favoriteId)
    ElMessage.success('已取消收藏')
  } catch {
    ElMessage.error('取消收藏失败，请稍后重试')
  }
}

async function onBatchUnfavorite() {
  const n = batchSelectionCount.value
  if (!n) return
  try {
    await ElMessageBox.confirm(`确定取消已选中的 ${n} 条收藏？`, '批量取消收藏', {
      type: 'warning',
      confirmButtonText: '取消收藏',
      cancelButtonText: '关闭',
    })
    const rIds = [...selectedRecipeFavoriteIds.value]
    const aIds = [...selectedAiFavoriteIds.value]
    const localAiFavIds = aIds.filter((id) => String(id).startsWith('local:'))
    const remoteAiFavIds = aIds.filter((id) => !String(id).startsWith('local:'))
    for (const fid of localAiFavIds) {
      const pid = String(fid).slice('local:'.length)
      aiLocalStore.removePlan(pid)
    }
    if (rIds.length) await deleteRecipeFavorites(rIds)
    if (remoteAiFavIds.length) await deleteAiPlanFavorites(remoteAiFavIds)
    for (const fid of rIds) {
      const row = recipeFavorites.value.find((x) => String(x.favoriteId) === String(fid))
      if (row?.recipeId) collectStore.removeCollect(row.recipeId)
    }
    clearFavoriteSelection()
    manageFavorites.value = false
    await loadFavorites()
    ElMessage.success('已取消收藏')
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('操作失败，请稍后重试')
      await loadFavorites()
    }
  }
}

function onDeleteHistoryRow(row) {
  collectStore.removeHistoryByRecipeId(row.targetId)
  ElMessage.success('已删除')
}

async function onClearAllHistory() {
  try {
    await ElMessageBox.confirm('确定清空本设备上的全部浏览历史？', '提示', {
      type: 'warning',
      confirmButtonText: '清空',
      cancelButtonText: '取消',
    })
    collectStore.clearHistory()
    ElMessage.success('已清空浏览历史')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

function goRecommend() {
  router.push({ name: 'Home' })
}

function goPreference() {
  router.push({ path: '/settings/preference' })
}
</script>

<template>
  <div class="page">
    <h1 class="page-title">我的</h1>
    <p class="page-subtitle">体质档案与收藏来自服务端；浏览历史仅保存在本设备，换浏览器或清理站点数据后会丢失。</p>

    <div class="page-card profile-head">
      <div>
        <div class="profile-head__label">当前体质</div>
        <div class="profile-head__value">{{ constitutionName }}</div>
      </div>
      <div class="profile-head__actions">
        <el-button type="primary" @click="router.push({ name: 'Constitution' })">
          编辑体质
        </el-button>
        <el-button @click="goPreference">饮食偏好</el-button>
        <el-button @click="goRecommend">去首页</el-button>
      </div>
    </div>

    <div class="page-card profile-pref" role="group" aria-label="推荐偏好">
      <div class="profile-pref__text">
        <div class="profile-pref__title">个性化推荐开关</div>
        <p class="profile-pref__desc">
          开启后首页推荐会结合您的体质与应季食材匹配；关闭后仅展示应季通用推荐。
        </p>
      </div>
      <el-switch
        :model-value="userStore.personalizedRecommendEnabled"
        inline-prompt
        active-text="开"
        inactive-text="关"
        @update:model-value="(v) => userStore.$patch({ personalizedRecommendEnabled: Boolean(v) })"
      />
    </div>

    <div class="page-card tabs-wrap">
      <el-tabs v-model="activeTab" class="profile-tabs">
        <el-tab-pane label="收藏列表" name="favorites">
          <div class="tab-toolbar">
            <div class="tab-toolbar__left">
              <el-button
                v-if="hasAnyFavorite"
                :type="manageFavorites ? 'primary' : 'default'"
                plain
                @click="manageFavorites = !manageFavorites"
              >
                {{ manageFavorites ? '完成' : '批量取消收藏' }}
              </el-button>
              <template v-if="manageFavorites && hasAnyFavorite">
                <el-button text @click="selectAllFavorites">全选</el-button>
                <el-button text @click="clearFavoriteSelection">清空选择</el-button>
              </template>
            </div>
            <el-button
              v-if="manageFavorites && batchSelectionCount"
              type="danger"
              plain
              @click="onBatchUnfavorite"
            >
              取消选中收藏（{{ batchSelectionCount }}）
            </el-button>
          </div>

          <el-skeleton v-if="loadingFavorites" animated :rows="6" />

          <template v-else-if="!favoritesEmpty">
            <section class="fav-group">
              <h2 class="fav-group__title">药膳收藏</h2>
              <ul v-if="recipeFavorites.length" class="fav-list">
                <li
                  v-for="item in recipeFavorites"
                  :key="item.favoriteId"
                  class="fav-row"
                  @click="!manageFavorites && openRecipeDetail(item.recipeId)"
                >
                  <el-checkbox
                    v-if="manageFavorites"
                    class="fav-row__check"
                    :model-value="isRecipeFavoriteSelected(item.favoriteId)"
                    @click.stop
                    @update:model-value="(v) => onRecipeFavoriteCheck(item.favoriteId, v)"
                  />
                  <div class="fav-row__cover">
                    <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.name" loading="lazy" />
                    <div v-else class="fav-row__placeholder">{{ item.name?.slice(0, 1) }}</div>
                  </div>
                  <div class="fav-row__body">
                    <div class="fav-row__name">{{ item.name }}</div>
                    <div v-if="item.subtitle" class="fav-row__sub">{{ item.subtitle }}</div>
                  </div>
                  <div class="fav-row__actions" @click.stop>
                    <el-icon v-if="!manageFavorites" class="fav-row__chev"><Right /></el-icon>
                    <el-popconfirm
                      v-if="!manageFavorites"
                      title="确定取消收藏该药膳？"
                      confirm-button-text="取消收藏"
                      cancel-button-text="关闭"
                      @confirm="onUnfavoriteRecipe(item)"
                    >
                      <template #reference>
                        <el-button text type="danger" size="small">取消收藏</el-button>
                      </template>
                    </el-popconfirm>
                  </div>
                </li>
              </ul>
              <p v-else class="fav-group__empty">暂无药膳收藏</p>
            </section>

            <section class="fav-group">
              <h2 class="fav-group__title">AI方案收藏</h2>
              <ul v-if="aiPlanFavorites.length" class="fav-list">
                <li
                  v-for="item in aiPlanFavorites"
                  :key="item.favoriteId"
                  class="fav-row"
                  @click="!manageFavorites && openAiPlanDetail(item.planId)"
                >
                  <el-checkbox
                    v-if="manageFavorites"
                    class="fav-row__check"
                    :model-value="isAiFavoriteSelected(item.favoriteId)"
                    @click.stop
                    @update:model-value="(v) => onAiFavoriteCheck(item.favoriteId, v)"
                  />
                  <div class="fav-row__cover">
                    <img v-if="item.coverUrl" :src="item.coverUrl" :alt="item.name" loading="lazy" />
                    <div v-else class="fav-row__placeholder fav-row__placeholder--ai">AI</div>
                  </div>
                  <div class="fav-row__body">
                    <div class="fav-row__name">{{ item.name }}</div>
                    <div v-if="item.subtitle" class="fav-row__sub">{{ item.subtitle }}</div>
                  </div>
                  <div class="fav-row__actions" @click.stop>
                    <el-icon v-if="!manageFavorites" class="fav-row__chev"><Right /></el-icon>
                    <el-popconfirm
                      v-if="!manageFavorites"
                      title="确定取消收藏该方案？"
                      confirm-button-text="取消收藏"
                      cancel-button-text="关闭"
                      @confirm="onUnfavoriteAi(item)"
                    >
                      <template #reference>
                        <el-button text type="danger" size="small">取消收藏</el-button>
                      </template>
                    </el-popconfirm>
                  </div>
                </li>
              </ul>
              <p v-else class="fav-group__empty">暂无 AI 方案收藏</p>
            </section>
          </template>

          <el-empty v-else description="暂无收藏内容，快去收藏喜欢的药膳和方案吧">
            <router-link class="favorites-empty-link" to="/home">去推荐页</router-link>
          </el-empty>
        </el-tab-pane>

        <el-tab-pane label="浏览历史" name="history">
          <div class="tab-toolbar tab-toolbar--history">
            <span />
            <el-button
              v-if="historyItems.length"
              text
              type="danger"
              :icon="Delete"
              @click="onClearAllHistory"
            >
              清空历史
            </el-button>
          </div>

          <template v-if="!historyEmpty">
            <section v-for="grp in historyGroups" :key="grp.key" class="hist-block">
              <h2 class="hist-block__title">{{ grp.label }}</h2>
              <ul class="fav-list">
                <li
                  v-for="row in grp.items"
                  :key="row.historyId"
                  class="fav-row fav-row--history"
                  @click="openHistoryItem(row)"
                >
                  <div class="fav-row__cover">
                    <img v-if="row.coverUrl" :src="row.coverUrl" :alt="row.name" loading="lazy" />
                    <div v-else class="fav-row__placeholder">
                      {{ row.type === 'ai_plan' ? 'AI' : row.name?.slice(0, 1) }}
                    </div>
                  </div>
                  <div class="fav-row__body">
                    <div class="fav-row__name">
                      {{ row.name }}
                      <el-tag v-if="row.type === 'ai_plan'" size="small" type="info" effect="plain">
                        AI 方案
                      </el-tag>
                      <el-tag v-else size="small" type="success" effect="plain">药膳</el-tag>
                    </div>
                    <div v-if="row.subtitle" class="fav-row__sub">{{ row.subtitle }}</div>
                  </div>
                  <div class="fav-row__actions" @click.stop>
                    <el-icon class="fav-row__chev"><Right /></el-icon>
                    <el-popconfirm
                      title="删除这条浏览记录？"
                      confirm-button-text="删除"
                      cancel-button-text="关闭"
                      @confirm="onDeleteHistoryRow(row)"
                    >
                      <template #reference>
                        <el-button text type="danger" size="small" :icon="Delete">删除</el-button>
                      </template>
                    </el-popconfirm>
                  </div>
                </li>
              </ul>
            </section>
          </template>

          <el-empty v-else description="暂无浏览记录" />
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<style scoped>
.profile-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  margin-bottom: var(--space-lg);
}

.profile-head__label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.profile-head__value {
  font-size: var(--font-size-xl);
  font-weight: 600;
  margin-top: var(--space-xs);
}

.profile-head__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}

.profile-pref {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: var(--space-md) var(--space-lg);
  margin-bottom: var(--space-lg);
}

.profile-pref__text {
  min-width: 0;
}

.profile-pref__title {
  font-weight: 600;
  font-size: var(--font-size-md);
  margin-bottom: 4px;
}

.profile-pref__desc {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.55;
}

.tabs-wrap {
  padding-top: var(--space-sm);
}

.profile-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--space-md);
}

.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  flex-wrap: wrap;
  margin-bottom: var(--space-md);
}

.tab-toolbar__left {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
}

.tab-toolbar--history {
  min-height: 32px;
}

.fav-group {
  margin-bottom: var(--space-xl);
}

.fav-group__title {
  margin: 0 0 var(--space-md);
  font-size: var(--font-size-lg);
  font-weight: 600;
}

.fav-group__empty {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
}

.fav-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.fav-row {
  display: flex;
  align-items: stretch;
  gap: var(--space-md);
  padding: var(--space-md);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  background: var(--color-bg-surface);
  cursor: pointer;
  transition:
    box-shadow 0.15s ease,
    transform 0.15s ease,
    border-color 0.15s ease;
}

.fav-row:hover {
  border-color: var(--color-border-hover-primary);
  box-shadow: var(--shadow-card-hover-float);
  transform: translateY(-1px);
}

.fav-row--history {
  cursor: pointer;
}

.fav-row__check {
  align-self: center;
}

.fav-row__cover {
  width: 88px;
  height: 66px;
  flex-shrink: 0;
  border-radius: var(--radius-md);
  overflow: hidden;
  background: var(--color-bg-elevated);
}

.fav-row__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.fav-row__placeholder {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  font-weight: 700;
  font-size: 20px;
  color: var(--color-primary-light);
  background: linear-gradient(135deg, #ecfdf5, #d8f3dc);
}

.fav-row__placeholder--ai {
  background: linear-gradient(135deg, #ecfdf5, #bbf7d0);
  color: var(--color-primary-dark);
}

.fav-row__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  justify-content: center;
}

.fav-row__name {
  font-weight: 600;
  font-size: var(--font-size-md);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.fav-row__sub {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.fav-row__actions {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: center;
  gap: 4px;
  flex-shrink: 0;
}

.fav-row__chev {
  color: var(--color-text-muted);
}

.hist-block {
  margin-bottom: var(--space-xl);
}

.hist-block__title {
  margin: 0 0 var(--space-md);
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-text-secondary);
}

.favorites-empty-link {
  display: inline-block;
  margin-top: var(--space-sm);
  font-size: var(--font-size-md);
  font-weight: 600;
  color: var(--color-primary, #16a34a);
  text-decoration: none;
}

.favorites-empty-link:hover {
  text-decoration: underline;
}
</style>
