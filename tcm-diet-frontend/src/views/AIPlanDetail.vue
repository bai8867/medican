<script setup>
import { ref, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import AiTherapyPlanView from '@/components/ai/AiTherapyPlanView.vue'
import { fetchAiPlanDetail } from '@/api/ai.js'
import { useAiPlanStore } from '@/stores/aiPlan'

const route = useRoute()
const router = useRouter()
const aiPlanStore = useAiPlanStore()

const loading = ref(true)
const plan = ref(null)
/** 'structured' | 'legacy' */
const viewMode = ref('legacy')

const planId = computed(() => route.params.id)

function isStructuredPayload(res) {
  return (
    res &&
    typeof res === 'object' &&
    Array.isArray(res.recipes) &&
    Array.isArray(res.coreIngredients)
  )
}

function unwrapLegacy(res) {
  if (!res) return null
  if (typeof res === 'string') {
    return { title: 'AI 药膳方案', summary: '', content: res }
  }
  return {
    title: res.title || res.name || 'AI 药膳方案',
    summary: res.summary || res.intro || res.description || '',
    content: res.content || res.text || res.body || '',
    coverUrl: res.coverUrl || res.cover || '',
    updatedAt: res.updatedAt || res.createdAt || '',
  }
}

async function load() {
  loading.value = true
  plan.value = null
  viewMode.value = 'legacy'

  const local = aiPlanStore.savedPlans.find((p) => p.planId === planId.value)
  if (local?.snapshot && isStructuredPayload(local.snapshot)) {
    plan.value = local.snapshot
    viewMode.value = 'structured'
    loading.value = false
    return
  }

  try {
    const data = await fetchAiPlanDetail(planId.value)
    if (isStructuredPayload(data)) {
      plan.value = data
      viewMode.value = 'structured'
    } else {
      plan.value = unwrapLegacy(data)
      viewMode.value = 'legacy'
    }
  } catch {
    plan.value = null
  } finally {
    loading.value = false
  }
}

watch(planId, load, { immediate: true })

function onBack() {
  router.back()
}
</script>

<template>
  <div class="page">
    <el-page-header @back="onBack" content="AI 方案详情" />

    <LoadingSkeleton v-if="loading" :rows="4" />

    <template v-else-if="plan && viewMode === 'structured'">
      <div class="page-card head-block">
        <p v-if="plan.symptomSummary" class="symptom-echo">
          <span class="symptom-echo__label">症状描述</span>
          {{ plan.symptomSummary }}
        </p>
        <p v-if="plan.constitutionApplied" class="symptom-echo">
          <span class="symptom-echo__label">参考体质</span>
          {{ plan.constitutionApplied }}
        </p>
      </div>
      <AiTherapyPlanView :plan="plan" />
    </template>

    <template v-else-if="plan && viewMode === 'legacy'">
      <div class="page-card hero" v-if="plan.coverUrl">
        <img class="hero__img" :src="plan.coverUrl" :alt="plan.title" loading="lazy" />
      </div>
      <div class="page-card body">
        <h1 class="title">{{ plan.title }}</h1>
        <p v-if="plan.summary" class="summary">{{ plan.summary }}</p>
        <pre v-if="plan.content" class="content">{{ plan.content }}</pre>
        <el-empty v-if="!plan.summary && !plan.content" description="暂无方案正文" />
      </div>
    </template>

    <div v-else class="page-card">
      <el-empty description="未找到该方案或后端未接入">
        <el-button type="primary" @click="router.push({ name: 'AIGenerate' })">去生成方案</el-button>
      </el-empty>
    </div>
  </div>
</template>

<style scoped>
.head-block {
  margin-top: var(--space-md);
}

.symptom-echo {
  margin: 0 0 var(--space-sm);
  font-size: var(--font-size-sm);
  line-height: 1.6;
  color: var(--color-text);
}

.symptom-echo:last-child {
  margin-bottom: 0;
}

.symptom-echo__label {
  display: inline-block;
  margin-right: 8px;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: var(--font-size-xs);
  color: var(--color-primary-dark);
  background: rgba(74, 124, 89, 0.1);
}

.hero {
  padding: 0;
  overflow: hidden;
  margin-top: var(--space-md);
}

.hero__img {
  display: block;
  width: 100%;
  max-height: 220px;
  object-fit: cover;
}

.body {
  margin-top: var(--space-md);
}

.title {
  margin: 0 0 var(--space-sm);
  font-size: var(--font-size-xl);
}

.summary {
  margin: 0 0 var(--space-md);
  color: var(--color-text-secondary);
  line-height: 1.6;
}

.content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', monospace;
  font-size: var(--font-size-sm);
  line-height: 1.6;
  color: var(--color-text);
}
</style>
