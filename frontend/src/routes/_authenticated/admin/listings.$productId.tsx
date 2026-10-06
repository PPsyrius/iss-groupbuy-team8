import { Button } from "@heroui/react";
import { useQuery, useQueryClient, useSuspenseQuery } from "@tanstack/react-query";
import { createFileRoute, Link, notFound } from "@tanstack/react-router";
import { useState } from "react";

import { ApiError } from "@/api/envelope";
import { adminListingOptions, adminServerVersionOptions } from "@/features/admin/admin-api";
import { AdminChildError, isAdminDenied } from "@/features/admin/AdminShell";
import { ConflictBanner, ProblemsCard } from "@/features/admin/ConflictBanner";
import { EditorSidebar, SidebarActions } from "@/features/admin/EditorSidebar";
import { actionsFor, deleteBlockedReason } from "@/features/admin/lifecycle";
import { type DialogKind, LifecycleDialog } from "@/features/admin/LifecycleDialogs";
import { refusalOf } from "@/features/admin/listing-errors";
import { fromListing } from "@/features/admin/listing-form";
import { statusLabel } from "@/features/admin/listing-status";
import { type FieldProblem, ListingForm, type SubmitProblem } from "@/features/admin/ListingForm";
import { PricePolicyPanel } from "@/features/admin/PricePolicyPanel";
import { TeamTermsPanel } from "@/features/admin/TeamTermsPanel";
import {
  problemFor,
  useRouteBoundary,
  useSaveListing,
} from "@/features/admin/use-listing-mutations";

// The editor for an existing listing (admin plan A1-06, artboards 4b.2 and 4b.3). The version is
// the save's precondition, so this read is always fresh (§12 note 5): the loader asks with
// `staleTime: 0` instead of the usual `'static'`, and a stale cached version cannot guarantee a
// 412. The form is keyed by the version, so a save (or the conflict banner's Reload) remounts it
// from server data and leaves it clean.
//
// A 412 on a save, a lifecycle change or a delete raises the conflict banner (A1-08, artboard
// 4b.4) and disables every write until Reload: no merge, no retry (§12 note 7). The banner names
// the server's version from the 412 itself (#286, A3-09); only a 412 without one sends the
// background read.
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
  errorComponent: AdminChildError,
  component: EditListingPage,
});

const FORM_ID = "edit-listing";

