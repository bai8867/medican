<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  showToast,
  RadioGroup as VanRadioGroup,
  Radio as VanRadio,
  CellGroup as VanCellGroup,
  Field as VanField,
  Button as VanButton,
} from 'vant'
import { useUserStore } from '@/stores/user'
import { login as authLogin, register as authRegister } from '@/api/auth'
import '@/style/admin.css'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
/** 与 van-tabs name 对齐：login | register */
const panel = ref('login')

const form = reactive({
  username: '',
  password: '',
  passwordConfirm: '',
})

const pwdPlaceholder = computed(() =>
  panel.value === 'register' ? '至少 6 位建议包含字母与数字' : '请输入密码',
)

function resolveCampusRedirect(raw) {
  if (typeof raw !== 'string' || !raw.startsWith('/') || raw.startsWith('//')) {
    return '/home'
  }
  if (raw.startsWith('/admin')) return '/home'
  return raw
}

function onPanelSwitch() {
  form.passwordConfirm = ''
}

async function onSubmit() {
  const u = form.username.trim()
  if (!u) {
    showToast('请输入学号或昵称')
    return
  }
  if (!form.password) {
    showToast('请输入密码')
    return
  }
  if (panel.value === 'register') {
    if (!form.passwordConfirm) {
      showToast('请再次输入密码')
      return
    }
    if (form.passwordConfirm !== form.password) {
      showToast('两次输入的密码不一致')
      return
    }
  }

  loading.value = true
  try {
    if (import.meta.env.VITE_USE_MOCK === 'true') {
      if (panel.value === 'register') {
        showToast({
          message: 'Mock 模式未连接数据库，请关闭 VITE_USE_MOCK 并启动后端后再注册',
          duration: 4500,
        })
        return
      }
      userStore.signInCampus({ username: u })
      showToast({ type: 'success', message: '登录成功（Mock）' })
      const redirect = route.query.redirect
      const path = resolveCampusRedirect(typeof redirect === 'string' ? redirect : '/home')
      await router.push(path)
      return
    }
    if (panel.value === 'register') {
      const data = await authRegister(u, form.password, 'USER')
      const token = data?.token
      const user = data?.user
      if (!token) {
        showToast('注册响应异常，未返回 token')
        return
      }
      userStore.applyCampusBackendSession({ token, user, resetLocalPortrait: true })
      showToast({ type: 'success', message: '注册成功，已自动登录' })
    } else {
      const data = await authLogin(u, form.password)
      const token = data?.token
      const user = data?.user
      if (!token) {
        showToast('登录响应异常，未返回 token')
        return
      }
      userStore.applyCampusBackendSession({ token, user })
      showToast({ type: 'success', message: '登录成功' })
    }
    const redirect = route.query.redirect
    const path = resolveCampusRedirect(typeof redirect === 'string' ? redirect : '/home')
    await router.push(path)
  } catch (e) {
    if (e?.accountDisabled) return
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.push({ path: '/' })
}
</script>

<template>
  <div class="admin-login-page campus-login">
    <div class="admin-login-card">
      <h1 class="campus-login__title">校园端登录</h1>
      <p class="campus-login__hint">
        请先启动后端。测试账号：<strong>student</strong> / <strong>123456</strong>，或 <strong>demo</strong> /
        <strong>demo123</strong>。无账号可在「注册」中新建学生用户（写入数据库）。
      </p>
      <van-radio-group
        v-model="panel"
        direction="horizontal"
        class="campus-login__mode"
        @change="onPanelSwitch"
      >
        <van-radio name="login">登录</van-radio>
        <van-radio name="register">注册</van-radio>
      </van-radio-group>
      <div class="campus-login__form">
        <van-cell-group inset>
          <van-field
            v-model="form.username"
            label="学号 / 昵称"
            placeholder="例如 student"
            autocomplete="username"
          />
          <van-field
            v-model="form.password"
            type="password"
            label="密码"
            :placeholder="pwdPlaceholder"
            :autocomplete="panel === 'register' ? 'new-password' : 'current-password'"
          />
          <van-field
            v-if="panel === 'register'"
            v-model="form.passwordConfirm"
            type="password"
            label="确认密码"
            placeholder="再次输入密码"
            autocomplete="new-password"
          />
        </van-cell-group>
        <div class="campus-login__actions">
          <van-button type="primary" block round :loading="loading" @click="onSubmit">
            {{ panel === 'register' ? '注册并进入' : '登录' }}
          </van-button>
          <van-button block round plain hairline class="campus-login__back" @click="goBack">
            返回选择模式
          </van-button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.campus-login__title {
  margin: 0 0 var(--space-lg);
  font-size: var(--font-size-xl);
  color: var(--color-text-primary);
  font-family: var(--font-serif);
  font-weight: 600;
  text-align: center;
}

.campus-login__hint {
  margin: 0 0 var(--space-md);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.campus-login__hint strong {
  color: var(--color-text-primary);
  font-weight: 600;
}

.campus-login__mode {
  display: flex;
  justify-content: center;
  gap: var(--space-lg);
  margin-bottom: var(--space-lg);
}

.campus-login__mode :deep(.van-radio__label) {
  font-size: var(--font-size-md);
}

.campus-login__actions {
  margin-top: var(--space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}

.campus-login__back {
  margin-top: var(--space-xs);
}
</style>
