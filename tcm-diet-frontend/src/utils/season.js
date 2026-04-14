/** 季节编码（与画像存储一致） */
export const SEASON_OPTIONS = [
  { code: 'spring', label: '春' },
  { code: 'summer', label: '夏' },
  { code: 'autumn', label: '秋' },
  { code: 'winter', label: '冬' },
]

/**
 * @param {number} month 1–12，默认当前月
 */
export function getCurrentSeasonCode(month = new Date().getMonth() + 1) {
  if (month >= 3 && month <= 5) return 'spring'
  if (month >= 6 && month <= 8) return 'summer'
  if (month >= 9 && month <= 11) return 'autumn'
  return 'winter'
}

export function getSeasonLabel(code) {
  return SEASON_OPTIONS.find((s) => s.code === code)?.label || '春'
}
