// Development-only module: `vite.config.ts` aliases `@/dev/browser-msw` to `./browser-msw.noop`
// for `vite build`, so neither msw nor the sample seed reaches the delivery artifact (ADR 0004
// addendum 2026-09-23, #156). The worker script is served from `../../dev/mockServiceWorker.js`
// by a dev-server middleware in the same config.
export { startBrowserMsw } from "@/test/browser-msw";
