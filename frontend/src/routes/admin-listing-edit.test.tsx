import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it } from "vitest";

import type { createAdminStore } from "@/test/admin-msw";
import { server } from "@/test/msw";
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

  it("keeps a stored category the fixed list does not name, and saves with it", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const milk = rowOf(store, "BB-100472");
    milk.listing.category = "snacks";
    renderRoute(`/admin/listings/${milk.id}`);
    const title = await screen.findByRole("textbox", { name: "Title" });
    expect(screen.getByRole("button", { name: /snacks/ })).toBeVisible();

    await user.clear(title);
    await user.type(title, "Hokkaido milk, 12 cartons (chilled)");
    await user.click(screen.getByRole("button", { name: "Save draft" }));

    expect(await screen.findByText("BB-100472 · draft · v1")).toBeVisible();
    expect(screen.queryByText(/choose a category/i)).not.toBeInTheDocument();
    expect(milk).toMatchObject({
      version: 1,
      listing: { title: "Hokkaido milk, 12 cartons (chilled)", category: "snacks" },
    });
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

  it("keeps asking before leaving while a save is still in flight", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const milk = rowOf(store, "BB-100472");
    // Hold the save open; returning nothing falls through to the store's handler once released.
    let release!: () => void;
    const held = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.put("*/api/v1/admin/products/:productId", async () => {
        await held;
      }),
    );
    const { router } = renderRoute(`/admin/listings/${milk.id}`);
    await user.type(await screen.findByRole("textbox", { name: "Subtitle" }), "Chilled, 1 L each");
    await user.click(screen.getByRole("button", { name: "Save draft" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Save draft" })).toBeDisabled());

    // A failed save must still find its form, and an unload would cancel the request.
    expect(blocksUnload(router)).toBe(true);
    const crumb = screen.getByRole("navigation", { name: "Breadcrumb" });
    await user.click(within(crumb).getByRole("link", { name: "Listings" }));
    const dialog = await screen.findByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "Keep editing" }));
    expect(router.state.location.pathname).toBe(`/admin/listings/${milk.id}`);

    release();
    expect(await screen.findByText("BB-100472 · draft · v1")).toBeVisible();
    expect(blocksUnload(router)).toBe(false);
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

  it("publishes a saved draft after naming the fields that freeze", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const milk = rowOf(store, "BB-100472");
    renderRoute(`/admin/listings/${milk.id}`);
    await user.click(await screen.findByRole("button", { name: "Publish" }));
    const dialog = await screen.findByRole("alertdialog", { name: "Publish this listing?" });
    expect(within(dialog).getByText("S$46.80")).toBeVisible();
    await user.click(within(dialog).getByRole("button", { name: "Publish listing" }));

    expect(await screen.findByText("BB-100472 · on sale · v1 · 0 sold")).toBeVisible();
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Solo price" })).toHaveAttribute("readonly");
    expect(screen.queryByRole("button", { name: "Publish" })).not.toBeInTheDocument();
    const status = screen.getByRole("region", { name: "Status" });
    expect(within(status).getByRole("button", { name: "Take off sale" })).toBeVisible();
    expect(milk).toMatchObject({ status: "on_sale", version: 1 });
  });

  it("keeps Publish disabled until the draft is saved and sellable", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const milk = rowOf(store, "BB-100472");
    renderRoute(`/admin/listings/${milk.id}`);
    const publish = await screen.findByRole("button", { name: "Publish" });
    expect(publish).toBeEnabled();
    await user.type(screen.getByRole("textbox", { name: "Subtitle" }), "Chilled");
    await waitFor(() => expect(publish).toBeDisabled());
    expect(screen.getByText("Save your changes before publishing.")).toBeVisible();
  });

  it("explains why Publish is disabled on a draft without stock", async () => {
    const store = serveAdmin();
    const milk = rowOf(store, "BB-100472");
    milk.listing.stock = 0;
    renderRoute(`/admin/listings/${milk.id}`);
    expect(await screen.findByRole("button", { name: "Publish" })).toBeDisabled();
    expect(
      screen.getByText(
        "Publishing needs a positive price and positive stock. Fill those in and save first.",
      ),
    ).toBeVisible();
  });

  it("deletes a draft from its danger zone and lands on the Draft tab", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const milk = rowOf(store, "BB-100472");
    const { router } = renderRoute(`/admin/listings/${milk.id}`);
    const status = await screen.findByRole("region", { name: "Status" });
    expect(within(status).getByText("Danger zone")).toBeVisible();
    expect(
      within(status).getByText("Allowed: never published, no bloc, quote or rule history."),
    ).toBeVisible();
    await user.click(within(status).getByRole("button", { name: "Delete draft" }));
    const dialog = await screen.findByRole("alertdialog", { name: "Delete this draft?" });
    await user.click(within(dialog).getByRole("button", { name: "Delete draft" }));

    expect(await screen.findByText("Draft deleted")).toBeVisible();
    await waitFor(() => expect(router.state.location.pathname).toBe("/admin/listings"));
    expect(router.state.location.search).toMatchObject({ tab: "draft" });
    expect(store.find(milk.id)).toBeUndefined();
  });

  it("replaces Delete with its reason once published, and takes the listing off sale", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const rice = rowOf(store, "BB-100481");
    renderRoute(`/admin/listings/${rice.id}`);
    const status = await screen.findByRole("region", { name: "Status" });
    expect(
      within(status).getByText(
        "Not available. This listing has been published; take it off sale instead.",
      ),
    ).toBeVisible();
    expect(within(status).queryByRole("button", { name: /Delete/ })).not.toBeInTheDocument();

    await user.click(within(status).getByRole("button", { name: "Take off sale" }));
    const dialog = await screen.findByRole("alertdialog", { name: "Take this listing off sale?" });
    await user.click(within(dialog).getByRole("button", { name: "Take off sale" }));
    expect(await screen.findByText("BB-100481 · off sale · v5 · 142 sold")).toBeVisible();
    expect(
      await within(screen.getByRole("region", { name: "Status" })).findByRole("button", {
        name: "Put back on sale",
      }),
    ).toBeVisible();
  });
});

