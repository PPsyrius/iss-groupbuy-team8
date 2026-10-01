import { Link } from "@tanstack/react-router";

import type { ProductSummaryResponse } from "@/api/generated/types.gen";
import { ImageFallback } from "@/components/ImageFallback";

import { Price } from "./Price";

// Catalog tile from the Claude Design artboards (docs/design-screens.md §4.1 "Card anatomy"):
// a sanctioned custom composition, not a HeroUI Card, so the media block can bleed to the edges.
// The artboard's team-progress row ("3 of 5 joined", bar) is not rendered: ProductSummaryResponse
// carries no deal sizes (CR-11). With no bloc price the card reads "Buying alone" and offers
// "Start one →", which goes to the listing detail where the bloc actions live. The title link
// stretches over the tile via `after:absolute after:inset-0`, so the tile is `relative` (its
// containing block) and the start link is `relative` too, painting above the stretched overlay.
export function ProductCard({ product }: { product: ProductSummaryResponse }) {
  const {
    id,
    title,
    subtitle,
    coverImage,
    individualPrice,
    groupPrice,
    currency = "sgd",
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
        {hasBloc ? null : (
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
