import { Alert, Button, buttonVariants } from "@heroui/react";
import { useSuspenseQuery } from "@tanstack/react-query";
import {
  createFileRoute,
  Link,
  redirect,
  stripSearchParams,
  useNavigate,
  useRouter,
} from "@tanstack/react-router";

import type { AdminListingResponse } from "@/api/generated/types.gen";
import { adminListingsOptions } from "@/features/admin/admin-api";
import {
  type ListingsSearch,
  type ListingTab,
  listingsSearchDefaults,
  listingsSearchSchema,
  tabToStatus,
} from "@/features/admin/listings-search";
import { ListingsTable, ListingsTableSkeleton } from "@/features/admin/ListingsTable";
import { StatusTabs } from "@/features/admin/StatusTabs";
import { useStatusCounts } from "@/features/admin/use-status-counts";

const listOptions = ({ tab, page, size }: ListingsSearch) => {
  const status = tabToStatus(tab);
  return adminListingsOptions({ page, size, ...(status === undefined ? {} : { status }) });
};

// The listings table (admin plan A1-04, artboards 4a.2–4a.4). Status tabs plus server paging are
// the whole navigation model: the contract has no sort and no search (§12 note 4).
export const Route = createFileRoute("/_authenticated/admin/listings/")({
  validateSearch: listingsSearchSchema,
  search: { middlewares: [stripSearchParams(listingsSearchDefaults)] },
  loaderDeps: ({ search }) => search,
  // As on the catalog (buyer plan note 40): a page past the last one is a valid request that the
  // backend answers with no items and the full `total`, so it goes to the last page instead.
  loader: async ({ context, deps }) => {
    const page = await context.queryClient.query({ ...listOptions(deps), staleTime: "static" });
    const lastPage = Math.max(1, Math.ceil((page.total ?? 0) / deps.size));
    if (deps.page > lastPage) {
      throw redirect({
        to: "/admin/listings",
        search: (prev) => ({ ...prev, page: lastPage }),
        replace: true,
      });
    }
    return page;
  },
  pendingComponent: ListingsLoading,
  errorComponent: ListingsError,
  component: ListingsPage,
});

const emptyCopy: Record<ListingTab, { heading: string; body: string }> = {
  // 4a.3, verbatim. The other three are not drawn (admin plan §11) and follow its shape.
  draft: {
    heading: "No drafts",
    body: "Every listing here is published. A new listing starts as a draft at version 0 and stays private until you publish it.",
  },
  all: {
    heading: "No listings yet",
    body: "A new listing starts as a draft at version 0 and stays private until you publish it.",
  },
  "on-sale": {
    heading: "Nothing on sale",
    body: "Publish a draft or put an off-sale listing back on sale to show it on the storefront.",
  },
  "off-sale": {
    heading: "Nothing off sale",
    body: "A listing taken off sale stays here, out of the storefront, until you put it back.",
  },
};

// The header button drops to outline when the empty state carries its own (4a.3), so one primary
// stays on screen.
function NewListing({ variant }: Readonly<{ variant: "secondary" | "outline" }>) {
  return (
    <Link
      to="/admin/listings/new"
      className={buttonVariants({ variant }) + " min-h-11 px-[18px] text-sm"}
    >
      New listing
    </Link>
  );
}

function ListingsHeader({
  subtitle,
  cta,
}: Readonly<{ subtitle?: string; cta?: "secondary" | "outline" }>) {
  return (
    <div className="flex items-end justify-between gap-6">
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-semibold tracking-tight">Listings</h1>
        {subtitle === undefined ? null : <p className="text-muted">{subtitle}</p>}
      </div>
      {cta === undefined ? null : <NewListing variant={cta} />}
    </div>
  );
}

const noRows: AdminListingResponse[] = [];
const pagerButton = buttonVariants({ variant: "outline" }) + " min-h-11 px-4 text-sm";

function ListingsPage() {
  const search = Route.useSearch();
  const navigate = useNavigate({ from: Route.fullPath });
  const { data } = useSuspenseQuery(listOptions(search));
  const counts = useStatusCounts();
  const items = data.items ?? noRows;
  const total = data.total ?? 0;
  const lastPage = Math.max(1, Math.ceil(total / search.size));
  const first = (search.page - 1) * search.size + 1;
  const last = first + items.length - 1;
  const empty = items.length === 0;
  return (
    <div className="flex flex-col gap-5">
      <ListingsHeader
        {...(empty ? {} : { subtitle: `${total} listings · page size ${search.size}` })}
        cta={empty ? "outline" : "secondary"}
      />
      <StatusTabs
        tab={search.tab}
        counts={counts}
        onSelect={(tab) => void navigate({ search: (prev) => ({ ...prev, tab, page: 1 }) })}
      />
      {empty ? (
        <div className="border-border bg-surface flex flex-col items-center gap-3 rounded-lg border px-8 py-16 text-center">
          <h2 className="text-lg font-semibold">{emptyCopy[search.tab].heading}</h2>
          <p className="text-muted max-w-[52ch] text-pretty">{emptyCopy[search.tab].body}</p>
          <div className="mt-2">
            <NewListing variant="secondary" />
          </div>
        </div>
      ) : (
        <div className="flex flex-col">
          <ListingsTable items={items} />
          <nav
            aria-label="Pagination"
            className="flex items-center justify-between gap-4 px-5 py-3 tabular-nums"
          >
            <span className="text-muted">
              Showing {first}–{last} of {total}
            </span>
            <div className="flex items-center gap-3">
              <Link
                from={Route.fullPath}
                search={(prev) => ({ ...prev, page: Math.max(1, search.page - 1) })}
                disabled={search.page <= 1}
                className={pagerButton}
              >
                Previous
              </Link>
              <span className="text-muted">
                Page {search.page} of {lastPage}
              </span>
              <Link
                from={Route.fullPath}
                search={(prev) => ({ ...prev, page: Math.min(lastPage, search.page + 1) })}
                disabled={search.page >= lastPage}
                className={pagerButton}
              >
                Next
              </Link>
            </div>
          </nav>
        </div>
      )}
    </div>
  );
}

// 4a.4: tab labels without counts and eight skeleton rows in the column grid.
function ListingsLoading() {
  return (
    <div className="flex flex-col gap-5" aria-busy="true">
      <ListingsHeader />
      <StatusTabs tab={Route.useSearch().tab} onSelect={() => {}} />
      <ListingsTableSkeleton />
    </div>
  );
}

// Not drawn (admin plan §2.1 S3): the catalog's error Alert in the console's words.
function ListingsError({ reset }: Readonly<{ reset: () => void }>) {
  const router = useRouter();
  return (
    <div className="flex flex-col gap-5">
      <ListingsHeader />
      <Alert status="danger">
        <Alert.Content>
          <Alert.Title>We couldn’t load the listings</Alert.Title>
          <Alert.Description>
            Something went wrong on our side. No listing was changed.
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
    </div>
  );
}
