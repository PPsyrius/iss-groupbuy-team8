import { Alert, Button, buttonVariants, Skeleton } from "@heroui/react";
import { queryOptions, useQuery, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute, Link, notFound, redirect, useRouter } from "@tanstack/react-router";
import { type ReactNode, useState } from "react";

import { ApiError, unwrapResult } from "@/api/envelope";
import { getListingOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { ProductDetailResponse } from "@/api/generated/types.gen";
import { NotFoundCard } from "@/components/ErrorCard";
import { blocFilter } from "@/features/blocs/bloc-status";
import { myTeamsQuery } from "@/features/blocs/my-teams-query";
import { DealProgress, useNow } from "@/features/catalog/DealProgress";
import {
  blocHeadline,
  chooseJoin,
  chooseStart,
  inviteView,
  type ListingDealView,
  listingDealView,
} from "@/features/catalog/listing-view";
import { formatPrice, Price } from "@/features/catalog/Price";
import { ProductGallery } from "@/features/catalog/ProductGallery";
import { listingSearchSchema } from "@/features/catalog/products-search";
import { TrustTriptych } from "@/features/catalog/TrustTriptych";
import { QuoteSheet } from "@/features/payment/QuoteSheet";
import { useCheckout } from "@/features/payment/use-checkout";
import { useQuote } from "@/features/payment/use-quote";
import { GatedActionSheet } from "@/features/session/GatedActionSheet";
import { useSession } from "@/features/session/use-session";
import { discardInvite, parkInvite, readParkedInvite } from "@/features/share/invite-ref";
import { awaitSharer, useInviteSharer } from "@/features/share/invite-sharer";
import { InviteBanner } from "@/features/share/InviteBanner";
import { ShareBlock } from "@/features/share/ShareBlock";

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
  // UC7 invite link (T3-05, #30): `?deal=…&ref=…` is parked in sessionStorage and dropped from
  // the URL before anything renders, so it survives the sign-in hop and the quote sheet without
  // riding along on every later link, and a copied address bar never re-shares someone's ref.
  // A pair that fails the contract's patterns is dropped without being parked, and it empties the
  // slot too (the newest link wins, D9), so an older sharer is never credited for it. The schema
  // marks a supplied but invalid value `null`, an absent one `undefined`.
  validateSearch: listingSearchSchema,
  beforeLoad: ({ search, params }) => {
    if (search.deal === undefined && search.ref === undefined) return;
    if (typeof search.deal === "string" && typeof search.ref === "string") {
      parkInvite({ productId: params.productId, dealId: search.deal, ref: search.ref });
    } else {
      discardInvite();
    }
    throw redirect({ to: "/products/$productId", params, search: {}, replace: true });
  },
  loader: async ({ context, params }) => {
    // The invite this buyer arrived with, if it was for this listing (drives the banner). Its
    // sharer is resolved beside the listing so the banner's lead does not flip once the page is
    // up; a failed or slow resolve (past `SHARER_DEADLINE_MS`) leaves the generic lead (FB-19).
    const parked = readParkedInvite();
    const invite = parked?.productId === params.productId ? parked : null;
    const sharer = invite ? awaitSharer(context.queryClient, invite.ref) : undefined;
    try {
      await context.queryClient.query({
        ...detailOptions(params.productId),
        staleTime: "static",
      });
    } catch (error) {
      if (isListingNotFound(error)) throw notFound();
      throw error;
    }
    await sharer;
    return invite;
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
  const invite = Route.useLoaderData();
  const { data: p } = useSuspenseQuery(detailOptions(productId));
  const now = useNow();
  const view = listingDealView(p.deal, now);
  const currency = p.currency ?? "sgd";
  const groupPrice = p.groupPrice ?? undefined;
  const category = p.category ? titleCase(p.category) : undefined;
  // Keep the existing sign-in gate; the server derives buyer identity and price.
  const session = useSession();
  const [gateOpen, setGateOpen] = useState(false);
  // Which action opened the gate: the trust triptych's bloc price and refund promise only hold for
  // Start and Join, so the Buy alone gate leaves it out (FB-11). Kept apart from `gateOpen` so the
  // sheet's content does not change while it animates out.
  const [gateForBloc, setGateForBloc] = useState(true);
  const anonymous = session.status !== "authenticated";
  const openGate = (forBloc: boolean) => {
    setGateForBloc(forBloc);
    setGateOpen(true);
  };
  const onGated = anonymous ? () => openGate(true) : undefined;
  const onGatedAlone = anonymous ? () => openGate(false) : undefined;
  const buyerId = session.status === "authenticated" ? session.userId : undefined;
  // A refused JOIN (full, closed or gone) refetches the listing so the bloc row shows why.
  const queryClient = useQueryClient();
  const checkout = useCheckout(buyerId, () => {
    void queryClient.invalidateQueries({ queryKey: detailOptions(productId).queryKey });
  });
  const start = useStartFlow(buyerId, productId, checkout);
  const onStart = chooseStart({ onGated, enabled: checkout.enabled, openQuote: start.open });
  const { myTeam, membershipPending } = useMyOpenBloc(buyerId, view.dealId);
  const invited = inviteView({
    view,
    signedIn: buyerId !== undefined,
    member: myTeam !== undefined,
    inviteDealId: invite?.dealId,
    hasInvite: invite !== null,
  });
  const sharer = useInviteSharer(invite);
  const onJoin = chooseJoin({
    onGated,
    enabled: checkout.enabled,
    membershipPending,
    member: myTeam !== undefined,
    view,
    join: (dealId) => {
      void checkout.begin("JOIN", dealId);
    },
  });
  const joinPress = checkout.isPending ? undefined : onJoin;
  const blocTrust =
    groupPrice === undefined || view.closesMs === null
      ? undefined
      : { payNow: formatPrice(groupPrice, currency), closesMs: view.closesMs };
  return (
    <main className={shell}>
      <GatedActionSheet
        isOpen={gateOpen}
        onOpenChange={setGateOpen}
        returnTo={`/products/${productId}`}
        trust={gateForBloc ? blocTrust : undefined}
      />
      <QuoteSheet
        isOpen={start.isOpen}
        onOpenChange={start.setOpen}
        state={start.quoting.state}
        now={now}
        isPaying={checkout.isPending}
        isRejected={start.isRejected}
        onContinue={(quoteId) => {
          void start.continueWithQuote(quoteId);
        }}
        onRefresh={() => {
          void start.quoting.refresh();
        }}
        onRetry={() => {
          void start.quoting.load();
        }}
      />
      <CrumbRow category={category} title={p.title} />
      {invited.showBanner ? (
        <InviteBanner
          open={invited.invitedOpen}
          sharer={sharer}
          hasOtherBloc={invited.listingOpen}
          signedIn={buyerId !== undefined}
          join={{
            label: joinLabel(groupPrice, currency),
            onPress: joinPress,
            isPending: checkout.isPending,
          }}
        />
      ) : null}
      {checkout.error ? (
        <p role="alert" className="text-danger">
          {checkout.error}
        </p>
      ) : null}
      <div className="flex flex-col gap-4 xl:grid xl:grid-cols-[620px_minmax(0,1fr)] xl:gap-8">
        <div className="flex min-w-0 flex-col gap-4 xl:gap-6">
          <ProductGallery key={productId} images={galleryImages(p)} />
          <AboutCard product={p} category={category} className="hidden xl:flex" />
        </div>
        <div className="flex flex-col gap-4 xl:gap-6">
          <SummaryCard product={p} currency={currency} closesMs={view.closesMs}>
            <Actions
              product={p}
              currency={currency}
              onGatedAlone={onGatedAlone}
              onStart={onStart}
              isPending={checkout.isPending}
              demoted={invited.inviteJoin}
              className="hidden xl:flex"
            />
          </SummaryCard>
          <section className="flex flex-col gap-3">
            {p.deal ? <OpenBlocsHeading view={view} /> : null}
            <DealProgress
              deal={p.deal ?? undefined}
              onJoin={joinPress}
              member={myTeam !== undefined}
            />
          </section>
          {invited.shareable && myTeam && buyerId && view.dealId ? (
            <ShareBlock
              userId={buyerId}
              dealId={view.dealId}
              productId={productId}
              leader={myTeam.role?.toLowerCase() === "leader"}
              title={p.title ?? "this listing"}
              groupPrice={groupPrice}
              individualPrice={p.individualPrice}
              currency={currency}
              seatsLeft={view.seatsLeft}
              closesMs={view.closesMs}
            />
          ) : null}
          <AboutCard product={p} category={category} className="flex xl:hidden" />
        </div>
      </div>
      <Actions
        product={p}
        currency={currency}
        onGatedAlone={onGatedAlone}
        onStart={onStart}
        isPending={checkout.isPending}
        demoted={invited.inviteJoin}
        className="bg-surface border-border sticky bottom-0 -mx-4 flex border-t px-4 py-3 xl:hidden"
      />
    </main>
  );
}

function joinLabel(groupPrice: number | undefined, currency: string): string {
  if (groupPrice === undefined) return "Join this bloc";
  return `Join this bloc · ${formatPrice(groupPrice, currency)}`;
}

// START goes through the quote sheet first (T3-01); JOIN has no quote and pays directly.
// The sheet belongs to the buyer and listing it was opened for: the page stays mounted across
// `$productId` changes, so a new listing starts with the sheet closed (CodeRabbit r4102738802).
function useStartFlow(
  buyerId: string | undefined,
  productId: string,
  checkout: ReturnType<typeof useCheckout>,
) {
  const quoting = useQuote(buyerId, productId);
  const sheetFor = `${buyerId ?? ""}/${productId}`;
  const [openFor, setOpenFor] = useState<string | null>(null);
  const setOpen = (open: boolean) => setOpenFor(open ? sheetFor : null);
  // The quote the server refused at START (US26): the sheet stays open on "Get a new price", and
  // a fresh quote has a new id, so it is no longer marked.
  const [rejectedQuote, setRejectedQuote] = useState<string | null>(null);
  const shownQuote = quoting.state.status === "ready" ? quoting.state.quote.id : null;
  // Success leaves for Stripe; a failure closes the sheet so the page-level alert shows, except a
  // refused quote, which keeps the sheet open for a new price.
  async function continueWithQuote(quoteId: string) {
    const outcome = await checkout.begin("START", productId, quoteId);
    if (outcome === "quote-rejected") setRejectedQuote(quoteId);
    else setOpen(false);
  }
  return {
    quoting,
    isOpen: openFor === sheetFor,
    setOpen,
    isRejected: shownQuote !== null && shownQuote === rejectedQuote,
    continueWithQuote,
    open: () => {
      setOpen(true);
      void quoting.load();
    },
  };
}

// UC7 share block: a signed-in buyer who is in this listing's open bloc. Membership comes from
// `GET /group-deals/me` (the My blocs query), re-read on every visit: a buyer who just paid comes
// back here and must see the block at once.
function useMyOpenBloc(buyerId: string | undefined, dealId: string | undefined) {
  const teams = useQuery({ ...myTeamsQuery, staleTime: 0, enabled: buyerId !== undefined });
  const myTeam = dealId
    ? teams.data?.items?.find((t) => t.dealId === dealId && blocFilter(t.status) === "active")
    : undefined;
  return { myTeam, membershipPending: buyerId !== undefined && teams.isPending };
}

function SummaryCard({
  product: p,
  currency,
  closesMs,
  children,
}: Readonly<{
  product: ProductDetailResponse;
  currency: string;
  closesMs: number | null;
  children: ReactNode;
}>) {
  const groupPrice = p.groupPrice ?? undefined;
  return (
    <section className={"flex flex-col gap-3 xl:gap-4 xl:p-6 " + card + " max-xl:border-0"}>
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-semibold tracking-tight xl:text-3xl">{p.title}</h1>
        {p.subtitle ? <p className="text-muted text-base">{p.subtitle}</p> : null}
      </div>
      <PriceRow groupPrice={groupPrice} individualPrice={p.individualPrice} currency={currency} />
      {groupPrice !== undefined && p.deal?.targetSize ? (
        <p className="text-muted text-sm">
          Bloc price when a bloc fills · {p.deal.targetSize} seats per bloc
        </p>
      ) : null}
      <StockLine product={p} />
      {groupPrice !== undefined && closesMs !== null ? (
        <TrustTriptych payNow={formatPrice(groupPrice, currency)} closesMs={closesMs} />
      ) : null}
      {children}
    </section>
  );
}

// The bloc price leads with the individual price struck through; without a bloc price the
// individual price takes the lead size.
function PriceRow({
  groupPrice,
  individualPrice,
  currency,
}: Readonly<{
  groupPrice: number | undefined;
  individualPrice: number | undefined;
  currency: string;
}>) {
  if (groupPrice === undefined) {
    return (
      <div className="flex flex-wrap items-baseline gap-3">
        {individualPrice === undefined ? null : (
          <Price
            amount={individualPrice}
            currency={currency}
            className="text-4xl font-semibold tracking-tight tabular-nums"
          />
        )}
      </div>
    );
  }
  return (
    <div className="flex flex-wrap items-baseline gap-3">
      <Price
        amount={groupPrice}
        currency={currency}
        className="text-accent-soft-foreground text-4xl font-semibold tracking-tight tabular-nums"
      />
      {individualPrice === undefined ? null : (
        <Price
          amount={individualPrice}
          currency={currency}
          className="text-muted text-lg tabular-nums line-through"
        />
      )}
    </div>
  );
}

function OpenBlocsHeading({ view }: Readonly<{ view: ListingDealView }>) {
  return (
    <div className="flex items-baseline justify-between gap-3">
      <h2 className="text-lg font-semibold xl:text-xl">Open blocs</h2>
      <span className="text-muted text-sm">
        {blocHeadline(view) ?? (
          <>
            <span className="xl:hidden">Join one, or start your own</span>
            <span className="hidden xl:inline">1 filling now</span>
          </>
        )}
      </span>
    </div>
  );
}

function CrumbRow({
  category,
  title,
}: Readonly<{
  category: string | undefined;
  title: string | undefined;
}>) {
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

function StockLine({ product: p }: Readonly<{ product: ProductDetailResponse }>) {
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

// Both actions are the artboard's, in its order of prominence (Start a bloc outranks Buy alone).
// Buy alone stays separate and disabled for authenticated buyers until its own flow exists.
// `demoted` (an invite to the open bloc) drops Start a bloc to the `outline` rank, 44px, so the
// invite's Join is the only top-rank button on the page.
function Actions({
  product: p,
  currency,
  onGatedAlone,
  onStart,
  isPending,
  demoted,
  className,
}: Readonly<{
  product: ProductDetailResponse;
  currency: string;
  onGatedAlone: (() => void) | undefined;
  onStart: (() => void) | undefined;
  isPending: boolean;
  demoted: boolean;
  className: string;
}>) {
  const groupPrice = p.groupPrice ?? undefined;
  // `exactOptionalPropertyTypes`: an absent handler is no prop at all, not `onPress={undefined}`.
  const press = onGatedAlone ? { onPress: onGatedAlone } : { isDisabled: true };
  return (
    <div className={"flex-col gap-2 " + className}>
      <Button
        variant={demoted ? "outline" : "secondary"}
        size={demoted ? "md" : "lg"}
        className={demoted ? "min-h-11 w-full text-base" : "min-h-12 w-full text-lg"}
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
}: Readonly<{
  product: ProductDetailResponse;
  category: string | undefined;
  className: string;
}>) {
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
function DetailError({ reset }: Readonly<{ reset: () => void }>) {
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
