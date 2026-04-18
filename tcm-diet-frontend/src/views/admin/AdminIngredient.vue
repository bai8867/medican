<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchIngredientList,
  createIngredient,
  updateIngredient,
  deleteIngredient,
} from '@/api/ingredient'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(10)

const dialogVisible = ref(false)
const dialogTitle = ref('新增食材')
const editingId = ref(null)

const form = reactive({
  name: '',
  efficacySummary: '',
  enabled: true,
})

const rules = {
  name: [{ required: true, message: '请输入食材名称', trigger: 'blur' }],
  efficacySummary: [
    { required: true, message: '请输入功效简介', trigger: 'blur' },
  ],
}

const formRef = ref(null)

const filters = reactive({
  keyword: '',
  /** '' | '1' | '0' → 全部 / 仅启用 / 仅禁用 */
  enabled: '',
})

async function load() {
  loading.value = true
  try {
    const kw = String(filters.keyword ?? '').trim()
    const enabledParam =
      filters.enabled === '1' ? true : filters.enabled === '0' ? false : undefined
    const data = await fetchIngredientList({
      page: page.value,
      page_size: pageSize.value,
      ...(kw ? { keyword: kw } : {}),
      ...(enabledParam !== undefined ? { enabled: enabledParam } : {}),
    })
    const tot = Number(data?.total) || 0
    const maxPage = Math.max(1, Math.ceil(tot / pageSize.value) || 1)
    if (page.value > maxPage) {
      page.value = maxPage
      return load()
    }
    total.value = tot
    rows.value = data?.records || data?.list || []
  } catch (e) {
    rows.value = []
    total.value = 0
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

function onSearch() {
  page.value = 1
  load()
}

function onReset() {
  filters.keyword = ''
  filters.enabled = ''
  page.value = 1
  load()
}

function onPageSizeChange() {
  page.value = 1
  load()
}

onMounted(load)
</script>

<template>
  <div>
    <h2 class="admin-ing__title">食材管理</h2>
    <p class="admin-ing__intro">
      维护药食同源食材库：名称全局唯一；禁用后不会在药膳表单的食材下拉中出现。
    </p>

    <div class="admin-page-card admin-ing__filter">
      <el-form :model="filters" label-width="88px" @submit.prevent="onSearch">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="12" :md="8">
            <el-form-item label="关键词">
              <el-input
                v-model="filters.keyword"
                clearable
                placeholder="按名称或功效简介筛选"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="12" :md="6">
            <el-form-item label="状态">
              <el-select v-model="filters.enabled" clearable placeholder="全部" style="width: 100%">
                <el-option label="全部" value="" />
                <el-option label="仅启用" value="1" />
                <el-option label="仅禁用" value="0" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="24" :md="10">
            <el-form-item label-width="0" class="admin-ing__filter-actions">
              <el-button type="primary" @click="onSearch">查询</el-button>
              <el-button @click="onReset">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </div>

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
      <div class="admin-ing__pager">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          :total="total"
          background
          @current-change="load"
          @size-change="onPageSizeChange"
        />
      </div>
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

.admin-ing__filter {
  margin-bottom: var(--space-lg);
}

.admin-ing__filter-actions :deep(.el-form-item__content) {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  justify-content: flex-start;
}

@media (min-width: 768px) {
  .admin-ing__filter-actions :deep(.el-form-item__content) {
    justify-content: flex-end;
  }
}

.admin-ing__toolbar {
  margin-bottom: var(--space-lg);
  display: flex;
  justify-content: flex-end;
}

.admin-ing__pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--space-md);
}

.admin-ing__brief {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}
</style>
