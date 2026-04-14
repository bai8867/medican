/// <reference types="vite/client" />

declare module '*.json' {
  const value: Record<string, unknown> & { version?: string; name?: string }
  export default value
}

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}
