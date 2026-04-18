<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { changePassword } from '@/api/system/settings'

const formRef = ref<FormInstance>()
const submitLoading = ref(false)

const form = reactive({
  old_password: '',
  new_password: '',
  confirm_password: '',
})

const rules: FormRules = {
  old_password: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  new_password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      min: 6,
      message: '新密码至少 6 位',
      trigger: 'blur',
    },
  ],
  confirm_password: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.new_password) {
          callback(new Error('两次输入的新密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur',
    },
  ],
}

async function submit() {
  const fr = formRef.value
  if (!fr) return
  await fr.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      await changePassword({
        old_password: form.old_password,
        new_password: form.new_password,
        confirm_password: form.confirm_password,
      })
      ElMessage.success('密码已修改')
      fr.resetFields()
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : '修改失败'
      ElMessage.warning(msg)
    } finally {
      submitLoading.value = false
    }
  })
}

</script>

<template>
  <el-form
    ref="formRef"
    :model="form"
    :rules="rules"
    label-width="120px"
    class="pwd-form"
    @submit.prevent
  >
    <el-form-item label="原密码" prop="old_password">
      <el-input
        v-model="form.old_password"
        type="password"
        autocomplete="current-password"
        placeholder="请输入原密码"
        show-password
      />
    </el-form-item>
    <el-form-item label="新密码" prop="new_password">
      <el-input
        v-model="form.new_password"
        type="password"
        autocomplete="new-password"
        placeholder="至少 6 位"
        show-password
      />
    </el-form-item>
    <el-form-item label="确认新密码" prop="confirm_password">
      <el-input
        v-model="form.confirm_password"
        type="password"
        autocomplete="new-password"
        placeholder="再次输入新密码"
        show-password
      />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" :loading="submitLoading" @click="submit">
        确认修改
      </el-button>
    </el-form-item>
  </el-form>
</template>

<style scoped>
.pwd-form {
  max-width: 480px;
}
</style>
