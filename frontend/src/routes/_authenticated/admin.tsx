import { createFileRoute } from "@tanstack/react-router";

import { requireAdmin } from "@/features/admin/admin-guard";
import { AdminRouteError, AdminShell } from "@/features/admin/AdminShell";

// The admin console subtree (admin plan A1-03). The parent `_authenticated` sends anonymous
// visitors to /login; this layout then probes the admin API, and a 403 renders the Access denied
// card in place at /admin rather than redirecting (§12 note 2). The backend enforces the role on
// every admin call; the probe is UX only.
export const Route = createFileRoute("/_authenticated/admin")({
  staticData: { shell: "admin" },
  beforeLoad: ({ context }) => requireAdmin(context.queryClient),
  component: AdminShell,
  errorComponent: AdminRouteError,
});
