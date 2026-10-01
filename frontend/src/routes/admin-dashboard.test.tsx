import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import { queryClient } from "@/api/query-client";
import { createAdminStore } from "@/test/admin-msw";
import { server } from "@/test/msw";
import { renderRoute, serveAdmin } from "@/test/render-route";

// A stat card is its term's group in the description list.
function stat(label: string): HTMLElement {
  const group = screen.getByText(label, { selector: "dt" }).parentElement;
  if (!group) throw new Error(`no stat card for ${label}`);
  return group;
}

const boom = () => HttpResponse.json({ code: "COMMON_500_000", message: "boom" }, { status: 500 });

// The app client retries a 500 twice with backoff; skip the wait, not the behaviour.
async function withoutRetryDelay(run: () => Promise<void>) {
  const defaults = queryClient.getDefaultOptions();
  queryClient.setDefaultOptions({ ...defaults, queries: { ...defaults.queries, retryDelay: 0 } });
  try {
    await run();
  } finally {
    queryClient.setDefaultOptions(defaults);
  }
}

// The dashboard (admin plan A2-02, artboard 4a.1) over the stateful admin store: 62 rows, of
// which 14 are drafts, 34 on sale and 14 off sale, and 26 open blocs.
describe("/admin (dashboard)", () => {
  it("shows the date line, the four counts and the five newest listings", async () => {
    // Only `Date` is faked, so the date line reads a fixed Singapore day.
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-09-16T02:00:00Z"));
    try {
      serveAdmin();
      const urls: string[] = [];
      const listen = ({ request }: { request: Request }) => void urls.push(request.url);
      server.events.on("request:start", listen);
      renderRoute("/admin");
      expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeVisible();
      expect(screen.getByText("Wednesday 16 September 2026 · all times SGT")).toBeVisible();
      expect(screen.getByRole("link", { name: "New listing" })).toHaveAttribute(
        "href",
        "/admin/listings/new",
      );

      expect(await within(stat("Open blocs")).findByText("26")).toBeVisible();
      expect(within(stat("Drafts")).getByText("14")).toBeVisible();
      expect(within(stat("On sale")).getByText("34")).toBeVisible();
      expect(within(stat("Off sale")).getByText("14")).toBeVisible();

      const recent = screen.getByRole("region", { name: "Recent listings" });
      expect(within(recent).getByRole("link", { name: "View all" })).toHaveAttribute(
        "href",
        "/admin/listings",
      );
      // 4a.1's seven columns: the table's, less SOLD and the row menu.
      expect(
        within(recent)
          .getAllByRole("columnheader")
          .map((h) => h.textContent),
      ).toEqual(["PRODUCT NO.", "TITLE", "CATEGORY", "PRICE", "STOCK", "STATUS", "VER"]);
      const rows = within(recent).getAllByRole("row").slice(1);
      expect(rows).toHaveLength(5);
      expect(rows[0]).toHaveTextContent("BB-100481");
      expect(rows[0]).toHaveTextContent("Thai jasmine rice 10 kg");
      expect(rows[0]).toHaveTextContent("v4");
      expect(rows[4]).toHaveTextContent("Kopi powder 500 g");
      expect(within(recent).queryByRole("button", { name: /^Actions for/ })).toBeNull();
      expect(within(recent).getByRole("link", { name: "Thai jasmine rice 10 kg" })).toHaveAttribute(
        "href",
        expect.stringMatching(/^\/admin\/listings\/[^/]+$/),
      );

      server.events.removeListener("request:start", listen);
      // One page of five and one `counts` read (#290); no per-status pages.
      expect(urls.filter((u) => u.includes("/admin/products/counts"))).toHaveLength(1);
      expect(urls.filter((u) => /\/admin\/products\?/.test(u))).toEqual([
        expect.stringContaining("size=5"),
      ]);
    } finally {
      vi.useRealTimers();
    }
  });

  it("invites the first listing when there are none", async () => {
    serveAdmin(createAdminStore([], { openDeals: 0 }));
    renderRoute("/admin");
    expect(await screen.findByRole("heading", { name: "No listings yet" })).toBeVisible();
    expect(screen.queryByRole("region", { name: "Recent listings" })).toBeNull();
    // The empty state carries the one primary New listing; the header's drops to outline.
    const links = screen.getAllByRole("link", { name: "New listing" });
    expect(links).toHaveLength(2);
    for (const link of links) expect(link).toHaveAttribute("href", "/admin/listings/new");
    expect(await within(stat("Drafts")).findByText("0")).toBeVisible();
  });

  it("offers Try again when the recent listings fail to load", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const user = userEvent.setup();
    serveAdmin();
    let fail = true;
    server.use(http.get("*/api/v1/admin/products", () => (fail ? boom() : undefined)));
    await withoutRetryDelay(async () => {
      renderRoute("/admin");
      expect(await screen.findByText("We couldn’t load the dashboard")).toBeVisible();
      fail = false;
      await user.click(screen.getByRole("button", { name: "Try again" }));
      expect(await screen.findByRole("region", { name: "Recent listings" })).toBeVisible();
    });
  });

  it("shows dashes when only the counts fail", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    serveAdmin();
    server.use(http.get("*/api/v1/admin/products/counts", boom));
    await withoutRetryDelay(async () => {
      renderRoute("/admin");
      expect(await screen.findByRole("region", { name: "Recent listings" })).toBeVisible();
      expect(await within(stat("Drafts")).findByText("—")).toBeVisible();
    });
  });
});
