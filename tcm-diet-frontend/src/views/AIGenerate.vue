<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  showToast,
  Field as VanField,
  Checkbox as VanCheckbox,
  Button as VanButton,
  Empty as VanEmpty,
} from 'vant'
import AiTherapyPlanView from '@/components/ai/AiTherapyPlanView.vue'
import { useUserStore } from '@/stores/user'
import { useAiPlanStore } from '@/stores/aiPlan'
import { generateAiTherapyPlan, submitAiPlanFeedback } from '@/api/ai'
import { normalizeAiTherapyPlanData } from '@/mocks/aiTherapyPlanResponse'

const GENERIC_PLAN_MESSAGE = '症状表述不够清晰，为您推荐通用养生方案'
const FALLBACK_PLAN_MESSAGE =
  '大模型暂不可用或响应不完整，已为您展示本地参考方案（可检查网络与 LLM 配置）'

function aiGenerateUiDelayMs() {
  const raw = import.meta.env.VITE_AI_GENERATE_UI_DELAY_MS
  if (raw != null && String(raw).trim() !== '') {
    const n = Number(raw)
    if (Number.isFinite(n) && n >= 0) return Math.min(n, 5000)
  }
  if (import.meta.env.DEV && import.meta.env.VITE_USE_MOCK === 'true') {
    return 3200
  }
  return 0
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

const route = useRoute()
const userStore = useUserStore()
const aiPlanStore = useAiPlanStore()

/** 从详情页浏览器后退时恢复上次生成结果（bf-cache 外组件会重挂载） */
const AI_PAGE_DRAFT_KEY = 'tcm_ai_page_draft_v1'

const symptom = ref('')
const includeConstitution = ref(true)
const loading = ref(false)
const plan = ref(null)

const feedbackSent = ref(false)
const feedbackUseful = ref(null)

const examples = [
  '熬夜多，口干舌燥，脸上长痘',
  '易疲劳，气短，不想说话',
  '手脚发凉，怕冷，食欲不振',
]

const canUseConstitution = computed(() => Boolean(userStore.constitutionCode))

const isSaved = computed(() => (plan.value ? aiPlanStore.isPlanSaved(plan.value.planId) : false))

function fillExample(text) {
  symptom.value = text
}

function resetFeedback() {
  feedbackSent.value = false
  feedbackUseful.value = null
}

function loadAiPageDraft() {
  try {
    const raw = sessionStorage.getItem(AI_PAGE_DRAFT_KEY)
    if (!raw) return
    const o = JSON.parse(raw)
    if (typeof o?.symptom === 'string' && o.symptom.trim()) symptom.value = o.symptom
    if (o?.plan && typeof o.plan === 'object') plan.value = o.plan
    if (typeof o?.includeConstitution === 'boolean') includeConstitution.value = o.includeConstitution
    if (typeof o?.feedbackSent === 'boolean') feedbackSent.value = o.feedbackSent
    if (o?.feedbackUseful === true || o?.feedbackUseful === false) feedbackUseful.value = o.feedbackUseful
  } catch {
    /* ignore */
  }
}

function persistAiPageDraft() {
  try {
    if (!plan.value) {
      sessionStorage.removeItem(AI_PAGE_DRAFT_KEY)
      return
    }
    sessionStorage.setItem(
      AI_PAGE_DRAFT_KEY,
      JSON.stringify({
        symptom: symptom.value,
        plan: plan.value,
        includeConstitution: includeConstitution.value,
        feedbackSent: feedbackSent.value,
        feedbackUseful: feedbackUseful.value,
      }),
    )
  } catch {
    /* ignore */
  }
}

function applyRecipeEntryQuery() {
  const q = route.query
  const sceneCtx = typeof q.scene_context === 'string' ? q.scene_context.trim() : ''
  if (sceneCtx) {
    try {
      symptom.value = decodeURIComponent(sceneCtx)
    } catch {
      symptom.value = sceneCtx
    }
    plan.value = null
    resetFeedback()
    try {
      sessionStorage.removeItem(AI_PAGE_DRAFT_KEY)
    } catch {
      /* ignore */
    }
    return
  }
  if (symptom.value.trim()) return
  const rawTags = typeof q.effectTags === 'string' ? q.effectTags : ''
  const name = typeof q.recipeName === 'string' ? q.recipeName : ''
  if (rawTags) {
    const tags = rawTags
      .split(',')
      .map((s) => {
        const t = s.trim()
        if (!t) return ''
        try {
          return decodeURIComponent(t)
        } catch {
          return t
        }
      })
      .filter(Boolean)
    if (tags.length) {
      symptom.value = `参考药膳「${name || '当前药膳'}」的功效侧重：${tags.join('、')}，希望得到更贴合我个人的调养与膳食建议。`
      return
    }
  }
  if (name) {
    symptom.value = `参考药膳「${name}」，希望得到更贴合我个人的替代或加减食材方案。`
  }
}

onMounted(() => {
  const q = route.query
  const hasSceneCtx = typeof q.scene_context === 'string' && String(q.scene_context).trim() !== ''
  if (!hasSceneCtx) loadAiPageDraft()
  applyRecipeEntryQuery()
})
watch(() => route.query, applyRecipeEntryQuery)

watch(
  [plan, symptom, includeConstitution, feedbackSent, feedbackUseful],
  () => {
    persistAiPageDraft()
  },
  { deep: true },
)

async function onGenerate() {
  const text = symptom.value.trim()
  if (!text) {
    showToast({ type: 'warning', message: '请输入你的不适症状' })
    return
  }

  loading.value = true
  resetFeedback()

  const payload = {
    symptom: text,
    constitution:
      includeConstitution.value && userStore.constitutionCode ? userStore.constitutionCode : undefined,
  }

  const uiDelay = aiGenerateUiDelayMs()
  const delayP = uiDelay > 0 ? sleep(uiDelay) : Promise.resolve()

  try {
    const [raw] = await Promise.all([generateAiTherapyPlan(payload), delayP])
    const normalized = normalizeAiTherapyPlanData(raw, payload)
    plan.value = normalized
    if (normalized.isGenericPlan) {
      const sym = (payload.symptom || '').trim()
      const shortSymptom = sym.length > 0 && sym.length < 4
      showToast({
        type: 'primary',
        message: shortSymptom ? GENERIC_PLAN_MESSAGE : FALLBACK_PLAN_MESSAGE,
        duration: 4500,
      })
    }
  } catch {
    await delayP
    plan.value = normalizeAiTherapyPlanData(null, payload)
    showToast({ type: 'primary', message: FALLBACK_PLAN_MESSAGE, duration: 4500 })
  } finally {
    loading.value = false
  }
}

function onRegenerate() {
  onGenerate()
}

function onToggleSave() {
  if (!plan.value) return
  const on = aiPlanStore.toggleSavePlan(plan.value)
  showToast({ type: 'success', message: on ? '已加入「我的 → AI 方案收藏」' : '已取消收藏' })
}

async function onFeedback(useful) {
  if (!plan.value || feedbackSent.value) return
  feedbackUseful.value = useful
  feedbackSent.value = true
  try {
    await submitAiPlanFeedback({
      planId: plan.value.planId,
      useful,
    })
  } catch {
    /* 预览或未配置反馈接口时忽略 */
  }
  showToast({
    type: 'success',
    message: useful ? '感谢反馈，我们会持续优化推荐逻辑。' : '已记录，我们会改进生成质量。',
  })
}
</script>

<template>
  <div class="page ai-page campus-guide">
    <div class="compliance-banner page-card" role="alert">
      <strong class="compliance-banner__title">AI生成内容，仅供养生参考，不替代药物治疗</strong>
      <p class="compliance-banner__sub">
        不构成医疗诊断或处方；急重症请及时就医，请结合自身体质与医嘱审慎采纳。
      </p>
    </div>

    <header class="hero" aria-labelledby="ai-hero-title">
      <p class="hero__eyebrow">校园导览 · 个人调养</p>
      <h1 id="ai-hero-title" class="hero__title">AI 食疗方案</h1>
      <p class="hero__sub">
        用自然语言描述近期不适或调养目标，点击「生成方案」后由后端通过
        <strong>Chat Completions</strong>
        兼容接口调用大模型（配置见《大模型调用调试说明》与 Spring
        <code class="hero__code">llm.url</code>、<code class="hero__code">llm.api-key</code>、<code class="hero__code">llm.model</code>），在下方展示推荐菜谱、食材与生活调养要点。
      </p>
    </header>

    <section class="input-panel page-card">
      <van-field
        v-model="symptom"
        type="textarea"
        rows="8"
        autosize
        maxlength="500"
        show-word-limit
        placeholder="例如：最近熬夜多，口干、脸上容易长痘，想从饮食上调理……"
        class="symptom-field"
      />

      <div class="examples">
        <span class="examples__label">示例（点击填入）：</span>
        <div class="examples__chips">
          <button
            v-for="(ex, i) in examples"
            :key="i"
            type="button"
            class="example-chip"
            @click="fillExample(ex)"
          >
            {{ ex }}
          </button>
        </div>
      </div>

      <div class="toolbar">
        <van-checkbox v-model="includeConstitution" :disabled="!canUseConstitution" shape="square">
          带入我的体质档案（{{ canUseConstitution ? userStore.constitutionLabel : '未设置' }}）
        </van-checkbox>
        <van-button type="primary" size="large" native-type="button" :loading="loading" @click="onGenerate">
          生成方案
        </van-button>
      </div>
    </section>

    <div v-if="plan" class="result-stack">
      <div class="result-actions page-card">
        <van-button
          :type="isSaved ? 'warning' : 'primary'"
          plain
          hairline
          native-type="button"
          @click="onToggleSave"
        >
          {{ isSaved ? '已收藏方案' : '收藏本方案' }}
        </van-button>
        <van-button plain hairline native-type="button" :loading="loading" @click="onRegenerate">
          重新生成
        </van-button>
        <div class="feedback-inline">
          <span class="feedback-inline__label">这条方案有用吗？</span>
          <van-button
            size="small"
            :type="feedbackUseful === true ? 'success' : 'default'"
            plain
            hairline
            native-type="button"
            :disabled="feedbackSent"
            @click="onFeedback(true)"
          >
            有用
          </van-button>
          <van-button
            size="small"
            :type="feedbackUseful === false ? 'primary' : 'default'"
            plain
            hairline
            native-type="button"
            :disabled="feedbackSent"
            @click="onFeedback(false)"
          >
            没用
          </van-button>
        </div>
      </div>

      <AiTherapyPlanView :plan="plan" />
    </div>

    <van-empty
      v-else-if="!loading"
      class="empty-hint"
      image="search"
      description="填写症状描述后点击「生成方案」"
    />

    <Teleport to="body">
      <transition name="fade">
        <div v-if="loading" class="loading-overlay" role="status" aria-live="polite">
          <p class="loading-overlay__compliance" aria-hidden="false">
            <strong>AI生成内容，仅供养生参考，不替代药物治疗</strong>
            <span class="loading-overlay__compliance-sub">
              不构成医疗诊断或处方；急重症请及时就医。
            </span>
          </p>
          <div class="loading-card loading-breath">
            <p class="loading-card__title">正在生成专属食疗方案，请稍候…</p>
            <p class="loading-card__sub">结合症状描述与可选体质信息编排建议中</p>
          </div>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<style scoped>
.campus-guide {
  max-width: 1100px;
  padding-top: 0;
}

.hero {
  margin-bottom: var(--space-lg);
  padding: var(--space-md) 0 var(--space-sm);
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
  font-family: var(--font-serif);
  color: var(--color-text-primary);
}

.hero__sub {
  margin: 0;
  max-width: 42rem;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.65;
}

.hero__code {
  font-family: ui-monospace, monospace;
  font-size: 0.92em;
  padding: 0.1em 0.35em;
  border-radius: 4px;
  background: color-mix(in srgb, var(--color-text-primary) 6%, transparent);
}

.ai-page {
  position: relative;
}

.compliance-banner {
  position: sticky;
  top: 46px;
  z-index: 20;
  margin: 0 0 var(--space-md);
  padding: var(--space-md) var(--space-lg);
  border: 1px solid rgba(230, 162, 60, 0.55);
  box-shadow: 0 2px 10px rgba(230, 162, 60, 0.12);
  background: #fffbeb;
}

.compliance-banner__title {
  display: block;
  font-size: var(--font-size-md);
  font-weight: 700;
  color: var(--color-text-primary);
  line-height: 1.45;
}

.compliance-banner__sub {
  margin: 6px 0 0;
  font-size: var(--font-size-sm);
  line-height: 1.55;
  color: var(--color-text-secondary);
}

.input-panel {
  margin-bottom: var(--space-lg);
}

.symptom-field :deep(.van-field__control) {
  font-size: var(--font-size-md);
  line-height: 1.65;
  min-height: 200px !important;
  padding-top: 4px;
  padding-bottom: 4px;
}

.examples {
  margin-top: var(--space-md);
}

.examples__label {
  display: block;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  margin-bottom: var(--space-sm);
}

.examples__chips {
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.example-chip {
  text-align: left;
  padding: 10px 14px;
  border-radius: var(--radius-md);
  border: 1px dashed var(--color-border);
  background: var(--color-bg-main);
  color: var(--color-text);
  font-size: var(--font-size-sm);
  line-height: 1.5;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    color 0.2s ease;
}

.example-chip:hover {
  border-color: var(--color-primary);
  background: rgba(74, 124, 89, 0.06);
  color: var(--color-primary-dark);
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  margin-top: var(--space-lg);
}

.toolbar :deep(.van-checkbox) {
  flex: 1 1 220px;
}

.result-stack {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

.result-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-md);
}

.feedback-inline {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-sm);
  margin-left: auto;
}

