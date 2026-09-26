import { Alert, Button, buttonVariants, Skeleton } from "@heroui/react";
import { queryOptions, useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute, Link, notFound, useRouter } from "@tanstack/react-router";
import { useState } from "react";

import { ApiError, unwrapResult } from "@/api/envelope";
import { getListingOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { ProductDetailResponse } from "@/api/generated/types.gen";
import { NotFoundCard } from "@/components/ErrorCard";
import { formatDeadlineShort, hasEnded, parseDeadline } from "@/features/catalog/deal-time";
import { DealProgress, useNow } from "@/features/catalog/DealProgress";
import { formatPrice, Price } from "@/features/catalog/Price";
import { ProductGallery } from "@/features/catalog/ProductGallery";
import { QuoteSheet } from "@/features/payment/QuoteSheet";
import { useCheckout } from "@/features/payment/use-checkout";
import { useQuote } from "@/features/payment/use-quote";
import { GatedActionSheet } from "@/features/session/GatedActionSheet";
import { useSession } from "@/features/session/use-session";

// A 200 envelope without `data` is malformed for this endpoint; rejecting it here sends it to
// the route's error boundary instead of a TypeError in the page (CodeRabbit r4045102889).
// No `staleTime` here on purpose (AGENTS.md rule 7, ADR 0001): the loader below passes
// `staleTime: "static"` so navigation never waits on a cached listing, while the component's
// 30 s default refetches seat counts in the background when the cache is older than that.
// Static on the shared options would freeze the deal row until a manual invalidate
// (CodeRabbit r4047087107, declined).
const detailOptions = (id: string) =>
  queryOptions({
    ...getListingOptions({ path: { id } }),
    select: (r) => unwrapResult(r, { allowVoid: false }),
  });

// The contract declares only a 200 for GET /api/v1/products/{id}; the backend's not-found shape
// is either an HTTP 404 (the interceptor stamps `status`) or an envelope code of the
// `<DOMAIN>_404_<n>` family. Both map to the route's not-found state (plan §12 note 42).
function isListingNotFound(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 404 || /_404_/.test(error.code));
}

export const Route = createFileRoute("/products/$productId")({
  loader: async ({ context, params }) => {
    try {
      await context.queryClient.query({
        ...detailOptions(params.productId),
        staleTime: "static",
      });
    } catch (error) {
      if (isListingNotFound(error)) throw notFound();
      throw error;
    }
  },
  pendingComponent: DetailLoading,
  errorComponent: DetailError,
  notFoundComponent: NotFoundCard,
  component: ProductDetailPage,
});

// Layout follows `BlocBuy Buyer Listing Detail.dc.html` (docs/design-screens.md §4.2): phone is
// one column with a full-bleed gallery, the info block, the open-bloc section, the About card and
// a bottom bar pinned under the content; 1280 is a `620px minmax(0,1fr)` grid, gallery and About
// on the left, price card and blocs on the right. The global Header stays; the artboard's
// sub-page header becomes a crumb row under it (note 41).
const shell = "mx-auto flex max-w-[1280px] flex-col gap-4 p-4 xl:gap-6 xl:px-8 xl:pt-6 xl:pb-8";
const card = "border-border bg-surface rounded-lg border";

function titleCase(value: string): string {
  return value.charAt(0).toUpperCase() + value.slice(1);
}

// `images` first (up to 20 URLs per the artboard), else the cover, else nothing (fallback tile).
function galleryImages(p: ProductDetailResponse): string[] {
  if (p.images?.length) return p.images;
  return p.coverImage ? [p.coverImage] : [];
}

