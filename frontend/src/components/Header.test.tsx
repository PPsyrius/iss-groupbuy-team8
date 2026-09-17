import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import {
  createMemoryHistory,
  createRootRoute,
  createRouter,
  RouterProvider,
} from "@tanstack/react-router";
import { render, screen } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { server } from "@/test/msw";

import { Header } from "./Header";

// Header renders a TanStack Router `Link`, which needs a router in context. Build a
// minimal memory-history router with a single root route rendering the UI under test.
async function renderWithQuery(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const rootRoute = createRootRoute({ component: () => ui });
  const router = createRouter({
    routeTree: rootRoute,
    history: createMemoryHistory({ initialEntries: ["/"] }),
  });
  const result = render(
    <QueryClientProvider client={qc}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  await router.load();
  return result;
}

describe("Header", () => {
  it("shows the login link when /auth/me answers 401", async () => {
    server.use(
      http.get("*/api/v1/auth/me", () =>
        HttpResponse.json({ code: "AUTH_401", message: "unauthenticated" }, { status: 401 }),
      ),
    );
    await renderWithQuery(<Header />);
    expect(await screen.findByRole("link", { name: /log in/i })).toHaveAttribute(
      "href",
      "/api/v1/auth/oauth2/google",
    );
  });

  it("shows the nickname when /auth/me answers 200", async () => {
    server.use(
      http.get("*/api/v1/auth/me", () =>
        HttpResponse.json({
          code: "COMMON_200_000",
          data: { userId: "u1", nickname: "Ada", avatarUrl: "https://example.test/a.png" },
          message: "ok",
        }),
      ),
    );
    await renderWithQuery(<Header />);
    expect(await screen.findByTestId("session-user")).toHaveTextContent("Ada");
    expect(screen.queryByRole("link", { name: /log in/i })).not.toBeInTheDocument();
  });

  it("falls back to the user id when the nickname is null", async () => {
    server.use(
      http.get("*/api/v1/auth/me", () =>
        HttpResponse.json({
          code: "COMMON_200_000",
          data: { userId: "u1", nickname: null, avatarUrl: null },
          message: "ok",
        }),
      ),
    );
    await renderWithQuery(<Header />);
    expect(await screen.findByTestId("session-user")).toHaveTextContent("u1");
  });
});
