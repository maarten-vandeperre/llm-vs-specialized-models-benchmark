import { lazy } from 'react'
import { Route, Routes } from 'react-router'
import Layout from './components/Layout'

const Playground = lazy(() => import('./pages/Playground'))
const Runner = lazy(() => import('./pages/Runner'))
const Results = lazy(() => import('./pages/Results'))
const Inspect = lazy(() => import('./pages/Inspect'))

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Playground />} />
        <Route path="runner" element={<Runner />} />
        <Route path="results/:runId?" element={<Results />} />
        <Route path="inspect/:runId?" element={<Inspect />} />
        <Route path="*" element={<div className="text-sm text-gray-600">Page not found.</div>} />
      </Route>
    </Routes>
  )
}
