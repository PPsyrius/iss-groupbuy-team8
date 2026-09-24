import { Avatar, buttonVariants } from "@heroui/react";
import { createFileRoute, Link } from "@tanstack/react-router";

import { initials } from "@/features/session/initials";

export const Route = createFileRoute("/_authenticated/account")({
  component: AccountPage,
});

// Interim account page (docs/design-screens.md §4.4, §7 Q15): the signed-in header and a card
// in the §4.4 empty-state layout, restored by #137 after #126 cut the page down to its header.
// The copy is neutral, not §4.4's "You haven't joined a bloc yet": the page cannot tell whether
// the buyer has orders, and the payment receipt links here as "My orders" (#165 review). The
// order history replaces the card once the order team's GET /orders/me lands (#128); the Active /
// Completed / Refunded chips wait for per-user bloc data the contract does not expose yet.
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
        <h2 className="text-lg font-semibold">Your orders will appear here</h2>
        <p className="text-base text-zinc-600">
          Order history is on its way. Every bloc either fills or refunds you in full.
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
