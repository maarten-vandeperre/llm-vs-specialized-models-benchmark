import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { api, GROUPS, OUTCOMES, type Gold, type PredictionRecord } from '../api'
import { colorFor } from '../colors'
import { display, isNum, ms, nanosToMs, pct } from '../format'
import { useAsync } from '../hooks'
import RunPicker from '../components/RunPicker'
import { displayGoldItems, ExtractionView, LabelsTable, TimingView, DecisionView } from '../components/PredictionView'
import { Card, Check, Collapsible, Empty, ErrorBanner, Field, JsonBlock, Loading, ModelSwatch, OutcomeBadge, SyntheticNote, TableWrap, Val } from '../components/ui'

const PAGE = 50
const PHASES = ['cold', 'warmup', 'quality', 'latency', 'throughput']

interface Mark {
  start: number
  end: number
  kind: 'gold' | 'pred'
}

/** Highlights gold (green underline) and predicted (red background) spans. */
function HighlightedText({ text, marks }: { text: string; marks: Mark[] }) {
  const valid = marks.filter((m) => isNum(m.start) && isNum(m.end) && m.start >= 0 && m.end <= text.length && m.start < m.end)
  if (valid.length === 0) return <pre className="whitespace-pre-wrap break-words font-mono text-xs">{text}</pre>
  const cuts = Array.from(new Set([0, text.length, ...valid.flatMap((m) => [m.start, m.end])])).sort((a, b) => a - b)
  const parts = []
  for (let i = 0; i < cuts.length - 1; i++) {
    const a = cuts[i]
    const b = cuts[i + 1]
    const gold = valid.some((m) => m.kind === 'gold' && m.start <= a && m.end >= b)
    const pred = valid.some((m) => m.kind === 'pred' && m.start <= a && m.end >= b)
    const cls = `${gold ? 'border-b-2 border-green-600' : ''} ${pred ? 'bg-red-100' : ''}`
    parts.push(
      <span key={a} className={cls} title={gold && pred ? 'gold + predicted' : gold ? 'gold span' : pred ? 'predicted span' : undefined}>
        {text.slice(a, b)}
      </span>,
    )
  }
  return <pre className="whitespace-pre-wrap break-words font-mono text-xs leading-relaxed">{parts}</pre>
}

function GoldView({ gold }: { gold: Gold | null }) {
  if (!gold) return <Val v={null} />
  const rows: [string, unknown][] = [
    ['intent', gold.intent],
    ['sentiment', gold.sentiment],
    ['category', gold.category],
    ['complaint', gold.complaint],
    ['decision', gold.decision],
    ['orderId', gold.orderId],
    ['orderIdSpan', gold.orderIdSpan ? `[${gold.orderIdSpan.start}, ${gold.orderIdSpan.end})` : null],
    ['items', gold.items && gold.items.length ? displayGoldItems(gold.items) : null],
    ['total', gold.total],
    ['currency', gold.currency],
  ]
  return (
    <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-sm">
      {rows.map(([k, v]) => (
        <div key={k} className="contents">
          <dt className="text-gray-600">{k}</dt>
          <dd className="break-words font-mono text-xs">{v === null || v === undefined ? <span className="text-gray-400">—</span> : String(v)}</dd>
        </div>
      ))}
    </dl>
  )
}

