import { readFileSync } from "node:fs";
import { createRequire } from "node:module";

import { expect, it, vi } from "vitest";

it("keeps the development service worker in sync with the installed MSW version", () => {
  const require = createRequire(import.meta.url);
  const installed = readFileSync(require.resolve("msw/mockServiceWorker.js"), "utf8");
  const served = readFileSync(require.resolve("../../dev/mockServiceWorker.js"), "utf8");
  expect(served.replaceAll("\r\n", "\n")).toBe(installed.replaceAll("\r\n", "\n"));
});

it("rejects unhandled requests rather than silently reaching the network", async () => {
  const error = vi.spyOn(console, "error").mockImplementation(() => {});
  try {
    await expect(
      fetch(`${window.location.origin}/api/v1/unhandled-msw-regression`),
    ).rejects.toThrow(
      'Cannot bypass a request when using the "error" strategy for the "onUnhandledFrame" option.',
    );
    expect(error).toHaveBeenCalled();
  } finally {
    error.mockRestore();
  }
});
