<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore, CONSTITUTION_TYPES } from '@/stores/user'
import {
  fetchAdminUserList,
  fetchAdminUserDetail,
  patchAdminUserStatus,
} from '@/api/adminUser.js'
import { setUserStatus } from '@/utils/adminUserStatus.js'

const userStore = useUserStore()

const loading = ref(false)
const list = ref([])
const total = ref(0)

const filters = reactive({
  userId: '',
  constitutionCode: '',
})

const detailOpen = ref(false)
const detailLoading = ref(false)
/** @type {import('vue').Ref<Record<string, unknown> | null>} */
const detail = ref(null)

const statusBusyId = ref('')

const constitutionOptions = computed(() => [
  { label: '全部', value: '' },
  ...CONSTITUTION_TYPES.map((c) => ({ label: c.label, value: c.code })),
])

const sourceLabel = computed(() => {
  const m = {
    questionnaire: '问卷测评',
    survey: '问卷测评',
    manual: '手动设置',
    unset: '未设置',
    api: '接口同步',
  }
  return (k) => m[k] || k || '—'
})

const tendencyRows = computed(() => {
  const raw = detail.value?.surveyScores?.constitutionTendency
  if (!raw || typeof raw !== 'object') return []
  return CONSTITUTION_TYPES.map((c) => ({
    code: c.label,
    score: raw[c.code] ?? 0,
  })).filter((r) => r.score > 0)
})

function formatTime(iso) {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return String(iso)
  return d.toLocaleString('zh-CN', { hour12: false })
}

async function loadList() {
  loading.value = true
  try {
    const data = await fetchAdminUserList({
      userId: filters.userId.trim(),
      constitutionCode: filters.constitutionCode || undefined,
    })
    list.value = Array.isArray(data?.list) ? data.list : Array.isArray(data?.records) ? data.records : []
    total.value = typeof data?.total === 'number' ? data.total : list.value.length
  } catch (e) {
    list.value = []
    total.value = 0
    ElMessage.error(e?.message || e?.msg || '用户列表加载失败')
  } finally {
    loading.value = false
  }
}

function onSearch() {
  loadList()
}

function onReset() {
  filters.userId = ''
  filters.constitutionCode = ''
  loadList()
}

async function openDetail(row) {
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    detail.value = await fetchAdminUserDetail(row.id)
  } catch {
    detail.value = null
  } finally {
    detailLoading.value = false
  }
}

function onDetailClosed() {
  detail.value = null
}

async function onStatusChange(row, nextActive) {
  const next = nextActive ? 'active' : 'disabled'
  const isSelf = row.id === userStore.userId
  if (next === 'disabled' && isSelf) {
    try {
      await ElMessageBox.confirm(
        '将禁用当前浏览器正在使用的校园端账号。保存后返回前台任意页面时将无法使用应用（模拟无法登录）。是否继续？',
        '禁用本机用户',
        { type: 'warning', confirmButtonText: '确定禁用', cancelButtonText: '取消' },
      )
    } catch {
      return
    }
  }

  statusBusyId.value = row.id
  try {
    await patchAdminUserStatus(row.id, next)
    setUserStatus(String(row.id), next)
    ElMessage.success(next === 'active' ? '已设为正常' : '已禁用')
    row.status = next
    if (detail.value?.id === row.id) {
      detail.value.status = next
    }
  } catch {
    // 请求层已提示
  } finally {
    statusBusyId.value = ''
  }
}

onMounted(() => {
  loadList()
})
</script>

