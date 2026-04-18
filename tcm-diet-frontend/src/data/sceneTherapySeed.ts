// @ts-nocheck
/**
 * 校园十大场景食疗：与后端 `campus_scene.extra_json` 语义对齐，供 Mock 与离线降级。
 * @typedef {{ title: string, body: string, type: 'tea'|'tip' }} SceneTea
 * @typedef {{ id: number, name: string, icon: string, description: string, tagline: string, painTags: string[], tags: string[], forbidden: string[], ingredientInsight: string, teas: SceneTea[] }} SceneTherapySeed
 */

/** @type {SceneTherapySeed[]} */
export const SCENE_THERAPY_SEED = [
  {
    id: 1,
    name: '考前突击·熬夜救星',
    icon: '📚',
    description: '明天就要交作业或考试周，眼干脑胀还爆痘',
    tagline: '为挑灯夜战的你，守护最后一道防线',
    painTags: ['眼干眼涩', '疲劳乏力', '记忆力下降', '爆痘'],
    tags: ['明目', '养肝', '滋阴', '清热', '安神'],
    forbidden: ['熬夜后忌骤然大补，宜清淡平补', '少浓茶咖啡连喝', '忌高油夜宵加重胃火痤疮'],
    ingredientInsight:
      '枸杞滋补肝肾、益精明目；菊花散风清热、平肝明目。熬夜久视耗肝血、虚火上扰，宜清肝养阴柔润。',
    teas: [
      { title: '菊花枸杞茶', body: '菊花6g、枸杞10g，沸水冲泡8～10分钟温饮。', type: 'tea' },
      { title: '间歇护眼', body: '每25分钟远眺20秒并用力眨眼，减轻干眼。', type: 'tip' },
    ],
  },
  {
    id: 2,
    name: '体测来临·能量满格',
    icon: '🏃‍♀️',
    description: '800/1000米或力量训练后，肌肉酸胀体力见底',
    tagline: '科学补给，让身体告别体测后遗症',
    painTags: ['肌肉酸痛', '体力透支', '容易抽筋'],
    tags: ['补气', '温阳', '补血', '强筋骨'],
    forbidden: ['忌赛后立刻冰水猛灌', '忌空腹高强度训练'],
    ingredientInsight:
      '黄芪补气升阳；当归补血活血；生姜温中散寒。运动耗气伤津又易寒凝肌肉，宜温补气血、柔和舒展。',
    teas: [
      { title: '生姜红糖水', body: '生姜3片、红糖适量，温水小煮5分钟运动后少量补充。', type: 'tea' },
      { title: '小腿拉伸', body: '体测前后做腓肠肌拉伸各30秒，减少抽筋。', type: 'tip' },
    ],
  },
  {
    id: 3,
    name: '外卖胃·脾胃告急',
    icon: '🍔',
    description: '重油重盐外卖连轴转，胃胀反酸不消化',
    tagline: '告别重油重盐，给肠胃放个假',
    painTags: ['胃胀', '反酸', '不消化', '食欲不振'],
    tags: ['健脾', '和胃', '消食', '温中'],
    forbidden: ['忌暴饮暴食连环夜宵', '忌冰饮配重辣'],
    ingredientInsight:
      '山药健脾固肾；红枣补中益气；生姜温中止呕。外卖多油腻碍脾，宜补脾和胃、温运中焦。',
    teas: [
      { title: '陈皮山楂水', body: '陈皮5g、山楂6g，热水闷泡10分钟助消化。', type: 'tea' },
      { title: '饭后散步', body: '进食后慢走10～15分钟，助消化匠气。', type: 'tip' },
    ],
  },
  {
    id: 4,
    name: '换季敏感·免疫防线',
    icon: '🤧',
    description: '气温忽冷忽热，鼻塞咽痛易感冒',
    tagline: '换季不慌张，为身体建起防护墙',
    painTags: ['鼻塞流涕', '喉咙干痒', '容易感冒'],
    tags: ['解表', '益气', '温中', '润肺'],
    forbidden: ['忌汗出当风', '忌熬夜削弱卫气'],
    ingredientInsight:
      '生姜解表散寒；黄芪固表益气；银耳润肺养阴。风寒与燥邪并存时，宜扶正解表、润而不腻。',
    teas: [
      { title: '姜枣茶', body: '生姜3片、红枣3枚掰开，煮10分钟趁热小口喝。', type: 'tea' },
      { title: '盐水漱喉', body: '温淡盐水早晚漱喉10秒，缓解咽干痒勿吞。', type: 'tip' },
    ],
  },
  {
    id: 5,
    name: '鸭梨山大·情绪内耗',
    icon: '😫',
    description: '论文答辩、实习面试，焦虑胸闷睡不着',
    tagline: '疏肝解郁，让坏心情烟消云散',
    painTags: ['焦虑', '失眠', '胸闷', '想发脾气'],
    tags: ['疏肝', '解郁', '理气', '安神', '养心'],
    forbidden: ['忌酒精麻痹情绪', '忌通宵刷短视频加重焦虑'],
    ingredientInsight:
      '菊花清肝散热；酸枣仁养心安神；枸杞柔肝。肝气郁结易胸闷烦躁，宜清肝柔肝、宁心安神。',
    teas: [
      { title: '玫瑰佛手茶', body: '干玫瑰花5朵、佛手片3g，闷泡代茶，情志不畅时饮用。', type: 'tea' },
      { title: '478呼吸', body: '吸气4秒屏息7秒呼气8秒，循环3轮，缓解急性焦虑。', type: 'tip' },
    ],
  },
  {
    id: 6,
    name: '码字赶Due·屏幕眼',
    icon: '💻',
    description: '赶Due盯屏一整天，眼干肩硬还模糊',
    tagline: '你的电子设备眼，需要一场雨露滋润',
    painTags: ['眼睛干涩', '视力模糊', '肩颈僵硬'],
    tags: ['明目', '清热', '养肝', '滋阴'],
    forbidden: ['忌暗环境长时间盯屏', '忌用力揉眼'],
    ingredientInsight:
      '决明子清肝明目；菊花散风热；枸杞养肝血。久视伤血，肝开窍于目，宜清热与养阴并用。',
    teas: [
      { title: '菊花决明子茶', body: '菊花5g、决明子6g，沸水冲泡加盖5分钟，白天饮用。', type: 'tea' },
      { title: '肩颈米字操', body: '缓慢做颈部前后左右与旋转各5次，放松上斜方肌。', type: 'tip' },
    ],
  },
  {
    id: 7,
    name: '上火冒痘·炎症风暴',
    icon: '🔥',
    description: '熬夜烧烤后口疮龈肿，脸上痘痘此起彼伏',
    tagline: '清热降火，和痘痘说拜拜',
    painTags: ['口腔溃疡', '牙龈肿痛', '面部痤疮'],
    tags: ['清热', '解毒', '养阴'],
    forbidden: ['忌辛辣烧烤连环熬夜', '脾胃虚寒者不宜寒凉过量'],
    ingredientInsight:
      '绿豆清心胃热毒；菊花平肝胃火；决明子清肝。实火上炎可见口疮龈肿痤疮，宜清热不伤正。',
    teas: [
      { title: '绿豆甘草水', body: '绿豆30g煮开花后取汤，少加甘草2g调和，少量频服。', type: 'tea' },
      { title: '口腔护理', body: '进食后温水漱口，避免残屑刺激溃疡面。', type: 'tip' },
    ],
  },
  {
    id: 8,
    name: '手脚冰凉·畏寒星人',
    icon: '🥶',
    description: '教室空调冷、四肢不温，生理期更怕冷',
    tagline: '从内而外暖起来，不做冰美人',
    painTags: ['怕冷', '手脚不温', '生理期不适'],
    tags: ['温阳', '补血', '散寒', '活血', '暖宫'],
    forbidden: ['忌贪凉冰饮配露脐装', '经量多者活血方需个体评估'],
    ingredientInsight:
      '当归补血活血；生姜散寒温中；红糖温经散寒。阳虚血弱则四末不温，宜温阳补血并行。',
    teas: [
      { title: '红糖姜枣茶', body: '红糖、生姜3片、红枣3枚，小煮10分钟温热饮用。', type: 'tea' },
      { title: '温水泡脚', body: '40℃左右水泡脚至小腿10分钟，睡前促循环糖尿患者慎烫。', type: 'tip' },
    ],
  },
  {
    id: 9,
    name: '彻夜难眠·睡个好觉',
    icon: '🛌',
    description: '躺下脑子停不下来，多梦易醒白天更累',
    tagline: '安神助眠，愿你一夜好梦',
    painTags: ['入睡困难', '多梦易醒', '醒后疲惫'],
    tags: ['安神', '养心', '滋阴'],
    forbidden: ['忌睡前2小时剧烈运动', '忌睡前咖啡因与刺激刷剧'],
    ingredientInsight:
      '酸枣仁养心益肝安神；百合清心安神；银耳滋阴润燥。阴血不足则阳不入阴，宜养阴安神。',
    teas: [
      { title: '酸枣仁百合茶', body: '炒酸枣仁10g捣碎、百合10g，水煎或闷泡睡前1小时温饮。', type: 'tea' },
      { title: '固定起床时间', body: '即使失眠也固定起床，重建节律比补觉更有效。', type: 'tip' },
    ],
  },
  {
    id: 10,
    name: '健康减脂·科学掉秤',
    icon: '🏋️',
    description: '平台期焦虑，怕饿又怕反弹',
    tagline: '吃饱吃好，健康地瘦下来',
    painTags: ['易胖体质', '代谢缓慢', '减肥平台期'],
    tags: ['健脾', '清热', '养阴', '利湿'],
    forbidden: ['忌极端节食导致暴食反弹', '忌把代糖饮料当水'],
    ingredientInsight:
      '山药健脾助运；绿豆汤清湿热；银耳高纤维润燥。减重贵在健脾助运与适度清热利湿，忌蛮力克伐伤脾。',
    teas: [
      { title: '山药薏米水', body: '山药片15g、炒薏米12g，煮水代茶，油腻餐后更合适。', type: 'tea' },
      { title: '蛋白质优先', body: '每餐先吃豆鱼蛋瘦肉再吃主食，稳血糖更易坚持。', type: 'tip' },
    ],
  },
]

