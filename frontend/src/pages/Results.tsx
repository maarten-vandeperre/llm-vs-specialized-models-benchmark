import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { api, type Ci, type ModelReport } from '../api'
import { colorFor } from '../colors'
import { bytes, ci, dateTime, isNum, ms, num, pct } from '../format'
import { useAsync } from '../hooks'
import RunPicker from '../components/RunPicker'
import {
  AccuracyLatencyScatter,
  AccuracyMemoryScatter,
  CoverageChart,
  FailureChart,
  LatencyChart,
  PerLengthCharts,
  ReliabilityChart,
  ThroughputChart,
} from '../components/ResultCharts'
import { Card, Collapsible, Empty, ErrorBanner, HealthBadge, KeyValue, Loading, ModelSwatch, RunBadge, SyntheticNote, TableWrap, Val } from '../components/ui'

function P({ v, c, reason }: { v: number | null | undefined; c?: Ci; reason?: string }) {
  return (
    <Val v={v} reason={reason}>
      {pct(v)}
      {c && <span className="ml-1 text-xs text-gray-400">{ci(c)}</span>}
    </Val>
  )
}

function ModelCell({ m }: { m: ModelReport }) {
  return (
    <td className="font-medium">
      <ModelSwatch color={colorFor(m.modelId)} />
      <span title={m.modelId}>{m.displayName || m.modelId}</span>
    </td>
  )
}

function QualityTable({ models }: { models: ModelReport[] }) {
  return (
    <TableWrap>
      <thead>
        <tr>
          <th>Model</th>
          <th>Health</th>
          <th>Cls. accuracy [95% CI]</th>
          <th>Macro-F1 [95% CI]</th>
          <th>Extr. exact match [CI]</th>
          <th>Entity F1</th>
          <th>Hallucination</th>
          <th>Missing</th>
          <th>Offset acc.</th>
          <th>Decision acc.</th>
          <th>Abstention (ambiguous)</th>
          <th>Total</th>
          <th>Malformed</th>
          <th>Timeouts</th>
          <th>Errors</th>
          <th>Unsupported</th>
        </tr>
      </thead>
      <tbody>
        {models.map((m) => (
          <tr key={m.modelId}>
            <ModelCell m={m} />
            <td><HealthBadge state={m.health?.state} detail={m.health?.detail} /></td>
            <td><P v={m.classification?.accuracy} c={m.classification?.accuracyCi} reason={m.classification ? undefined : 'Classification not evaluated for this model'} /></td>
            <td><P v={m.classification?.macroF1} c={m.classification?.macroF1Ci} /></td>
            <td><P v={m.extraction?.exactMatch} c={m.extraction?.exactMatchCi} reason={m.extraction ? undefined : 'Extraction not evaluated for this model'} /></td>
            <td><P v={m.extraction?.entityF1} /></td>
            <td><P v={m.extraction?.hallucinationRate} /></td>
            <td><P v={m.extraction?.missingRate} /></td>
            <td><P v={m.extraction?.offsetAccuracy} reason="Model does not return span offsets" /></td>
            <td><P v={m.decision?.accuracy} c={m.decision?.accuracyCi} /></td>
            <td><P v={m.abstention?.abstentionRate} /></td>
            <td><Val v={m.counts?.total} /></td>
            <td><Val v={m.counts?.malformed} /></td>
            <td><Val v={m.counts?.timeout} /></td>
            <td><Val v={m.counts?.infraError} /></td>
            <td><Val v={m.counts?.unsupported} /></td>
          </tr>
        ))}
      </tbody>
    </TableWrap>
  )
}

