// @ts-nocheck
/**
 * PRD 5.4.3 — AI 食疗方案生成接口响应体（data 字段）
 * 与页面五块展示一一对应：推荐方、核心食材、生活调理、禁忌、原因解读
 */

import { MOCK_RECIPES } from '../data/recommendMock'

function pickRecipes(symptom, count = 3) {
  let pool = [...MOCK_RECIPES]
  if (/痘|口干|熬夜|燥|阴/.test(symptom)) {
    pool = pool.filter((r) => /百合|银耳|粥|羹/.test(r.name)).concat(pool)
  }
  if (/疲劳|气短|虚|不想说话/.test(symptom)) {
    pool = pool.filter((r) => /黄芪|鸡|山药/.test(r.name)).concat(pool)
  }
  if (/凉|怕冷|胃|食欲/.test(symptom)) {
    pool = pool.filter((r) => /姜|羊肉|粥/.test(r.name)).concat(pool)
  }
  const seen = new Set()
  const out = []
  for (const r of pool) {
    if (seen.has(r.id)) continue
    seen.add(r.id)
    out.push({
      recipeId: r.id,
      recipeName: r.name,
      matchReason: r.recommendReason || r.summary || '与当前症状方向相符的参考搭配。',
    })
    if (out.length >= count) break
  }
  while (out.length < count && out.length < MOCK_RECIPES.length) {
    const r = MOCK_RECIPES[out.length]
    out.push({
      recipeId: r.id,
      recipeName: r.name,
      matchReason: r.recommendReason || r.summary || '',
    })
  }
  return out.slice(0, count)
}

const CONSTITUTION_LABEL = {
  pinghe: '平和质',
  qixu: '气虚质',
  yangxu: '阳虚质',
  yinxu: '阴虚质',
  tanshi: '痰湿质',
  shire: '湿热质',
  xueyu: '血瘀质',
  qiyu: '气郁质',
  tebing: '特禀质',
}

/**
 * @param {{ symptom: string, constitution?: string }} input
 */
function nonEmptyStrings(arr) {
  if (!Array.isArray(arr)) return []
  return arr.map((s) => (typeof s === 'string' ? s.trim() : '')).filter(Boolean)
}

function mdLine(s) {
  return String(s || '')
    .replace(/\r\n/g, '\n')
    .replace(/\r/g, '\n')
    .replace(/\n+/g, ' ')
    .trim()
}

/**
 * 无大模型正文时的本地 Markdown，与后端 fallback 结构相近。
 * @param {object} plan
 */
export function buildTherapyMarkdownFallback(plan) {
  const symptom = mdLine(plan.symptomSummary) || '日常调养'
  const cons = plan.constitutionApplied ? mdLine(plan.constitutionApplied) : ''
  let md = `## 调养焦点\n\n结合您描述的「**${symptom}**」`
  if (cons) {
    md += `，并参考体质倾向「**${cons}**」`
  }
  md +=
    '，以下为**以食为先**的温和调理思路，便于日常执行（仅供养生参考，不替代诊疗）。\n\n## 食养思路与原则\n\n'
  md +=
    '- 以**清淡、温热适口、易消化**为主，避免过于滋腻或刺激。\n- 建议**每周 3～4 次**、单次适量，轮换食材与菜谱。\n\n'
  md += '## 推荐食材与意象\n\n'
  for (const c of plan.coreIngredients || []) {
    if (c?.name && c?.benefit) {
      md += `- **${mdLine(c.name)}**：${mdLine(c.benefit)}\n`
    }
  }
  md += '\n## 一周参考搭配\n\n下列与「推荐食疗方」一致，可点击菜名查看做法。\n\n'
  for (const r of plan.recipes || []) {
    if (r?.recipeName) {
      md += `- **${mdLine(r.recipeName)}**`
      if (r.matchReason) md += `：${mdLine(r.matchReason)}`
      md += '\n'
    }
  }
  md += '\n## 生活习惯配合\n\n'
  for (const t of plan.lifestyleAdvice || []) {
    if (t) md += `- ${mdLine(t)}\n`
  }
  md += '\n## 禁忌与就医提示\n\n'
  for (const t of plan.cautionNotes || []) {
    if (t) md += `- ${mdLine(t)}\n`
  }
  if (plan.rationale) {
    md += `\n## 简要说明\n\n${mdLine(plan.rationale)}\n`
  }
  md +=
    '\n---\n\n*本页内容为膳食与生活调养参考，**不替代**诊疗与处方；急症或症状持续加重请及时就医。*\n'
  return md
}

