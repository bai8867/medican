<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore, CONSTITUTION_TYPES } from '@/stores/user'
import { submitConstitutionTest } from '@/api/constitution.js'
import {
  CONSTITUTION_BRIEF,
  SCORE_OPTIONS,
  getQuestionsByGroup,
  computeMockConstitution,
} from '@/data/constitutionSurvey.js'
import { SEASON_OPTIONS, getCurrentSeasonCode, getSeasonLabel } from '@/utils/season.js'

const router = useRouter()
const userStore = useUserStore()

/** summary: 已完成用户入口；quiz: 分步问卷；confirm: 结果确认 */
const phase = ref(userStore.constitutionSurveyCompleted ? 'summary' : 'quiz')
const groupIndex = ref(0)
/** @type {import('vue').Ref<(number | null)[]>} */
const answers = ref(Array.from({ length: 9 }, () => null))

const submitting = ref(false)
const saving = ref(false)

/** 测评或跳过得到的判定体质（确认页初始值） */
const inferredCode = ref('pinghe')
const inferredBrief = ref('')

const pickCode = ref('pinghe')
const pickSeason = ref(getCurrentSeasonCode())

const progressPercent = computed(() => ((groupIndex.value + 1) / 3) * 100)

const currentQuestions = computed(() => getQuestionsByGroup(groupIndex.value))

const groupFilled = computed(() =>
  currentQuestions.value.every((q) => {
    const v = answers.value[q.id - 1]
    return v != null && v >= 1 && v <= 5
  }),
)

function allQuestionsAnswered() {
  return answers.value.every((v) => v != null && v >= 1 && v <= 5)
}

const seasonLabel = computed(() => getSeasonLabel(userStore.seasonCode))

/** 所选体质与问卷/跳过初判不一致时，视为手动选择模式（U-05） */
const isManualConstitutionPick = computed(() => pickCode.value !== inferredCode.value)

/** 确认页解读：与下拉所选体质一致；若仍为初判体质则优先展示接口文案 */
const resultBriefDisplay = computed(() => {
  if (pickCode.value !== inferredCode.value) return briefFor(pickCode.value)
  return inferredBrief.value || briefFor(pickCode.value)
})

function briefFor(code) {
  return CONSTITUTION_BRIEF[code] || ''
}

function labelForConstitution(code) {
  return CONSTITUTION_TYPES.find((c) => c.code === code)?.label || code
}

function normalizeApiResult(data) {
  if (!data || typeof data !== 'object') return null
  const constitutionCode =
    data.constitutionCode ??
    data.primaryCode ??
    data.code ??
    data.constitution ??
    data.type ??
    null
  const brief =
    data.brief ??
    data.primaryLabel ??
    data.summary ??
    data.interpretation ??
    null
  if (!constitutionCode) return null
  return { constitutionCode, brief: brief || briefFor(constitutionCode) }
}

onMounted(() => {
  /** U-08：已有画像时确保展示摘要（不覆盖未保存的 confirm/quiz 中间态） */
  if (userStore.constitutionSurveyCompleted) phase.value = 'summary'
})

async function finishQuiz() {
  const list = answers.value.map((v) => Number(v))
  submitting.value = true
  let code = 'pinghe'
  let brief = briefFor('pinghe')
  try {
    const raw = await submitConstitutionTest({ answers: list })
    const norm = normalizeApiResult(raw)
    if (norm) {
      code = norm.constitutionCode
      brief = norm.brief || briefFor(code)
    } else {
      throw new Error('empty')
    }
  } catch {
    const mock = computeMockConstitution(answers.value)
    code = mock.constitutionCode
    brief = briefFor(code)
    ElMessage.info('接口暂不可用，已按本地规则取最高分体质')
  } finally {
    submitting.value = false
  }
  inferredCode.value = code
  inferredBrief.value = brief
  pickCode.value = code
  pickSeason.value = userStore.seasonCode || getCurrentSeasonCode()
  phase.value = 'confirm'
}

function onNextGroup() {
  if (!groupFilled.value) {
    ElMessage.warning('请为本组每一题选择 1–5 分')
    return
  }
  if (groupIndex.value < 2) {
    groupIndex.value += 1
    return
  }
  if (!allQuestionsAnswered()) {
    ElMessage.warning('请完成全部 9 题后再提交（可返回前两组检查）')
    return
  }
  finishQuiz()
}

function onPrevGroup() {
  if (groupIndex.value > 0) groupIndex.value -= 1
}

