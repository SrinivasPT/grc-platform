---
applyTo: 'frontend/**'
description: 'Frontend React/TypeScript rules for GRC platform. Use when writing or reviewing any React, TypeScript, Apollo, or Vite code.'
---

# Frontend — React / TypeScript Rules

## Tech Stack

React 18 · TypeScript 5 (strict) · Apollo Client 3.x · Zustand · shadcn/ui · Vite 5 · GraphQL Codegen

---

## TypeScript Rules

- `strict: true` in `tsconfig.json` — no implicit `any`, no `@ts-ignore`.
- Use `unknown` instead of `any` when type is genuinely unknown; narrow it explicitly.
- Use discriminated unions with `type` literals — not class hierarchies.
- Prefer `readonly` arrays and records for data from Apollo.

---

## React Patterns

- All data fetching via Apollo Client generated hooks — never `fetch` directly in components.
- Local UI state in Zustand stores; server state in Apollo cache — never overlap the two.
- Use React 18 Suspense for async data boundaries — no manual loading state booleans.
- Co-locate component, styles, and tests in the same folder: `components/MyComp/index.tsx`, `MyComp.test.tsx`.
- No prop drilling more than 2 levels — extract to Zustand or React context.

---

## GraphQL Conventions

- All GraphQL operations in `.graphql` files under `src/graphql/`.
- Run `pnpm codegen` to regenerate typed hooks after changing `.graphql` files.
- Never write raw query strings in component files — always use generated hooks.
- Apollo cache optimistic updates required for all mutation UIs.
- Use `@connection` directive for paginated lists.

---

## Form Engine

- All form layouts defined in JSON config (`FormLayout` type) — never hardcoded JSX field lists.
- `FieldRenderer` handles field type dispatch; `LayoutRenderer` handles section/group structure.
- Validation rules come from the backend `RuleDslParser` result — never duplicated in frontend code.
- File upload fields use the REST endpoint (`POST /api/files`) — not GraphQL mutations.

---

## Accessibility & UX

- All interactive elements must have keyboard navigation and ARIA roles.
- Use shadcn/ui primitives — do not reimplement buttons, modals, or form inputs.
- Table components use the shared `<Table>` from `src/components/shared/` — never raw `<table>`.

---

## Testing

- Write tests first (TDD). Use Vitest + React Testing Library.
- Test component behavior, not implementation. Query by role/label — never by class or data-testid unless no semantic alternative.
- MSW (Mock Service Worker) for mocking Apollo/GraphQL in tests — never mock Apollo Client directly.
- Coverage target: ≥ 80% branch coverage on form engine and shared components.

---

## Agent Checklist (Frontend)

1. Check `src/graphql/` for existing query/mutation before creating a new one.
2. Write the failing Vitest test first.
3. Use generated GraphQL hooks — run codegen first.
4. Verify shadcn/ui has a matching primitive before building a new component.
