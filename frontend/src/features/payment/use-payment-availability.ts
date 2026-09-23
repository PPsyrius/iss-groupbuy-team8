import { useQuery } from "@tanstack/react-query";

import { unwrapResult } from "@/api/envelope";
import { getPaymentCapabilitiesOptions } from "@/api/generated/@tanstack/react-query.gen";
import { useSession } from "@/features/session/use-session";

// Missing/older backends and failed capability requests never enable money-related requests.
export function usePaymentAvailability() {
  const session = useSession();
  const options = getPaymentCapabilitiesOptions();
  const query = useQuery({
    ...options,
    queryKey: [
      {
        ...options.queryKey[0],
        tags: [session.status === "authenticated" ? session.userId : "anonymous"],
      },
    ],
    enabled: session.status === "authenticated",
    select: (response) => unwrapResult(response, { allowVoid: false }),
    retry: false,
    staleTime: 0,
  });
  return session.status === "authenticated" && query.isSuccess && query.data.enabled === true;
}