function ProductDetailPage() {
  const { productId } = Route.useParams();
  const { data: p } = useSuspenseQuery(detailOptions(productId));
  const now = useNow();
  // Same clock and rule as the DealProgress row's "Ended" chip, so the header never calls an
  // expired bloc "filling" (CodeRabbit r4042898931).
  const ended = hasEnded(p.deal?.expireTime, now);
  const closesMs = parseDeadline(p.deal?.expireTime);
  const currency = p.currency ?? "sgd";
  const groupPrice = p.groupPrice ?? undefined;
  const category = p.category ? titleCase(p.category) : undefined;
  const images = galleryImages(p);
  // Keep the existing sign-in gate; the server derives buyer identity and price.
  const session = useSession();
  const [gateOpen, setGateOpen] = useState(false);
  const onGated = session.status === "authenticated" ? undefined : () => setGateOpen(true);
  const buyerId = session.status === "authenticated" ? session.userId : undefined;
  const checkout = useCheckout(buyerId);
  // START goes through the quote sheet first (T3-01); JOIN has no quote and pays directly.
  // The sheet belongs to the buyer and listing it was opened for: this component stays mounted
  // across `$productId` changes, so a new listing starts with the sheet closed (CodeRabbit
  // r4102738802).
  const quoting = useQuote(buyerId, productId);
  const sheetFor = `${buyerId ?? ""}/${productId}`;
  const [openFor, setOpenFor] = useState<string | null>(null);
  const quoteOpen = openFor === sheetFor;
  const setQuoteOpen = (open: boolean) => setOpenFor(open ? sheetFor : null);
  const onStart =
    onGated ??
    (checkout.enabled
      ? () => {
          setQuoteOpen(true);
          void quoting.load();
        }
      : undefined);
  const onJoin =
    onGated ??
    (checkout.enabled &&
    p.deal?.dealId &&
    !ended &&
    (p.deal.currentSize ?? 0) < (p.deal.targetSize ?? 0)
      ? () => {
          void checkout.begin("JOIN", p.deal!.dealId!);
        }
      : undefined);
  return (
    <main className={shell}>
      <GatedActionSheet
        isOpen={gateOpen}
        onOpenChange={setGateOpen}
        returnTo={`/products/${productId}`}
      />
      <QuoteSheet
        isOpen={quoteOpen}
        onOpenChange={setQuoteOpen}
        state={quoting.state}
        now={now}
        isPaying={checkout.isPending}
        // Success leaves for Stripe; a failure closes the sheet so the page-level alert shows.
        onContinue={() => {
          void checkout.begin("START", productId).then(() => setQuoteOpen(false));
        }}
        onRefresh={() => {
          void quoting.refresh();
        }}
        onRetry={() => {
          void quoting.load();
        }}
      />
      <CrumbRow category={category} title={p.title} />
      {checkout.error ? (
        <p role="alert" className="text-danger">
          {checkout.error}
        </p>
      ) : null}
      <div className="flex flex-col gap-4 xl:grid xl:grid-cols-[620px_minmax(0,1fr)] xl:gap-8">
        <div className="flex min-w-0 flex-col gap-4 xl:gap-6">
          <ProductGallery key={productId} images={images} />
          <AboutCard product={p} category={category} className="hidden xl:flex" />
        </div>
        <div className="flex flex-col gap-4 xl:gap-6">
          <section className={"flex flex-col gap-3 xl:gap-4 xl:p-6 " + card + " max-xl:border-0"}>
            <div className="flex flex-col gap-1">
              <h1 className="text-2xl font-semibold tracking-tight xl:text-3xl">{p.title}</h1>
              {p.subtitle ? <p className="text-muted text-base">{p.subtitle}</p> : null}
            </div>
            <div className="flex flex-wrap items-baseline gap-3">
              {groupPrice !== undefined ? (
                <>
                  <Price
                    amount={groupPrice}
                    currency={currency}
                    className="text-accent-soft-foreground text-4xl font-semibold tracking-tight tabular-nums"
                  />
                  {p.individualPrice === undefined ? null : (
                    <Price
                      amount={p.individualPrice}
                      currency={currency}
                      className="text-muted text-lg tabular-nums line-through"
                    />
                  )}
                </>
              ) : p.individualPrice === undefined ? null : (
                <Price
                  amount={p.individualPrice}
                  currency={currency}
                  className="text-4xl font-semibold tracking-tight tabular-nums"
                />
              )}
            </div>
            {groupPrice !== undefined && p.deal?.targetSize ? (
              <p className="text-muted text-sm">
                Bloc price when a bloc fills · {p.deal.targetSize} seats per bloc
              </p>
            ) : null}
            <StockLine product={p} />
            {groupPrice !== undefined && closesMs !== null ? (
              <TrustTriptych payNow={formatPrice(groupPrice, currency)} closesMs={closesMs} />
            ) : null}
            <Actions
              product={p}
              currency={currency}
              onGated={onGated}
              onStart={onStart}
              isPending={checkout.isPending}
              className="hidden xl:flex"
            />
          </section>
          <section className="flex flex-col gap-3">
            {p.deal ? (
              <div className="flex items-baseline justify-between gap-3">
                <h2 className="text-lg font-semibold xl:text-xl">Open blocs</h2>
                <span className="text-muted text-sm">
                  {ended ? (
                    "This bloc has ended"
                  ) : (
                    <>
                      <span className="xl:hidden">Join one, or start your own</span>
                      <span className="hidden xl:inline">1 filling now</span>
                    </>
                  )}
                </span>
              </div>
            ) : null}
            <DealProgress deal={p.deal} onJoin={checkout.isPending ? undefined : onJoin} />
          </section>
          <AboutCard product={p} category={category} className="flex xl:hidden" />
        </div>
      </div>
      <Actions
        product={p}
        currency={currency}
        onGated={onGated}
        onStart={onStart}
        isPending={checkout.isPending}
        className="bg-surface border-border sticky bottom-0 -mx-4 flex border-t px-4 py-3 xl:hidden"
      />
    </main>
  );
}

