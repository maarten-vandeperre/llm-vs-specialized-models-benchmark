import { useEffect, useMemo, useState } from 'react'
import { api, GROUPS, LANGUAGES, TASKS, type BenchmarkResponse, type Task } from '../api'
import { useAsync } from '../hooks'
import { DecisionView, ExtractionView, LabelsTable, TimingView } from '../components/PredictionView'
import { Card, Collapsible, ErrorBanner, Field, HealthBadge, JsonBlock, OutcomeBadge, SyntheticNote } from '../components/ui'

const DATASET = 'coffee-v1'

export default function Playground() {
  const models = useAsync(() => api.models(), [])
  const [modelId, setModelId] = useState('')
  const [task, setTask] = useState<Task>('CLASSIFICATION')
  const [language, setLanguage] = useState('')
  const [text, setText] = useState('')
  const [group, setGroup] = useState('')
  const [running, setRunning] = useState(false)
  const [runError, setRunError] = useState<string | null>(null)
  const [result, setResult] = useState<BenchmarkResponse | null>(null)

  const model = useMemo(() => models.data?.find((m) => m.id === modelId), [models.data, modelId])
  const supported = model?.capabilities?.tasks ?? []

  useEffect(() => {
    if (!modelId && models.data && models.data.length > 0) setModelId(models.data[0].id)
  }, [models.data, modelId])

  useEffect(() => {
    if (model && supported.length > 0 && !supported.includes(task)) setTask(supported[0])
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [model])

  const health = useAsync(() => api.health(modelId), [modelId], !!modelId)
  const examples = useAsync(() => api.records(DATASET, { group, limit: 20 }), [group], !!group)

  const run = async () => {
    setRunning(true)
    setRunError(null)
    setResult(null)
    try {
      setResult(await api.infer({ modelId, task, text, language: language || null }))
    } catch (e) {
      setRunError(e instanceof Error ? e.message : String(e))
    } finally {
      setRunning(false)
    }
  }

  const loadExample = (id: string) => {
    const rec = examples.data?.find((r) => r.id === id)
    if (!rec) return
    setText(rec.text)
    if (LANGUAGES.includes(rec.language)) setLanguage(rec.language)
    if (supported.length === 0 || supported.includes(rec.task)) setTask(rec.task)
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-end justify-between gap-2">
        <h1 className="text-xl font-semibold">Playground</h1>
        <SyntheticNote />
      </div>
      <ErrorBanner error={models.error} onRetry={models.reload} />

      <div className="grid gap-4 lg:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
        <Card title="Input">
          <div className="space-y-3">
            <Field label="Model">
              <div className="flex items-center gap-2">
                <select className="input" value={modelId} onChange={(e) => setModelId(e.target.value)} disabled={!models.data?.length}>
                  {!models.data?.length && <option value="">{models.loading ? 'Loading…' : 'No models'}</option>}
                  {models.data?.map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.displayName || m.id} {m.kind === 'pipeline' ? '(pipeline)' : ''}
                    </option>
                  ))}
                </select>
                {modelId && <HealthBadge state={health.data?.state} detail={health.data?.detail} loading={health.loading} error={health.error} />}
              </div>
            </Field>
            {model && (
              <p className="text-xs text-gray-500">
                {[model.runtime, model.modelRef, model.quantization].filter(Boolean).join(' · ')}
                {model.notes && <> — {model.notes}</>}
                {health.data?.detail && <span className="block">Health: {health.data.detail}</span>}
              </p>
            )}
            <div className="grid grid-cols-2 gap-3">
              <Field label="Task">
                <select className="input" value={task} onChange={(e) => setTask(e.target.value as Task)}>
                  {TASKS.map((t) => (
                    <option key={t} value={t} disabled={!!model && !supported.includes(t)}>
                      {t}
                      {model && !supported.includes(t) ? ' (unsupported)' : ''}
                    </option>
                  ))}
                </select>
              </Field>
              <Field label="Language">
                <select className="input" value={language} onChange={(e) => setLanguage(e.target.value)}>
                  <option value="">(not specified)</option>
                  {LANGUAGES.map((l) => (
                    <option key={l} value={l}>{l}</option>
                  ))}
                </select>
              </Field>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <Field label="Example group">
                <select className="input" value={group} onChange={(e) => setGroup(e.target.value)}>
                  <option value="">Load example…</option>
                  {GROUPS.map((g) => (
                    <option key={g} value={g}>{g}</option>
                  ))}
                </select>
              </Field>
              <Field label="Example record">
                <select className="input" value="" onChange={(e) => loadExample(e.target.value)} disabled={!examples.data?.length}>
                  <option value="">{examples.loading ? 'Loading…' : group ? `${examples.data?.length ?? 0} records` : '—'}</option>
                  {examples.data?.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.id} [{r.language}] {r.text.slice(0, 50)}
                    </option>
                  ))}
                </select>
              </Field>
            </div>
            <ErrorBanner error={examples.error} />
            <Field label="Text">
              <textarea className="input min-h-48 font-mono text-xs" value={text} onChange={(e) => setText(e.target.value)} placeholder="Paste a customer message…" />
            </Field>
            <button type="button" className="btn-primary w-full" disabled={!modelId || !text.trim() || running || (!!model && !supported.includes(task))} onClick={run}>
              {running ? 'Running…' : 'Run inference'}
            </button>
          </div>
        </Card>

        <div className="min-w-0 space-y-4">
          <ErrorBanner error={runError} />
          {!result && !runError && <Card title="Result"><p className="text-sm text-gray-500">Run a model to see its prediction.</p></Card>}
          {result && (
            <>
              <Card title="Result" actions={<OutcomeBadge outcome={result.outcome} />}>
                <div className="space-y-1 text-xs text-gray-500">
                  <div>
                    model <span className="font-mono">{result.modelId}</span> · task {result.task} · request <span className="font-mono">{result.requestId}</span>
                  </div>
                  {result.error && <ErrorBanner error={result.error} />}
                </div>
              </Card>
              {result.prediction && (
                <>
                  <Card title="Labels"><LabelsTable labels={result.prediction.labels} /></Card>
                  {(result.prediction.extraction || result.task !== 'CLASSIFICATION') && (
                    <Card title="Extraction"><ExtractionView extraction={result.prediction.extraction} /></Card>
                  )}
                  {(result.prediction.decision || (result.prediction.route?.length ?? 0) > 0) && (
                    <Card title="Decision & route"><DecisionView prediction={result.prediction} /></Card>
                  )}
                </>
              )}
              <Card title="Timing"><TimingView timing={result.timing} /></Card>
              <Collapsible title="Raw response">
                <JsonBlock value={result.rawResponse} />
                {result.metadata && Object.keys(result.metadata).length > 0 && (
                  <div className="mt-3">
                    <div className="mb-1 text-xs font-semibold text-gray-600 uppercase">Metadata</div>
                    <JsonBlock value={result.metadata} />
                  </div>
                )}
              </Collapsible>
            </>
          )}
        </div>
      </div>
    </div>
  )
}
