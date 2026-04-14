<script setup lang="ts">
import { onMounted, ref } from 'vue'
import ChangePasswordForm from './ChangePasswordForm.vue'
import { fetchAdminAuditLog, type AdminAuditLogRow } from '@/api/system/settings'

const auditLoading = ref(false)
const auditRows = ref<AdminAuditLogRow[]>([])

async function loadAudit() {
  auditLoading.value = true
  try {
    const res = await fetchAdminAuditLog()
    auditRows.value = res.items || []
  } finally {
    auditLoading.value = false
  }
}

onMounted(loadAudit)
</script>

<template>
  <div class="settings-stack">
    <el-card class="settings-card" shadow="never">
      <template #header>
        <span class="settings-card-title">修改当前管理员密码</span>
      </template>
      <ChangePasswordForm />
    </el-card>

    <el-card class="settings-card" shadow="never">
      <template #header>
        <span class="settings-card-title">登录安全策略</span>
      </template>
      <el-descriptions :column="1" border class="readonly-desc">
        <el-descriptions-item label="登录失败锁定策略">
          连续失败 5 次锁定 15 分钟（V1.0 只读展示）
        </el-descriptions-item>
        <el-descriptions-item label="会话超时时间">
          30 分钟（V1.0 只读展示）
        </el-descriptions-item>
      </el-descriptions>
      <p class="readonly-hint">后续迭代可在此放开编辑能力。</p>
    </el-card>

    <el-card class="settings-card" shadow="never">
      <template #header>
        <span class="settings-card-title">管理员操作记录</span>
      </template>
      <el-table
        v-loading="auditLoading"
        :data="auditRows"
        stripe
        size="default"
        style="width: 100%"
        empty-text="暂无记录"
      >
        <el-table-column prop="operation_time" label="操作时间" width="190" />
        <el-table-column prop="operation" label="操作内容" min-width="220" />
        <el-table-column prop="ip" label="IP 地址" width="140" />
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.settings-stack {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.settings-card :deep(.el-card__header) {
  padding: 16px 20px;
}

.settings-card :deep(.el-card__body) {
  padding: 20px;
}

.settings-card-title {
  font-size: 16px;
  font-weight: 600;
}

.readonly-desc {
  max-width: 720px;
}

.readonly-hint {
  margin-top: 12px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}
</style>