function Detail({ rec, onClose }: { rec: PredictionRecord; onClose: () => void }) {
  const marks: Mark[] = []
  if (rec.gold?.orderIdSpan) marks.push({ ...rec.gold.orderIdSpan, kind: 'gold' })
  if (rec.gold?.totalSpan) marks.push({ ...rec.gold.totalSpan, kind: 'gold' })
  const ex = rec.prediction?.extraction
  if (ex?.orderIdSpan) marks.push({ start: ex.orderIdSpan.start, end: ex.orderIdSpan.end, kind: 'pred' })
  if (ex?.totalSpan) marks.push({ start: ex.totalSpan.start, end: ex.totalSpan.end, kind: 'pred' })
  const ev = rec.evaluation

  return (
    <Card
      title={
        <span className="flex flex-wrap items-center gap-2">
          <OutcomeBadge outcome={rec.outcome} />
          <span className="font-mono text-xs">{rec.recordId}</span>
          {ev && <Check ok={ev.correct} />}
        </span>
      }
      actions={<button type="button" className="btn" onClick={onClose}>Close</button>}
    >
      <div className="space-y-4">
        <div className="text-xs text-gray-500">
          <ModelSwatch color={colorFor(rec.modelId)} />
          {rec.modelId} · {rec.group} · {rec.language} · {rec.task} · phase {rec.phase}
          {rec.repetition != null && <> · rep {rec.repetition}</>}
          {rec.concurrency != null && <> · conc {rec.concurrency}</>}
        </div>
        {rec.error && <ErrorBanner error={rec.error} />}
        <div>
          <div className="mb-1 flex flex-wrap items-center gap-3 text-xs font-semibold text-gray-600 uppercase">
            Input
            <span className="font-normal normal-case"><span className="border-b-2 border-green-600">gold span</span> · <span className="bg-red-100">predicted span</span></span>
          </div>
          <div className="max-h-72 overflow-auto rounded border border-gray-200 bg-gray-50 p-2">
            <HighlightedText text={rec.text ?? ''} marks={marks} />
          </div>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <div>
            <div className="mb-1 text-xs font-semibold text-gray-600 uppercase">Gold</div>
            <GoldView gold={rec.gold} />
          </div>
          <div className="min-w-0 space-y-3">
            <div className="text-xs font-semibold text-gray-600 uppercase">Predicted</div>
            {rec.prediction ? (
              <>
                <LabelsTable labels={rec.prediction.labels} />
                {rec.prediction.extraction && <ExtractionView extraction={rec.prediction.extraction} />}
                <DecisionView prediction={rec.prediction} />
              </>
            ) : (
              <p className="text-sm text-gray-500">No prediction.</p>
            )}
          </div>
        </div>

        <div>
          <div className="mb-1 text-xs font-semibold text-gray-600 uppercase">Evaluation</div>
          {!ev ? (
            <p className="text-sm text-gray-500">Not evaluated.</p>
          ) : (
            <div className="space-y-3">
              {ev.classification && Object.keys(ev.classification).length > 0 && (
                <TableWrap>
                  <thead>
                    <tr>
                      <th>Field</th>
                      <th>Gold</th>
                      <th>Predicted</th>
                      <th>Probability</th>
                      <th>Correct</th>
                    </tr>
                  </thead>
                  <tbody>
                    {Object.entries(ev.classification).map(([f, c]) => (
                      <tr key={f}>
                        <td className="font-medium">{f}</td>
                        <td>{display(c.gold)}</td>
                        <td>{display(c.predicted)}</td>
                        <td><Val v={c.probability}>{pct(c.probability)}</Val></td>
                        <td><Check ok={c.correct} /></td>
                      </tr>
                    ))}
                  </tbody>
                </TableWrap>
              )}
              {ev.extraction && (
                <div className="space-y-1 text-sm">
                  <div className="flex flex-wrap gap-x-4 gap-y-1">
                    <span>exact match <Check ok={ev.extraction.exactMatch} /></span>
                    <span>orderId <Check ok={ev.extraction.fieldCorrect?.orderId} /></span>
                    <span>items <Check ok={ev.extraction.fieldCorrect?.items} /></span>
                    <span>total <Check ok={ev.extraction.fieldCorrect?.total} /></span>
                    <span>currency <Check ok={ev.extraction.fieldCorrect?.currency} /></span>
                    <span>offsets <Check ok={ev.extraction.offsetCorrect} /></span>
                    <span className="text-gray-600">tp {ev.extraction.tp} · fp {ev.extraction.fp} · fn {ev.extraction.fn}</span>
                  </div>
                  {ev.extraction.hallucinated?.length > 0 && <div className="text-red-700">Hallucinated: {ev.extraction.hallucinated.join(', ')}</div>}
                  {ev.extraction.missing?.length > 0 && <div className="text-amber-700">Missing: {ev.extraction.missing.join(', ')}</div>}
                </div>
              )}
              {ev.decision && (
                <div className="text-sm">
                  decision: gold <span className="font-mono">{display(ev.decision.gold)}</span> · predicted <span className="font-mono">{display(ev.decision.predicted)}</span> <Check ok={ev.decision.correct} />
                </div>
              )}
            </div>
          )}
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <div>
            <div className="mb-1 text-xs font-semibold text-gray-600 uppercase">Timing</div>
            <TimingView timing={rec.timing} />
          </div>
        </div>
        <Collapsible title="Raw provider response">
          <JsonBlock value={rec.rawResponse} />
        </Collapsible>
      </div>
    </Card>
  )
}

