/**
 * 校园「本周药膳日历」Mock 载荷（与 /campus/weekly-calendar 对齐）
 */

export type MealSlot = 'breakfast' | 'lunch' | 'dinner' | 'midnightSnack'

export interface CalendarDish {
  id: string
  recipeId: string
  name: string
  window: string
  priceYuan: number
  supplyTimeLabel: string
  limited: boolean
  stopped: boolean
  stopReason?: string
  /** 禁忌人群与注意事项（「不适合我？」弹窗） */
  contraindicationNote: string
  /** 更适宜人群；空数组表示通用档口 */
  suitConstitutionLabels: string[]
  /** 明确不宜人群 */
  avoidConstitutionLabels: string[]
  /** 口味/食材标签，用于忌口偏好（辛辣、生冷等） */
  tags: string[]
}

export interface CalendarDay {
  date: string
  weekdayLabel: string
  weekdayIndex: number
  meals: Record<MealSlot, CalendarDish[]>
  /** 节气/运营配置的当日摘要条；无则前端不展示 */
  wellnessBanner?: string
}

export interface CalendarCanteenOption {
  id: string
  campusName: string
  name: string
}

export interface WeeklyCalendarPayload {
  published: boolean
  weekTitle: string
  /** 未发布时的说明 */
  estimatedPublishNote?: string
  canteens: CalendarCanteenOption[]
  days: CalendarDay[]
  /**
   * 学生端：接口返回未发布时，用本地 Mock 排期兜底展示，正式发布后该字段不出现。
   */
  studentDemoFallback?: boolean
}

const MEAL_ORDER: MealSlot[] = ['breakfast', 'lunch', 'dinner', 'midnightSnack']

function pad2(n: number) {
  return String(n).padStart(2, '0')
}

