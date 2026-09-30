import { Button, Chip, ProgressBar } from "@heroui/react";
import { useId, useSyncExternalStore } from "react";

import type { ActiveDealResponse } from "@/api/generated/types.gen";

import { formatCountdown, formatDeadline, hasEnded, parseDeadline } from "./deal-time";

// One open-bloc row from the Listing Detail artboards (docs/design-screens.md §4.2 "Deal
// progress row"). The contract's `ActiveDealResponse` carries sizes and the deadline only, so the
// row has no starter, bloc number or avatar (CR: open-team list); it is labelled "Open bloc".
// With no deal the artboard's "no open bloc" copy renders instead (plan §12 note 41).

// Live clock, one tick per second, shared by every row on the page. `useSyncExternalStore`
// keeps `Date.now()` out of render (React Compiler purity rule).
function subscribe(onTick: () => void) {
  const id = setInterval(onTick, 1000);
  return () => clearInterval(id);
}
function getNow() {
  return Math.floor(Date.now() / 1000) * 1000;
}
export function useNow() {
  return useSyncExternalStore(subscribe, getNow, getNow);
}

const maxSeatDots = 10;

// `onJoin` is the gated action for the row's Join (T2-04: anonymous buyers get the sign-in
// sheet); without one the button is disabled rather than activatable with no effect
// (CodeRabbit r4045102864, r4050400116). A full or ended bloc disables Join whatever the handler
// and names the reason (T3-04); the server repeats both checks (GROUPBUY_409_004 / _002).
// A buyer already in the bloc (`member`, from `GET /group-deals/me`) gets the same treatment
// with its own reason (FB-20); the server would refuse the JOIN with GROUPBUY_409_003.
export function DealProgress({
  deal,
  onJoin,
  member = false,
}: Readonly<{
  deal: ActiveDealResponse | undefined;
  onJoin?: (() => void) | undefined;
  member?: boolean;
}>) {
  const now = useNow();
  const reasonId = useId();
  if (!deal) return <NoOpenBloc />;
  const joined = deal.currentSize ?? 0;
  const target = Math.max(1, deal.targetSize ?? 1);
  const left = Math.max(0, target - joined);
  const expireMs = parseDeadline(deal.expireTime);
  const countdown = expireMs === null ? null : formatCountdown(expireMs, now);
  const urgent = countdown?.urgent ?? false;
  const ended = hasEnded(deal.expireTime, now);
  const blocked = joinBlockedReason({ ended, full: left === 0, member });
  const join = onJoin && !blocked ? { onPress: onJoin } : { isDisabled: true };
  return (
    <article
      aria-label="Open bloc"
      className="border-border bg-surface flex flex-col gap-3 rounded-lg border p-4 xl:p-5"
    >
      <div className="flex items-center justify-between gap-3">
        <span className="text-sm font-semibold">Open bloc</span>
        {ended ? (
          <Chip color="default" variant="soft" size="sm">
            Ended
          </Chip>
        ) : left === 0 ? (
          <Chip color="default" variant="soft" size="sm">
            Full
          </Chip>
        ) : urgent ? (
          <Chip color="warning" variant="soft" size="sm">
            Closing soon
          </Chip>
        ) : (
          <Chip color="default" variant="soft" size="sm">
            Filling
          </Chip>
        )}
      </div>
      <div className="flex items-baseline justify-between gap-3">
        <span className="text-sm tabular-nums">
          {joined} of {target} joined
        </span>
        <span
          className={
            left === 1 ? "text-sm font-semibold tabular-nums" : "text-muted text-sm tabular-nums"
          }
        >
          {left === 1 ? "1 seat left" : `${left} seats left`}
        </span>
      </div>
      <ProgressBar
        aria-label={`${joined} of ${target} joined`}
        value={joined}
        minValue={0}
        maxValue={target}
        color="accent"
        size="sm"
      >
        <ProgressBar.Track className="bg-surface-tertiary h-2.5 rounded-full">
          <ProgressBar.Fill className="rounded-full" />
        </ProgressBar.Track>
      </ProgressBar>
      {target <= maxSeatDots ? (
        <ul aria-hidden="true" className="flex gap-1.5">
          {Array.from({ length: target }, (_, i) => (
            <li
              key={i}
              className={
                "border-border h-5 w-5 rounded-full border" +
                (i < joined ? " bg-accent border-accent" : " bg-surface")
              }
            />
          ))}
        </ul>
      ) : null}
      <div className="flex items-center justify-between gap-3">
        <div className="flex flex-col">
          {countdown ? (
            <span
              className={
                "text-sm font-semibold tabular-nums" +
                (urgent ? " text-warning-soft-foreground" : "")
              }
            >
              {countdown.text}
            </span>
          ) : null}
          {expireMs === null ? null : (
            <span className="text-muted text-xs">{formatDeadline(expireMs, now)}</span>
          )}
        </div>
        <div className="flex flex-col items-end gap-1">
          <Button
            variant="outline"
            className="min-h-11 px-4"
            {...join}
            {...(blocked ? { "aria-describedby": reasonId } : {})}
          >
            <span className="xl:hidden">Join</span>
            <span className="hidden xl:inline">Join this bloc</span>
          </Button>
          {blocked ? (
            <span id={reasonId} className="text-muted text-xs">
              {blocked}
            </span>
          ) : null}
        </div>
      </div>
    </article>
  );
}

// Why Join is disabled, in the order a buyer can act on least: an ended bloc, then a full one,
// then the buyer's own membership. `null` while the bloc can still take this buyer.
function joinBlockedReason({
  ended,
  full,
  member,
}: Readonly<{ ended: boolean; full: boolean; member: boolean }>) {
  if (ended) return "No longer taking members";
  if (full) return "This bloc is full";
  if (member) return "You’re in this bloc";
  return null;
}

function NoOpenBloc() {
  return (
    <div className="flex flex-col gap-3">
      <p className="text-muted text-sm">Buying alone. No bloc is open yet.</p>
      <div className="border-border bg-surface flex flex-col gap-2 rounded-lg border p-4 xl:p-5">
        <h3 className="text-base font-semibold">Be the first to start a bloc</h3>
        <p className="text-muted text-sm">
          You pay the bloc price now and get an invite link to share. If four neighbours join before
          the deadline, everyone keeps the lower price. If not, you are refunded in full.
        </p>
      </div>
    </div>
  );
}
