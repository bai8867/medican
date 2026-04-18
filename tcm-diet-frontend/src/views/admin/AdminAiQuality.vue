<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Histogram, RefreshRight, WarningFilled } from '@element-plus/icons-vue'
import {
  fetchAiIssueSampleDetail,
  fetchAiIssueSamples,
  fetchAiQualityRules,
  replayAiIssueSample,
  updateAiQualityRules,
} from '@/api/adminAiQuality'

const quiet = { skipGlobalMessage: true }

const pageLoading = ref(true)
const loadHint = ref('')
const loading = ref(false)
const saving = ref(false)
const rulesForm = reactive({
  guardEnabled: true,
  strictSafety: true,
  scoreThreshold: 75,
})
const query = reactive({
  page: 1,
  pageSize: 20,
  unresolvedOnly: false,
})
const total = ref(0)
const samples = ref([])
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const violatedRulesText = computed(() => {
  const v = detail.value?.violatedRules
  if (Array.isArray(v)) return v.join('、') || '—'
  return '—'
})

const samplesEmptyText = computed(() => {
  if (loadHint.value && loadHint.value.includes('样本列表')) return '样本接口不可用，请查看上方说明或重试'
  return '暂无样本'
})

function describeHttpError(e) {
  const status = e?.response?.status
  if (status === 404) {
    return '接口返回 404：当前请求未到达后端的 /api/admin/ai-quality。请确认 Spring 已用本仓库最新代码启动；若 .env 中设置了 VITE_API_PREFIX，请同时让 VITE_API_BASE_URL 与之相同，或删除二者使用默认 /api。'
  }
  if (e?.code === 'ERR_NETWORK' || String(e?.message || '').toLowerCase().includes('network')) {
    return '网络不可用：请确认后端在 11888 运行，且开发环境使用 npm run dev 走 Vite 代理。'
  }
  const body = e?.response?.data
  const msg =
    (body && typeof body === 'object' && String(body.msg || body.message || '')) || e?.message || ''
  return msg || '加载失败'
}

async function loadRules() {
  const data = await fetchAiQualityRules(quiet)
  rulesForm.guardEnabled = data.guardEnabled !== false
  rulesForm.strictSafety = data.strictSafety !== false
  rulesForm.scoreThreshold = Number(data.scoreThreshold) || 75
}

async function loadSamples() {
  loading.value = true
  try {
    const data = await fetchAiIssueSamples({ ...query }, quiet)
    samples.value = Array.isArray(data.records) ? data.records : []
    total.value = Number(data.total) || 0
  } finally {
    loading.value = false
  }
}

async function bootstrap() {
  pageLoading.value = true
  loadHint.value = ''
  const hints = []
  try {
    await loadRules()
  } catch (e) {
    hints.push(`规则：${describeHttpError(e)}`)
  }
  try {
    await loadSamples()
  } catch (e) {
    hints.push(`样本列表：${describeHttpError(e)}`)
  }
  if (hints.length) loadHint.value = hints.join(' ')
  pageLoading.value = false
}

