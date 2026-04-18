/**
 * Mock 匹配从拦截器传入的配置里实际读取的字段子集。
 * 与 axios 的 InternalAxiosRequestConfig 在结构上兼容，但不依赖 axios 类型，便于纯 Node / 无前端依赖的脚本复用。
 */
export type ApiMockRequestConfig = {
  params?: Record<string, unknown>
  data?: unknown
}
