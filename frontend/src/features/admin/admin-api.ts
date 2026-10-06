import { type QueryClient, queryOptions, useMutation } from "@tanstack/react-query";

import { ApiError, unwrapResult } from "@/api/envelope";
import {
  countsOptions,
  countsQueryKey,
  getAdminListingOptions,
  getTeamPricePolicyQueryKey,
  pageAdminListingsOptions,
  pageAdminListingsQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import {
  changeAdminListingStatus,
  configureTeamPricePolicy,
  configureTeamTerms,
  createAdminListing,
  deleteAdminListing,
  getAdminListing,
  getTeamPricePolicy,
  reviseAdminListing,
} from "@/api/generated/sdk.gen";
import type {
  CreateListingRequest,
  ListingStatusRequest,
  PolicyRequest,
  PolicyResponse,
  TeamTermsRequest,
  UpdateListingRequest,
} from "@/api/generated/types.gen";

import { isNoRule } from "./policy";

// Admin data access wraps the resource-specific generated operations (#253, BA-9).
// Every read unwraps the Result envelope with `select`; every write unwraps on success,
// so an OK envelope with a failure code still rejects with an `ApiError`. Writes are pessimistic:
// no wrapper touches the cache, the caller decides what to write back or invalidate.

// A 403 is a definite answer (the admin role is gone, A3-01), so no admin read retries it and the
// Access denied card appears at once; otherwise this is the app default (two retries, none for a
// 401 or 404).
export function adminRetry(failureCount: number, error: unknown) {
  if (error instanceof ApiError && [401, 403, 404].includes(error.status)) return false;
  return failureCount < 2;
}

export function adminListingsOptions(q: { page: number; size: number; status?: string }) {
  return queryOptions({
    ...pageAdminListingsOptions({ query: q }),
    select: (r) => unwrapResult(r, { allowVoid: false }),
    retry: adminRetry,
  });
}

// The per-status totals and the open blocs, one snapshot (#290, BA-3). A 30s staleTime keeps
// moving between tabs from re-asking; writes refresh it through `forgetListingPages`.
export function adminCountsOptions() {
  return queryOptions({
    ...countsOptions(),
    select: (r) => unwrapResult(r, { allowVoid: false }),
    staleTime: 30_000,
    retry: adminRetry,
  });
}

// After a write: every page shares the `pageAdminListings` key, and the counts move too. Pages on
// screen refetch now; the others are dropped rather than marked stale, because the route loaders
// read with `staleTime: 'static'`, which would serve an invalidated page as is.
export function forgetListingPages(qc: QueryClient) {
  void qc.invalidateQueries({ queryKey: pageAdminListingsQueryKey() });
  qc.removeQueries({ queryKey: pageAdminListingsQueryKey(), type: "inactive" });
  void qc.invalidateQueries({ queryKey: countsQueryKey() });
}

export function adminListingOptions(productId: string) {
  return queryOptions({
    ...getAdminListingOptions({ path: { productId } }),
    select: (r) => unwrapResult(r, { allowVoid: false }),
    retry: adminRetry,
  });
}

// The version the server holds now, for the conflict banner when a 412 does not name it (A1-08;
// since #286 the 412 carries `currentVersion`, read by `conflictVersion`, so this is the
// fallback). Its own key, never the editor's `getAdminListing` entry: writing there would remount the form and
// drop the edits the banner warns about (§12 note 7).
export function adminServerVersionOptions(productId: string) {
  return queryOptions({
    queryKey: ["admin", "listing-server-version", productId] as const,
    queryFn: async ({ signal }) =>
      (await getAdminListing({ path: { productId }, signal, throwOnError: true })).data,
    staleTime: 0,
    select: (r) => unwrapResult(r, { allowVoid: false }).version,
    retry: adminRetry,
  });
}

// The listing's latest bloc price rule (A2-01), or `null` when it has none: the read answers 409
// `CATALOG_409_001` for that, not 404, and the panel shows "No rule yet" rather than an error.
async function readPolicy(productId: string, signal: AbortSignal): Promise<PolicyResponse | null> {
  try {
    const { data } = await getTeamPricePolicy({ path: { productId }, signal, throwOnError: true });
    return unwrapResult(data, { allowVoid: false });
  } catch (error) {
    if (isNoRule(error)) return null;
    throw error;
  }
}

export function adminPolicyOptions(productId: string) {
  return queryOptions({
    queryKey: getTeamPricePolicyQueryKey({ path: { productId } }),
    queryFn: ({ signal }) => readPolicy(productId, signal),
    retry: adminRetry,
  });
}

// The rule version the server holds now (0 when there is no rule), for the panel's conflict line
// when a 412 leaves the version out (A2-01 step 6). Its own key, so the panel keeps showing what
// the admin loaded until they press Reload.
export function adminPolicyVersionOptions(productId: string) {
  return queryOptions({
    queryKey: ["admin", "policy-server-version", productId] as const,
    queryFn: async ({ signal }) => (await readPolicy(productId, signal))?.version ?? 0,
    staleTime: 0,
    retry: adminRetry,
  });
}

export function useCreateListing() {
  return useMutation({
    mutationFn: async (body: CreateListingRequest) =>
      unwrapResult((await createAdminListing({ body, throwOnError: true })).data, {
        allowVoid: false,
      }),
  });
}

export function useReviseListing() {
  return useMutation({
    mutationFn: async ({ productId, ...body }: { productId: string } & UpdateListingRequest) =>
      unwrapResult(
        (await reviseAdminListing({ path: { productId }, body, throwOnError: true })).data,
        {
          allowVoid: false,
        },
      ),
  });
}

export function useChangeStatus() {
  return useMutation({
    mutationFn: async ({ productId, ...body }: { productId: string } & ListingStatusRequest) =>
      unwrapResult(
        (await changeAdminListingStatus({ path: { productId }, body, throwOnError: true })).data,
        {
          allowVoid: false,
        },
      ),
  });
}

// The delete answers with an empty OK envelope.
export function useDeleteListing() {
  return useMutation({
    mutationFn: async ({
      productId,
      expectedVersion,
    }: {
      productId: string;
      expectedVersion: number;
    }) =>
      unwrapResult(
        (
          await deleteAdminListing({
            path: { productId },
            query: { expectedVersion },
            throwOnError: true,
          })
        ).data,
      ),
  });
}

export function useConfigurePolicy() {
  return useMutation({
    mutationFn: async ({ productId, ...body }: { productId: string } & PolicyRequest) =>
      unwrapResult(
        (await configureTeamPricePolicy({ path: { productId }, body, throwOnError: true })).data,
        {
          allowVoid: false,
        },
      ),
  });
}

// The group terms write (A3-04, #242). It carries and bumps the listing's version, and answers
// with the new version and terms only, not the whole listing.
export function useConfigureTeamTerms() {
  return useMutation({
    mutationFn: async ({ productId, ...body }: { productId: string } & TeamTermsRequest) =>
      unwrapResult(
        (await configureTeamTerms({ path: { productId }, body, throwOnError: true })).data,
        {
          allowVoid: false,
        },
      ),
  });
}
