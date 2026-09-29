import { Button, buttonVariants, Chip } from "@heroui/react";
import { useQueryClient } from "@tanstack/react-query";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";

import { ApiError, fieldViolations, OK_CODE } from "@/api/envelope";
import {
  adminListingOptions,
  forgetListingPages,
  useCreateListing,
} from "@/features/admin/admin-api";
import { emptyListingForm, toListingContent } from "@/features/admin/listing-form";
import { ListingForm, type SubmitProblem } from "@/features/admin/ListingForm";

// The new-draft editor (admin plan A1-05, artboard 4b.1): the shared listing form beside a
// sidebar that already teaches the lifecycle (Draft v0, Publish disabled with its precondition,
// the price rule not yet available). The create is a non-idempotent POST, so Save is disabled
// while it is in flight.
export const Route = createFileRoute("/_authenticated/admin/listings/new")({
  component: NewListingPage,
});

const FORM_ID = "new-listing";
const notSaved = "The draft wasn’t saved";

// Where a refused create goes: a taken product number and field violations onto their fields,
// anything else into the form's alert. Raw codes never reach the screen.
function problemFor(error: unknown): SubmitProblem {
  if (error instanceof ApiError) {
    if (error.code === "CATALOG_409_003")
      return { fields: { productNo: "That product number is already in use." } };
    const fields = fieldViolations(error);
    if (Object.keys(fields).length > 0) return { fields };
    if (error.status === 400)
      return {
        alert: {
          title: notSaved,
          description: "The server refused this listing. Check the fields and try again.",
        },
      };
  }
  return {
    alert: { title: notSaved, description: "Something went wrong on our side. Try again." },
  };
}

function NewListingPage() {
  const qc = useQueryClient();
  const navigate = useNavigate();
  const create = useCreateListing();
  const save = async (values: typeof emptyListingForm): Promise<SubmitProblem | undefined> => {
    try {
      const listing = await create.mutateAsync({
        productNo: values.productNo.trim(),
        listing: toListingContent(values),
      });
      if (listing.id) {
        qc.setQueryData(adminListingOptions(listing.id).queryKey, {
          code: OK_CODE,
          message: "ok",
          data: listing,
        });
      }
      forgetListingPages(qc);
      // A1-06 adds the editor route; until then the new draft is shown at the top of the table
      // (newest first).
      await navigate({ to: "/admin/listings", replace: true });
      return undefined;
    } catch (error) {
      return problemFor(error);
    }
  };
  return (
    <div className="flex flex-col gap-6">
      <nav aria-label="Breadcrumb" className="text-muted flex items-center gap-2">
        <Link to="/admin/listings" className="hover:text-foreground">
          Listings
        </Link>
        <span aria-hidden="true">/</span>
        <span aria-current="page" className="text-foreground">
          New listing
        </span>
      </nav>
      <div className="flex items-end justify-between gap-6">
        <div className="flex flex-col gap-1">
          <h1 className="text-2xl font-semibold tracking-tight">New listing</h1>
          <p className="text-muted">
            Saved as a draft at version 0. Nothing is visible on the storefront until you publish.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Link
            to="/admin/listings"
            className={buttonVariants({ variant: "outline" }) + " min-h-11 px-[18px] text-sm"}
          >
            Cancel
          </Link>
          <Button
            type="submit"
            form={FORM_ID}
            variant="secondary"
            className="min-h-11 px-[18px] text-sm"
            isDisabled={create.isPending}
          >
            Save draft
          </Button>
        </div>
      </div>
      <div className="grid grid-cols-[minmax(0,1fr)_320px] items-start gap-6">
        <ListingForm id={FORM_ID} mode="create" defaultValues={emptyListingForm} onSubmit={save} />
        <aside className="flex flex-col gap-4">
          <section
            aria-labelledby="new-listing-status"
            className="border-border bg-surface flex flex-col gap-4 rounded-lg border p-5"
          >
            <div className="flex items-center justify-between">
              <h2 id="new-listing-status" className="font-semibold">
                Status
              </h2>
              <div className="flex items-center gap-2">
                <Chip size="sm" variant="soft">
                  Draft
                </Chip>
                <span className="font-mono text-xs tabular-nums">v0</span>
              </div>
            </div>
            <div className="flex flex-col gap-2">
              <span className="text-muted text-xs font-medium tracking-wide uppercase">
                Available now
              </span>
              <Button variant="outline" className="min-h-11 w-full text-sm" isDisabled>
                Publish
              </Button>
              <p className="text-muted text-sm">
                Publishing needs a positive price and positive stock. Fill those in and save first.
              </p>
            </div>
          </section>
          <section
            aria-labelledby="new-listing-rule"
            className="border-border bg-surface flex flex-col gap-3 rounded-lg border p-5"
          >
            <h2 id="new-listing-rule" className="font-semibold">
              Bloc price rule
            </h2>
            <p className="text-muted text-sm">
              Add a rule after the listing has a price. The bloc price must be strictly below the
              solo price.
            </p>
            <Button variant="outline" className="min-h-11 self-start px-4 text-sm" isDisabled>
              Add a rule
            </Button>
          </section>
        </aside>
      </div>
    </div>
  );
}
