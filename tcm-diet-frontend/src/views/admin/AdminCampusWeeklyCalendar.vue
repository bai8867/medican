<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Calendar } from '@element-plus/icons-vue'
import { fetchAdminWeeklyCalendar, saveAdminWeeklyCalendar } from '@/api/adminCampusWeeklyCalendar'
import { fetchAdminRecipePage } from '@/api/adminRecipe'
import { startOfWeekMonday, formatYmd } from '@/utils/campusWeekCalendar'

const MEAL_SLOTS = [
  { key: 'breakfast', label: '早餐' },
  { key: 'lunch', label: '午餐' },
  { key: 'dinner', label: '晚餐' },
  { key: 'midnightSnack', label: '夜宵' },
]

const weekAnchor = ref(new Date())
const weekMonday = computed(() => formatYmd(startOfWeekMonday(weekAnchor.value)))

const canteens = ref([])
const canteenId = ref('north-1')
const published = ref(false)
const weekTitle = ref('')
const estimatedPublishNote = ref('')
const mealsTemplate = ref(emptyMealsTemplate())
const activeMealTab = ref('breakfast')
const loading = ref(false)
const saving = ref(false)
const exists = ref(false)

/** 药膳库选项（id + name），用于下拉；含当前周模板里已引用但库列表未返回的占位项 */
const recipeLibrary = ref([])
const recipeOptions = ref([])
const recipesLoading = ref(false)

const recipeById = computed(() => {
  const m = new Map()
  for (const o of recipeOptions.value) {
    m.set(String(o.id), o)
  }
  return m
})

function emptyMealsTemplate() {
  return {
    breakfast: [],
    lunch: [],
    dinner: [],
    midnightSnack: [],
  }
}

function emptyDishRow() {
  return {
    recipeId: '',
    name: '',
    window: '',
    priceYuan: 0,
    supplyTimeLabel: '',
    limited: false,
    stopped: false,
    stopReason: '',
    contraindicationNote: '',
    suitConstitutionLabels: [],
    avoidConstitutionLabels: [],
    tags: [],
  }
}

function splitLabels(s) {
  if (s == null || String(s).trim() === '') return []
  return String(s)
    .split(/[,，、\s]+/)
    .map((x) => x.trim())
    .filter(Boolean)
}

function normalizeDishRow(d) {
  const r = emptyDishRow()
  if (!d || typeof d !== 'object') return r
  r.recipeId = d.recipeId != null ? String(d.recipeId) : ''
  r.name = d.name != null ? String(d.name) : ''
  r.window = d.window != null ? String(d.window) : ''
  r.priceYuan = Number(d.priceYuan) || 0
  r.supplyTimeLabel = d.supplyTimeLabel != null ? String(d.supplyTimeLabel) : ''
  r.limited = !!d.limited
  r.stopped = !!d.stopped
  r.stopReason = d.stopReason != null ? String(d.stopReason) : ''
  r.contraindicationNote = d.contraindicationNote != null ? String(d.contraindicationNote) : ''
  r.suitConstitutionLabels = Array.isArray(d.suitConstitutionLabels)
    ? d.suitConstitutionLabels.map(String)
    : []
  r.avoidConstitutionLabels = Array.isArray(d.avoidConstitutionLabels)
    ? d.avoidConstitutionLabels.map(String)
    : []
  r.tags = Array.isArray(d.tags) ? d.tags.map(String) : []
  return r
}

function normalizeMealsTemplate(raw) {
  const t = emptyMealsTemplate()
  if (!raw || typeof raw !== 'object') return t
  for (const { key } of MEAL_SLOTS) {
    const arr = Array.isArray(raw[key]) ? raw[key] : []
    t[key] = arr.map(normalizeDishRow)
  }
  return t
}

/** 兼容旧接口：从整周 days[0].meals 推导模板（去掉 id） */
function mealsTemplateFromDays(days) {
  if (!Array.isArray(days) || !days.length || !days[0]?.meals) return emptyMealsTemplate()
  const m = days[0].meals
  const t = emptyMealsTemplate()
  for (const { key } of MEAL_SLOTS) {
    const arr = Array.isArray(m[key]) ? m[key] : []
    t[key] = arr.map((d) => {
      if (!d || typeof d !== 'object') return emptyDishRow()
      const { id: _id, ...rest } = d
      return normalizeDishRow(rest)
    })
  }
  return t
}

