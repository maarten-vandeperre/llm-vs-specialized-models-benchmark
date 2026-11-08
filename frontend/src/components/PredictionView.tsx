import { Bar, BarChart, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { Extraction, LabelPrediction, Prediction, Timing } from '../api'
import { display, isNum, ms, nanosToMs, num, pct } from '../format'
import { TableWrap, Val } from './ui'

function Distribution({ dist, chosen }: { dist: Record<string, number>; chosen: string | null }) {
  const data = Object.entries(dist)
    .map(([label, p]) => ({ label, p }))
    .sort((a, b) => b.p - a.p)
  if (data.length === 0) return <Val v={null} />
  return (
    <div style={{ width: 220, height: Math.max(40, data.length * 18 + 8) }}>
      <ResponsiveContainer>
        <BarChart data={data} layout="vertical" margin={{ top: 2, right: 8, bottom: 2, left: 0 }}>
          <XAxis type="number" domain={[0, 1]} hide />
          <YAxis type="category" dataKey="label" width={90} tick={{ fontSize: 10 }} interval={0} />
          <Tooltip formatter={(v) => pct(Number(v))} />
          <Bar dataKey="p" isAnimationActive={false} barSize={10}>
            {data.map((d) => (
              <Cell key={d.label} fill={d.label === chosen ? '#ee0000' : '#8a8d90'} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

export function LabelsTable({ labels }: { labels: Record<string, LabelPrediction> | null | undefined }) {
  if (!labels || Object.keys(labels).length === 0) return <p className="text-sm text-gray-500">No labels returned.</p>
  return (
    <TableWrap>
      <thead>
        <tr>
          <th>Field</th>
          <th>Label</th>
          <th>Probability</th>
          <th>Provider confidence</th>
          <th>Distribution</th>
        </tr>
      </thead>
      <tbody>
        {Object.entries(labels).map(([field, l]) => (
          <tr key={field}>
            <td className="font-medium">{field}</td>
            <td><Val v={l.label} /></td>
            <td><Val v={l.probability} reason="This model does not report a calibrated probability">{pct(l.probability)}</Val></td>
            <td>{isNum(l.providerConfidence) ? num(l.providerConfidence, 3) : <span className="text-gray-400">—</span>}</td>
            <td>{l.distribution ? <Distribution dist={l.distribution} chosen={l.label} /> : <span className="text-gray-400">—</span>}</td>
          </tr>
        ))}
      </tbody>
    </TableWrap>
  )
}

export function ExtractionView({ extraction }: { extraction: Extraction | null | undefined }) {
  if (!extraction) return <p className="text-sm text-gray-500">No extraction returned.</p>
  const s = extraction.orderIdSpan
  return (
    <div className="space-y-3 text-sm">
      <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1">
        <dt className="text-gray-600">Order ID</dt>
        <dd className="font-mono"><Val v={extraction.orderId} /></dd>
        <dt className="text-gray-600">Order ID span</dt>
        <dd className="font-mono text-xs">
          {s ? (
            <>
              [{s.start}, {s.end}){s.text != null && <> “{s.text}”</>}
              {isNum(s.confidence) && <> · conf {num(s.confidence, 3)}</>}
            </>
          ) : (
            <Val v={null} reason="No span offsets reported" />
          )}
        </dd>
        <dt className="text-gray-600">Total</dt>
        <dd className="font-mono"><Val v={extraction.total} /></dd>
        <dt className="text-gray-600">Currency</dt>
        <dd className="font-mono"><Val v={extraction.currency} /></dd>
      </dl>
      {extraction.items && extraction.items.length > 0 ? (
        <TableWrap>
          <thead>
            <tr>
              <th>Product</th>
              <th>Quantity</th>
              <th>Span</th>
            </tr>
          </thead>
          <tbody>
            {extraction.items.map((it, i) => (
              <tr key={i}>
                <td>{it.product}</td>
                <td><Val v={it.quantity} /></td>
                <td className="font-mono text-xs">{it.productSpan ? `[${it.productSpan.start}, ${it.productSpan.end})` : <Val v={null} />}</td>
              </tr>
            ))}
          </tbody>
        </TableWrap>
      ) : (
        <p className="text-gray-500">No items extracted.</p>
      )}
    </div>
  )
}

export function DecisionView({ prediction }: { prediction: Prediction | null | undefined }) {
  if (!prediction) return null
  if (!prediction.decision && (!prediction.route || prediction.route.length === 0)) return <p className="text-sm text-gray-500">No decision or route returned.</p>
  return (
    <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-sm">
      <dt className="text-gray-600">Decision</dt>
      <dd className="font-mono"><Val v={prediction.decision} /></dd>
      <dt className="text-gray-600">Route</dt>
      <dd className="flex flex-wrap items-center gap-1">
        {prediction.route && prediction.route.length > 0 ? (
          prediction.route.map((r, i) => (
            <span key={i} className="flex items-center gap-1">
              {i > 0 && <span className="text-gray-400">→</span>}
              <span className="rounded bg-gray-100 px-1.5 py-0.5 font-mono text-xs">{r}</span>
            </span>
          ))
        ) : (
          <Val v={null} />
        )}
      </dd>
    </dl>
  )
}

export function TimingView({ timing }: { timing: Timing | null | undefined }) {
  if (!timing) return <p className="text-sm text-gray-500">No timing returned.</p>
  const rows: [string, string | null][] = [
    ['Client', isNum(timing.clientNanos) ? ms(nanosToMs(timing.clientNanos), 2) : null],
    ['Server', isNum(timing.serverNanos) ? ms(nanosToMs(timing.serverNanos), 2) : null],
    ['Load', isNum(timing.loadNanos) ? ms(nanosToMs(timing.loadNanos), 2) : null],
    ['Generation', isNum(timing.generationNanos) ? ms(nanosToMs(timing.generationNanos), 2) : null],
    ['Input tokens', isNum(timing.inputTokens) ? String(timing.inputTokens) : null],
    ['Output tokens', isNum(timing.outputTokens) ? String(timing.outputTokens) : null],
  ]
  const stages = timing.stageNanos ? Object.entries(timing.stageNanos) : []
  return (
    <div className="space-y-2 text-sm">
      <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1">
        {rows.map(([k, v]) => (
          <div key={k} className="contents">
            <dt className="text-gray-600">{k}</dt>
            <dd className="font-mono tabular-nums"><Val v={v} /></dd>
          </div>
        ))}
      </dl>
      {stages.length > 0 && (
        <div>
          <div className="mb-1 text-xs font-semibold text-gray-600 uppercase">Stages</div>
          <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1">
            {stages.map(([k, v]) => (
              <div key={k} className="contents">
                <dt className="text-gray-600">{k}</dt>
                <dd className="font-mono tabular-nums">{ms(nanosToMs(v), 2)}</dd>
              </div>
            ))}
          </dl>
        </div>
      )}
    </div>
  )
}

export function displayGoldItems(items: { product: string; quantity: number | null }[] | null | undefined): string {
  if (!items || items.length === 0) return display(null)
  return items.map((i) => `${i.quantity ?? '?'}× ${i.product}`).join(', ')
}
