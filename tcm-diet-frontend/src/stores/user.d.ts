export declare const CONSTITUTION_TYPES: ReadonlyArray<{ code: string; label: string }>

/** Pinia user store：与 `user.js` state/actions 对齐的最小类型，供 TS/Vue 引用 */
export interface UserStore {
  userId: string
  registeredAt: string
  constitutionCode: string
  constitutionSource: string
  seasonCode: string
  username: string
  avatar: string
  token: string
  campusSignedIn: boolean
  constitutionSurveyCompleted: boolean
  personalizedRecommendEnabled: boolean
  preferences: Record<string, unknown>
  updateSeason(code: string): void
  logoutCampus(): void
  ensureUserId(): void
  $patch(p: Record<string, unknown>): void
}

export declare function useUserStore(): UserStore
