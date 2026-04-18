// @ts-nocheck
import { useRouter } from 'vue-router'

/** 推荐首页常用路由跳转（与周历「从首页应用筛选」约定一致） */
export function useRecommendPageNav() {
  const router = useRouter()

  function goCalendar() {
    router.push({ path: '/calendar', query: { from: 'home', apply: '1' } })
  }

  function goConstitution() {
    router.push({ path: '/constitution' })
  }

  function goAi() {
    router.push({ path: '/ai' })
  }

  return { goCalendar, goConstitution, goAi }
}
