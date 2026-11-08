// Typed client for the benchmark backend. All URLs are relative (/api) so the
// app works behind the Vite dev proxy and behind the nginx proxy in production.

export type Task = 'CLASSIFICATION' | 'EXTRACTION' | 'COMBINED'
export const TASKS: Task[] = ['CLASSIFICATION', 'EXTRACTION', 'COMBINED']

export type Outcome = 'SUCCESS' | 'ABSTAINED' | 'UNSUPPORTED' | 'MALFORMED' | 'TIMEOUT' | 'INFRA_ERROR'
export const OUTCOMES: Outcome[] = ['SUCCESS', 'ABSTAINED', 'UNSUPPORTED', 'MALFORMED', 'TIMEOUT', 'INFRA_ERROR']

export type HealthState = 'UP' | 'MODEL_MISSING' | 'RUNTIME_UNSUPPORTED' | 'DOWN' | 'DISABLED'
export type RunState = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export type Phase = 'cold' | 'warmup' | 'quality' | 'latency' | 'throughput'

export const GROUPS = ['classification', 'extraction', 'combined', 'ambiguous', 'multilingual', 'long']
export const LANGUAGES = ['en', 'nl', 'fr']

export interface ModelInfo {
  id: string
  displayName: string
  kind: 'model' | 'pipeline'
  runtime: string | null
  modelRef: string | null
  family: string | null
  quantization: string | null
  notes: string | null
  capabilities: { features: string[]; tasks: Task[]; maxInputTokens: number | null }
}

export interface ModelHealth {
  modelId: string
  state: HealthState
  detail: string | null
  info: Record<string, unknown> | null
}

export interface DatasetInfo {
  name: string
  description: string | null
  version: string | null
  records: number
  seed: number | null
  sha256: string | null
  groups: Record<string, number>
  languages: Record<string, number>
  splits: Record<string, number>
}

export interface Span {
  start: number
  end: number
}

export interface Gold {
  intent: string | null
  sentiment: string | null
  category: string | null
  complaint: string | boolean | null
  orderId: string | null
  orderIdSpan: Span | null
  items: { product: string; quantity: number | null }[] | null
  total: number | null
  currency: string | null
  totalSpan: Span | null
  decision: string | null
}

export interface DatasetRecord {
  id: string
  group: string
  split: string
  language: string
  task: Task
  text: string
  approxTokens: number | null
  tags: string[]
  meta: { targetTokens?: number; position?: string } & Record<string, unknown>
  gold: Gold
}

export interface LabelPrediction {
  label: string | null
  probability: number | null
  distribution: Record<string, number> | null
  providerConfidence: number | null
}

export interface ExtractedSpan {
  start: number
  end: number
  text?: string | null
  confidence?: number | null
}

export interface Extraction {
  orderId: string | null
  orderIdSpan: ExtractedSpan | null
  items: { product: string; quantity: number | null; productSpan: ExtractedSpan | null }[] | null
  total: number | null
  totalSpan: ExtractedSpan | null
  currency: string | null
}

export interface Prediction {
  labels: Record<string, LabelPrediction> | null
  extraction: Extraction | null
  decision: string | null
  route: string[] | null
}

export interface Timing {
  clientNanos: number | null
  serverNanos: number | null
  loadNanos: number | null
  inputTokens: number | null
  outputTokens: number | null
  generationNanos: number | null
  stageNanos: Record<string, number> | null
}

export interface BenchmarkResponse {
  modelId: string
  requestId: string
  task: Task
  outcome: Outcome
  prediction: Prediction | null
  error: string | null
  timing: Timing | null
  rawResponse: string | null
  metadata: Record<string, unknown> | null
}

export interface InferenceRequest {
  modelId: string
  task: Task
  text: string
  language: string | null
}

export interface BenchmarkConfig {
  models: string[]
  dataset: string
  groups: string[] | null
  split: string
  languages: string[] | null
  limit: number | null
  warmup: number
  repetitions: number
  latencySampleSize: number
  concurrency: number[]
  throughputRequests: number
  timeoutSeconds: number
  coldStart: boolean
  seed: number
  label: string | null
}

export interface RunStatus {
  runId: string
  label: string | null
  status: RunState
  progress: { completed: number; total: number; phase: string | null; currentModel: string | null } | null
  errors: string[] | null
  config: Partial<BenchmarkConfig> | null
  createdAt: string | null
  startedAt: string | null
  finishedAt: string | null
}

export interface Evaluation {
  classification: Record<string, { gold: unknown; predicted: unknown; correct: boolean; probability: number | null }> | null
  extraction: {
    exactMatch: boolean
    tp: number
    fp: number
    fn: number
    hallucinated: string[]
    missing: string[]
    fieldCorrect: { orderId: boolean | null; items: boolean | null; total: boolean | null; currency: boolean | null }
    offsetCorrect: boolean | null
  } | null
  decision: { gold: string | null; predicted: string | null; correct: boolean } | null
  correct: boolean
}

export interface PredictionRecord {
  runId: string
  modelId: string
  recordId: string
  group: string
  language: string
  task: Task
  phase: Phase
  repetition: number | null
  concurrency: number | null
  outcome: Outcome
  error: string | null
  text: string
  gold: Gold | null
  prediction: Prediction | null
  evaluation: Evaluation | null
  timing: Timing | null
  rawResponse: string | null
  timestamp: string | null
}

export interface Page<T> {
  total: number
  items: T[]
}

export type Ci = [number, number] | null

export interface LatencyStats {
  n: number
  p50: number | null
  p90: number | null
  p95: number | null
  p99: number | null
  mean: number | null
  min: number | null
  max: number | null
  p50Ci: Ci
}

