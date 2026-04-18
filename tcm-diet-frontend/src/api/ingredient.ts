import request from './request'
import type { IngredientRow } from './ingredientPreset'

export type { IngredientRow } from './ingredientPreset'
export { PRESET_INGREDIENTS } from './ingredientPreset'

export interface IngredientWritePayload {
  name: string
  efficacySummary?: string
  enabled?: boolean
}

export interface IngredientListParams extends Record<string, unknown> {
  enabled?: boolean | string | number
  keyword?: string
  page?: number
  page_size?: number
}

/** 列表接口返回（兼容 records / list 字段） */
export type IngredientListResult = Record<string, unknown> & {
  records: IngredientRow[]
  list: IngredientRow[]
}

export function fetchIngredientList(params?: IngredientListParams): Promise<IngredientListResult> {
  return request.get('/admin/ingredients', { params }).then((data: unknown) => {
    const d = (data ?? {}) as Record<string, unknown>
    const rawUnknown = d.records ?? d.list
    const raw = Array.isArray(rawUnknown) ? rawUnknown : []
    const rows: IngredientRow[] = raw.map((row: unknown) => {
      const r = row as Record<string, unknown>
      const en = r.enabled
      const enabled =
        en === false || en === 0 || String(en).toLowerCase() === 'false' ? false : true
      return {
        id: Number(r.id ?? 0),
        name: String(r.name ?? ''),
        efficacySummary: String(r.efficacySummary ?? r.note ?? ''),
        enabled,
      }
    })
    return { ...d, records: rows, list: rows } as IngredientListResult
  })
}

export function createIngredient(payload: IngredientWritePayload) {
  return request.post('/admin/ingredients', payload)
}

export function updateIngredient(id: number | string, payload: Partial<IngredientWritePayload>) {
  return request.put(`/admin/ingredients/${id}`, payload)
}

export function deleteIngredient(id: number | string) {
  return request.delete(`/admin/ingredients/${id}`)
}
