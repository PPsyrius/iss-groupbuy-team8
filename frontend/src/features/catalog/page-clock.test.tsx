import { act, cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { readPageClock, resetPageClockForTests, useNow } from "./page-clock";

const now = Date.parse("2026-09-17T11:00:00+08:00");

function Probe({ id = "probe" }: Readonly<{ id?: string }>) {
  return <output data-testid={id}>{useNow()}</output>;
}
const shown = (id = "probe") => Number(screen.getByTestId(id).textContent);
const advance = (ms: number) => {
  act(() => {
    vi.advanceTimersByTime(ms);
  });
};

describe("page clock", () => {
  beforeEach(() => {
    resetPageClockForTests();
    vi.useFakeTimers();
    vi.setSystemTime(now);
  });
  afterEach(() => {
    // Unmount while the fake timers are still installed, so the store clears its own timer.
    cleanup();
    vi.useRealTimers();
  });

  it("runs one timer for every subscriber and clears it when the last one unmounts", () => {
    const cleared = vi.spyOn(globalThis, "clearTimeout");
    const rows = Array.from({ length: 25 }, (_, i) => render(<Probe id={`p${i}`} />));
    expect(vi.getTimerCount()).toBe(1);
    advance(5000);
    expect(vi.getTimerCount()).toBe(1);
    expect(shown("p0")).toBe(now + 5000);

    for (const row of rows.slice(0, -1)) row.unmount();
    expect(cleared).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(1);

    rows.at(-1)?.unmount();
    expect(cleared).toHaveBeenCalledTimes(1);
    expect(vi.getTimerCount()).toBe(0);

    // A subscriber mounted later starts a fresh timer.
    render(<Probe />);
    expect(vi.getTimerCount()).toBe(1);
  });

  it("ticks on whole seconds whenever the first subscriber mounts", () => {
    vi.setSystemTime(now + 990);
    render(<Probe />);
    expect(shown()).toBe(now);
    advance(9);
    expect(shown()).toBe(now);
    advance(1);
    expect(shown()).toBe(now + 1000);
    advance(999);
    expect(shown()).toBe(now + 1000);
    advance(1);
    expect(shown()).toBe(now + 2000);
  });

  it("re-arms from when a late tick fired, so the next one lands back on the second", () => {
    render(<Probe />);
    // The tick due at +1000 runs 300 ms late (a busy main thread); the one after is still due at
    // +2000, not +2300, so no second is shown twice or skipped.
    vi.setSystemTime(now + 300);
    advance(1000);
    expect(shown()).toBe(now + 1000);
    expect(vi.getTimerCount()).toBe(1);
    advance(699);
    expect(shown()).toBe(now + 1000);
    advance(1);
    expect(shown()).toBe(now + 2000);
  });

  it("returns the same reading between ticks however often it is read", () => {
    render(<Probe />);
    // The wall clock moves past a second boundary, but no tick has fired yet.
    vi.setSystemTime(now + 1500);
    expect(readPageClock()).toBe(now);
    expect(readPageClock()).toBe(now);
    // The next tick reads the clock once, and every read after it returns that time.
    advance(1000);
    expect(readPageClock()).toBe(now + 2500);
    expect(readPageClock()).toBe(now + 2500);
  });
});
