import { useSyncExternalStore } from "react";

import { type ClockNeed, clockReading } from "./deal-time";

// The page clock. `useSyncExternalStore` calls `subscribe` once for every hook instance, so each
// instance only registers a listener here: the first one starts the page's single timer and the
// last one to unsubscribe clears it, however many rows are mounted (My blocs shows up to 100). The
// timer wakes every listener each second, but a component re-renders only when its own snapshot
// changed. It fires on whole seconds and re-arms from the time it actually fired, not on a 1 s
// interval from whenever the first row mounted: readings are floored to the second, so a tick that
// drifted across a boundary would show one second twice and skip the next.
//
// The time is read once per tick and cached: React calls `getSnapshot` several times per render
// and compares the results, so a snapshot that read `Date.now()` itself could return two values
// across a second (or deadline-minute, or midnight) boundary, and React would warn and re-render.
// Every snapshot derives from `readPageClock()` instead. With no subscriber the cache is filled on
// first read and refreshed when the first subscriber starts the timer. Reading the clock in the
// store keeps `Date.now()` out of render (React Compiler purity rule).
const listeners = new Set<() => void>();
let timer: ReturnType<typeof setTimeout> | undefined;
let current: number | undefined;

export function readPageClock(): number {
  current ??= Date.now();
  return current;
}
const untilNextSecond = (ms: number) => 1000 - (ms % 1000);

function tick() {
  current = Date.now();
  timer = setTimeout(tick, untilNextSecond(current));
  for (const notify of listeners) notify();
}
export function subscribePageClock(onTick: () => void) {
  listeners.add(onTick);
  if (timer === undefined) {
    current = Date.now();
    timer = setTimeout(tick, untilNextSecond(current));
  }
  return () => {
    listeners.delete(onTick);
    if (listeners.size === 0 && timer !== undefined) {
      clearTimeout(timer);
      timer = undefined;
      current = undefined;
    }
  };
}

// Tests only: forget every listener and the pending timer, so a test that counts timers does not
// depend on what an earlier test in the same worker left subscribed. Rows still mounted after this
// unsubscribe harmlessly (their listener is already gone).
export function resetPageClockForTests() {
  if (timer !== undefined) clearTimeout(timer);
  listeners.clear();
  timer = undefined;
  current = undefined;
}

// `useNow()` reads the real clock to the second; `useNow(deadlineMs, need)` reads `clockReading`,
// which holds still while the caller's text cannot change, so a row hours from its deadline, or
// past it, sleeps through the ticks.
export function useNow(deadlineMs?: number | null, need?: ClockNeed) {
  const read = () => clockReading(deadlineMs, readPageClock(), need);
  return useSyncExternalStore(subscribePageClock, read, read);
}
