# Build from the repository root; no host NodeJS or unpublished local base image.
FROM node:24.21.0-slim@sha256:d6aa754f16b3197301076f047b5def2f02ea1dbbc2ca920407d46d7ec7f87b20
ENV CI=true COREPACK_ENABLE_DOWNLOAD_PROMPT=0
RUN npm install --global pnpm@12.4.2
WORKDIR /repo
COPY contracts/openapi.json contracts/openapi.json
WORKDIR /repo/frontend
# Cache dependencies/engines independently of app, policy and documentation edits.
# Keep prepare/gen:api enabled with its real inputs, rather than ignoring scripts.
COPY frontend/package.json frontend/pnpm-lock.yaml frontend/pnpm-workspace.yaml frontend/.npmrc frontend/openapi-ts.config.ts ./
COPY frontend/scripts/ts-nocheck-generated.mjs scripts/ts-nocheck-generated.mjs
RUN pnpm install --frozen-lockfile
# WebKit uses the system trust store for real HTTPS subresources.
RUN apt-get update -qq \
    && apt-get install -y --no-install-recommends ca-certificates \
    && pnpm exec playwright install --with-deps chromium firefox webkit \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /repo
COPY frontend/ frontend/
COPY deployment/frontend/cloudfront/response-headers-policy.json deployment/frontend/cloudfront/response-headers-policy.json
COPY .gitignore .gitignore
COPY docs/quality/evidence/zap/remaining-policy-2026-10-27/audit.cjs audit.cjs
WORKDIR /repo/frontend
RUN pnpm build
ENV E2E_SECURITY_BROWSERS=all \
    PLAYWRIGHT_HTML_OPEN=never \
    PLAYWRIGHT_HTML_OUTPUT_DIR=/evidence/playwright-report \
    PLAYWRIGHT_JSON_OUTPUT_FILE=/evidence/browser-suite.json
CMD ["pnpm", "exec", "playwright", "test", "--workers=2", "--reporter=list,html,json", "--output=/evidence/test-results"]
