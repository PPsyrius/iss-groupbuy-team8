import { Link } from "@tanstack/react-router";
import { useId } from "react";

import type { AdminListingResponse } from "@/api/generated/types.gen";

import { ListingsTable, ListingsTableSkeleton } from "./ListingsTable";

// 4a.1's Recent listings: the newest five (the admin page is newest first), in the listings
// table's columns less SOLD and the row menu, with "View all" into the full table.
export function RecentListings({ items }: Readonly<{ items: AdminListingResponse[] | undefined }>) {
  const heading = useId();
  return (
    <section
      aria-labelledby={heading}
      className="border-border bg-surface overflow-hidden rounded-lg border"
    >
      <div className="border-border flex items-baseline justify-between border-b px-5 py-4">
        <h2 id={heading} className="text-base font-semibold">
          Recent listings
        </h2>
        <Link to="/admin/listings" className="text-muted hover:underline">
          View all
        </Link>
      </div>
      {items === undefined ? (
        <ListingsTableSkeleton rows={5} variant="recent" />
      ) : (
        <ListingsTable items={items} variant="recent" />
      )}
    </section>
  );
}
