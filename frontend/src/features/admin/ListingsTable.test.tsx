import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { AdminListingResponse } from "@/api/generated/types.gen";
import { renderWithRouter } from "@/test/render";

import { ListingsTable, ListingsTableSkeleton } from "./ListingsTable";
import { StatusTabs } from "./StatusTabs";

const row = (over: Partial<AdminListingResponse>): AdminListingResponse => ({
  id: "0192f0a0-0000-7000-a000-000000000001",
  productNo: "BB-100481",
  listing: {
    title: "Thai jasmine rice 10 kg",
    category: "groceries",
    originalPrice: "89",
    currency: "sgd",
    stock: 38,
  },
  status: "on_sale",
  soldCount: 142,
  version: 4,
  ...over,
});

const rice = [row({})];
const mixed = [
  row({}),
  row({ id: "b", productNo: "BB-2", status: "draft", version: 0 }),
  row({ id: "c", productNo: "BB-3", status: "off_sale", version: 7 }),
];

// 4a.2's first row: the rice rule runs 1–30 Sep SGT (its end, 1 Oct 00:00 SGT, is exclusive).
const ruled = [
  row({
    latestPolicy: {
      amount: "71.00",
      currency: "sgd",
      validFrom: "2026-08-31T16:00:00Z",
      validUntil: "2026-09-30T16:00:00Z",
      version: 3,
    },
  }),
  ...mixed.slice(1),
];

const heads = ["PRODUCT NO.", "TITLE", "CATEGORY", "PRICE", "STOCK", "SOLD", "STATUS", "VER"];

describe("ListingsTable", () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it("has the artboard's nine columns, the last one for the row menu", async () => {
    await renderWithRouter(<ListingsTable items={rice} />);
    const columns = await screen.findAllByRole("columnheader");
    expect(columns.map((c) => c.textContent)).toEqual([...heads, "Actions"]);
  });

  it("prints the row in the console's words, numbers right-aligned", async () => {
    await renderWithRouter(<ListingsTable items={rice} />);
    const row1 = (await screen.findAllByRole("row"))[1];
    const cells = [...(row1?.querySelectorAll('[role="gridcell"], [role="rowheader"]') ?? [])];
    expect(cells.map((c) => c.textContent)).toEqual([
      "BB-100481",
      "Thai jasmine rice 10 kgNo bloc price rule yet",
      "Groceries",
      "S$89.00",
      "38",
      "142",
      "On sale",
      "v4",
      "",
    ]);
    for (const i of [3, 4, 5, 7]) expect(cells[i]).toHaveClass("text-right", "tabular-nums");
  });

  it("links the title to the listing's editor", async () => {
    await renderWithRouter(<ListingsTable items={rice} />);
    expect(await screen.findByRole("link", { name: "Thai jasmine rice 10 kg" })).toHaveAttribute(
      "href",
      "/admin/listings/0192f0a0-0000-7000-a000-000000000001",
    );
  });

  it("prints the latest bloc price rule under the title, as 4a.2 words it", async () => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-09-16T02:00:00Z"));
    await renderWithRouter(<ListingsTable items={ruled} />);
    const rule = await screen.findByText("Bloc price S$71.00 · valid to 30 Sep");
    expect(rule).toHaveClass("text-xs", "text-muted", "truncate");
    expect(rule.closest('[role="rowheader"]')).toHaveTextContent(/^Thai jasmine rice 10 kg/);
    expect(screen.getByText("Draft · not published")).toBeVisible();
    expect(screen.getByText("No bloc price rule yet")).toBeVisible();
  });

  it("marks only On sale with the success chip", async () => {
    await renderWithRouter(<ListingsTable items={mixed} />);
    expect((await screen.findByText("On sale")).closest(".chip")).toHaveClass("chip--success");
    expect(screen.getByText("Draft").closest(".chip")).toHaveClass("chip--default");
    expect(screen.getByText("Off sale").closest(".chip")).toHaveClass("chip--default");
    expect(screen.getByText("v0")).toBeInTheDocument();
  });
});

describe("ListingsTableSkeleton", () => {
  it("draws eight skeleton rows under the real column headers", async () => {
    await renderWithRouter(<ListingsTableSkeleton />);
    expect(await screen.findAllByRole("columnheader")).toHaveLength(9);
    expect(screen.getAllByRole("row")).toHaveLength(9);
  });
});

describe("StatusTabs", () => {
  it("withholds the counts until they are known", async () => {
    await renderWithRouter(<StatusTabs tab="all" onSelect={() => {}} />);
    expect((await screen.findAllByRole("tab")).map((t) => t.textContent)).toEqual([
      "All",
      "Draft",
      "On sale",
      "Off sale",
    ]);
  });
});