function EditListingPage() {
  const { productId } = Route.useParams();
  const { data: listing, error: listingError } = useSuspenseQuery(adminListingOptions(productId));
  const version = listing.version ?? 0;
  const published = listing.status !== "draft";
  const { save, isPending } = useSaveListing(productId, version);
  const [dirty, setDirty] = useState(false);
  // The bloc price rule form's and the group terms form's unsaved edits share the listing form's
  // leave prompt.
  const [ruleDirty, setRuleDirty] = useState(false);
  const [termsDirty, setTermsDirty] = useState(false);
  const [dialog, setDialog] = useState<DialogKind | null>(null);
  const [fieldProblems, setFieldProblems] = useState<FieldProblem[]>([]);
  // `null` while there is no conflict; otherwise the server's version as the 412 named it.
  const [conflict, setConflict] = useState<{ serverVersion: number | null } | null>(null);
  const [reloads, setReloads] = useState(0);
  const [reloading, setReloading] = useState(false);
  const [reloadFailed, setReloadFailed] = useState(false);
  const toBoundary = useRouteBoundary();
  const qc = useQueryClient();
  const { data: readVersion, error: versionError } = useQuery({
    ...adminServerVersionOptions(productId),
    enabled: conflict !== null && conflict.serverVersion === null,
  });
  // A refetch keeps the cached listing and does not throw, so a role revoked after the editor
  // loaded is rethrown here to reach Access denied (as on the listings table and the dashboard).
  if (isAdminDenied(listingError)) throw listingError;
  if (isAdminDenied(versionError)) throw versionError;
  const serverVersion = conflict?.serverVersion ?? readVersion;
  // Reload writes the fresh read into the editor's entry and remounts the form from it; the
  // counter remounts it even if the version did not move. A failed read keeps the banner and
  // says so, so Reload can be tried again; a listing deleted meanwhile shows the not-found card,
  // and a revoked role goes to Access denied, as a refused write does.
  const reload = async () => {
    setReloading(true);
    setReloadFailed(false);
    try {
      // No automatic retry: Reload is the user's own action and they can press it again.
      await qc.fetchQuery({ ...adminListingOptions(productId), staleTime: 0, retry: false });
      qc.removeQueries({ queryKey: adminServerVersionOptions(productId).queryKey });
      setConflict(null);
      setFieldProblems([]);
      setReloads((n) => n + 1);
    } catch (error) {
      if (error instanceof ApiError && (error.status === 404 || /_404_/.test(error.code)))
        toBoundary(notFound());
      else if (refusalOf(error).kind === "forbidden") toBoundary(error);
      else setReloadFailed(true);
    } finally {
      setReloading(false);
    }
  };
  const actions = actionsFor(listing.status, {
    dirty,
    stock: listing.listing?.stock ?? 0,
    price: listing.listing?.originalPrice ?? "",
    deletable: listing.deletable,
  });
  const publish = actions.find((a) => a.kind === "publish");
  const onSubmit = async (
    values: ReturnType<typeof fromListing>,
  ): Promise<SubmitProblem | undefined> => {
    try {
      await save(values);
      return undefined;
    } catch (error) {
      const refusal = refusalOf(error);
      if (refusal.kind === "conflict") setConflict({ serverVersion: refusal.serverVersion });
      if (refusal.kind === "forbidden") toBoundary(error);
      return problemFor(refusal, "Your changes weren’t saved");
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
      <title>{`Edit ${listing.listing?.title ?? "listing"} · BlocBuy`}</title>
      {conflict ? (
        <ConflictBanner
          loadedVersion={version}
          serverVersion={serverVersion}
          onReload={() => void reload()}
          isReloading={reloading}
          reloadFailed={reloadFailed}
        />
      ) : null}
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
              isDisabled={isPending || conflict !== null || fieldProblems.length > 0}
            >
              {published ? "Save changes" : "Save draft"}
            </Button>
            {publish ? (
              <Button
                variant="secondary"
                className="min-h-11 px-[18px] text-sm"
                isDisabled={!publish.enabled || isPending || conflict !== null}
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
      <LifecycleDialog
        kind={dialog}
        listing={listing}
        onClose={() => setDialog(null)}
        onConflict={(named) => setConflict({ serverVersion: named })}
      />
      <div className="grid grid-cols-[minmax(0,1fr)_320px] items-start gap-6">
        <ListingForm
          key={`${version}.${reloads}`}
          id={FORM_ID}
          defaultValues={fromListing(listing)}
          onSubmit={onSubmit}
          frozen={published}
          soldCount={published ? (listing.soldCount ?? 0) : undefined}
          guardUnsaved
          unsavedElsewhere={ruleDirty || termsDirty}
          onDirtyChange={setDirty}
          onProblemsChange={setFieldProblems}
        />
        <aside className="flex flex-col gap-4">
          <EditorSidebar status={listing.status} version={version} showLifecycle>
            <SidebarActions
              actions={actions}
              onOpen={setDialog}
              blockedReason={deleteBlockedReason(listing.status, listing.references)}
              isDisabled={conflict !== null}
            />
          </EditorSidebar>
          {conflict !== null && fieldProblems.length > 0 ? (
            <ProblemsCard fieldProblems={fieldProblems} serverVersion={serverVersion} />
          ) : null}
          <PricePolicyPanel
            productId={productId}
            status={listing.status}
            originalPrice={listing.listing?.originalPrice ?? ""}
            onDirtyChange={setRuleDirty}
          />
          <TeamTermsPanel
            productId={productId}
            version={version}
            terms={listing.teamTerms}
            reloads={reloads}
            isDisabled={conflict !== null}
            listingDirty={dirty}
            onConflict={(named) => setConflict({ serverVersion: named })}
            onDirtyChange={setTermsDirty}
          />
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
      <title>Listing not found · BlocBuy</title>
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
