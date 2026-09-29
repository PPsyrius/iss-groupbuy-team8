# Full-stack localhost development

One command runs the whole of BlocBuy on your machine with the same routing as the dev and production CloudFront deployments: the SPA at `/` and the backend at `/api/` on one origin, <http://localhost:8080>. Google sign-in, once enabled (see below), works in both modes because both use that origin.

| Mode | Run in `frontend/` | Open | SPA comes from |
| --- | --- | --- | --- |
| Development | `pnpm stack:dev` | <http://localhost:8080> | Vite on your machine, with hot module reloading |
| Production build | `pnpm stack:prod` | <http://localhost:8080> | `pnpm build` output served by Caddy in a container |

`pnpm stack:dev` needs Node 24 and pnpm (see `CONTRIBUTING.md`). `pnpm stack:prod` needs only Docker: without Node, run the same stack from the repository root with `docker compose -f fullstack-localhost/compose.yaml up --build`. Docker means Docker Desktop, or Docker Engine with the Compose v2 plugin; no JDK is needed. The first build downloads Maven and pnpm dependencies and takes a few minutes; later builds reuse the BuildKit cache.

Always use port 8080, because Google sign-in only returns there. Under `stack:dev`, Vite's banner prints that address, and a browser that reaches Vite's own port 5173 is redirected to the same path on 8080. The backend also answers directly on <http://localhost:8589> for API tools.

## What runs

| Service | Built from | Notes |
| --- | --- | --- |
| `frontend` | `frontend.Dockerfile` | `stack:prod` only. Builds `frontend/` with Node 24 and pnpm, then serves `dist/` with Caddy; `Caddyfile` sends `/api/*` to the backend and adds the CloudFront response headers |
| `frontend-dev` | `caddy` image | `stack:dev` only (Compose profile `dev`). `Caddyfile.dev` sends `/api/*` to the backend and everything else, including the hot-reload WebSocket, to Vite on the host |
| `backend` | `backend.Dockerfile` | Compiles `backend/` with JDK 21 (tests and quality gates skipped, CI runs them) under the `docker` profile |
| `postgres`, `redis` | upstream images | Internal network only; data persists in named volumes |

Both front doors publish port 8080, so each script stops the other one first. They start once the backend's health check passes, so after a cold build the page can take a minute to appear. Flyway applies the schema migrations and the demo catalog seed (10 products) that the `local` profile uses, so the listing page is not empty. Swagger UI is at <http://localhost:8080/api/swagger-ui.html>.

## Google sign-in

1. Copy `.env.example` to `.env` in this folder and fill in `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`; the comments in `.env.example` walk through it.
2. The OAuth client must list `http://localhost:8080/api/v1/auth/oauth2/google/callback` as an authorized redirect URI.
3. Restart with `pnpm stack:dev` or `pnpm stack:prod`.

After the callback the backend redirects to the relative `/auth/callback`, so you land back on the front door you started from.

## Everyday use

- Backend or Dockerfile changes: rerun your `pnpm stack:*` command; it rebuilds what changed.
- Frontend changes: under `stack:dev` they appear immediately; under `stack:prod` rerun the command to rebuild the image.
- Stopping Vite (Ctrl+C) leaves the containers running, and `frontend-dev` answers 502 until Vite is back. Stop everything with `docker compose -f fullstack-localhost/compose.yaml --profile dev down`; add `-v` to wipe the database and Redis data.
- Logs: `docker compose -f fullstack-localhost/compose.yaml logs -f backend`.
- Plain `pnpm dev` still works against this backend on port 8589, without Google sign-in.

## Differences from dev and production

- Plain HTTP, so session cookies are not `Secure` (the `docker` profile already allows this).
- `stack:prod` sends the CloudFront response headers (CSP, `X-Frame-Options`, `Referrer-Policy`, `X-Content-Type-Options`, no `Server`), generated from `deployment/frontend/cloudfront/response-headers-policy.json` as `pnpm preview` does; HSTS is left out on plain HTTP. `stack:dev` sends none, like `pnpm dev`.
- Stripe Checkout is off: it needs the `stripe-sprint2` Spring profile, which this stack does not enable.
- Ports 8080 and 8589 bind to `127.0.0.1` only. Port 8589 is also used by the backend-only root `docker-compose.yaml` stack and by a backend started on the host (`./mvnw spring-boot:run`); stop those first, or `up` fails with "port is already allocated".
- Tested on macOS with Docker Desktop only. On Linux Docker Engine, `host.docker.internal` is the docker0 bridge address rather than the host's loopback, so `stack:dev` binds Vite to that address (still not reachable from your network). That path is not yet verified, and a host firewall that filters Docker bridge traffic, or rootless Docker, can still block it; `stack:prod` does not depend on it.