function CrumbRow({
  category,
  title,
}: {
  category: string | undefined;
  title: string | undefined;
}) {
  return (
    <nav aria-label="Breadcrumb" className="flex items-center gap-1 text-sm">
      <Link
        to="/"
        aria-label="Back to browse"
        className="-ml-3 inline-flex h-11 w-11 items-center justify-center rounded-lg xl:hidden"
      >
        <svg aria-hidden="true" width="20" height="20" viewBox="0 0 16 16" fill="none">
          <path
            d="M10 3.5 5.5 8l4.5 4.5"
            stroke="currentColor"
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </Link>
      <span className="text-muted xl:hidden">{category ?? "Back to browse"}</span>
      <ol className="hidden items-center gap-2 xl:flex">
        <li>
          <Link to="/" className="text-[#52525B] hover:underline">
            Home
          </Link>
        </li>
        {category ? (
          <>
            <li aria-hidden="true" className="text-muted">
              /
            </li>
            <li className="text-[#52525B]">{category}</li>
          </>
        ) : null}
        {title ? (
          <>
            <li aria-hidden="true" className="text-muted">
              /
            </li>
            <li aria-current="page" className="text-foreground">
              {title}
            </li>
          </>
        ) : null}
      </ol>
    </nav>
  );
}

function StockLine({ product: p }: { product: ProductDetailResponse }) {
  if (p.soldCount === undefined && p.stock === undefined) return null;
  return (
    <p className="text-sm tabular-nums">
      {p.soldCount === undefined ? null : <span>{p.soldCount} sold</span>}
      {p.soldCount !== undefined && p.stock !== undefined ? (
        <span className="text-muted"> · </span>
      ) : null}
      {p.stock === undefined ? null : <span className="text-muted">{p.stock} in stock</span>}
    </p>
  );
}

