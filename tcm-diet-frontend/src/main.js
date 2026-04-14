import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import 'vant/lib/index.css'
import * as echarts from 'echarts'

import App from './App.vue'
import router from './router'
import { useUserStore } from '@/stores/user'

import '@/style/reset.css'
import '@/style/variables.css'
import '@/style/global.css'
import '@/style/main.css'

const app = createApp(App)

const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)
app.use(pinia)
useUserStore().ensureUserId()
app.use(router)
app.use(ElementPlus, { locale: zhCn })

app.config.globalProperties.$echarts = echarts
app.provide('echarts', echarts)

app.mount('#app')
