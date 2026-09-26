// The invite landing's lead (`BlocBuy Buyer Invite.dc.html` 5b.1–5b.2) on the listing, for a
// buyer who arrived through a share link (UC7, #30). The contract has no resolve endpoint
// (backend plan D5), so the sharer's name the artboard leads with ("Sarah started this bloc") is
// not drawn; the banner says what the link was for and what happens next. `open` is whether the
// invited bloc is still the listing's open, joinable bloc; otherwise the artboard's "bloc full"
// and "bloc ended" recovery applies: join the open bloc if there is one, or start your own.
export function InviteBanner({
  open,
  hasOtherBloc,
  signedIn,
}: {
  open: boolean;
  hasOtherBloc: boolean;
  signedIn: boolean;
}) {
  if (open) {
    return (
      <section
        aria-label="Invite"
        className="border-border bg-surface flex flex-col gap-1 rounded-lg border p-4"
      >
        <h2 className="text-base font-semibold">You’ve been invited to join this bloc</h2>
        <p className="text-sm text-zinc-600">
          {signedIn
            ? "Join it below at the bloc price. If the bloc doesn’t fill, you’re refunded in full."
            : "Join it below. You’ll sign in with Google, then pay. If the bloc doesn’t fill, you’re refunded in full."}
        </p>
      </section>
    );
  }
  return (
    <section
      aria-label="Invite"
      className="border-border bg-surface flex flex-col gap-1 rounded-lg border p-4"
    >
      <h2 className="text-base font-semibold">The bloc you were invited to has closed</h2>
      <p className="text-sm text-zinc-600">
        {hasOtherBloc
          ? "It filled or ended before you got here. You can still get the bloc price by joining the open bloc below, or by starting your own."
          : "It filled or ended before you got here. You can still get the bloc price by starting a bloc of your own."}{" "}
        Nothing has been charged.
      </p>
    </section>
  );
}
