const ING_GROUP_LABEL = {
  main: '主料',
  aux: '辅料',
  seasoning: '调料',
}

/** 根据名称推断分组（Mock / 后端未分组时使用） */
function inferIngredientCategory(name) {
  if (!name) return 'main'
  const n = String(name)
  if (
    /盐|冰糖|白糖|红糖|蜂蜜|酱油|生抽|老抽|醋|料酒|蚝油|花椒|胡椒|味精|鸡精|淀粉|香油|小葱|大葱|蒜末|香菜|姜片|生姜/.test(
      n,
    )
  ) {
    return 'seasoning'
  }
  if (
    /黄芪|当归|茯苓|薏苡仁|薏米|川贝|沙参|玉竹|枸杞|枸杞子|菊花|陈皮|山楂|麦芽|酸枣仁|百合|银耳|莲子|芡实|五指毛桃|茯苓粉|玫瑰花|佛手|杭白菊|炒麦芽|北沙参|干银耳|鲜百合|川贝母|红枣|铁棍山药|山药/.test(
      n,
    )
  ) {
    return 'aux'
  }
  return 'main'
}

export function normalizeIngredientGroups(recipe) {
  if (recipe.ingredientGroups?.length) {
    return recipe.ingredientGroups
      .map((g) => ({
        key: g.key || g.category || 'main',
        label: g.label || ING_GROUP_LABEL[g.key] || g.category || '食材',
        items: g.items || [],
      }))
      .filter((g) => g.items.length)
  }
  const items = recipe.ingredients || []
  const buckets = { main: [], aux: [], seasoning: [] }
  for (const it of items) {
    const cat = it.category || inferIngredientCategory(it.name)
    const key = buckets[cat] ? cat : 'main'
    buckets[key].push(it)
  }
  return (['main', 'aux', 'seasoning'])
    .map((key) => ({
      key,
      label: ING_GROUP_LABEL[key],
      items: buckets[key],
    }))
    .filter((g) => g.items.length)
}
