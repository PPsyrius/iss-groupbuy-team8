import { Button } from "@heroui/react";
import { useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { useState } from "react";

import { ApiError } from "@/api/envelope";
import { adminListingOptions } from "@/features/admin/admin-api";
import { EditorSidebar, SidebarActions } from "@/features/admin/EditorSidebar";
import { actionsFor } from "@/features/admin/lifecycle";
import { type DialogKind, LifecycleDialog } from "@/features/admin/LifecycleDialogs";
import { fromListing } from "@/features/admin/listing-form";
import { statusLabel } from "@/features/admin/listing-status";
import { ListingForm, type SubmitProblem } from "@/features/admin/ListingForm";
import { problemFor, useSaveListing } from "@/features/admin/use-listing-mutations";

// The editor for an existing listing (admin plan A1-06, artboards 4b.2 and 4b.3). The version is
// the save's precondition, so this read is always fresh (§12 note 5): the loader asks with
// `staleTime: 0` instead of the usual `'static'`, and a stale cached version cannot guarantee a
// 412. The form is keyed by the version, so a save (or A1-08's Reload) remounts it from server
// data and leaves it clean.
export const Route = createFileRoute("/_authenticated/admin/listings/$productId")({
  loader: async ({ context, params }) => {
    try {
      await context.queryClient.query({ ...adminListingOptions(params.productId), staleTime: 0 });
    } catch (error) {
      if (error instanceof ApiError && (error.status === 404 || /_404_/.test(error.code)))
        throw notFound();
      throw error;
    }
  },
  notFoundComponent: ListingNotFound,
  component: EditListingPage,
});

const FORM_ID = "edit-listing";

function EditListingPage() {
  const { productId } = Route.useParams();
  const { data: listing } = useSuspenseQuery(adminListingOptions(productId));
  const version = listing.version ?? 0;
  const published = listing.status !== "draft";
  const { save, isPending } = useSaveListing(productId, version);
  const [dirty, setDirty] = useState(false);
  const [dialog, setDialog] = useState<DialogKind | null>(null);
  const actions = actionsFor(listing.status, {
    dirty,
    stock: listing.listing?.stock ?? 0,
    price: listing.listing?.originalPrice ?? "",
  });
  const publish = actions.find((a) => a.kind === "publish");
  const onSubmit = async (
    values: ReturnType<typeof fromListing>,
  ): Promise<SubmitProblem | undefined> => {
    try {
      await save(values);
      return undefined;
    } catch (error) {
      return problemFor(error, "Your changes weren’t saved");
    }
  };
  const facts = [
    listing.productNo,
    statusLabel(listing.status).toLowerCase(),
    `v${version}`,
    ...(published ? [`${listing.soldCount ?? 0} sold`] : []),
  ];
  return (
    <div className="flex flex-col gap-6">
      <nav aria-label="Breadcrumb" className="text-muted flex items-center gap-2">
        <Link to="/admin/listings" className="hover:text-foreground">
          Listings
        </Link>
        <span aria-hidden="true">/</span>
        <span aria-current="page" className="text-foreground truncate">
          {listing.listing?.title}
        </span>
      </nav>
      <div className="flex items-end justify-between gap-6">
        <div className="flex min-w-0 flex-col gap-1">
          <h1 className="truncate text-2xl font-semibold tracking-tight">
            {listing.listing?.title}
          </h1>
          <p className="text-muted font-mono text-xs">{facts.join(" · ")}</p>
        </div>
        <div className="flex shrink-0 flex-col items-end gap-1.5">
          <div className="flex gap-2">
            <Button
              type="submit"
              form={FORM_ID}
              variant={publish ? "tertiary" : "secondary"}
              className="min-h-11 px-[18px] text-sm"
              isDisabled={isPending}
            >
              {published ? "Save changes" : "Save draft"}
            </Button>
            {publish ? (
              <Button
                variant="secondary"
                className="min-h-11 px-[18px] text-sm"
                isDisabled={!publish.enabled || isPending}
                onPress={() => setDialog("publish")}
              >
                Publish
              </Button>
            ) : null}
          </div>
          {publish && !publish.enabled ? (
            <p className="text-muted text-xs">
              {dirty
                ? "Save your changes before publishing."
                : "Publishing needs a positive price and positive stock. Fill those in and save first."}
            </p>
          ) : null}
        </div>
      </div>
      <LifecycleDialog kind={dialog} listing={listing} onClose={() => setDialog(null)} />
      <div className="grid grid-cols-[minmax(0,1fr)_320px] items-start gap-6">
        <ListingForm
          key={version}
          id={FORM_ID}
          mode="edit"
          defaultValues={fromListing(listing)}
          onSubmit={onSubmit}
          frozen={published}
          soldCount={published ? (listing.soldCount ?? 0) : undefined}
          guardUnsaved
          onDirtyChange={setDirty}
        />
        <aside className="flex flex-col gap-4">
          <EditorSidebar status={listing.status} version={version} showLifecycle>
            <SidebarActions actions={actions} onOpen={setDialog} />
          </EditorSidebar>
        </aside>
      </div>
    </div>
  );
}

// An unknown or deleted listing, inside the console (the rail stays) rather than the storefront's
// 404 card, with the way back to the table.
function ListingNotFound() {
  return (
    <section className="border-border bg-surface mx-auto mt-12 flex max-w-md flex-col items-start gap-3 rounded-lg border p-6">
      <h1 className="text-lg font-semibold">This listing no longer exists</h1>
      <p className="text-muted">
        It may have been deleted, or the link is wrong. Deleted drafts cannot be recovered.
      </p>
      <Link to="/admin/listings" className="font-medium underline underline-offset-4">
        Listings
      </Link>
    </section>
  );
}
