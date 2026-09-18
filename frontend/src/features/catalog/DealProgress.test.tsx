import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { DealProgress } from "./DealProgress";

const now = Date.parse("2026-09-17T11:00:00+08:00");

describe("DealProgress", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(now);
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("renders the joined count, a 60 % progress bar and the seats left", () => {
    const { container } = render(
      <DealProgress
        deal={{ dealId: "d1", currentSize: 3, targetSize: 5, expireTime: "2026-09-17T05:14:00Z" }}
      />,
    );
    expect(screen.getByText("3 of 5 joined")).toBeInTheDocument();
    const bar = screen.getByRole("progressbar");
    expect(bar).toHaveAttribute("aria-valuenow", "3");
    expect(bar).toHaveAttribute("aria-valuemax", "5");
    expect(container.querySelector('[data-slot="progress-bar-fill"]')).toHaveStyle({
      width: "60%",
    });
    expect(screen.getByText("2 seats left")).toBeInTheDocument();
  });

  it("shows the live countdown, the absolute deadline and a Filling chip", () => {
    render(
      <DealProgress
        deal={{ dealId: "d1", currentSize: 3, targetSize: 5, expireTime: "2026-09-17T05:14:00Z" }}
      />,
    );
    expect(screen.getByText("2h 14m left")).toBeInTheDocument();
    expect(screen.getByText("Ends today, 1.14 pm SGT")).toBeInTheDocument();
    expect(screen.getByText("Filling")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /join/i })).toBeInTheDocument();
  });

  it("switches to Closing soon and mm:ss under one hour, and bolds the last seat", () => {
    render(
      <DealProgress
        deal={{ dealId: "d2", currentSize: 4, targetSize: 5, expireTime: "2026-09-17T03:47:25Z" }}
      />,
    );
    expect(screen.getByText("47:25 left")).toBeInTheDocument();
    expect(screen.getByText("Closing soon")).toBeInTheDocument();
    expect(screen.getByText("1 seat left")).toHaveClass("font-semibold");
  });

  it("ticks every second", () => {
    render(
      <DealProgress
        deal={{ dealId: "d2", currentSize: 4, targetSize: 5, expireTime: "2026-09-17T03:47:25Z" }}
      />,
    );
    act(() => {
      vi.advanceTimersByTime(1000);
    });
    expect(screen.getByText("47:24 left")).toBeInTheDocument();
  });

  it("omits the countdown and deadline when expireTime cannot be parsed", () => {
    render(
      <DealProgress deal={{ dealId: "d3", currentSize: 2, targetSize: 5, expireTime: "soon" }} />,
    );
    expect(screen.getByText("2 of 5 joined")).toBeInTheDocument();
    expect(screen.getByText("Filling")).toBeInTheDocument();
    expect(screen.queryByText(/(\dh \d+m|\d\d:\d\d) left$/)).not.toBeInTheDocument();
    expect(screen.queryByText(/^Ends /)).not.toBeInTheDocument();
  });

  it("renders the no-bloc copy when there is no deal", () => {
    render(<DealProgress deal={undefined} />);
    expect(screen.getByText("Buying alone. No bloc is open yet.")).toBeInTheDocument();
    expect(screen.getByText("Be the first to start a bloc")).toBeInTheDocument();
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
  });
});
