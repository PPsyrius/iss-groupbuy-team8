import { Alert, Button, buttonVariants } from "@heroui/react";
import { useQuery, useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute, Link, useRouter } from "@tanstack/react-router";

import type { AdminListingResponse } from "@/api/generated/types.gen";
import { BrandIsologo } from "@/components/BrandMark";
import { adminCountsOptions, adminListingsOptions } from "@/features/admin/admin-api";
import { isAdminDenied } from "@/features/admin/AdminShell";
import { RecentListings } from "@/features/admin/RecentListings";
import { formatSgtDate } from "@/features/admin/sgt";
import { StatCards } from "@/features/admin/StatCards";
import { useMinuteClock } from "@/features/admin/use-minute-clock";

const noRows: AdminListingResponse[] = [];
const recentOptions = () => adminListingsOptions({ page: 1, size: 5 });

// The dashboard (admin plan A2-02, artboard 4a.1): the counts the backend can answer and the five
// newest listings as a way into the work. The loader waits for the listings only; the counts fill
// their cards when they land (#290).
export const Route = createFileRoute("/_authenticated/admin/")({
  loader: ({ context }) => {
    // Started, not awaited: a failed counts read leaves the cards on "—", not the page on error.
    context.queryClient.query(adminCountsOptions()).catch(() => undefined);
    return context.queryClient.query({ ...recentOptions(), staleTime: "static" });
  },
  pendingComponent: DashboardLoading,
  errorComponent: DashboardError,
  component: AdminDashboard,
});

// The empty state carries its own New listing, so the header's drops to outline (as on 4a.3).
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

function DashboardHeader({ cta }: Readonly<{ cta?: "secondary" | "outline" }>) {
  const now = useMinuteClock();
  return (
    <div className="flex items-end justify-between gap-6">
      <title>Dashboard · BlocBuy</title>
      <div className="flex flex-col gap-1">
        <h1 className="text-2xl font-semibold tracking-tight">Dashboard</h1>
        <p className="text-muted">{formatSgtDate(now)} · all times SGT</p>
      </div>
      {cta === undefined ? null : <NewListing variant={cta} />}
    </div>
  );
}

function AdminDashboard() {
  const { data, error } = useSuspenseQuery(recentOptions());
  const counts = useQuery(adminCountsOptions());
  // A refetch keeps the cached data and does not throw, so a role revoked after the dashboard
  // loaded is rethrown here to reach Access denied; other counts failures stay on the cards.
  if (isAdminDenied(error)) throw error;
  if (isAdminDenied(counts.error)) throw counts.error;
  const items = data.items ?? noRows;
  const empty = items.length === 0;
  return (
    <div className="flex flex-col gap-6">
      <DashboardHeader cta={empty ? "outline" : "secondary"} />
      <StatCards counts={counts.data} failed={counts.isError} />
      {empty ? (
        // Not drawn (admin plan §11 D4): the listings table's All empty state.
        <div className="border-border bg-surface flex flex-col items-center gap-3 rounded-lg border px-8 py-16 text-center">
          {/* The brand mark is the only decoration allowed on an empty state (CR-7). */}
          <BrandIsologo alt="" className="h-10 w-auto opacity-80" />
          <h2 className="text-lg font-semibold">No listings yet</h2>
          <p className="text-muted max-w-[52ch] text-pretty">
            A new listing starts as a draft at version 0 and stays private until you publish it.
          </p>
          <div className="mt-2">
            <NewListing variant="secondary" />
          </div>
        </div>
      ) : (
        <RecentListings items={items} />
      )}
    </div>
  );
}

// Not drawn (§11 D4): the header, withheld counts and five skeleton rows.
function DashboardLoading() {
  return (
    <div className="flex flex-col gap-6" aria-busy="true">
      <DashboardHeader />
      <StatCards counts={undefined} />
      <RecentListings items={undefined} />
    </div>
  );
}

// Not drawn: the listings table's error Alert in the dashboard's words. A 403 (a role revoked
// mid-session) goes up to the console's Access denied screen.
function DashboardError({ error, reset }: Readonly<{ error: unknown; reset: () => void }>) {
  const router = useRouter();
  if (isAdminDenied(error)) throw error;
  return (
    <div className="flex flex-col gap-6">
      <DashboardHeader />
      <Alert status="danger">
        <Alert.Content>
          <Alert.Title>We couldn’t load the dashboard</Alert.Title>
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
