import { Chip, Skeleton, Table } from "@heroui/react";

import type { AdminListingResponse } from "@/api/generated/types.gen";
import { categoryLabel } from "@/features/catalog/categories";

import { statusLabel } from "./listing-status";
import { formatSgd } from "./money";

// The 4a.2 table: exactly the admin DTO's columns, 44px rows, numbers right-aligned in tabular
// figures, version on every row (the number a 412 later talks about). HeroUI `Table`, no
// react-table and no sort indicators: the contract cannot sort or search (admin plan §12 note 4).
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

function Head() {
  return (
    <Table.Header>
      {heads.map((h) => (
        <Table.Column
          key={h.label}
          id={h.label}
          isRowHeader={h.label === "TITLE"}
          className={`${mono} ${h.end ? "text-right" : ""}`}
        >
          {h.label}
        </Table.Column>
      ))}
      <Table.Column id="menu" className="w-14">
        <span className="sr-only">Actions</span>
      </Table.Column>
    </Table.Header>
  );
}

export function ListingsTable({ items }: { items: AdminListingResponse[] }) {
  return (
    <Table>
      <Table.ScrollContainer>
        <Table.Content aria-label="Listings" className="text-sm">
          <Head />
          <Table.Body>
            {items.map((item, i) => (
              // The contract types every field optional; a row always has an id in practice.
              <Table.Row key={item.id ?? i} id={item.id ?? i} className="h-11">
                <Table.Cell className={mono}>{item.productNo}</Table.Cell>
                <Table.Cell className="max-w-0 min-w-48">
                  {/* A1-06 adds the editor route; a plain link until the route tree knows it. */}
                  <a
                    href={`/admin/listings/${item.id ?? ""}`}
                    className="block truncate font-medium hover:underline"
                  >
                    {item.listing?.title}
                  </a>
                </Table.Cell>
                <Table.Cell className="text-muted">
                  {item.listing?.category ? categoryLabel(item.listing.category) : "—"}
                </Table.Cell>
                <Table.Cell className={num}>
                  {item.listing?.originalPrice ? formatSgd(item.listing.originalPrice) : "—"}
                </Table.Cell>
                <Table.Cell className={`${num} text-muted`}>{item.listing?.stock}</Table.Cell>
                <Table.Cell className={`${num} text-muted`}>{item.soldCount ?? 0}</Table.Cell>
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
                {/* The row menu (Edit, lifecycle actions, Delete draft) lands with A1-07. */}
                <Table.Cell>{null}</Table.Cell>
              </Table.Row>
            ))}
          </Table.Body>
        </Table.Content>
      </Table.ScrollContainer>
    </Table>
  );
}

// 4a.4: eight skeleton rows in the real column grid, so nothing jumps when the page lands.
export function ListingsTableSkeleton() {
  return (
    <Table aria-busy="true">
      <Table.ScrollContainer>
        <Table.Content aria-label="Listings loading" className="text-sm">
          <Head />
          <Table.Body>
            {Array.from({ length: 8 }, (_row, i) => (
              <Table.Row key={i} id={i} className="h-11">
                {Array.from({ length: 9 }, (_cell, j) => (
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