export default function Inspect() {
  const { runId } = useParams()
  const models = useAsync(() => api.models(), [])
  const [modelId, setModelId] = useState('')
  const [group, setGroup] = useState('')
  const [outcome, setOutcome] = useState('')
  const [phase, setPhase] = useState('')
  const [incorrect, setIncorrect] = useState(false)
  const [offset, setOffset] = useState(0)
  const [selected, setSelected] = useState<PredictionRecord | null>(null)

  useEffect(() => {
    setOffset(0)
    setSelected(null)
  }, [runId, modelId, group, outcome, phase, incorrect])

  const page = useAsync(
    () => api.results(runId!, { modelId, group, outcome, phase, incorrect: incorrect || undefined, offset, limit: PAGE }),
    [runId, modelId, group, outcome, phase, incorrect, offset],
    !!runId,
  )
  const total = page.data?.total ?? 0
  const items = page.data?.items ?? []

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-semibold">Inspect predictions</h1>
        <RunPicker base="/inspect" runId={runId} />
      </div>
      {!runId && <Empty>No run selected. Start one from the <Link className="underline" to="/runner">Runner</Link>.</Empty>}
      {runId && (
        <>
          <SyntheticNote />
          <Card title="Filters">
            <div className="grid grid-cols-2 gap-3 md:grid-cols-5">
              <Field label="Model">
                <select className="input" value={modelId} onChange={(e) => setModelId(e.target.value)}>
                  <option value="">All</option>
                  {models.data?.map((m) => (
                    <option key={m.id} value={m.id}>{m.displayName || m.id}</option>
                  ))}
                </select>
              </Field>
              <Field label="Group">
                <select className="input" value={group} onChange={(e) => setGroup(e.target.value)}>
                  <option value="">All</option>
                  {GROUPS.map((g) => <option key={g} value={g}>{g}</option>)}
                </select>
              </Field>
              <Field label="Outcome">
                <select className="input" value={outcome} onChange={(e) => setOutcome(e.target.value)}>
                  <option value="">All</option>
                  {OUTCOMES.map((o) => <option key={o} value={o}>{o}</option>)}
                </select>
              </Field>
              <Field label="Phase">
                <select className="input" value={phase} onChange={(e) => setPhase(e.target.value)}>
                  <option value="">All</option>
                  {PHASES.map((p) => <option key={p} value={p}>{p}</option>)}
                </select>
              </Field>
              <label className="flex items-center gap-2 self-end pb-2 text-sm">
                <input type="checkbox" className="accent-rh-red" checked={incorrect} onChange={(e) => setIncorrect(e.target.checked)} />
                Only incorrect
              </label>
            </div>
          </Card>

          <div className={`grid gap-4 ${selected ? 'xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]' : ''}`}>
            <Card
              title={`Predictions ${page.data ? `(${total})` : ''}`}
              actions={
                <div className="flex items-center gap-2 text-xs">
                  <button type="button" className="btn" disabled={offset === 0 || page.loading} onClick={() => setOffset(Math.max(0, offset - PAGE))}>‹ Prev</button>
                  <span className="text-gray-600">{total === 0 ? '0' : `${offset + 1}–${Math.min(offset + PAGE, total)}`} of {total}</span>
                  <button type="button" className="btn" disabled={offset + PAGE >= total || page.loading} onClick={() => setOffset(offset + PAGE)}>Next ›</button>
                </div>
              }
            >
              <ErrorBanner error={page.error} onRetry={page.reload} />
              {page.loading && !page.data && <Loading />}
              {page.data && items.length === 0 && <Empty>No predictions match these filters.</Empty>}
              {items.length > 0 && (
                <TableWrap>
                  <thead>
                    <tr>
                      <th>Record</th>
                      <th>Model</th>
                      <th>Group</th>
                      <th>Lang</th>
                      <th>Phase</th>
                      <th>Outcome</th>
                      <th>Correct</th>
                      <th>Client ms</th>
                      <th>Text</th>
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((r, i) => {
                      const isSel = selected === r
                      return (
                        <tr key={`${r.modelId}-${r.recordId}-${r.phase}-${r.repetition}-${i}`} onClick={() => setSelected(r)} className={`cursor-pointer ${isSel ? 'bg-red-50' : ''}`}>
                          <td className="font-mono text-xs">{r.recordId}</td>
                          <td>
                            <ModelSwatch color={colorFor(r.modelId)} />
                            {r.modelId}
                          </td>
                          <td>{r.group}</td>
                          <td>{r.language}</td>
                          <td>{r.phase}</td>
                          <td><OutcomeBadge outcome={r.outcome} /></td>
                          <td><Check ok={r.evaluation?.correct} /></td>
                          <td><Val v={r.timing?.clientNanos}>{ms(nanosToMs(r.timing?.clientNanos), 1)}</Val></td>
                          <td className="max-w-64 truncate text-gray-600" title={r.text}>{r.text}</td>
                        </tr>
                      )
                    })}
                  </tbody>
                </TableWrap>
              )}
            </Card>
            {selected && <Detail rec={selected} onClose={() => setSelected(null)} />}
          </div>
        </>
      )}
    </div>
  )
}
