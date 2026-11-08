import { useEffect } from 'react'
import { useNavigate } from 'react-router'
import { api } from '../api'
import { dateTime } from '../format'
import { useAsync } from '../hooks'
import { ErrorBanner } from './ui'

/** Selects a completed run and keeps it in the URL (`${base}/:runId`). */
export default function RunPicker({ base, runId }: { base: string; runId: string | undefined }) {
  const navigate = useNavigate()
  const runs = useAsync(() => api.runs(), [])
  const completed = (runs.data ?? [])
    .filter((r) => r.status === 'COMPLETED')
    .sort((a, b) => (b.finishedAt ?? b.createdAt ?? '').localeCompare(a.finishedAt ?? a.createdAt ?? ''))

  useEffect(() => {
    if (!runId && completed.length > 0) navigate(`${base}/${encodeURIComponent(completed[0].runId)}`, { replace: true })
  }, [runId, completed.length, base, navigate]) // eslint-disable-line react-hooks/exhaustive-deps

  const known = completed.some((r) => r.runId === runId)
  return (
    <div className="flex min-w-0 flex-col gap-1">
      <select
        className="input max-w-full sm:w-96"
        value={runId ?? ''}
        onChange={(e) => navigate(`${base}/${encodeURIComponent(e.target.value)}`)}
        aria-label="Benchmark run"
      >
        {!runId && <option value="">{runs.loading ? 'Loading runs…' : completed.length ? 'Select a run' : 'No completed runs'}</option>}
        {runId && !known && <option value={runId}>{runId}</option>}
        {completed.map((r) => (
          <option key={r.runId} value={r.runId}>
            {r.label ? `${r.label} — ` : ''}
            {r.runId.slice(0, 12)} · {dateTime(r.finishedAt ?? r.createdAt)}
          </option>
        ))}
      </select>
      <ErrorBanner error={runs.error} onRetry={runs.reload} />
    </div>
  )
}
