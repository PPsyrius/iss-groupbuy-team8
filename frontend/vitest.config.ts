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
      // Bootstrap and dev-only wiring, mirrored in the sonar-frontend coverage exclusions (FB-07):
      // main.tsx and router.tsx only mount the app, and `vite build` aliases src/dev to no-ops.
      exclude: [
        "src/api/generated/**",
        "src/routeTree.gen.ts",
        "src/main.tsx",
        "src/router.tsx",
        "src/dev/**",
      ],
    },
  },
});