function PerformanceTable({ models }: { models: ModelReport[] }) {
  return (
    <TableWrap>
      <thead>
        <tr>
          <th>Model</th>
          <th>n</th>
          <th>p50</th>
          <th>p90</th>
          <th>p95</th>
          <th>p99</th>
          <th>Server p50</th>
          <th>Cold start</th>
          <th>Load</th>
          <th>Tok in/out</th>
          <th>Out tok/s</th>
          <th>RSS mean/peak</th>
          <th>CPU mean/peak</th>
          <th>Storage</th>
          <th>GPU</th>
          <th>Energy</th>
        </tr>
      </thead>
      <tbody>
        {models.map((m) => (
          <tr key={m.modelId}>
            <ModelCell m={m} />
            <td><Val v={m.latency?.n} /></td>
            <td><Val v={m.latency?.p50}>{ms(m.latency?.p50)}</Val></td>
            <td><Val v={m.latency?.p90}>{ms(m.latency?.p90)}</Val></td>
            <td><Val v={m.latency?.p95}>{ms(m.latency?.p95)}</Val></td>
            <td><Val v={m.latency?.p99} reason="Too few samples for p99">{ms(m.latency?.p99)}</Val></td>
            <td><Val v={m.serverLatency?.p50}>{ms(m.serverLatency?.p50)}</Val></td>
            <td><Val v={m.coldStart?.clientMs} reason="Cold start not measured">{ms(m.coldStart?.clientMs, 0)}</Val></td>
            <td><Val v={m.coldStart?.loadMs}>{ms(m.coldStart?.loadMs, 0)}</Val></td>
            <td>
              <Val v={m.tokens?.meanInput}>{num(m.tokens?.meanInput, 0)}</Val> / <Val v={m.tokens?.meanOutput}>{num(m.tokens?.meanOutput, 0)}</Val>
            </td>
            <td><Val v={m.tokens?.outputTokensPerSecond}>{num(m.tokens?.outputTokensPerSecond, 1)}</Val></td>
            <td>
              <Val v={m.resources?.rssMeanMb}>{num(m.resources?.rssMeanMb, 0)}</Val> / <Val v={m.resources?.rssPeakMb}>{num(m.resources?.rssPeakMb, 0)} MB</Val>
            </td>
            <td>
              <Val v={m.resources?.cpuMeanPct}>{num(m.resources?.cpuMeanPct, 0)}</Val> / <Val v={m.resources?.cpuPeakPct}>{num(m.resources?.cpuPeakPct, 0)}%</Val>
            </td>
            <td><Val v={m.resources?.modelStorageBytes}>{bytes(m.resources?.modelStorageBytes)}</Val></td>
            <td className="max-w-48 truncate" title={m.resources?.gpu ?? undefined}><Val v={m.resources?.gpu} /></td>
            <td className="max-w-48 truncate" title={m.resources?.energy ?? undefined}><Val v={m.resources?.energy} /></td>
          </tr>
        ))}
      </tbody>
    </TableWrap>
  )
}

