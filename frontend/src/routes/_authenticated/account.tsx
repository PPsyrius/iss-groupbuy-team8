import { Avatar } from "@heroui/react";
import { createFileRoute } from "@tanstack/react-router";

import { initials } from "@/features/session/initials";

export const Route = createFileRoute("/_authenticated/account")({
  component: AccountPage,
});

// The account header only. The order history section returns with the order team's /orders/me:
// 0b1f14d dropped this branch's duplicate GET /api/v1/orders, so no listing endpoint exists yet.
const shell = "mx-auto flex max-w-[1280px] flex-col gap-4 p-4 xl:gap-6 xl:p-8";

function AccountPage() {
  const { session } = Route.useRouteContext();
  if (session.status !== "authenticated") return null; // the layout guard already redirected
  const name = session.nickname ?? session.userId;
  return (
    <main className={shell}>
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-lg font-semibold xl:text-2xl">My blocs</h1>
        <div className="flex items-center gap-2">
          <Avatar size="sm" aria-hidden="true">
            {session.avatarUrl ? <Avatar.Image src={session.avatarUrl} alt="" /> : null}
            <Avatar.Fallback>{initials(name)}</Avatar.Fallback>
          </Avatar>
          <span data-testid="account-user" className="text-sm font-medium">
            {name}
          </span>
        </div>
      </div>
    </main>
  );
}
