import { Skeleton } from "@heroui/react";

import type { MyTeamResponse } from "@/api/generated/types.gen";

import { blocCounts, blocFilter, blocFilters, type BlocFilter } from "./bloc-status";
import { BlocFilterChips } from "./BlocFilterChips";
import { BlocRow } from "./BlocRow";

// The §4.4 My blocs list on `/account` (UC8, #31). `GET /group-deals/me` has no status filter
// and no counts, so the route fetches the latest 100 blocs once and the chips filter and count
// them here; switching chips never refetches (plan §12 note 50).
export const MY_BLOCS_FETCH_SIZE = 100;

const noTeams: MyTeamResponse[] = [];

export function MyBlocs({
  teams = noTeams,
  total,
  selected,
}: {
  teams?: MyTeamResponse[] | undefined;
  total: number;
  selected: BlocFilter;
}) {
  const shown = teams.filter((team) => blocFilter(team.status) === selected);
  const label = blocFilters.find((f) => f.value === selected)?.label.toLowerCase() ?? selected;
  return (
    <div className="flex flex-col gap-3.5 xl:max-w-[720px]">
      <BlocFilterChips selected={selected} counts={blocCounts(teams)} />
      {total > teams.length ? (
        <p className="text-xs text-zinc-600">Showing your latest {teams.length} blocs</p>
      ) : null}
      {shown.length === 0 ? (
        <p className="text-sm text-zinc-600">No {label} blocs</p>
      ) : (
        <ul aria-label="My blocs" className="flex flex-col gap-3">
          {shown.map((team) => (
            <li key={team.dealId}>
              <BlocRow team={team} />
            </li>
          ))}
        </ul>
      )}
      {selected === "refunded" ? (
        <p className="bg-surface-secondary rounded-lg px-3.5 py-3 text-xs text-zinc-600">
          Refunds are issued automatically when a bloc closes unfilled, or when a payment could not
          secure a seat. Timing depends on your bank.
        </p>
      ) : null}
    </div>
  );
}

// §4.4 loading: three 44px chip skeletons (96 / 112 / 104 wide) and two row skeletons in the
// row's geometry (56px thumb, two text bars, the 10px progress bar).
export function BlocsSkeleton() {
  return (
    <div className="flex flex-col gap-3.5 xl:max-w-[720px]">
      <div className="flex gap-2">
        <Skeleton className="h-11 w-24 rounded-full" />
        <Skeleton className="h-11 w-28 rounded-full" />
        <Skeleton className="h-11 w-26 rounded-full" />
      </div>
      <ul className="flex flex-col gap-3">
        {[0, 1].map((i) => (
          <li
            key={i}
            className="border-border bg-surface flex flex-col gap-3 rounded-lg border p-3.5"
          >
            <div className="flex gap-3">
              <Skeleton className="size-14 shrink-0 rounded-lg" />
              <div className="flex flex-1 flex-col gap-2">
                <Skeleton className="h-3.5 w-[76%] rounded-full" />
                <Skeleton className="h-5 w-[40%] rounded-full" />
              </div>
            </div>
            <Skeleton className="h-2.5 rounded-full" />
          </li>
        ))}
      </ul>
    </div>
  );
}
