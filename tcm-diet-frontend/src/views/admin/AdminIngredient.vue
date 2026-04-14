<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchIngredientList,
  createIngredient,
  updateIngredient,
  deleteIngredient,
} from '@/api/ingredient.js'

const loading = ref(false)
const rows = ref([])

const dialogVisible = ref(false)
const dialogTitle = ref('新增食材')
const editingId = ref(null)

const form = reactive({
  name: '',
  efficacySummary: '',
  enabled: true,
})

function validateNameUnique(_rule, value, callback) {
  const n = String(value ?? '').trim()
  if (!n) {
    callback()
    return
  }
  const dup = rows.value.some(
    (r) =>
      String(r.name || '').trim() === n &&
      (editingId.value == null || Number(r.id) !== Number(editingId.value)),
  )
  if (dup) callback(new Error('食材名称已存在，请更换名称'))
  else callback()
}

const rules = {
  name: [
    { required: true, message: '请输入食材名称', trigger: 'blur' },
    { validator: validateNameUnique, trigger: 'blur' },
  ],
  efficacySummary: [
    { required: true, message: '请输入功效简介', trigger: 'blur' },
  ],
}

const formRef = ref(null)

async function load() {
  loading.value = true
  try {
    const data = await fetchIngredientList()
    rows.value = data?.records || data?.list || []
  } catch (e) {
    rows.value = []
    ElMessage.error(e?.message || e?.msg || '食材列表加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  dialogTitle.value = '新增食材'
  editingId.value = null
  form.name = ''
  form.efficacySummary = ''
  form.enabled = true
  dialogVisible.value = true
}

function openEdit(row) {
  dialogTitle.value = '编辑食材'
  editingId.value = row.id
  form.name = row.name
  form.efficacySummary = row.efficacySummary || ''
  form.enabled = row.enabled !== false
  dialogVisible.value = true
}

async function onSubmit() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  try {
    if (editingId.value == null) {
      await createIngredient({
        name: form.name.trim(),
        efficacySummary: form.efficacySummary.trim(),
        enabled: form.enabled,
      })
      ElMessage.success('已新增')
    } else {
      await updateIngredient(editingId.value, {
        name: form.name.trim(),
        efficacySummary: form.efficacySummary.trim(),
        enabled: form.enabled,
      })
      ElMessage.success('已保存')
    }
    dialogVisible.value = false
    await load()
  } catch {
    /* 全局错误提示 */
  }
}

async function onDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除食材「${row.name}」？`, '删除确认', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteIngredient(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    if (e !== 'cancel') {
      /* handled */
    }
  }
}

onMounted(load)
</script>

<template>
  <div>
    <h2 class="admin-ing__title">食材管理</h2>
    <p class="admin-ing__intro">
      维护药食同源食材库：名称全局唯一；禁用后不会在药膳表单的食材下拉中出现。
    </p>

    <div class="admin-page-card admin-ing__toolbar">
      <el-button type="primary" @click="openCreate">新增食材</el-button>
    </div>

    <div class="admin-page-card">
      <el-table v-loading="loading" :data="rows" row-key="id" stripe border style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="食材名称" min-width="100" />
        <el-table-column prop="efficacySummary" label="功效简介" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="admin-ing__brief">{{ row.efficacySummary || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" effect="plain" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="onDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="480px"
      destroy-on-close
      @closed="formRef?.resetFields?.()"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="食材名称" prop="name">
          <el-input v-model="form.name" maxlength="32" show-word-limit placeholder="如：银耳" />
        </el-form-item>
        <el-form-item label="功效简介" prop="efficacySummary">
          <el-input
            v-model="form.efficacySummary"
            type="textarea"
            :rows="3"
            maxlength="120"
            show-word-limit
            placeholder="如：滋阴润肺、养胃生津"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.enabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-ing__title {
  margin: 0 0 var(--space-sm);
  font-size: var(--font-size-xl);
  color: var(--color-text-primary);
}

.admin-ing__intro {
  margin: 0 0 var(--space-lg);
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.5;
  max-width: 720px;
}

.admin-ing__toolbar {
  margin-bottom: var(--space-lg);
  display: flex;
  justify-content: flex-end;
}

.admin-ing__brief {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}
</style>
