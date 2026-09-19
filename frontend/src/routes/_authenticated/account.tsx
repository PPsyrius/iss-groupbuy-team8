import { Avatar, buttonVariants } from "@heroui/react";
import { createFileRoute, Link } from "@tanstack/react-router";

import { initials } from "@/features/session/initials";

export const Route = createFileRoute("/_authenticated/account")({
  component: AccountPage,
});

// Interim account page (docs/design-screens.md §4.4, §7 Q15): no artboard draws a profile, and
// the My blocs screen needs per-user bloc data the contract does not expose yet. This borrows its
// signed-in header pattern (title, 32px letter avatar) and its empty-state card; the Active /
// Completed / Refunded chips wait for that data, so nothing here is invented.
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
      <section className="border-border bg-surface flex flex-col gap-3 rounded-lg border p-5 xl:max-w-[560px] xl:p-8">
        <h2 className="text-lg font-semibold">You haven’t joined a bloc yet</h2>
        <p className="text-base text-zinc-600">
          Join one that’s filling, or start your own and share the link with your neighbours. Either
          way you’re refunded in full if it doesn’t fill.
        </p>
        <Link
          to="/"
          className={
            buttonVariants({ variant: "secondary", size: "lg" }) +
            " min-h-12 justify-center text-lg font-semibold xl:self-start"
          }
        >
          See what’s filling
        </Link>
      </section>
    </main>
  );
}