.feedback-inline__label {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.empty-hint {
  margin-top: var(--space-xl);
}

.loading-overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  background: rgba(40, 44, 42, 0.28);
  backdrop-filter: blur(2px);
  padding: var(--space-lg);
}

.loading-overlay__compliance {
  margin: 0;
  max-width: 420px;
  text-align: center;
  font-size: var(--font-size-sm);
  line-height: 1.5;
  color: #5c4a1a;
  background: rgba(253, 246, 236, 0.96);
  border: 1px solid rgba(230, 162, 60, 0.65);
  border-radius: var(--radius-md);
  padding: 10px 14px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
}

.loading-overlay__compliance-sub {
  display: block;
  margin-top: 4px;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-secondary);
}

.loading-card {
  max-width: 380px;
  padding: 28px 32px;
  border-radius: var(--radius-lg);
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-card);
  text-align: center;
}

.loading-card__title {
  margin: 0 0 var(--space-sm);
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--color-text);
}

.loading-card__sub {
  margin: 0;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

@keyframes breathe-glow {
  0%,
  100% {
    box-shadow:
      0 0 0 0 rgba(74, 124, 89, 0.2),
      0 0 0 1px var(--color-border);
    transform: scale(1);
  }
  50% {
    box-shadow:
      0 0 28px 6px rgba(74, 124, 89, 0.18),
      0 0 0 1px rgba(74, 124, 89, 0.25);
    transform: scale(1.01);
  }
}

.loading-breath {
  animation: breathe-glow 2.4s ease-in-out infinite;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.22s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 640px) {
  .feedback-inline {
    margin-left: 0;
    width: 100%;
  }

  .toolbar .van-button {
    width: 100%;
  }
}
</style>
