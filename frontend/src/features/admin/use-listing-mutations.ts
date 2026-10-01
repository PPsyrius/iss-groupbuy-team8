import { type QueryClient, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";

import { OK_CODE } from "@/api/envelope";
import type { AdminListingResponse } from "@/api/generated/types.gen";

import {
  adminListingOptions,
  forgetListingPages,
  useChangeStatus,
  useCreateListing,
  useDeleteListing,
  useReviseListing,
} from "./admin-api";
import type { Refusal } from "./listing-errors";
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
// colleague who saved first wins with a 412, which the editor turns into the conflict banner. Frozen
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

const targetStatus = {
  publish: "on_sale",
  takeOffSale: "off_sale",
  putBackOnSale: "on_sale",
} as const;

export type LifecycleChange = keyof typeof targetStatus | "delete";

// The lifecycle writes (admin plan A1-07): a status change or a draft's delete, each carrying the
// loaded version. A status change answers with the listing at its new version, which becomes the
// `get` cache like a save's. A delete answers with nothing; only the table pages are dropped, and
// the caller leaves the editor (the listing's own entry would 404 on its next read anyway).
export function useLifecycleChange(productId: string, expectedVersion: number) {
  const qc = useQueryClient();
  const change = useChangeStatus();
  const remove = useDeleteListing();
  return {
    isPending: change.isPending || remove.isPending,
    run: async (kind: LifecycleChange) => {
      if (kind === "delete") {
        await remove.mutateAsync({ productId, expectedVersion });
        forgetListingPages(qc);
        return;
      }
      remember(
        qc,
        await change.mutateAsync({ productId, status: targetStatus[kind], expectedVersion }),
      );
    },
  };
}

// Where a refused write goes on the form (admin plan A1-08): field messages onto their fields, an
// ended session and everything else into the form's alert. Raw codes never reach the screen. The
// conflict and a revoked role are the page's to show (banner, route boundary), so they place
// nothing here.
export function problemFor(refusal: Refusal, notSaved: string): SubmitProblem {
  switch (refusal.kind) {
    case "conflict":
    case "forbidden":
      return {};
    case "session":
      return {
        alert: { title: notSaved, description: "Your session ended. Sign in again to save." },
        signIn: true,
      };
    case "fields":
      return { fields: refusal.fields };
    case "message":
      return { alert: { title: notSaved, description: refusal.message } };
    default:
      return {};
  }
}

// A 403 on a write means the admin role went away mid-session (§12 note 2). Thrown from render,
// it reaches the route boundary, which hands it up to the console's Access denied screen.
export function useRouteBoundary() {
  const [error, setError] = useState<{ value: unknown }>();
  if (error) throw error.value;
  return (value: unknown) => setError({ value });
}
