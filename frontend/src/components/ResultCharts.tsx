import { useState, type ReactNode } from 'react'
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  LabelList,
  Legend,
  Line,
  LineChart,
  ReferenceLine,
  ResponsiveContainer,
  Scatter,
  ScatterChart,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import type { ModelReport } from '../api'
import { colorFor, OUTCOME_COLORS } from '../colors'
import { isNum, num, pct } from '../format'

const AXIS = { fontSize: 11, fill: '#4f5255' }
const GRID = <CartesianGrid stroke="#e8e8e8" strokeDasharray="3 3" />
// Legend on top (clear of axis labels); text stays in neutral ink, the swatch carries identity.
const LEGEND = {
  verticalAlign: 'top' as const,
  wrapperStyle: { fontSize: 12 },
  formatter: (v: unknown) => <span style={{ color: '#4f5255' }}>{String(v)}</span>,
}
const name = (m: ModelReport) => m.displayName || m.modelId

function ChartBox({ height = 300, children }: { height?: number; children: ReactNode }) {
  return (
    <div style={{ width: '100%', height }}>
      <ResponsiveContainer>{children as React.ReactElement}</ResponsiveContainer>
    </div>
  )
}

function Missing({ models, what }: { models: ModelReport[]; what: string }) {
  if (models.length === 0) return null
  return (
    <p className="mt-2 text-xs text-gray-500">
      Not plotted ({what} not measured): {models.map(name).join(', ')}
    </p>
  )
}

const fmtPct = (v: unknown) => (isNum(v) ? pct(v) : 'n/a')
const fmtMs = (v: unknown) => (isNum(v) ? `${num(v, 1)} ms` : 'n/a')

export function LatencyChart({ models }: { models: ModelReport[] }) {
  const ok = models.filter((m) => m.latency && (isNum(m.latency.p50) || isNum(m.latency.p95)))
  const data = ok.map((m) => ({ id: m.modelId, name: name(m), p50: m.latency!.p50, p95: m.latency!.p95 }))
  if (data.length === 0) return <p className="text-sm text-gray-500">No latency measurements in this run.</p>
  return (
    <>
      <ChartBox>
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 8, left: 8 }} barGap={2}>
          {GRID}
          <XAxis dataKey="name" tick={AXIS} interval={0} />
          <YAxis tick={AXIS} label={{ value: 'client ms', angle: -90, position: 'insideLeft', style: AXIS }} />
          <Tooltip formatter={fmtMs} />
          <Bar dataKey="p50" name="p50" radius={[4, 4, 0, 0]} isAnimationActive={false}>
            {data.map((d) => <Cell key={d.id} fill={colorFor(d.id)} />)}
          </Bar>
          <Bar dataKey="p95" name="p95" radius={[4, 4, 0, 0]} isAnimationActive={false}>
            {data.map((d) => <Cell key={d.id} fill={colorFor(d.id)} fillOpacity={0.4} />)}
          </Bar>
        </BarChart>
      </ChartBox>
      <p className="text-xs text-gray-500">Solid bar = p50, light bar = p95 (client-side, ms).</p>
      <Missing models={models.filter((m) => !ok.includes(m))} what="latency" />
    </>
  )
}

function ModelScatter({ points, xLabel, yLabel, logX, xFmt }: { points: { id: string; name: string; x: number; y: number }[]; xLabel: string; yLabel: string; logX?: boolean; xFmt: (v: unknown) => string }) {
  return (
    <ChartBox>
      <ScatterChart margin={{ top: 16, right: 24, bottom: 24, left: 8 }}>
        {GRID}
        <XAxis type="number" dataKey="x" name={xLabel} scale={logX ? 'log' : 'auto'} domain={['auto', 'auto']} tick={AXIS} label={{ value: xLabel, position: 'insideBottom', offset: -12, style: AXIS }} allowDataOverflow={false} />
        <YAxis type="number" dataKey="y" name={yLabel} domain={[0, 1]} tickFormatter={(v) => `${Math.round(v * 100)}%`} tick={AXIS} />
        <Tooltip
          cursor={{ strokeDasharray: '3 3' }}
          formatter={(v, n) => (n === yLabel ? fmtPct(v) : xFmt(v))}
          labelFormatter={() => ''}
        />
        {points.map((p) => (
          <Scatter key={p.id} name={p.name} data={[p]} fill={colorFor(p.id)} isAnimationActive={false} shape="circle">
            <LabelList dataKey="name" position="top" style={{ fontSize: 11, fill: '#151515' }} />
          </Scatter>
        ))}
      </ScatterChart>
    </ChartBox>
  )
}

