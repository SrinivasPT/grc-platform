import { Routes, Route } from 'react-router-dom'
import { ShellLayout } from './ShellLayout'

export function App() {
  return (
    <Routes>
      <Route path="/" element={<ShellLayout />}>
        <Route index element={<div>GRC Platform — Dashboard (Phase 4)</div>} />
        <Route path="*" element={<div>404 — Page not found</div>} />
      </Route>
    </Routes>
  )
}
