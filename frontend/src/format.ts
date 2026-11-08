export const NA = 'n/a'

export function isNum(v: unknown): v is number {
  return typeof v === 'number' && Number.isFinite(v)
}

export function pct(v: number | null | undefined, digits = 1): string {
  return isNum(v) ? `${(v * 100).toFixed(digits)}%` : NA
}

export function num(v: number | null | undefined, digits = 2): string {
  if (!isNum(v)) return NA
  return Number.isInteger(v) ? String(v) : v.toFixed(digits)
}

export function ms(v: number | null | undefined, digits = 1): string {
  return isNum(v) ? `${v.toFixed(digits)} ms` : NA
}

export function nanosToMs(v: number | null | undefined): number | null {
  return isNum(v) ? v / 1e6 : null
}

export function ci(c: [number, number] | null | undefined): string {
  return c && isNum(c[0]) && isNum(c[1]) ? `[${(c[0] * 100).toFixed(1)}–${(c[1] * 100).toFixed(1)}]` : ''
}

export function bytes(v: number | null | undefined): string {
  if (!isNum(v)) return NA
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let x = v
  let i = 0
  while (x >= 1024 && i < units.length - 1) {
    x /= 1024
    i++
  }
  return `${x.toFixed(i === 0 ? 0 : 1)} ${units[i]}`
}

export function dateTime(iso: string | null | undefined): string {
  if (!iso) return NA
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString()
}

export function prettyJson(raw: unknown): string {
  if (raw === null || raw === undefined) return ''
  if (typeof raw === 'string') {
    try {
      return JSON.stringify(JSON.parse(raw), null, 2)
    } catch {
      return raw
    }
  }
  return JSON.stringify(raw, null, 2)
}

export function display(v: unknown): string {
  if (v === null || v === undefined || v === '') return NA
  if (typeof v === 'object') return JSON.stringify(v)
  return String(v)
}
