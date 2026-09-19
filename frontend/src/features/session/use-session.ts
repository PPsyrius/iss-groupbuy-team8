import { useQuery } from "@tanstack/react-query";

import { type SessionState, sessionOptions, toSessionState } from "./session";

// Component-side view of the same `me` query the root route resolves in `beforeLoad`, so by the
// time anything renders the cache is settled and there is no anonymous flash. While a refetch is
// pending or after a 401 the state reads as anonymous; the header never redirects on it.
export function useSession(): SessionState {
  const { data } = useQuery(sessionOptions());
  return toSessionState(data);
}