function dishRowForApi(row) {
  return {
    recipeId: row.recipeId || '',
    name: row.name || '',
    window: row.window || '',
    priceYuan: Number(row.priceYuan) || 0,
    supplyTimeLabel: row.supplyTimeLabel || '',
    limited: !!row.limited,
    stopped: !!row.stopped,
    stopReason: row.stopReason || '',
    contraindicationNote: row.contraindicationNote || '',
    suitConstitutionLabels: Array.isArray(row.suitConstitutionLabels) ? [...row.suitConstitutionLabels] : [],
    avoidConstitutionLabels: Array.isArray(row.avoidConstitutionLabels) ? [...row.avoidConstitutionLabels] : [],
    tags: Array.isArray(row.tags) ? [...row.tags] : [],
  }
}

function mealsTemplateForApi() {
  const src = mealsTemplate.value
  const out = {}
  for (const { key } of MEAL_SLOTS) {
    out[key] = (Array.isArray(src[key]) ? src[key] : []).map(dishRowForApi)
  }
  return out
}

function sortRecipeOptions(list) {
  return [...list].sort((a, b) => String(a.name).localeCompare(String(b.name), 'zh-CN'))
}

async function loadRecipeLibrary() {
  recipesLoading.value = true
  try {
    const all = []
    let page = 1
    const pageSize = 200
    while (true) {
      const data = await fetchAdminRecipePage({ page, pageSize })
      const rows = data?.records || data?.list || []
      for (const r of rows) {
        const id = r?.id != null ? String(r.id).trim() : ''
        if (!id) continue
        all.push({ id, name: String(r.name || '').trim() || `药膳 #${id}` })
      }
      const total = Number(data?.total) || 0
      if (!rows.length || all.length >= total) break
      page += 1
    }
    recipeLibrary.value = all
    rebuildRecipeOptions()
  } catch (e) {
    ElMessage.error(e?.message || '药膳库加载失败')
    recipeLibrary.value = []
    rebuildRecipeOptions()
  } finally {
    recipesLoading.value = false
  }
}

function rebuildRecipeOptions() {
  const byId = new Map(recipeLibrary.value.map((x) => [String(x.id), { ...x }]))
  for (const { key } of MEAL_SLOTS) {
    const arr = mealsTemplate.value[key] || []
    for (const row of arr) {
      const id = row.recipeId != null ? String(row.recipeId).trim() : ''
      if (!id) continue
      if (!byId.has(id)) {
        const name = row.name != null ? String(row.name).trim() : ''
        byId.set(id, {
          id,
          name: name || `药膳（ID: ${id}，库中暂无）`,
        })
      }
    }
  }
  recipeOptions.value = sortRecipeOptions(Array.from(byId.values()))
}

function onRecipeChange(row, recipeId) {
  const sid = recipeId != null && recipeId !== '' ? String(recipeId) : ''
  row.recipeId = sid
  const hit = sid ? recipeById.value.get(sid) : null
  row.name = hit?.name || ''
}

function addDish(mealKey) {
  if (!mealsTemplate.value[mealKey]) mealsTemplate.value[mealKey] = []
  mealsTemplate.value[mealKey].push(emptyDishRow())
}

function removeDish(mealKey, index) {
  const arr = mealsTemplate.value[mealKey]
  if (!Array.isArray(arr) || index < 0 || index >= arr.length) return
  arr.splice(index, 1)
}

