import type { AxiosRequestConfig } from 'axios'

/** 与拦截器、各 API 调用处一致的可选字段 */
export type TcmAxiosRequestConfig = AxiosRequestConfig

/** 与 `../request.ts` 再导出语义一致 */
export interface TcmHttpClient {
  get<T = unknown>(url: string, config?: TcmAxiosRequestConfig): Promise<T>
  post<T = unknown>(url: string, data?: unknown, config?: TcmAxiosRequestConfig): Promise<T>
  put<T = unknown>(url: string, data?: unknown, config?: TcmAxiosRequestConfig): Promise<T>
  patch<T = unknown>(url: string, data?: unknown, config?: TcmAxiosRequestConfig): Promise<T>
  delete<T = unknown>(url: string, config?: TcmAxiosRequestConfig): Promise<T>
}
