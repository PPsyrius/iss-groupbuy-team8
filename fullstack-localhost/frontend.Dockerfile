# Localhost-only frontend image: builds the SPA and serves it with Caddy, which also forwards /api/* to
# the backend, the same single-origin routing CloudFront applies in dev and production.
# Context: the repository root, because `gen:api` reads contracts/openapi.json.

FROM node:24.21.0-slim AS build
WORKDIR /repo/frontend
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml frontend/.npmrc ./
# Scripts are skipped here: `prepare` needs sources that are copied only in a later layer. Corepack
# keeps the pnpm release it downloads in a cache mount, so a lockfile change does not fetch it again.
RUN --mount=type=cache,target=/root/.cache/node/corepack \
    --mount=type=cache,target=/root/.local/share/pnpm/store \
    corepack enable && pnpm install --frozen-lockfile --ignore-scripts
COPY contracts/openapi.json /repo/contracts/openapi.json
# vite.config.ts loads the CloudFront headers policy for `vite preview`; Caddy sends it as well.
COPY deployment/frontend/cloudfront/response-headers-policy.json /repo/deployment/frontend/cloudfront/
COPY fullstack-localhost/cloudfront-headers.mjs /repo/fullstack-localhost/
RUN node /repo/fullstack-localhost/cloudfront-headers.mjs \
      /repo/deployment/frontend/cloudfront/response-headers-policy.json \
      > /repo/fullstack-localhost/cloudfront-headers.caddy
COPY frontend/ ./
RUN --mount=type=cache,target=/root/.cache/node/corepack \
    pnpm build

FROM caddy:2.11.4-alpine
COPY fullstack-localhost/Caddyfile /etc/caddy/Caddyfile
COPY --from=build /repo/fullstack-localhost/cloudfront-headers.caddy /etc/caddy/cloudfront-headers.caddy
COPY --from=build /repo/frontend/dist /srv
# Unprivileged: Caddy listens on 8080, and the Caddyfile turns off everything that writes to disk.
USER 65534:65534
EXPOSE 8080
