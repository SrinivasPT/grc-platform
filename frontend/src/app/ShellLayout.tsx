import { Outlet } from 'react-router-dom'

export function ShellLayout() {
  return (
    <div className="flex h-screen bg-background">
      <aside className="w-64 border-r bg-card">
        <div className="p-4 font-semibold text-lg">GRC Platform</div>
        <nav className="p-2">
          {/* Navigation populated in Phase 4 */}
          <p className="text-sm text-muted-foreground px-2">Modules loading in Phase 4</p>
        </nav>
      </aside>
      <main className="flex-1 overflow-auto">
        <Outlet />
      </main>
    </div>
  )
}
