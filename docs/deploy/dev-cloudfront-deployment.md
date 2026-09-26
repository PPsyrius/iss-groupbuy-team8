# Dev CloudFront entry layer: operations and import status

**Environment:** account `402178232802`, region `ap-southeast-1`. **Status (2026-09-25):** PR #190 merged the Terraform-managed dev frontend edge after nine state imports and a zero-change plan; a later origin-secret rotation also passed a zero-change plan (§7). The dev backend EC2/ALB remains hand-managed. Origin transport is still HTTP (NFR-37, §12).

This is the current operator guide. The original 2026-09-06 CLI bootstrap remains in [Git history at 12df8a8](https://github.com/PPsyrius/iss-groupbuy-team8/blob/12df8a83c86012d51b9de4532741bc0bf80dcad7/docs/deploy/dev-cloudfront-deployment.md); [`frontend-stack/`](../../infra/terraform/frontend-stack/README.md) now owns resource definitions and drift checks. Sections 6–7 preserve the entry-layer pattern cited by prod guides; §§14–17 preserve frontend rollout references. Issues #93 and #99–#102 record the original execution.

## 0. Target architecture

```text
Browser --HTTPS--> CloudFront d1b3np88z657cp.cloudfront.net
                   | /*      --> private S3 bucket via OAC
                   | /api/*  --> ALB HTTP:80 (CloudFront prefix list + X-Origin-Secret rule)
                                 --> EC2:8589 (inbound from ALB SG only)
```

The ALB defaults to 403 and forwards only requests with `X-Origin-Secret`. CloudFront's built-in certificate protects viewers, but its `alb-backend` origin uses `http-only`: **the custom header and `/api/*` payload cross the origin hop unencrypted**. The CloudFront prefix-list SG and header restrict access, not observation on that hop. The disclosed old value was revoked; the replacement still needs an HTTPS-origin cutover and another rotation before NFR-37 can pass (§12). Frontend clients use relative `/api/...` paths on the shared viewer origin, without CORS.

| Resource | Existing dev value |
|---|---|
| Distribution | `E3U5W7UWDL8LRV` (`d1b3np88z657cp.cloudfront.net`) |
| S3 bucket | `blocbuy-dev-frontend-402178232802` |
| S3 origin access control | `blocbuy-dev-frontend-oac` (`E2WEBPIBHXOW90`) |
| Backend ALB origin | `blocbuy-dev-backend-1247042341.ap-southeast-1.elb.amazonaws.com` |
| SPA function | `blocbuy-dev-spa-fallback` |
| Response-headers policy | `blocbuy-dev-security-headers` (`3c91a2c1-f130-42e8-b9cc-fec6a682c838`) |
| Deploy role | `BlocBuyGitHubFrontendDeployDev` |

## 1. Prerequisites

Use an authorized AWS account-holder session and the [backend EC2 operator guide](backend-ec2-pipeline-operator-guide.md) for runtime/deploy checks. Confirm the AWS account, region, distribution and GitHub `frontend-dev` environment before an operation; do not infer live settings from examples. Backend health is `/api/actuator/health`: the `/api` prefix comes from controller mappings and the management base path, not a servlet context path. Never print, paste or commit the origin secret, Terraform state or a saved plan.

## 2. Collect and export variables

The following are non-secret identifiers. These exports apply to the current shell only; recheck AWS before using them. Retrieve the existing origin secret through an approved private channel only when an authorized task requires it; never regenerate it during verification.

```bash
export AWS_REGION=ap-southeast-1
export ACCOUNT_ID=402178232802
export DIST_ID=E3U5W7UWDL8LRV
export DIST_DOMAIN=d1b3np88z657cp.cloudfront.net
export S3_BUCKET=blocbuy-dev-frontend-402178232802
aws sts get-caller-identity --query Account --output text
aws cloudfront get-distribution --id "$DIST_ID" --query 'Distribution.[ARN,DomainName,Status]' --output text
```

The ARN returned by AWS must identify account `402178232802`; the distribution must be `Deployed` before checking changes to the edge.

## 3. Security groups

The ALB security group accepts TCP:80 only from `com.amazonaws.global.cloudfront.origin-facing` (look up the AWS-managed prefix-list ID with `describe-managed-prefix-lists`, not the legacy `describe-prefix-lists`). The backend security group accepts TCP:8589 from the ALB security group only; public `0.0.0.0/0:8589` must stay closed. Review the actual group IDs and rules before diagnosing an inaccessible origin.

## 4. Target group, ALB and listener

The `blocbuy-dev-backend` target group forwards HTTP:8589 to the existing instance and health-checks `/api/actuator/health` for 200. The ALB listens on HTTP:80 with a default fixed 403; priority 10 forwards only requests with the matching `X-Origin-Secret`. Do not expose or recreate the rule when checking it. Query the non-secret status; a direct-ALB request without the header must not reach backend health:

```bash
aws elbv2 describe-target-groups --names blocbuy-dev-backend --query 'TargetGroups[0].[TargetGroupArn,HealthCheckPath]' --output text
curl --max-time 5 -sS -o /dev/null -w '%{http_code}\n' 'http://blocbuy-dev-backend-1247042341.ap-southeast-1.elb.amazonaws.com/api/actuator/health'
# A timeout/000 means the SG blocked the client; 403 means the request reached the default listener. Neither should return 200.
```

### 4.1 Listener rule

The forwarding header must have the same private value on the ALB rule and CloudFront's `alb-backend` origin. The default 403 is defense in depth; direct clients outside the managed prefix list are dropped by the ALB security group before reaching the listener. Inspect the rule through an authorized private channel if its behavior differs; do not attach unredacted listener-rule or origin configuration to an issue or PR.

### 4.2 Origin-chain check

Use `describe-target-health` for the existing target group and the direct probe above, then check the public CloudFront API health in section 8. A failing backend target is a runtime/deploy issue, not a reason to open port 8589 publicly.

## 5. Backend `/api` routing

`deployment/backend/compose.yml` sets `SERVER_FORWARD_HEADERS_STRATEGY: framework`; the controller paths and management base path supply `/api`. `health-check.sh` checks `http://127.0.0.1:8589/api/actuator/health` on the host. Regular backend deployment via `ci-backend` delivers the bundle over SSM and force-recreates the container when its environment changes. Check host-local health with the [backend operator guide](backend-ec2-pipeline-operator-guide.md) rather than adding a second servlet context path (the 2026-09-07 correction prevented `/api/api/...`).

## 6. Frontend S3 bucket and OAC

The frontend bucket is private with all four S3 public-access-block switches enabled. `blocbuy-dev-frontend-oac` signs S3 reads with sigv4 and `always`; the S3 origin domain is the regional REST endpoint, not a website endpoint. The 2026-09-25 read-only checks and zero-change Terraform plan verified these imported resources. For a new environment, use these constraints and the source under `infra/terraform/frontend-stack/s3.tf` and `cloudfront.tf` rather than replaying dev creation commands against existing names.

## 7. CloudFront distribution and bucket policy

The distribution has a default `s3-frontend` origin/behavior and a single ordered `/api/*` behavior targeting `alb-backend`. Viewer traffic redirects to HTTPS. The S3 behavior uses AWS managed CachingOptimized (`658327ea-f89d-4fab-a63d-7e88639e58f6`); `/api/*` uses CachingDisabled (`4135ea2d-6df8-44a3-9df3-4b5a84be39ad`) and AllViewerExceptHostHeader (`b689b0a8-53d0-40ab-baf2-68738e2966ac`). Both behaviors attach the response-headers policy; only the default behavior attaches the SPA function. The S3 bucket policy allows `s3:GetObject` to the CloudFront service principal conditioned on this distribution's ARN.

**2026-09-25 Terraform import (#161, PR #190):** the account holder applied a reviewed plan with **9 imports, 0 added/changed/destroyed** into the separate encrypted frontend state key. An immediate `plan -detailed-exitcode -var-file=../envs/frontend-dev.tfvars` exited **0**; the source merged in PR #190. The import did not change live resources. Keep raw plans/state private: they can expose the origin header.

**2026-09-25 secret rotation (NFR-37):** the ALB temporarily accepted old/new values while a private Terraform plan changed **only** the CloudFront distribution (`0 added, 1 changed, 0 destroyed`); after `Deployed` and API health `UP`, the account holder removed the old ALB match. Private comparisons and a post-change plan (exit **0**) confirmed the new value only and no drift. The local `main.tf`/`s3.tf`/`iam.tf` stable-ARN refactor prevented unrelated policy updates but is **not merged**. The replacement still crosses HTTP (§12); no secret or raw plan belongs in Git.

**2026-09-25 dev security/cost decision:** retain scoped Trivy `AWS-0011` (no WAF) and `AWS-0132` (SSE-S3 rather than CMK) exceptions for the imported edge; neither implies equivalent protection or weakens the HIGH/CRITICAL gate. Revisit WAF on measured abuse/rate-limit need and CMK on data-classification or key-ownership requirements; both would require separately reviewed live changes.

### 7.1 Distribution configuration

The live default CloudFront viewer certificate reports `CloudFrontDefaultCertificate: true`, `SSLSupportMethod: vip` and `MinimumProtocolVersion: TLSv1` (read-only 2026-09-25 check). The old CLI tutorial proposed `TLSv1.2_2021`, which did not describe the returned live configuration; `infra/terraform/frontend-stack/cloudfront.tf` matches live. The ALB origin uses HTTP:80, SSL-protocol entry TLSv1.2 and default 30-second read / 5-second keepalive timeouts. Both origins use 3 connection attempts, 10-second connection timeout and no Origin Shield. Do not print `Origins.Items` wholesale: it includes the private custom header.

### 7.2 S3 bucket policy (OAC)

The `AllowCloudFrontOAC` policy has `AWS:SourceArn` set to the exact distribution ARN and grants `s3:GetObject` on `arn:aws:s3:::blocbuy-dev-frontend-402178232802/*` to `cloudfront.amazonaws.com`. `infra/terraform/frontend-stack/s3.tf` is the imported definition. If a future plan proposes modifying this policy, stop and inspect the actual ARN and JSON normalization before applying anything.

### 7.3 SPA routing fallback (superseded by section 14)

The 2026-09-16 optional fallback from the bootstrap tutorial was superseded by the mandatory, versioned CloudFront Function in section 14. Do not enable distribution-wide 403/404 custom error responses: they mask real `/api/*` failures.

## 8. Verify public entry and closed port 8589

Run these after a deployment or CloudFront change. Root and extensionless deep links should return 200 HTML, missing static assets should remain 403/404, API health should return 200 with `UP`, and direct ALB access without the header must be blocked (timeout at the SG or 403 from the listener). Confirm the backend security group has no public inbound 8589 rule; do not treat a healthy CloudFront response alone as proof that 8589 is closed.

```bash
curl -sS -o /dev/null -w '%{http_code} %{content_type}\n' "https://$DIST_DOMAIN/"
curl -sS -o /dev/null -w '%{http_code} %{content_type}\n' "https://$DIST_DOMAIN/products/00000000-0000-0000-0000-000000000000"
curl -sS -o /dev/null -w '%{http_code}\n' "https://$DIST_DOMAIN/assets/does-not-exist.js"
curl --fail --silent "https://$DIST_DOMAIN/api/actuator/health"
```

## 9. Backend GitHub variable

`BACKEND_URL_DEV` should be `https://d1b3np88z657cp.cloudfront.net`; verify with `gh variable get BACKEND_URL_DEV`. The backend workflow uses it for public health verification and the environment URL, not for its ECR/SSM deploy or host-local health check. Change it only when the distribution domain changes.

## 10. Frontend build and release

The normal `deploy-s3-dev` workflow builds the versioned frontend, synchronizes immutable assets with a long cache policy, uploads `index.html` with `no-cache`, and invalidates `/index.html`. The manual fallback, when explicitly needed, must perform those same steps from `frontend/` after `pnpm install --frozen-lockfile` and `pnpm build` (Node/pnpm versions in `frontend/AGENTS.md`):

```bash
aws s3 sync dist/ "s3://$S3_BUCKET" --delete --exclude 'index.html' --exclude '*.map' --cache-control 'public,max-age=31536000,immutable'
aws s3 cp dist/index.html "s3://$S3_BUCKET/index.html" --cache-control 'no-cache' --content-type 'text/html'
aws cloudfront create-invalidation --distribution-id "$DIST_ID" --paths '/index.html'
```

Source maps are disabled in the build since 2026-09-23 (#155) and excluded from the sync. `--delete` does not remove excluded legacy `*.map` objects from the first 2026-09-22 deployment; review and remove those separately if they still exist. Deploy the same built artifact through the normal workflow when possible; validate root, deep link, asset, API and headers afterward.

## 11. Impact scope

| Item | Current rule |
|---|---|
| Swagger | `https://<cloudfront-domain>/api/swagger-ui.html` |
| Stripe webhook | Callback URL needs the `/api` prefix if configured publicly |
| Local development | The management/swagger paths have the same `/api` shape across profiles |
| CI smoke | Backend Docker smoke checks `localhost:8589/api/actuator/health` |
| Direct ALB | DNS resolves publicly, but SG and default-403 listener block unauthorized access |
| Production | ALB/target health path is `/api/actuator/health` in the prod Terraform guide |

## 12. Optional custom-domain upgrade

Viewer-domain customization is optional: if the team owns a domain, issue a CloudFront viewer ACM certificate in `us-east-1`, attach it to a reviewed distribution change and add the alias/DNS record. **Origin TLS is a separate confidentiality requirement**, not dependent on a custom viewer domain: use an origin DNS name the team controls with a valid ACM certificate in `ap-southeast-1`, add an ALB HTTPS:443 listener and corresponding CloudFront-prefix-list SG ingress, switch the CloudFront ALB origin to `https-only` with TLSv1.2+, and verify `/api/*` health and negative direct-ALB probes. The disclosed original `X-Origin-Secret` was revoked on 2026-09-25, but its replacement is still sent over HTTP; rotate **again** after the encrypted origin leg works so the retained value has not crossed the HTTP hop. Plan/review those live changes separately from #161's import-only adoption, and never publish their secret-bearing plan output.

**2026-09-25 dependency:** the account holder reports no team-controlled origin hostname or issued regional ACM certificate and chose to defer the HTTPS-origin migration. NFR-37 remains unmet; the new header is an access-control check, not confidential in transit. Do not describe the HTTP origin as encrypted or close NFR-37 on the basis of secret rotation alone. Resume this migration when DNS/certificate ownership is available, then rotate again after TLS cutover.

## 13. Recovery and cleanup

Use the existing `ci-backend` / `rollback-backend` workflow for application rollback and verify CloudFront API health, blocked direct-ALB access and host-local health. A frontend release rolls back by redeploying a known-good artifact and invalidating `/index.html`; check that assets referenced by that index are still present. Do not reopen public port 8589 or delete the distribution/bucket as routine recovery. If the entire dev edge is to be retired, review dependencies and the Terraform state ownership first, then use a separately approved cleanup plan; the original destructive bootstrap-era CLI snippet is not a current runbook step.

## 14. SPA routing fallback

The mandatory function [`deployment/frontend/cloudfront/spa-fallback.js`](../../deployment/frontend/cloudfront/spa-fallback.js) rewrites extensionless SPA paths to `/index.html` only on the default behavior; `/api/*` and asset paths are not rewritten. Issue #99 records the 2026-09-22 rollout. On 2026-09-25 the account holder fetched both DEVELOPMENT and LIVE stages read-only; each `git hash-object` matched the versioned file (`af347accf5d62eaf9c6b5515ddc8621e1edcf3dc`). The import and post-import plan made no function changes. AWS provider 5.100.0 reads DEVELOPMENT code but does not read the virtual `publish` flag back from AWS; `publish = false` with `ignore_changes = [publish]` prevented a publish during import. Publishing future code to LIVE needs a separately reviewed step. Verify behavior with section 8 probes after any such change.

## 15. Security response headers

The versioned [`response-headers-policy.json`](../../deployment/frontend/cloudfront/response-headers-policy.json) declares HSTS, CSP, content-type, frame and referrer protections plus removal of `Server`. It attaches to both default and `/api/*` behaviors (issue #100). The 2026-09-23 probes checked five header name/value pairs on both behaviors; the 2026-09-25 policy query matched the tightened fields, including self-hosted fonts and no `data:` source. AWS returned an empty `XSSProtection` object, but the import and post-import plan required no policy update. The older instruction to re-apply the tightened CSP is superseded by the live policy check; use reviewed Terraform changes for future policy edits.

After a policy or deployment change, run the following from the repository root with `DIST_DOMAIN` set as in section 2. Require 2xx from both behaviors and compare all five configured header values; a response that merely contains header names is not sufficient.

```bash
ROOT_HEADERS=$(mktemp)
API_HEADERS=$(mktemp)
trap 'rm -f "$ROOT_HEADERS" "$API_HEADERS"' EXIT
check_success() {
  local status
  status=$(curl -sS -D "$1" -o /dev/null -w '%{http_code}' "$2") || return 1
  case "$status" in 2??) ;; *) echo "unexpected HTTP status $status: $2" >&2; return 1 ;; esac
}
check_success "$ROOT_HEADERS" "https://$DIST_DOMAIN/" || exit 1
check_success "$API_HEADERS" "https://$DIST_DOMAIN/api/v1/products?page=1" || exit 1
python3 - "$ROOT_HEADERS" "$API_HEADERS" <<'PY'
import json
import sys
from pathlib import Path

policy = json.loads(Path('deployment/frontend/cloudfront/response-headers-policy.json').read_text())
security = policy['SecurityHeadersConfig']
hsts = security['StrictTransportSecurity']
hsts_value = f"max-age={hsts['AccessControlMaxAgeSec']}"
if hsts['IncludeSubdomains']:
    hsts_value += '; includeSubDomains'
if hsts['Preload']:
    hsts_value += '; preload'
expected = {
    'strict-transport-security': hsts_value,
    'content-security-policy': security['ContentSecurityPolicy']['ContentSecurityPolicy'],
    'x-content-type-options': 'nosniff',
    'x-frame-options': security['FrameOptions']['FrameOption'],
    'referrer-policy': security['ReferrerPolicy']['ReferrerPolicy'],
}
for path in map(Path, sys.argv[1:]):
    actual = {}
    for line in path.read_text().splitlines():
        name, separator, value = line.partition(':')
        if separator:
            actual.setdefault(name.lower(), []).append(value.strip())
    for name, value in expected.items():
        if actual.get(name) != [value]:
            raise SystemExit(f'{path}: expected {name}: {value!r}, got {actual.get(name)!r}')
PY
```

`style-src 'unsafe-inline'` remains for React Aria's injected styles; `img-src https:` permits product images and avatars; `Preload: false` suits the `*.cloudfront.net` domain. A CSP on API JSON is harmless. The zero-change import has passed; use reviewed Terraform changes rather than manual `update-response-headers-policy` commands.

## 16. Frontend deploy role (GitHub OIDC)

The `deploy-s3-dev` job assumes `BlocBuyGitHubFrontendDeployDev` under the `frontend-dev` GitHub environment. The trust policy exact-matches audience `sts.amazonaws.com` and immutable subject `repo:PPsyrius@19505219/iss-groupbuy-team8@1344446887:environment:frontend-dev`. Its `frontend-s3-cloudfront-deploy` inline policy grants bucket list, object put/delete and invalidation on this distribution only; the OIDC provider is existing account infrastructure and is not imported by frontend-stack. The versioned trust/permission examples are in [`deployment/frontend/iam/`](../../deployment/frontend/iam/README.md). Issue #101 and `ci-frontend` run #165 record the successful first assumption on 2026-09-23; read-only `get-role` / `get-role-policy` checks matched the configuration. The role and inline policy were imported without changes and passed the post-import zero-change plan. The GitHub environment remains outside this stack and restricts deployment to `dev`.

## 17. GitHub `frontend-dev` environment and variables

Issue #102 records that `frontend-dev` and its `dev`-only deployment branch policy were operational on 2026-09-23. Confirm that protection in GitHub before changing delivery. `FRONTEND_URL_DEV`, `FRONTEND_S3_BUCKET_DEV`, `FRONTEND_CLOUDFRONT_DIST_ID_DEV` and `AWS_FRONTEND_DEPLOY_ROLE_ARN_DEV` are repository variables; `package-dev` evaluates the `ENABLE_S3_DEPLOY` switch before environment entry, and `e2e-dev` has no environment declaration. `ENABLE_S3_DEPLOY=true` is the normal deploy/freeze switch, not a Terraform input. The GitHub environment is outside frontend-stack's import scope.

The frontend edge and deploy role import/rotation evidence is in §7. The GitHub environment and repository variables remain outside Terraform; see [frontend-stack/README.md](../../infra/terraform/frontend-stack/README.md) for private inputs and drift checks.
