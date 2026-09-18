import { Alert, Button, buttonVariants, Skeleton } from "@heroui/react";
import { queryOptions, useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute, Link, redirect, useRouter } from "@tanstack/react-router";

import { unwrapResult } from "@/api/envelope";
import { pageListingsOptions } from "@/api/generated/@tanstack/react-query.gen";
import { categoryLabel } from "@/features/catalog/categories";
import { FilterForm } from "@/features/catalog/FilterForm";
import { ProductCard } from "@/features/catalog/ProductCard";
import { productsSearchSchema, type ProductsSearch } from "@/features/catalog/products-search";

// `exactOptionalPropertyTypes`: the generated query type has `category?: string` and
// `keyword?: string`, so an undefined filter is left out rather than passed as `undefined`. The
// `queryOptions()` wrapper is what types `select`'s input from the generated `queryFn` (plan §12
// note 36). `keyword` is live on dev since PR #107 (note 43).
const listOptions = ({ page, size, category, keyword }: ProductsSearch) =>
  queryOptions({
    ...pageListingsOptions({
      query: {
        page,
        size,
        ...(category === undefined ? {} : { category }),
        ...(keyword === undefined ? {} : { keyword }),
      },
    }),
    select: (r) => unwrapResult(r),
  });

export const Route = createFileRoute("/")({
  validateSearch: productsSearchSchema,
  loaderDeps: ({ search }) => search,
  // `queryClient.query` applies `select`, so the loader sees the unwrapped page. A `page` past
  // the last one is a valid request the backend answers with no items and the full `total` (it is
  // an SQL offset), so the route would render "Page 999 of 3" over the empty state; send it to
  // the last page instead, keeping `size` and `category` (CodeRabbit r4042311596, plan note 40).
  loader: async ({ context, deps }) => {
    const page = await context.queryClient.query({ ...listOptions(deps), staleTime: "static" });
    const lastPage = Math.max(1, Math.ceil((page.total ?? 0) / deps.size));
    if (deps.page > lastPage) {
      throw redirect({ to: "/", search: { ...deps, page: lastPage }, replace: true });
    }
    return page;
  },
  pendingComponent: CatalogLoading,
  errorComponent: CatalogError,
  component: ProductsPage,
});

// Layout follows `BlocBuy Buyer Catalog.dc.html` (docs/design-screens.md §4.1): body padding 16
// (32 at 1280) and column gap 16 (24); hero; the category chip row with the keyword search at its
// end (FilterForm); 1 / 2 / 4 columns at 390 / 768 / 1280; pager below.
const shell = "mx-auto flex max-w-[1280px] flex-col gap-4 p-4 xl:gap-6 xl:p-8";
const grid = "grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-4 xl:gap-6";
const pagerButton = buttonVariants({ variant: "outline" }) + " min-h-11 px-4 text-sm xl:px-[18px]";

function ProductsPage() {
  const search = Route.useSearch();
  const { data } = useSuspenseQuery(listOptions(search));
  const items = data.items ?? [];
  const total = data.total ?? 0;
  const lastPage = Math.max(1, Math.ceil(total / search.size));
  const prevSearch = { ...search, page: Math.max(1, search.page - 1) };
  const nextSearch = { ...search, page: Math.min(lastPage, search.page + 1) };
  return (
    <main className={shell}>
      <CatalogHero total={total} />
      <FilterForm search={search} />
      {items.length === 0 ? (
        <CatalogEmpty category={search.category} keyword={search.keyword} />
      ) : (
        <ul className={grid}>
          {items.map((p) => (
            <li key={p.id} className="relative">
              <ProductCard product={p} />
            </li>
          ))}
        </ul>
      )}
      <nav
        aria-label="Pagination"
        className="flex items-center justify-between gap-3 pt-1 pb-2 xl:justify-center xl:gap-4 xl:pt-2"
      >
        <Link
          from={Route.fullPath}
          search={prevSearch}
          disabled={search.page <= 1}
          className={pagerButton}
        >
          Previous
        </Link>
        <span className="text-muted text-sm tabular-nums">
          Page {search.page} of {lastPage}
          <span className="hidden xl:inline"> · {total} listings</span>
        </span>
        <Link
          from={Route.fullPath}
          search={nextSearch}
          disabled={search.page >= lastPage}
          className={pagerButton}
        >
          Next
        </Link>
      </nav>
    </main>
  );
}

