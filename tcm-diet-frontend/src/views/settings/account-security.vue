<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { NavBar as VanNavBar, CellGroup as VanCellGroup, Cell as VanCell, Tag as VanTag } from 'vant'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const devices = computed(() => [
  {
    name: '本机浏览器（当前）',
    time: new Date().toLocaleString('zh-CN'),
    current: true,
  },
  {
    name: '校园网 · Chrome（示例）',
    time: '—',
    current: false,
  },
])
</script>

<template>
  <div class="settings-sub">
    <van-nav-bar title="账户安全" left-arrow fixed placeholder @click-left="router.back()" />
    <p class="hint">以下为 V1.0 界面示意，正式环境由后端返回最近登录设备与时间。</p>
    <van-cell-group inset>
      <van-cell v-for="(d, i) in devices" :key="i" :title="d.name" :label="`最近活跃：${d.time}`">
        <template #value>
          <van-tag v-if="d.current" type="success" plain>当前</van-tag>
        </template>
      </van-cell>
    </van-cell-group>
    <p class="fine">用户 ID：{{ userStore.userId || '—' }}</p>
  </div>
</template>

<style scoped>
.hint {
  margin: 12px 16px;
  font-size: 13px;
  color: var(--color-text-secondary);
  line-height: 1.5;
}

.fine {
  margin: 16px;
  font-size: 12px;
  color: var(--color-text-muted);
  word-break: break-all;
}
</style>
