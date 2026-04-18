<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  NavBar as VanNavBar,
  CellGroup as VanCellGroup,
  Cell as VanCell,
  Field as VanField,
  Button as VanButton,
  showToast,
} from 'vant'
import { useUserStore, CONSTITUTION_TYPES } from '@/stores/user'
import { updateProfileBasics } from '@/api/userSettings'

const router = useRouter()
const userStore = useUserStore()

const nickname = ref(String(userStore.username || '').trim())

watch(
  () => userStore.username,
  (v) => {
    nickname.value = String(v || '').trim()
  },
)

const constitutionName = computed(() => {
  const hit = CONSTITUTION_TYPES.find((c) => c.code === userStore.constitutionCode)
  return hit?.label || '未设置'
})

const saving = ref(false)

async function onSave() {
  const name = nickname.value.trim()
  if (!name) {
    showToast('请输入昵称')
    return
  }
  saving.value = true
  try {
    await updateProfileBasics({ username: name })
    userStore.$patch({ username: name })
    showToast('已保存（本机展示）')
  } catch {
    showToast('保存失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="settings-sub settings-sub--scroll">
    <van-nav-bar title="个人资料" left-arrow fixed placeholder @click-left="router.back()" />
    <div class="body">
      <p class="hint">昵称仅影响本机展示；账号与体质等仍以服务端与画像数据为准。</p>
      <van-cell-group inset>
        <van-field v-model="nickname" label="昵称" placeholder="请输入昵称" maxlength="32" clearable />
        <van-cell title="用户 ID" :value="userStore.userId || '—'" />
        <van-cell title="体质" :value="constitutionName" is-link @click="router.push('/constitution')" />
      </van-cell-group>
      <van-button type="primary" block round class="save-btn" :loading="saving" @click="onSave">
        保存
      </van-button>
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

.save-btn {
  margin: 20px 16px 0;
}
</style>
