// @ts-nocheck
/**
 * @template {(...args: any[]) => void} T
 * @param {T} fn
 * @param {number} ms
 * @returns {T & { cancel: () => void }}
 */
export function debounce(fn, ms) {
  let t = null
  const wrapped = (...args) => {
    if (t) clearTimeout(t)
    t = setTimeout(() => {
      t = null
      fn(...args)
    }, ms)
  }
  wrapped.cancel = () => {
    if (t) clearTimeout(t)
    t = null
  }
  return wrapped
}
