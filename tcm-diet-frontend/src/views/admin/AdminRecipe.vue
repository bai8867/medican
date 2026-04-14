<script setup>
import { ref, reactive, onMounted, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  fetchAdminRecipePage,
  fetchAdminRecipeDetail,
  createAdminRecipe,
  updateAdminRecipe,
  deleteAdminRecipe,
  batchDeleteAdminRecipes,
  uploadAdminRecipeCoverDataUrl,
  ADMIN_EFFECT_TAG_OPTIONS,
  ADMIN_CONSTITUTION_OPTIONS,
  ADMIN_SEASON_FORM_OPTIONS,
  ADMIN_SEASON_FILTER_OPTIONS,
  ADMIN_RECIPE_STATUS,
} from '@/api/adminRecipe.js'
import { DEFAULT_TABOO } from '@/api/recipe.js'
import { fetchIngredientList } from '@/api/ingredient.js'

const BUCKET_DEFS = [
  { key: 'main', label: '主料' },
  { key: 'aux', label: '辅料' },
  { key: 'seasoning', label: '调料' },
]

function emptyIngRow() {
  return { name: '', amount: '' }
}

const loading = ref(false)
const tableData = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)
const tableRef = ref(null)
const selectedRows = ref([])

const query = reactive({
  keyword: '',
  constitution: '',
  effectTag: '',
  season: '',
})

const dialogVisible = ref(false)
const dialogTitle = ref('新增药膳')
const editingId = ref(null)
const formRef = ref(null)
const coverUploading = ref(false)
const statusBusyId = ref(null)

const form = reactive({
  name: '',
  taboo: '',
  summary: '',
  instructionRows: [{ text: '' }],
  collectCount: 0,
  season: 'all',
  effectTags: [],
  suitConstitutions: [],
  status: ADMIN_RECIPE_STATUS.ON,
  coverUrl: '',
  ingredientBuckets: {
    main: [emptyIngRow()],
    aux: [emptyIngRow()],
    seasoning: [emptyIngRow()],
  },
  stepRows: [{ description: '' }],
})

const ingredientLibrary = ref([])

async function loadIngredientLibrary() {
  try {
    const data = await fetchIngredientList({ enabled: true })
    ingredientLibrary.value = data?.records || data?.list || []
  } catch {
    ingredientLibrary.value = []
  }
}

function ingredientOptionLabel(ing) {
  return `${ing.name}：${ing.efficacySummary || '—'}`
}

const mergedIngredientOptions = computed(() => {
  const byName = new Map(
    ingredientLibrary.value.map((i) => [String(i.name || '').trim(), i]),
  )
  for (const def of BUCKET_DEFS) {
    for (const r of form.ingredientBuckets[def.key]) {
      const n = String(r.name || '').trim()
      if (!n || byName.has(n)) continue
      byName.set(n, {
        id: `legacy-${n}`,
        name: n,
        efficacySummary: '原方用语，可在食材库补建同名条目',
        enabled: true,
      })
    }
  }
  return [...byName.values()]
})

watch(dialogVisible, (v) => {
  if (v) loadIngredientLibrary()
})

const rules = {
  name: [{ required: true, message: '请输入药膳名称', trigger: 'blur' }],
  taboo: [
    { required: true, message: '请填写禁忌提醒', trigger: ['blur', 'change'] },
    { min: 4, message: '禁忌提醒请至少填写 4 个字', trigger: ['blur', 'change'] },
  ],
}

function seasonLabel(code) {
  return ADMIN_SEASON_FORM_OPTIONS.find((o) => o.value === code)?.label || code || '—'
}

function statusLabel(s) {
  return s === ADMIN_RECIPE_STATUS.OFF ? '下架' : '上架'
}

function detailToIngredientBuckets(recipe) {
  const groups = recipe.ingredientGroups || []
  const buckets = {
    main: [],
    aux: [],
    seasoning: [],
  }
  for (const g of groups) {
    const key = ['main', 'aux', 'seasoning'].includes(g.key) ? g.key : 'main'
    for (const it of g.items || []) {
      buckets[key].push({
        name: it.name || '',
        amount: String(it.amount || ''),
      })
    }
  }
  for (const def of BUCKET_DEFS) {
    if (!buckets[def.key].length) buckets[def.key] = [emptyIngRow()]
  }
  return buckets
}

