import type { QueryClient } from "@tanstack/react-query";
import { createRouter } from "@tanstack/react-router";

import { RouteErrorCard } from "./components/ErrorCard";
import type { SessionState } from "./features/session/session";
import { routeTree } from "./routeTree.gen";

export interface RouterContext {
  queryClient: QueryClient;
  // Seeded anonymous in main.tsx; the root route's `beforeLoad` resolves the real value from the
  // `me` query before any child route runs its guard.
  session: SessionState;
}

export function createAppRouter(context: RouterContext) {
  return createRouter({
    routeTree,
    context,
    defaultPreload: "intent",
    scrollRestoration: true,
    // §4.5 "500" card for routes without their own `errorComponent` (#157).
    defaultErrorComponent: RouteErrorCard,
  });
}

declare module "@tanstack/react-router" {
  interface Register {
    router: ReturnType<typeof createAppRouter>;
  }
  // `shell: "admin"` marks the console subtree, whose own layout replaces the storefront header
  // (admin plan §12 note 1).
  interface StaticDataRouteOption {
    shell?: "admin";
  }
}
