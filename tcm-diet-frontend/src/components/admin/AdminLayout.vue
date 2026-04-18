<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  Bowl,
  Box,
  Calendar,
  DataLine,
  Histogram,
  Setting,
  UserFilled,
} from '@element-plus/icons-vue'
import { useAdminAuthStore } from '@/stores/adminAuth'
import '@/style/admin.css'

const route = useRoute()
const router = useRouter()
const adminAuth = useAdminAuthStore()

const menuItems = computed(() => {
  const all = [
    {
      path: '/admin/dashboard',
      title: '数据看板',
      icon: DataLine,
      roles: ['admin', 'canteen'],
    },
    {
      path: '/admin/campus-calendar',
      title: '本周药膳日历',
      icon: Calendar,
      roles: ['admin', 'canteen'],
    },
    {
      path: '/admin/recipe',
      title: '药膳管理',
      icon: Bowl,
      roles: ['admin', 'canteen'],
    },
    {
      path: '/admin/ingredient',
      title: '食材管理',
      icon: Box,
      roles: ['admin', 'canteen'],
    },
    {
      path: '/admin/user',
      title: '用户管理',
      icon: UserFilled,
      roles: ['admin'],
    },
    {
      path: '/admin/system/settings',
      title: '系统设置',
      icon: Setting,
      roles: ['admin'],
    },
    {
      path: '/admin/ai-quality',
      title: 'AI 质量治理',
      icon: Histogram,
      roles: ['admin'],
    },
  ]
  return all.filter((i) => i.roles.includes(adminAuth.role))
})

const activeMenu = computed(() => route.path)

function onLogout() {
  adminAuth.logout()
  router.replace({ path: '/admin/login' })
}
</script>

<template>
  <div class="admin-root">
    <el-container class="admin-layout">
      <el-aside width="220px" class="admin-sidebar">
        <div class="admin-sidebar__brand">药膳后台</div>
        <el-menu
          :default-active="activeMenu"
          class="admin-menu"
          background-color="transparent"
          text-color="rgba(245,243,239,0.88)"
          active-text-color="#f0d9b5"
          router
        >
          <el-menu-item
            v-for="item in menuItems"
            :key="item.path"
            :index="item.path"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </el-menu>
      </el-aside>
      <el-container>
        <el-header
          class="admin-header"
          height="56px"
          style="
            display: flex;
            align-items: center;
            justify-content: flex-end;
            gap: 12px;
            background: #fff;
            border-bottom: 1px solid var(--color-border);
            padding: 0 var(--space-lg);
          "
        >
          <span
            style="font-size: var(--font-size-sm); color: var(--color-text-secondary)"
          >
            {{ adminAuth.username }}
            <template v-if="adminAuth.role === 'admin'">（管理员）</template>
            <template v-else>（食堂负责人）</template>
          </span>
          <el-button type="primary" plain size="small" @click="onLogout">
            退出
          </el-button>
        </el-header>
        <el-main class="admin-main" style="padding: var(--space-lg)">
          <router-view />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<style scoped>
.admin-layout {
  min-height: 100vh;
}

.admin-menu {
  border-right: none;
  padding: var(--space-sm) 0;
}

.admin-menu :deep(.el-menu-item) {
  margin: 2px var(--space-sm);
  border-radius: var(--radius-sm);
}

.admin-menu :deep(.el-menu-item.is-active) {
  background: rgba(255, 255, 255, 0.12) !important;
}
</style>
