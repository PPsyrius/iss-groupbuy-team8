import { createRootRouteWithContext, Link, Outlet } from '@tanstack/react-router'
import { TanStackDevtools } from '@tanstack/react-devtools'
import { ReactQueryDevtoolsPanel } from '@tanstack/react-query-devtools'
import { TanStackRouterDevtoolsPanel } from '@tanstack/react-router-devtools'
import type { RouterContext } from '@/router'

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootLayout,
  notFoundComponent: () => (
    <main className="p-8">
      <h1 className="text-xl font-semibold">Page not found</h1>
      <Link to="/" className="underline">Back to listings</Link>
    </main>
  ),
})

function RootLayout() {
  return (
    <>
      <Outlet />
      <TanStackDevtools
        plugins={[
          { name: 'TanStack Query', render: <ReactQueryDevtoolsPanel /> },
          { name: 'TanStack Router', render: <TanStackRouterDevtoolsPanel /> },
        ]}
      />
    </>
  )
}
