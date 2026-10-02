import { Avatar, Button } from "@heroui/react";

import { initials } from "@/features/session/initials";

// The invite landing's lead (`BlocBuy Buyer Invite.dc.html` 5b.1–5b.2) on the listing, for a
// buyer who arrived through a share link (UC7, #30). `sharer` is the nickname the ref resolves to
// (FB-19, #201); with it the banner leads with the artboard's avatar and "Sarah started this bloc
// / and invited you to join it", and the closed variant reads "Sarah’s bloc has closed". Without
// one (none set, a failed resolve, or a ref for another bloc) it says what the link was for.
// `open` is whether the invited bloc is still the listing's open, joinable bloc. Then 5b.1's one
// button leads the page: "Join this bloc · S$71.00" in the artboard's top-rank style (the tinted
// `secondary`, 48px, 18/600, the same rank as the listing's "Start a bloc"), with the sign-in and
// refund micro-copy under it, and the listing's "Start a bloc" steps down a rank for the visit.
// Otherwise the artboard's "bloc full" and "bloc ended" recovery applies: join the open bloc if
// there is one, or start your own.
export function InviteBanner({
  open,
  sharer,
  hasOtherBloc,
  signedIn,
  join,
}: Readonly<{
  open: boolean;
  sharer: string | undefined;
  hasOtherBloc: boolean;
  signedIn: boolean;
  join: { label: string; onPress: (() => void) | undefined; isPending: boolean };
}>) {
  if (open) {
    return (
      <section
        aria-label="Invite"
        className="border-border bg-surface flex flex-col gap-3 rounded-lg border p-4"
      >
        {sharer ? (
          <div className="flex items-center gap-3">
            <Avatar size="lg" color="accent" variant="soft" aria-hidden="true">
              <Avatar.Fallback>{initials(sharer)}</Avatar.Fallback>
            </Avatar>
            <div className="flex min-w-0 flex-col">
              <h2 className="text-lg font-semibold break-words">{sharer} started this bloc</h2>
              <p className="text-sm text-zinc-600">and invited you to join it</p>
            </div>
          </div>
        ) : (
          <h2 className="text-base font-semibold">You’ve been invited to join this bloc</h2>
        )}
        <div className="flex flex-col gap-2">
          <Button
            variant="secondary"
            size="lg"
            className="min-h-12 w-full text-lg font-semibold"
            {...(join.onPress ? { onPress: join.onPress } : {})}
            isPending={join.isPending}
            isDisabled={join.isPending || !join.onPress}
          >
            {join.label}
          </Button>
          <p className="text-center text-xs text-pretty text-zinc-600">
            {signedIn
              ? "You’ll pay the bloc price now. If the bloc doesn’t fill, you’re refunded in full."
              : "You’ll sign in with Google, then pay. If the bloc doesn’t fill, you’re refunded in full."}
          </p>
        </div>
      </section>
    );
  }
  return (
    <section
      aria-label="Invite"
      className="border-border bg-surface flex flex-col gap-1 rounded-lg border p-4"
    >
      <h2 className="text-base font-semibold break-words">
        {sharer ? `${sharer}’s bloc has closed` : "The bloc you were invited to has closed"}
      </h2>
      <p className="text-sm text-zinc-600">
        {hasOtherBloc
          ? "It filled or ended before you got here. You can still get the bloc price by joining the open bloc below, or by starting your own."
          : "It filled or ended before you got here. You can still get the bloc price by starting a bloc of your own."}{" "}
        Nothing has been charged.
      </p>
    </section>
  );
}
