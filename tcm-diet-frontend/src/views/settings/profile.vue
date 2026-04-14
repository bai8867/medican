<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, NavBar as VanNavBar, CellGroup as VanCellGroup, Field as VanField, Button as VanButton, Image as VanImage } from 'vant'
import { useUserStore } from '@/stores/user'
import { updateProfileBasics } from '@/api/userSettings'

const router = useRouter()
const userStore = useUserStore()

const nickname = ref('')
const saving = ref(false)

const avatarUrl = computed(() => (userStore.avatar || '').trim())

onMounted(() => {
  nickname.value = (userStore.username || '').trim()
})

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
    showToast('已保存')
  } catch {
    /* 拦截器已提示 */
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="settings-sub">
    <van-nav-bar title="个人资料" left-arrow fixed placeholder @click-left="router.back()" />
    <div class="settings-sub__body">
      <div class="avatar-block">
        <p class="avatar-block__label">头像（V1.0 仅展示）</p>
        <van-image
          v-if="avatarUrl"
          round
          width="88"
          height="88"
          fit="cover"
          :src="avatarUrl"
        />
        <div v-else class="avatar-block__placeholder" aria-hidden="true">
          {{ nickname.slice(0, 1) || '用' }}
        </div>
      </div>
      <van-cell-group inset>
        <van-field v-model="nickname" label="昵称" placeholder="请输入昵称" maxlength="24" />
      </van-cell-group>
      <van-button
        type="primary"
        block
        round
        class="save-btn"
        :loading="saving"
        @click="onSave"
      >
        保存
      </van-button>
    </div>
  </div>
</template>

<style scoped>
.settings-sub__body {
  padding: 16px;
}

.avatar-block {
  margin-bottom: 20px;
  text-align: center;
}

.avatar-block__label {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.avatar-block__placeholder {
  width: 88px;
  height: 88px;
  margin: 0 auto;
  border-radius: 50%;
  background: linear-gradient(135deg, #ecfdf5, #d8f3dc);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: 700;
  color: #2d6a4f;
}

.save-btn {
  margin-top: 24px;
}
</style>
