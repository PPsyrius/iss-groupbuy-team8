import { TanStackDevtools } from "@tanstack/react-devtools";
import { ReactQueryDevtoolsPanel } from "@tanstack/react-query-devtools";
import { TanStackRouterDevtoolsPanel } from "@tanstack/react-router-devtools";

// Development-only module. `vite.config.ts` aliases `@/dev/devtools` to `./devtools.noop` for
// `vite build`, so the delivery artifact never contains the devtools packages (ADR 0004
// addendum 2026-09-23, #154). No environment variable is involved: the switch is the build command.
const devtoolsPlugins = [
  { name: "TanStack Query", render: <ReactQueryDevtoolsPanel /> },
  { name: "TanStack Router", render: <TanStackRouterDevtoolsPanel /> },
];

// Opt-in, like `?msw=1` in main.tsx: the floating trigger sits over the wordmark on a phone, so a
// plain `pnpm dev` session stays clean for demos and exactly `?devtools=1` mounts it. Read once at
// start-up because in-app navigation drops the flag from the URL.
const requested = new URLSearchParams(window.location.search).get("devtools") === "1";

export function Devtools() {
  return requested ? <TanStackDevtools plugins={devtoolsPlugins} /> : null;
}
