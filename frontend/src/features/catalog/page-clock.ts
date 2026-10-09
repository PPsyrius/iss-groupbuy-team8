import { useSyncExternalStore } from "react";

import { clockReading } from "./deal-time";

// The page clock. `useSyncExternalStore` calls `subscribe` once for every hook instance, so each
// instance only registers a listener here: the first one starts the page's single 1 s interval and
// the last one to unsubscribe clears it, however many rows are mounted (My blocs shows up to 100).
// The interval wakes every listener each second, but a component re-renders only when its own
// snapshot changed.
//
// The time is read once per tick and cached: React calls `getSnapshot` several times per render
// and compares the results, so a snapshot that read `Date.now()` itself could return two values
// across a second (or deadline-minute, or midnight) boundary, and React would warn and re-render.
// Every snapshot derives from `readPageClock()` instead. With no subscriber the cache is filled on
// first read and refreshed when the first subscriber starts the timer. Reading the clock in the
// store keeps `Date.now()` out of render (React Compiler purity rule).
const listeners = new Set<() => void>();
let timer: ReturnType<typeof setInterval> | undefined;
let current: number | undefined;

export function readPageClock(): number {
  current ??= Date.now();
  return current;
}
function tick() {
  current = Date.now();
  for (const notify of listeners) notify();
}
export function subscribePageClock(onTick: () => void) {
  listeners.add(onTick);
  if (timer === undefined) {
    current = Date.now();
    timer = setInterval(tick, 1000);
  }
  return () => {
    listeners.delete(onTick);
    if (listeners.size === 0 && timer !== undefined) {
      clearInterval(timer);
      timer = undefined;
      current = undefined;
    }
  };
}

// `useNow()` reads the real clock to the second (the quote sheet's countdown); `useNow(deadlineMs)`
// reads `clockReading`, which holds still while a deadline row's text cannot change, so a row hours
// from its deadline, or past it, sleeps through the ticks.
export function useNow(deadlineMs?: number | null) {
  const read = () => clockReading(deadlineMs, readPageClock());
  return useSyncExternalStore(subscribePageClock, read, read);
}