/**
 * 合并后端返回与本地兜底，保证五模块可展示；必要时标记通用方案。
 * @param {Record<string, unknown>|null|undefined} raw
 * @param {{ symptom: string, constitution?: string }} input
 */
export function normalizeAiTherapyPlanData(raw, input) {
  const fallback = buildAiTherapyPlanData(input)
  if (!raw || typeof raw !== 'object') {
    return { ...fallback, isGenericPlan: true }
  }

  const flagGeneric =
    Boolean(raw.isGenericPlan || raw.genericPlan || raw.fallback === true)

  const recipesIn = Array.isArray(raw.recipes) ? raw.recipes : []
  const recipes = recipesIn
    .map((r) => ({
      recipeId: r?.recipeId != null ? String(r.recipeId) : '',
      recipeName: typeof r?.recipeName === 'string' ? r.recipeName.trim() : '',
      matchReason:
        typeof r?.matchReason === 'string' && r.matchReason.trim()
          ? r.matchReason.trim()
          : '与当前调养方向相符的参考搭配。',
    }))
    .filter((r) => r.recipeId && r.recipeName)

  const coreIn = Array.isArray(raw.coreIngredients) ? raw.coreIngredients : []
  const coreIngredients = coreIn
    .map((c) => ({
      name: typeof c?.name === 'string' ? c.name.trim() : '',
      benefit: typeof c?.benefit === 'string' ? c.benefit.trim() : '',
    }))
    .filter((c) => c.name && c.benefit)

  let lifestyleAdvice = nonEmptyStrings(raw.lifestyleAdvice)
  let cautionNotes = nonEmptyStrings(raw.cautionNotes)
  const rationale =
    typeof raw.rationale === 'string' && raw.rationale.trim()
      ? raw.rationale.trim()
      : ''
  const disclaimer =
    typeof raw.disclaimer === 'string' && raw.disclaimer.trim()
      ? raw.disclaimer.trim()
      : fallback.disclaimer

  const incomplete =
    !recipes.length ||
    !coreIngredients.length ||
    !lifestyleAdvice.length ||
    !cautionNotes.length ||
    !rationale

  const therapyFromRaw =
    typeof raw.therapyRecommendMarkdown === 'string' && raw.therapyRecommendMarkdown.trim()
      ? raw.therapyRecommendMarkdown.trim()
      : ''

  const merged = {
    planId:
      typeof raw.planId === 'string' && raw.planId.trim()
        ? raw.planId.trim()
        : fallback.planId,
    symptomSummary:
      typeof raw.symptomSummary === 'string' && raw.symptomSummary.trim()
        ? raw.symptomSummary.trim()
        : fallback.symptomSummary,
    constitutionApplied:
      raw.constitutionApplied != null && String(raw.constitutionApplied).trim()
        ? String(raw.constitutionApplied).trim()
        : fallback.constitutionApplied,
    recipes: recipes.length ? recipes : fallback.recipes,
    coreIngredients: coreIngredients.length ? coreIngredients : fallback.coreIngredients,
    lifestyleAdvice: lifestyleAdvice.length ? lifestyleAdvice : fallback.lifestyleAdvice,
    cautionNotes: cautionNotes.length ? cautionNotes : fallback.cautionNotes,
    rationale: rationale || fallback.rationale,
    disclaimer,
    therapyRecommendMarkdown: '',
    isGenericPlan: flagGeneric || incomplete,
  }

  merged.therapyRecommendMarkdown = therapyFromRaw || buildTherapyMarkdownFallback(merged)

  return merged
}

