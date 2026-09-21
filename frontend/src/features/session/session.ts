import { type QueryClient, queryOptions } from "@tanstack/react-query";

import { ApiError, unwrapResult } from "@/api/envelope";
import { meOptions } from "@/api/generated/@tanstack/react-query.gen";
import type { MeResponse } from "@/api/generated/types.gen";

// The one source of auth truth (AGENTS.md rule 6, ADR 0002): `me` through the generated client.
// B4 (#97): `nickname` and `avatarUrl` are nullable for accounts registered before the Google
// profile was wired, so callers fall back to the stable `userId`.
export type SessionState =
  | { status: "anonymous" }
  | { status: "authenticated"; userId: string; nickname: string | null; avatarUrl: string | null };

export const sessionOptions = () =>
  queryOptions({
    ...meOptions(),
    select: (r) => unwrapResult(r),
    retry: false,
    staleTime: 60_000,
  });

export function toSessionState(me: MeResponse | undefined): SessionState {
  if (!me?.userId) return { status: "anonymous" };
  return {
    status: "authenticated",
    userId: me.userId,
    nickname: me.nickname ?? null,
    avatarUrl: me.avatarUrl ?? null,
  };
}

// Route `beforeLoad` entry point: resolves the session from the cache (fresh for a minute) or the
// network. A 401 is the normal anonymous answer (the interceptor in api/client.ts settles the
// query on an anonymous envelope and throws); anything else is a real failure and propagates.
export async function sessionFromQuery(queryClient: QueryClient): Promise<SessionState> {
  try {
    return toSessionState(await queryClient.query(sessionOptions()));
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return { status: "anonymous" };
    throw error;
  }
}