export interface ReliabilityBin {
  binLower: number
  binUpper: number
  count: number
  meanConfidence: number | null
  accuracy: number | null
}

export interface ModelReport {
  modelId: string
  displayName: string | null
  kind: string | null
  runtime: string | null
  modelRef: string | null
  quantization: string | null
  health: { state: HealthState | string; detail: string | null } | null
  counts: { total: number; success: number; abstained: number; unsupported: number; malformed: number; timeout: number; infraError: number } | null
  classification: {
    n: number
    accuracy: number | null
    accuracyCi: Ci
    macroF1: number | null
    macroF1Ci: Ci
    fields: Record<string, {
      n: number
      accuracy: number | null
      accuracyCi: Ci
      macroF1: number | null
      macroPrecision: number | null
      macroRecall: number | null
      perLabel: Record<string, { precision: number | null; recall: number | null; f1: number | null; support: number }>
    }> | null
  } | null
  extraction: {
    n: number
    exactMatch: number | null
    exactMatchCi: Ci
    entityPrecision: number | null
    entityRecall: number | null
    entityF1: number | null
    hallucinationRate: number | null
    missingRate: number | null
    fieldAccuracy: { orderId: number | null; items: number | null; total: number | null; currency: number | null } | null
    offsetAccuracy: number | null
  } | null
  decision: { n: number; accuracy: number | null; accuracyCi: Ci } | null
  abstention: { ambiguousN: number; abstentionRate: number | null; falsePositiveRate: number | null; coverage: number | null } | null
  calibration: {
    available: boolean
    reason: string | null
    n: number
    brier: number | null
    ece: number | null
    reliability: ReliabilityBin[] | null
    coverageCurve: { coverage: number; accuracy: number | null; threshold: number }[] | null
    riskAtThreshold: { threshold: number; coverage: number; risk: number } | null
  } | null
  latency: LatencyStats | null
  serverLatency: LatencyStats | null
  coldStart: { clientMs: number | null; loadMs: number | null } | null
  throughput: { concurrency: number; requests: number; wallSeconds: number | null; rps: number | null; p50: number | null; p95: number | null; errors: number }[] | null
  tokens: { meanInput: number | null; meanOutput: number | null; outputTokensPerSecond: number | null } | null
  resources: {
    processPattern: string | null
    samples: number
    rssMeanMb: number | null
    rssPeakMb: number | null
    cpuMeanPct: number | null
    cpuPeakPct: number | null
    modelStorageBytes: number | null
    gpu: string | null
    energy: string | null
  } | null
  perLanguage: Record<string, { n: number; classificationAccuracy: number | null; extractionExactMatch: number | null }> | null
  perGroup: Record<string, { n: number; classificationAccuracy: number | null; extractionExactMatch: number | null }> | null
  perLength: { targetTokens: number; position: string | null; n: number; exactMatch: number | null; unsupported: number; latencyP50: number | null }[] | null
}

export interface Report {
  runId: string
  label: string | null
  status: RunState
  createdAt: string | null
  finishedAt: string | null
  synthetic: boolean
  datasetVersion: string | null
  environment: Record<string, unknown> | null
  config: Record<string, unknown> | null
  models: ModelReport[]
}

export class ApiError extends Error {
  status: number
  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res: Response
  try {
    res = await fetch(path, {
      ...init,
      headers: { Accept: 'application/json', ...(init?.body ? { 'Content-Type': 'application/json' } : {}), ...init?.headers },
    })
  } catch (e) {
    throw new ApiError(0, `Backend unreachable (${e instanceof Error ? e.message : String(e)})`)
  }
  if (!res.ok) {
    let msg = `${res.status} ${res.statusText}`
    try {
      const body = await res.text()
      if (body) {
        try {
          const j = JSON.parse(body) as Record<string, unknown>
          msg += `: ${String(j.message ?? j.error ?? j.title ?? body)}`
        } catch {
          msg += `: ${body.slice(0, 300)}`
        }
      }
    } catch {
      /* ignore */
    }
    throw new ApiError(res.status, msg)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}

function qs(params: Record<string, string | number | boolean | null | undefined>): string {
  const p = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== null && v !== undefined && v !== '') p.set(k, String(v))
  }
  const s = p.toString()
  return s ? `?${s}` : ''
}

const enc = encodeURIComponent

export const api = {
  models: () => request<ModelInfo[]>('/api/models'),
  health: (id: string) => request<ModelHealth>(`/api/models/${enc(id)}/health`),
  datasets: () => request<DatasetInfo[]>('/api/datasets'),
  records: (name: string, params: { group?: string; split?: string; limit?: number }) =>
    request<DatasetRecord[]>(`/api/datasets/${enc(name)}/records${qs(params)}`),
  infer: (body: InferenceRequest) =>
    request<BenchmarkResponse>('/api/inference', { method: 'POST', body: JSON.stringify(body) }),
  startRun: (body: BenchmarkConfig) =>
    request<RunStatus>('/api/benchmarks', { method: 'POST', body: JSON.stringify(body) }),
  runs: () => request<RunStatus[]>('/api/benchmarks'),
  run: (runId: string) => request<RunStatus>(`/api/benchmarks/${enc(runId)}`),
  report: (runId: string) => request<Report>(`/api/benchmarks/${enc(runId)}/report`),
  results: (
    runId: string,
    params: { modelId?: string; group?: string; outcome?: string; incorrect?: boolean; phase?: string; offset?: number; limit?: number },
  ) => request<Page<PredictionRecord>>(`/api/benchmarks/${enc(runId)}/results${qs(params)}`),
  reportMarkdownUrl: (runId: string) => `/api/benchmarks/${enc(runId)}/report?format=md`,
  resultsCsvUrl: (runId: string) => `/api/benchmarks/${enc(runId)}/results.csv`,
}
