<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAdminAuthStore } from '@/stores/adminAuth'
import '@/style/admin.css'

const route = useRoute()
const router = useRouter()
const adminAuth = useAdminAuthStore()

const formRef = ref()
const loading = ref(false)
const panel = ref('login')

const form = reactive({
  username: '',
  password: '',
  passwordConfirm: '',
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
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

async function onSubmit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    let res
    if (panel.value === 'register') {
      res = await adminAuth.registerCanteenManager(
        form.username,
        form.password,
        form.passwordConfirm,
      )
    } else {
      res = await adminAuth.login(form.username, form.password)
    }
    if (!res.ok) {
      ElMessage.error(res.message)
      return
    }
    ElMessage.success(panel.value === 'register' ? '注册成功，已自动登录' : '登录成功')
    const redirect = route.query.redirect
    const path =
      typeof redirect === 'string' && redirect.startsWith('/admin')
        ? redirect
        : '/admin/dashboard'
    await router.replace(path)
  } finally {
    loading.value = false
  }
}

function onPanelChange() {
  form.passwordConfirm = ''
  formRef.value?.clearValidate?.()
}

function goBack() {
  router.push({ path: '/' })
}
</script>

<template>
  <div class="admin-login-page">
    <div class="admin-login-card">
      <h1
        style="
          margin: 0 0 var(--space-lg);
          font-size: var(--font-size-xl);
          color: var(--color-text-primary);
        "
      >
        后台登录
      </h1>
      <p
        style="
          margin: 0 0 var(--space-md);
          font-size: var(--font-size-sm);
          color: var(--color-text-secondary);
        "
      >
        对接后端时请使用种子账号：管理员 <strong>admin</strong> / <strong>admin123</strong>；食堂负责人
        <strong>canteen</strong> 或 <strong>canteen_manager</strong> / <strong>canteen123</strong>。纯前端 Mock
        模式（VITE_USE_MOCK=true）下仍为 admin / 123456。无食堂账号可在「注册（食堂负责人）」自助开通并写入数据库。
      </p>
      <el-radio-group
        v-model="panel"
        style="
          display: flex;
          width: 100%;
          margin-bottom: var(--space-lg);
          justify-content: center;
        "
        @change="onPanelChange"
      >
        <el-radio-button label="login">登录</el-radio-button>
        <el-radio-button label="register">注册（食堂负责人）</el-radio-button>
      </el-radio-group>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="onSubmit"
      >
        <el-form-item label="账号" prop="username">
          <el-input
            v-model="form.username"
            autocomplete="username"
            placeholder="admin / canteen / canteen_manager"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :autocomplete="panel === 'register' ? 'new-password' : 'current-password'"
            placeholder="123456"
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
          <el-button
            type="primary"
            native-type="submit"
            style="width: 100%"
            :loading="loading"
          >
            {{ panel === 'register' ? '注册并进入后台' : '登录' }}
          </el-button>
        </el-form-item>
        <el-form-item style="margin-bottom: 0">
          <el-button style="width: 100%" @click="goBack">返回选择模式</el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>