async function onSkip() {
  const season = getCurrentSeasonCode()
  saving.value = true
  try {
    await submitConstitutionTest({
      constitutionCode: 'pinghe',
      seasonCode: season,
    }).catch(() => {})
    userStore.saveConstitutionProfile('pinghe', season)
    ElMessage.success('已跳过测评，默认保存为平和质')
    router.push({ name: 'Home' })
  } finally {
    saving.value = false
  }
}

function startRetest() {
  answers.value = Array.from({ length: 9 }, () => null)
  groupIndex.value = 0
  phase.value = 'quiz'
}

function openManualAdjust() {
  pickCode.value = userStore.constitutionCode || 'pinghe'
  pickSeason.value = userStore.seasonCode || getCurrentSeasonCode()
  inferredCode.value = pickCode.value
  inferredBrief.value = briefFor(pickCode.value)
  phase.value = 'confirm'
}

function backToSummary() {
  phase.value = 'summary'
}

async function onConfirmProfile() {
  saving.value = true
  try {
    const payload = {
      constitutionCode: pickCode.value,
      seasonCode: pickSeason.value,
    }
    const filled = answers.value.every((v) => v != null && v >= 1 && v <= 5)
    if (filled) payload.answers = [...answers.value]
    await submitConstitutionTest(payload).catch(() => {})
  } finally {
    userStore.saveConstitutionProfile(pickCode.value, pickSeason.value)
    saving.value = false
    ElMessage.success('画像已保存')
    router.push({ name: 'Home' })
  }
}
</script>

<template>
  <div class="page constitution-page">
    <h1 class="page-title">体质采集</h1>
    <p class="page-subtitle">
      共 9 题分三组，请按近一年的真实感受作答；提交后系统判定体质，您仍可在确认页微调。
    </p>

    <!-- 已完成：档案摘要 -->
    <div v-if="phase === 'summary'" class="page-card summary-card">
      <div class="summary-row">
        <span class="summary-label">当前体质</span>
        <span class="summary-value">{{ userStore.constitutionLabel }}</span>
      </div>
      <div class="summary-row">
        <span class="summary-label">养生季节</span>
        <span class="summary-value">{{ seasonLabel }}</span>
      </div>
      <p class="summary-hint">如需更新测评结果或手动调整画像，可使用下方按钮。</p>
      <div class="summary-actions">
        <el-button type="primary" @click="startRetest">重新测评</el-button>
        <el-button @click="openManualAdjust">手动调整</el-button>
      </div>
    </div>

    <!-- 分步问卷 -->
    <div v-else-if="phase === 'quiz'" class="page-card quiz-card">
      <div class="quiz-head">
        <span class="quiz-step">第 {{ groupIndex + 1 }} / 3 组</span>
        <el-button text type="primary" class="quiz-skip" :loading="saving" @click="onSkip">
          跳过测评
        </el-button>
      </div>
      <el-progress :percentage="progressPercent" :stroke-width="10" striped striped-flow />

      <div class="question-list">
        <section
          v-for="q in currentQuestions"
          :key="q.id"
          class="question-block"
        >
          <h2 class="question-title">
            <span class="question-no">{{ q.id }}.</span>
            {{ q.text }}
          </h2>
          <p class="question-meta">
            本题得分主要计入「{{ labelForConstitution(q.targetCode) }}」维度（1–5 分越高，该倾向越明显）。
          </p>
          <el-radio-group v-model="answers[q.id - 1]" class="score-group">
            <el-radio
              v-for="opt in SCORE_OPTIONS"
              :key="opt.score"
              :value="opt.score"
              border
              class="score-radio"
            >
              <span class="score-num">{{ opt.score }} 分</span>
              <span class="score-label">{{ opt.label }}</span>
            </el-radio>
          </el-radio-group>
        </section>
      </div>

      <div class="quiz-nav">
        <el-button :disabled="groupIndex === 0" @click="onPrevGroup">上一组</el-button>
        <el-button type="primary" :loading="submitting" @click="onNextGroup">
          {{ groupIndex < 2 ? '下一组' : '提交并查看结果' }}
        </el-button>
      </div>
    </div>

    <!-- 结果确认 -->
    <div v-else-if="phase === 'confirm'" class="page-card confirm-card">
      <div v-if="userStore.constitutionSurveyCompleted" class="confirm-back">
        <el-button text @click="backToSummary">← 返回档案</el-button>
      </div>

      <div class="result-hero" aria-live="polite">
        <h1 class="result-heading">
          您的体质：<span class="result-type-inline">{{ labelForConstitution(pickCode) }}</span>
        </h1>
        <p class="result-label">以下为当前保存前预览，可在下方调整</p>
      </div>

      <p class="result-brief">{{ resultBriefDisplay }}</p>

      <el-form label-position="top" class="confirm-form">
        <el-form-item label="手动切换体质（九种）">
          <el-select v-model="pickCode" placeholder="请选择" style="width: 100%">
            <el-option
              v-for="c in CONSTITUTION_TYPES"
              :key="c.code"
              :label="c.label"
              :value="c.code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="养生季节">
          <el-radio-group v-model="pickSeason" class="season-radio-group">
            <el-radio
              v-for="s in SEASON_OPTIONS"
              :key="s.code"
              :value="s.code"
              border
            >
              {{ s.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <p v-if="isManualConstitutionPick" class="manual-mode-hint">
        您已切换至手动选择模式
      </p>

      <div class="confirm-actions">
        <el-button type="primary" size="large" :loading="saving" @click="onConfirmProfile">
          确认并保存，进入首页
        </el-button>
      </div>
      <p class="confirm-footnote">
        系统初判为「{{ labelForConstitution(inferredCode) }}」，您可在上方下拉框中修改后保存。
      </p>
    </div>
  </div>
</template>

<style scoped>
.constitution-page {
  max-width: 720px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.summary-row {
  display: flex;
  align-items: baseline;
  gap: var(--space-md);
  font-size: var(--font-size-md);
}

.summary-label {
  color: var(--color-text-secondary);
  min-width: 5em;
}

.summary-value {
  font-weight: 600;
  font-size: var(--font-size-lg);
  color: var(--color-primary);
}

.summary-hint {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.summary-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  margin-top: var(--space-sm);
}

.quiz-card {
  display: flex;
  flex-direction: column;
  gap: var(--space-lg);
}

.quiz-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
}

.quiz-step {
  font-weight: 600;
  color: var(--color-text-primary);
}

.quiz-skip {
  flex-shrink: 0;
}

.question-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-xl);
}

