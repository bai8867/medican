/**
 * 推荐流「全部功效」时穿插排序：按菜谱主打功效（effectTags 首项）分桶后轮询取出，
 * 避免首屏几乎全是同一标签（如养胃）。
 */

function leadEfficacyKey(recipe) {
  const tags = recipe?.effectTags
  if (Array.isArray(tags) && tags.length) {
    const t = String(tags[0]).trim()
    if (t) return t
  }
  const head = String(recipe?.effect || '')
    .split(/[,，、;；]/)
    .map((s) => s.trim())
    .find(Boolean)
  return head || '其他'
}

/**
 * @param {object[]} recipes 已按业务规则排好序的列表（如收藏量）
 * @returns {object[]} 稳定轮询穿插后的新列表
 */
export function diversifyEfficacyRoundRobin(recipes) {
  if (!Array.isArray(recipes) || recipes.length <= 1) return recipes

  const bucketOrder = []
  const buckets = new Map()
  for (const r of recipes) {
    const k = leadEfficacyKey(r)
    if (!buckets.has(k)) {
      buckets.set(k, [])
      bucketOrder.push(k)
    }
    buckets.get(k).push(r)
  }

  const out = []
  let i = 0
  let more = true
  while (more) {
    more = false
    for (const k of bucketOrder) {
      const q = buckets.get(k)
      if (q && q[i]) {
        out.push(q[i])
        more = true
      }
    }
    i++
  }
  return out.length ? out : recipes
}
