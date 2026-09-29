import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { installAuthInterceptor } from "@/api/client";
import { OK_CODE } from "@/api/envelope";
import { server } from "@/test/msw";

import { ShareBlock } from "./ShareBlock";

// Non-2xx envelopes reach the component as `ApiError` only through the production interceptor.
installAuthInterceptor(new QueryClient());

const dealId = "5b1b9d5c-1a11-4e1e-9a10-0000000000d1";
const productId = "5b1b9d5c-1a11-4e1e-9a10-000000000001";
const ref = "Qm9vbS1pbnZpdGUtcmVm_-";
const link = `${window.location.origin}/products/${productId}?deal=${dealId}&ref=${ref}`;

let marker: string | null = null;
let mints = 0;

function serveInvite(answer?: () => Response) {
  server.use(
    http.post("*/api/v1/group-deals/:dealId/invites", ({ request }) => {
      mints++;
      marker = request.headers.get("X-BlocBuy-Request");
      return (
        answer?.() ??
        HttpResponse.json({
          code: OK_CODE,
          message: "ok",
          data: { ref, dealId, productId, expiresAt: "2099-01-01T00:00:00Z" },
        })
      );
    }),
  );
}

function renderBlock(overrides: Partial<Parameters<typeof ShareBlock>[0]> = {}) {
  const qc = new QueryClient();
  return render(
    <QueryClientProvider client={qc}>
      <ShareBlock
        userId="u1"
        dealId={dealId}
        productId={productId}
        leader
        title="Kopi powder 500 g"
        groupPrice={9.9}
        individualPrice={12.8}
        currency="sgd"
        seatsLeft={3}
        closesMs={null}
        {...overrides}
      />
    </QueryClientProvider>,
  );
}

function stubClipboard(writeText: (text: string) => Promise<void>) {
  Object.defineProperty(navigator, "clipboard", { configurable: true, value: { writeText } });
}

beforeEach(() => {
  marker = null;
  mints = 0;
});

afterEach(() => {
  Reflect.deleteProperty(navigator, "share");
  vi.restoreAllMocks();
});

describe("ShareBlock", () => {
  it("mints the invite once with the invite marker and shows the link and message", async () => {
    serveInvite();
    renderBlock();
    const field = await screen.findByRole("textbox", { name: "Invite link" });
    expect(field).toHaveValue(link.replace(/^https?:\/\//, ""));
    expect(marker).toBe("invite");
    expect(mints).toBe(1);
    expect(screen.getByRole("heading", { name: "Share your bloc" })).toBeInTheDocument();
    expect(screen.getByText("3 seats to go.")).toBeInTheDocument();
    expect(
      screen.getByText(/Getting Kopi powder 500 g at S\$9.90 instead of S\$12.80/),
    ).toHaveTextContent(link);
  });

  it("shows the member view's wording to a member", async () => {
    serveInvite();
    renderBlock({ leader: false, seatsLeft: 1 });
    expect(await screen.findByRole("heading", { name: "Help fill the last seat" })).toBeVisible();
    expect(screen.getByText("Anyone in this bloc can share the link.")).toBeInTheDocument();
  });

  it("copies the full link and confirms it", async () => {
    serveInvite();
    const writeText = vi.fn<(text: string) => Promise<void>>(() => Promise.resolve());
    renderBlock();
    await screen.findByRole("textbox", { name: "Invite link" });
    stubClipboard(writeText);
    await userEvent.click(screen.getByRole("button", { name: "Copy" }));
    expect(writeText).toHaveBeenCalledWith(link);
    expect(await screen.findByText("Invite link copied")).toBeInTheDocument();
  });

  it("selects the link for a manual copy when the clipboard refuses", async () => {
    serveInvite();
    renderBlock();
    const field = await screen.findByRole("textbox", { name: "Invite link" });
    stubClipboard(() => Promise.reject(new DOMException("denied", "NotAllowedError")));
    const select = vi.spyOn(HTMLInputElement.prototype, "select");
    await userEvent.click(screen.getByRole("button", { name: "Copy" }));
    expect(await screen.findByText(/Copy didn’t work here/)).toBeInTheDocument();
    expect(select.mock.contexts).toContain(field);
  });

  it("offers Share only where the Web Share API exists, and ignores a dismissed sheet", async () => {
    serveInvite();
    const { unmount } = renderBlock();
    await screen.findByRole("textbox", { name: "Invite link" });
    expect(screen.queryByRole("button", { name: "Share" })).toBeNull();
    unmount();

    const share = vi.fn<(data: ShareData) => Promise<void>>(() =>
      Promise.reject(new DOMException("dismissed", "AbortError")),
    );
    Object.defineProperty(navigator, "share", { configurable: true, value: share });
    const writeText = vi.fn<(text: string) => Promise<void>>(() => Promise.resolve());
    renderBlock();
    await screen.findByRole("textbox", { name: "Invite link" });
    stubClipboard(writeText);
    await userEvent.click(screen.getByRole("button", { name: "Share" }));
    expect(share).toHaveBeenCalledOnce();
    expect(share.mock.calls[0]?.[0].title).toBe("Kopi powder 500 g");
    // The link rides in `url`, which some targets read instead of the text; not in both.
    expect(share.mock.calls[0]?.[0].url).toBe(link);
    expect(share.mock.calls[0]?.[0].text).toMatch(/^Getting Kopi powder 500 g/);
    expect(share.mock.calls[0]?.[0].text).not.toContain(link);
    expect(writeText).not.toHaveBeenCalled();
  });

  it("falls back to copying when sharing fails for another reason", async () => {
    serveInvite();
    Object.defineProperty(navigator, "share", {
      configurable: true,
      value: () => Promise.reject(new TypeError("not allowed")),
    });
    const writeText = vi.fn<(text: string) => Promise<void>>(() => Promise.resolve());
    renderBlock();
    await screen.findByRole("textbox", { name: "Invite link" });
    stubClipboard(writeText);
    await userEvent.click(screen.getByRole("button", { name: "Share" }));
    await waitFor(() => expect(writeText).toHaveBeenCalledWith(link));
  });

  it("says a closed bloc has nothing to share, without a retry", async () => {
    serveInvite(() =>
      HttpResponse.json(
        { code: "GROUPBUY_409_002", message: "Group deal is not accepting members" },
        { status: 409 },
      ),
    );
    renderBlock();
    expect(await screen.findByRole("alert")).toHaveTextContent(/no longer taking members/);
    expect(screen.queryByRole("button", { name: "Try again" })).toBeNull();
    expect(mints).toBe(1);
  });

  it("says only members can share when the server does not count the buyer in", async () => {
    serveInvite(() =>
      HttpResponse.json(
        { code: "GROUPBUY_404_001", message: "Group deal not found" },
        { status: 404 },
      ),
    );
    renderBlock();
    expect(await screen.findByRole("alert")).toHaveTextContent(/Only people in this bloc/);
  });

  it("retries after an unexpected failure", async () => {
    let fail = true;
    serveInvite(() =>
      fail
        ? HttpResponse.json({ code: "COMMON_500_000", message: "boom" }, { status: 500 })
        : HttpResponse.json({
            code: OK_CODE,
            message: "ok",
            data: { ref, dealId, productId, expiresAt: "2099-01-01T00:00:00Z" },
          }),
    );
    renderBlock();
    expect(await screen.findByRole("alert")).toHaveTextContent(/Nothing has been charged/);
    fail = false;
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByRole("textbox", { name: "Invite link" })).toBeInTheDocument();
    expect(mints).toBe(2);
  });
});
