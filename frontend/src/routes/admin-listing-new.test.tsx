import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { server } from "@/test/msw";
import { renderRoute, serveAdmin } from "@/test/render-route";

type User = ReturnType<typeof userEvent.setup>;

async function fillRequired(user: User, productNo = "BB-100490") {
  await user.type(
    await screen.findByRole("textbox", { name: "Title" }),
    "Hokkaido milk, 12 cartons",
  );
  await user.type(screen.getByRole("textbox", { name: "Product number" }), productNo);
  await user.type(screen.getByRole("textbox", { name: "Solo price" }), "46.80");
}

// The new-draft editor (admin plan A1-05, artboard 4b.1) over the stateful admin store.
describe("/admin/listings/new", () => {
  it("explains the draft before anything is saved", async () => {
    serveAdmin();
    renderRoute("/admin/listings/new");
    expect(await screen.findByRole("heading", { name: "New listing" })).toBeVisible();
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
  });

  it("saves a draft at version 0 and lands on the listings with it on top", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const { router } = renderRoute("/admin/listings/new");
    await fillRequired(user);
    const stock = screen.getByRole("textbox", { name: "Stock" });
    await user.clear(stock);
    await user.type(stock, "45");
    await user.type(
      screen.getByRole("textbox", { name: "Image URL 1" }),
      "https://cdn.example.com/milk.jpg",
    );
    await user.click(screen.getByRole("button", { name: "Save draft" }));
    await waitFor(() => expect(router.state.location.pathname).toBe("/admin/listings"));
    expect(store.rows[0]).toMatchObject({
      productNo: "BB-100490",
      status: "draft",
      version: 0,
      listing: {
        title: "Hokkaido milk, 12 cartons",
        originalPrice: "46.80",
        currency: "sgd",
        stock: 45,
        images: ["https://cdn.example.com/milk.jpg"],
      },
    });
    expect(store.rows[0]?.listing).not.toHaveProperty("subtitle");
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
    expect(screen.getByText("Enter a product number.")).toBeVisible();
    expect(screen.getByText("Enter a price above S$0.00")).toBeVisible();
    expect(screen.getByText("Use an https:// URL.")).toBeVisible();
    expect(screen.getByRole("textbox", { name: "Title" })).toHaveAttribute("aria-invalid", "true");
    expect(store.rows).toHaveLength(62);
  });

  it("puts a taken product number on its field", async () => {
    const user = userEvent.setup();
    serveAdmin();
    renderRoute("/admin/listings/new");
    await fillRequired(user, "BB-100481");
    await user.click(screen.getByRole("button", { name: "Save draft" }));
    expect(await screen.findByText("That product number is already in use.")).toBeVisible();
    expect(screen.getByRole("textbox", { name: "Product number" })).toHaveAttribute(
      "aria-invalid",
      "true",
    );
  });

  it("maps the server's field errors onto the form", async () => {
    const user = userEvent.setup();
    serveAdmin();
    server.use(
      http.post("*/api/v1/admin/products", () =>
        HttpResponse.json(
          {
            code: "COMMON_400_001",
            message: "Invalid request",
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
});
