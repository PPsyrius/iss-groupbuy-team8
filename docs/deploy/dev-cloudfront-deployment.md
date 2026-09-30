# Dev CloudFront entry layer: operations and import status

**Environment:** account `402178232802`, region `ap-southeast-1`. **Status:** the dev frontend edge was imported into Terraform in PR #190; the backend EC2/ALB remains hand-managed. Origin transport remains HTTP (NFR-37, §12). Import and rotation evidence is in §7.

This is the current operator guide; [`frontend-stack/`](../../infra/terraform/frontend-stack/README.md) owns the frontend resources and drift checks. The [original CLI bootstrap](https://github.com/PPsyrius/iss-groupbuy-team8/blob/12df8a83c86012d51b9de4532741bc0bf80dcad7/docs/deploy/dev-cloudfront-deployment.md) is historical, not a procedure to replay.

## 0. Target architecture

```text
Browser --HTTPS--> CloudFront d1b3np88z657cp.cloudfront.net
                   | /*      --> private S3 bucket via OAC
                   | /api/*  --> ALB HTTP:80 (CloudFront prefix list + X-Origin-Secret rule)
                                 --> EC2:8589 (inbound from ALB SG only)
```

The ALB defaults to 403 and forwards only requests with `X-Origin-Secret`. Viewer HTTPS, the CloudFront prefix-list SG and the header do not encrypt the `http-only` CloudFront→ALB hop: `/api/*` payloads and the header cross it in cleartext (NFR-37, §12). Frontend clients use same-origin `/api/...` paths without CORS.

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

Use an authorized AWS session; confirm the account, region, distribution and `frontend-dev` environment before changing anything. The [backend operator guide](backend-ec2-pipeline-operator-guide.md) owns runtime checks. Never publish the origin secret, Terraform state or saved plans.

## 2. Collect and export variables

These identifiers are non-secret examples; confirm them against AWS before use. Retrieve the origin secret privately only for an authorized change, never for a health check.

```bash
export AWS_REGION=ap-southeast-1
export ACCOUNT_ID=402178232802
export DIST_ID=E3U5W7UWDL8LRV
export DIST_DOMAIN=d1b3np88z657cp.cloudfront.net
export S3_BUCKET=blocbuy-dev-frontend-402178232802
aws sts get-caller-identity --query Account --output text
aws cloudfront get-distribution --id "$DIST_ID" --query 'Distribution.[ARN,DomainName,Status]' --output text
```

Confirm account `402178232802` in the returned ARN and distribution status `Deployed` before an edge change.

## 3. Security groups

The ALB SG accepts TCP:80 only from the CloudFront origin-facing managed prefix list (resolve it with `describe-managed-prefix-lists`); the backend SG accepts TCP:8589 only from the ALB SG. Confirm actual rule IDs and never open public 8589.

## 4. Target group, ALB and listener

The `blocbuy-dev-backend` target group forwards HTTP:8589 and health-checks `/api/actuator/health`. The ALB's HTTP:80 listener defaults to 403; priority 10 forwards only a matching `X-Origin-Secret`. Inspect non-secret metadata and probe direct access without the header:

```bash
aws elbv2 describe-target-groups --names blocbuy-dev-backend --query 'TargetGroups[0].[TargetGroupArn,HealthCheckPath]' --output text
curl --max-time 5 -sS -o /dev/null -w '%{http_code}\n' 'http://blocbuy-dev-backend-1247042341.ap-southeast-1.elb.amazonaws.com/api/actuator/health'
# A timeout/000 means the SG blocked the client; 403 means the request reached the default listener. Neither should return 200.
```

### 4.1 Listener rule

CloudFront and the ALB rule must match the private header. The prefix-list SG drops other clients before the default-403 listener; inspect mismatches privately, never in an issue or PR.

### 4.2 Origin-chain check

Check `describe-target-health`, the direct probe above and public API health (§8); a failing target is not a reason to open port 8589.

## 5. Backend `/api` routing

`deployment/backend/compose.yml` sets `SERVER_FORWARD_HEADERS_STRATEGY: framework`; controllers and the management base path supply `/api`, not a servlet context path. `health-check.sh` probes `127.0.0.1:8589/api/actuator/health`; the [backend operator guide](backend-ec2-pipeline-operator-guide.md) owns SSM deploy and host-local checks. Do not double-prefix `/api`.

## 6. Frontend S3 bucket and OAC

The private bucket blocks public access; `blocbuy-dev-frontend-oac` signs reads with SigV4 (`always`) against the regional S3 REST endpoint, not a website endpoint. Terraform [frontend-stack](../../infra/terraform/frontend-stack/README.md) owns the resources; the 2026-09-25 import passed a zero-change plan (§7).

## 7. CloudFront distribution and bucket policy

The default behavior serves S3 through OAC with CachingOptimized (`658327ea-f89d-4fab-a63d-7e88639e58f6`); `/api/*` uses `alb-backend`, CachingDisabled (`4135ea2d-6df8-44a3-9df3-4b5a84be39ad`) and AllViewerExceptHostHeader (`b689b0a8-53d0-40ab-baf2-68738e2966ac`). Viewers redirect to HTTPS; both behaviors attach security headers, only S3 attaches the SPA function, and the bucket policy limits CloudFront reads to this distribution.

**2026-09-25 import (#161, PR #190):** a reviewed plan imported nine resources into separate encrypted state with zero live changes; the post-import detailed plan exited 0. Keep plans/state private: they can expose the origin header.

**2026-09-25 rotation (NFR-37):** the ALB accepted old/new headers during a CloudFront-only change (0/1/0), then the old match was removed after `Deployed` and API health `UP`; the final plan exited 0. The stable-ARN fix merged in PR #196. The replacement still crosses HTTP (§12); never publish its value or a raw plan.

**2026-09-25 dev exceptions:** scoped Trivy `AWS-0011` (no WAF) and `AWS-0132` (SSE-S3, no CMK) preserve the HIGH/CRITICAL gate. Revisit on measured abuse or a customer-key requirement; review any live change separately.

### 7.1 Distribution configuration

The 2026-09-25 read-only check found CloudFront's default viewer certificate (`vip`, `MinimumProtocolVersion: TLSv1`); the old bootstrap's `TLSv1.2_2021` claim was not live. The ALB origin remains HTTP:80 even though `origin_ssl_protocols = [TLSv1.2]` is configured for a future HTTPS switch. Do not print `Origins.Items`: it contains the private header.

### 7.2 S3 bucket policy (OAC)

`AllowCloudFrontOAC` grants CloudFront `s3:GetObject` on the bucket only when `AWS:SourceArn` matches this distribution. [`s3.tf`](../../infra/terraform/frontend-stack/s3.tf) owns it; review the ARN and JSON normalization before any policy update.

### 7.3 SPA routing fallback (superseded by section 14)

The mandatory CloudFront Function (§14) supersedes the optional 2026-09-16 fallback. Distribution-wide 403/404 custom errors would mask `/api/*` failures.

## 8. Verify public entry and closed port 8589

After a deploy or edge change, require 200 HTML for root/deep links, 403/404 for a missing asset, API health 200 `UP`, blocked direct ALB access (§4) and no public 8589 SG rule. A healthy CloudFront response alone does not prove the port is closed.

```bash
curl -sS -o /dev/null -w '%{http_code} %{content_type}\n' "https://$DIST_DOMAIN/"
curl -sS -o /dev/null -w '%{http_code} %{content_type}\n' "https://$DIST_DOMAIN/products/00000000-0000-0000-0000-000000000000"
curl -sS -o /dev/null -w '%{http_code}\n' "https://$DIST_DOMAIN/assets/does-not-exist.js"
curl --fail --silent "https://$DIST_DOMAIN/api/actuator/health"
```

## 9. Backend GitHub variable

Check `gh variable get BACKEND_URL_DEV`: it should be `https://d1b3np88z657cp.cloudfront.net` for public health and environment links; ECR/SSM delivery does not use it. Change it only with the distribution domain.

## 10. Frontend build and release

`deploy-s3-dev` syncs immutable assets, uploads `index.html` last with `no-cache` and invalidates it. For an authorized manual fallback, run from `frontend/` after `pnpm install --frozen-lockfile` and `pnpm build` (versions in `frontend/AGENTS.md`):

```bash
aws s3 sync dist/ "s3://$S3_BUCKET" --delete --exclude 'index.html' --exclude '*.map' --cache-control 'public,max-age=31536000,immutable'
aws s3 cp dist/index.html "s3://$S3_BUCKET/index.html" --cache-control 'no-cache' --content-type 'text/html'
aws cloudfront create-invalidation --distribution-id "$DIST_ID" --paths '/index.html'
```

`--exclude '*.map'` prevents upload but `--delete` cannot remove previously excluded keys. On 2026-09-30 an operator listed all 45 dev-bucket objects: no maps remained, and the former `/assets/index-DhzGdaI9.js.map` (#155) returned 403. If maps recur, review exact keys, delete only those and invalidate their CloudFront paths. Verify §8 and §15 after delivery.

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

**Origin TLS (NFR-37) is required for confidentiality; a custom viewer domain is optional.** Use a team-controlled origin DNS name and a valid regional ACM certificate in `ap-southeast-1`, add an ALB HTTPS:443 listener and CloudFront-prefix-list SG ingress, then switch the CloudFront ALB origin to `https-only` with TLSv1.2+. Verify `/api/*` health and blocked direct-ALB probes. Rotate `X-Origin-Secret` again *after* the HTTPS cutover: its current value crossed HTTP even though the disclosed predecessor was revoked on 2026-09-25. Review the live change separately from the Terraform import; never publish its secret-bearing plan.

**2026-09-25 dependency:** no team-controlled origin hostname or regional certificate was available, so the cutover was deferred; NFR-37 remains unmet. Issue #227 requires a dated migration plan or a time-bound accepted-risk decision. If a custom viewer hostname is separately requested, its CloudFront ACM certificate belongs in `us-east-1`; the existing `*.cloudfront.net` viewer name can remain.

## 13. Recovery and cleanup

Backend rollback uses `ci-backend` / `rollback-backend`; recheck public, direct-ALB and host-local health. Frontend rollback redeploys a known-good artifact, invalidates `/index.html` and confirms its referenced assets still exist. Do not open public 8589 or delete the bucket/distribution for recovery; edge retirement needs a separate dependency and Terraform-state review.

## 14. SPA routing fallback

The mandatory [SPA function](../../deployment/frontend/cloudfront/spa-fallback.js) rewrites only extensionless default-behavior paths to `/index.html`, never assets or `/api/*`. On 2026-09-25 DEVELOPMENT and LIVE matched the versioned code; import made no changes. AWS provider 5.100.0 does not refresh the virtual `publish` flag, so Terraform ignores it after import; review any future LIVE publish separately and probe §8 afterward.

## 15. Security response headers

The versioned [policy](../../deployment/frontend/cloudfront/response-headers-policy.json) attaches to both behaviors and defines CSP, HSTS, content-type, frame and referrer headers. On 2026-09-30 live HEAD responses for `/` and `/api/actuator/health` returned 200 with all five values matching the policy (NFR-16). Use reviewed Terraform changes for future edits.

After a policy or deploy change, run this stronger GET/value check from the repository root with `DIST_DOMAIN` set in §2; require 2xx from both behaviors, not merely present header names.

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

`style-src 'unsafe-inline'` supports React Aria's injected styles; `img-src https:` permits product images. Use Terraform, not manual policy updates.

## 16. Frontend deploy role (GitHub OIDC)

`deploy-s3-dev` assumes `BlocBuyGitHubFrontendDeployDev` via OIDC under `frontend-dev`. Trust exact-matches `sts.amazonaws.com` and `repo:PPsyrius@19505219/iss-groupbuy-team8@1344446887:environment:frontend-dev`; permissions cover only bucket list/put/delete and this distribution's invalidations. [IAM examples](../../deployment/frontend/iam/README.md) own policy details. Issue #101 / run #165 verified the first assumption; the role was imported with no live change. The OIDC provider and `dev`-only GitHub environment are outside frontend-stack.

## 17. GitHub `frontend-dev` environment and variables

Issue #102 records the `dev`-only `frontend-dev` environment; recheck its branch policy before deployment. `FRONTEND_URL_DEV`, `FRONTEND_S3_BUCKET_DEV`, `FRONTEND_CLOUDFRONT_DIST_ID_DEV` and `AWS_FRONTEND_DEPLOY_ROLE_ARN_DEV` are repository variables. `package-dev` reads the `ENABLE_S3_DEPLOY` deploy/freeze switch before environment entry; `e2e-dev` has no environment. The GitHub environment is not Terraform-managed; [frontend-stack/README.md](../../infra/terraform/frontend-stack/README.md) owns private inputs and drift checks.
