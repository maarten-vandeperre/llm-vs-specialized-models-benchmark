import { useState, type ReactNode } from 'react'
import type { HealthState, Outcome, RunState } from '../api'
import { OUTCOME_COLORS } from '../colors'
import { NA, prettyJson } from '../format'

export function Card({ title, actions, children, className = '' }: { title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={`min-w-0 rounded-lg border border-gray-200 bg-white shadow-sm ${className}`}>
      {(title || actions) && (
        <header className="flex flex-wrap items-center justify-between gap-2 border-b border-gray-100 px-4 py-2.5">
          {title && <h2 className="text-sm font-semibold text-gray-800">{title}</h2>}
          {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
        </header>
      )}
      <div className="p-4">{children}</div>
    </section>
  )
}

export function ErrorBanner({ error, onRetry }: { error: string | null | undefined; onRetry?: () => void }) {
  if (!error) return null
  return (
    <div role="alert" className="flex flex-wrap items-start justify-between gap-2 rounded-md border border-red-300 bg-red-50 px-3 py-2 text-sm text-red-800">
      <span className="break-all">{error}</span>
      {onRetry && (
        <button type="button" onClick={onRetry} className="shrink-0 font-medium underline">
          Retry
        </button>
      )}
    </div>
  )
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="rounded-md border border-dashed border-gray-300 px-4 py-8 text-center text-sm text-gray-500">{children}</div>
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return <div className="py-4 text-sm text-gray-500">{label}</div>
}

/** Renders a value or a muted "n/a" with an optional reason tooltip. */
export function Val({ v, reason, children }: { v: unknown; reason?: string | null; children?: ReactNode }) {
  if (v === null || v === undefined || v === '' || (typeof v === 'number' && !Number.isFinite(v))) {
    return (
      <span className="cursor-help text-gray-400" title={reason ?? 'Not measured / not reported by the backend'}>
        {NA}
      </span>
    )
  }
  return <>{children ?? String(v)}</>
}

export function Badge({ children, color, title }: { children: ReactNode; color: string; title?: string }) {
  return (
    <span
      title={title}
      className="inline-flex items-center whitespace-nowrap rounded px-1.5 py-0.5 text-xs font-medium"
      style={{ backgroundColor: `${color}1f`, color, border: `1px solid ${color}55` }}
    >
      {children}
    </span>
  )
}

export function OutcomeBadge({ outcome }: { outcome: Outcome | string | null | undefined }) {
  if (!outcome) return <Val v={null} />
  return <Badge color={OUTCOME_COLORS[outcome] ?? '#6a6e73'}>{outcome}</Badge>
}

const HEALTH_COLORS: Record<string, string> = {
  UP: '#3e8635',
  MODEL_MISSING: '#f0ab00',
  RUNTIME_UNSUPPORTED: '#8a8d90',
  DOWN: '#c9190b',
  DISABLED: '#6a6e73',
}

export function HealthBadge({ state, detail, loading, error }: { state?: HealthState | string | null; detail?: string | null; loading?: boolean; error?: string | null }) {
  if (loading) return <Badge color="#6a6e73">checking…</Badge>
  if (error) return <Badge color="#c9190b" title={error}>health unknown</Badge>
  if (!state) return null
  return (
    <Badge color={HEALTH_COLORS[state] ?? '#6a6e73'} title={detail ?? undefined}>
      {state}
    </Badge>
  )
}

const RUN_COLORS: Record<string, string> = {
  QUEUED: '#6a6e73',
  RUNNING: '#0066cc',
  COMPLETED: '#3e8635',
  FAILED: '#c9190b',
  CANCELLED: '#8a8d90',
}

export function RunBadge({ status }: { status: RunState | string }) {
  return <Badge color={RUN_COLORS[status] ?? '#6a6e73'}>{status}</Badge>
}

export function Check({ ok }: { ok: boolean | null | undefined }) {
  if (ok === null || ok === undefined) return <Val v={null} />
  return ok ? <span className="font-bold text-green-700" title="correct">✓</span> : <span className="font-bold text-red-700" title="incorrect">✗</span>
}

export function Collapsible({ title, children, defaultOpen = false }: { title: ReactNode; children: ReactNode; defaultOpen?: boolean }) {
  const [open, setOpen] = useState(defaultOpen)
  return (
    <div className="rounded-md border border-gray-200">
      <button type="button" onClick={() => setOpen((o) => !o)} className="flex w-full items-center gap-2 px-3 py-2 text-left text-sm font-medium text-gray-700 hover:bg-gray-50">
        <span className="inline-block w-3 text-gray-400">{open ? '▾' : '▸'}</span>
        {title}
      </button>
      {open && <div className="border-t border-gray-200 p-3">{children}</div>}
    </div>
  )
}

export function JsonBlock({ value }: { value: unknown }) {
  const text = prettyJson(value)
  if (!text) return <span className="text-sm text-gray-400">empty</span>
  return <pre className="max-h-96 overflow-auto rounded bg-gray-900 p-3 text-xs leading-relaxed text-gray-100">{text}</pre>
}

export function KeyValue({ data }: { data: Record<string, unknown> | null | undefined }) {
  if (!data || Object.keys(data).length === 0) return <span className="text-sm text-gray-400">none</span>
  return (
    <dl className="grid grid-cols-1 gap-x-4 gap-y-1 text-sm sm:grid-cols-[max-content_1fr]">
      {Object.entries(data).map(([k, v]) => (
        <div key={k} className="contents">
          <dt className="font-medium text-gray-600">{k}</dt>
          <dd className="mb-1 break-all font-mono text-xs text-gray-800 sm:mb-0">
            {v === null || v === undefined ? <Val v={null} /> : typeof v === 'object' ? JSON.stringify(v) : String(v)}
          </dd>
        </div>
      ))}
    </dl>
  )
}

/** Horizontally scrolling table container. */
export function TableWrap({ children }: { children: ReactNode }) {
  return (
    <div className="-mx-4 overflow-x-auto px-4">
      <table className="data-table min-w-full text-sm">{children}</table>
    </div>
  )
}

export function SyntheticNote() {
  return (
    <div className="rounded-md border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-900">
      <strong>Synthetic dataset</strong> — results do not generalize to production customers.
    </div>
  )
}

export function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <label className="flex min-w-0 flex-col gap-1 text-sm">
      <span className="font-medium text-gray-700">{label}</span>
      {children}
      {hint && <span className="text-xs text-gray-500">{hint}</span>}
    </label>
  )
}

export function ModelSwatch({ color }: { color: string }) {
  return <span className="mr-1.5 inline-block h-2.5 w-2.5 shrink-0 rounded-sm align-middle" style={{ backgroundColor: color }} />
}
