import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { App } from './App'
import { ApolloProvider } from '@apollo/client'
import { ApolloClient, InMemoryCache } from '@apollo/client'
import { AuthProvider } from '../lib/authContext'

const client = new ApolloClient({ cache: new InMemoryCache(), uri: '/graphql' })

function renderApp() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <ApolloProvider client={client}>
          <App />
        </ApolloProvider>
      </AuthProvider>
    </MemoryRouter>
  )
}

describe('App', () => {
  it('renders the shell layout', () => {
    renderApp()
    expect(screen.getByText('GRC Platform')).toBeInTheDocument()
  })

  it('shows placeholder text for Phase 4 modules', () => {
    renderApp()
    expect(screen.getByText(/Phase 4/i)).toBeInTheDocument()
  })
})
