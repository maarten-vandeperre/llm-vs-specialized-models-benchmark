import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router'
import { api, GROUPS, LANGUAGES, type BenchmarkConfig, type ModelHealth, type RunStatus } from '../api'
import { colorFor } from '../colors'
import { dateTime } from '../format'
import { useAsync } from '../hooks'
import { Card, Empty, ErrorBanner, Field, HealthBadge, Loading, ModelSwatch, RunBadge } from '../components/ui'

interface Form {
  models: string[]
  dataset: string
  groups: string[]
  languages: string[]
  split: string
  limit: string
  warmup: string
  repetitions: string
  latencySampleSize: string
  concurrency: string
  throughputRequests: string
  timeoutSeconds: string
  coldStart: boolean
  seed: string
  label: string
}

const DEFAULTS = {
  limit: '',
  warmup: '10',
  repetitions: '5',
  latencySampleSize: '30',
  concurrency: '1,4,8',
  throughputRequests: '40',
  timeoutSeconds: '180',
  coldStart: true,
}

const SMOKE = {
  limit: '30',
  warmup: '2',
  repetitions: '1',
  concurrency: '1',
  throughputRequests: '0',
  coldStart: false,
}

function toInt(s: string, fallback: number): number {
  const n = Number.parseInt(s, 10)
  return Number.isFinite(n) ? n : fallback
}

function parseConcurrency(s: string): number[] {
  return s
    .split(/[,\s]+/)
    .map((x) => Number.parseInt(x, 10))
    .filter((n) => Number.isFinite(n) && n > 0)
}

function toggle(list: string[], v: string): string[] {
  return list.includes(v) ? list.filter((x) => x !== v) : [...list, v]
}

function Chips({ options, selected, onChange, counts }: { options: string[]; selected: string[]; onChange: (v: string[]) => void; counts?: Record<string, number> }) {
  return (
    <div className="flex flex-wrap gap-1.5">
      {options.map((o) => {
        const on = selected.includes(o)
        return (
          <button
            key={o}
            type="button"
            onClick={() => onChange(toggle(selected, o))}
            className={`rounded-full border px-2.5 py-0.5 text-xs ${on ? 'border-rh-red bg-red-50 text-red-800' : 'border-gray-300 bg-white text-gray-700 hover:bg-gray-50'}`}
          >
            {o}
            {counts && counts[o] !== undefined && <span className="ml-1 text-gray-400">{counts[o]}</span>}
          </button>
        )
      })}
    </div>
  )
}

function RunRow({ initial }: { initial: RunStatus }) {
  const [run, setRun] = useState(initial)
  const [pollError, setPollError] = useState<string | null>(null)
  useEffect(() => setRun(initial), [initial])
  const active = run.status === 'RUNNING' || run.status === 'QUEUED'

  useEffect(() => {
    if (!active) return
    let alive = true
    const t = setInterval(() => {
      api
        .run(run.runId)
        .then((r) => {
          if (alive) {
            setRun(r)
            setPollError(null)
          }
        })
        .catch((e: unknown) => alive && setPollError(e instanceof Error ? e.message : String(e)))
    }, 2000)
    return () => {
      alive = false
      clearInterval(t)
    }
  }, [active, run.runId])

  const p = run.progress
  const frac = p && p.total > 0 ? Math.min(1, p.completed / p.total) : 0
  return (
    <li className="space-y-2 border-b border-gray-100 py-3 last:border-0">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <RunBadge status={run.status} />
            <span className="font-medium">{run.label || <span className="text-gray-400">(no label)</span>}</span>
            <span className="font-mono text-xs text-gray-500">{run.runId}</span>
          </div>
          <div className="mt-0.5 text-xs text-gray-500">
            created {dateTime(run.createdAt)}
            {run.finishedAt && <> · finished {dateTime(run.finishedAt)}</>}
            {run.config?.models && <> · {run.config.models.join(', ')}</>}
          </div>
        </div>
        {run.status === 'COMPLETED' && (
          <div className="flex gap-2 text-sm">
            <Link className="btn" to={`/results/${encodeURIComponent(run.runId)}`}>Results</Link>
            <Link className="btn" to={`/inspect/${encodeURIComponent(run.runId)}`}>Inspect</Link>
          </div>
        )}
      </div>
      {active && p && (
        <div>
          <div className="h-2 overflow-hidden rounded bg-gray-200">
            <div className="h-full bg-rh-red transition-all" style={{ width: `${frac * 100}%` }} />
          </div>
          <div className="mt-1 text-xs text-gray-600">
            {p.completed} / {p.total}
            {p.phase && <> · phase <span className="font-mono">{p.phase}</span></>}
            {p.currentModel && <> · model <span className="font-mono">{p.currentModel}</span></>}
          </div>
        </div>
      )}
      <ErrorBanner error={pollError} />
      {run.errors && run.errors.length > 0 && (
        <ul className="max-h-40 list-disc space-y-0.5 overflow-auto rounded bg-red-50 py-2 pr-2 pl-6 text-xs text-red-800">
          {run.errors.map((e, i) => (
            <li key={i} className="break-all">{e}</li>
          ))}
        </ul>
      )}
    </li>
  )
}

