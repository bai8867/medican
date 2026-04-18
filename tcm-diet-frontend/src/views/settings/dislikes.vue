<script setup lang="ts">
import { ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import {
  NavBar as VanNavBar,
  CellGroup as VanCellGroup,
  Cell as VanCell,
  Button as VanButton,
  Empty as VanEmpty,
  showToast,
  showConfirmDialog,
} from 'vant'
import { fetchDislikedRecipes, removeDislikedRecipe } from '@/api/userSettings'

const router = useRouter()

type Row = { recipeId: string; title: string; dismissedAt: string }

const items = ref<Row[]>([])

async function load() {
  try {
    const res = await fetchDislikedRecipes()
    items.value = (res.items || []).map((x) => ({
      recipeId: String(x.recipeId),
      title: String(x.title || '').trim(),
      dismissedAt: String(x.dismissedAt || ''),
    }))
  } catch {
    showToast('加载失败')
    items.value = []
  }
}

function displayTitle(row: Row) {
  return row.title || `药膳 #${row.recipeId}`
}

function timeLabel(row: Row) {
  if (!row.dismissedAt) return undefined
  try {
    return `标记时间：${new Date(row.dismissedAt).toLocaleString('zh-CN')}`
  } catch {
    return undefined
  }
}

async function onRemove(row: Row) {
  try {
    await showConfirmDialog({
      title: '确认移除',
      message: `将「${displayTitle(row)}」从不再推荐列表中移除？`,
    })
  } catch {
    return
  }
  await removeDislikedRecipe(row.recipeId)
  showToast('已恢复')
  await load()
}

onMounted(load)
onActivated(load)
</script>

<template>
  <div class="settings-sub settings-sub--scroll">
    <van-nav-bar title="不感兴趣" left-arrow fixed placeholder @click-left="router.back()" />
    <div class="body">
      <p class="hint">
        以下药膳来自推荐页的「不感兴趣」，数据仅保存在本机；移除后将重新参与推荐排序。
      </p>
      <van-empty v-if="items.length === 0" description="暂无记录" />
      <van-cell-group v-else inset>
        <van-cell v-for="row in items" :key="row.recipeId" :title="displayTitle(row)" :label="timeLabel(row)">
          <template #value>
            <van-button size="small" type="danger" plain hairline @click.stop="onRemove(row)">移除</van-button>
          </template>
        </van-cell>
      </van-cell-group>
    </div>
  </div>
</template>

<style scoped>
.body {
  padding-bottom: 24px;
}

.hint {
  margin: 12px 16px;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.5;
}
</style>
