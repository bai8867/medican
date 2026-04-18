declare module '@/mocks/aiTherapyPlanResponse' {
  export function buildAiTherapyPlanData(input: {
    symptom?: string
    constitution?: string
  }): Record<string, unknown>
}