type Metric = 'classification' | 'extraction'
function metricOf(m: ModelReport, metric: Metric): number | null {
  return metric === 'classification' ? (m.classification?.accuracy ?? null) : (m.extraction?.exactMatch ?? null)
}

function MetricToggle({ metric, setMetric }: { metric: Metric; setMetric: (m: Metric) => void }) {
  return (
    <div className="mb-2 inline-flex overflow-hidden rounded-md border border-gray-300 text-xs">
      {(['classification', 'extraction'] as Metric[]).map((k) => (
        <button key={k} type="button" onClick={() => setMetric(k)} className={`px-2.5 py-1 ${metric === k ? 'bg-rh-dark text-white' : 'bg-white text-gray-700'}`}>
          {k === 'classification' ? 'Classification accuracy' : 'Extraction exact match'}
        </button>
      ))}
    </div>
  )
}

export function AccuracyLatencyScatter({ models }: { models: ModelReport[] }) {
  const [metric, setMetric] = useState<Metric>('classification')
  const yLabel = metric === 'classification' ? 'accuracy' : 'exact match'
  const points = models
    .map((m) => ({ id: m.modelId, name: name(m), x: m.latency?.p50, y: metricOf(m, metric) }))
    .filter((p): p is { id: string; name: string; x: number; y: number } => isNum(p.x) && p.x > 0 && isNum(p.y))
  const missing = models.filter((m) => !points.some((p) => p.id === m.modelId))
  return (
    <>
      <MetricToggle metric={metric} setMetric={setMetric} />
      {points.length ? <ModelScatter points={points} xLabel="p50 latency (ms, log)" yLabel={yLabel} logX xFmt={fmtMs} /> : <p className="text-sm text-gray-500">No model has both p50 latency and {yLabel}.</p>}
      <Missing models={missing} what={`p50 latency or ${yLabel}`} />
    </>
  )
}

export function AccuracyMemoryScatter({ models }: { models: ModelReport[] }) {
  const [metric, setMetric] = useState<Metric>('classification')
  const yLabel = metric === 'classification' ? 'accuracy' : 'exact match'
  const points = models
    .map((m) => ({ id: m.modelId, name: name(m), x: m.resources?.rssPeakMb, y: metricOf(m, metric) }))
    .filter((p): p is { id: string; name: string; x: number; y: number } => isNum(p.x) && isNum(p.y))
  const missing = models.filter((m) => !points.some((p) => p.id === m.modelId))
  return (
    <>
      <MetricToggle metric={metric} setMetric={setMetric} />
      {points.length ? <ModelScatter points={points} xLabel="peak RSS (MB)" yLabel={yLabel} xFmt={(v) => (isNum(v) ? `${num(v, 0)} MB` : 'n/a')} /> : <p className="text-sm text-gray-500">No model has both peak RSS and {yLabel}.</p>}
      <Missing models={missing} what={`peak RSS or ${yLabel}`} />
    </>
  )
}

export function ThroughputChart({ models }: { models: ModelReport[] }) {
  const withTp = models.filter((m) => (m.throughput ?? []).some((t) => isNum(t.rps)))
  const levels = Array.from(new Set(withTp.flatMap((m) => (m.throughput ?? []).map((t) => t.concurrency)))).sort((a, b) => a - b)
  const data = levels.map((c) => {
    const row: Record<string, number | null> = { concurrency: c }
    for (const m of withTp) row[m.modelId] = m.throughput?.find((t) => t.concurrency === c)?.rps ?? null
    return row
  })
  if (withTp.length === 0) return <p className="text-sm text-gray-500">No throughput measurements in this run.</p>
  return (
    <>
      <ChartBox>
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 16, left: 8 }} barGap={2}>
          {GRID}
          <XAxis dataKey="concurrency" tick={AXIS} label={{ value: 'concurrency', position: 'insideBottom', offset: -8, style: AXIS }} />
          <YAxis tick={AXIS} label={{ value: 'req/s', angle: -90, position: 'insideLeft', style: AXIS }} />
          <Tooltip formatter={(v) => (isNum(v) ? `${num(v, 2)} rps` : 'n/a')} labelFormatter={(l) => `concurrency ${l}`} />
          <Legend {...LEGEND} />
          {withTp.map((m) => (
            <Bar key={m.modelId} dataKey={m.modelId} name={name(m)} fill={colorFor(m.modelId)} radius={[4, 4, 0, 0]} isAnimationActive={false} />
          ))}
        </BarChart>
      </ChartBox>
      <Missing models={models.filter((m) => !withTp.includes(m))} what="throughput" />
    </>
  )
}

