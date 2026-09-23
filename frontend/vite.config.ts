import { fileURLToPath, URL } from "node:url";

import babel from "@rolldown/plugin-babel";
import tailwindcss from "@tailwindcss/vite";
import { tanstackRouter } from "@tanstack/router-plugin/vite";
import react, { reactCompilerPreset } from "@vitejs/plugin-react";
import { defineConfig } from "vite";

const src = fileURLToPath(new URL("./src", import.meta.url));

// Development-only modules under `src/dev/` are swapped for their `.noop` siblings when the
// command is `build`, so one delivery artifact serves every environment (ADR 0004) without any
// `import.meta.env` in `src/`. `vite dev` and `vitest` keep the real modules.
const deliveryStubs = [{ find: /^@\/dev\/devtools$/, replacement: `${src}/dev/devtools.noop.tsx` }];

export default defineConfig(({ command }) => ({
  plugins: [
    tanstackRouter({ target: "react", autoCodeSplitting: true }),
    react(),
    babel({ presets: [reactCompilerPreset()] }),
    tailwindcss(),
  ],
  resolve: {
    alias: [...(command === "build" ? deliveryStubs : []), { find: "@", replacement: src }],
  },
  server: {
    proxy: {
      "/api": { target: "http://localhost:8589", changeOrigin: true },
    },
  },
  build: {
    // No maps in the delivery artifact: they were synced to the public bucket with the code
    // (#155). Nothing consumes them (no error tracker); `vite dev` serves maps regardless.
    sourcemap: false,
  },
}));
