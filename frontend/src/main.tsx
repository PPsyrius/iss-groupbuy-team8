import { QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "@tanstack/react-router";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import { queryClient } from "./api/query-client";
import { AppErrorBoundary } from "./components/AppErrorBoundary";
import { createAppRouter } from "./router";

import "./styles/app.css";

// Dev flag for offline UI work (plan §5): exactly `?msw=1` starts the browser MSW worker with
// sample listings before the first query (`msw=0` or a bare `msw` does not). Read from the
// URL, not an env var (ADR 0004); `vite build` swaps the module for a no-op (#156).
if (new URLSearchParams(window.location.search).get("msw") === "1") {
  const { startBrowserMsw } = await import("@/dev/browser-msw");
  await startBrowserMsw();
}

const router = createAppRouter({ queryClient, session: { status: "anonymous" } });

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AppErrorBoundary>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </AppErrorBoundary>
  </StrictMode>,
);