function detailToStepRows(recipe) {
  const steps = recipe.steps || []
  if (!steps.length) return [{ description: '' }]
  return steps.map((s) => ({
    description: String(s.description ?? s.text ?? '').trim(),
  }))
}

function detailToInstructionRows(recipe) {
  const raw = String(recipe.instructionSummary || '').trim()
  if (!raw) return [{ text: '' }]
  const parts = raw
    .split(/\n+/)
    .map((x) => x.trim())
    .filter(Boolean)
  if (!parts.length) return [{ text: '' }]
  return parts.map((text) => ({ text }))
}

function resetForm() {
  form.name = ''
  form.taboo = ''
  form.summary = ''
  form.instructionRows = [{ text: '' }]
  form.collectCount = 0
  form.season = 'all'
  form.effectTags = []
  form.suitConstitutions = []
  form.status = ADMIN_RECIPE_STATUS.ON
  form.coverUrl = ''
  form.ingredientBuckets = {
    main: [emptyIngRow()],
    aux: [emptyIngRow()],
    seasoning: [emptyIngRow()],
  }
  form.stepRows = [{ description: '' }]
}

const GROUP_LABEL = { main: '主料', aux: '辅料', seasoning: '调料' }

function buildPayload() {
  const ingredientGroups = BUCKET_DEFS.map((def) => ({
    key: def.key,
    label: GROUP_LABEL[def.key],
    items: form.ingredientBuckets[def.key]
      .map((r) => ({
        name: String(r.name || '').trim(),
        amount: String(r.amount || '').trim(),
      }))
      .filter((r) => r.name),
  })).filter((g) => g.items.length)

  const steps = form.stepRows
    .map((r) => ({ text: String(r.description || '').trim() }))
    .filter((s) => s.text)
  const instructionSummary = form.instructionRows
    .map((r) => String(r.text || '').trim())
    .filter(Boolean)
    .join('\n')
  return {
    name: form.name.trim(),
    taboo: form.taboo.trim(),
    summary: form.summary.trim(),
    instructionSummary,
    collectCount: form.collectCount,
    season: form.season,
    effectTags: [...form.effectTags],
    suitConstitutions: [...form.suitConstitutions],
    status: form.status,
    coverUrl: form.coverUrl.trim(),
    ingredientGroups,
    steps,
  }
}

/** 列表内上架开关：用详情拼出与表单一致的更新体 */
function recipeDetailToPayload(recipe, overrides = {}) {
  const groups = recipe.ingredientGroups || []
  const ingredientGroups = groups
    .map((g) => ({
      key: g.key || 'main',
      label: g.label || GROUP_LABEL[g.key] || '食材',
      items: (g.items || [])
        .map((it) => ({
          name: String(it.name || '').trim(),
          amount: String(it.amount || '').trim(),
          note: it.note,
        }))
        .filter((it) => it.name),
    }))
    .filter((g) => g.items.length)
  const steps = (recipe.steps || [])
    .map((s, idx) => ({
      order: Number(s.order) || idx + 1,
      text: String(s.text ?? s.description ?? '').trim(),
      tip: s.tip,
    }))
    .filter((s) => s.text)
  return {
    name: String(recipe.name || '').trim(),
    taboo: String(recipe.taboo || recipe.tabooReminder || '').trim() || DEFAULT_TABOO,
    summary: String(recipe.summary || '').trim(),
    instructionSummary: String(recipe.instructionSummary || '').trim(),
    season: recipe.season || 'all',
    effectTags: [...(recipe.effectTags || [])],
    suitConstitutions: [...(recipe.suitConstitutions || [])],
    status: overrides.status ?? recipe.status,
    coverUrl: String(recipe.coverUrl || '').trim(),
    ingredientGroups,
    steps,
    collectCount: recipe.collectCount,
  }
}

