import type { SessionState } from "@/features/session/session";

// `me` names the signed-in roles (#282, BA-1), so admin access is read from the session the root
// route already resolved, with no request of its own (admin plan A3-01). This is UX only: the
// backend checks the role on every admin call, and a role revoked mid-session still reaches the
// Access denied card through the 403 boundary of whichever admin read notices it first.
export class AdminAccessDenied extends Error {
  constructor() {
    super("Admin access denied");
    this.name = "AdminAccessDenied";
  }
}

const ADMIN_ROLE = "admin";

export function requireAdmin(session: SessionState) {
  if (session.status !== "authenticated" || !session.roles.includes(ADMIN_ROLE)) {
    throw new AdminAccessDenied();
  }
}
