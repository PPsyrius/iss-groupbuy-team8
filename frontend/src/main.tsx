import { QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "@tanstack/react-router";
import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import { queryClient } from "./api/query-client";
import { createAppRouter } from "./router";

import "./styles/app.css";

async function bootstrap() {
  // Dev flag for offline UI work (plan §5): `?msw=1` starts the browser MSW worker with sample
  // listings before the first query. Read from the URL, not an env var, so one build serves all
  // environments (ADR 0004).
  if (new URLSearchParams(window.location.search).has("msw")) {
    const { startBrowserMsw } = await import("./test/browser-msw");
    await startBrowserMsw();
  }

  const router = createAppRouter({ queryClient, session: null });

  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </StrictMode>,
  );
}

void bootstrap();