async function loadTable() {
  loading.value = true
  try {
    const data = await fetchAdminRecipePage({
      page: page.value,
      pageSize: pageSize.value,
      keyword: query.keyword.trim(),
      constitution: query.constitution,
      effectTag: query.effectTag,
      season: query.season,
    })
    tableData.value = data?.records || data?.list || []
    total.value = Number(data?.total) || tableData.value.length
  } catch (e) {
    tableData.value = []
    total.value = 0
    const msg =
      e?.message ||
      e?.msg ||
      (typeof e === 'string' ? e : '') ||
      '药膳列表加载失败'
    ElMessage.error(msg)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  loadTable()
}

function onReset() {
  query.keyword = ''
  query.constitution = ''
  query.effectTag = ''
  query.season = ''
  onSearch()
}

function onPageSizeChange() {
  page.value = 1
  loadTable()
}

function onSelectionChange(rows) {
  selectedRows.value = rows || []
}

function openCreate() {
  dialogTitle.value = '新增药膳'
  editingId.value = null
  resetForm()
  dialogVisible.value = true
}

async function openEdit(row) {
  dialogTitle.value = '编辑药膳'
  editingId.value = row.id
  resetForm()
  dialogVisible.value = true
  try {
    const data = await fetchAdminRecipeDetail(row.id)
    const recipe = data?.recipe || data
    if (!recipe) return
    form.name = recipe.name || ''
    form.taboo = String(recipe.taboo || recipe.tabooReminder || '').trim()
    form.summary = recipe.summary || ''
    form.instructionRows = detailToInstructionRows(recipe)
    form.collectCount = Number(recipe.collectCount) || 0
    form.season = recipe.season || 'all'
    form.effectTags = [...(recipe.effectTags || [])]
    form.suitConstitutions = [...(recipe.suitConstitutions || [])]
    form.status = recipe.status === ADMIN_RECIPE_STATUS.OFF ? ADMIN_RECIPE_STATUS.OFF : ADMIN_RECIPE_STATUS.ON
    form.coverUrl = recipe.coverUrl || ''
    form.ingredientBuckets = detailToIngredientBuckets(recipe)
    form.stepRows = detailToStepRows(recipe)
  } catch {
    dialogVisible.value = false
  }
}

function addIngredientRow(bucketKey) {
  form.ingredientBuckets[bucketKey].push(emptyIngRow())
}

function removeIngredientRow(bucketKey, i) {
  const rows = form.ingredientBuckets[bucketKey]
  if (rows.length <= 1) return
  rows.splice(i, 1)
}

function addStepRow() {
  form.stepRows.push({ description: '' })
}

function removeStepRow(i) {
  if (form.stepRows.length <= 1) return
  form.stepRows.splice(i, 1)
}

function moveStep(i, delta) {
  const j = i + delta
  if (j < 0 || j >= form.stepRows.length) return
  const rows = form.stepRows
  const [item] = rows.splice(i, 1)
  rows.splice(j, 0, item)
}

function addInstructionRow() {
  form.instructionRows.push({ text: '' })
}

function removeInstructionRow(i) {
  if (form.instructionRows.length <= 1) return
  form.instructionRows.splice(i, 1)
}

function moveInstructionRow(i, delta) {
  const j = i + delta
  if (j < 0 || j >= form.instructionRows.length) return
  const rows = form.instructionRows
  const [item] = rows.splice(i, 1)
  rows.splice(j, 0, item)
}

function fileToDataUrl(file) {
  return new Promise((resolve, reject) => {
    const fr = new FileReader()
    fr.onload = () => resolve(fr.result)
    fr.onerror = reject
    fr.readAsDataURL(file)
  })
}

function beforeCoverUpload(raw) {
  const ok = raw.type === 'image/jpeg' || raw.type === 'image/png'
  if (!ok) {
    ElMessage.error('仅支持 JPG、PNG 格式')
    return false
  }
  if (raw.size > 2 * 1024 * 1024) {
    ElMessage.error('图片大小不能超过 2MB')
    return false
  }
  return true
}

async function httpCoverUpload({ file }) {
  coverUploading.value = true
  try {
    const dataUrl = await fileToDataUrl(file)
    const res = await uploadAdminRecipeCoverDataUrl(dataUrl, file.name)
    const url = res?.url ?? res
    if (typeof url !== 'string' || !url) throw new Error('empty')
    form.coverUrl = url
    ElMessage.success('上传成功')
  } catch {
    ElMessage.error('上传失败，请重试')
  } finally {
    coverUploading.value = false
  }
}

async function onSubmit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  const payload = buildPayload()
  if (!payload.ingredientGroups.length) {
    ElMessage.warning('请至少选择一项食材（从食材库下拉）并填写用量')
    return
  }
  if (!payload.steps.length) {
    ElMessage.warning('请至少填写一步制作步骤')
    return
  }
  try {
    if (editingId.value == null) {
      await createAdminRecipe(payload)
      ElMessage.success('已新增')
    } else {
      await updateAdminRecipe(editingId.value, payload)
      ElMessage.success('已保存')
    }
    dialogVisible.value = false
    await loadTable()
  } catch {
    /* 全局错误提示 */
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除药膳「${row.name}」？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteAdminRecipe(row.id)
    ElMessage.success('已删除')
    await loadTable()
  } catch (e) {
    if (e !== 'cancel') {
      /* handled */
    }
  }
}

