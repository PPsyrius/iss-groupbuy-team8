import { Toast, toast } from "@heroui/react";
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { afterEach, describe, expect, it, vi } from "vitest";

import type { createAdminStore } from "@/test/admin-msw";
import { server } from "@/test/msw";
import { renderWithRouter } from "@/test/render";
import { serveAdmin } from "@/test/render-route";

import { type DialogKind, LifecycleDialog } from "./LifecycleDialogs";

type Store = ReturnType<typeof createAdminStore>;

function rowOf(store: Store, productNo: string) {
  const row = store.rows.find((r) => r.productNo === productNo);
  if (!row) throw new Error(`no seeded row ${productNo}`);
  return row;
}

async function open(kind: DialogKind, productNo: string) {
  const store = serveAdmin();
  const row = rowOf(store, productNo);
  const onClose = vi.fn<() => void>();
  const { router } = await renderWithRouter(
    <>
      <Toast.Provider />
      <LifecycleDialog kind={kind} listing={structuredClone(row)} onClose={onClose} />
    </>,
  );
  // Named, because a HeroUI toast is an alertdialog too.
  const dialog = await screen.findByRole("alertdialog", { name: /\?$/ });
  return { store, row, onClose, router, dialog };
}

// The lifecycle dialogs (admin plan A1-07, artboard 4b.2 and §11 D2) over the stateful admin
// store: each confirms, sends the loaded version, and closes only once the server agreed.
describe("LifecycleDialog", () => {
  // The toast queue is module-wide; a "Draft deleted" toast must not leak into the next test.
  afterEach(() => toast.clear());

  it("names the three fields that freeze, with their saved values, before publishing", async () => {
    const user = userEvent.setup();
    const { row, onClose, dialog } = await open("publish", "BB-100472");
    expect(within(dialog).getByRole("heading", { name: "Publish this listing?" })).toBeVisible();
    expect(
      within(dialog).getByText(
        "It becomes visible on the storefront immediately and buyers can start blocs on it.",
      ),
    ).toBeVisible();
    const freeze = within(dialog).getByRole("group", {
      name: "These three fields freeze for good",
    });
    const pairs = [...freeze.querySelectorAll("dt")].map(
      (dt) => `${dt.textContent}: ${dt.nextElementSibling?.textContent}`,
    );
    expect(pairs).toEqual([
      `Solo price: S$${row.listing.originalPrice}`,
      "Currency: SGD",
      `Stock: ${row.listing.stock}`,
    ]);
    expect(
      within(dialog).getByText(
        "Title, subtitle, description, images and category stay editable. Price, currency and stock do not — and there is no route back to draft.",
      ),
    ).toBeVisible();

    await user.click(within(dialog).getByRole("button", { name: "Publish listing" }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(row).toMatchObject({ status: "on_sale", version: 1 });
  });

  it("takes an on-sale listing off sale", async () => {
    const user = userEvent.setup();
    const { row, onClose, dialog } = await open("takeOffSale", "BB-100481");
    expect(
      within(dialog).getByRole("heading", { name: "Take this listing off sale?" }),
    ).toBeVisible();
    expect(
      within(dialog).getByText(
        "Buyers can no longer start or join blocs on it. Blocs already filled keep their orders. You can put it back on sale later.",
      ),
    ).toBeVisible();
    await user.click(within(dialog).getByRole("button", { name: "Take off sale" }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(row).toMatchObject({ status: "off_sale", version: 5 });
  });

  it("puts an off-sale listing back on sale", async () => {
    const user = userEvent.setup();
    const { row, onClose, dialog } = await open("putBackOnSale", "BB-100476");
    const before = row.version;
    expect(
      within(dialog).getByRole("heading", { name: "Put this listing back on sale?" }),
    ).toBeVisible();
    expect(
      within(dialog).getByText(
        "It becomes visible on the storefront again. Price, currency and stock are unchanged.",
      ),
    ).toBeVisible();
    await user.click(within(dialog).getByRole("button", { name: "Put back on sale" }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(row).toMatchObject({ status: "on_sale", version: before + 1 });
  });

  it("deletes a draft, says so, and lands on the Draft tab", async () => {
    const user = userEvent.setup();
    const { store, row, router, dialog } = await open("delete", "BB-100472");
    expect(within(dialog).getByRole("heading", { name: "Delete this draft?" })).toBeVisible();
    expect(
      within(dialog).getByText("It disappears from the console. This cannot be undone."),
    ).toBeVisible();
    await user.click(within(dialog).getByRole("button", { name: "Delete draft" }));
    expect(await screen.findByText("Draft deleted")).toBeVisible();
    await waitFor(() => expect(router.state.location.pathname).toBe("/admin/listings"));
    expect(router.state.location.search).toEqual({ tab: "draft" });
    expect(store.find(row.id)).toBeUndefined();
  });

  it("explains a draft that already has history instead of deleting it", async () => {
    const user = userEvent.setup();
    const { row, onClose, dialog } = await open("delete", "BB-100472");
    server.use(
      http.delete(`*/api/v1/admin/products/${row.id}`, () =>
        HttpResponse.json(
          { code: "CATALOG_409_004", message: "Listing has business history" },
          { status: 409 },
        ),
      ),
    );
    await user.click(within(dialog).getByRole("button", { name: "Delete draft" }));
    expect(
      await within(dialog).findByText(
        "Not available. This draft already has price or bloc history.",
      ),
    ).toBeVisible();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("keeps the dialog open with a plain message when the server refuses", async () => {
    const user = userEvent.setup();
    const { store, row, onClose, dialog } = await open("takeOffSale", "BB-100481");
    // Someone else changed the listing first: the loaded version is stale (A1-08 names it).
    store.conflictOnNextWrite();
    await user.click(within(dialog).getByRole("button", { name: "Take off sale" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent(
      "The listing was not changed",
    );
    expect(onClose).not.toHaveBeenCalled();
    expect(row.status).toBe("on_sale");
  });

  it("closes on Cancel without a request", async () => {
    const user = userEvent.setup();
    const { row, onClose, dialog } = await open("publish", "BB-100472");
    await user.click(within(dialog).getByRole("button", { name: "Cancel" }));
    expect(onClose).toHaveBeenCalled();
    expect(row).toMatchObject({ status: "draft", version: 0 });
  });

  it.each([
    [
      401,
      { code: "AUTH_401_001", message: "Expired" },
      "Your session ended. Sign in again to change this listing.",
    ],
    [
      400,
      {
        code: "COMMON_400_002",
        message: "Invalid",
        errors: [{ field: "status", message: "Invalid status" }],
      },
      "The server refused this change. Reload the listing and try again.",
    ],
  ])("keeps a refused change open for HTTP %s", async (status, body, message) => {
    const user = userEvent.setup();
    const { row, onClose, dialog } = await open("publish", "BB-100472");
    server.use(
      http.put(`*/api/v1/admin/products/${row.id}/status`, () =>
        HttpResponse.json(body, { status }),
      ),
    );
    await user.click(within(dialog).getByRole("button", { name: "Publish listing" }));
    expect(await within(dialog).findByText(message)).toBeVisible();
    expect(onClose).not.toHaveBeenCalled();
    expect(row.status).toBe("draft");
  });

  it("requires an explicit decision rather than dismissing with Escape", async () => {
    const user = userEvent.setup();
    const { row, onClose, dialog } = await open("publish", "BB-100472");
    await user.keyboard("{Escape}");
    expect(dialog).toBeVisible();
    expect(onClose).not.toHaveBeenCalled();
    expect(row).toMatchObject({ status: "draft", version: 0 });
  });

  it("shows unavailable saved values without inventing a price or stock", async () => {
    await renderWithRouter(<LifecycleDialog kind="publish" listing={{}} onClose={() => {}} />);
    const group = await screen.findByRole("group", { name: "These three fields freeze for good" });
    expect(within(group).getAllByText("—")).toHaveLength(2);
    expect(within(group).getByText("SGD")).toBeVisible();
  });

  it("does not open a dialog without a selected action", async () => {
    await renderWithRouter(<LifecycleDialog kind={null} listing={{}} onClose={() => {}} />);
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("disables both decisions while the lifecycle write is pending", async () => {
    const user = userEvent.setup();
    const { row, onClose, dialog } = await open("publish", "BB-100472");
    let release!: () => void;
    const held = new Promise<void>((resolve) => {
      release = resolve;
    });
    server.use(
      http.put(`*/api/v1/admin/products/${row.id}/status`, async () => {
        await held;
      }),
    );
    try {
      await user.click(within(dialog).getByRole("button", { name: "Publish listing" }));
      await waitFor(() =>
        expect(within(dialog).getByRole("button", { name: "Publish listing" })).toBeDisabled(),
      );
      expect(within(dialog).getByRole("button", { name: "Cancel" })).toBeDisabled();
      await user.keyboard("{Escape}");
      expect(onClose).not.toHaveBeenCalled();
    } finally {
      release();
    }
    await waitFor(() => expect(onClose).toHaveBeenCalledOnce());
    expect(row.status).toBe("on_sale");
  });
});
