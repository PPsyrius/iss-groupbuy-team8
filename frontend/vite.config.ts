import { readFile } from "node:fs/promises";
import { fileURLToPath, URL } from "node:url";

import babel from "@rolldown/plugin-babel";
import tailwindcss from "@tailwindcss/vite";
import { tanstackRouter } from "@tanstack/router-plugin/vite";
import react, { reactCompilerPreset } from "@vitejs/plugin-react";
import { defineConfig, type Plugin } from "vite";

const src = fileURLToPath(new URL("./src", import.meta.url));
const mswWorker = fileURLToPath(new URL("./dev/mockServiceWorker.js", import.meta.url));

// Development-only modules under `src/dev/` are swapped for their `.noop` siblings when the
// command is `build`, so one delivery artifact serves every environment (ADR 0004) without any
// `import.meta.env` in `src/`. `vite dev` and Vitest keep the real modules.
const deliveryStubs = [
  { find: /^@\/dev\/devtools$/, replacement: `${src}/dev/devtools.noop.tsx` },
  { find: /^@\/dev\/browser-msw$/, replacement: `${src}/dev/browser-msw.noop.ts` },
];

// The MSW worker script lives in `dev/`, not `public/`, so it is never copied into `dist/`; the
// dev server serves it at the URL msw registers by default (#156).
const mswWorkerInDev: Plugin = {
  name: "blocbuy:msw-worker-in-dev",
  apply: "serve",
  configureServer(server) {
    server.middlewares.use("/mockServiceWorker.js", async (_req, res) => {
      res.setHeader("Content-Type", "text/javascript");
      res.setHeader("Cache-Control", "no-store");
      res.end(await readFile(mswWorker));
    });
  },
};

export default defineConfig(({ command }) => ({
  plugins: [
    tanstackRouter({ target: "react", autoCodeSplitting: true }),
    react(),
    babel({ presets: [reactCompilerPreset()] }),
    tailwindcss(),
    mswWorkerInDev,
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
