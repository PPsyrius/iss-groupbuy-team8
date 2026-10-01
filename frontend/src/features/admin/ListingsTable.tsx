import { Chip, Skeleton, Table } from "@heroui/react";
import { Link } from "@tanstack/react-router";

import type { AdminListingResponse } from "@/api/generated/types.gen";
import { categoryLabel } from "@/features/catalog/categories";

import { blocRuleLine } from "./bloc-rule";
import { statusLabel } from "./listing-status";
import { formatSgd } from "./money";
import { RowMenu } from "./RowMenu";
import { useMinuteClock } from "./use-minute-clock";

// The 4a.2 table: exactly the admin DTO's columns, with the bloc price rule line under each title
// (#293), 44px rows, numbers right-aligned in tabular figures, version on every row (the number a
// 412 later talks about). HeroUI `Table`, no react-table and no sort indicators: the contract
// cannot sort or search (admin plan §12 note 4). The dashboard's Recent listings (4a.1) is the same
// table, less SOLD, the rule line and the row menu.
type Variant = "full" | "recent";

const heads = [
  { label: "PRODUCT NO.", end: false },
  { label: "TITLE", end: false },
  { label: "CATEGORY", end: false },
  { label: "PRICE", end: true },
  { label: "STOCK", end: true },
  { label: "SOLD", end: true },
  { label: "STATUS", end: false },
  { label: "VER", end: true },
] as const;

const num = "text-right tabular-nums";
const mono = "font-mono text-xs text-muted";

function Head({ variant }: Readonly<{ variant: Variant }>) {
  const full = variant === "full";
  return (
    <Table.Header>
      {heads
        .filter((h) => full || h.label !== "SOLD")
        .map((h) => (
          <Table.Column
            key={h.label}
            id={h.label}
            isRowHeader={h.label === "TITLE"}
            className={`${mono} ${h.end ? "text-right" : ""}`}
          >
            {h.label}
          </Table.Column>
        ))}
      {full ? (
        <Table.Column id="menu" className="w-14">
          <span className="sr-only">Actions</span>
        </Table.Column>
      ) : null}
    </Table.Header>
  );
}

export function ListingsTable({
  items,
  variant = "full",
}: Readonly<{ items: AdminListingResponse[]; variant?: Variant }>) {
  const now = useMinuteClock();
  const full = variant === "full";
  return (
    <Table>
      <Table.ScrollContainer>
        <Table.Content aria-label={full ? "Listings" : "Recent listings"} className="text-sm">
          <Head variant={variant} />
          <Table.Body>
            {items.map((item, i) => (
              // The contract types every field optional; a row always has an id in practice.
              <Table.Row key={item.id ?? i} id={item.id ?? i} className="h-11">
                <Table.Cell className={mono}>{item.productNo}</Table.Cell>
                <Table.Cell className="max-w-0 min-w-48">
                  <Link
                    to="/admin/listings/$productId"
                    params={{ productId: item.id ?? "" }}
                    className="block truncate font-medium hover:underline"
                  >
                    {item.listing?.title}
                  </Link>
                  {full ? (
                    <span className="text-muted block truncate text-xs">
                      {blocRuleLine(item, now)}
                    </span>
                  ) : null}
                </Table.Cell>
                <Table.Cell className="text-muted">
                  {item.listing?.category ? categoryLabel(item.listing.category) : "—"}
                </Table.Cell>
                <Table.Cell className={num}>
                  {item.listing?.originalPrice ? formatSgd(item.listing.originalPrice) : "—"}
                </Table.Cell>
                <Table.Cell className={`${num} text-muted`}>{item.listing?.stock}</Table.Cell>
                {full ? (
                  <Table.Cell className={`${num} text-muted`}>{item.soldCount ?? 0}</Table.Cell>
                ) : null}
                <Table.Cell>
                  <Chip
                    size="sm"
                    variant="soft"
                    color={item.status === "on_sale" ? "success" : "default"}
                  >
                    {statusLabel(item.status)}
                  </Chip>
                </Table.Cell>
                <Table.Cell className={`${num} ${mono}`}>v{item.version}</Table.Cell>
                {full ? (
                  <Table.Cell>
                    <RowMenu listing={item} />
                  </Table.Cell>
                ) : null}
              </Table.Row>
            ))}
          </Table.Body>
        </Table.Content>
      </Table.ScrollContainer>
    </Table>
  );
}

// 4a.4: skeleton rows in the real column grid, so nothing jumps when the page lands.
export function ListingsTableSkeleton({
  rows = 8,
  variant = "full",
}: Readonly<{ rows?: number; variant?: Variant }>) {
  const cells = variant === "full" ? 9 : 7;
  return (
    <Table aria-busy="true">
      <Table.ScrollContainer>
        <Table.Content aria-label="Listings loading" className="text-sm">
          <Head variant={variant} />
          <Table.Body>
            {Array.from({ length: rows }, (_row, i) => (
              <Table.Row key={i} id={i} className="h-11">
                {Array.from({ length: cells }, (_cell, j) => (
                  <Table.Cell key={j}>
                    {j === 8 ? null : <Skeleton className="h-3.5 w-full rounded-full" />}
                  </Table.Cell>
                ))}
              </Table.Row>
            ))}
          </Table.Body>
        </Table.Content>
      </Table.ScrollContainer>
    </Table>
  );
}
