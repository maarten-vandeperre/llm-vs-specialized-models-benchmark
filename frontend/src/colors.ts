// Categorical palette, assigned per modelId on first sight and stable for the
// rest of the session, so a model keeps the same color on every chart/page.
// Validated categorical order (adjacent-pair CVD separation >= 8 dE on a light surface).
export const PALETTE = [
  '#2a78d6', // blue
  '#eb6834', // orange
  '#1baf7a', // aqua
  '#eda100', // yellow
  '#e87ba4', // magenta
  '#008300', // green
  '#6250d6', // violet
  '#e34948', // red
]

const assigned = new Map<string, string>()

export function registerModels(ids: string[]): void {
  for (const id of ids) colorFor(id)
}

export function colorFor(modelId: string): string {
  let c = assigned.get(modelId)
  if (!c) {
    c = assigned.size < PALETTE.length ? PALETTE[assigned.size] : '#8a8d90' // beyond 8 models: neutral 'other'
    assigned.set(modelId, c)
  }
  return c
}

export const ACCENT = '#ee0000'

export const OUTCOME_COLORS: Record<string, string> = {
  SUCCESS: '#3e8635',
  ABSTAINED: '#2b9af3',
  UNSUPPORTED: '#8a8d90',
  MALFORMED: '#f0ab00',
  TIMEOUT: '#ec7a08',
  INFRA_ERROR: '#c9190b',
}
