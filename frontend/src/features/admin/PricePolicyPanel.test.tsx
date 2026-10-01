import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";

import { queryClient } from "@/api/query-client";
import type { createAdminStore } from "@/test/admin-msw";
import { server } from "@/test/msw";
import { renderRoute, serveAdmin } from "@/test/render-route";

type Store = ReturnType<typeof createAdminStore>;

const DAY = 86_400_000;
const iso = (ms: number) => new Date(ms).toISOString();
const policyPath = "*/api/v1/products/:productId/team-price-policy";

function rowOf(store: Store, productNo: string) {
  const row = store.rows.find((r) => r.productNo === productNo);
  if (!row) throw new Error(`no seeded row ${productNo}`);
  return row;
}

async function openPanel(store: Store, productNo: string) {
  const row = rowOf(store, productNo);
  renderRoute(`/admin/listings/${row.id}`);
  const panel = await screen.findByRole("region", { name: "Bloc price rule" });
  return { row, panel };
}

const refuse = (status: number, body: Record<string, unknown>) =>
  http.put(policyPath, () => HttpResponse.json(body, { status }));

// The bloc price rule panel on the editor page (admin plan A2-01, artboards 4b.3 and 4b.5).
describe("PricePolicyPanel", () => {
  it("asks for a published listing on a draft, and reads no rule for it", async () => {
    const store = serveAdmin();
    const reads = vi.fn<() => undefined>();
    server.use(http.get(policyPath, reads));
    const { panel } = await openPanel(store, "BB-100472");
    expect(
      within(panel).getByText(
        "Add a rule after you publish. The bloc price must be strictly below the solo price.",
      ),
    ).toBeVisible();
    expect(within(panel).getByRole("button", { name: "Add a rule" })).toBeDisabled();
    expect(reads).not.toHaveBeenCalled();
  });

  it("shows the active rule over the struck solo price (4b.3)", async () => {
    const store = serveAdmin();
    const { row, panel } = await openPanel(store, "BB-100481");
    const rule = store.policyOf(row.id);
    expect(await within(panel).findByText("S$71.00")).toBeVisible();
    expect(within(panel).getByText("S$89.00")).toHaveClass("line-through");
    expect(within(panel).getByText("v3")).toBeVisible();
    expect(within(panel).getByText("Active")).toBeVisible();
    expect(within(panel).getByText(/^Valid .+ → .+ SGT$/)).toBeVisible();
    expect(rule).toBeDefined();
    expect(
      within(panel).getByText("Quotes issued now lock this amount for 15 minutes each."),
    ).toBeVisible();
    expect(within(panel).getByRole("button", { name: "New revision" })).toBeEnabled();
    expect(
      within(panel).getByText(
        "Saving appends a revision. Prices already locked into a quote never change.",
      ),
    ).toBeVisible();
  });

  it("shows an expired rule, never deleted (4b.5)", async () => {
    const store = serveAdmin();
    const { panel } = await openPanel(store, "BB-100480");
    expect(await within(panel).findByText(/^Expired \d{1,2} [A-Z][a-z]{2}$/)).toBeVisible();
    expect(within(panel).getByText("S$32.00")).toBeVisible();
    expect(
      within(panel).getByText(
        "No bloc price is shown on the storefront until a new revision starts. Blocs already filled keep their locked price.",
      ),
    ).toBeVisible();
  });

  it("shows a scheduled rule with its start (§11 D3)", async () => {
    const store = serveAdmin();
    const kopi = rowOf(store, "BB-100477");
    const start = Date.now() + 3 * DAY;
    store.policies.set(kopi.id, {
      productId: kopi.id,
      amount: "9.90",
      currency: "sgd",
      validFrom: iso(start),
      validUntil: iso(start + 10 * DAY),
      version: 1,
    });
    const { panel } = await openPanel(store, "BB-100477");
    expect(await within(panel).findByText(/^Starts \d{1,2} [A-Z][a-z]{2}$/)).toBeVisible();
    expect(
      within(panel).getByText("Buyers see no bloc price until this rule starts."),
    ).toBeVisible();
  });

  it("keeps an off-sale listing's rule on show but closes revisions", async () => {
    const store = serveAdmin();
    const rack = rowOf(store, "BB-100476");
    store.policies.set(rack.id, {
      productId: rack.id,
      amount: "50.00",
      currency: "sgd",
      validFrom: iso(Date.now() - DAY),
      validUntil: iso(Date.now() + DAY),
      version: 2,
    });
    const { panel } = await openPanel(store, "BB-100476");
    expect(await within(panel).findByText("S$50.00")).toBeVisible();
    expect(
      within(panel).getByText("Put the listing back on sale to add a revision."),
    ).toBeVisible();
    expect(within(panel).getByRole("button", { name: "New revision" })).toBeDisabled();
  });

  it("adds a first rule with expectedVersion 0 and shows it active (4b.5 none)", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const { row, panel } = await openPanel(store, "BB-100477");
    expect(await within(panel).findByText("No rule yet")).toBeVisible();
    expect(within(panel).getByText("—")).toBeVisible();
    expect(
      within(panel).getByText(
        "Buyers see the solo price only, and the card shows a quiet “start a bloc” affordance.",
      ),
    ).toBeVisible();
    await user.click(within(panel).getByRole("button", { name: "Add a rule" }));
    const amount = within(panel).getByRole("textbox", { name: "Bloc price" });
    expect(amount).toHaveValue("");
    expect(within(panel).getByRole("group", { name: "Valid from" })).toBeVisible();
    expect(within(panel).getByRole("group", { name: "Valid until" })).toBeVisible();
    await user.type(amount, "9.90");
    await user.click(within(panel).getByRole("button", { name: "Save rule" }));

    expect(await within(panel).findByText("Active")).toBeVisible();
    expect(within(panel).getByText("S$9.90")).toBeVisible();
    expect(within(panel).getByText("v1")).toBeVisible();
    expect(store.policyOf(row.id)).toMatchObject({ amount: "9.90", version: 1 });
  });

  it("revises with the loaded version and closes the form", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const { row, panel } = await openPanel(store, "BB-100481");
    await user.click(await within(panel).findByRole("button", { name: "New revision" }));
    const amount = within(panel).getByRole("textbox", { name: "Bloc price" });
    expect(amount).toHaveValue("71.00");
    await user.clear(amount);
    await user.type(amount, "69.00");
    await user.click(within(panel).getByRole("button", { name: "Save rule" }));
    expect(await within(panel).findByText("S$69.00")).toBeVisible();
    expect(within(panel).getByText("v4")).toBeVisible();
    expect(within(panel).queryByRole("textbox", { name: "Bloc price" })).not.toBeInTheDocument();
    expect(store.policyOf(row.id)).toMatchObject({ amount: "69.00", version: 4 });
  });

  it("cancels a revision without saving", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const { panel } = await openPanel(store, "BB-100481");
    await user.click(await within(panel).findByRole("button", { name: "New revision" }));
    await user.click(within(panel).getByRole("button", { name: "Cancel" }));
    expect(within(panel).queryByRole("textbox", { name: "Bloc price" })).not.toBeInTheDocument();
    expect(within(panel).getByText("v3")).toBeVisible();
  });

  it("refuses a bloc price not below the solo price before sending (4b.4)", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const writes = vi.fn<() => undefined>();
    server.use(http.put(policyPath, writes));
    const { panel } = await openPanel(store, "BB-100481");
    await user.click(await within(panel).findByRole("button", { name: "New revision" }));
    const amount = within(panel).getByRole("textbox", { name: "Bloc price" });
    await user.clear(amount);
    await user.type(amount, "89.00");
    await user.click(within(panel).getByRole("button", { name: "Save rule" }));
    expect(
      await within(panel).findByText("Must be strictly below the solo price of S$89.00."),
    ).toBeVisible();
    expect(writes).not.toHaveBeenCalled();
  });

  it("warns that a later start replaces the active rule now (step 5)", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const { panel } = await openPanel(store, "BB-100481");
    await user.click(await within(panel).findByRole("button", { name: "New revision" }));
    const warning = /^Saving replaces the active rule now\. Buyers see no bloc price until /;
    expect(within(panel).queryByText(warning)).not.toBeInTheDocument();
    const from = within(panel).getByRole("group", { name: "Valid from" });
    const [day] = within(from).getAllByRole("spinbutton", { name: /day/i });
    if (!day) throw new Error("no day segment");
    await user.click(day);
    await user.keyboard("{ArrowUp}");
    expect(await within(panel).findByText(warning)).toBeVisible();
  });

  it("picks the end day from the calendar", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const { panel } = await openPanel(store, "BB-100477");
    await user.click(await within(panel).findByRole("button", { name: "Add a rule" }));
    const until = within(panel).getByRole("group", { name: "Valid until" });
    await user.click(within(until).getByRole("button"));
    const calendar = await screen.findByRole("application", { name: /Valid until/ });
    const [day28] = within(calendar).getAllByText("28");
    if (!day28) throw new Error("no 28th in the calendar");
    await user.click(day28);
    const [daySegment] = within(until).getAllByRole("spinbutton", { name: /day/i });
    await waitFor(() => expect(daySegment).toHaveAttribute("aria-valuenow", "28"));
  });

  it("turns a stale revision into the conflict state; Reload shows the server's rule", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const { panel } = await openPanel(store, "BB-100481");
    await user.click(await within(panel).findByRole("button", { name: "New revision" }));
    const amount = within(panel).getByRole("textbox", { name: "Bloc price" });
    await user.clear(amount);
    await user.type(amount, "69.00");
    store.conflictOnNextWrite();
    await user.click(within(panel).getByRole("button", { name: "Save rule" }));

    expect(await within(panel).findByText("Not saved")).toBeVisible();
    expect(within(panel).getByText("You loaded v3; the server now has v4")).toBeVisible();
    expect(within(panel).getByText("v3 → v4")).toBeVisible();
    expect(within(panel).getByText("S$69.00")).toBeVisible();
    expect(
      within(panel).getByText(
        "Someone else added a revision while this panel was open. Reload to see it — revisions append, so nothing you saved was lost.",
      ),
    ).toBeVisible();
    await user.click(within(panel).getByRole("button", { name: "Reload" }));
    expect(await within(panel).findByText("v4")).toBeVisible();
    expect(within(panel).getByText("S$71.00")).toBeVisible();
    expect(within(panel).queryByText("Not saved")).not.toBeInTheDocument();
  });

  it("asks the server for its version when a 412 does not name it", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    server.use(refuse(412, { code: "CATALOG_412_001", message: "stale" }));
    const { panel } = await openPanel(store, "BB-100481");
    await user.click(await within(panel).findByRole("button", { name: "New revision" }));
    await user.click(within(panel).getByRole("button", { name: "Save rule" }));
    expect(await within(panel).findByText("You loaded v3; the server now has v3")).toBeVisible();
  });

  it("says when Reload fails, and lets it be tried again", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const { row, panel } = await openPanel(store, "BB-100481");
    await user.click(await within(panel).findByRole("button", { name: "New revision" }));
    store.conflictOnNextWrite();
    await user.click(within(panel).getByRole("button", { name: "Save rule" }));
    await within(panel).findByText("Not saved");
    server.use(
      http.get(policyPath, () => HttpResponse.json({ code: "X", message: "x" }, { status: 404 }), {
        once: true,
      }),
    );
    await user.click(within(panel).getByRole("button", { name: "Reload" }));
    expect(await within(panel).findByText("We couldn’t reload the rule. Try again.")).toBeVisible();
    await user.click(within(panel).getByRole("button", { name: "Reload" }));
    await waitFor(() => expect(within(panel).queryByText("Not saved")).not.toBeInTheDocument());
    expect(within(panel).getByText(`v${store.policyOf(row.id)?.version}`)).toBeVisible();
  });

  it("shows Access denied when Reload is refused", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const user = userEvent.setup();
    const store = serveAdmin();
    const { panel } = await openPanel(store, "BB-100481");
    await user.click(await within(panel).findByRole("button", { name: "New revision" }));
    store.conflictOnNextWrite();
    await user.click(within(panel).getByRole("button", { name: "Save rule" }));
    await within(panel).findByText("Not saved");
    server.use(forbiddenRuleRead());
    await user.click(within(panel).getByRole("button", { name: "Reload" }));
    expect(
      await screen.findByRole("heading", { name: "You don’t have access to the admin console" }),
    ).toBeVisible();
  });

  it("shows Access denied when the conflict's version read is refused", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const user = userEvent.setup();
    const store = serveAdmin();
    const { panel } = await openPanel(store, "BB-100481");
    await user.click(await within(panel).findByRole("button", { name: "New revision" }));
    server.use(refuse(412, { code: "CATALOG_412_001", message: "stale" }), forbiddenRuleRead());
    await user.click(within(panel).getByRole("button", { name: "Save rule" }));
    expect(
      await screen.findByRole("heading", { name: "You don’t have access to the admin console" }),
    ).toBeVisible();
  });

  it("turns a 409 into the unavailable state; Reload reads the listing again", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    server.use(refuse(409, { code: "CATALOG_409_001", message: "Team price is unavailable" }));
    const { row, panel } = await openPanel(store, "BB-100481");
    await user.click(await within(panel).findByRole("button", { name: "New revision" }));
    await user.click(within(panel).getByRole("button", { name: "Save rule" }));
    expect(
      await within(panel).findByText(
        "This listing changed state and can’t take a rule now. Reload to see what you can do.",
      ),
    ).toBeVisible();
    // Meanwhile another admin took it off sale.
    row.status = "off_sale";
    row.version += 1;
    await user.click(within(panel).getByRole("button", { name: "Reload" }));
    expect(
      await within(panel).findByText("Put the listing back on sale to add a revision."),
    ).toBeVisible();
  });

  it("puts a domain 400's field paths on the fields and the rest in the alert (BA-6, #323)", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    server.use(
      refuse(400, {
        code: "CATALOG_400_001",
        message: "Invalid team price policy",
        errors: [
          { field: "validUntil", message: "Must be in the future" },
          { field: "currency", message: "Must match the listing currency" },
        ],
      }),
    );
    const { panel } = await openPanel(store, "BB-100481");
    await user.click(await within(panel).findByRole("button", { name: "New revision" }));
    await user.click(within(panel).getByRole("button", { name: "Save rule" }));
    const until = within(panel).getByRole("group", { name: "Valid until" });
    expect(await within(panel).findByText("Must be in the future")).toBeVisible();
    expect(until).toHaveAttribute("data-invalid", "true");
    const alert = within(panel).getByRole("alert");
    expect(within(alert).getByText("Some fields need another look")).toBeVisible();
    expect(within(alert).getByText("Must match the listing currency")).toBeVisible();
  });

  it("clears a server message once its field changes", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    server.use(
      refuse(400, {
        code: "CATALOG_400_001",
        message: "Invalid team price policy",
        errors: [{ field: "amount", message: "Must be strictly below the solo price" }],
      }),
    );
    const { panel } = await openPanel(store, "BB-100481");
    await user.click(await within(panel).findByRole("button", { name: "New revision" }));
    await user.click(within(panel).getByRole("button", { name: "Save rule" }));
    expect(await within(panel).findByText("Must be strictly below the solo price")).toBeVisible();
    await user.type(within(panel).getByRole("textbox", { name: "Bloc price" }), "0");
    expect(
      within(panel).queryByText("Must be strictly below the solo price"),
    ).not.toBeInTheDocument();
  });

  it("keeps a 400 without field paths as a form alert", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    server.use(refuse(400, { code: "CATALOG_400_001", message: "Invalid team price policy" }));
    const { panel } = await openPanel(store, "BB-100481");
    await user.click(await within(panel).findByRole("button", { name: "New revision" }));
    await user.click(within(panel).getByRole("button", { name: "Save rule" }));
    const alert = await within(panel).findByRole("alert");
    expect(within(alert).getByText("The rule wasn’t saved")).toBeVisible();
    expect(
      within(alert).getByText("The server refused this rule. Check the fields and try again."),
    ).toBeVisible();
  });

  it("keeps the form and links to sign-in when the session ended", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    server.use(refuse(401, { code: "AUTH_401", message: "unauthenticated" }));
    const { panel } = await openPanel(store, "BB-100481");
    await user.click(await within(panel).findByRole("button", { name: "New revision" }));
    await user.click(within(panel).getByRole("button", { name: "Save rule" }));
    const alert = await within(panel).findByRole("alert");
    expect(within(alert).getByText("Your session ended. Sign in again to save.")).toBeVisible();
    expect(within(alert).getByRole("link", { name: "Sign in" })).toBeVisible();
    expect(within(panel).getByRole("textbox", { name: "Bloc price" })).toHaveValue("71.00");
  });

  it("shows Access denied when a rule write answers 403", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    server.use(refuse(403, { code: "COMMON_403_001", message: "Permission denied" }));
    const { panel } = await openPanel(store, "BB-100481");
    await user.click(await within(panel).findByRole("button", { name: "New revision" }));
    await user.click(within(panel).getByRole("button", { name: "Save rule" }));
    expect(
      await screen.findByRole("heading", { name: "You don’t have access to the admin console" }),
    ).toBeVisible();
  });

  it("shows Access denied when the rule read answers 403", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const store = serveAdmin();
    await openPanel(store, "BB-100481");
    server.use(
      http.get(policyPath, () =>
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

  it("says when the rule cannot be read, and reads it again on Reload", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    server.use(
      http.get(policyPath, () => HttpResponse.json({ code: "X", message: "x" }, { status: 404 }), {
        once: true,
      }),
    );
    const { panel } = await openPanel(store, "BB-100481");
    expect(await within(panel).findByText("We couldn’t load the bloc price rule.")).toBeVisible();
    await user.click(within(panel).getByRole("button", { name: "Reload" }));
    expect(await within(panel).findByText("S$71.00")).toBeVisible();
  });
});

function forbiddenRuleRead() {
  return http.get(policyPath, () =>
    HttpResponse.json({ code: "COMMON_403_001", message: "Permission denied" }, { status: 403 }),
  );
}