export function ReliabilityChart({ models }: { models: ModelReport[] }) {
  const avail = models.filter((m) => m.calibration?.available && (m.calibration.reliability ?? []).length > 0)
  if (avail.length === 0) return <p className="text-sm text-gray-500">No model in this run reports calibrated probabilities.</p>
  return (
    <ChartBox>
      <LineChart margin={{ top: 8, right: 16, bottom: 20, left: 8 }}>
        {GRID}
        <XAxis type="number" dataKey="x" domain={[0, 1]} tick={AXIS} tickFormatter={(v) => `${Math.round(v * 100)}%`} label={{ value: 'mean confidence', position: 'insideBottom', offset: -10, style: AXIS }} />
        <YAxis type="number" domain={[0, 1]} tick={AXIS} tickFormatter={(v) => `${Math.round(v * 100)}%`} />
        <Tooltip formatter={fmtPct} labelFormatter={(l) => `confidence ${fmtPct(l)}`} />
        <Legend {...LEGEND} />
        <ReferenceLine segment={[{ x: 0, y: 0 }, { x: 1, y: 1 }]} stroke="#8a8d90" strokeDasharray="4 4" ifOverflow="extendDomain" />
        {avail.map((m) => (
          <Line
            key={m.modelId}
            name={name(m)}
            data={(m.calibration!.reliability ?? [])
              .filter((b) => b.count > 0 && isNum(b.meanConfidence) && isNum(b.accuracy))
              .map((b) => ({ x: b.meanConfidence, y: b.accuracy, count: b.count }))}
            dataKey="y"
            stroke={colorFor(m.modelId)}
            strokeWidth={2}
            dot={{ r: 4 }}
            isAnimationActive={false}
          />
        ))}
      </LineChart>
    </ChartBox>
  )
}

export function CoverageChart({ models }: { models: ModelReport[] }) {
  const avail = models.filter((m) => m.calibration?.available && (m.calibration.coverageCurve ?? []).length > 0)
  if (avail.length === 0) return <p className="text-sm text-gray-500">No coverage curves available.</p>
  return (
    <ChartBox>
      <LineChart margin={{ top: 8, right: 16, bottom: 20, left: 8 }}>
        {GRID}
        <XAxis type="number" dataKey="x" domain={[0, 1]} tick={AXIS} tickFormatter={(v) => `${Math.round(v * 100)}%`} label={{ value: 'coverage', position: 'insideBottom', offset: -10, style: AXIS }} />
        <YAxis type="number" domain={['auto', 1]} tick={AXIS} tickFormatter={(v) => `${Math.round(v * 100)}%`} />
        <Tooltip formatter={fmtPct} labelFormatter={(l) => `coverage ${fmtPct(l)}`} />
        <Legend {...LEGEND} />
        {avail.map((m) => (
          <Line
            key={m.modelId}
            name={name(m)}
            data={(m.calibration!.coverageCurve ?? [])
              .filter((p) => isNum(p.accuracy))
              .map((p) => ({ x: p.coverage, y: p.accuracy, threshold: p.threshold }))
              .sort((a, b) => a.x - b.x)}
            dataKey="y"
            stroke={colorFor(m.modelId)}
            strokeWidth={2}
            dot={false}
            isAnimationActive={false}
          />
        ))}
      </LineChart>
    </ChartBox>
  )
}

const DASHES = ['', '6 3', '2 3', '8 3 2 3']