async function onBatchDelete() {
  const rows = selectedRows.value
  if (!rows.length) {
    ElMessage.warning('请先勾选要删除的药膳')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${rows.length} 条药膳？`, '批量删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    const ids = rows.map((r) => r.id)
    await batchDeleteAdminRecipes(ids)
    ElMessage.success('已批量删除')
    tableRef.value?.clearSelection?.()
    selectedRows.value = []
    await loadTable()
  } catch (e) {
    if (e !== 'cancel') {
      /* handled */
    }
  }
}

async function onShelfSwitch(row, online) {
  const next = online ? ADMIN_RECIPE_STATUS.ON : ADMIN_RECIPE_STATUS.OFF
  if (row.status === next) return
  statusBusyId.value = row.id
  try {
    const data = await fetchAdminRecipeDetail(row.id)
    const recipe = data?.recipe || data
    if (!recipe) return
    const payload = recipeDetailToPayload(recipe, { status: next })
    await updateAdminRecipe(row.id, payload)
    ElMessage.success(online ? '已上架' : '已下架')
    await loadTable()
  } catch {
    await loadTable()
  } finally {
    statusBusyId.value = null
  }
}

onMounted(loadTable)
</script>

<template>
  <div class="page admin-page">
    <header class="page-head">
      <div>
        <h1 class="page-title">药膳管理</h1>
        <p class="page-subtitle">
          新增或编辑时须填写禁忌提醒；食材名称请从食材库下拉选择（与接口校验一致）。
        </p>
      </div>
      <div class="head-actions">
        <el-button
          type="danger"
          plain
          :disabled="!selectedRows.length"
          @click="onBatchDelete"
        >
          批量删除
        </el-button>
        <el-button type="primary" @click="openCreate">新增药膳</el-button>
      </div>
    </header>

    <section class="page-card filter-card">
      <el-form :model="query" label-width="88px" @submit.prevent="onSearch">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="6">
            <el-form-item label="药膳名称">
              <el-input v-model="query.keyword" clearable placeholder="按名称筛选" />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="6">
            <el-form-item label="适宜体质">
              <el-select v-model="query.constitution" clearable placeholder="全部" style="width: 100%">
                <el-option v-for="c in ADMIN_CONSTITUTION_OPTIONS" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="6">
            <el-form-item label="功效标签">
              <el-select v-model="query.effectTag" clearable placeholder="全部" style="width: 100%">
                <el-option v-for="t in ADMIN_EFFECT_TAG_OPTIONS" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="6">
            <el-form-item label="季节">
              <el-select v-model="query.season" clearable placeholder="全部" style="width: 100%">
                <el-option
                  v-for="o in ADMIN_SEASON_FILTER_OPTIONS"
                  :key="o.value || 'all'"
                  :label="o.label"
                  :value="o.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="24" class="filter-actions">
            <el-button type="primary" @click="onSearch">查询</el-button>
            <el-button @click="onReset">重置</el-button>
          </el-col>
        </el-row>
      </el-form>
    </section>

    <section class="page-card table-card">
      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="tableData"
        row-key="id"
        stripe
        border
        empty-text="暂无药膳"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="id" label="ID" min-width="100" show-overflow-tooltip />
        <el-table-column prop="name" label="名称" min-width="120" />
        <el-table-column label="功效标签" min-width="160">
          <template #default="{ row }">
            <template v-if="(row.effectTags || []).length">
              <el-tag
                v-for="t in row.effectTags"
                :key="t"
                size="small"
                effect="plain"
                class="tag-gap"
              >
                {{ t }}
              </el-tag>
            </template>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="适用体质" min-width="140">
          <template #default="{ row }">
            {{ (row.suitConstitutions || []).join('、') || '—' }}
          </template>
        </el-table-column>
        <el-table-column label="季节" width="72">
          <template #default="{ row }">{{ seasonLabel(row.season) }}</template>
        </el-table-column>
        <el-table-column prop="collectCount" label="收藏数" width="88" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === ADMIN_RECIPE_STATUS.ON"
              :disabled="statusBusyId === row.id"
              inline-prompt
              active-text="上架"
              inactive-text="下架"
              @change="(v) => onShelfSwitch(row, v)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          :total="total"
          background
          @current-change="loadTable"
          @size-change="onPageSizeChange"
        />
      </div>
    </section>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="720px"
      destroy-on-close
      class="recipe-dialog"
      @closed="formRef?.resetFields?.()"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="药膳名称" prop="name">
          <el-input v-model="form.name" maxlength="48" show-word-limit placeholder="如：黄芪炖鸡" />
        </el-form-item>
        <el-form-item label="封面图（JPG/PNG，≤2MB）">
          <div class="cover-block">
            <el-upload
              class="cover-uploader"
              :show-file-list="false"
              accept="image/jpeg,image/png"
              :before-upload="beforeCoverUpload"
              :http-request="httpCoverUpload"
              :disabled="coverUploading"
            >
              <el-button type="primary" :loading="coverUploading">
                <el-icon class="btn-icon"><UploadFilled /></el-icon>
                上传图片
              </el-button>
            </el-upload>
            <el-input
              v-model="form.coverUrl"
              class="cover-url"
              placeholder="或直接粘贴图片 URL（https://）"
            />
          </div>
          <div v-if="form.coverUrl" class="cover-preview">
            <el-image :src="form.coverUrl" fit="cover" class="cover-thumb">
              <template #error>
                <span class="thumb-fail">无法显示缩略图</span>
              </template>
            </el-image>
          </div>
        </el-form-item>
        <el-form-item label="禁忌提醒（必填）" prop="taboo">
          <el-input
            v-model="form.taboo"
            type="textarea"
            :rows="4"
            maxlength="800"
            show-word-limit
            placeholder="请明确不适宜人群、食材过敏与用药相互作用等风险提示"
          />
        </el-form-item>
        <el-form-item label="简介">
          <el-input
            v-model="form.summary"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="简要介绍功效侧重、适合人群等（一两句话即可）"
          />
        </el-form-item>
        <el-form-item label="药膳要求">
          <p class="ing-hint">按条填写火候、食用频次、注意事项等；保存时自动按顺序合并，无需特殊格式。</p>
          <div v-for="(row, i) in form.instructionRows" :key="`ins-${i}`" class="step-block">
            <div class="dyn-row">
              <span class="step-row-label">第 {{ i + 1 }} 条</span>
              <el-input
                v-model="row.text"
                type="textarea"
                :rows="2"
                maxlength="300"
                show-word-limit
                placeholder="本条约说明什么（例如：小火慢炖约 30 分钟）"
                class="dyn-row__grow"
              />
              <div class="step-side">
                <el-button text type="primary" :disabled="i === 0" @click="moveInstructionRow(i, -1)">
                  上移
                </el-button>
                <el-button
                  text
                  type="primary"
                  :disabled="i === form.instructionRows.length - 1"
                  @click="moveInstructionRow(i, 1)"
                >
                  下移
                </el-button>
                <el-button text type="danger" @click="removeInstructionRow(i)">删</el-button>
              </div>
            </div>
          </div>
          <el-button text type="primary" @click="addInstructionRow">+ 添加一条</el-button>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="适用季节">
              <el-select v-model="form.season" style="width: 100%">
                <el-option v-for="o in ADMIN_SEASON_FORM_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上架状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="上架" :value="ADMIN_RECIPE_STATUS.ON" />
                <el-option label="下架" :value="ADMIN_RECIPE_STATUS.OFF" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="功效标签">
          <el-select v-model="form.effectTags" multiple filterable style="width: 100%" placeholder="选择标签">
            <el-option v-for="t in ADMIN_EFFECT_TAG_OPTIONS" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="适宜体质">
          <el-select v-model="form.suitConstitutions" multiple filterable style="width: 100%" placeholder="选择体质">
            <el-option v-for="c in ADMIN_CONSTITUTION_OPTIONS" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <template v-for="def in BUCKET_DEFS" :key="def.key">
          <el-form-item :label="def.label">
            <p class="ing-hint">
              「食材名称」须从下拉选择（已启用食材库）；缺项请先到「食材管理」新增。
            </p>
            <div v-for="(row, i) in form.ingredientBuckets[def.key]" :key="`${def.key}-${i}`" class="dyn-row">
              <el-select
                v-model="row.name"
                filterable
                clearable
                placeholder="从食材库选择"
                class="dyn-row__grow"
              >
                <el-option
                  v-for="ing in mergedIngredientOptions"
                  :key="`${def.key}-${ing.id}`"
                  :label="ingredientOptionLabel(ing)"
                  :value="ing.name"
                />
              </el-select>
              <el-input v-model="row.amount" placeholder="用量" class="dyn-row__amt" />
              <el-button text type="danger" @click="removeIngredientRow(def.key, i)">删</el-button>
            </div>
            <el-button text type="primary" @click="addIngredientRow(def.key)">+ 添加{{ def.label }}</el-button>
          </el-form-item>
        </template>
        <el-form-item label="制作步骤">
          <p class="ing-hint">按步填写即可；保存时由系统自动生成完整步骤数据，无需手写编号或 JSON。</p>
          <div v-for="(row, i) in form.stepRows" :key="i" class="step-block">
            <div class="dyn-row">
              <span class="step-row-label">第 {{ i + 1 }} 步</span>
              <el-input
                v-model="row.description"
                type="textarea"
                :rows="2"
                maxlength="400"
                show-word-limit
                placeholder="本步具体操作（如清洗、下锅顺序、调味）"
                class="dyn-row__grow"
              />
              <div class="step-side">
                <el-button text type="primary" :disabled="i === 0" @click="moveStep(i, -1)">上移</el-button>
                <el-button
                  text
                  type="primary"
                  :disabled="i === form.stepRows.length - 1"
                  @click="moveStep(i, 1)"
                >
                  下移
                </el-button>
                <el-button text type="danger" @click="removeStepRow(i)">删</el-button>
              </div>
            </div>
          </div>
          <el-button text type="primary" @click="addStepRow">+ 添加步骤</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-page {
  max-width: 1100px;
}

.page-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-md);
  margin-bottom: var(--space-lg);
}

.head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}

.page-title {
  margin: 0 0 6px;
  font-size: var(--font-size-xl);
}

.page-subtitle {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  max-width: 520px;
  line-height: 1.55;
}

.filter-card {
  margin-bottom: var(--space-lg);
  padding: var(--space-md) var(--space-lg);
}

.filter-actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-md);
}

.table-card {
  padding: var(--space-md);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-md);
}

.dyn-row {
  display: flex;
  align-items: flex-start;
  gap: var(--space-sm);
  margin-bottom: var(--space-sm);
  width: 100%;
}

.dyn-row__grow {
  flex: 1;
  min-width: 0;
}

.dyn-row__amt {
  width: 120px;
  flex-shrink: 0;
}

.step-block {
  width: 100%;
  margin-bottom: var(--space-sm);
}

.step-side {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  gap: 2px;
}

.step-row-label {
  flex-shrink: 0;
  width: 4.5em;
  padding-top: 8px;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.ing-hint {
  margin: 0 0 var(--space-sm);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.tag-gap {
  margin-right: 4px;
  margin-bottom: 4px;
}

.cover-block {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  align-items: center;
  width: 100%;
}

.cover-url {
  flex: 1;
  min-width: 200px;
}

.btn-icon {
  margin-right: 4px;
}

.cover-preview {
  margin-top: var(--space-sm);
}

.cover-thumb {
  width: 120px;
  height: 120px;
  border-radius: 8px;
  border: 1px solid var(--el-border-color);
}

.thumb-fail {
  font-size: 12px;
  color: var(--color-text-secondary);
}
</style>