export default function Runner() {
  const models = useAsync(() => api.models(), [])
  const datasets = useAsync(() => api.datasets(), [])
  const runs = useAsync(() => api.runs(), [])
  const [health, setHealth] = useState<Record<string, ModelHealth | { error: string } | undefined>>({})
  const [form, setForm] = useState<Form>({
    models: [],
    dataset: '',
    groups: [],
    languages: [],
    split: 'evaluation',
    seed: '42',
    label: '',
    ...DEFAULTS,
  })
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const set = <K extends keyof Form>(k: K, v: Form[K]) => setForm((f) => ({ ...f, [k]: v }))

  useEffect(() => {
    if (!form.dataset && datasets.data && datasets.data.length > 0) set('dataset', datasets.data[0].name)
  }, [datasets.data, form.dataset])

  useEffect(() => {
    if (!models.data) return
    let alive = true
    setHealth({})
    for (const m of models.data) {
      api
        .health(m.id)
        .then((h) => alive && setHealth((s) => ({ ...s, [m.id]: h })))
        .catch((e: unknown) => alive && setHealth((s) => ({ ...s, [m.id]: { error: e instanceof Error ? e.message : String(e) } })))
    }
    return () => {
      alive = false
    }
  }, [models.data])

  // Refresh the list periodically while something is in flight, to pick up new/finished runs.
  const anyActive = runs.data?.some((r) => r.status === 'RUNNING' || r.status === 'QUEUED') ?? false
  useEffect(() => {
    if (!anyActive) return
    const t = setInterval(runs.reload, 10000)
    return () => clearInterval(t)
  }, [anyActive, runs.reload])

  const dataset = datasets.data?.find((d) => d.name === form.dataset)
  const splits = dataset && Object.keys(dataset.splits ?? {}).length > 0 ? Object.keys(dataset.splits) : ['evaluation', 'calibration']
  const groupOptions = dataset && Object.keys(dataset.groups ?? {}).length > 0 ? Array.from(new Set([...GROUPS, ...Object.keys(dataset.groups)])) : GROUPS
  const concurrency = parseConcurrency(form.concurrency)
  const sortedRuns = useMemo(
    () => [...(runs.data ?? [])].sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? '')),
    [runs.data],
  )

  const submit = async () => {
    setSubmitting(true)
    setSubmitError(null)
    const body: BenchmarkConfig = {
      models: form.models,
      dataset: form.dataset,
      groups: form.groups.length ? form.groups : null,
      split: form.split,
      languages: form.languages.length ? form.languages : null,
      limit: form.limit.trim() ? toInt(form.limit, 0) : null,
      warmup: toInt(form.warmup, 10),
      repetitions: toInt(form.repetitions, 5),
      latencySampleSize: toInt(form.latencySampleSize, 30),
      concurrency: concurrency.length ? concurrency : [1],
      throughputRequests: toInt(form.throughputRequests, 40),
      timeoutSeconds: toInt(form.timeoutSeconds, 180),
      coldStart: form.coldStart,
      seed: toInt(form.seed, 42),
      label: form.label.trim() || null,
    }
    try {
      await api.startRun(body)
      runs.reload()
    } catch (e) {
      setSubmitError(e instanceof Error ? e.message : String(e))
    } finally {
      setSubmitting(false)
    }
  }

  const numField = (k: keyof Form, label: string, hint?: string) => (
    <Field label={label} hint={hint}>
      <input className="input" type="number" min={0} value={form[k] as string} onChange={(e) => set(k, e.target.value as never)} />
    </Field>
  )

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-semibold">Benchmark runner</h1>
      <ErrorBanner error={models.error ?? datasets.error} onRetry={() => { models.reload(); datasets.reload() }} />

      <Card
        title="New run"
        actions={
          <>
            <span className="text-xs text-gray-500">Presets:</span>
            <button type="button" className="btn" onClick={() => setForm((f) => ({ ...f, ...SMOKE }))}>Smoke</button>
            <button type="button" className="btn" onClick={() => setForm((f) => ({ ...f, ...DEFAULTS }))}>Default</button>
          </>
        }
      >
        <div className="space-y-4">
          <div>
            <div className="mb-1 text-sm font-medium text-gray-700">Models</div>
            {models.loading && <Loading />}
            {models.data && models.data.length === 0 && <Empty>No models registered in the backend.</Empty>}
            <div className="grid gap-1.5 sm:grid-cols-2 lg:grid-cols-3">
              {models.data?.map((m) => {
                const h = health[m.id]
                return (
                  <label key={m.id} className={`flex cursor-pointer items-center gap-2 rounded-md border px-2.5 py-1.5 text-sm ${form.models.includes(m.id) ? 'border-rh-red bg-red-50' : 'border-gray-200 hover:bg-gray-50'}`}>
                    <input type="checkbox" className="accent-rh-red" checked={form.models.includes(m.id)} onChange={() => set('models', toggle(form.models, m.id))} />
                    <ModelSwatch color={colorFor(m.id)} />
                    <span className="min-w-0 flex-1 truncate" title={m.id}>{m.displayName || m.id}</span>
                    <HealthBadge
                      loading={h === undefined}
                      state={h && 'state' in h ? h.state : undefined}
                      detail={h && 'state' in h ? h.detail : undefined}
                      error={h && 'error' in h ? h.error : undefined}
                    />
                  </label>
                )
              })}
            </div>
          </div>

          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Field label="Dataset" hint={dataset ? `${dataset.records} records · v${dataset.version ?? '?'}` : undefined}>
              <select className="input" value={form.dataset} onChange={(e) => set('dataset', e.target.value)}>
                {!datasets.data?.length && <option value="">{datasets.loading ? 'Loading…' : 'No datasets'}</option>}
                {datasets.data?.map((d) => (
                  <option key={d.name} value={d.name}>{d.name}</option>
                ))}
              </select>
            </Field>
            <Field label="Split">
              <select className="input" value={form.split} onChange={(e) => set('split', e.target.value)}>
                {splits.map((s) => (
                  <option key={s} value={s}>{s}{dataset?.splits?.[s] !== undefined ? ` (${dataset.splits[s]})` : ''}</option>
                ))}
              </select>
            </Field>
            <Field label="Label">
              <input className="input" value={form.label} onChange={(e) => set('label', e.target.value)} placeholder="optional" />
            </Field>
            {numField('limit', 'Limit', 'empty = all records')}
          </div>

          <div className="grid gap-3 md:grid-cols-2">
            <div>
              <div className="mb-1 text-sm font-medium text-gray-700">Groups <span className="font-normal text-gray-400">(none = all)</span></div>
              <Chips options={groupOptions} selected={form.groups} onChange={(v) => set('groups', v)} counts={dataset?.groups} />
            </div>
            <div>
              <div className="mb-1 text-sm font-medium text-gray-700">Languages <span className="font-normal text-gray-400">(none = all)</span></div>
              <Chips options={LANGUAGES} selected={form.languages} onChange={(v) => set('languages', v)} counts={dataset?.languages} />
            </div>
          </div>

          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
            {numField('warmup', 'Warmup')}
            {numField('repetitions', 'Repetitions')}
            {numField('latencySampleSize', 'Latency sample')}
            <Field label="Concurrency" hint={concurrency.length ? `[${concurrency.join(', ')}]` : 'invalid → [1]'}>
              <input className="input" value={form.concurrency} onChange={(e) => set('concurrency', e.target.value)} placeholder="1,4,8" />
            </Field>
            {numField('throughputRequests', 'Throughput req.')}
            {numField('timeoutSeconds', 'Timeout (s)')}
            {numField('seed', 'Seed')}
            <label className="flex items-center gap-2 self-end pb-2 text-sm">
              <input type="checkbox" className="accent-rh-red" checked={form.coldStart} onChange={(e) => set('coldStart', e.target.checked)} />
              Cold start
            </label>
          </div>

          <ErrorBanner error={submitError} />
          <button type="button" className="btn-primary" disabled={submitting || form.models.length === 0 || !form.dataset} onClick={submit}>
            {submitting ? 'Starting…' : `Start benchmark (${form.models.length} model${form.models.length === 1 ? '' : 's'})`}
          </button>
        </div>
      </Card>

      <Card title="Runs" actions={<button type="button" className="btn" onClick={runs.reload}>Refresh</button>}>
        <ErrorBanner error={runs.error} onRetry={runs.reload} />
        {runs.loading && !runs.data && <Loading />}
        {runs.data && runs.data.length === 0 && <Empty>No benchmark runs yet. Start one above.</Empty>}
        <ul>
          {sortedRuns.map((r) => (
            <RunRow key={r.runId} initial={r} />
          ))}
        </ul>
      </Card>
    </div>
  )
}
