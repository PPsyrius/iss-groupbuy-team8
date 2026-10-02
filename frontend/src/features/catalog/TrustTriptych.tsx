import { formatDeadlineShort } from "./deal-time";

// The trust triptych (§4.2): what the buyer pays now, when the bloc closes, and the refund if it
// does not fill. The listing's price card shows it, and so does the gated sheet (artboard
// `BlocBuy Buyer Sign In.dc.html` 3c.4 / 3c.7, FB-11), so the price and the deadline travel with
// the buyer into sign-in.
export function TrustTriptych({
  payNow,
  closesMs,
}: Readonly<{ payNow: string; closesMs: number }>) {
  const cell = "flex flex-col gap-0.5 rounded-lg border-border border p-3";
  return (
    <dl className="grid grid-cols-3 gap-2">
      <div className={cell}>
        <dt className="text-muted text-xs">You pay now</dt>
        <dd className="text-accent-soft-foreground text-sm font-semibold tabular-nums">{payNow}</dd>
      </div>
      <div className={cell}>
        <dt className="text-muted text-xs">Bloc closes</dt>
        <dd className="text-sm font-semibold tabular-nums">{formatDeadlineShort(closesMs)}</dd>
      </div>
      <div className={cell}>
        <dt className="text-muted text-xs">If it doesn’t fill</dt>
        <dd className="text-success-soft-foreground text-sm font-semibold">Refunded in full</dd>
      </div>
    </dl>
  );
}