async function load() {
  loading.value = true
  try {
    const data = await fetchAdminWeeklyCalendar({
      weekMonday: weekMonday.value,
      canteenId: canteenId.value,
    })
    canteens.value = Array.isArray(data?.canteens) ? data.canteens : []
    exists.value = !!data?.exists
    published.value = !!data?.published
    weekTitle.value = data?.weekTitle != null ? String(data.weekTitle) : ''
    estimatedPublishNote.value =
      data?.estimatedPublishNote != null ? String(data.estimatedPublishNote) : ''
    if (data?.mealsTemplate != null && typeof data.mealsTemplate === 'object') {
      mealsTemplate.value = normalizeMealsTemplate(data.mealsTemplate)
    } else {
      mealsTemplate.value = mealsTemplateFromDays(data?.days)
    }
    rebuildRecipeOptions()
    if (!canteenId.value && canteens.value.length) {
      canteenId.value = canteens.value[0].id
    }
  } catch (e) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  for (const slot of MEAL_SLOTS) {
    const arr = mealsTemplate.value[slot.key] || []
    for (let i = 0; i < arr.length; i += 1) {
      if (!String(arr[i].recipeId || '').trim()) {
        ElMessage.error(`请在「${slot.label}」第 ${i + 1} 行从药膳库选择药膳`)
        activeMealTab.value = slot.key
        return
      }
    }
  }
  saving.value = true
  try {
    await saveAdminWeeklyCalendar({
      weekMonday: weekMonday.value,
      canteenId: canteenId.value,
      published: published.value,
      weekTitle: weekTitle.value.trim() || undefined,
      estimatedPublishNote: estimatedPublishNote.value.trim() || undefined,
      mealsTemplate: mealsTemplateForApi(),
    })
    ElMessage.success('已保存（服务端已生成整周日历 JSON）')
    await load()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadRecipeLibrary()
  await load()
})
</script>

