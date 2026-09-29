# Localhost-only frontend image: builds the SPA and serves it with Caddy, which also forwards /api/* to
# the backend, the same single-origin routing CloudFront applies in dev and production.
# Context: the repository root, because `gen:api` reads contracts/openapi.json.

FROM node:24-slim AS build
RUN corepack enable
WORKDIR /repo/frontend
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml frontend/.npmrc ./
# Scripts are skipped here: `prepare` needs sources that are copied only in the next layer.
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --ignore-scripts
COPY contracts/openapi.json /repo/contracts/openapi.json
# vite.config.ts loads the CloudFront headers policy for `vite preview`, so the build needs it too.
COPY deployment/frontend/cloudfront/response-headers-policy.json /repo/deployment/frontend/cloudfront/
COPY frontend/ ./
RUN pnpm build

FROM caddy:2-alpine
COPY fullstack-localhost/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /repo/frontend/dist /srv
