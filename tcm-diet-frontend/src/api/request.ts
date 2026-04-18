/**
 * Axios 实例入口；实现位于 `./http/client`（拦截器与 Mock 适配）。
 * 与 `优化pro` 解耦方向一致：业务 API 只依赖本模块，不直接 import client。
 */
export { default } from './http/client'
export type { TcmHttpClient, TcmAxiosRequestConfig } from './http/httpTypes'