// Conflicts and refusals (admin plan A1-08, artboard 4b.4). "Someone else" is the test moving the
// stored row on, as another admin's save would.
describe("/admin/listings/$productId conflicts and refusals", () => {
  const stale =
    "You loaded v4; the server now has v5. Reload to see the latest version — your unsaved changes will be lost.";

  it("turns a stale save into the conflict banner and keeps the typed edits until Reload", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const rice = rowOf(store, "BB-100481");
    renderRoute(`/admin/listings/${rice.id}`);
    const title = await screen.findByRole("textbox", { name: "Title" });
    rice.version = 5;
    rice.listing.title = "Thai jasmine rice 10 kg (their edit)";
    await user.clear(title);
    await user.type(title, "Thai jasmine rice 10 kg (my edit)");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByText("This listing was updated by someone else")).toBeVisible();
    expect(await screen.findByText(stale)).toBeVisible();
    // The background read leaves the form alone, and nothing can be sent until Reload.
    expect(screen.getByRole("textbox", { name: "Title" })).toHaveValue(
      "Thai jasmine rice 10 kg (my edit)",
    );
    expect(screen.getByText("BB-100481 · on sale · v4 · 142 sold")).toBeVisible();
    expect(screen.getByRole("button", { name: "Save changes" })).toBeDisabled();
    const status = screen.getByRole("region", { name: "Status" });
    expect(within(status).getByRole("button", { name: "Take off sale" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Reload" }));
    expect(await screen.findByText("BB-100481 · on sale · v5 · 142 sold")).toBeVisible();
    expect(screen.queryByText("This listing was updated by someone else")).not.toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Title" })).toHaveValue(
      "Thai jasmine rice 10 kg (their edit)",
    );
    expect(screen.getByRole("button", { name: "Save changes" })).toBeEnabled();
  });

  // Raises the banner the usual way: someone else saved v5 while v4 was open.
  async function staleSave(user: ReturnType<typeof userEvent.setup>, store: Store) {
    const rice = rowOf(store, "BB-100481");
    renderRoute(`/admin/listings/${rice.id}`);
    await user.type(await screen.findByRole("textbox", { name: "Subtitle" }), "!");
    rice.version = 5;
    await user.click(screen.getByRole("button", { name: "Save changes" }));
    expect(await screen.findByText(stale)).toBeVisible();
    return rice;
  }

  it("says so when Reload fails, keeps the banner and lets Reload try again", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    let failing = true;
    server.use(
      http.get("*/api/v1/admin/products/:productId", () =>
        failing
          ? HttpResponse.json(
              { code: "COMMON_500_001", message: "Internal error" },
              { status: 500 },
            )
          : undefined,
      ),
    );
    failing = false;
    await staleSave(user, store);

    failing = true;
    await user.click(screen.getByRole("button", { name: "Reload" }));
    expect(await screen.findByText("We couldn’t reload the listing. Try again.")).toBeVisible();
    expect(screen.getByText("This listing was updated by someone else")).toBeVisible();

    failing = false;
    await user.click(screen.getByRole("button", { name: "Reload" }));
    expect(await screen.findByText("BB-100481 · on sale · v5 · 142 sold")).toBeVisible();
    expect(screen.queryByText("This listing was updated by someone else")).not.toBeInTheDocument();
    expect(
      screen.queryByText("We couldn’t reload the listing. Try again."),
    ).not.toBeInTheDocument();
  });

  it("shows that the listing no longer exists when Reload finds it deleted", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const rice = await staleSave(user, store);
    store.rows.splice(store.rows.indexOf(rice), 1);

    await user.click(screen.getByRole("button", { name: "Reload" }));
    expect(
      await screen.findByRole("heading", { name: "This listing no longer exists" }),
    ).toBeVisible();
  });

  it("shows Access denied when Reload is refused", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    await staleSave(user, store);
    server.use(
      http.get("*/api/v1/admin/products/:productId", () =>
        HttpResponse.json(
          { code: "COMMON_403_001", message: "Permission denied" },
          { status: 403 },
        ),
      ),
    );

    await user.click(screen.getByRole("button", { name: "Reload" }));
    expect(
      await screen.findByRole("heading", { name: "You don’t have access to the admin console" }),
    ).toBeVisible();
  });

  it("names the server's version from the 412 itself, without another read (#286)", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const rice = rowOf(store, "BB-100481");
    renderRoute(`/admin/listings/${rice.id}`);
    const title = await screen.findByRole("textbox", { name: "Title" });
    // The 412 names v7 while a read would still say v4, so the banner's number can only come from
    // the error.
    let readsAfterSave = 0;
    server.use(
      http.put("*/api/v1/admin/products/:productId", () =>
        HttpResponse.json(
          {
            code: "CATALOG_412_002",
            message: "Listing version precondition failed",
            errors: { currentVersion: 7 },
          },
          { status: 412 },
        ),
      ),
      http.get("*/api/v1/admin/products/:productId", () => {
        readsAfterSave += 1;
      }),
    );
    await user.type(title, " (my edit)");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(
      await screen.findByText(
        "You loaded v4; the server now has v7. Reload to see the latest version — your unsaved changes will be lost.",
      ),
    ).toBeVisible();
    expect(readsAfterSave).toBe(0);
  });

  it("falls back to reading the server's version when the 412 names none", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const rice = rowOf(store, "BB-100481");
    renderRoute(`/admin/listings/${rice.id}`);
    const title = await screen.findByRole("textbox", { name: "Title" });
    rice.version = 5;
    server.use(
      http.put("*/api/v1/admin/products/:productId", () =>
        HttpResponse.json(
          { code: "CATALOG_412_002", message: "Listing version precondition failed" },
          { status: 412 },
        ),
      ),
    );
    await user.type(title, " (my edit)");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByText(stale)).toBeVisible();
  });

  it("closes a stale publish into the same banner", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const milk = rowOf(store, "BB-100472");
    renderRoute(`/admin/listings/${milk.id}`);
    await user.click(await screen.findByRole("button", { name: "Publish" }));
    milk.version = 1;
    const dialog = await screen.findByRole("alertdialog", { name: "Publish this listing?" });
    await user.click(within(dialog).getByRole("button", { name: "Publish listing" }));

    expect(await screen.findByText("This listing was updated by someone else")).toBeVisible();
    await waitFor(() =>
      expect(
        screen.queryByRole("alertdialog", { name: "Publish this listing?" }),
      ).not.toBeInTheDocument(),
    );
    expect(screen.getByRole("button", { name: "Publish" })).toBeDisabled();
    expect(milk).toMatchObject({ status: "draft", version: 1 });
  });

  it("closes a stale delete into the same banner", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const milk = rowOf(store, "BB-100472");
    renderRoute(`/admin/listings/${milk.id}`);
    const status = await screen.findByRole("region", { name: "Status" });
    await user.click(within(status).getByRole("button", { name: "Delete draft" }));
    milk.version = 1;
    const dialog = await screen.findByRole("alertdialog", { name: "Delete this draft?" });
    await user.click(within(dialog).getByRole("button", { name: "Delete draft" }));

    expect(await screen.findByText("This listing was updated by someone else")).toBeVisible();
    expect(
      within(screen.getByRole("region", { name: "Status" })).getByRole("button", {
        name: "Delete draft",
      }),
    ).toBeDisabled();
    expect(store.find(milk.id)).toBeDefined();
  });

  it("asks for a reload when the listing changed state underneath", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const rice = rowOf(store, "BB-100481");
    renderRoute(`/admin/listings/${rice.id}`);
    const status = await screen.findByRole("region", { name: "Status" });
    rice.status = "off_sale";
    await user.click(within(status).getByRole("button", { name: "Take off sale" }));
    const dialog = await screen.findByRole("alertdialog", { name: "Take this listing off sale?" });
    await user.click(within(dialog).getByRole("button", { name: "Take off sale" }));

    expect(
      await within(dialog).findByText(
        "This listing changed state. Reload to see what you can do now.",
      ),
    ).toBeVisible();
    expect(screen.queryByText("This listing was updated by someone else")).not.toBeInTheDocument();
  });

  it("keeps Save disabled until a field error clears", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const rice = rowOf(store, "BB-100481");
    renderRoute(`/admin/listings/${rice.id}`);
    const title = await screen.findByRole("textbox", { name: "Title" });
    await user.clear(title);
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    expect(await screen.findByText("Enter a title.")).toBeVisible();
    expect(screen.getByRole("button", { name: "Save changes" })).toBeDisabled();
    await user.type(title, "Jasmine rice");
    await waitFor(() => expect(screen.queryByText("Enter a title.")).not.toBeInTheDocument());
    expect(screen.getByRole("button", { name: "Save changes" })).toBeEnabled();
  });

  it("lists a field error and a conflict together as two problems to clear", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const rice = rowOf(store, "BB-100481");
    renderRoute(`/admin/listings/${rice.id}`);
    const title = await screen.findByRole("textbox", { name: "Title" });
    await user.clear(title);
    await user.click(screen.getByRole("button", { name: "Save changes" }));
    expect(await screen.findByText("Enter a title.")).toBeVisible();

    rice.version = 5;
    const status = screen.getByRole("region", { name: "Status" });
    await user.click(within(status).getByRole("button", { name: "Take off sale" }));
    const dialog = await screen.findByRole("alertdialog", { name: "Take this listing off sale?" });
    await user.click(within(dialog).getByRole("button", { name: "Take off sale" }));

    const problems = await screen.findByRole("region", { name: "Two problems to clear" });
    expect(
      within(problems)
        .getAllByRole("listitem")
        .map((li) => li.textContent),
    ).toEqual(["Title: Enter a title.", "Someone else saved v5 while this page was open."]);
    expect(await screen.findByText(stale)).toBeVisible();
  });

  it("keeps the form and offers sign-in when the session ended", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const rice = rowOf(store, "BB-100481");
    server.use(
      http.put("*/api/v1/admin/products/:productId", () =>
        HttpResponse.json({ code: "COMMON_401_001", message: "Not signed in" }, { status: 401 }),
      ),
    );
    renderRoute(`/admin/listings/${rice.id}`);
    const subtitle = await screen.findByRole("textbox", { name: "Subtitle" });
    await user.clear(subtitle);
    await user.type(subtitle, "Kept while signed out");
    await user.click(screen.getByRole("button", { name: "Save changes" }));

    const message = await screen.findByText("Your session ended. Sign in again to save.");
    const alert = message.closest<HTMLElement>('[role="alert"]');
    if (!alert) throw new Error("the session message is not in an alert");
    expect(message).toBeVisible();
    expect(within(alert).getByRole("link", { name: "Sign in" })).toHaveAttribute(
      "href",
      `/login?returnTo=${encodeURIComponent(`/admin/listings/${rice.id}`)}`,
    );
    expect(subtitle).toHaveValue("Kept while signed out");
  });

  it("shows Access denied when the admin role is revoked mid-session", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const rice = rowOf(store, "BB-100481");
    server.use(
      http.put("*/api/v1/admin/products/:productId", () =>
        HttpResponse.json(
          { code: "COMMON_403_001", message: "Permission denied" },
          { status: 403 },
        ),
      ),
    );
    renderRoute(`/admin/listings/${rice.id}`);
    await user.type(await screen.findByRole("textbox", { name: "Subtitle" }), "!");
    await user.click(screen.getByRole("button", { name: "Save changes" }));
    expect(
      await screen.findByRole("heading", { name: "You don’t have access to the admin console" }),
    ).toBeVisible();
  });
});
