import { readFileSync } from "node:fs";
import { readFile } from "node:fs/promises";
import { createServer } from "node:http";
import { networkInterfaces } from "node:os";
import { fileURLToPath, URL } from "node:url";

import babel from "@rolldown/plugin-babel";
import tailwindcss from "@tailwindcss/vite";
import { tanstackRouter } from "@tanstack/router-plugin/vite";
import react, { reactCompilerPreset } from "@vitejs/plugin-react";
import { defineConfig, type Plugin, type ViteDevServer } from "vite";

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

// `pnpm stack:dev` runs `vite --mode stack` behind the fullstack-localhost Caddy front door. Google
// sign-in only returns to that origin, so the banner names it and browser requests that reach Vite's
// own port directly are sent there. Caddy forwards the front door's Host header, which passes through.
const stackFrontDoor = "http://localhost:8080";

// The front door reaches Vite at host.docker.internal:5173 (`fullstack-localhost/Caddyfile.dev`).
// Docker Desktop forwards that name to the host's loopback; Linux Docker Engine maps it to the docker0
// bridge address, so Vite listens there instead, which is still not reachable from the network.
function stackDevHost(): string {
  if (process.platform !== "linux") return "127.0.0.1";
  const bridge = networkInterfaces().docker0?.find((address) => address.family === "IPv4");
  return bridge?.address ?? "127.0.0.1";
}

// Only the path and query carry over, so any request target stays on the front door.
function frontDoorLocation(requestUrl = "/"): string {
  if (!URL.canParse(requestUrl, stackFrontDoor)) return `${stackFrontDoor}/`;
  const { pathname, search } = new URL(requestUrl, stackFrontDoor);
  return `${stackFrontDoor}${pathname}${search}`;
}

function listeningPort(server: ViteDevServer): number | undefined {
  const address = server.httpServer?.address();
  return typeof address === "object" && address !== null ? address.port : undefined;
}

// On Linux Vite listens on the docker0 address only, so a loopback listener on the same port keeps
// http://localhost:5173 answering with the redirect to the front door.
function redirectFromLoopback(server: ViteDevServer): void {
  const loopback = createServer((req, res) => {
    res.writeHead(307, { Location: frontDoorLocation(req.url) }).end();
  });
  loopback.on("error", (error) => {
    server.config.logger.warn(`Loopback redirect to ${stackFrontDoor} is off: ${error.message}`);
  });
  server.httpServer?.once("listening", () => {
    const port = listeningPort(server);
    if (port !== undefined) loopback.listen(port, "127.0.0.1");
  });
  server.httpServer?.once("close", () => loopback.close());
}

const stackDevServer: Plugin = {
  name: "blocbuy:stack-front-door",
  apply: (_config, { command, mode }) => command === "serve" && mode === "stack",
  config: () => ({ server: { host: stackDevHost() } }),
  configureServer(server) {
    server.printUrls = () => {
      server.config.logger.info(`\n  ➜  Open ${stackFrontDoor}/\n`);
    };
    if (server.config.server.host !== "127.0.0.1") redirectFromLoopback(server);
    server.middlewares.use((req, res, next) => {
      const port = listeningPort(server);
      if (port !== undefined && req.headers.host?.endsWith(`:${port}`)) {
        // 307 keeps the method and body, so a direct POST to /api is not replayed as a GET.
        res.statusCode = 307;
        res.setHeader("Location", frontDoorLocation(req.url));
        res.end();
        return;
      }
      next();
    });
  },
};

// `vite preview` mirrors the dev CloudFront response headers policy so the CSP can be checked
// locally before the account holder re-applies it (plan T2-11). Read from the versioned JSON so
// the two cannot drift; HSTS is skipped because preview is plain http.
const headersPolicy: unknown = JSON.parse(
  readFileSync(
    fileURLToPath(
      new URL("../deployment/frontend/cloudfront/response-headers-policy.json", import.meta.url),
    ),
    "utf8",
  ),
);
function policyString(...path: string[]): string {
  let node: unknown = headersPolicy;
  for (const key of path) {
    if (typeof node !== "object" || node === null || !(key in node)) {
      throw new Error(`response-headers-policy.json: missing ${path.join(".")}`);
    }
    node = Reflect.get(node, key);
  }
  if (typeof node !== "string") {
    throw new Error(`response-headers-policy.json: ${path.join(".")} is not a string`);
  }
  return node;
}
const previewHeaders = {
  "Content-Security-Policy": policyString(
    "SecurityHeadersConfig",
    "ContentSecurityPolicy",
    "ContentSecurityPolicy",
  ),
  "X-Content-Type-Options": "nosniff",
  "X-Frame-Options": policyString("SecurityHeadersConfig", "FrameOptions", "FrameOption"),
  "Referrer-Policy": policyString("SecurityHeadersConfig", "ReferrerPolicy", "ReferrerPolicy"),
};

export default defineConfig(({ command }) => ({
  plugins: [
    // Route tests sit next to their routes; the generator matches the pattern against each file
    // and directory name under src/routes and skips the matches without a "no Route" warning.
    tanstackRouter({
      target: "react",
      autoCodeSplitting: true,
      routeFileIgnorePattern: String.raw`\.test\.tsx$`,
    }),
    react(),
    babel({ presets: [reactCompilerPreset()] }),
    tailwindcss(),
    mswWorkerInDev,
    stackDevServer,
  ],
  resolve: {
    alias: [...(command === "build" ? deliveryStubs : []), { find: "@", replacement: src }],
  },
  server: {
    proxy: {
      "/api": { target: "http://localhost:8589", changeOrigin: true },
    },
  },
  preview: { headers: previewHeaders },
  build: {
    // No maps in the delivery artifact: they were synced to the public bucket with the code
    // (#155). Nothing consumes them (no error tracker); `vite dev` serves maps regardless.
    sourcemap: false,
    // The CSP's `img-src 'self' https:` has no `data:`, so the brand SVGs (under the 4 KiB
    // default) must ship as files, not inlined URIs the browser would refuse to load.
    assetsInlineLimit: (file) => (file.endsWith(".svg") ? false : undefined),
  },
}));
