import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { describe, expect, it, vi } from "vitest";

import type { createAdminStore } from "@/test/admin-msw";
import { server } from "@/test/msw";
import { renderWithRouter } from "@/test/render";
import { renderRoute, serveAdmin } from "@/test/render-route";

import { TeamTermsPanel } from "./TeamTermsPanel";

type Store = ReturnType<typeof createAdminStore>;

const termsPath = "*/api/v1/admin/products/:productId/team-terms";

function rowOf(store: Store, productNo: string) {
  const row = store.rows.find((r) => r.productNo === productNo);
  if (!row) throw new Error(`no seeded row ${productNo}`);
  return row;
}

async function openPanel(store: Store, productNo: string) {
  const row = rowOf(store, productNo);
  const { router } = renderRoute(`/admin/listings/${row.id}`);
  const panel = await screen.findByRole("region", { name: "Group terms" });
  return { row, panel, router };
}

const field = (panel: HTMLElement, name: string) => within(panel).getByRole("textbox", { name });

// The group terms panel on the editor page (admin plan A3-04, §11 D6): a card under the bloc price
// rule panel with its own save through `configureTeamTerms`.
describe("TeamTermsPanel", () => {
  it("can save terms even when the listing is not in the query cache", async () => {
    serveAdmin();
    server.use(http.put(termsPath, () => HttpResponse.json({ code: "COMMON_200_000", data: {} })));
    const onConflict = vi.fn<(version: number | null) => void>();
    await renderWithRouter(
      <TeamTermsPanel
        productId="p1"
        version={0}
        terms={{ configured: true, targetSize: 5, lifetimeSeconds: 7200 }}
        reloads={0}
        isDisabled={false}
        listingDirty={false}
        onConflict={onConflict}
      />,
    );
    await userEvent.click(screen.getByRole("button", { name: "Save terms" }));
    expect(await screen.findByText("Terms saved.")).toBeVisible();
    expect(screen.getByRole("textbox", { name: "Seats per bloc" })).toHaveValue("5");
  });
  it("shows configured terms as ready", async () => {
    const store = serveAdmin();
    const { panel } = await openPanel(store, "BB-100481");
    expect(within(panel).getByText("Ready to start blocs")).toBeVisible();
    expect(field(panel, "Seats per bloc")).toHaveValue("5");
    expect(field(panel, "Hours")).toHaveValue("2");
    expect(field(panel, "Minutes")).toHaveValue("0");
    expect(
      within(panel).getByText("New blocs use these terms. Blocs already started keep theirs."),
    ).toBeVisible();
    expect(within(panel).getByRole("button", { name: "Save terms" })).toBeEnabled();
  });

  it("sets a draft's first terms with the listing version and no team price", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const sent = vi.fn<(body: unknown) => void>();
    const listen = ({ request }: { request: Request }) => {
      if (request.method === "PUT" && request.url.endsWith("/team-terms"))
        void request
          .clone()
          .json()
          .then((body) => sent(body));
    };
    server.events.on("request:start", listen);
    // Removed even when an assertion fails, so no later test hears this one's requests.
    try {
      const { row, panel } = await openPanel(store, "BB-100472");
      expect(within(panel).getByText("Not set")).toBeVisible();
      expect(
        within(panel).getByText("Buyers can’t start a bloc on this listing until both are set."),
      ).toBeVisible();
      expect(field(panel, "Seats per bloc")).toHaveValue("");
      expect(field(panel, "Hours")).toHaveValue("");

      await user.type(field(panel, "Seats per bloc"), "4");
      await user.type(field(panel, "Hours"), "0");
      await user.clear(field(panel, "Minutes"));
      await user.type(field(panel, "Minutes"), "40");
      await user.click(within(panel).getByRole("button", { name: "Save terms" }));

      expect(await within(panel).findByText("Ready to start blocs")).toBeVisible();
      expect(within(panel).getByText("Terms saved.")).toBeVisible();
      expect(sent).toHaveBeenCalledWith({
        expectedVersion: 0,
        targetSize: 4,
        lifetimeSeconds: 2400,
      });
      expect(store.termsOf(row.id)).toEqual({ targetSize: 4, lifetimeSeconds: 2400 });
      // The write bumped the listing version, which the editor shows and its next save sends.
      expect(await screen.findByText("BB-100472 · draft · v1")).toBeVisible();
      expect(field(panel, "Minutes")).toHaveValue("40");
    } finally {
      server.events.removeListener("request:start", listen);
    }
  });

  it("checks the terms before sending", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const puts = vi.fn<() => undefined>();
    server.use(http.put(termsPath, puts));
    const { panel } = await openPanel(store, "BB-100481");
    await user.clear(field(panel, "Seats per bloc"));
    await user.type(field(panel, "Seats per bloc"), "1");
    await user.clear(field(panel, "Hours"));
    await user.type(field(panel, "Hours"), "0");
    await user.type(field(panel, "Minutes"), "30");
    await user.click(within(panel).getByRole("button", { name: "Save terms" }));

    expect(await within(panel).findByText("Enter at least 2 seats.")).toBeVisible();
    expect(within(panel).getByText("A bloc stays open from 40 minutes to 24 hours.")).toBeVisible();
    expect(puts).not.toHaveBeenCalled();
  });

  it("puts the backend's field errors on the fields", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    server.use(
      http.put(termsPath, () =>
        HttpResponse.json(
          {
            code: "COMMON_400_002",
            message: "Request validation failed",
            errors: [{ field: "targetSize", message: "must be greater than or equal to 2" }],
          },
          { status: 400 },
        ),
      ),
    );
    const { panel } = await openPanel(store, "BB-100481");
    await user.clear(field(panel, "Seats per bloc"));
    await user.type(field(panel, "Seats per bloc"), "6");
    await user.click(within(panel).getByRole("button", { name: "Save terms" }));
    expect(await within(panel).findByText("must be greater than or equal to 2")).toBeVisible();
    expect(within(panel).getByText("Ready to start blocs")).toBeVisible();
    await user.type(field(panel, "Seats per bloc"), "0");
    await user.tab();
    await waitFor(() =>
      expect(
        within(panel).queryByText("must be greater than or equal to 2"),
      ).not.toBeInTheDocument(),
    );
  });

  it("shows an unknown server field in the form alert", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    server.use(
      http.put(termsPath, () =>
        HttpResponse.json(
          {
            code: "COMMON_400_002",
            errors: [{ field: "futureField", message: "Reload the terms" }],
          },
          { status: 400 },
        ),
      ),
    );
    const { panel } = await openPanel(store, "BB-100481");
    await user.click(within(panel).getByRole("button", { name: "Save terms" }));
    expect(await within(panel).findByRole("alert")).toHaveTextContent("Reload the terms");
  });

  it("retains cached version and terms when the save response omits them", async () => {
    const store = serveAdmin();
    server.use(http.put(termsPath, () => HttpResponse.json({ code: "COMMON_200_000", data: {} })));
    const { panel } = await openPanel(store, "BB-100481");
    await userEvent.click(within(panel).getByRole("button", { name: "Save terms" }));
    expect(await within(panel).findByText("Terms saved.")).toBeVisible();
    expect(screen.getByText("BB-100481 · on sale · v4 · 142 sold")).toBeVisible();
    expect(field(panel, "Seats per bloc")).toHaveValue("5");
  });

  it("hands a revoked admin role to the route boundary", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    const store = serveAdmin();
    server.use(
      http.put(termsPath, () => HttpResponse.json({ code: "COMMON_403_001" }, { status: 403 })),
    );
    const { panel } = await openPanel(store, "BB-100481");
    await userEvent.click(within(panel).getByRole("button", { name: "Save terms" }));
    expect(
      await screen.findByRole("heading", { name: "You don’t have access to the admin console" }),
    ).toBeVisible();
  });

  it("turns a stale version into the editor's conflict banner and closes every write", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const { row, panel } = await openPanel(store, "BB-100481");
    row.version = 5;
    await user.clear(field(panel, "Seats per bloc"));
    await user.type(field(panel, "Seats per bloc"), "6");
    await user.click(within(panel).getByRole("button", { name: "Save terms" }));

    expect(await screen.findByText("This listing was updated by someone else")).toBeVisible();
    expect(
      screen.getByText(
        "You loaded v4; the server now has v5. Reload to see the latest version — your unsaved changes will be lost.",
      ),
    ).toBeVisible();
    expect(within(panel).getByRole("button", { name: "Save terms" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Save changes" })).toBeDisabled();
    expect(store.termsOf(row.id)).toEqual({ targetSize: 5, lifetimeSeconds: 7200 });

    // Reload drops the unsaved terms with the rest, as the banner says.
    await user.click(screen.getByRole("button", { name: "Reload" }));
    expect(await screen.findByText("BB-100481 · on sale · v5 · 142 sold")).toBeVisible();
    expect(field(panel, "Seats per bloc")).toHaveValue("5");
    expect(within(panel).getByRole("button", { name: "Save terms" })).toBeEnabled();
  });

  it("waits for unsaved listing edits, whose form a terms save would reload", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const { panel } = await openPanel(store, "BB-100481");
    await user.clear(field(panel, "Seats per bloc"));
    await user.type(field(panel, "Seats per bloc"), "6");
    expect(within(panel).getByRole("button", { name: "Save terms" })).toBeEnabled();
    await user.type(screen.getByRole("textbox", { name: "Subtitle" }), "!");
    expect(within(panel).getByRole("button", { name: "Save terms" })).toBeDisabled();
    expect(within(panel).getByText("Save your listing changes first.")).toBeVisible();
  });

  it("keeps unsaved terms when the listing is saved", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const { panel } = await openPanel(store, "BB-100481");
    await user.clear(field(panel, "Seats per bloc"));
    await user.type(field(panel, "Seats per bloc"), "6");
    await user.type(screen.getByRole("textbox", { name: "Subtitle" }), "!");
    await user.click(screen.getByRole("button", { name: "Save changes" }));
    expect(await screen.findByText("BB-100481 · on sale · v5 · 142 sold")).toBeVisible();
    expect(field(panel, "Seats per bloc")).toHaveValue("6");
    await user.click(within(panel).getByRole("button", { name: "Save terms" }));
    expect(await screen.findByText("BB-100481 · on sale · v6 · 142 sold")).toBeVisible();
  });

  it("asks before leaving with unsaved terms, as the listing form does", async () => {
    const user = userEvent.setup();
    const store = serveAdmin();
    const { row, panel, router } = await openPanel(store, "BB-100476");
    await user.type(field(panel, "Seats per bloc"), "3");
    const crumb = screen.getByRole("navigation", { name: "Breadcrumb" });
    await user.click(within(crumb).getByRole("link", { name: "Listings" }));
    const dialog = await screen.findByRole("alertdialog");
    expect(within(dialog).getByRole("heading", { name: "Discard your changes?" })).toBeVisible();
    await user.click(within(dialog).getByRole("button", { name: "Keep editing" }));
    await waitFor(() => expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument());
    expect(router.state.location.pathname).toBe(`/admin/listings/${row.id}`);
    expect(field(panel, "Seats per bloc")).toHaveValue("3");
  });
});
