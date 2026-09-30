import { type QueryClient, queryOptions, useMutation } from "@tanstack/react-query";

import { unwrapResult } from "@/api/envelope";
import {
  getOptions,
  getPolicyOptions,
  pageOptions,
  pageQueryKey,
} from "@/api/generated/@tanstack/react-query.gen";
import { changeStatus, configure, create, delete_, revise } from "@/api/generated/sdk.gen";
import type {
  CreateListingRequest,
  ListingStatusRequest,
  PolicyRequest,
  UpdateListingRequest,
} from "@/api/generated/types.gen";

// The one file that knows the admin operations' generated names (`page`, `get`, `create`,
// `revise`, `delete`, ...): the backend's operationIds are generic (BA-9), so feature code imports
// these named wrappers instead and a rename after regeneration is fixed here once (admin plan §12
// note 3). Every read unwraps the Result envelope with `select`; every write unwraps on success,
// so an OK envelope with a failure code still rejects with an `ApiError`. Writes are pessimistic:
// no wrapper touches the cache, the caller decides what to write back or invalidate.

export function adminListingsOptions(q: { page: number; size: number; status?: string }) {
  return queryOptions({
    ...pageOptions({ query: q }),
    select: (r) => unwrapResult(r, { allowVoid: false }),
  });
}

// After a write: every page of the table, the tab counts and the guard's probe share the `page`
// key. Pages on screen refetch now; the others are dropped rather than marked stale, because the
// route loaders read with `staleTime: 'static'`, which would serve an invalidated page as is.
export function forgetListingPages(qc: QueryClient) {
  void qc.invalidateQueries({ queryKey: pageQueryKey() });
  qc.removeQueries({ queryKey: pageQueryKey(), type: "inactive" });
}

export function adminListingOptions(productId: string) {
  return queryOptions({
    ...getOptions({ path: { productId } }),
    select: (r) => unwrapResult(r, { allowVoid: false }),
  });
}

export function adminPolicyOptions(productId: string) {
  return queryOptions({
    ...getPolicyOptions({ path: { productId } }),
    select: (r) => unwrapResult(r, { allowVoid: false }),
  });
}

export function useCreateListing() {
  return useMutation({
    mutationFn: async (body: CreateListingRequest) =>
      unwrapResult((await create({ body, throwOnError: true })).data, { allowVoid: false }),
  });
}

export function useReviseListing() {
  return useMutation({
    mutationFn: async ({ productId, ...body }: { productId: string } & UpdateListingRequest) =>
      unwrapResult((await revise({ path: { productId }, body, throwOnError: true })).data, {
        allowVoid: false,
      }),
  });
}

export function useChangeStatus() {
  return useMutation({
    mutationFn: async ({ productId, ...body }: { productId: string } & ListingStatusRequest) =>
      unwrapResult((await changeStatus({ path: { productId }, body, throwOnError: true })).data, {
        allowVoid: false,
      }),
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
        (await delete_({ path: { productId }, query: { expectedVersion }, throwOnError: true }))
          .data,
      ),
  });
}

export function useConfigurePolicy() {
  return useMutation({
    mutationFn: async ({ productId, ...body }: { productId: string } & PolicyRequest) =>
      unwrapResult((await configure({ path: { productId }, body, throwOnError: true })).data, {
        allowVoid: false,
      }),
  });
}
