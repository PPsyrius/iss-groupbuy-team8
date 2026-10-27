import { defineConfig, devices } from "@playwright/test";

const baseURL = process.env.E2E_BASE_URL ?? "http://localhost:4173";
const isDev = process.env.E2E_TARGET === "dev";

export default defineConfig({
  testDir: "./e2e",
  timeout: 30_000,
  retries: isDev ? 1 : 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: { baseURL, trace: "on-first-retry", screenshot: "only-on-failure" },
  projects: [
    { name: "chromium", use: { ...devices["Desktop Chrome"] } },
    ...(process.env.E2E_SECURITY_BROWSERS === "all"
      ? [
          {
            name: "firefox",
            use: { ...devices["Desktop Firefox"] },
            testMatch: /(?:security-headers|login|buyer-journey|admin)\.spec\.ts/,
          },
          {
            name: "webkit",
            use: { ...devices["Desktop Safari"] },
            testMatch: /(?:security-headers|login|buyer-journey|admin)\.spec\.ts/,
          },
          {
            name: "webkit-touch",
            use: {
              ...devices["iPhone 13"],
              userAgent:
                "Mozilla/5.0 (iPhone; CPU iPhone OS 26_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Mobile/15E148 Safari/604.1",
            },
            testMatch: /(?:security-headers|login|buyer-journey)\.spec\.ts/,
          },
        ]
      : []),
  ],
  ...(process.env.E2E_BASE_URL
    ? {}
    : {
        webServer: {
          // A clean checkout has no dist/: build it first so `pnpm e2e` works on its own.
          // CI downloads the validated artifact into dist/ and skips the rebuild (ADR 0004).
          command:
            "(test -f dist/index.html || pnpm build) && pnpm preview --port 4173 --strictPort",
          url: "http://localhost:4173",
          reuseExistingServer: true,
        },
      }),
});