function formatYmd(d: Date) {
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

/** 本周一 00:00（本地时区） */
export function getMondayOfWeek(anchor = new Date()) {
  const d = new Date(anchor)
  d.setHours(0, 0, 0, 0)
  const day = d.getDay()
  const diff = day === 0 ? -6 : 1 - day
  d.setDate(d.getDate() + diff)
  return d
}

function addDays(d: Date, n: number) {
  const x = new Date(d)
  x.setDate(x.getDate() + n)
  return x
}

const WD_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

function baseTemplates(dayOffset: number, todayYmd: string): Record<MealSlot, CalendarDish[]> {
  const ymd = formatYmd(addDays(getMondayOfWeek(), dayOffset))
  const isToday = ymd === todayYmd

  /** 与 campus-diet-backend data.sql `recipe` 表 id 1–10 对齐，便于日历点击进入真实详情 */
  const breakfast: CalendarDish[] = [
    {
      id: `bf-${ymd}-1`,
      recipeId: '9',
      name: '山药红枣粥',
      window: '药膳粥品窗',
      priceYuan: 6,
      supplyTimeLabel: '07:00–09:00',
      limited: false,
      stopped: false,
      contraindicationNote: '湿盛中满者酌用',
      suitConstitutionLabels: ['气虚质', '痰湿质', '平和质'],
      avoidConstitutionLabels: [],
      tags: [],
    },
    {
      id: `bf-${ymd}-2`,
      recipeId: '10',
      name: '银耳雪梨汤',
      window: '轻养甜品窗',
      priceYuan: 8,
      supplyTimeLabel: '07:00–08:30',
      limited: true,
      stopped: false,
      contraindicationNote: '风寒咳嗽不宜',
      suitConstitutionLabels: ['阴虚质', '痰湿质', '平和质'],
      avoidConstitutionLabels: ['阳虚质'],
      tags: ['生冷'],
    },
  ]

  const lunch: CalendarDish[] = [
    {
      id: `lc-${ymd}-1`,
      recipeId: '2',
      name: '黄芪炖鸡',
      window: '二楼药膳炖品',
      priceYuan: 18,
      supplyTimeLabel: '11:00–13:00',
      limited: true,
      stopped: isToday,
      stopReason: isToday ? '当日食材黄芪临时缺货，已暂停供应，预计次日恢复。' : undefined,
      contraindicationNote: '实热证慎用',
      suitConstitutionLabels: ['气虚质', '阳虚质', '平和质'],
      avoidConstitutionLabels: ['湿热质'],
      tags: [],
    },
    {
      id: `lc-${ymd}-2`,
      recipeId: '6',
      name: '当归生姜羊肉汤',
      window: '二楼药膳炖品',
      priceYuan: 22,
      supplyTimeLabel: '11:20–12:40',
      limited: false,
      stopped: false,
      contraindicationNote: '湿热体质慎用',
      suitConstitutionLabels: ['阳虚质', '血瘀质', '气虚质'],
      avoidConstitutionLabels: ['湿热质', '阴虚质'],
      tags: [],
    },
    {
      id: `lc-${ymd}-3`,
      recipeId: '1',
      name: '枸杞菊花茶',
      window: '一楼例汤窗',
      priceYuan: 12,
      supplyTimeLabel: '11:00–12:30',
      limited: false,
      stopped: false,
      contraindicationNote: '脾胃虚寒者少饮',
      suitConstitutionLabels: ['阴虚质', '气郁质', '平和质'],
      avoidConstitutionLabels: [],
      tags: [],
    },
  ]

  const dinner: CalendarDish[] = [
    {
      id: `dn-${ymd}-1`,
      recipeId: '5',
      name: '绿豆汤',
      window: '一楼例汤窗',
      priceYuan: 15,
      supplyTimeLabel: '17:00–18:30',
      limited: false,
      stopped: false,
      contraindicationNote: '脾胃虚寒不宜多服',
      suitConstitutionLabels: ['湿热质', '阴虚质', '平和质'],
      avoidConstitutionLabels: ['阳虚质'],
      tags: [],
    },
    {
      id: `dn-${ymd}-2`,
      recipeId: '8',
      name: '菊花决明子茶',
      window: '二楼药膳炖品',
      priceYuan: 26,
      supplyTimeLabel: '17:10–19:00',
      limited: true,
      stopped: false,
      contraindicationNote: '低血压、腹泻慎用',
      suitConstitutionLabels: ['湿热质', '阴虚质', '平和质'],
      avoidConstitutionLabels: ['特禀质'],
      tags: ['辛辣'],
    },
  ]

  const midnightSnack: CalendarDish[] = [
    {
      id: `ms-${ymd}-1`,
      recipeId: '4',
      name: '酸枣仁茶',
      window: '夜宵轻食窗',
      priceYuan: 7,
      supplyTimeLabel: '21:00–22:30',
      limited: false,
      stopped: false,
      contraindicationNote: '腹泻者慎用',
      suitConstitutionLabels: ['阴虚质', '气郁质', '平和质'],
      avoidConstitutionLabels: [],
      tags: [],
    },
    {
      id: `ms-${ymd}-2`,
      recipeId: '7',
      name: '红糖姜枣茶',
      window: '夜宵轻食窗',
      priceYuan: 9,
      supplyTimeLabel: '21:00–22:00',
      limited: true,
      stopped: false,
      contraindicationNote: '糖尿病者酌量',
      suitConstitutionLabels: ['阳虚质', '血瘀质', '气虚质'],
      avoidConstitutionLabels: ['湿热质'],
      tags: ['辛辣'],
    },
  ]

  return { breakfast, lunch, dinner, midnightSnack }
}

function cloneMeals(src: Record<MealSlot, CalendarDish[]>): Record<MealSlot, CalendarDish[]> {
  const out = {} as Record<MealSlot, CalendarDish[]>
  for (const k of MEAL_ORDER) {
    out[k] = src[k].map((d) => ({ ...d }))
  }
  return out
}

/**
 * 后台未发布周历时：用与种子数据一致的本地排期展示；正式发布后应直接使用接口载荷。
 */
export function mergeUnpublishedWeeklyCalendarWithMock(
  api: WeeklyCalendarPayload,
  canteenId: string,
  now = new Date(),
): WeeklyCalendarPayload {
  if (api.published) {
    const { studentDemoFallback: _drop, ...rest } = api
    return rest
  }
  let mock = buildWeeklyCalendarMockPayload(canteenId, now)
  if (!mock.days?.length) {
    mock = buildWeeklyCalendarMockPayload('north-1', now)
  }
  const weekTitle =
    api.weekTitle && String(api.weekTitle).trim() ? String(api.weekTitle).trim() : mock.weekTitle
  return {
    ...mock,
    published: true,
    weekTitle,
    estimatedPublishNote: api.estimatedPublishNote ?? mock.estimatedPublishNote,
    canteens: api.canteens?.length ? api.canteens : mock.canteens,
    studentDemoFallback: true,
  }
}

export function buildWeeklyCalendarMockPayload(canteenId: string, now = new Date()): WeeklyCalendarPayload {
  const canteens: CalendarCanteenOption[] = [
    { id: 'north-1', campusName: '主校区', name: '北苑一食堂 · 药膳档' },
    { id: 'east-2', campusName: '主校区', name: '东苑二食堂 · 轻养窗' },
    {
      id: 'south-unpub',
      campusName: '南校区',
      name: '南苑二食堂（演示：日历未发布）',
    },
  ]

  if (canteenId === 'south-unpub') {
    return {
      published: false,
      weekTitle: '本周药膳日历',
      estimatedPublishNote: '本周菜单预计每周日 20:00 发布，届时将同步更新小程序与网页端。',
      canteens,
      days: [],
    }
  }

  const mon = getMondayOfWeek(now)
  const todayYmd = formatYmd(now)
  const days: CalendarDay[] = []

  for (let i = 0; i < 7; i++) {
    const d = addDays(mon, i)
    const ymd = formatYmd(d)
    let meals = cloneMeals(baseTemplates(i, todayYmd))
    if (canteenId === 'east-2') {
      meals = cloneMeals(baseTemplates(i, todayYmd))
      for (const slot of MEAL_ORDER) {
        meals[slot] = meals[slot].map((row, idx) => ({
          ...row,
          id: `${row.id}-e2`,
          window: row.window.replace('二楼', '东苑').replace('一楼', '东苑'),
          priceYuan: Math.max(5, row.priceYuan + idx - 1),
        }))
      }
    }
    days.push({
      date: ymd,
      weekdayLabel: WD_LABELS[i],
      weekdayIndex: i,
      meals,
      wellnessBanner:
        ymd === todayYmd ? '今日宜：滋阴润肺 · 忌：辛辣油腻' : undefined,
    })
  }

  const wk = `${mon.getMonth() + 1}月${mon.getDate()}日 — ${addDays(mon, 6).getMonth() + 1}月${addDays(mon, 6).getDate()}日`

  return {
    published: true,
    weekTitle: wk,
    canteens,
    days,
  }
}