function CatalogHero({ total }: { total?: number }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-6">
      <div className="flex max-w-[560px] flex-col gap-1 xl:gap-1.5">
        <h1 className="text-xl font-semibold tracking-tight xl:text-4xl">Buy better together</h1>
        <p className="text-muted text-sm xl:text-base">
          Fill a bloc with your neighbours and the lower price unlocks.
          <span className="hidden xl:inline">
            {" "}
            Pay once, get refunded in full if the bloc doesn’t fill.
          </span>
        </p>
      </div>
      {total === undefined ? null : (
        <span className="text-muted hidden text-sm tabular-nums xl:inline">
          {total} listings on sale
        </span>
      )}
    </div>
  );
}

// Artboard `empty category`: "Nothing in Beauty yet" with a "Browse Groceries" recovery. The
// backend's category values are not confirmed (CR-10), so the recovery goes to the unfiltered
// catalog instead of guessing a value. No artboard covers an empty keyword search (the artboards
// have no search field); that variant keeps the same card and clears the keyword only, so the
// chosen category survives.
function CatalogEmpty({
  category,
  keyword,
}: {
  category: string | undefined;
  keyword: string | undefined;
}) {
  const heading = keyword
    ? `No listings match “${keyword}”`
    : category
      ? `Nothing in ${categoryLabel(category)} yet`
      : "Nothing on sale yet";
  const body = keyword
    ? "Try a different word, or clear the search to see everything on sale."
    : category
      ? "This category has no listings on sale right now. Try another category, where most blocs are filling."
      : "No listings are on sale right now. Check back soon.";
  return (
    <div className="border-border bg-surface flex flex-col items-center gap-3 rounded-lg border px-6 py-8 text-center xl:px-8 xl:py-16">
      <h2 className="text-lg font-semibold xl:text-xl">{heading}</h2>
      <p className="text-muted max-w-[46ch] text-sm text-pretty xl:text-base">{body}</p>
      {keyword ? (
        <Link
          to="/"
          search={{ page: 1, ...(category === undefined ? {} : { category }) }}
          className={buttonVariants({ variant: "secondary" }) + " mt-1 min-h-11 px-5"}
        >
          Clear search
        </Link>
      ) : category ? (
        <Link to="/" className={buttonVariants({ variant: "secondary" }) + " mt-1 min-h-11 px-5"}>
          Browse all listings
        </Link>
      ) : null}
    </div>
  );
}

// Artboard `loading`: three chip skeletons (36×72/96/88 on phone, 40×80/104/96/88 at 1280) and
// skeleton tiles in the card's own geometry so nothing jumps when data lands (3 on phone, 8 at
// 1280). HeroUI 3 ships Skeleton; the kit sheet did not.
const skeletonCount = 8;
const chipSkeletons = ["w-[72px] xl:w-20", "w-24 xl:w-[104px]", "w-[88px] xl:w-24"];

function CatalogLoading() {
  return (
    <main className={shell} aria-busy="true">
      <CatalogHero />
      <div className="flex flex-wrap gap-2 xl:gap-2.5">
        {chipSkeletons.map((w) => (
          <Skeleton key={w} className={`h-9 rounded-full xl:h-10 ${w}`} />
        ))}
      </div>
      <ul className={grid}>
        {Array.from({ length: skeletonCount }, (_, i) => (
          <li
            key={i}
            className={
              "border-border bg-surface overflow-hidden rounded-lg border" +
              (i >= 3 ? " hidden md:block" : "")
            }
          >
            <Skeleton className="h-[150px] rounded-none xl:h-[168px]" />
            <div className="flex flex-col gap-2.5 p-4">
              <Skeleton className="h-4 w-[78%] rounded-full" />
              <Skeleton className="h-3.5 w-[52%] rounded-full" />
              <Skeleton className="h-6 w-[44%] rounded-full" />
              <Skeleton className="h-2.5 w-full rounded-full" />
            </div>
          </li>
        ))}
      </ul>
    </main>
  );
}

// Artboard `error`: Alert variant danger with a retry; the reassurance about existing blocs is
// deliberate. `router.invalidate()` re-runs the loader; `reset` clears the error boundary.
function CatalogError({ reset }: { reset: () => void }) {
  const router = useRouter();
  return (
    <main className={shell}>
      <Alert status="danger" className="xl:px-6 xl:py-5">
        <Alert.Content>
          <Alert.Title className="text-base xl:text-lg">We couldn’t load the listings</Alert.Title>
          <Alert.Description>
            Something went wrong on our side. Your account and any bloc you have joined are
            unaffected.
          </Alert.Description>
          <Button
            variant="outline"
            className="mt-3 min-h-11 self-start px-5"
            onPress={() => {
              reset();
              void router.invalidate();
            }}
          >
            Try again
          </Button>
        </Alert.Content>
      </Alert>
    </main>
  );
}
