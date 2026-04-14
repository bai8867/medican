/**
 * 体质测评问卷（PRD 5.1.2.2：9 题分三组）
 * 计分规则（前端 Mock / 可与后端对齐）：第 i 题得分累加到该题主属体质，取总分最高者；
 * 同分按偏颇质优先顺序打破平局，平和质最后。
 */

export const SCORE_OPTIONS = [
  { score: 1, label: '没有（根本不）' },
  { score: 2, label: '很少（有一点）' },
  { score: 3, label: '有时（有些）' },
  { score: 4, label: '经常（相当）' },
  { score: 5, label: '总是（非常）' },
]

/**
 * 同分时优先判定的体质顺序（偏颇质在前，平和质在后）。
 * 与《中医体质分类与判定》常见实现一致：并列最高分时取排序更靠前的偏颇质，平和质最后。
 * 若贵司 PRD 要求「并列时平和质优先于偏颇质」，需将 pinghe 提前到 TIE_BREAK_ORDER 前列。
 */
const TIE_BREAK_ORDER = [
  'qixu',
  'yangxu',
  'yinxu',
  'tanshi',
  'shire',
  'xueyu',
  'qiyu',
  'tebing',
  'pinghe',
]

/** 每题主属体质（与 PRD 5.1.2.2 九题一一对应） */
export const CONSTITUTION_QUESTIONS = [
  {
    id: 1,
    group: 0,
    text: '您容易疲乏、气短、懒言吗？',
    targetCode: 'qixu',
  },
  {
    id: 2,
    group: 0,
    text: '您手足不温、畏寒怕冷吗？',
    targetCode: 'yangxu',
  },
  {
    id: 3,
    group: 0,
    text: '您感到口干咽燥、手足心热吗？',
    targetCode: 'yinxu',
  },
  {
    id: 4,
    group: 1,
    text: '您身体沉重困倦、腹部肥满松软吗？',
    targetCode: 'tanshi',
  },
  {
    id: 5,
    group: 1,
    text: '您面部油腻、口苦或舌苔黄腻吗？',
    targetCode: 'shire',
  },
  {
    id: 6,
    group: 1,
    text: '您肤色晦暗或易出现瘀斑、健忘吗？',
    targetCode: 'xueyu',
  },
  {
    id: 7,
    group: 2,
    text: '您情绪低落、胸闷或喜叹息吗？',
    targetCode: 'qiyu',
  },
  {
    id: 8,
    group: 2,
    text: '您没有感冒也常鼻塞、喷嚏、易过敏吗？',
    targetCode: 'tebing',
  },
  {
    id: 9,
    group: 2,
    text: '您精力充沛、睡眠良好、适应外界变化吗？',
    targetCode: 'pinghe',
  },
]

/** 各体质一句话解读（结果页展示） */
export const CONSTITUTION_BRIEF = {
  pinghe: '阴阳气血调和，体态适中，对四时适应力强。建议保持规律作息与均衡饮食。',
  qixu: '元气不足，易疲乏气短。宜食健脾益气之品，避免过度劳累与生冷。',
  yangxu: '阳气偏衰，畏寒肢冷。宜温补，少食生冷瓜果，注意保暖。',
  yinxu: '阴液亏少，易口干内热。宜甘凉滋润，少食辛辣煎炸，忌熬夜。',
  tanshi: '痰湿内盛，多见困重与黏腻。宜清淡利湿，少甜腻油腻，配合运动。',
  shire: '湿热内蕴，多见油腻与口苦。宜清热化湿，忌酒辣肥甘。',
  xueyu: '血行不畅，易见瘀滞与晦暗。宜活血理气，保持活动与情绪舒畅。',
  qiyu: '气机郁滞，情志不畅。宜疏肝理气，多户外与社交，少郁闷。',
  tebing: '先天禀赋特异，易过敏。宜查明诱因、谨慎尝试新食材，遵医嘱。',
}

export function getQuestionsByGroup(groupIndex) {
  return CONSTITUTION_QUESTIONS.filter((q) => q.group === groupIndex)
}

/**
 * 平和质「转化分阈值」类简化规则（适配本问卷：每题 1–5 分、单题对应单体质）。
 * 当八种偏颇题均未达到「明显倾向」上限，且第 9 题（平和）达到「平和优势」下限时，
 * 主导体质定为平和质，避免仅凭并列顺序误判为偏颇质。
 * 与《中医体质分类与判定》中「平和质需结合总体平和、各偏颇未突出」的精神对齐，便于与后端转化分逻辑对照。
 */
export const PINGHE_DOMINANT_THRESHOLDS = {
  /** 第 9 题得分 ≥ 此值视为平和倾向足够（默认 4 = 经常/相当及以上） */
  minPingheQuestionScore: 4,
  /** 第 1–8 题（偏颇）单项得分 ≤ 此值视为该维未达「明显偏颇」（默认 3 = 不超过「有时」） */
  maxBiasedQuestionScore: 3,
}

/**
 * 9 题均已作答且每项为 1–5 时，判断是否满足平和主导特殊条件。
 * @param {number[]} answers
 * @param {{ minPingheQuestionScore?: number, maxBiasedQuestionScore?: number }} [thresholds]
 */
export function isPingheDominantBySpecialRule(answers, thresholds = PINGHE_DOMINANT_THRESHOLDS) {
  if (!Array.isArray(answers) || answers.length !== 9) return false
  const minP = thresholds.minPingheQuestionScore ?? PINGHE_DOMINANT_THRESHOLDS.minPingheQuestionScore
  const maxB = thresholds.maxBiasedQuestionScore ?? PINGHE_DOMINANT_THRESHOLDS.maxBiasedQuestionScore
  for (let i = 0; i < 9; i++) {
    const v = answers[i]
    if (v == null || !Number.isFinite(v) || v < 1 || v > 5) return false
  }
  if (answers[8] < minP) return false
  for (let i = 0; i < 8; i++) {
    if (answers[i] > maxB) return false
  }
  return true
}

function buildConstitutionScores(answers) {
  const scores = Object.fromEntries(TIE_BREAK_ORDER.map((c) => [c, 0]))
  for (let i = 0; i < CONSTITUTION_QUESTIONS.length; i++) {
    const v = answers[i]
    if (v == null || v < 1 || v > 5) continue
    const code = CONSTITUTION_QUESTIONS[i].targetCode
    scores[code] = (scores[code] || 0) + v
  }
  return scores
}

/**
 * @param {number[]} answers 长度 9，元素为 1–5 或 null
 * @param {{ pingheThresholds?: typeof PINGHE_DOMINANT_THRESHOLDS }} [options] 与后端对齐时可传入转化分等价阈值
 * @returns {{
 *   constitutionCode: string,
 *   scores: Record<string, number>,
 *   meta: { pingheDominantBySpecialRule: boolean }
 * }}
 */
export function computeMockConstitution(answers, options) {
  const scores = buildConstitutionScores(answers)
  const thresholds = options?.pingheThresholds ?? PINGHE_DOMINANT_THRESHOLDS
  const bySpecial = isPingheDominantBySpecialRule(answers, thresholds)
  if (bySpecial) {
    return {
      constitutionCode: 'pinghe',
      scores,
      meta: { pingheDominantBySpecialRule: true },
    }
  }
  const max = Math.max(...TIE_BREAK_ORDER.map((c) => scores[c] || 0))
  const constitutionCode = TIE_BREAK_ORDER.find((c) => (scores[c] || 0) === max) || 'pinghe'
  return {
    constitutionCode,
    scores,
    meta: { pingheDominantBySpecialRule: false },
  }
}