function TrustTriptych({ payNow, closesMs }: { payNow: string; closesMs: number }) {
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

// Both actions are the artboard's, in its order of prominence (Start a bloc outranks Buy alone).
// Buy alone stays separate and disabled for authenticated buyers until its own flow exists.
function Actions({
  product: p,
  currency,
  onGated,
  onStart,
  isPending,
  className,
}: {
  product: ProductDetailResponse;
  currency: string;
  onGated: (() => void) | undefined;
  onStart: (() => void) | undefined;
  isPending: boolean;
  className: string;
}) {
  const groupPrice = p.groupPrice ?? undefined;
  // `exactOptionalPropertyTypes`: an absent handler is no prop at all, not `onPress={undefined}`.
  const press = onGated ? { onPress: onGated } : { isDisabled: true };
  return (
    <div className={"flex-col gap-2 " + className}>
      <Button
        variant="secondary"
        size="lg"
        className="min-h-12 w-full text-lg"
        {...(onStart ? { onPress: onStart } : {})}
        isPending={isPending}
        isDisabled={isPending || !onStart}
      >
        {groupPrice === undefined
          ? "Start a bloc"
          : `Start a bloc · ${formatPrice(groupPrice, currency)}`}
      </Button>
      {p.individualPrice === undefined ? null : (
        <Button
          variant="ghost"
          className="min-h-11 w-full text-base font-medium xl:hidden"
          {...press}
        >
          Buy alone · {formatPrice(p.individualPrice, currency)}
        </Button>
      )}
      {p.individualPrice === undefined ? null : (
        <Button
          variant="outline"
          className="hidden min-h-11 w-full text-base xl:inline-flex"
          {...press}
        >
          Buy alone · {formatPrice(p.individualPrice, currency)}
        </Button>
      )}
    </div>
  );
}

function AboutCard({
  product: p,
  category,
  className,
}: {
  product: ProductDetailResponse;
  category: string | undefined;
  className: string;
}) {
  return (
    <section className={card + " flex-col gap-2 p-4 xl:gap-3 xl:p-6 " + className}>
      <h2 className="text-base font-semibold xl:text-lg">About this listing</h2>
      {p.description ? (
        <p className="text-muted text-sm whitespace-pre-line xl:text-base">{p.description}</p>
      ) : null}
      <p className="text-muted hidden text-sm tabular-nums xl:block">
        {[
          p.soldCount === undefined ? null : `${p.soldCount} sold`,
          p.stock === undefined ? null : `${p.stock} in stock`,
          category ? `Category · ${category}` : null,
        ]
          .filter((s) => s !== null)
          .join(" · ")}
      </p>
    </section>
  );
}

// Artboard `loading`: gallery block (280, 420 + thumbs at 1280), title bars, price bar, two card
// blocks; the bottom bar is withheld until data arrives.
function DetailLoading() {
  return (
    <main className={shell} aria-busy="true">
      <Skeleton className="h-5 w-40 rounded-full" />
      <div className="flex flex-col gap-4 xl:grid xl:grid-cols-[620px_minmax(0,1fr)] xl:gap-8">
        <div className="flex flex-col gap-3">
          <Skeleton className="-mx-4 h-[280px] rounded-none xl:mx-0 xl:h-[420px] xl:rounded-lg" />
          <div className="hidden gap-3 xl:flex">
            {Array.from({ length: 5 }, (_, i) => (
              <Skeleton key={i} className="h-[84px] w-[112px] rounded-lg" />
            ))}
          </div>
        </div>
        <div className="flex flex-col gap-4">
          <Skeleton className="h-5 w-[84%] rounded-full" />
          <Skeleton className="h-4 w-[56%] rounded-full" />
          <Skeleton className="h-8 w-[46%] rounded-full" />
          <Skeleton className="h-px w-full" />
          <Skeleton className="h-[92px] rounded-lg" />
          <Skeleton className="h-[92px] rounded-lg" />
        </div>
      </div>
    </main>
  );
}

// Artboard `error`: the copy says explicitly that nothing was charged.
function DetailError({ reset }: { reset: () => void }) {
  const router = useRouter();
  return (
    <main className={shell}>
      <Alert status="danger" className="xl:px-6 xl:py-5">
        <Alert.Content>
          <Alert.Title className="text-base xl:text-lg">We couldn’t load this listing</Alert.Title>
          <Alert.Description>
            Something went wrong on our side. Nothing has been charged and no bloc was joined.
          </Alert.Description>
          <div className="mt-3 flex gap-2">
            <Button
              variant="outline"
              className="min-h-11 px-5"
              onPress={() => {
                reset();
                void router.invalidate();
              }}
            >
              Try again
            </Button>
            <Link
              to="/"
              className={
                buttonVariants({ variant: "ghost" }) + " hidden min-h-11 px-5 xl:inline-flex"
              }
            >
              Back to browse
            </Link>
          </div>
        </Alert.Content>
      </Alert>
    </main>
  );
}
