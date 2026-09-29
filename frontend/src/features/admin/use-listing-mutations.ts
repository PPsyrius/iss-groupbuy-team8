import { type QueryClient, useQueryClient } from "@tanstack/react-query";

import { ApiError, fieldViolations, OK_CODE } from "@/api/envelope";
import type { AdminListingResponse } from "@/api/generated/types.gen";

import {
  adminListingOptions,
  forgetListingPages,
  useCreateListing,
  useReviseListing,
} from "./admin-api";
import { type ListingFormValues, toListingContent } from "./listing-form";
import type { SubmitProblem } from "./ListingForm";

// The listing writes behind the new-draft and editor screens (admin plan A1-05, A1-06). Writes are
// pessimistic (§12 note 6): the response, which carries the new version, becomes the `get` cache,
// and the table pages are dropped so their counts and rows follow.
function remember(qc: QueryClient, listing: AdminListingResponse) {
  if (listing.id) {
    qc.setQueryData(adminListingOptions(listing.id).queryKey, {
      code: OK_CODE,
      message: "ok",
      data: listing,
    });
  }
  forgetListingPages(qc);
}

export function useCreateDraft() {
  const qc = useQueryClient();
  const create = useCreateListing();
  return {
    isPending: create.isPending,
    save: async (values: ListingFormValues) => {
      const listing = await create.mutateAsync({
        productNo: values.productNo.trim(),
        listing: toListingContent(values),
      });
      remember(qc, listing);
      return listing;
    },
  };
}

// A revise replaces the whole listing and carries the version the form was loaded at, so a
// colleague who saved first wins with a 412 (A1-08 turns that into the conflict banner). Frozen
// fields are read-only in the form, so they go back exactly as the server sent them.
export function useSaveListing(productId: string, expectedVersion: number) {
  const qc = useQueryClient();
  const revise = useReviseListing();
  return {
    isPending: revise.isPending,
    save: async (values: ListingFormValues) => {
      const listing = await revise.mutateAsync({
        productId,
        expectedVersion,
        listing: toListingContent(values),
      });
      remember(qc, listing);
      return listing;
    },
  };
}

// Where a refused write goes: a taken product number and field violations onto their fields,
// anything else into the form's alert. Raw codes never reach the screen.
export function problemFor(error: unknown, notSaved: string): SubmitProblem {
  if (error instanceof ApiError) {
    if (error.code === "CATALOG_409_003")
      return { fields: { productNo: "That product number is already in use." } };
    const fields = fieldViolations(error);
    if (Object.keys(fields).length > 0) return { fields };
    if (error.status === 400 || error.status === 409)
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
