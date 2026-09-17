import { TanStackDevtools } from "@tanstack/react-devtools";
import { ReactQueryDevtoolsPanel } from "@tanstack/react-query-devtools";
import { createRootRouteWithContext, Link, Outlet } from "@tanstack/react-router";
import { TanStackRouterDevtoolsPanel } from "@tanstack/react-router-devtools";

import { Header } from "@/components/Header";
import type { RouterContext } from "@/router";

export const Route = createRootRouteWithContext<RouterContext>()({
  component: RootLayout,
  notFoundComponent: () => (
    <main className="p-8">
      <h1 className="text-xl font-semibold">Page not found</h1>
      <Link to="/" className="underline">
        Back to listings
      </Link>
    </main>
  ),
});

const devtoolsPlugins = [
  { name: "TanStack Query", render: <ReactQueryDevtoolsPanel /> },
  { name: "TanStack Router", render: <TanStackRouterDevtoolsPanel /> },
];

function RootLayout() {
  return (
    <>
      <Header />
      <Outlet />
      <TanStackDevtools plugins={devtoolsPlugins} />
    </>
  );
}
