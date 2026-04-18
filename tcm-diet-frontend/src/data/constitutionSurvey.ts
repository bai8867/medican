// @ts-nocheck
/**
 * 体质测评问卷（研究版）：标准核心题 + 校园场景题。
 * 保留 legacy 9 题规则作为 A/B 基线比较。
 */

export const SCORE_OPTIONS = [
  { score: 1, label: '没有（根本不）' },
  { score: 2, label: '很少（有一点）' },
  { score: 3, label: '有时（有些）' },
  { score: 4, label: '经常（相当）' },
  { score: 5, label: '总是（非常）' },
]

const CONSTITUTION_CODES = [
  'pinghe',
  'qixu',
  'yangxu',
  'yinxu',
  'tanshi',
  'shire',
  'xueyu',
  'qiyu',
  'tebing',
]

/** 旧版 9 题，保留用于对照基线。 */
export const LEGACY_CONSTITUTION_QUESTIONS = [
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

const LEGACY_TIE_BREAK_ORDER = [
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

export const QUESTION_BANK_VERSION = 'v2-research-hybrid'

const DIMENSION_THRESHOLD = {
  pingheMin: 60,
  biasedMaxForPinghe: 40,
  secondaryMin: 45,
  secondaryDeltaMax: 30,
}

function makeQuestion(id, targetCode, text, source, symptomDomain, direction = 'direct') {
  return {
    id,
    group: Math.floor((id - 1) / 9),
    targetCode,
    text,
    source,
    symptomDomain,
    direction,
    weight: source === 'core' ? 1.0 : 0.9,
  }
}

export const RESEARCH_CONSTITUTION_QUESTIONS = [
  // 平和质
  makeQuestion(1, 'pinghe', '您精力充沛、体力恢复较快吗？', 'core', 'vitality'),
  makeQuestion(2, 'pinghe', '您睡眠质量稳定、醒后精神较好吗？', 'core', 'sleep'),
  makeQuestion(3, 'pinghe', '您对季节变换与环境变化适应较好吗？', 'core', 'adaptation'),
  makeQuestion(4, 'pinghe', '考试周或赶作业期间，您整体状态仍较平稳吗？', 'scene', 'stress_response'),
  makeQuestion(5, 'pinghe', '连续一周在食堂/外卖间切换时，您消化反应依然稳定吗？', 'scene', 'digestion_stability'),

  // 气虚质
  makeQuestion(6, 'qixu', '您容易疲乏、气短、说话无力吗？', 'core', 'qi_deficiency'),
  makeQuestion(7, 'qixu', '您稍微活动后就容易出汗或乏力吗？', 'core', 'exercise_tolerance'),
  makeQuestion(8, 'qixu', '您容易反复感冒、恢复偏慢吗？', 'core', 'defense_qi'),
  makeQuestion(9, 'qixu', '上楼到教室后，您常感觉明显气短吗？', 'scene', 'campus_stairs'),
  makeQuestion(10, 'qixu', '期末周连续复习几天后，您常出现明显倦怠吗？', 'scene', 'study_load'),

  // 阳虚质
  makeQuestion(11, 'yangxu', '您手足不温、怕冷明显吗？', 'core', 'cold_intolerance'),
  makeQuestion(12, 'yangxu', '您偏好热饮热食，受凉后不适明显吗？', 'core', 'warm_preference'),
  makeQuestion(13, 'yangxu', '您清晨易腹泻或小便清长吗？', 'core', 'yang_deficiency'),
  makeQuestion(14, 'yangxu', '冬天教室空调不足时，您常明显畏寒吗？', 'scene', 'winter_classroom'),
  makeQuestion(15, 'yangxu', '淋雨或夜间受凉后，您常出现不适吗？', 'scene', 'rain_cold_exposure'),

  // 阴虚质
  makeQuestion(16, 'yinxu', '您常感口干咽燥、想喝水吗？', 'core', 'fluid_deficiency'),
  makeQuestion(17, 'yinxu', '您手足心发热或午后潮热吗？', 'core', 'internal_heat'),
  makeQuestion(18, 'yinxu', '您容易心烦、睡眠偏浅吗？', 'core', 'yin_sleep'),
  makeQuestion(19, 'yinxu', '熬夜学习后，您次日口干咽燥明显吗？', 'scene', 'late_night'),
  makeQuestion(20, 'yinxu', '连续食用辛辣烧烤后，您常出现“上火”感吗？', 'scene', 'spicy_trigger'),

  // 痰湿质
  makeQuestion(21, 'tanshi', '您感到身体沉重困倦、头身不清爽吗？', 'core', 'heaviness'),
  makeQuestion(22, 'tanshi', '您腹部肥满松软、痰多或口黏吗？', 'core', 'phlegm_damp'),
  makeQuestion(23, 'tanshi', '您运动后出汗较黏、恢复偏慢吗？', 'core', 'metabolic_damp'),
  makeQuestion(24, 'tanshi', '连续外卖高油饮食后，您更易困倦乏力吗？', 'scene', 'takeout_oily'),
  makeQuestion(25, 'tanshi', '久坐上课一天后，您常感身重懒动吗？', 'scene', 'sedentary_day'),

  // 湿热质
  makeQuestion(26, 'shire', '您面部油腻、口苦口黏或易生痤疮吗？', 'core', 'damp_heat_skin'),
  makeQuestion(27, 'shire', '您大便黏滞不爽或小便短黄吗？', 'core', 'damp_heat_excretion'),
  makeQuestion(28, 'shire', '您常有心烦、口气偏重吗？', 'core', 'damp_heat_internal'),
  makeQuestion(29, 'shire', '夏季闷热时，您更易长痘和口苦吗？', 'scene', 'summer_humidity'),
  makeQuestion(30, 'shire', '连续辛辣油炸后，您不适加重明显吗？', 'scene', 'diet_heat_trigger'),

  // 血瘀质
  makeQuestion(31, 'xueyu', '您肤色晦暗或易出现瘀斑吗？', 'core', 'stasis_color'),
  makeQuestion(32, 'xueyu', '您常有固定部位刺痛或痛经吗？', 'core', 'stasis_pain'),
  makeQuestion(33, 'xueyu', '您记忆力下降、健忘较明显吗？', 'core', 'stasis_cognition'),
  makeQuestion(34, 'xueyu', '久坐后肩颈或腰背固定痛点更明显吗？', 'scene', 'sedentary_pain'),
  makeQuestion(35, 'xueyu', '熬夜后面色暗沉恢复较慢吗？', 'scene', 'late_night_recovery'),

  // 气郁质
  makeQuestion(36, 'qiyu', '您常情绪低落、胸闷或喜叹息吗？', 'core', 'mood_stagnation'),
  makeQuestion(37, 'qiyu', '您紧张时易胃口差、腹胀或咽部异物感吗？', 'core', 'stress_digestive'),
  makeQuestion(38, 'qiyu', '您对压力事件恢复较慢吗？', 'core', 'stress_recovery'),
  makeQuestion(39, 'qiyu', '课程/社团冲突增多时，您情绪波动明显吗？', 'scene', 'schedule_conflict'),
  makeQuestion(40, 'qiyu', '在宿舍人际压力下，您易感胸闷烦躁吗？', 'scene', 'dorm_social'),

  // 特禀质
  makeQuestion(41, 'tebing', '您常有过敏反应（鼻炎、皮疹、食物不耐受）吗？', 'core', 'allergy_history'),
  makeQuestion(42, 'tebing', '季节交替时，您过敏症状易发作吗？', 'core', 'allergy_seasonal'),
  makeQuestion(43, 'tebing', '接触尘螨/花粉后，您反应明显吗？', 'core', 'allergy_trigger'),
  makeQuestion(44, 'tebing', '换宿舍或打扫后，您鼻塞喷嚏加重吗？', 'scene', 'dorm_dust'),
  makeQuestion(45, 'tebing', '尝试新饮料/零食后，您更易出现不适吗？', 'scene', 'new_food_trigger'),
]

export function getTotalQuestionCount() {
  return RESEARCH_CONSTITUTION_QUESTIONS.length
}

export function getQuestionGroupCount() {
  return Math.max(...RESEARCH_CONSTITUTION_QUESTIONS.map((q) => q.group)) + 1
}

export function getQuestionsByGroup(groupIndex) {
  return RESEARCH_CONSTITUTION_QUESTIONS.filter((q) => q.group === groupIndex)
}

export function buildNeutralAnswers(score = 3) {
  return Array.from({ length: getTotalQuestionCount() }, () => score)
}

export function scoreQuestionByDirection(score, direction) {
  if (!Number.isFinite(score)) return NaN
  if (direction === 'reverse') return 6 - score
  return score
}

function createDimensionAcc() {
  const entries = Object.fromEntries(CONSTITUTION_CODES.map((code) => [code, 0]))
  return { ...entries }
}

function computeTransformedScore(rawScore, minScore, maxScore) {
  if (!Number.isFinite(rawScore) || maxScore <= minScore) return 0
  const value = ((rawScore - minScore) / (maxScore - minScore)) * 100
  return Math.max(0, Math.min(100, Number(value.toFixed(2))))
}

function roundTwo(v) {
  return Number(Number(v).toFixed(2))
}

function normalizeAnswers(answers) {
  if (!Array.isArray(answers)) return []
  return answers.map((v) => {
    const n = Number(v)
    return Number.isFinite(n) ? n : null
  })
}

function sortByScore(scoreMap) {
  return Object.entries(scoreMap).sort((a, b) => b[1] - a[1])
}

export function computeLegacyConstitutionFromNineAnswers(answers) {
  if (!Array.isArray(answers) || answers.length !== 9) {
    throw new Error('Legacy 规则要求 9 题答案')
  }
  const scores = Object.fromEntries(LEGACY_TIE_BREAK_ORDER.map((c) => [c, 0]))
  for (let i = 0; i < LEGACY_CONSTITUTION_QUESTIONS.length; i++) {
    const v = Number(answers[i])
    if (!Number.isFinite(v) || v < 1 || v > 5) continue
    const code = LEGACY_CONSTITUTION_QUESTIONS[i].targetCode
    scores[code] = (scores[code] || 0) + v
  }
  const max = Math.max(...LEGACY_TIE_BREAK_ORDER.map((c) => scores[c] || 0))
  const constitutionCode = LEGACY_TIE_BREAK_ORDER.find((c) => (scores[c] || 0) === max) || 'pinghe'
  return { constitutionCode, scores }
}

function evaluateResearchConstitution(answers) {
  if (!Array.isArray(answers) || answers.length !== RESEARCH_CONSTITUTION_QUESTIONS.length) {
    throw new Error(`研究版问卷需提交 ${RESEARCH_CONSTITUTION_QUESTIONS.length} 题`)
  }
  const rawScores = createDimensionAcc()
  const weightSum = createDimensionAcc()
  for (const q of RESEARCH_CONSTITUTION_QUESTIONS) {
    const raw = answers[q.id - 1]
    if (!Number.isFinite(raw) || raw < 1 || raw > 5) {
      throw new Error(`第 ${q.id} 题得分需在 1-5`)
    }
    const scored = scoreQuestionByDirection(raw, q.direction)
    const weighted = scored * q.weight
    rawScores[q.targetCode] += weighted
    weightSum[q.targetCode] += q.weight
  }
  const transformedScores = createDimensionAcc()
  for (const code of CONSTITUTION_CODES) {
    const minScore = weightSum[code] * 1
    const maxScore = weightSum[code] * 5
    transformedScores[code] = computeTransformedScore(rawScores[code], minScore, maxScore)
  }

  const ruleTrace = []
  const pingheScore = transformedScores.pinghe
  const maxBiasedScore = Math.max(...CONSTITUTION_CODES.filter((c) => c !== 'pinghe').map((c) => transformedScores[c]))
  let constitutionCode = 'pinghe'
  if (pingheScore >= DIMENSION_THRESHOLD.pingheMin && maxBiasedScore < DIMENSION_THRESHOLD.biasedMaxForPinghe) {
    constitutionCode = 'pinghe'
    ruleTrace.push('pinghe-gate:hit')
  } else {
    const sorted = sortByScore(transformedScores)
    constitutionCode = sorted[0][0]
    ruleTrace.push('pinghe-gate:miss')
  }
  const sortedAll = sortByScore(transformedScores)
  const topScore = sortedAll[0][1]
  const secondScore = sortedAll[1]?.[1] ?? topScore
  const delta = topScore - secondScore
  let confidence = 0.5 + delta / 50
  if (topScore < 45) confidence -= 0.08
  confidence = Math.max(0.45, Math.min(0.98, roundTwo(confidence)))

  const secondaryCodes = sortedAll
    .filter(([code, score]) => code !== constitutionCode)
    .filter(([, score]) => score >= DIMENSION_THRESHOLD.secondaryMin && topScore - score <= DIMENSION_THRESHOLD.secondaryDeltaMax)
    .slice(0, 2)
    .map(([code]) => code)

  if (secondaryCodes.length > 0) {
    ruleTrace.push(`secondary:${secondaryCodes.join(',')}`)
  }

  const nineAnswers = answers.slice(0, 9)
  const legacyComparator = computeLegacyConstitutionFromNineAnswers(nineAnswers)

  return {
    constitutionCode,
    scores: Object.fromEntries(CONSTITUTION_CODES.map((code) => [code, roundTwo(rawScores[code])])),
    meta: {
      questionVersion: QUESTION_BANK_VERSION,
      transformedScores,
      confidence,
      secondaryCodes,
      ruleTrace,
      legacyComparator,
    },
  }
}

/**
 * @param {(number | null)[]} answers
 * @param {{ questionVersion?: string }} [options]
 */
export function computeMockConstitution(answers, options = {}) {
  const normalized = normalizeAnswers(answers)
  const version = options.questionVersion || QUESTION_BANK_VERSION
  if (version === 'legacy-v1' || normalized.length === 9) {
    const legacy = computeLegacyConstitutionFromNineAnswers(normalized)
    return {
      constitutionCode: legacy.constitutionCode,
      scores: legacy.scores,
      meta: {
        questionVersion: 'legacy-v1',
        confidence: 0.55,
        secondaryCodes: [],
        ruleTrace: ['legacy-nine'],
      },
    }
  }
  return evaluateResearchConstitution(normalized)
}
