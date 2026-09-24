// Build-time stand-in for `./browser-msw` (see `vite.config.ts` and ADR 0004 addendum 2026-09-23).
export async function startBrowserMsw() {
  console.warn("BlocBuy: browser mocks (?msw=1) are not part of the delivery build.");
}
