import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import { server } from "@/test/msw";
import { renderRoute, serveAdmin } from "@/test/render-route";

type User = ReturnType<typeof userEvent.setup>;

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

async function fillRequired(user: User) {
  await user.type(
    await screen.findByRole("textbox", { name: "Title" }),
    "Hokkaido milk, 12 cartons",
  );
  await user.type(screen.getByRole("textbox", { name: "Solo price" }), "46.80");
}

// The new-draft editor (admin plan A1-05, artboard 4b.1) over the stateful admin store.
describe("/admin/listings/new", () => {
  it("returns to the listings when a successful save omits the new id", async () => {
    serveAdmin();
    server.use(
      http.post("*/api/v1/admin/products", () =>
        HttpResponse.json({ code: "COMMON_200_000", data: { status: "draft", version: 0 } }),
      ),
    );
    const { router } = renderRoute("/admin/listings/new");
    await fillRequired(userEvent.setup());
    await userEvent.click(screen.getByRole("button", { name: "Save draft" }));
    await waitFor(() => expect(router.state.location.pathname).toBe("/admin/listings"));
    expect(await screen.findByRole("heading", { name: "Listings" })).toBeVisible();
  });
  it("hands a revoked role during draft creation to the admin boundary", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    serveAdmin();
    server.use(
      http.post("*/api/v1/admin/products", () =>
        HttpResponse.json({ code: "COMMON_403_001" }, { status: 403 }),
      ),
    );
    renderRoute("/admin/listings/new");
    await fillRequired(userEvent.setup());
    await userEvent.click(screen.getByRole("button", { name: "Save draft" }));
    expect(
      await screen.findByRole("heading", { name: "You don’t have access to the admin console" }),
    ).toBeVisible();
  });
  it("explains the draft before anything is saved", async () => {
    serveAdmin();
    renderRoute("/admin/listings/new");
    expect(await screen.findByRole("heading", { name: "New listing" })).toBeVisible();
    expect(document.title).toBe("New listing · BlocBuy");
    expect(
      screen.getByText(
        "Saved as a draft at version 0. Nothing is visible on the storefront until you publish.",
      ),
    ).toBeVisible();
    const crumb = screen.getByRole("navigation", { name: "Breadcrumb" });
    expect(within(crumb).getByRole("link", { name: "Listings" })).toHaveAttribute(
      "href",
      "/admin/listings",
    );
    const status = screen.getByRole("region", { name: "Status" });
    expect(within(status).getByText("Draft")).toBeVisible();
    expect(within(status).getByText("v0")).toBeVisible();
    expect(within(status).getByRole("button", { name: "Publish" })).toBeDisabled();
    expect(
      within(status).getByText(
        "Publishing needs a positive price and positive stock. Fill those in and save first.",
      ),
    ).toBeVisible();
    const rule = screen.getByRole("region", { name: "Bloc price rule" });
    expect(within(rule).getByRole("button", { name: "Add a rule" })).toBeDisabled();
    expect(screen.getByRole("textbox", { name: "Currency" })).toHaveValue("SGD");
    expect(screen.getByRole("textbox", { name: "Currency" })).toHaveAttribute("readonly");
    // Artboard 4b.1: "the server assigns it on save" (#288).
    expect(screen.queryByRole("textbox", { name: "Product number" })).not.toBeInTheDocument();
  });

  it("saves a draft at version 0 and lands on its editor", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const sent: unknown[] = [];
    server.events.on("request:start", ({ request }) => {
      if (request.method === "POST")
        void request
          .clone()
          .json()
          .then((b) => sent.push(b));
    });
    const { router } = renderRoute("/admin/listings/new");
    await fillRequired(user);
    const stock = screen.getByRole("textbox", { name: "Stock" });
    await user.clear(stock);
    await user.type(stock, "45");
    await user.click(screen.getByRole("button", { name: /choose a category/i }));
    await user.click(await screen.findByRole("option", { name: "Groceries" }));
    await user.click(screen.getByRole("button", { name: "Add another URL" }));
    await user.type(
      screen.getByRole("textbox", { name: "Image URL 2" }),
      "https://cdn.example.com/unused.jpg",
    );
    await user.click(screen.getByRole("button", { name: "Remove image URL 2" }));
    await user.type(
      screen.getByRole("textbox", { name: "Image URL 1" }),
      "https://cdn.example.com/milk.jpg",
    );
    await user.click(screen.getByRole("button", { name: "Save draft" }));
    await waitFor(() =>
      expect(router.state.location.pathname).toBe(`/admin/listings/${store.rows[0]?.id ?? ""}`),
    );
    expect(await screen.findByText("BB-100001 · draft · v0")).toBeVisible();
    // The save's own navigation leaves a dirty form without the leave prompt.
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
    expect(sent).toHaveLength(1);
    expect(sent[0]).not.toHaveProperty("productNo");
    expect(store.rows[0]).toMatchObject({
      productNo: "BB-100001",
      status: "draft",
      version: 0,
      listing: {
        title: "Hokkaido milk, 12 cartons",
        originalPrice: "46.80",
        currency: "sgd",
        category: "groceries",
        stock: 45,
        images: ["https://cdn.example.com/milk.jpg"],
      },
    });
    expect(store.rows[0]?.listing).not.toHaveProperty("subtitle");
    // The table pages were dropped on save, so the new draft is counted there on the way back.
    const crumb = screen.getByRole("navigation", { name: "Breadcrumb" });
    await user.click(within(crumb).getByRole("link", { name: "Listings" }));
    expect(await screen.findByText("Showing 1–20 of 63")).toBeVisible();
  });

  it("shows each problem next to its field and sends nothing", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    renderRoute("/admin/listings/new");
    await user.type(await screen.findByRole("textbox", { name: "Solo price" }), "0");
    await user.type(screen.getByRole("textbox", { name: "Cover image URL" }), "http://x.test/a");
    await user.click(screen.getByRole("button", { name: "Save draft" }));
    expect(await screen.findByText("Enter a title.")).toBeVisible();
    expect(screen.getByText("Enter a price above S$0.00")).toBeVisible();
    expect(screen.getByText("Use an https:// URL.")).toBeVisible();
    expect(screen.getByRole("textbox", { name: "Title" })).toHaveAttribute("aria-invalid", "true");
    expect(store.rows).toHaveLength(62);
  });

  it("maps the server's field errors onto the form", async () => {
    const user = userEvent.setup();
    serveAdmin();
    server.use(
      http.post("*/api/v1/admin/products", () =>
        HttpResponse.json(
          {
            code: "COMMON_400_002",
            message: "Request validation failed",
            errors: [{ field: "listing.subtitle", message: "size must be between 0 and 256" }],
          },
          { status: 400 },
        ),
      ),
    );
    renderRoute("/admin/listings/new");
    await fillRequired(user);
    await user.click(screen.getByRole("button", { name: "Save draft" }));
    expect(await screen.findByText("size must be between 0 and 256")).toBeVisible();
    expect(screen.getByRole("textbox", { name: "Subtitle" })).toHaveAttribute(
      "aria-invalid",
      "true",
    );
  });

  // #323 (BA-6): a domain 400 names the request path of the field it refuses.
  it("puts a catalog domain 400 on the field it names", async () => {
    const user = userEvent.setup();
    serveAdmin();
    server.use(
      http.post("*/api/v1/admin/products", () =>
        HttpResponse.json(
          {
            code: "CATALOG_400_002",
            message: "Invalid listing",
            errors: [
              { field: "listing.originalPrice", message: "Must have at most two decimal places" },
            ],
          },
          { status: 400 },
        ),
      ),
    );
    renderRoute("/admin/listings/new");
    await fillRequired(user);
    await user.click(screen.getByRole("button", { name: "Save draft" }));
    expect(await screen.findByText("Must have at most two decimal places")).toBeVisible();
    expect(screen.getByRole("textbox", { name: "Solo price" })).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(screen.queryByText("Some fields need another look")).not.toBeInTheDocument();
  });

  // The form has no product number field since A3-08, so that path, like any path without a
  // field, is read in the form's alert; a named field still gets its own message.
  it("reads a domain 400 on a path without a field in the form's alert", async () => {
    const user = userEvent.setup();
    serveAdmin();
    server.use(
      http.post("*/api/v1/admin/products", () =>
        HttpResponse.json(
          {
            code: "CATALOG_400_002",
            message: "Invalid listing",
            errors: [
              { field: "productNo", message: "Must start with a letter or digit." },
              { field: "listing.title", message: "Must be nonblank and at most 256 characters" },
            ],
          },
          { status: 400 },
        ),
      ),
    );
    renderRoute("/admin/listings/new");
    await fillRequired(user);
    await user.click(screen.getByRole("button", { name: "Save draft" }));
    const alert = await screen.findByRole("alert");
    expect(within(alert).getByText("Some fields need another look")).toBeVisible();
    expect(within(alert).getByText("Must start with a letter or digit.")).toBeVisible();
    expect(screen.getByText("Must be nonblank and at most 256 characters")).toBeVisible();
    expect(screen.getByRole("textbox", { name: "Title" })).toHaveAttribute("aria-invalid", "true");
  });

  it("says so when the server refuses the listing as a whole", async () => {
    const user = userEvent.setup();
    serveAdmin();
    server.use(
      http.post("*/api/v1/admin/products", () =>
        HttpResponse.json({ code: "CATALOG_400_002", message: "Invalid listing" }, { status: 400 }),
      ),
    );
    renderRoute("/admin/listings/new");
    await fillRequired(user);
    await user.click(screen.getByRole("button", { name: "Save draft" }));
    const alert = await screen.findByRole("alert");
    expect(within(alert).getByText("The draft wasn’t saved")).toBeVisible();
  });

  it("goes back to the listings on Cancel", async () => {
    const user = userEvent.setup();
    serveAdmin();
    const { router } = renderRoute("/admin/listings/new");
    await user.click(await screen.findByRole("link", { name: "Cancel" }));
    await waitFor(() => expect(router.state.location.pathname).toBe("/admin/listings"));
  });

  it("asks before the breadcrumb drops a typed title, and keeps it on Keep editing", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const { router } = renderRoute("/admin/listings/new");
    await user.type(await screen.findByRole("textbox", { name: "Title" }), "Hokkaido milk");
    const crumb = screen.getByRole("navigation", { name: "Breadcrumb" });

    await user.click(within(crumb).getByRole("link", { name: "Listings" }));
    const dialog = await screen.findByRole("alertdialog");
    expect(within(dialog).getByRole("heading", { name: "Discard your changes?" })).toBeVisible();
    await user.click(within(dialog).getByRole("button", { name: "Keep editing" }));
    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
    expect(router.state.location.pathname).toBe("/admin/listings/new");
    expect(screen.getByRole("textbox", { name: "Title" })).toHaveValue("Hokkaido milk");
    expect(store.rows).toHaveLength(62);
  });

  it("leaves for the listings once the unsaved draft is discarded", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const { router } = renderRoute("/admin/listings/new");
    await user.type(await screen.findByRole("textbox", { name: "Title" }), "Hokkaido milk");

    await user.click(screen.getByRole("link", { name: "Cancel" }));
    await user.click(
      within(await screen.findByRole("alertdialog")).getByRole("button", { name: "Discard" }),
    );
    await waitFor(() => expect(router.state.location.pathname).toBe("/admin/listings"));
    expect(store.rows).toHaveLength(62);
  });

  it("never asks while the form is untouched", async () => {
    const user = userEvent.setup();
    serveAdmin();
    const { router } = renderRoute("/admin/listings/new");
    await user.click(await screen.findByRole("link", { name: "Cancel" }));
    await waitFor(() => expect(router.state.location.pathname).toBe("/admin/listings"));
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("asks before a reload or tab close only once something is typed", async () => {
    const user = userEvent.setup();
    serveAdmin();
    const { router } = renderRoute("/admin/listings/new");
    const title = await screen.findByRole("textbox", { name: "Title" });
    expect(blocksUnload(router)).toBe(false);
    await user.type(title, "Hokkaido milk");
    expect(blocksUnload(router)).toBe(true);
  });
});
