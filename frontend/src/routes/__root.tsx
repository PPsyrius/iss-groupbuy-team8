import { createRootRouteWithContext, Outlet, useMatches } from "@tanstack/react-router";

import { NotFoundCard, RouteErrorCard } from "@/components/ErrorCard";
import { Header } from "@/components/Header";
import { Devtools } from "@/dev/devtools";
import { type SessionState, sessionFromQuery } from "@/features/session/session";
import type { RouterContext } from "@/router";

async function sessionOrAnonymous(
  queryClient: RouterContext["queryClient"],
): Promise<SessionState> {
  try {
    return await sessionFromQuery(queryClient);
  } catch {
    return { status: "anonymous" };
  }
}

export const Route = createRootRouteWithContext<RouterContext>()({
  // Resolves the session once per navigation from the `me` query (cached for a minute), so every
  // child `beforeLoad` guard reads `context.session` instead of racing the header's query. A 401
  // is anonymous by contract; any other failure (backend down, proxy 502 in `vite preview`) is
  // treated as anonymous too, because browsing must never depend on `me` answering. Guarded
  // routes then send the visitor to /login, which is the honest outcome of an unconfirmed
  // session; the callback route keeps the strict version (plan §12 note 46).
  beforeLoad: async ({ context }) => ({ session: await sessionOrAnonymous(context.queryClient) }),
  component: RootLayout,
  // §4.5 terminal screens (#157): the shared card for 404 and for any loader or render error
  // in a route without its own `errorComponent` (router.tsx sets the same default).
  notFoundComponent: NotFoundCard,
  errorComponent: RouteErrorCard,
});

function RootLayout() {
  // The admin console draws its own dark rail instead (staticData.shell, admin plan §12 note 1).
  const adminShell = useMatches({ select: (ms) => ms.some((m) => m.staticData.shell === "admin") });
  return (
    <>
      {adminShell ? null : <Header />}
      <Outlet />
      <Devtools />
    </>
  );
}
