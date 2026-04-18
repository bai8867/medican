// @ts-nocheck
import { ref } from 'vue'

export function useRecommendPage({
  onSearchCommit,
  recipeSearchInput: recipeSearchInputExternal,
  recipeSearchKeyword: recipeSearchKeywordExternal,
} = {}) {
  const recipeSearchInput = recipeSearchInputExternal ?? ref('')
  const recipeSearchKeyword = recipeSearchKeywordExternal ?? ref('')

  function commitRecipeSearch() {
    const next = recipeSearchInput.value.trim()
    recipeSearchKeyword.value = next
    onSearchCommit?.(next)
  }

  function onSearchEnter(ev) {
    if (ev?.isComposing) return
    ev?.preventDefault?.()
    commitRecipeSearch()
  }

  function onRecipeSearchClear() {
    recipeSearchInput.value = ''
    if (!recipeSearchKeyword.value) return
    recipeSearchKeyword.value = ''
    onSearchCommit?.('')
  }

  return {
    recipeSearchInput,
    recipeSearchKeyword,
    commitRecipeSearch,
    onSearchEnter,
    onRecipeSearchClear,
  }
}
