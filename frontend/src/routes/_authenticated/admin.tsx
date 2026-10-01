import { createFileRoute } from "@tanstack/react-router";

import { requireAdmin } from "@/features/admin/admin-guard";
import { AdminRouteError, AdminShell } from "@/features/admin/AdminShell";

// The admin console subtree (admin plan A1-03, A3-01). The parent `_authenticated` sends anonymous
// visitors to /login; this layout then reads the session's roles, and a missing admin role renders
// the Access denied card in place at /admin rather than redirecting (§12 note 2). The backend
// enforces the role on every admin call; the guard is UX only.
export const Route = createFileRoute("/_authenticated/admin")({
  staticData: { shell: "admin" },
  beforeLoad: ({ context }) => requireAdmin(context.session),
  component: AdminShell,
  errorComponent: AdminRouteError,
});
