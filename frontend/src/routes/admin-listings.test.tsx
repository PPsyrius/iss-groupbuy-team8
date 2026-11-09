import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import { OK_CODE } from "@/api/envelope";
import { queryClient } from "@/api/query-client";
import { adminSeed, createAdminStore } from "@/test/admin-msw";
import { server } from "@/test/msw";
import { renderRoute, serveAdmin } from "@/test/render-route";

// The listings table route (admin plan A1-04, artboards 4a.2–4a.4) over the stateful admin store:
// 62 rows, of which 14 are drafts, 34 on sale and 14 off sale.
describe("/admin/listings", () => {
  it("defaults missing page and status counts to zero", async () => {
    serveAdmin();
    server.use(
      http.get("*/api/v1/admin/products", () => HttpResponse.json({ code: OK_CODE, data: {} })),
      http.get("*/api/v1/admin/products/counts", () =>
        HttpResponse.json({ code: OK_CODE, data: {} }),
      ),
    );
    renderRoute("/admin/listings");
    expect(await screen.findByRole("heading", { name: "No listings yet" })).toBeVisible();
    const tabs = await screen.findByRole("tablist", { name: "Listing status" });
    await waitFor(() =>
      expect(
        within(tabs)
          .getAllByRole("tab")
          .map((t) => t.textContent),
      ).toEqual(["All0", "Draft0", "On sale0", "Off sale0"]),
    );
  });
  it("shows the first page with the tab counts and the pager", async () => {
    serveAdmin();
    const urls: string[] = [];
    const listen = ({ request }: { request: Request }) => void urls.push(request.url);
    server.events.on("request:start", listen);
    renderRoute("/admin/listings");
    expect(await screen.findByRole("heading", { name: "Listings" })).toBeVisible();
    expect(document.title).toBe("Manage listings · BlocBuy");
    expect(screen.getByText("62 listings · page size 20")).toBeVisible();
    expect(await screen.findByText("Showing 1–20 of 62")).toBeVisible();
    expect(screen.getByText("Page 1 of 4")).toBeVisible();
    expect(screen.getAllByRole("row")).toHaveLength(21);
    const tabs = screen.getByRole("tablist", { name: "Listing status" });
    await waitFor(() =>
      expect(
        within(tabs)
          .getAllByRole("tab")
          .map((t) => t.textContent),
      ).toEqual(["All62", "Draft14", "On sale34", "Off sale14"]),
    );
    server.events.removeListener("request:start", listen);
    // One `counts` read (#290) instead of a one-row page per tab.
    expect(urls.filter((u) => u.includes("/admin/products/counts"))).toHaveLength(1);
    expect(urls.filter((u) => u.includes("size=1&") || u.endsWith("size=1"))).toHaveLength(0);
    expect(within(tabs).getByRole("tab", { name: /All/ })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("link", { name: "Listings" })).toHaveAttribute(
      "href",
      "/admin/listings",
    );
  });

  it("prints each row's latest bloc price rule under its title (#293)", async () => {
    // Only `Date` is faked, so the seed's rule windows and the table read the same clock.
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(new Date("2026-09-16T02:00:00Z"));
    try {
      serveAdmin();
      renderRoute("/admin/listings");
      const rice = await screen.findByRole("link", { name: "Thai jasmine rice 10 kg" });
      const riceRow = rice.closest('[role="row"]');
      // The seed's rice rule ends 20 days on (6 Oct, 10.00 am SGT, exclusive).
      expect(riceRow).toHaveTextContent("Bloc price S$71.00 · valid to 6 Oct");
      const durianRow = screen
        .getByRole("link", { name: "D24 durian, per kg" })
        .closest('[role="row"]');
      expect(durianRow).toHaveTextContent("Bloc price S$32.00 · expired 15 Sep");
      const milkRow = screen
        .getByRole("link", { name: "Hokkaido milk, 12 cartons" })
        .closest('[role="row"]');
      expect(milkRow).toHaveTextContent("Draft · not published");
      const hepa = screen
        .getByRole("link", { name: /Replacement HEPA filters/ })
        .closest('[role="row"]');
      expect(hepa).toHaveTextContent("No bloc price rule yet");
    } finally {
      vi.useRealTimers();
    }
  });

  it("filters by the tab in the URL and moves between tabs", async () => {
    const user = userEvent.setup();
    serveAdmin();
    const { router } = renderRoute("/admin/listings?tab=off-sale");
    expect(await screen.findByText("Showing 1–14 of 14")).toBeVisible();
    for (const chip of screen
      .getAllByText(/^(On sale|Draft|Off sale)$/)
      .filter((e) => e.closest(".chip")))
      expect(chip).toHaveTextContent("Off sale");
    await user.click(screen.getByRole("tab", { name: /Draft/ }));
    await waitFor(() => expect(router.state.location.search).toEqual({ tab: "draft" }));
    expect(await screen.findByText("Showing 1–14 of 14")).toBeVisible();
    await user.click(screen.getByRole("tab", { name: /All/ }));
    await waitFor(() => expect(router.state.location.search).toEqual({}));
  });

  it("sends a page past the last one to the last page", async () => {
    serveAdmin();
    const { router } = renderRoute("/admin/listings?page=99");
    await waitFor(() => expect(router.state.location.search).toEqual({ page: 4 }));
    expect(await screen.findByText("Showing 61–62 of 62")).toBeVisible();
    expect(screen.getByText("Page 4 of 4")).toBeVisible();
    expect(screen.getByRole("link", { name: "Next" })).toHaveAttribute("aria-disabled", "true");
  });

  it("teaches the draft rule when the Draft tab is empty", async () => {
    serveAdmin(createAdminStore(adminSeed().filter((r) => r.status !== "draft")));
    renderRoute("/admin/listings?tab=draft");
    expect(await screen.findByRole("heading", { name: "No drafts" })).toBeVisible();
    expect(
      screen.getByText(
        "Every listing here is published. A new listing starts as a draft at version 0 and stays private until you publish it.",
      ),
    ).toBeVisible();
    const buttons = screen.getAllByRole("link", { name: "New listing" });
    expect(buttons).toHaveLength(2);
    expect(buttons[0]).toHaveClass("button--outline");
    expect(buttons[1]).toHaveClass("button--secondary");
    expect(screen.queryByRole("table")).not.toBeInTheDocument();
  });

  it("offers Try again when the listings fail to load", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const user = userEvent.setup();
    serveAdmin();
    let fail = true;
    server.use(
      http.get("*/api/v1/admin/products", ({ request }) =>
        new URL(request.url).searchParams.get("size") === "20" && fail
          ? HttpResponse.json({ code: "COMMON_500_000", message: "boom" }, { status: 500 })
          : undefined,
      ),
    );
    // The app client retries a 500 twice with backoff; skip the wait, not the behaviour.
    const defaults = queryClient.getDefaultOptions();
    queryClient.setDefaultOptions({ ...defaults, queries: { ...defaults.queries, retryDelay: 0 } });
    try {
      renderRoute("/admin/listings");
      expect(await screen.findByText("We couldn’t load the listings")).toBeVisible();
      fail = false;
      await user.click(screen.getByRole("button", { name: "Try again" }));
      expect(await screen.findByText("Showing 1–20 of 62")).toBeVisible();
    } finally {
      queryClient.setDefaultOptions(defaults);
    }
  });

  it.each([
    ["the listings page", "*/api/v1/admin/products"],
    ["the tab counts", "*/api/v1/admin/products/counts"],
  ])("shows Access denied when a refetch of %s answers 403", async (_, path) => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    serveAdmin();
    renderRoute("/admin/listings");
    expect(await screen.findByText("Showing 1–20 of 62")).toBeVisible();
    const tabs = screen.getByRole("tablist", { name: "Listing status" });
    await waitFor(() =>
      expect(within(tabs).getByRole("tab", { name: /All/ })).toHaveTextContent("All62"),
    );
    // The role goes away after the table loaded, with its data still cached.
    server.use(
      http.get(path, () =>
        HttpResponse.json(
          { code: "COMMON_403_001", message: "Permission denied" },
          { status: 403 },
        ),
      ),
    );
    await queryClient.refetchQueries();
    expect(
      await screen.findByRole("heading", { name: "You don’t have access to the admin console" }),
    ).toBeVisible();
  });

  it("offers Edit and the one status change a listing allows from its row menu", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    renderRoute("/admin/listings");
    await user.click(
      await screen.findByRole("button", { name: "Actions for Thai jasmine rice 10 kg" }),
    );
    const menu = await screen.findByRole("menu");
    expect(
      within(menu)
        .getAllByRole("menuitem")
        .map((i) => i.textContent),
    ).toEqual(["Edit", "Take off sale"]);
    await user.click(within(menu).getByRole("menuitem", { name: "Take off sale" }));
    const dialog = await screen.findByRole("alertdialog", { name: "Take this listing off sale?" });
    await user.click(within(dialog).getByRole("button", { name: "Take off sale" }));
    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
    const row = screen.getByRole("row", { name: /Thai jasmine rice 10 kg/ });
    expect(await within(row).findByText("Off sale")).toBeVisible();
    expect(within(row).getByText("v5")).toBeVisible();
    expect(store.rows[0]).toMatchObject({ status: "off_sale", version: 5 });
    const tabs = screen.getByRole("tablist", { name: "Listing status" });
    await waitFor(() =>
      expect(
        within(tabs)
          .getAllByRole("tab")
          .map((t) => t.textContent),
      ).toEqual(["All62", "Draft14", "On sale33", "Off sale15"]),
    );
  });

  it("refreshes a stale row after a 412 from its menu, with no banner to raise", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    renderRoute("/admin/listings");
    await user.click(
      await screen.findByRole("button", { name: "Actions for Thai jasmine rice 10 kg" }),
    );
    const menu = await screen.findByRole("menu");
    await user.click(within(menu).getByRole("menuitem", { name: "Take off sale" }));
    const rice = store.rows[0];
    if (!rice) throw new Error("no seeded rows");
    rice.version = 5;
    const dialog = await screen.findByRole("alertdialog", { name: "Take this listing off sale?" });
    await user.click(within(dialog).getByRole("button", { name: "Take off sale" }));

    expect(
      await within(dialog).findByText(
        "Someone else changed this listing while the table was open. Its row has been refreshed; try again.",
      ),
    ).toBeVisible();
    const row = screen.getByRole("row", { name: /Thai jasmine rice 10 kg/ });
    expect(await within(row).findByText("v5")).toBeVisible();
    expect(rice).toMatchObject({ status: "on_sale", version: 5 });
  });

  it("offers Publish and Delete draft on a draft row, and Edit opens the editor", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const milk = store.rows.find((r) => r.productNo === "BB-100472");
    const { router } = renderRoute("/admin/listings");
    await user.click(
      await screen.findByRole("button", { name: "Actions for Hokkaido milk, 12 cartons" }),
    );
    const menu = await screen.findByRole("menu");
    expect(
      within(menu)
        .getAllByRole("menuitem")
        .map((i) => i.textContent),
    ).toEqual(["Edit", "Publish", "Delete draft"]);
    await user.click(within(menu).getByRole("menuitem", { name: "Edit" }));
    await waitFor(() => expect(router.state.location.pathname).toBe(`/admin/listings/${milk?.id}`));
  });
});
