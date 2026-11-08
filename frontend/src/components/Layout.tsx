import { Suspense, useEffect, useState } from 'react'
import { NavLink, Outlet } from 'react-router'
import { api, ApiError } from '../api'
import { registerModels } from '../colors'

const NAV = [
  { to: '/', label: 'Playground', end: true },
  { to: '/runner', label: 'Runner', end: false },
  { to: '/results', label: 'Results', end: false },
  { to: '/inspect', label: 'Inspect', end: false },
]

/** Polls the backend so a visible banner appears when it is unreachable. */
function useBackendStatus(): { down: string | null; ready: boolean } {
  const [down, setDown] = useState<string | null>(null)
  const [ready, setReady] = useState(false)
  useEffect(() => {
    let alive = true
    const check = () =>
      api
        .models()
        .then((ms) => {
          if (!alive) return
          registerModels(ms.map((m) => m.id))
          setDown(null)
        })
        .catch((e: unknown) => {
          if (!alive) return
          if (e instanceof ApiError && (e.status === 0 || e.status >= 500)) setDown(e.message)
          else setDown(null)
        })
        .finally(() => alive && setReady(true))
    check()
    const t = setInterval(check, 15000)
    return () => {
      alive = false
      clearInterval(t)
    }
  }, [])
  return { down, ready }
}

export default function Layout() {
  // Wait for the first model list so colors are assigned in backend order on every page.
  const { down, ready } = useBackendStatus()
  return (
    <div className="flex min-h-screen flex-col">
      <header className="bg-rh-dark text-white">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
          <div className="flex items-center gap-2">
            <span className="inline-block h-5 w-1.5 rounded-sm bg-rh-red" />
            <div className="leading-tight">
              <div className="text-sm font-semibold">Beyond the LLM</div>
              <div className="text-[11px] text-gray-400">Benchmark platform · decision, extraction &amp; LLM models</div>
            </div>
          </div>
          <nav className="-mb-3 flex gap-1 overflow-x-auto">
            {NAV.map((n) => (
              <NavLink
                key={n.to}
                to={n.to}
                end={n.end}
                className={({ isActive }) =>
                  `whitespace-nowrap border-b-2 px-3 pt-1 pb-2.5 text-sm ${isActive ? 'border-rh-red font-semibold text-white' : 'border-transparent text-gray-300 hover:text-white'}`
                }
              >
                {n.label}
              </NavLink>
            ))}
          </nav>
        </div>
      </header>
      {down && (
        <div role="alert" className="bg-red-700 px-4 py-2 text-center text-sm text-white">
          Backend unavailable — {down}
        </div>
      )}
      <main className="mx-auto w-full max-w-7xl flex-1 px-4 py-5">
        {ready ? <Suspense fallback={<div className="py-4 text-sm text-gray-500">Loading…</div>}><Outlet /></Suspense> : <div className="py-4 text-sm text-gray-500">Connecting to backend…</div>}
      </main>
      <footer className="border-t border-gray-200 bg-white px-4 py-3 text-center text-xs text-gray-500">
        Synthetic coffee-shop dataset — results do not generalize to production customers.
      </footer>
    </div>
  )
}
