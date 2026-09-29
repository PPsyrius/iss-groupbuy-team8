import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { QuoteSheet } from "./QuoteSheet";
import type { QuoteState } from "./use-quote";

const now = Date.parse("2026-09-24T10:00:00Z");
const ready = (expiresMs: number): QuoteState => ({
  status: "ready",
  quote: { id: "q-1", amount: 71, currency: "sgd", expiresMs },
});

function renderSheet(state: QuoteState, overrides: Partial<Parameters<typeof QuoteSheet>[0]> = {}) {
  const props = {
    isOpen: true,
    onOpenChange: vi.fn<(open: boolean) => void>(),
    state,
    now,
    isPaying: false,
    onContinue: vi.fn<(quoteId: string) => void>(),
    onRefresh: vi.fn<() => void>(),
    onRetry: vi.fn<() => void>(),
    ...overrides,
  };
  render(<QuoteSheet {...props} />);
  return props;
}

describe("QuoteSheet", () => {
  // The sheet's `now` prop is the route's per-second clock; Continue also re-reads `Date.now()`.
  beforeEach(() => {
    vi.spyOn(Date, "now").mockReturnValue(now);
  });
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("shows the quoted price as the price to pay, with its countdown and the refund promise", async () => {
    const props = renderSheet(ready(now + 14 * 60_000 + 59_000));
    const dialog = await screen.findByRole("dialog", { name: "Your bloc price" });
    expect(dialog).toHaveTextContent("S$71.00");
    expect(screen.getByRole("timer")).toHaveTextContent("14:59 left");
    expect(dialog).toHaveTextContent("Refunded in full");
    expect(dialog).toHaveTextContent("Quote valid for");
    expect(dialog).toHaveTextContent(/This is the price you pay if you continue before it expires/);
    expect(dialog).not.toHaveTextContent(/preview/i);
    await userEvent.click(screen.getByRole("button", { name: "Continue to payment" }));
    expect(props.onContinue).toHaveBeenCalledExactlyOnceWith("q-1");
  });

  it("blocks payment once the quote expires and offers a new price", async () => {
    const props = renderSheet(ready(now - 1));
    expect(await screen.findByText("This price has expired")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Continue to payment" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Get a new price" }));
    expect(props.onRefresh).toHaveBeenCalledOnce();
  });

  it("blocks payment when the server refused the quote and offers a new price", async () => {
    const props = renderSheet(ready(now + 60_000), { isRejected: true });
    expect(await screen.findByText("This price is no longer available")).toBeInTheDocument();
    expect(screen.getByText(/Nothing has been charged/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Continue to payment" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Get a new price" }));
    expect(props.onRefresh).toHaveBeenCalledOnce();
  });

  it("does not start checkout when the quote expired since the last clock tick", async () => {
    // The rendered clock still says 10:00:00.000 but the quote lapsed at 10:00:00.500.
    const props = renderSheet(ready(now + 500));
    const button = await screen.findByRole("button", { name: "Continue to payment" });
    vi.mocked(Date.now).mockReturnValue(now + 900);
    await userEvent.click(button);
    expect(props.onContinue).not.toHaveBeenCalled();
  });

  it("says no price is set yet when the policy is unavailable", async () => {
    const props = renderSheet({ status: "unavailable" });
    expect(
      await screen.findByText(/No bloc price is set for this listing yet/),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Continue to payment" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Close" }));
    expect(props.onOpenChange).toHaveBeenCalledWith(false);
  });

  it("offers a retry after a failed request and says nothing was charged", async () => {
    const props = renderSheet({ status: "error" });
    expect(await screen.findByText(/Nothing has been charged/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(props.onRetry).toHaveBeenCalledOnce();
  });

  it("shows a busy placeholder while the price loads", async () => {
    renderSheet({ status: "loading" });
    expect(await screen.findByText("Getting your bloc price…")).toBeInTheDocument();
  });

  it("keeps the continue button pending while checkout opens", async () => {
    renderSheet(ready(now + 60_000), { isPaying: true });
    const button = await screen.findByRole("button", { name: /Continue to payment/ });
    expect(button).toHaveAttribute("data-pending", "true");
  });
});
