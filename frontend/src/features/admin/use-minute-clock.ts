import { useSyncExternalStore } from "react";

import { subscribePageClock } from "@/features/catalog/page-clock";

// The console's clock for dates and rule lines, floored to the minute and read through
// `useSyncExternalStore` so `Date.now()` stays out of render (React Compiler purity rule). It rides
// the page's one shared clock instead of starting an interval per caller; the snapshot only moves
// once a minute, so a whole table re-renders once a minute for text that changes at most once a day.
function currentMinute() {
  return Math.floor(Date.now() / 60_000) * 60_000;
}

export function useMinuteClock(): number {
  return useSyncExternalStore(subscribePageClock, currentMinute, currentMinute);
}
