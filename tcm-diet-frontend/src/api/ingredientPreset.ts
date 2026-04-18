/**
 * 食材行与内置预设数据：无 HTTP / axios 依赖，可供 mock 与纯 Node 侧复用。
 */
export interface IngredientRow {
  id: number
  name: string
  efficacySummary: string
  enabled: boolean
}

export const PRESET_INGREDIENTS: IngredientRow[] = [
  { id: 1, name: '山药', efficacySummary: '补脾养胃、生津益肺', enabled: true },
  { id: 2, name: '枸杞', efficacySummary: '滋补肝肾、益精明目', enabled: true },
  { id: 3, name: '银耳', efficacySummary: '滋阴润肺、养胃生津', enabled: true },
  { id: 4, name: '莲子', efficacySummary: '补脾止泻、养心安神', enabled: true },
  { id: 5, name: '百合', efficacySummary: '养阴润肺、清心安神', enabled: true },
  { id: 6, name: '雪梨', efficacySummary: '润燥生津、清热化痰', enabled: true },
  { id: 7, name: '黄芪', efficacySummary: '补气固表、利水消肿', enabled: true },
  { id: 8, name: '当归', efficacySummary: '补血活血、调经止痛', enabled: true },
  { id: 9, name: '红枣', efficacySummary: '补中益气、养血安神', enabled: true },
  { id: 10, name: '冰糖', efficacySummary: '补中益气、和胃润肺', enabled: true },
  { id: 11, name: '演示禁用项', efficacySummary: '用于测试「仅禁用」筛选', enabled: false },
]