<template>
  <div>
    <h2
      style="
        margin: 0 0 var(--space-lg);
        font-size: var(--font-size-xl);
        color: var(--color-text-primary);
      "
    >
      用户管理
    </h2>
    <p
      style="
        margin: -8px 0 var(--space-lg);
        font-size: var(--font-size-sm);
        color: var(--color-text-secondary);
      "
    >
      PRD 5.6.4：支持按用户 ID、体质筛选；查看画像；设置正常/禁用（禁用后校园端将提示无法使用）。
    </p>

    <div class="admin-page-card" style="margin-bottom: var(--space-lg)">
      <el-form :model="filters" label-width="96px" @submit.prevent="onSearch">
        <el-row :gutter="16">
          <el-col :xs="24" :sm="8">
            <el-form-item label="用户 ID">
              <el-input
                v-model="filters.userId"
                clearable
                placeholder="支持模糊匹配"
              />
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label="体质类型">
              <el-select
                v-model="filters.constitutionCode"
                clearable
                placeholder="全部"
                style="width: 100%"
              >
                <el-option
                  v-for="opt in constitutionOptions.filter((o) => o.value)"
                  :key="opt.value"
                  :label="opt.label"
                  :value="opt.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :xs="24" :sm="8">
            <el-form-item label-width="0">
              <el-button type="primary" @click="onSearch">查询</el-button>
              <el-button @click="onReset">重置</el-button>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
    </div>

    <div class="admin-page-card">
      <div
        style="
          margin-bottom: var(--space-md);
          font-size: var(--font-size-sm);
          color: var(--color-text-muted);
        "
      >
        共 {{ total }} 条
        <template v-if="userStore.userId">
          · 本机用户 ID：<code style="font-size: 12px">{{ userStore.userId }}</code>
        </template>
      </div>
      <el-table v-loading="loading" :data="list" stripe border style="width: 100%">
        <el-table-column prop="id" label="用户 ID" min-width="200">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">
              {{ row.id }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column
          prop="constitutionLabel"
          label="体质类型"
          width="120"
        />
        <el-table-column prop="registeredAt" label="注册时间" min-width="170">
          <template #default="{ row }">
            {{ formatTime(row.registeredAt) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="200" fixed="right">
          <template #default="{ row }">
            <el-switch
              :model-value="row.status === 'active'"
              :loading="statusBusyId === row.id"
              inline-prompt
              active-text="正常"
              inactive-text="禁用"
              @change="(v) => onStatusChange(row, v)"
            />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog
      v-model="detailOpen"
      title="用户画像详情"
      width="720px"
      destroy-on-close
      @closed="onDetailClosed"
    >
      <div v-loading="detailLoading">
        <template v-if="detail">
          <el-descriptions :column="2" border size="small">
            <el-descriptions-item label="用户 ID" :span="2">
              {{ detail.id }}
            </el-descriptions-item>
            <el-descriptions-item label="体质">
              {{ detail.constitutionLabel }}（{{ detail.constitutionCode || '—' }}）
            </el-descriptions-item>
            <el-descriptions-item label="体质来源">
              {{ sourceLabel(detail.constitutionSource) }}
            </el-descriptions-item>
            <el-descriptions-item label="季节偏好">
              {{ detail.seasonLabel }}（{{ detail.seasonCode }}）
            </el-descriptions-item>
            <el-descriptions-item label="账号状态">
              {{ detail.status === 'disabled' ? '禁用' : '正常' }}
            </el-descriptions-item>
            <el-descriptions-item label="注册时间" :span="2">
              {{ formatTime(detail.registeredAt) }}
            </el-descriptions-item>
          </el-descriptions>

          <h4
            style="
              margin: var(--space-lg) 0 var(--space-sm);
              font-size: var(--font-size-md);
              color: var(--color-text-primary);
            "
          >
            问卷得分
          </h4>
          <template v-if="detail.surveyScores?.groupAverages && Object.keys(detail.surveyScores.groupAverages).length">
            <p style="font-size: 13px; color: var(--color-text-secondary); margin: 0 0 8px">
              分组均分（示意）
            </p>
            <el-descriptions :column="2" border size="small" style="margin-bottom: 12px">
              <el-descriptions-item
                v-for="(val, key) in detail.surveyScores.groupAverages"
                :key="key"
                :label="key"
              >
                {{ val }}
              </el-descriptions-item>
            </el-descriptions>
          </template>
          <p
            v-if="detail.surveyScores?.submittedAt"
            style="font-size: 12px; color: var(--color-text-muted); margin: 0 0 8px"
          >
            最近提交：{{ formatTime(detail.surveyScores.submittedAt) }}
          </p>
          <el-table
            v-if="tendencyRows.length"
            :data="tendencyRows"
            size="small"
            border
            style="width: 100%; margin-bottom: var(--space-md)"
          >
            <el-table-column prop="code" label="体质倾向" />
            <el-table-column prop="score" label="得分" width="100" />
          </el-table>
          <el-empty
            v-else
            description="暂无问卷得分数据"
            :image-size="72"
            style="margin: var(--space-md) 0"
          />

          <h4
            style="
              margin: var(--space-lg) 0 var(--space-sm);
              font-size: var(--font-size-md);
              color: var(--color-text-primary);
            "
          >
            收藏记录
          </h4>
          <p style="font-size: 13px; color: var(--color-text-secondary); margin: 0 0 6px">
            药膳
          </p>
          <el-table
            :data="detail.favorites?.recipes || []"
            size="small"
            border
            style="width: 100%; margin-bottom: var(--space-md)"
            empty-text="暂无"
          >
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="recipeId" label="菜谱 ID" width="120" />
            <el-table-column prop="favoritedAt" label="收藏时间" width="170">
              <template #default="{ row: r }">
                {{ formatTime(r.favoritedAt) }}
              </template>
            </el-table-column>
          </el-table>
          <p style="font-size: 13px; color: var(--color-text-secondary); margin: 0 0 6px">
            AI 方案
          </p>
          <el-table
            :data="detail.favorites?.aiPlans || []"
            size="small"
            border
            style="width: 100%"
            empty-text="暂无"
          >
            <el-table-column prop="name" label="名称" min-width="160" />
            <el-table-column prop="planId" label="方案 ID" width="120" />
            <el-table-column prop="favoritedAt" label="收藏时间" width="170">
              <template #default="{ row: r }">
                {{ formatTime(r.favoritedAt) }}
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
    </el-dialog>
  </div>
</template>
