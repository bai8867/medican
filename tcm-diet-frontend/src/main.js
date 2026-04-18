import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import 'vant/lib/index.css'

import App from './App.vue'
import router from './router'
import { useUserStore } from '@/stores/user'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { setAccountDisabledHandler } from '@/api/http/accountDisabledNavigator'

import '@/style/reset.css'
import '@/style/variables.css'
import '@/style/vant-theme.css'
import '@/style/global.css'
import '@/style/main.css'

const app = createApp(App)

const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)
app.use(pinia)
useUserStore().ensureUserId()
app.use(router)

setAccountDisabledHandler(async (message) => {
  const msg = message || '账号已被禁用，请重新注册'
  useUserStore().logoutCampus()
  useAdminAuthStore().logout()
  ElMessage.error(msg)
  await router.replace({ path: '/account-disabled' })
})

app.use(ElementPlus, { locale: zhCn })

app.mount('#app')
