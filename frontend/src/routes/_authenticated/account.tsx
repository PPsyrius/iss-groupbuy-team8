import { Alert, Avatar, Button, buttonVariants, Skeleton } from "@heroui/react";
import { queryOptions, useSuspenseQuery } from "@tanstack/react-query";
import {
  createFileRoute,
  Link,
  redirect,
  stripSearchParams,
  useRouter,
} from "@tanstack/react-router";

import { unwrapResult } from "@/api/envelope";
import { myOrdersOptions, myTeamsOptions } from "@/api/generated/@tanstack/react-query.gen";
import { accountSearchDefaults, accountSearchSchema } from "@/features/blocs/blocs-search";
import { BlocsSkeleton, MY_BLOCS_FETCH_SIZE, MyBlocs } from "@/features/blocs/MyBlocs";
import { OrderHistory } from "@/features/order/OrderHistory";
import type { OrdersSearch } from "@/features/order/orders-search";
import { initials } from "@/features/session/initials";

const ordersOptions = ({ page, size }: OrdersSearch) =>
  queryOptions({
    ...myOrdersOptions({ query: { page, size } }),
    select: (r) => unwrapResult(r),
  });

// One fetch of the latest 100 blocs; the chips filter it client-side (plan §12 note 50).
const teamsOptions = queryOptions({
  ...myTeamsOptions({ query: { page: 1, size: MY_BLOCS_FETCH_SIZE } }),
  select: (r) => unwrapResult(r),
});

export const Route = createFileRoute("/_authenticated/account")({
  validateSearch: accountSearchSchema,
  search: { middlewares: [stripSearchParams(accountSearchDefaults)] },
  // The bloc chip is not a dependency: switching it filters the loaded list without a reload.
  loaderDeps: ({ search: { page, size } }) => ({ page, size }),
  // As on the catalog: a page past the last one is a valid request the backend answers with no
  // items and the full `total`, so it goes to the last page instead of an empty list.
  loader: async ({ context, deps }) => {
    const [page] = await Promise.all([
      context.queryClient.query({ ...ordersOptions(deps), staleTime: "static" }),
      context.queryClient.query({ ...teamsOptions, staleTime: "static" }),
    ]);
    const lastPage = Math.max(1, Math.ceil((page.total ?? 0) / deps.size));
    if (deps.page > lastPage) {
      throw redirect({
        to: "/account",
        search: (prev) => ({ ...prev, page: lastPage }),
        replace: true,
      });
    }
    return page;
  },
  pendingComponent: AccountLoading,
  errorComponent: AccountError,
  component: AccountPage,
});

// `/account` is the §4.4 My blocs screen (docs/design-screens.md), phone-first: the header, the
// Active / Completed / Refunded chips and bloc rows from GET /group-deals/me (T3-03, #31), then
// the buyer's orders from GET /orders/me (T3-02, #128) under a "My orders" heading (§7 Q25). A
// buyer with neither sees the one §4.4 empty card. The header, the empty card and the error alert
// keep the page's shape in every state, so the signed-in name never disappears behind a failed
// list.
const shell = "mx-auto flex max-w-[1280px] flex-col gap-4 p-4 xl:gap-6 xl:p-8";
const pagerButton = buttonVariants({ variant: "outline" }) + " min-h-11 px-4 text-sm xl:px-[18px]";

function AccountPage() {
  const search = Route.useSearch();
  const { data } = useSuspenseQuery(ordersOptions(search));
  const { data: teams } = useSuspenseQuery(teamsOptions);
  const total = data.total ?? 0;
  const teamsTotal = teams.total ?? 0;
  const lastPage = Math.max(1, Math.ceil(total / search.size));
  if (total === 0 && teamsTotal === 0) {
    return (
      <main className={shell}>
        <AccountHeader />
        <NoBlocs />
      </main>
    );
  }
  return (
    <main className={shell}>
      <AccountHeader />
      <MyBlocs teams={teams.items} total={teamsTotal} selected={search.blocs} />
      {total === 0 ? (
        <NoOrders />
      ) : (
        <section className="flex flex-col gap-3" aria-labelledby="orders-heading">
          <h2 id="orders-heading" className="text-base font-semibold xl:text-lg">
            My orders
          </h2>
          <OrderHistory orders={data.items} />
          {lastPage > 1 ? (
            <nav
              aria-label="Order pages"
              className="flex items-center justify-between gap-3 pt-1 pb-2 xl:max-w-[720px]"
            >
              <Link
                from={Route.fullPath}
                search={{ ...search, page: Math.max(1, search.page - 1) }}
                disabled={search.page <= 1}
                className={pagerButton}
              >
                Previous
              </Link>
              <span className="text-muted text-sm tabular-nums">
                Page {search.page} of {lastPage}
              </span>
              <Link
                from={Route.fullPath}
                search={{ ...search, page: Math.min(lastPage, search.page + 1) }}
                disabled={search.page >= lastPage}
                className={pagerButton}
              >
                Next
              </Link>
            </nav>
          ) : null}
        </section>
      )}
    </main>
  );
}

