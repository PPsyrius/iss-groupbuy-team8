import { ProgressBar } from "@heroui/react";
import { Link } from "@tanstack/react-router";

import type { ActiveDealResponse, ProductSummaryResponse } from "@/api/generated/types.gen";
import { ImageFallback } from "@/components/ImageFallback";

import { SeatsLeft } from "./DealProgress";
import { Price } from "./Price";

// Catalog tile from the Claude Design artboards (docs/design-screens.md §4.1 "Card anatomy"):
// a sanctioned custom composition, not a HeroUI Card, so the media block can bleed to the edges.
// An open bloc adds the artboard's progress row ("3 of 5 joined", seats left, bar) from the
// summary's `deal` (#375, CR-11); the grid shows no countdown. With no bloc price the card reads
// "Buying alone" and offers "Start one →", which goes to the listing detail where the bloc
// actions live. The title link
// stretches over the tile via `after:absolute after:inset-0`, so the tile is `relative` (its
// containing block) and the start link is `relative` too, painting above the stretched overlay.
export function ProductCard({ product }: Readonly<{ product: ProductSummaryResponse }>) {
  const {
    id,
    title,
    subtitle,
    coverImage,
    individualPrice,
    groupPrice,
    currency = "sgd",
    deal,
  } = product;
  const hasBloc = groupPrice !== undefined && groupPrice !== null;
  const detail = id ? { to: "/products/$productId" as const, params: { productId: id } } : null;
  return (
    <article className="border-border bg-surface relative flex h-full flex-col overflow-hidden rounded-lg border">
      <ImageFallback src={coverImage} className="h-[150px] shrink-0 xl:h-[168px]" />
      <div className="flex flex-1 flex-col gap-2.5 p-4">
        <div className="flex flex-col gap-0.5">
          <h3 className="line-clamp-2 text-base font-semibold">
            {detail ? (
              <Link {...detail} className="after:absolute after:inset-0">
                {title}
              </Link>
            ) : (
              title
            )}
          </h3>
          {subtitle ? <p className="text-muted text-sm">{subtitle}</p> : null}
        </div>
        <div className="mt-auto flex flex-wrap items-baseline gap-2.5">
          {hasBloc ? (
            <>
              <Price
                amount={groupPrice}
                currency={currency}
                className="text-accent-soft-foreground text-2xl font-semibold tabular-nums"
              />
              {individualPrice === undefined ? null : (
                <Price
                  amount={individualPrice}
                  currency={currency}
                  className="text-muted text-sm tabular-nums line-through"
                />
              )}
            </>
          ) : (
            <>
              {individualPrice === undefined ? null : (
                <Price
                  amount={individualPrice}
                  currency={currency}
                  className="text-2xl font-semibold tabular-nums"
                />
              )}
              <span className="text-muted text-sm">Buying alone</span>
            </>
          )}
        </div>
        {deal ? <CardProgress deal={deal} /> : null}
        {hasBloc || deal ? null : (
          <div className="flex items-center justify-between gap-2">
            <span className="text-muted text-sm">No open bloc yet</span>
            {detail ? (
              <Link
                {...detail}
                className="text-accent-soft-foreground relative -mr-2.5 inline-flex min-h-11 items-center rounded-lg px-2.5 text-sm font-semibold"
              >
                Start one →
              </Link>
            ) : null}
          </div>
        )}
      </div>
    </article>
  );
}

// §4.1 progress row: "N of M joined", seats left (the last one emphasised) and a 10px bar, as on
// the listing detail's Open bloc row but without its countdown, dots or Join.
function CardProgress({ deal }: Readonly<{ deal: ActiveDealResponse }>) {
  const joined = deal.currentSize ?? 0;
  const target = Math.max(1, deal.targetSize ?? 1);
  const label = `${joined} of ${target} joined`;
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-baseline justify-between gap-2">
        <span className="text-sm tabular-nums">{label}</span>
        <SeatsLeft left={Math.max(0, target - joined)} />
      </div>
      <ProgressBar
        aria-label={label}
        value={Math.min(joined, target)}
        minValue={0}
        maxValue={target}
        color="accent"
        size="sm"
      >
        <ProgressBar.Track className="bg-surface-tertiary h-2.5 rounded-full">
          <ProgressBar.Fill className="rounded-full" />
        </ProgressBar.Track>
      </ProgressBar>
    </div>
  );
}
