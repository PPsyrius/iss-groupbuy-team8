import { createRootRouteWithContext, Link, Outlet } from "@tanstack/react-router";

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
  notFoundComponent: () => (
    <main className="p-8">
      <h1 className="text-xl font-semibold">Page not found</h1>
      <Link to="/" className="underline">
        Back to listings
      </Link>
    </main>
  ),
});

function RootLayout() {
  return (
    <>
      <Header />
      <Outlet />
      <Devtools />
    </>
  );
}
