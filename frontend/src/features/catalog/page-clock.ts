import { useSyncExternalStore } from "react";

import { clockReading } from "./deal-time";

// The page clock. `useSyncExternalStore` calls `subscribe` once for every hook instance, so each
// instance only registers a listener here: the first one starts the page's single 1 s interval and
// the last one to unsubscribe clears it, however many rows are mounted (My blocs shows up to 100).
// The interval wakes every listener each second, but a component re-renders only when its own
// snapshot changed. Reading `Date.now()` in a snapshot keeps it out of render (React Compiler
// purity rule).
const listeners = new Set<() => void>();
let timer: ReturnType<typeof setInterval> | undefined;

function tick() {
  for (const notify of listeners) notify();
}
export function subscribePageClock(onTick: () => void) {
  listeners.add(onTick);
  timer ??= setInterval(tick, 1000);
  return () => {
    listeners.delete(onTick);
    if (listeners.size === 0 && timer !== undefined) {
      clearInterval(timer);
      timer = undefined;
    }
  };
}

// `useNow()` reads the real clock to the second (the quote sheet's countdown); `useNow(deadlineMs)`
// reads `clockReading`, which holds still while a deadline row's text cannot change, so a row hours
// from its deadline, or past it, sleeps through the ticks.
export function useNow(deadlineMs?: number | null) {
  const read = () => clockReading(deadlineMs, Date.now());
  return useSyncExternalStore(subscribePageClock, read, read);
}