function CalibrationSection({ models }: { models: ModelReport[] }) {
  const avail = models.filter((m) => m.calibration?.available)
  const unavailable = models.filter((m) => !m.calibration?.available)
  return (
    <div className="space-y-4">
      <div className="grid gap-4 lg:grid-cols-2">
        <div>
          <div className="mb-1 text-xs font-semibold text-gray-600">Reliability diagram (dashed = perfect calibration)</div>
          <ReliabilityChart models={models} />
        </div>
        <div>
          <div className="mb-1 text-xs font-semibold text-gray-600">Accuracy vs coverage</div>
          <CoverageChart models={models} />
        </div>
      </div>
      {avail.length > 0 && (
        <TableWrap>
          <thead>
            <tr>
              <th>Model</th>
              <th>n</th>
              <th>Brier</th>
              <th>ECE</th>
              <th>Risk @ threshold</th>
            </tr>
          </thead>
          <tbody>
            {avail.map((m) => {
              const c = m.calibration!
              return (
                <tr key={m.modelId}>
                  <ModelCell m={m} />
                  <td>{c.n}</td>
                  <td><Val v={c.brier}>{num(c.brier, 4)}</Val></td>
                  <td><Val v={c.ece}>{num(c.ece, 4)}</Val></td>
                  <td>
                    {c.riskAtThreshold ? (
                      <>
                        risk {pct(c.riskAtThreshold.risk)} at coverage {pct(c.riskAtThreshold.coverage)} (t = {num(c.riskAtThreshold.threshold, 2)})
                      </>
                    ) : (
                      <Val v={null} />
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </TableWrap>
      )}
      {unavailable.length > 0 && (
        <div className="text-sm">
          <div className="mb-1 font-medium text-gray-700">Calibration not available</div>
          <ul className="space-y-0.5 text-gray-600">
            {unavailable.map((m) => (
              <li key={m.modelId}>
                <ModelSwatch color={colorFor(m.modelId)} />
                {m.displayName || m.modelId}: <span className="text-gray-500">{m.calibration?.reason ?? 'no reason given'}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  )
}

function BreakdownTable({ models, pick, title }: { models: ModelReport[]; pick: (m: ModelReport) => Record<string, { n: number; classificationAccuracy: number | null; extractionExactMatch: number | null }> | null; title: string }) {
  const keys = Array.from(new Set(models.flatMap((m) => Object.keys(pick(m) ?? {})))).sort()
  if (keys.length === 0) return <p className="text-sm text-gray-500">No {title} breakdown reported.</p>
  return (
    <TableWrap>
      <thead>
        <tr>
          <th rowSpan={2}>Model</th>
          {keys.map((k) => (
            <th key={k} colSpan={3} className="text-center">{k}</th>
          ))}
        </tr>
        <tr>
          {keys.map((k) => [
            <th key={`${k}n`}>n</th>,
            <th key={`${k}c`}>Cls. acc</th>,
            <th key={`${k}e`}>Extr. EM</th>,
          ])}
        </tr>
      </thead>
      <tbody>
        {models.map((m) => {
          const b = pick(m) ?? {}
          return (
            <tr key={m.modelId}>
              <ModelCell m={m} />
              {keys.map((k) => [
                <td key={`${k}n`}><Val v={b[k]?.n} /></td>,
                <td key={`${k}c`}><P v={b[k]?.classificationAccuracy} /></td>,
                <td key={`${k}e`}><P v={b[k]?.extractionExactMatch} /></td>,
              ])}
            </tr>
          )
        })}
      </tbody>
    </TableWrap>
  )
}

function PerLengthTable({ models }: { models: ModelReport[] }) {
  const rows = models.flatMap((m) => (m.perLength ?? []).map((p) => ({ m, p })))
  if (rows.length === 0) return null
  return (
    <Collapsible title="Per-length table">
      <TableWrap>
        <thead>
          <tr>
            <th>Model</th>
            <th>Target tokens</th>
            <th>Position</th>
            <th>n</th>
            <th>Exact match</th>
            <th>Unsupported</th>
            <th>Latency p50</th>
          </tr>
        </thead>
        <tbody>
          {rows.map(({ m, p }, i) => (
            <tr key={i}>
              <ModelCell m={m} />
              <td>{p.targetTokens}</td>
              <td><Val v={p.position} /></td>
              <td>{p.n}</td>
              <td><P v={p.exactMatch} /></td>
              <td>{p.unsupported}</td>
              <td><Val v={p.latencyP50}>{ms(p.latencyP50)}</Val></td>
            </tr>
          ))}
        </tbody>
      </TableWrap>
    </Collapsible>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return <Card title={title}>{children}</Card>
}

export default function Results() {
  const { runId } = useParams()
  const report = useAsync(() => api.report(runId!), [runId], !!runId)
  const r = report.data
  const models = r?.models ?? []

  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-xl font-semibold">Results</h1>
        <RunPicker base="/results" runId={runId} />
      </div>
      {!runId && <Empty>No run selected. Completed runs appear here — start one from the <Link className="underline" to="/runner">Runner</Link>.</Empty>}
      <ErrorBanner error={report.error} onRetry={report.reload} />
      {report.loading && <Loading label="Loading report…" />}
      {r && (
        <>
          <SyntheticNote />
          <Card
            title={
              <span className="flex flex-wrap items-center gap-2">
                <RunBadge status={r.status} />
                {r.label || r.runId}
              </span>
            }
            actions={
              <>
                <a className="btn" href={api.reportMarkdownUrl(r.runId)} download>Download markdown report</a>
                <a className="btn" href={api.resultsCsvUrl(r.runId)} download>Download CSV</a>
                <Link className="btn" to={`/inspect/${encodeURIComponent(r.runId)}`}>Inspect predictions</Link>
              </>
            }
          >
            <div className="space-y-2 text-sm">
              <div className="text-gray-600">
                run <span className="font-mono text-xs">{r.runId}</span> · dataset {r.datasetVersion ?? 'n/a'} · created {dateTime(r.createdAt)} · finished {dateTime(r.finishedAt)}
                {!r.synthetic && <span className="ml-2 text-amber-700">(report not flagged synthetic)</span>}
              </div>
              <div className="grid gap-2 md:grid-cols-2">
                <Collapsible title="Environment"><KeyValue data={r.environment} /></Collapsible>
                <Collapsible title="Configuration"><KeyValue data={r.config} /></Collapsible>
              </div>
            </div>
          </Card>

          {models.length === 0 ? (
            <Empty>This report contains no model results.</Empty>
          ) : (
            <>
              <Section title="Quality comparison"><QualityTable models={models} /></Section>
              <div className="grid gap-4 lg:grid-cols-2">
                <Section title="Latency (client p50 / p95)"><LatencyChart models={models} /></Section>
                <Section title="Throughput by concurrency"><ThroughputChart models={models} /></Section>
                <Section title="Quality vs latency"><AccuracyLatencyScatter models={models} /></Section>
                <Section title="Quality vs memory"><AccuracyMemoryScatter models={models} /></Section>
              </div>
              <Section title="Latency, tokens & resources"><PerformanceTable models={models} /></Section>
              <Section title="Calibration"><CalibrationSection models={models} /></Section>
              <div className="grid gap-4 lg:grid-cols-2">
                <Section title="Per-language quality"><BreakdownTable models={models} pick={(m) => m.perLanguage} title="language" /></Section>
                <Section title="Per-group quality"><BreakdownTable models={models} pick={(m) => m.perGroup} title="group" /></Section>
              </div>
              <Section title="Long inputs (per length)">
                <div className="space-y-3">
                  <PerLengthCharts models={models} />
                  <PerLengthTable models={models} />
                </div>
              </Section>
              <Section title="Failure outcomes"><FailureChart models={models} /></Section>
              <p className="text-xs text-gray-500">
                All values come from the backend report. "n/a" means not measured or not applicable (hover for the reason when available). {isNum(models.length) && `${models.length} model(s).`}
              </p>
            </>
          )}
        </>
      )}
    </div>
  )
}
