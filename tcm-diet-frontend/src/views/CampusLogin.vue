<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { login as authLogin, register as authRegister } from '@/api/auth.js'
import '@/style/admin.css'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const panel = ref('login')

const form = reactive({
  username: '',
  password: '',
  passwordConfirm: '',
})

const rules = {
  username: [{ required: true, message: '请输入学号或昵称', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  passwordConfirm: [
    {
      validator: (_rule, val, cb) => {
        if (panel.value !== 'register') {
          cb()
          return
        }
        if (!val) {
          cb(new Error('请再次输入密码'))
          return
        }
        if (val !== form.password) {
          cb(new Error('两次输入的密码不一致'))
          return
        }
        cb()
      },
      trigger: 'blur',
    },
  ],
}

function resolveCampusRedirect(raw) {
  if (typeof raw !== 'string' || !raw.startsWith('/') || raw.startsWith('//')) {
    return '/home'
  }
  if (raw.startsWith('/admin')) return '/home'
  return raw
}

async function onSubmit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    if (import.meta.env.VITE_USE_MOCK === 'true') {
      if (panel.value === 'register') {
        ElMessage.warning('Mock 模式未连接数据库，请关闭 VITE_USE_MOCK 并启动后端后再注册')
        return
      }
      userStore.signInCampus({ username: form.username.trim() })
      ElMessage.success('登录成功（Mock）')
      const redirect = route.query.redirect
      const path = resolveCampusRedirect(
        typeof redirect === 'string' ? redirect : '/home',
      )
      await router.push(path)
      return
    }
    if (panel.value === 'register') {
      const data = await authRegister(form.username.trim(), form.password, 'USER')
      const token = data?.token
      const user = data?.user
      if (!token) {
        ElMessage.error('注册响应异常，未返回 token')
        return
      }
      userStore.applyCampusBackendSession({ token, user, resetLocalPortrait: true })
      ElMessage.success('注册成功，已自动登录')
    } else {
      const data = await authLogin(form.username.trim(), form.password)
      const token = data?.token
      const user = data?.user
      if (!token) {
        ElMessage.error('登录响应异常，未返回 token')
        return
      }
      userStore.applyCampusBackendSession({ token, user })
      ElMessage.success('登录成功')
    }
    const redirect = route.query.redirect
    const path = resolveCampusRedirect(
      typeof redirect === 'string' ? redirect : '/home',
    )
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

function onPanelChange() {
  form.passwordConfirm = ''
  formRef.value?.clearValidate?.()
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
      <el-radio-group
        v-model="panel"
        class="campus-login__tabs"
        @change="onPanelChange"
      >
        <el-radio-button label="login">登录</el-radio-button>
        <el-radio-button label="register">注册</el-radio-button>
      </el-radio-group>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="学号 / 昵称" prop="username">
          <el-input v-model="form.username" autocomplete="username" placeholder="例如 student" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :autocomplete="panel === 'register' ? 'new-password' : 'current-password'"
            placeholder="至少 6 位建议包含字母与数字"
          />
        </el-form-item>
        <el-form-item v-if="panel === 'register'" label="确认密码" prop="passwordConfirm">
          <el-input
            v-model="form.passwordConfirm"
            type="password"
            show-password
            autocomplete="new-password"
            placeholder="再次输入密码"
          />
        </el-form-item>
        <el-form-item style="margin-bottom: var(--space-sm)">
          <el-button type="primary" native-type="submit" style="width: 100%" :loading="loading">
            {{ panel === 'register' ? '注册并进入' : '登录' }}
          </el-button>
        </el-form-item>
        <el-form-item style="margin-bottom: 0">
          <el-button style="width: 100%" @click="goBack">返回选择模式</el-button>
        </el-form-item>
      </el-form>
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

.campus-login__tabs {
  display: flex;
  width: 100%;
  margin-bottom: var(--space-lg);
  justify-content: center;
}

.campus-login__tabs :deep(.el-radio-button__inner) {
  min-width: 6rem;
}
</style>
