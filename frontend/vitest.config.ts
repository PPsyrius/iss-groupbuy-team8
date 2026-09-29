import { fileURLToPath, URL } from "node:url";

import { defineConfig } from "vitest/config";

export default defineConfig({
  resolve: { alias: { "@": fileURLToPath(new URL("./src", import.meta.url)) } },
  test: {
    environment: "happy-dom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
    include: ["src/**/*.test.{ts,tsx}"],
    coverage: {
      provider: "v8",
      enabled: process.env.CI_COVERAGE === "true",
      reporter: ["text", "lcov"],
      include: ["src/**"],
      exclude: ["src/api/generated/**", "src/routeTree.gen.ts"],
    },
  },
});