export function buildAiTherapyPlanData(input) {
  const symptom = (input.symptom || '').trim() || '日常调养'
  const code = input.constitution || ''
  const constitutionLabel = code ? CONSTITUTION_LABEL[code] || code : null

  const recipes = pickRecipes(symptom, 3)

  const coreIngredients = [
    { name: '山药', benefit: '健脾益肺、补肾固精，适合作为温和食补基底。' },
    { name: '薏苡仁', benefit: '渗湿健脾；体寒者可选用炒制薏米以减凉性。' },
    { name: '百合', benefit: '养阴润肺、清心安神，对口干舌燥、睡眠不佳有辅助调理意象。' },
  ]
  const lifestyleAdvice = [
    '尽量固定作息，熬夜后次日补一小段午睡，避免连续高强度用脑。',
    '少量多次饮水，温水为宜；减少过甜饮料以免加重口干与皮肤出油。',
    '每周 3–4 次轻松有氧运动（快走、八段锦），以微汗为度即可。',
  ]
  const cautionNotes = [
    '本方案为膳食参考，不能替代诊疗；如有持续发热、胸痛、严重消瘦等请及时就医。',
    '孕妇、哺乳期、慢性肾病或正在服药者，食用药膳前请咨询医师或药师。',
    '对列出的食材过敏者请勿食用相应菜品。',
  ]
  const rationale = `结合您描述的主要不适「${symptom}」${
    constitutionLabel ? `，并参考体质倾向「${constitutionLabel}」` : ''
  }，从「清补兼顾、少刺激、易执行」的原则给出食疗与生活建议。若症状加重或迁延不愈，建议到校医院或正规医疗机构进一步评估。`

  const base = {
    planId: `plan-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    symptomSummary: symptom,
    constitutionApplied: constitutionLabel,
    recipes,
    coreIngredients,
    lifestyleAdvice,
    cautionNotes,
    rationale,
    disclaimer:
      '内容由算法生成并经合规提示，仅供健康教育参考，不构成医疗诊断或治疗方案。',
    therapyRecommendMarkdown: '',
  }

  const names = recipes.map((r) => r.recipeName).filter(Boolean)
  const demoMd = `## 调养焦点\n\n针对您提到的「**${symptom}**」${
    constitutionLabel ? `，并结合「**${constitutionLabel}**」体质参考` : ''
  }，下面给出**可执行的食养顺序**：先调作息与饮水，再用温和药膳做「慢调理」。\n\n## 食养思路与原则\n\n- **清补兼顾**：少油炸辛辣，多蒸煮炖；以「微汗、胃肠舒适」为度。\n- **频次**：药膳建议 **每周 3～4 次**，与家常饭搭配即可，不必天天重样。\n- **观察反应**：若出现胃胀、腹泻或过敏，先停用相关食材并咨询医师。\n\n## 一周参考搭配\n\n${
    names.length
      ? names.map((n) => `- **${n}**：与当前调养方向呼应，详见下方菜谱卡片。\n`).join('')
      : '- 可从平台「药膳推荐」中自选清淡粥羹类作为基底。\n'
  }\n## 简易食例与做法提示\n\n1. **粥/羹类**：食材切小块，冷水下米，小火熬至绵软；百合、山药、银耳宜后下以免过烂。\n2. **茶饮类**：玫瑰花、陈皮等**不宜久煮**，焖泡 5～8 分钟即可。\n\n## 生活习惯配合\n\n${lifestyleAdvice.map((t) => `- ${t}`).join('\n')}\n\n## 禁忌与就医提示\n\n${cautionNotes.map((t) => `- ${t}`).join('\n')}\n\n---\n\n*以上为演示用 Markdown 结构；正式环境由大模型按同一结构生成。*\n`

  base.therapyRecommendMarkdown = demoMd
  return base
}
