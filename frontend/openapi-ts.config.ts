import { defineConfig } from "@hey-api/openapi-ts";

export default defineConfig({
  input: "../contracts/openapi.json",
  output: "src/api/generated",
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
