import { Button, buttonVariants } from "@heroui/react";
import { createFileRoute, Link, useNavigate } from "@tanstack/react-router";
import { useState } from "react";

import { AdminChildError } from "@/features/admin/AdminShell";
import { EditorSidebar } from "@/features/admin/EditorSidebar";
import { refusalOf } from "@/features/admin/listing-errors";
import { emptyListingForm } from "@/features/admin/listing-form";
import { type FieldProblem, ListingForm, type SubmitProblem } from "@/features/admin/ListingForm";
import {
  problemFor,
  useCreateDraft,
  useRouteBoundary,
} from "@/features/admin/use-listing-mutations";

// The new-draft editor (admin plan A1-05, artboard 4b.1): the shared listing form beside a
// sidebar that already teaches the lifecycle (Draft v0, Publish disabled with its precondition,
// the price rule not yet available). The create is a non-idempotent POST, so Save is disabled
// while it is in flight. A saved draft opens in the editor (A1-06).
export const Route = createFileRoute("/_authenticated/admin/listings/new")({
  component: NewListingPage,
  errorComponent: AdminChildError,
});

const FORM_ID = "new-listing";

function NewListingPage() {
  const navigate = useNavigate();
  const create = useCreateDraft();
  const toBoundary = useRouteBoundary();
  // Save stays disabled while a field shows an error (A1-08, artboard 4b.4).
  const [fieldProblems, setFieldProblems] = useState<FieldProblem[]>([]);
  const save = async (values: typeof emptyListingForm): Promise<SubmitProblem | undefined> => {
    try {
      const listing = await create.save(values);
      await navigate(
        listing.id
          ? { to: "/admin/listings/$productId", params: { productId: listing.id }, replace: true }
          : { to: "/admin/listings", replace: true },
      );
      return undefined;
    } catch (error) {
      const refusal = refusalOf(error);
      if (refusal.kind === "forbidden") toBoundary(error);
      return problemFor(refusal, "The draft wasn’t saved");
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
            isDisabled={create.isPending || fieldProblems.length > 0}
          >
            Save draft
          </Button>
        </div>
      </div>
      <div className="grid grid-cols-[minmax(0,1fr)_320px] items-start gap-6">
        <ListingForm
          id={FORM_ID}
          mode="create"
          defaultValues={emptyListingForm}
          onSubmit={save}
          onProblemsChange={setFieldProblems}
        />
        <aside className="flex flex-col gap-4">
          <EditorSidebar version={0} showLifecycle={false}>
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
          </EditorSidebar>
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