async function saveRules() {
  saving.value = true
  try {
    const data = await updateAiQualityRules({
      guardEnabled: rulesForm.guardEnabled,
      strictSafety: rulesForm.strictSafety,
      scoreThreshold: rulesForm.scoreThreshold,
    })
    rulesForm.guardEnabled = data.guardEnabled !== false
    rulesForm.strictSafety = data.strictSafety !== false
    rulesForm.scoreThreshold = Number(data.scoreThreshold) || 75
    ElMessage.success('规则已更新')
  } catch (err) {
    ElMessage.error(describeHttpError(err))
  } finally {
    saving.value = false
  }
}

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await fetchAiIssueSampleDetail(row.id, quiet)
  } catch (e) {
    ElMessage.error(describeHttpError(e))
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function replay(row) {
  try {
    const data = await replayAiIssueSample(row.id)
    if (data?.ok) {
      ElMessage.success('回放完成')
      await loadSamples()
    } else {
      ElMessage.warning(data?.message || '回放失败')
    }
  } catch (e) {
    ElMessage.error(describeHttpError(e))
  }
}

function onPageChange(page) {
  query.page = page
  loadSamples()
}

async function refreshSamples() {
  try {
    await loadSamples()
    ElMessage.success('列表已刷新')
  } catch (e) {
    ElMessage.error(describeHttpError(e))
  }
}

onMounted(bootstrap)
</script>

<template>
  <div class="ai-quality-page" v-loading="pageLoading">
    <header class="ai-quality-hero admin-page-card">
      <div class="ai-quality-hero__icon" aria-hidden="true">
        <el-icon :size="28"><Histogram /></el-icon>
      </div>
      <div class="ai-quality-hero__text">
        <h1 class="ai-quality-hero__title">AI 质量治理</h1>
        <p class="ai-quality-hero__sub">
          配置生成结果的质量闸门与问题样本沉淀；支持查看违规上下文与离线回放评估。
        </p>
      </div>
    </header>

    <el-alert
      v-if="loadHint"
      class="ai-quality-alert"
      type="warning"
      :closable="false"
      show-icon
    >
      <template #icon>
        <el-icon class="ai-quality-alert__ico"><WarningFilled /></el-icon>
      </template>
      <div class="ai-quality-alert__body">{{ loadHint }}</div>
      <div class="ai-quality-alert__actions">
        <el-button type="primary" size="small" @click="bootstrap">重试</el-button>
      </div>
    </el-alert>

    <el-row :gutter="16" class="ai-quality-grid">
      <el-col :xs="24" :lg="10">
        <el-card class="ui-card ui-card--static ai-quality-card" shadow="never">
          <template #header>
            <div class="card-head">
              <span class="card-head__title">治理规则</span>
              <el-button type="primary" :loading="saving" :disabled="!!loadHint" @click="saveRules">
                保存
              </el-button>
            </div>
          </template>
          <p class="card-lead">作用于 AI 食疗方案等生成链路：低于阈值或命中安全规则时可拦截或降级。</p>
          <el-form label-position="top" class="rule-form">
            <el-form-item label="治理总开关">
              <div class="form-row">
                <el-switch v-model="rulesForm.guardEnabled" />
                <span class="form-hint">关闭后仅记录，不拦截低质量输出（慎用）。</span>
              </div>
            </el-form-item>
            <el-form-item label="安全违规严格拦截">
              <div class="form-row">
                <el-switch v-model="rulesForm.strictSafety" :disabled="!!loadHint" />
                <span class="form-hint">命中违规话术/承诺类问题时直接失败。</span>
              </div>
            </el-form-item>
            <el-form-item label="最低质量分阈值（0–100）">
              <el-input-number
                v-model="rulesForm.scoreThreshold"
                :min="0"
                :max="100"
                controls-position="right"
              />
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="14">
        <el-card class="ui-card ui-card--static ai-quality-card" shadow="never">
          <template #header>
            <div class="card-head">
              <span class="card-head__title">问题样本</span>
              <div class="head-actions">
                <el-switch
                  v-model="query.unresolvedOnly"
                  active-text="仅未回放"
                  :disabled="!!loadHint"
                  @change="loadSamples"
                />
                <el-button :icon="RefreshRight" :disabled="!!loadHint" @click="refreshSamples">
                  刷新
                </el-button>
              </div>
            </div>
          </template>
          <el-table
            v-loading="loading"
            :data="samples"
            class="ai-quality-table"
            stripe
            :empty-text="samplesEmptyText"
          >
            <el-table-column prop="id" label="ID" width="72" />
            <el-table-column prop="symptom" label="用户描述" min-width="160" show-overflow-tooltip />
            <el-table-column prop="qualityScore" label="质量分" width="84" align="center" />
            <el-table-column prop="scoreThreshold" label="阈值" width="72" align="center" />
            <el-table-column label="安全" width="96" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="Number(row.safetyPassed) === 1 ? 'success' : 'danger'">
                  {{ Number(row.safetyPassed) === 1 ? '通过' : '未过' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="source" label="来源" width="120" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="时间" width="168" />
            <el-table-column label="操作" width="168" fixed="right" align="right">
              <template #default="{ row }">
                <el-button link type="primary" :disabled="!!loadHint" @click="openDetail(row)">
                  详情
                </el-button>
                <el-button link type="warning" :disabled="!!loadHint" @click="replay(row)">
                  回放
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              :current-page="query.page"
              :page-size="query.pageSize"
              layout="total, prev, pager, next"
              :total="total"
              @current-change="onPageChange"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-drawer v-model="detailVisible" title="问题样本详情" size="min(560px, 92vw)" class="ai-quality-drawer">
      <div v-loading="detailLoading" class="drawer-body">
        <template v-if="detail && !detailLoading">
          <el-descriptions :column="2" border size="small" class="detail-desc">
            <el-descriptions-item label="ID">{{ detail.id }}</el-descriptions-item>
            <el-descriptions-item label="时间">{{ detail.createdAt || '—' }}</el-descriptions-item>
            <el-descriptions-item label="体质">{{ detail.constitutionCode || '—' }}</el-descriptions-item>
            <el-descriptions-item label="回放状态">
              {{ Number(detail.replayed) === 1 ? '已回放' : '未回放' }}
            </el-descriptions-item>
            <el-descriptions-item label="质量分 / 阈值" :span="2">
              {{ detail.qualityScore ?? '—' }} / {{ detail.scoreThreshold ?? '—' }}
            </el-descriptions-item>
            <el-descriptions-item label="违规规则" :span="2">
              {{ violatedRulesText }}
            </el-descriptions-item>
            <el-descriptions-item label="用户描述" :span="2">
              {{ detail.symptom || '—' }}
            </el-descriptions-item>
          </el-descriptions>
          <div class="json-blocks">
            <div class="json-block">
              <div class="json-block__label">请求载荷</div>
              <pre class="detail-pre">{{ JSON.stringify(detail.requestPayload || {}, null, 2) }}</pre>
            </div>
            <div class="json-block">
              <div class="json-block__label">模型输出（节选）</div>
              <pre class="detail-pre">{{ JSON.stringify(detail.responsePayload || {}, null, 2) }}</pre>
            </div>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.ai-quality-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
  min-height: 320px;
}

.ai-quality-hero {
  display: flex;
  align-items: flex-start;
  gap: var(--space-md);
  padding: var(--space-lg);
  border: 1px solid color-mix(in srgb, var(--color-border) 85%, var(--color-primary) 12%);
  background: linear-gradient(
    135deg,
    color-mix(in srgb, #fff 94%, var(--color-primary) 6%) 0%,
    #fff 48%
  );
}

.ai-quality-hero__icon {
  flex-shrink: 0;
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  background: color-mix(in srgb, var(--color-primary) 14%, #fff);
  color: var(--color-primary-dark);
}

.ai-quality-hero__title {
  margin: 0 0 6px;
  font-size: var(--font-size-xl);
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--color-text-primary);
}

.ai-quality-hero__sub {
  margin: 0;
  font-size: var(--font-size-sm);
  line-height: 1.55;
  color: var(--color-text-secondary);
  max-width: 52rem;
}

.ai-quality-alert :deep(.el-alert__content) {
  width: 100%;
}

.ai-quality-alert__body {
  font-size: var(--font-size-sm);
  line-height: 1.55;
  margin-bottom: 10px;
}

.ai-quality-alert__ico {
  font-size: 18px;
}

.ai-quality-grid {
  align-items: stretch;
}

.ai-quality-card :deep(.el-card__header) {
  padding: 14px 18px;
  border-bottom: 1px solid var(--color-border);
}

.ai-quality-card :deep(.el-card__body) {
  padding: 18px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-head__title {
  font-weight: 600;
  font-size: var(--font-size-md);
}

.card-lead {
  margin: 0 0 var(--space-md);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.head-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.form-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.form-hint {
  font-size: 12px;
  color: var(--color-text-secondary);
  line-height: 1.45;
  max-width: 100%;
}

.rule-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

.rule-form :deep(.el-form-item__label) {
  font-weight: 600;
  color: var(--color-text-primary);
}

.ai-quality-table :deep(.el-table__header th) {
  font-weight: 600;
  background: color-mix(in srgb, var(--color-bg-main) 65%, #fff) !important;
}

.pager {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.drawer-body {
  min-height: 120px;
}

.detail-desc {
  margin-bottom: var(--space-md);
}

.json-blocks {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.json-block__label {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
}

.detail-pre {
  margin: 0;
  background: #1a2220;
  color: #d6ebe0;
  border-radius: var(--radius-md);
  padding: 12px 14px;
  max-height: 36vh;
  overflow: auto;
  font-size: 12px;
  line-height: 1.45;
  border: 1px solid rgba(255, 255, 255, 0.06);
}
</style>
