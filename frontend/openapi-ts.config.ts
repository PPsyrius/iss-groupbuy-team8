import { defineConfig } from "@hey-api/openapi-ts";

export default defineConfig({
  input: "../contracts/openapi.json",
  // gen:api clears stale contents first, preserving the writable output directory
  // without needing write access to its root-owned parent in browser tooling.
  output: { path: "src/api/generated", clean: false },
  plugins: [
    { name: "@hey-api/client-fetch", runtimeConfigPath: "./src/api/hey-api.ts" },
    { name: "valibot", requests: false, responses: true, definitions: true },
    {
      name: "@tanstack/react-query",
      queryOptions: true,
      queryKeys: true,
      infiniteQueryOptions: false,
      mutationOptions: true,
    },
    "msw",
  ],
});
