import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import type { createAdminStore } from "@/test/admin-msw";
import { renderRoute, serveAdmin } from "@/test/render-route";

type Store = ReturnType<typeof createAdminStore>;

// The browser history's `beforeunload` handler (@tanstack/history) blocks when any blocker's
// `enableBeforeUnload` is `true` (the default) or a function returning `true`. The memory history
// used here never listens for `beforeunload`, so apply the same rule to its blockers directly.
function blocksUnload(router: ReturnType<typeof renderRoute>["router"]) {
  // oxlint-disable-next-line no-underscore-dangle -- the history's only (typed) way to list blockers
  return router.history._getBlockers().some((b) => {
    const enable = b.enableBeforeUnload ?? true;
    return enable === true || (typeof enable === "function" && enable());
  });
}

function rowOf(store: Store, productNo: string) {
  const row = store.rows.find((r) => r.productNo === productNo);
  if (!row) throw new Error(`no seeded row ${productNo}`);
  return row;
}

// The editor for an existing listing (admin plan A1-06, artboards 4b.2 and 4b.3) over the
// stateful admin store.
describe("/admin/listings/$productId", () => {
  it("edits a draft with every field open", async () => {
    const store = serveAdmin();
    const milk = rowOf(store, "BB-100472");
    renderRoute(`/admin/listings/${milk.id}`);
    expect(
      await screen.findByRole("heading", { level: 1, name: "Hokkaido milk, 12 cartons" }),
    ).toBeVisible();
    expect(screen.getByText("BB-100472 · draft · v0")).toBeVisible();
    expect(screen.getByRole("button", { name: "Save draft" })).toBeEnabled();
    expect(screen.getByRole("textbox", { name: "Title" })).toHaveValue("Hokkaido milk, 12 cartons");
    expect(screen.getByRole("textbox", { name: "Solo price" })).not.toHaveAttribute("readonly");
    expect(screen.getByRole("textbox", { name: "Stock" })).not.toHaveAttribute("readonly");
    expect(screen.queryByText("Frozen")).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Sold" })).not.toBeInTheDocument();

    const status = screen.getByRole("region", { name: "Status" });
    expect(within(status).getByText("Draft")).toBeVisible();
    expect(within(status).getByText("v0")).toBeVisible();
    const trail = within(status).getByRole("list", { name: "Lifecycle" });
    expect(
      within(trail)
        .getAllByRole("listitem")
        .map((li) => li.textContent),
    ).toEqual([
      "Draft — you are here",
      "On sale — after publishing",
      "Off sale — reversible later",
    ]);
    expect(within(trail).getByText("Draft — you are here").closest("li")).toHaveAttribute(
      "aria-current",
      "step",
    );
    expect(
      within(status).getByText(
        "Draft → on sale → off sale → on sale. There is no way back to draft.",
      ),
    ).toBeVisible();
  });

  it("freezes price, currency and stock once published", async () => {
    const store = serveAdmin();
    const rice = rowOf(store, "BB-100481");
    renderRoute(`/admin/listings/${rice.id}`);
    expect(
      await screen.findByRole("heading", { level: 1, name: "Thai jasmine rice 10 kg" }),
    ).toBeVisible();
    expect(screen.getByText("BB-100481 · on sale · v4 · 142 sold")).toBeVisible();
    expect(screen.getByRole("button", { name: "Save changes" })).toBeEnabled();

    expect(screen.getByRole("textbox", { name: "Title" })).not.toHaveAttribute("readonly");
    for (const name of ["Solo price", "Currency", "Stock", "Sold"]) {
      expect(screen.getByRole("textbox", { name })).toHaveAttribute("readonly");
    }
    expect(screen.getByRole("textbox", { name: "Solo price" })).toHaveValue("89.00");
    expect(screen.getByRole("textbox", { name: "Sold" })).toHaveValue("142");
    expect(screen.getAllByText("Frozen")).toHaveLength(3);
    expect(
      screen.getByText("Frozen at first publish, so quotes already issued stay honest."),
    ).toBeVisible();
    expect(screen.getByText("Frozen. BlocBuy is Singapore-only.")).toBeVisible();
    expect(
      screen.getByText("Frozen at first publish. Sold count moves; stock does not."),
    ).toBeVisible();
    expect(screen.getByText("Read-only, maintained by the server.")).toBeVisible();

    const trail = screen.getByRole("list", { name: "Lifecycle" });
    expect(
      within(trail)
        .getAllByRole("listitem")
        .map((li) => li.textContent),
    ).toEqual(["Draft — closed for good", "On sale — you are here", "Off sale — available now"]);
  });

  it("saves with the loaded version and the frozen values unchanged", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const rice = rowOf(store, "BB-100481");
    const { router } = renderRoute(`/admin/listings/${rice.id}`);
    const title = await screen.findByRole("textbox", { name: "Title" });
    await user.clear(title);
    await user.type(title, "Thai jasmine rice 10 kg, new crop");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByText("BB-100481 · on sale · v5 · 142 sold")).toBeVisible();
    expect(
      screen.getByRole("heading", { level: 1, name: "Thai jasmine rice 10 kg, new crop" }),
    ).toBeVisible();
    // The store checks `expectedVersion` against v4 and refuses a changed frozen field (409), so
    // a v5 row with the same price and stock means both were sent as loaded.
    expect(rice).toMatchObject({
      version: 5,
      listing: {
        title: "Thai jasmine rice 10 kg, new crop",
        originalPrice: "89.00",
        currency: "sgd",
        stock: 38,
      },
    });

    // The form was remounted from the saved listing, so leaving asks nothing.
    const crumb = screen.getByRole("navigation", { name: "Breadcrumb" });
    await user.click(within(crumb).getByRole("link", { name: "Listings" }));
    await waitFor(() => expect(router.state.location.pathname).toBe("/admin/listings"));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("asks before leaving with unsaved changes", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const milk = rowOf(store, "BB-100472");
    const { router } = renderRoute(`/admin/listings/${milk.id}`);
    await user.type(await screen.findByRole("textbox", { name: "Subtitle" }), "Chilled, 1 L each");
    const crumb = screen.getByRole("navigation", { name: "Breadcrumb" });

    await user.click(within(crumb).getByRole("link", { name: "Listings" }));
    const dialog = await screen.findByRole("alertdialog");
    expect(within(dialog).getByRole("heading", { name: "Discard your changes?" })).toBeVisible();
    await user.click(within(dialog).getByRole("button", { name: "Keep editing" }));
    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
    expect(router.state.location.pathname).toBe(`/admin/listings/${milk.id}`);
    expect(screen.getByRole("textbox", { name: "Subtitle" })).toHaveValue("Chilled, 1 L each");

    await user.click(within(crumb).getByRole("link", { name: "Listings" }));
    await user.click(
      within(await screen.findByRole("alertdialog")).getByRole("button", { name: "Discard" }),
    );
    await waitFor(() => expect(router.state.location.pathname).toBe("/admin/listings"));
    expect(milk.listing).not.toHaveProperty("subtitle");
  });

  it("asks before a reload or tab close only while the form has unsaved changes", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const milk = rowOf(store, "BB-100472");
    const { router } = renderRoute(`/admin/listings/${milk.id}`);
    const subtitle = await screen.findByRole("textbox", { name: "Subtitle" });
    expect(blocksUnload(router)).toBe(false);
    await user.type(subtitle, "Chilled, 1 L each");
    expect(blocksUnload(router)).toBe(true);
  });

  it("says so inside the console when the listing does not exist", async () => {
    serveAdmin();
    renderRoute("/admin/listings/0192f0a0-0000-7000-a000-999999999999");
    expect(
      await screen.findByRole("heading", { name: "This listing no longer exists" }),
    ).toBeVisible();
    const main = screen.getByRole("main");
    expect(within(main).getByRole("link", { name: "Listings" })).toHaveAttribute(
      "href",
      "/admin/listings",
    );
    // Still inside the console: the rail is there.
    expect(screen.getByRole("navigation", { name: "Admin" })).toBeVisible();
  });

  it("opens from a title in the listings table", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const rice = rowOf(store, "BB-100481");
    const { router } = renderRoute("/admin/listings");
    await user.click(await screen.findByRole("link", { name: "Thai jasmine rice 10 kg" }));
    await waitFor(() => expect(router.state.location.pathname).toBe(`/admin/listings/${rice.id}`));
    expect(
      await screen.findByRole("heading", { level: 1, name: "Thai jasmine rice 10 kg" }),
    ).toBeVisible();
  });
});