function AccountHeader() {
  const { session } = Route.useRouteContext();
  if (session.status !== "authenticated") return null; // the layout guard already redirected
  const name = session.nickname ?? session.userId;
  return (
    <div className="flex items-center justify-between gap-3">
      <h1 className="text-lg font-semibold xl:text-2xl">My blocs</h1>
      <div className="flex items-center gap-2">
        <Avatar size="sm" aria-hidden="true">
          {session.avatarUrl ? <Avatar.Image src={session.avatarUrl} alt="" /> : null}
          <Avatar.Fallback>{initials(name)}</Avatar.Fallback>
        </Avatar>
        <span data-testid="account-user" className="text-sm font-medium">
          {name}
        </span>
      </div>
    </div>
  );
}

// §4.4 empty: a buyer with no bloc and no order. The refund promise is repeated because this is
// the buyer deciding whether to risk a first bloc.
function NoBlocs() {
  return (
    <section className="flex flex-col gap-4 pt-2 xl:max-w-[560px]">
      <div className="border-border bg-surface flex flex-col items-center gap-2.5 rounded-lg border px-6 py-8 text-center">
        <h2 className="text-lg font-semibold">You haven’t joined a bloc yet</h2>
        <p className="text-sm text-zinc-600">
          Join one that’s filling, or start your own and share the link with your neighbours. Either
          way you’re refunded in full if it doesn’t fill.
        </p>
      </div>
      <Link
        to="/"
        className={
          buttonVariants({ variant: "secondary", size: "lg" }) +
          " min-h-12 w-full justify-center text-lg font-semibold xl:w-auto xl:self-start"
        }
      >
        See what’s filling
      </Link>
    </section>
  );
}

// The #137 card in the §4.4 empty-state layout, shown when blocs exist but no order does yet: an
// order is born only once a payment is verified, so a buyer landing here from the receipt a moment
// before the webhook arrives has joined but has no order yet.
function NoOrders() {
  return (
    <section className="border-border bg-surface flex flex-col gap-3 rounded-lg border p-5 xl:max-w-[560px] xl:p-8">
      <h2 className="text-lg font-semibold">No orders yet</h2>
      <p className="text-base text-zinc-600">
        Orders appear here once a payment goes through. Every bloc either fills or refunds you in
        full.
      </p>
      <Link
        to="/"
        className={
          buttonVariants({ variant: "secondary", size: "lg" }) +
          " min-h-12 justify-center text-lg font-semibold xl:self-start"
        }
      >
        See what’s filling
      </Link>
    </section>
  );
}

// §4.4 loading: the chip and bloc-row skeletons, then order-row skeletons in the row's own
// geometry (56px thumb, three text lines). The chips are skeletoned because their counts shift
// the layout.
function AccountLoading() {
  return (
    <main className={shell} aria-busy="true">
      <AccountHeader />
      <BlocsSkeleton />
      <ul className="flex flex-col gap-3 xl:max-w-[720px]">
        {[0, 1].map((i) => (
          <li key={i} className="border-border bg-surface flex gap-3 rounded-lg border p-3.5">
            <Skeleton className="size-14 shrink-0 rounded-lg" />
            <div className="flex flex-1 flex-col gap-2">
              <Skeleton className="h-3.5 w-[70%] rounded-full" />
              <Skeleton className="h-5 w-[40%] rounded-full" />
              <Skeleton className="h-3 w-[50%] rounded-full" />
            </div>
          </li>
        ))}
      </ul>
    </main>
  );
}

// Same Alert as the catalog error artboard, for either list failing; `router.invalidate()`
// re-runs the loader, which refetches both.
function AccountError({ reset }: { reset: () => void }) {
  const router = useRouter();
  return (
    <main className={shell}>
      <AccountHeader />
      <Alert status="danger" className="xl:max-w-[720px] xl:px-6 xl:py-5">
        <Alert.Content>
          <Alert.Title className="text-base xl:text-lg">
            We couldn’t load your blocs and orders
          </Alert.Title>
          <Alert.Description>
            Something went wrong on our side. Your payments and any bloc you have joined are
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
