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

export function Devtools() {
  return <TanStackDevtools plugins={devtoolsPlugins} />;
}
