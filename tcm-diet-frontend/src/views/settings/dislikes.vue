<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  showToast,
  showConfirmDialog,
  NavBar as VanNavBar,
  CellGroup as VanCellGroup,
  Cell as VanCell,
  Button as VanButton,
  Empty as VanEmpty,
} from 'vant'
import { loadDismissedRecipeIds, removeDismissedRecipeId } from '@/utils/recommendDismiss'
import { removeDislikedRecipe } from '@/api/userSettings'
import { getUnifiedRecipeMockStore } from '@/data/unifiedRecipeMockStore.js'

const router = useRouter()

type Row = { recipeId: string; title: string }

const rows = ref<Row[]>([])
const loading = ref(false)

const isEmpty = computed(() => !loading.value && rows.value.length === 0)

function resolveTitle(recipeId: string): string {
  const pool = getUnifiedRecipeMockStore()
  const hit = pool.find((r) => String(r.id) === String(recipeId))
  return hit?.name ? String(hit.name) : `药膳 #${recipeId}`
}

async function load() {
  loading.value = true
  try {
    const ids = loadDismissedRecipeIds()
    rows.value = ids.map((recipeId) => ({
      recipeId,
      title: resolveTitle(recipeId),
    }))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
})

async function onRemove(row: Row) {
  try {
    await showConfirmDialog({
      title: '移出不感兴趣',
      message: `将「${row.title}」重新加入推荐候选，确定吗？`,
    })
  } catch {
    return
  }
  try {
    await removeDislikedRecipe(row.recipeId)
  } catch {
    /* 非 Mock 时接口失败仍可本地移出 */
  }
  removeDismissedRecipeId(row.recipeId)
  showToast('已移出')
  load()
}
</script>

<template>
  <div class="settings-sub">
    <van-nav-bar title="不感兴趣" left-arrow fixed placeholder @click-left="router.back()" />
    <p class="hint">来自推荐流中标记为「不感兴趣」的药膳，移出后将可能再次出现在推荐中。</p>
    <van-empty v-if="isEmpty" description="暂无记录" />
    <van-cell-group v-else inset>
      <van-cell v-for="r in rows" :key="r.recipeId" :title="r.title">
        <template #right-icon>
          <van-button size="small" plain type="danger" @click="onRemove(r)">移出</van-button>
        </template>
      </van-cell>
    </van-cell-group>
  </div>
</template>

<style scoped>
.hint {
  margin: 12px 16px;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.5;
}
</style>