export function PerLengthCharts({ models }: { models: ModelReport[] }) {
  const withLen = models.filter((m) => (m.perLength ?? []).length > 0)
  if (withLen.length === 0) return <p className="text-sm text-gray-500">No long-context results in this run (include the "long" group).</p>
  const positions = Array.from(new Set(withLen.flatMap((m) => (m.perLength ?? []).map((p) => p.position ?? '')))).sort()
  const series = withLen.flatMap((m) =>
    positions
      .map((pos, i) => ({
        key: `${m.modelId}|${pos}`,
        label: positions.length > 1 && pos ? `${name(m)} · ${pos}` : name(m),
        color: colorFor(m.modelId),
        dash: DASHES[i % DASHES.length],
        points: (m.perLength ?? []).filter((p) => (p.position ?? '') === pos).sort((a, b) => a.targetTokens - b.targetTokens),
      }))
      .filter((s) => s.points.length > 0),
  )
  const chart = (field: 'exactMatch' | 'latencyP50', yLabel: string, fmt: (v: unknown) => string, pctAxis: boolean) => (
    <ChartBox height={260}>
      <LineChart margin={{ top: 8, right: 16, bottom: 20, left: 8 }}>
        {GRID}
        <XAxis type="number" dataKey="x" domain={['auto', 'auto']} tick={AXIS} label={{ value: 'target tokens', position: 'insideBottom', offset: -10, style: AXIS }} />
        <YAxis type="number" domain={pctAxis ? [0, 1] : ['auto', 'auto']} tick={AXIS} tickFormatter={pctAxis ? (v) => `${Math.round(v * 100)}%` : undefined} label={{ value: yLabel, angle: -90, position: 'insideLeft', style: AXIS }} />
        <Tooltip formatter={fmt} labelFormatter={(l) => `${l} tokens`} />
        <Legend {...LEGEND} />
        {series.map((s) => (
          <Line
            key={s.key}
            name={s.label}
            data={s.points.map((p) => ({ x: p.targetTokens, y: p[field] }))}
            dataKey="y"
            stroke={s.color}
            strokeDasharray={s.dash}
            strokeWidth={2}
            dot={{ r: 4 }}
            connectNulls={false}
            isAnimationActive={false}
          />
        ))}
      </LineChart>
    </ChartBox>
  )
  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <div>
        <div className="mb-1 text-xs font-semibold text-gray-600">Extraction exact match vs input length</div>
        {chart('exactMatch', 'exact match', fmtPct, true)}
      </div>
      <div>
        <div className="mb-1 text-xs font-semibold text-gray-600">p50 latency vs input length</div>
        {chart('latencyP50', 'ms', fmtMs, false)}
      </div>
      <p className="text-xs text-gray-500 lg:col-span-2">Gaps = not measured / unsupported at that length (counts in the table below).</p>
    </div>
  )
}

const FAILURES: { key: 'abstained' | 'unsupported' | 'malformed' | 'timeout' | 'infraError'; label: string; color: string }[] = [
  { key: 'abstained', label: 'Abstained', color: OUTCOME_COLORS.ABSTAINED },
  { key: 'unsupported', label: 'Unsupported', color: OUTCOME_COLORS.UNSUPPORTED },
  { key: 'malformed', label: 'Malformed', color: OUTCOME_COLORS.MALFORMED },
  { key: 'timeout', label: 'Timeout', color: OUTCOME_COLORS.TIMEOUT },
  { key: 'infraError', label: 'Infra error', color: OUTCOME_COLORS.INFRA_ERROR },
]

export function FailureChart({ models }: { models: ModelReport[] }) {
  const ok = models.filter((m) => m.counts)
  if (ok.length === 0) return <p className="text-sm text-gray-500">No outcome counts reported.</p>
  const data = ok.map((m) => ({ name: name(m), ...m.counts! }))
  return (
    <ChartBox height={Math.max(160, ok.length * 44 + 60)}>
      <BarChart data={data} layout="vertical" margin={{ top: 8, right: 16, bottom: 8, left: 8 }}>
        {GRID}
        <XAxis type="number" tick={AXIS} allowDecimals={false} />
        <YAxis type="category" dataKey="name" tick={AXIS} width={120} />
        <Tooltip />
        <Legend {...LEGEND} />
        {FAILURES.map((f) => (
          <Bar key={f.key} dataKey={f.key} name={f.label} stackId="f" fill={f.color} stroke="#fff" strokeWidth={1} isAnimationActive={false} barSize={22} />
        ))}
      </BarChart>
    </ChartBox>
  )
}
