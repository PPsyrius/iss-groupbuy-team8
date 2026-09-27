import { Link } from "@tanstack/react-router";

import { blocFilters, type BlocFilter } from "./bloc-status";

// The §4.4 filter chips "Active 3", "Completed 7", "Refunded 2": 44px pills, count 12 tabular,
// selected accent-soft over white. Same pattern as the catalog's category chips (FilterForm):
// router links, because the filter is URL state (`?blocs=`), and the selected chip is the
// current page rather than a link. The order-history paging in the URL is kept as it is.
const chip =
  "inline-flex min-h-11 items-center gap-1.5 rounded-full border px-4 text-sm font-medium whitespace-nowrap";
const chipOff = "border-border bg-surface text-foreground hover:bg-surface-secondary";
const chipOn =
  "border-accent-soft bg-surface bg-[image:linear-gradient(var(--accent-soft),var(--accent-soft))] text-accent-soft-foreground";

export function BlocFilterChips({
  selected,
  counts,
}: {
  selected: BlocFilter;
  counts: Record<BlocFilter, number>;
}) {
  return (
    <fieldset className="flex flex-wrap gap-2">
      <legend className="sr-only">Show blocs</legend>
      {blocFilters.map(({ value, label }) => {
        const count = (
          <span
            className={
              "text-xs tabular-nums" +
              (value === selected ? " text-accent-soft-foreground" : " text-zinc-600")
            }
          >
            {counts[value]}
          </span>
        );
        return value === selected ? (
          <span key={value} aria-current="page" className={`${chip} ${chipOn}`}>
            {label} {count}
          </span>
        ) : (
          <Link
            key={value}
            to="/account"
            search={(prev) => ({ ...prev, blocs: value })}
            className={`${chip} ${chipOff}`}
          >
            {label} {count}
          </Link>
        );
      })}
    </fieldset>
  );
}
