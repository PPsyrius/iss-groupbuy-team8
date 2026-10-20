import { Spinner } from "@heroui/react";
import { createFileRoute, redirect } from "@tanstack/react-router";

import { meQueryKey } from "@/api/generated/@tanstack/react-query.gen";
import { BrandIsologo } from "@/components/BrandMark";
import { takeReturnTo } from "@/features/session/return-to";
import { sessionFromQuery } from "@/features/session/session";

// The backend's success landing (contracts/openapi.json googleCallback: 302 here with the
// blocbuy-token cookie set). The route owns no token: it just re-resolves `me` and goes back to
// the path parked before the Google hop (ADR 0002). Landing here without a session means the
// cookie never arrived, which is a provider error as far as the buyer is concerned.
export const Route = createFileRoute("/auth/callback")({
  beforeLoad: async ({ context }) => {
    await context.queryClient.invalidateQueries({ queryKey: meQueryKey() });
    const session = await sessionFromQuery(context.queryClient);
    const returnTo = takeReturnTo();
    if (session.status === "authenticated") throw redirect({ href: returnTo, replace: true });
    throw redirect({ to: "/login", search: { returnTo, error: "no_session" }, replace: true });
  },
  pendingMs: 0,
  pendingComponent: SigningIn,
  component: SigningIn,
});

function SigningIn() {
  return (
    <main className="flex flex-col items-center gap-3 p-16" aria-live="polite">
      <title>Signing in · BlocBuy</title>
      {/* The screen has no header, so the mark is the only sign the buyer is back on BlocBuy. */}
      <BrandIsologo alt="" className="h-12 w-auto" />
      <Spinner aria-label="Signing you in" />
      <p className="text-sm text-zinc-600">Signing you in…</p>
    </main>
  );
}