export type SceneTherapySeed = (typeof SCENE_THERAPY_SEED)[number]

export function getSceneSeed(id) {
  const n = Number(id)
  return SCENE_THERAPY_SEED.find((s) => s.id === n) || null
}

function hayForRecipe(r) {
  const tags = Array.isArray(r.effectTags) ? r.effectTags.join(',') : ''
  const sym = r.symptomTags || r.symptom_tags || ''
  return `${tags},${sym},${r.effect || ''},${r.summary || ''}`.toLowerCase()
}

function matchPainInRecipe(r, painTags) {
  const hay = hayForRecipe(r).replace(/,/g, '').replace(/，/g, '').replace(/、/g, '')
  const out = []
  for (const p of painTags) {
    const flat = String(p).replace(/,/g, '').replace(/，/g, '').replace(/、/g, '').toLowerCase()
    if (flat.length >= 2 && hay.includes(flat)) out.push(p)
  }
  return [...new Set(out)]
}

/**
 * 与本周日历菜品做场景匹配打分（痛点命中 + 功效标签重合）。
 * @param {object} r 药膳对象（含 effectTags / effect / summary 等）
 * @param {import('./sceneTherapySeed').SceneTherapySeed} scene
 */
export function scoreRecipeForScene(r, scene) {
  const matched = matchPainInRecipe(r, scene.painTags)
  let s = matched.length * 4
  const effArr = Array.isArray(r.effectTags) ? r.effectTags : []
  const blob = `${effArr.join('')}${r.effect || ''}${r.summary || ''}`
  for (const t of scene.tags) {
    if (!t) continue
    if (blob.includes(t)) s += 3
  }
  return { score: s, matched }
}

/**
 * @param {import('@/data/sceneTherapySeed').SceneTherapySeed} scene
 * @param {object[]} pool
 */
export function pickSceneRecipes(scene, pool) {
  const rows = pool
    .map((r) => ({ r, ...scoreRecipeForScene(r, scene) }))
    .filter((x) => x.score > 0)
    .sort(
      (a, b) =>
        b.score - a.score ||
        (Number(b.r.collectCount) || 0) - (Number(a.r.collectCount) || 0),
    )
  const out = rows.length ? rows : pool.map((r) => ({ r, score: 0, matched: [] }))
  return out.slice(0, 6)
}
