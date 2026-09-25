import { Chip, ProgressBar } from "@heroui/react";
import { Link } from "@tanstack/react-router";

import type { MyTeamResponse } from "@/api/generated/types.gen";
import { formatDay, formatDayTime, parseDeadline } from "@/features/catalog/deal-time";
import { useNow } from "@/features/catalog/DealProgress";
import { formatPrice } from "@/features/catalog/Price";

import { blocChip, blocCountdown, blocFilter } from "./bloc-status";

// One §4.4 My blocs row (docs/design-screens.md, `BlocBuy Buyer My Blocs.dc.html`): card padding
// 14, gap 12; 56px thumb, title 14/600, the inverse "You started this" pill for the starter,
// the bloc price 18/24/600, the progress row and 10px bar, the countdown and the paid line, the
// status chip. `GET /group-deals/me` carries no bloc number, so the artboard's "Bloc 2091" is
// not drawn. A closed bloc swaps to the Refunded-filter variant: closing date, "… when it closed",
// a grey bar and the amount as refund due; the endpoint has no refund state, so it never claims
// "Refunded" (plan §12 note 50).
export function BlocRow({ team }: { team: MyTeamResponse }) {
  const now = useNow();
  const filter = blocFilter(team.status);
  const currency = team.currency ?? "sgd";
  const price = Number(team.dealPrice);
  const priceText = Number.isFinite(price) ? formatPrice(price, currency) : null;
  const target = Math.max(1, team.targetSize ?? 1);
  const joined = Math.min(target, Math.max(0, team.currentSize ?? 0));
  const paidMs = parseDeadline(team.joinTime);
  const chip = blocChip(team, now);
  const statusChip = (
    <Chip color={chip.color} variant="soft" size="sm" className="shrink-0">
      {chip.label}
    </Chip>
  );
  const paidLine =
    paidMs === null ? null : (
      <span className="text-xs text-zinc-600">Paid · {formatDayTime(paidMs)}</span>
    );

  if (filter === "refunded") {
    const closedMs = parseDeadline(team.expireTime);
    return (
      <article className="border-border bg-surface flex flex-col gap-3 rounded-lg border p-3.5">
        <div className="flex items-start gap-3">
          <Thumb image={team.productImage} />
          <div className="flex min-w-0 flex-1 flex-col gap-1">
            <Title team={team} />
            <span className="text-xs text-zinc-600 tabular-nums">
              {closedMs === null || closedMs > now ? "Closed" : `Closed ${formatDay(closedMs)}`}
            </span>
            <span className="text-xs tabular-nums">
              {joined} of {target} joined when it closed
            </span>
          </div>
        </div>
        <Progress joined={joined} target={target} tone="closed" />
        <div className="border-separator flex items-center justify-between gap-3 border-t pt-3">
          <div className="flex flex-col">
            {priceText ? (
              <span className="text-sm font-semibold tabular-nums">{priceText} · refund due</span>
            ) : null}
            {paidLine}
          </div>
          {statusChip}
        </div>
      </article>
    );
  }

  const completed = filter === "completed";
  const left = target - joined;
  const successMs = parseDeadline(team.successTime ?? undefined);
  // An open bloc past its deadline is "Ended" while it waits for the sweeper: no countdown and
  // no seats to fill.
  const ended = !completed && chip.label === "Ended";
  const countdown = completed || ended ? null : blocCountdown(team, now);
  return (
    <article className="border-border bg-surface flex flex-col gap-3 rounded-lg border p-3.5">
      <div className="flex items-start gap-3">
        <Thumb image={team.productImage} />
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <div className="flex items-start justify-between gap-2">
            <Title team={team} />
            {team.role?.toLowerCase() === "leader" ? (
              <span className="text-background shrink-0 rounded-full bg-[var(--background-inverse)] px-2 py-0.5 text-xs font-semibold whitespace-nowrap">
                You started this
              </span>
            ) : null}
          </div>
          {priceText ? (
            <span className="text-accent-soft-foreground text-lg font-semibold tabular-nums">
              {priceText}
            </span>
          ) : null}
        </div>
      </div>
      <div className="flex flex-col gap-1.5">
        <div className="flex items-baseline justify-between gap-2">
          <span className="text-sm tabular-nums">
            {joined} of {target} joined
          </span>
          {completed || ended ? null : (
            <span
              className={
                left === 1
                  ? "text-sm font-semibold tabular-nums"
                  : "text-sm text-zinc-600 tabular-nums"
              }
            >
              {left === 1 ? "1 seat left" : `${left} seats left`}
            </span>
          )}
        </div>
        <Progress joined={completed ? target : joined} target={target} tone="accent" />
      </div>
      <div className="flex items-center justify-between gap-3">
        <div className="flex flex-col">
          {countdown ? (
            <span
              className={
                "text-xs font-semibold tabular-nums" +
                (countdown.urgent ? " text-warning-soft-foreground" : "")
              }
            >
              {countdown.text}
            </span>
          ) : null}
          {completed && successMs !== null ? (
            <span className="text-xs font-semibold tabular-nums">
              Filled {formatDayTime(successMs)}
            </span>
          ) : null}
          {paidLine}
        </div>
        {statusChip}
      </div>
    </article>
  );
}

// The deleted-listing case: the title is null and the listing page would 404, so no link.
function Title({ team }: { team: MyTeamResponse }) {
  const { productId, productTitle } = team;
  return (
    <h3 className="line-clamp-2 min-w-0 text-sm font-semibold">
      {productId && productTitle ? (
        <Link to="/products/$productId" params={{ productId }}>
          {productTitle}
        </Link>
      ) : (
        (productTitle ?? "Listing removed")
      )}
    </h3>
  );
}

function Thumb({ image }: { image: string | null | undefined }) {
  return (
    <div
      aria-hidden="true"
      className="size-14 shrink-0 overflow-hidden rounded-lg bg-[repeating-linear-gradient(135deg,var(--surface-secondary)_0_8px,var(--surface-tertiary)_8px_16px)]"
    >
      {image ? (
        <img alt="" className="h-full w-full object-cover" loading="lazy" src={image} />
      ) : null}
    </div>
  );
}

function Progress({
  joined,
  target,
  tone,
}: {
  joined: number;
  target: number;
  tone: "accent" | "closed";
}) {
  return (
    <ProgressBar
      aria-label={`${joined} of ${target} joined`}
      value={joined}
      minValue={0}
      maxValue={target}
      color={tone === "accent" ? "accent" : "default"}
      size="sm"
    >
      <ProgressBar.Track className="bg-surface-tertiary h-2.5 rounded-full">
        <ProgressBar.Fill className={"rounded-full" + (tone === "closed" ? " bg-zinc-400" : "")} />
      </ProgressBar.Track>
    </ProgressBar>
  );
}
