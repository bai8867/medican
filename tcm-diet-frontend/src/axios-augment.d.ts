/** 确保以「模块」形式加载，使 `declare module 'axios'` 与 axios 本体合并生效（部分工具链对仅含 ambient 的 d.ts 合并不稳定） */
import 'axios'

declare module 'axios' {
  interface AxiosRequestConfig {
    /** 为 true 时业务错误不弹 ElMessage（拦截器约定） */
    skipGlobalMessage?: boolean
    /** 取消同 dedupeKey 的上一次请求 */
    cancelPrevious?: boolean
    dedupeKey?: string
    /** 请求耗时统计（拦截器写入） */
    __requestStartedAt?: number
    /** 请求去重键（与 cancelPrevious 配合） */
    __dedupeKey?: string
  }
}
