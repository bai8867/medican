export type UnifiedMockRecipeRow = Record<string, unknown> & {
  id?: string | number
  name?: string
}

export declare const UNIFIED_RECIPE_STATUS: Readonly<{ ON: string; OFF: string }>

export declare function getUnifiedRecipeMockStore(): UnifiedMockRecipeRow[]

export declare function setUnifiedRecipeMockStore(next: UnifiedMockRecipeRow[] | null): void

export declare function inferSeasonFromFit(seasonFit: unknown): string

export declare function seasonFitFromSingle(season: unknown): string[]

export declare function toAdminRow(raw: unknown): UnifiedMockRecipeRow
