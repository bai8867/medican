export type SeasonCode = 'spring' | 'summer' | 'autumn' | 'winter'

export declare const SEASON_OPTIONS: ReadonlyArray<{ code: SeasonCode; label: string }>

export declare function getCurrentSeasonCode(month?: number): SeasonCode

export declare function getSeasonLabel(code: string): string
