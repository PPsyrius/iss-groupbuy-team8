import { Skeleton } from "@heroui/react";

import type { ListingCountsResponse } from "@/api/generated/types.gen";

// 4a.1: the four counts the backend can answer (#290), in neutral surface panels with no accent,
// sparkline or trend, because there is no series to trend. The artboard's Open blocs detail,
// "filling across N listings", has no source in `counts` and is left out (admin plan A2-02).
const cards = [
  { label: "Drafts", key: "draft", detail: "private until published" },
  { label: "On sale", key: "onSale", detail: "visible on the storefront" },
  { label: "Off sale", key: "offSale", detail: "delisted, no new blocs" },
  { label: "Open blocs", key: "openDeals", detail: undefined },
] as const;

export function StatCards({
  counts,
  failed = false,
}: Readonly<{ counts: ListingCountsResponse | undefined; failed?: boolean }>) {
  // While the counts load the numbers are withheld; a failed read shows a dash, not a guess.
  const loading = counts === undefined && !failed;
  return (
    <dl aria-busy={loading ? true : undefined} className="grid grid-cols-4 gap-4">
      {cards.map((c) => (
        <div
          key={c.key}
          className="border-border bg-surface flex flex-col gap-1.5 rounded-lg border p-5"
        >
          <dt className="text-muted">{c.label}</dt>
          <dd className="text-4xl font-semibold tracking-tight tabular-nums">
            {loading ? (
              <Skeleton aria-hidden className="my-1 h-8 w-16 rounded-md" />
            ) : (
              (counts?.[c.key] ?? (failed ? "—" : 0))
            )}
          </dd>
          {c.detail === undefined ? null : <dd className="text-muted text-xs">{c.detail}</dd>}
        </div>
      ))}
    </dl>
  );
}
