import { QueryClientProvider } from "@tanstack/react-query";
import { createMemoryHistory, createRouter, RouterProvider } from "@tanstack/react-router";
import { render } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { vi } from "vitest";

import { OK_CODE } from "@/api/envelope";
import { queryClient } from "@/api/query-client";
import { routeTree } from "@/routeTree.gen";
import { adminHandlers, createAdminStore } from "@/test/admin-msw";
import { server } from "@/test/msw";

// The root layout mounts the devtools; they have no place in a happy-dom route test.
vi.mock("@tanstack/react-devtools", () => ({ TanStackDevtools: () => null }));
vi.mock("@tanstack/react-query-devtools", () => ({ ReactQueryDevtoolsPanel: () => null }));
vi.mock("@tanstack/react-router-devtools", () => ({ TanStackRouterDevtoolsPanel: () => null }));

// Mounts the real route tree at `path` on a memory history over the app's own QueryClient
// (cleared first): importing it installs the 401 interceptor on the generated client, exactly as
// main.tsx does, so `/auth/me` answering 401 is the normal anonymous path and not a raw error.
// The root route resolves the session on every navigation, so a test always installs one of the
// two `me` handlers below (or its own) before rendering.
export function renderRoute(path: string) {
  const qc = queryClient;
  qc.clear();
  const router = createRouter({
    routeTree,
    context: { queryClient: qc, session: { status: "anonymous" } },
    history: createMemoryHistory({ initialEntries: [path] }),
  });
  const result = render(
    <QueryClientProvider client={qc}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return { ...result, router, queryClient: qc };
}

const ada = { userId: "u1", nickname: "Ada", avatarUrl: null };

export function serveAnonymous() {
  server.use(
    http.get("*/api/v1/auth/me", () =>
      HttpResponse.json({ code: "AUTH_401", message: "unauthenticated" }, { status: 401 }),
    ),
  );
}

export function serveSignedIn(
  user: { userId: string; nickname: string | null; avatarUrl: string | null } = ada,
) {
  server.use(
    http.get("*/api/v1/auth/me", () =>
      HttpResponse.json({ code: OK_CODE, message: "ok", data: user }),
    ),
  );
}

// Signed in as an admin over a fresh stateful admin store (admin plan A1-02); the store is
// returned so a test can inspect it or arm a conflict.
export function serveAdmin(store = createAdminStore()) {
  serveSignedIn();
  server.use(...adminHandlers(store, { role: "admin" }));
  return store;
}

// Signed in as a buyer: every admin path answers 403, as Sa-Token's role check does.
export function serveNonAdmin() {
  serveSignedIn();
  server.use(...adminHandlers(createAdminStore(), { role: "buyer" }));
}
