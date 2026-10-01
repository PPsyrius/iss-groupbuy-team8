import { useSyncExternalStore } from "react";

// The console's clock for dates and rule lines: one tick a minute, read through
// `useSyncExternalStore` so `Date.now()` stays out of render (React Compiler purity rule; the
// per-second `useNow` would redraw a whole table every second for text that changes at most once a
// day).
function subscribeMinute(onTick: () => void) {
  const id = setInterval(onTick, 60_000);
  return () => clearInterval(id);
}
function currentMinute() {
  return Math.floor(Date.now() / 60_000) * 60_000;
}

export function useMinuteClock(): number {
  return useSyncExternalStore(subscribeMinute, currentMinute, currentMinute);
}
