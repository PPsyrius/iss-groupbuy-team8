import { act, cleanup, render, screen } from "@testing-library/react";
import { Profiler } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { formatCountdown } from "./deal-time";
import { DealProgress } from "./DealProgress";
import { resetPageClockForTests } from "./page-clock";

const now = Date.parse("2026-09-17T11:00:00+08:00");

describe("DealProgress", () => {
  beforeEach(() => {
    resetPageClockForTests();
    vi.useFakeTimers();
    vi.setSystemTime(now);
  });
  afterEach(() => {
    // Unmount while the fake timers are installed, so the page clock clears its own timer.
    cleanup();
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

  it("disables Join without a handler and enables it with one", () => {
    const deal = {
      dealId: "d1",
      currentSize: 3,
      targetSize: 5,
      expireTime: "2026-09-17T05:14:00Z",
    };
    const { rerender } = render(<DealProgress deal={deal} />);
    expect(screen.getByRole("button", { name: /join/i })).toBeDisabled();
    rerender(<DealProgress deal={deal} onJoin={() => {}} />);
    expect(screen.getByRole("button", { name: /join/i })).toBeEnabled();
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

  it("keeps Join disabled on a full bloc and says why, even with a handler", () => {
    render(
      <DealProgress
        deal={{ dealId: "d4", currentSize: 5, targetSize: 5, expireTime: "2026-09-17T05:14:00Z" }}
        onJoin={() => {}}
      />,
    );
    const join = screen.getByRole("button", { name: /join/i });
    expect(join).toBeDisabled();
    expect(join).toHaveAccessibleDescription("This bloc is full");
    expect(screen.getByText("This bloc is full")).toBeVisible();
    expect(screen.getByText("Full")).toBeInTheDocument();
    expect(screen.queryByText("Filling")).not.toBeInTheDocument();
  });

  it("keeps Join disabled on an ended bloc and says why", () => {
    render(
      <DealProgress
        deal={{ dealId: "d5", currentSize: 2, targetSize: 5, expireTime: "2026-09-17T02:00:00Z" }}
        onJoin={() => {}}
      />,
    );
    const join = screen.getByRole("button", { name: /join/i });
    expect(join).toBeDisabled();
    expect(join).toHaveAccessibleDescription("No longer taking members");
  });

  it("keeps Join disabled for a member of the bloc and says why (FB-20)", () => {
    render(
      <DealProgress
        deal={{ dealId: "d1", currentSize: 3, targetSize: 5, expireTime: "2026-09-17T05:14:00Z" }}
        member
      />,
    );
    const join = screen.getByRole("button", { name: /join/i });
    expect(join).toBeDisabled();
    expect(join).toHaveAccessibleDescription("You’re in this bloc");
    expect(screen.getByText("Filling")).toBeInTheDocument();
  });

  it("names the full reason before membership on a full bloc", () => {
    render(
      <DealProgress
        deal={{ dealId: "d4", currentSize: 5, targetSize: 5, expireTime: "2026-09-17T05:14:00Z" }}
        member
      />,
    );
    expect(screen.getByRole("button", { name: /join/i })).toHaveAccessibleDescription(
      "This bloc is full",
    );
  });

  it("gives no reason while the bloc can still be joined", () => {
    render(
      <DealProgress
        deal={{ dealId: "d1", currentSize: 3, targetSize: 5, expireTime: "2026-09-17T05:14:00Z" }}
        onJoin={() => {}}
      />,
    );
    expect(screen.getByRole("button", { name: /join/i })).not.toHaveAccessibleDescription();
  });
});

// The page clock: one timer for every row, and a row redraws only when its text changes (the
// timer's own behaviour is in page-clock.test.tsx).
const dealAt = (aheadMs: number, dealId = "d1") => ({
  dealId,
  currentSize: 3,
  targetSize: 5,
  expireTime: new Date(now + aheadMs).toISOString(),
});

describe("DealProgress clock", () => {
  const second = 1000;
  const minute = 60_000;
  const hour = 3_600_000;
  const tickOneSecond = () => {
    act(() => {
      vi.advanceTimersByTime(second);
    });
  };

  beforeEach(() => {
    resetPageClockForTests();
    vi.useFakeTimers();
    vi.setSystemTime(now);
  });
  afterEach(() => {
    // Unmount while the fake timers are installed, so the page clock clears its own timer.
    cleanup();
    vi.useRealTimers();
  });

  it("runs one timer for every mounted row and clears it when the last one unmounts", () => {
    const rows = Array.from({ length: 25 }, (_, i) =>
      render(<DealProgress deal={dealAt((i + 1) * hour, `d${i}`)} />),
    );
    expect(vi.getTimerCount()).toBe(1);
    for (const row of rows.slice(0, -1)) row.unmount();
    expect(vi.getTimerCount()).toBe(1);
    rows.at(-1)?.unmount();
    expect(vi.getTimerCount()).toBe(0);
  });

  it("redraws a row in its last hour every second and a row hours away only when its text changes", () => {
    const commits = { near: 0, far: 0, over: 0 };
    const counting = (row: keyof typeof commits) => () => {
      commits[row] += 1;
    };
    render(
      <>
        <Profiler id="near" onRender={counting("near")}>
          <DealProgress deal={dealAt(30 * minute, "near")} />
        </Profiler>
        <Profiler id="far" onRender={counting("far")}>
          <DealProgress deal={dealAt(5 * hour + 12 * minute, "far")} />
        </Profiler>
        <Profiler id="over" onRender={counting("over")}>
          <DealProgress deal={dealAt(-3 * minute, "over")} />
        </Profiler>
      </>,
    );
    const before = { ...commits };
    for (let s = 0; s < 120; s += 1) tickOneSecond();
    expect(commits.near - before.near).toBe(120);
    // "5h 12m left" becomes "5h 11m left" a second in, then "5h 10m left" a minute after that.
    expect(commits.far - before.far).toBe(2);
    expect(commits.over - before.over).toBe(0);
    expect(screen.getByText("5h 10m left")).toBeInTheDocument();
    expect(screen.getByText("28:00 left")).toBeInTheDocument();
  });

  it("prints what the real clock prints at every second, for a deadline off the minute", () => {
    const deal = dealAt(3 * hour + 30 * second);
    const deadline = Date.parse(deal.expireTime);
    render(<DealProgress deal={deal} />);
    for (let s = 0; s < 200; s += 1) {
      expect(screen.getByText(formatCountdown(deadline, Date.now()).text)).toBeInTheDocument();
      tickOneSecond();
    }
  });

  it("prints hours and minutes at exactly one hour left and mm:ss one second later", () => {
    render(<DealProgress deal={dealAt(hour)} />);
    expect(screen.getByText("1h 0m left")).toBeInTheDocument();
    expect(screen.getByText("Filling")).toBeInTheDocument();
    tickOneSecond();
    expect(screen.getByText("59:59 left")).toBeInTheDocument();
    expect(screen.getByText("Closing soon")).toBeInTheDocument();
  });

  it("counts down to the deadline, says Ended at zero and stays ended", () => {
    render(<DealProgress deal={dealAt(2 * second)} onJoin={() => {}} />);
    expect(screen.getByText("00:02 left")).toBeInTheDocument();
    tickOneSecond();
    expect(screen.getByText("00:01 left")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /join/i })).toBeEnabled();
    tickOneSecond();
    expect(screen.getAllByText("Ended")).toHaveLength(2);
    expect(screen.getByRole("button", { name: /join/i })).toBeDisabled();
    for (let s = 0; s < 600; s += 1) tickOneSecond();
    expect(screen.getAllByText("Ended")).toHaveLength(2);
    expect(screen.queryByText(/^(\d+h \d+m|\d\d:\d\d) left$/)).not.toBeInTheDocument();
  });

  it("starts ended for a deadline that has already passed", () => {
    render(<DealProgress deal={dealAt(-hour)} />);
    expect(screen.getAllByText("Ended")).toHaveLength(2);
    expect(screen.getByText("Ends today, 10.00 am SGT")).toBeInTheDocument();
  });
});
