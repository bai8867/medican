<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, NavBar as VanNavBar, Field as VanField, Button as VanButton, CellGroup as VanCellGroup } from 'vant'
import { changePassword } from '@/api/userSettings'

const router = useRouter()

const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const submitting = ref(false)

async function onSubmit() {
  if (!form.oldPassword) {
    showToast('请输入原密码')
    return
  }
  if (!form.newPassword || form.newPassword.length < 6) {
    showToast('新密码至少 6 位')
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    showToast('两次新密码不一致')
    return
  }
  submitting.value = true
  try {
    await changePassword({
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
      confirmPassword: form.confirmPassword,
    })
    showToast('密码已更新')
    router.back()
  } catch (e) {
    const msg = e instanceof Error ? e.message : '修改失败'
    showToast(msg)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="settings-sub">
    <van-nav-bar title="修改密码" left-arrow fixed placeholder @click-left="router.back()" />
    <div class="settings-sub__body">
      <van-cell-group inset>
        <van-field
          v-model="form.oldPassword"
          type="password"
          label="原密码"
          placeholder="请输入原密码"
          autocomplete="current-password"
        />
        <van-field
          v-model="form.newPassword"
          type="password"
          label="新密码"
          placeholder="至少 6 位"
          autocomplete="new-password"
        />
        <van-field
          v-model="form.confirmPassword"
          type="password"
          label="确认新密码"
          placeholder="再次输入新密码"
          autocomplete="new-password"
        />
      </van-cell-group>
      <van-button
        type="primary"
        block
        round
        class="submit-btn"
        :loading="submitting"
        @click="onSubmit"
      >
        确认修改
      </van-button>
    </div>
  </div>
</template>

<style scoped>
.settings-sub__body {
  padding: 16px;
}

.submit-btn {
  margin-top: 24px;
}
</style>
