# Full-stack localhost development

One command runs the whole of BlocBuy on your machine with the same routing as the dev and production CloudFront deployments: the SPA at `/` and the backend at `/api/` on one origin. The only requirement is Docker (Docker Desktop, or Docker Engine with the Compose plugin v2.24+); no JDK, Node or pnpm is needed.

```sh
# from the repository root
docker compose -f fullstack-localhost/compose.yaml up --build
```

Open <http://localhost:8080>. The first build downloads Maven and pnpm dependencies and takes a few minutes; later builds reuse the BuildKit cache.

| URL | Serves |
| --- | --- |
| `http://localhost:8080/` | SPA (production build), with client-side routes falling back to `index.html` |
| `http://localhost:8080/api/*` | Backend, proxied by Caddy |
| `http://localhost:8589/api/*` | Backend directly, for `pnpm dev` and API tools |
| `http://localhost:8080/api/swagger-ui.html` | Swagger UI |

## What runs

| Service | Built from | Notes |
| --- | --- | --- |
| `frontend` | `frontend.Dockerfile` | Builds `frontend/` with Node 24 and pnpm, then serves `dist/` with Caddy; `Caddyfile` sends `/api/*` to the backend |
| `backend` | `backend.Dockerfile` | Compiles `backend/` with JDK 21 (tests and quality gates skipped, CI runs them) under the `docker` profile |
| `postgres`, `redis` | upstream images | Internal network only; data persists in named volumes |

Flyway applies the schema migrations and the demo catalog seed (10 products) that the `local` profile uses, so the listing page is not empty.

## Everyday use

- Rebuild after code changes: `docker compose -f fullstack-localhost/compose.yaml up --build -d`.
- Frontend hot reload: keep this stack running and run `pnpm dev` in `frontend/`; Vite serves <http://localhost:5173> and proxies `/api` to the backend on port 8589.
- Logs: `docker compose -f fullstack-localhost/compose.yaml logs -f backend`.
- Stop: `docker compose -f fullstack-localhost/compose.yaml down`; add `-v` to wipe the database and Redis data.

## Optional settings

Localhost defaults need no configuration. To override them, create `fullstack-localhost/.env` (gitignored); Compose reads it automatically. Use Sandbox or local-only values, never deployed-environment secrets.

| Key | Default | Purpose |
| --- | --- | --- |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | empty | Google sign-in; the OAuth client must list `GOOGLE_REDIRECT_URI` |
| `GOOGLE_REDIRECT_URI` | `http://localhost:8080/api/v1/auth/oauth2/google/callback` | OAuth callback on the shared origin |
| `FRONTEND_URL` | `http://localhost:8080` | Where the backend redirects after sign-in |
| `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET` | empty | Stripe Sandbox (`sk_test_...`) |
| `BLOCBUY_SCHEDULING_ENABLED` | `false` | Team-expiry sweep |
| `DB_USERNAME`, `DB_PASSWORD`, `REDIS_PASSWORD` | local-only values in `compose.yaml` | Credentials for the containers on the internal network |

## Differences from dev and production

- Plain HTTP, so session cookies are not `Secure` (the `docker` profile already allows this).
- Caddy does not add the CloudFront response headers; check the CSP with `pnpm build && pnpm preview` in `frontend/`.
- Ports 8080 and 8589 bind to `127.0.0.1` only. Stop the backend-only root `docker-compose.yaml` stack first if it holds port 8589.
