/**
 * 与 `constitutionSurvey.js` 对齐的最小声明，供 `constitution.ts` 等 TS 模块引用。
 */
export const CONSTITUTION_BRIEF: Record<string, string>
export const QUESTION_BANK_VERSION: string

export function getTotalQuestionCount(): number
export function buildNeutralAnswers(score?: number): number[]

export function computeMockConstitution(
  answers: (number | null)[],
  options?: { questionVersion?: string },
): {
  constitutionCode: string
  scores?: Record<string, unknown>
  meta?: Record<string, unknown>
}