.question-block {
  padding-bottom: var(--space-lg);
  border-bottom: 1px solid var(--color-border);
}

.question-block:last-of-type {
  border-bottom: none;
  padding-bottom: 0;
}

.question-title {
  margin: 0 0 var(--space-xs);
  font-size: var(--font-size-md);
  font-weight: 600;
  line-height: 1.45;
  color: var(--color-text-primary);
}

.question-meta {
  margin: 0 0 var(--space-md);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.question-no {
  color: var(--color-primary);
  margin-right: 4px;
}

.score-group {
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: var(--space-xs);
}

.score-radio {
  margin: 0 !important;
  height: auto !important;
  align-items: flex-start;
  padding: 10px 12px;
  border-radius: var(--radius-md);
}

.score-radio :deep(.el-radio__label) {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  white-space: normal;
  line-height: 1.4;
}

.score-num {
  font-weight: 600;
  color: var(--color-text-primary);
}

.score-label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.quiz-nav {
  display: flex;
  justify-content: space-between;
  gap: var(--space-md);
  flex-wrap: wrap;
}

.confirm-card {
  text-align: center;
}

.confirm-back {
  text-align: left;
  margin-bottom: var(--space-sm);
}

.result-hero {
  margin-bottom: var(--space-md);
}

.result-heading {
  margin: 0 0 var(--space-xs);
  font-family: var(--font-serif);
  font-size: clamp(1.35rem, 4vw, 1.85rem);
  font-weight: 700;
  color: var(--color-text-primary);
  line-height: 1.3;
}

.result-type-inline {
  color: var(--color-primary);
}

.result-label {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  letter-spacing: 0.02em;
}

.season-radio-group {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
}

.manual-mode-hint {
  margin: 0 0 var(--space-md);
  font-size: var(--font-size-sm);
  color: var(--color-warning, #c27a00);
  font-weight: 600;
}

.result-brief {
  margin: 0 0 var(--space-xl);
  text-align: left;
  font-size: var(--font-size-md);
  line-height: 1.65;
  color: var(--color-text-primary);
}

.confirm-form {
  max-width: 400px;
  margin: 0 auto var(--space-lg);
  text-align: left;
}

.confirm-actions {
  margin-bottom: var(--space-md);
}

.confirm-footnote {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.5;
}
</style>