<template>
  <div class="admin-cal">
    <header class="admin-cal__head">
      <el-icon class="admin-cal__icon"><Calendar /></el-icon>
      <div>
        <h1 class="admin-cal__title">本周药膳日历</h1>
        <p class="admin-cal__sub">
          按食堂与周次维护发布状态与周标题。菜品请按餐次填写表格；保存时由服务端根据当周周一自动生成 7 天的
          <code>days_json</code>（含日期、星期与菜品 id）。
        </p>
      </div>
    </header>

    <el-card shadow="never" v-loading="loading">
      <el-form label-width="120px" class="admin-cal__form">
        <el-form-item label="周次（任一天）">
          <el-date-picker v-model="weekAnchor" type="date" placeholder="选择日期" @change="load" />
          <span class="admin-cal__hint">当周周一：{{ weekMonday }}</span>
        </el-form-item>
        <el-form-item label="食堂">
          <el-select v-model="canteenId" style="width: 280px" @change="load">
            <el-option v-for="c in canteens" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="发布">
          <el-switch v-model="published" />
          <span class="admin-cal__hint">{{ exists ? '已有记录' : '尚未建库，保存后将创建' }}</span>
        </el-form-item>
        <el-form-item label="周标题">
          <el-input v-model="weekTitle" placeholder="可选，留空则校园端按周一日期生成" clearable />
        </el-form-item>
        <el-form-item label="未发布说明">
          <el-input
            v-model="estimatedPublishNote"
            type="textarea"
            :rows="2"
            placeholder="未发布时向用户展示（可选）"
          />
        </el-form-item>

        <el-form-item label="周菜谱">
          <div class="admin-cal__meals">
            <p class="admin-cal__meals-hint">
              以下模板会应用到本周每一天；药膳须从「药膳管理」库中选择，保存时自动带上菜谱
              ID。各日菜品实例 id 与「今日停供」等规则由后端在保存时写入。
            </p>
            <el-tabs v-model="activeMealTab" type="border-card" class="admin-cal__tabs">
              <el-tab-pane
                v-for="mealSlot in MEAL_SLOTS"
                :key="mealSlot.key"
                :label="mealSlot.label"
                :name="mealSlot.key"
              >
                <div class="admin-cal__toolbar">
                  <el-button type="primary" link @click="addDish(mealSlot.key)">添加菜品</el-button>
                </div>
                <el-table
                  :data="mealsTemplate[mealSlot.key]"
                  border
                  stripe
                  size="small"
                  class="admin-cal__table"
                  empty-text="暂无菜品，点击「添加菜品」"
                  v-loading="recipesLoading"
                >
                  <el-table-column prop="recipeId" label="药膳" min-width="220">
                    <template #default="{ row }">
                      <el-select
                        :model-value="row.recipeId || undefined"
                        filterable
                        clearable
                        placeholder="从药膳库选择"
                        style="width: 100%"
                        @update:model-value="(v) => onRecipeChange(row, v)"
                      >
                        <el-option
                          v-for="opt in recipeOptions"
                          :key="opt.id"
                          :label="opt.name"
                          :value="opt.id"
                        />
                      </el-select>
                    </template>
                  </el-table-column>
                  <el-table-column prop="window" label="窗口" min-width="120">
                    <template #default="{ row }">
                      <el-input v-model="row.window" />
                    </template>
                  </el-table-column>
                  <el-table-column prop="priceYuan" label="价格(元)" width="100">
                    <template #default="{ row }">
                      <el-input-number v-model="row.priceYuan" :min="0" :max="999" controls-position="right" />
                    </template>
                  </el-table-column>
                  <el-table-column prop="supplyTimeLabel" label="供应时段" width="130">
                    <template #default="{ row }">
                      <el-input v-model="row.supplyTimeLabel" placeholder="如 11:00–13:00" />
                    </template>
                  </el-table-column>
                  <el-table-column label="限量" width="70" align="center">
                    <template #default="{ row }">
                      <el-switch v-model="row.limited" />
                    </template>
                  </el-table-column>
                  <el-table-column label="停供" width="70" align="center">
                    <template #default="{ row }">
                      <el-switch v-model="row.stopped" />
                    </template>
                  </el-table-column>
                  <el-table-column prop="stopReason" label="停供说明" min-width="120">
                    <template #default="{ row }">
                      <el-input v-model="row.stopReason" :disabled="!row.stopped" />
                    </template>
                  </el-table-column>
                  <el-table-column prop="contraindicationNote" label="禁忌说明" min-width="160">
                    <template #default="{ row }">
                      <el-input v-model="row.contraindicationNote" type="textarea" :rows="2" />
                    </template>
                  </el-table-column>
                  <el-table-column label="适宜体质" min-width="140">
                    <template #default="{ row }">
                      <el-input
                        :model-value="row.suitConstitutionLabels.join('、')"
                        placeholder="逗号或顿号分隔"
                        @update:model-value="(v) => (row.suitConstitutionLabels = splitLabels(v))"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="不宜体质" min-width="120">
                    <template #default="{ row }">
                      <el-input
                        :model-value="row.avoidConstitutionLabels.join('、')"
                        @update:model-value="(v) => (row.avoidConstitutionLabels = splitLabels(v))"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="标签" width="110">
                    <template #default="{ row }">
                      <el-input
                        :model-value="row.tags.join('、')"
                        placeholder="如 辛辣"
                        @update:model-value="(v) => (row.tags = splitLabels(v))"
                      />
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="72" fixed="right">
                    <template #default="{ $index }">
                      <el-button type="danger" link @click="removeDish(mealSlot.key, $index)">删除</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </el-tab-pane>
            </el-tabs>
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存</el-button>
          <el-button @click="load">重新加载</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.admin-cal__head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: var(--space-lg, 16px);
}
.admin-cal__icon {
  font-size: 28px;
  color: var(--color-primary, #2d6a4f);
  margin-top: 4px;
}
.admin-cal__title {
  margin: 0 0 6px;
  font-size: 20px;
}
.admin-cal__sub {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-secondary, #666);
  max-width: 820px;
  line-height: 1.5;
}
.admin-cal__hint {
  margin-left: 12px;
  font-size: 13px;
  color: var(--color-text-muted, #888);
}
.admin-cal__form {
  max-width: 1100px;
}
.admin-cal__meals {
  width: 100%;
}
.admin-cal__meals-hint {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--color-text-secondary, #666);
  line-height: 1.45;
}
.admin-cal__tabs {
  width: 100%;
}
.admin-cal__toolbar {
  margin-bottom: 8px;
}
.admin-cal__table {
  width: 100%;
}
.admin-cal__table :deep(.el-input-number) {
  width: 100%;
}
</style>
