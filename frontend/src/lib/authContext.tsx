import React, { createContext, useContext } from 'react'

interface AuthUser {
  id: string
  orgId: string
  username: string
  roles: string[]
  roleVersion: number
}

interface AuthContextValue {
  user: AuthUser | null
  isAuthenticated: boolean
  login: () => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: React.ReactNode }) {
  // TODO (Phase 2): implement Keycloak OIDC integration
  // Placeholder: unauthenticated state for skeleton
  const value: AuthContextValue = {
    user: null,
    isAuthenticated: false,
    login: () => { /* Keycloak redirect */ },
    logout: () => { /* Keycloak logout */ },
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return ctx
}
