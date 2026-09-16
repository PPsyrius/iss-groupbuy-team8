import { defineConfig, devices } from "@playwright/test";

const baseURL = process.env.E2E_BASE_URL ?? "http://localhost:4173";
const isDev = process.env.E2E_TARGET === "dev";

export default defineConfig({
  testDir: "./e2e",
  timeout: 30_000,
  retries: isDev ? 1 : 0,
  reporter: [["list"], ["html", { open: "never" }]],
  use: { baseURL, trace: "on-first-retry", screenshot: "only-on-failure" },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  ...(process.env.E2E_BASE_URL
    ? {}
    : {
        webServer: {
          command: "pnpm preview --port 4173 --strictPort",
          url: "http://localhost:4173",
          reuseExistingServer: true,
        },
      }),
});
