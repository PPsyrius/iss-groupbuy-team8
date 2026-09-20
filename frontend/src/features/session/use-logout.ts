import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";

import { OK_CODE, unwrapResult } from "@/api/envelope";
import { logoutMutation, meQueryKey } from "@/api/generated/@tanstack/react-query.gen";

// Logout through the generated mutation (`POST /api/v1/auth/logout`, `ResultVoid`). On success
// the `me` query is settled on the same anonymous envelope the 401 interceptor writes
// (AGENTS.md rule 6) rather than removed: removing it would refetch, get a 401 and go through
// the interceptor for the same result one round trip later. Then home. On failure the session
// stays as it was and the caller shows the error; nothing redirects.
export function useLogout() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const base = logoutMutation();
  return useMutation({
    ...base,
    mutationFn: async (vars, context) => {
      const envelope = await base.mutationFn!(vars, context);
      // A 200 whose envelope code is not OK is still a failed logout.
      unwrapResult(envelope);
      return envelope;
    },
    onSuccess: async () => {
      queryClient.setQueryData(meQueryKey(), { code: OK_CODE, message: "anonymous" });
      await navigate({ to: "/" });
    },
  });
}
