<script setup>
import { useRouter } from 'vue-router'
import { showToast, Button as VanButton } from 'vant'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

function goEntry() {
  router.push({ path: '/' })
}

async function copyId() {
  if (!userStore.userId) return
  try {
    await navigator.clipboard?.writeText(userStore.userId)
    showToast({ type: 'success', message: '已复制' })
  } catch {
    showToast('复制失败，请手动选择复制')
  }
}
</script>

<template>
  <div class="account-disabled">
    <div class="account-disabled__card">
      <h1>账号已禁用</h1>
      <p>该账号已被管理员禁用，无法继续使用校园药膳推荐、体质测评、收藏与个性化等功能。</p>
      <p>请使用新的学号或昵称重新注册；若需恢复本账号，请联系管理员在「用户管理」中将状态改回「正常」。</p>
      <p v-if="userStore.userId" class="account-disabled__id">
        当前用户 ID：<code>{{ userStore.userId }}</code>
        <van-button type="primary" size="small" plain hairline class="account-disabled__copy" @click="copyId">
          复制
        </van-button>
      </p>
      <p class="account-disabled__hint">提示：重新注册需使用未被占用的用户名。</p>
      <van-button type="primary" block round plain @click="goEntry">返回模式选择</van-button>
    </div>
  </div>
</template>

<style scoped>
.account-disabled {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-lg);
  background: var(--color-bg-page, #f5f2eb);
}

.account-disabled__card {
  max-width: 520px;
  padding: var(--space-xl);
  border-radius: var(--radius-md, 8px);
  background: #fff;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.06);
}

.account-disabled__card h1 {
  margin: 0 0 var(--space-md);
  font-size: var(--font-size-xl);
  color: var(--color-text-primary);
}

.account-disabled__card p {
  margin: 0 0 var(--space-sm);
  font-size: var(--font-size-sm);
  line-height: 1.6;
  color: var(--color-text-secondary);
}

.account-disabled__id {
  margin-top: var(--space-md) !important;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-xs);
}

.account-disabled__id code {
  font-size: 12px;
  word-break: break-all;
}

.account-disabled__copy {
  flex-shrink: 0;
}

.account-disabled__hint {
  color: var(--color-text-muted);
  font-size: var(--font-size-xs);
  margin-bottom: var(--space-lg) !important;
}
</style>
