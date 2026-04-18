// @ts-nocheck
/**
 * 药膳默认封面：SVG 示意图，文案为「药膳示意图（药膳名）」。
 * @param {string} [name]
 * @returns {string} data:image/svg+xml URL
 */
export function recipeSchematicCoverUrl(name) {
  const title = String(name || '药膳').trim() || '药膳'
  const label = `药膳示意图（${title}）`
  const fs = Math.max(14, Math.min(28, Math.round(520 / Math.max(label.length, 1))))
  const safe = label
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="480" height="360"><defs><linearGradient id="g" x1="0" x2="1" y1="0" y2="1"><stop offset="0%" stop-color="#ecfdf5"/><stop offset="100%" stop-color="#d8f3dc"/></linearGradient></defs><rect fill="url(#g)" width="100%" height="100%"/><text x="50%" y="50%" dominant-baseline="middle" text-anchor="middle" font-family="system-ui,-apple-system,Segoe UI,sans-serif" font-size="${fs}" fill="#2d6a4f">${safe}</text></svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}
