import { queryOptions } from "@tanstack/react-query";

import { unwrapResult } from "@/api/envelope";
import { myTeamsOptions } from "@/api/generated/@tanstack/react-query.gen";

// `GET /group-deals/me` has no status filter and no counts, so My blocs fetches the latest 100
// blocs once and the chips filter and count them client-side (plan §12 note 50). The listing
// reads the same cache entry to tell whether the buyer is in the bloc it shows (T3-05).
const MY_BLOCS_FETCH_SIZE = 100;

export const myTeamsQuery = queryOptions({
  ...myTeamsOptions({ query: { page: 1, size: MY_BLOCS_FETCH_SIZE } }),
  select: (r) => unwrapResult(r),
});
