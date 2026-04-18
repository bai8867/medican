// @ts-nocheck
/**
 * 判断是否为典型 JWT compact 串（header.payload.signature）。
 * 校园 Mock 登录写入的 `campus-token-*` 不是 JWT，不应作为后端 Bearer 使用。
 */
export function looksLikeBearerJwt(token) {
  if (typeof token !== 'string' || !token.trim()) return false
  const parts = token.trim().split('.')
  return parts.length === 3 && parts.every((p) => p.length > 0)
}
