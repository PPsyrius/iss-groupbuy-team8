import { screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { MyTeamResponse } from "@/api/generated/types.gen";
import { renderWithRouter } from "@/test/render";

import { BlocRow } from "./BlocRow";

// Only Date is faked: the router and the row's one-second clock keep real timers.
const now = Date.parse("2026-09-17T11:00:00+08:00");
const inMinutes = (m: number) => new Date(now + m * 60_000).toISOString();

const rice: MyTeamResponse = {
  dealId: "0192f0a0-0000-7000-8000-0000000000d1",
  productId: "0b6f2c1e-6b1a-4f0e-9d1c-2a9c3e5f7a11",
  productTitle: "Thai jasmine rice 10 kg",
  productImage: null,
  dealPrice: "71.00",
  currency: "sgd",
  targetSize: 5,
  currentSize: 4,
  status: "open",
  expireTime: inMinutes(5 * 60 + 12),
  successTime: null,
  role: "member",
  joinTime: "2026-09-16T11:46:00Z",
  orderId: null,
};

describe("BlocRow", () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["Date"] });
    vi.setSystemTime(now);
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("renders an active row: title link, price, progress, countdown, paid line, Filling", async () => {
    await renderWithRouter(<BlocRow team={rice} />);
    expect(screen.getByRole("link", { name: "Thai jasmine rice 10 kg" })).toHaveAttribute(
      "href",
      `/products/${rice.productId}`,
    );
    expect(screen.getByText("S$71.00")).toBeVisible();
    expect(screen.getByText("4 of 5 joined")).toBeVisible();
    expect(screen.getByText("1 seat left")).toBeVisible();
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "4");
    expect(screen.getByText("5h 12m left")).toBeVisible();
    expect(screen.getByText("Paid · 16 Sep, 7.46 pm")).toBeVisible();
    expect(screen.getByText("Filling")).toBeVisible();
    expect(screen.queryByText("You started this")).toBeNull();
  });

  it("marks the starter with the inverse pill and counts plural seats", async () => {
    await renderWithRouter(<BlocRow team={{ ...rice, role: "leader", currentSize: 2 }} />);
    expect(screen.getByText("You started this")).toBeVisible();
    expect(screen.getByText("3 seats left")).toBeVisible();
  });

  it("switches to mm:ss and Closing soon under one hour", async () => {
    await renderWithRouter(<BlocRow team={{ ...rice, expireTime: inMinutes(52) }} />);
    expect(screen.getByText("52:00 left")).toBeVisible();
    expect(screen.getByText("Closing soon")).toBeVisible();
  });

  it("shows Ended and no countdown for an open bloc past its deadline", async () => {
    await renderWithRouter(<BlocRow team={{ ...rice, expireTime: inMinutes(-3) }} />);
    expect(screen.getByText("Ended")).toBeVisible();
    expect(screen.queryByText(/left$/)).toBeNull();
  });

  it("renders a completed row with a full bar and the fill time", async () => {
    await renderWithRouter(
      <BlocRow
        team={{ ...rice, status: "succeeded", currentSize: 5, successTime: "2026-09-16T13:30:00Z" }}
      />,
    );
    expect(screen.getByText("Filled")).toBeVisible();
    expect(screen.getByText("Filled 16 Sep, 9.30 pm")).toBeVisible();
    expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "5");
    expect(screen.queryByText(/left$/)).toBeNull();
  });

  it("renders the refunded variant without claiming a refund state", async () => {
    await renderWithRouter(
      <BlocRow
        team={{
          ...rice,
          status: "expired",
          currentSize: 3,
          dealPrice: "19.50",
          expireTime: "2026-09-15T13:32:00Z",
        }}
      />,
    );
    expect(screen.getByText("Closed 15 Sep")).toBeVisible();
    expect(screen.getByText("3 of 5 joined when it closed")).toBeVisible();
    expect(screen.getByText("S$19.50 · refund due")).toBeVisible();
    expect(screen.getByText("Closed")).toBeVisible();
    expect(screen.queryByText(/Refunded/)).toBeNull();
    expect(screen.queryByText(/left$/)).toBeNull();
  });

  it("falls back to Listing removed with no link when the product is gone", async () => {
    await renderWithRouter(<BlocRow team={{ ...rice, productTitle: null, productImage: null }} />);
    expect(screen.getByRole("heading", { name: "Listing removed" })).toBeVisible();
    expect(screen.queryByRole("link")).toBeNull();
  });

  it("draws the product image when there is one", async () => {
    const { container } = await renderWithRouter(
      <BlocRow team={{ ...rice, productImage: "https://example.test/rice.jpg" }} />,
    );
    expect(container.querySelector("img")).toHaveAttribute("src", "https://example.test/rice.jpg");
  });
});
