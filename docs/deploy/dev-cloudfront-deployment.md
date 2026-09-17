# Dev Environment Entry-Layer Rework: CloudFront + ALB Tutorial (Removing Public Port 8589)

**Status:** to be executed
**Environment:** `backend-dev` (existing EC2 instance)
**Goal:** backend port 8589 is no longer exposed to the public internet; the frontend is served from S3 + CloudFront; all API traffic goes through CloudFront at `https://<cloudfront-domain>/api/*`. No custom domain is needed at any point — the built-in `*.cloudfront.net` certificate on CloudFront handles user-facing HTTPS.

## 0. Target architecture

```text
User ──HTTPS──► https://xxxx.cloudfront.net (CloudFront built-in certificate)
                  ├── /*      → S3 frontend bucket (private via OAC)
                  └── /api/*  → ALB (HTTP:80, accepts CloudFront origins only + secret header)
                                  └─► EC2:8589 (SG allows the ALB SG only)
                                        └─► blocbuy-backend (host network, /api path prefix)
```

| Change | Before | After |
|---|---|---|
| User access to backend | `http://<EC2-public-IP>:8589` | `https://<cloudfront-domain>/api/...` |
| EC2 security group inbound 8589 | `0.0.0.0/0` | ALB security group only |
| CORS | cross-origin configuration needed | frontend and backend same origin (`/api` prefix), no CORS needed |
| User-facing TLS | none (plaintext HTTP) | TLS terminated by CloudFront |
| Origin protection | security groups only | CloudFront managed prefix list + `X-Origin-Secret` header |

## 1. Prerequisites

- The dev EC2 instance already runs the backend per the [operator guide](backend-ec2-pipeline-operator-guide.md);
- AWS CLI v2 is available on your workstation or operator environment (all commands below are run by an operator with administrative permissions);
- The dev VPC has **subnets in two different AZs** (an ALB requires this);
- The companion changes in this repository have been merged (the `/api` context-path in `compose.yml`, the workflow health-check path), i.e. the current `dev` branch includes the same commit batch as this tutorial.

## 2. Collect and export variables

```bash
export AWS_REGION=ap-southeast-1            # replace with your actual region
export ACCOUNT_ID=402178232802
export INSTANCE_ID=i-0852b6c3475b19f05               # dev EC2 instance ID
export VPC_ID=$(aws ec2 describe-instances --instance-ids "i-0852b6c3475b19f05" \
  --query 'Reservations[0].Instances[0].VpcId' --output text)
export BACKEND_SG_ID=$(aws ec2 describe-instances --instance-ids "i-0852b6c3475b19f05" \
  --query 'Reservations[0].Instances[0].SecurityGroups[0].GroupId' --output text)

# Subnets in two different AZs (required by the ALB)
aws ec2 describe-subnets --filters Name=vpc-id,Values="$VPC_ID" \
  --query 'Subnets[?MapPublicIpOnLaunch==`true`].[SubnetId,AvailabilityZone]' --output table
export SUBNET_A=subnet-aaaa
export SUBNET_B=subnet-bbbb

# Anti-bypass secret: CloudFront → ALB must carry it; the ALB only forwards after validating it
export ORIGIN_SECRET=$(openssl rand -hex 32)
echo "$ORIGIN_SECRET"   # used in several later steps — keep it safe; do not commit it to the repository
<64-hex-character-secret>
```

If the instance has multiple security groups, `SecurityGroups[0]` may pick the wrong one; confirm which one allows port 8589 with `aws ec2 describe-instances --query '...Instances[0].SecurityGroups'`.

## 3. Security groups

### 3.1 Create the ALB security group (accepts CloudFront origins only)

CloudFront origin IPs are maintained in the official AWS managed prefix list — reference it directly and it updates automatically:

```bash
# Note: the CloudFront prefix list can only be found via describe-managed-prefix-lists;
# the older describe-prefix-lists only returns legacy lists such as S3/DynamoDB and yields None.
export CF_PREFIX_LIST=$(aws ec2 describe-managed-prefix-lists \
  --filters Name=prefix-list-name,Values=com.amazonaws.global.cloudfront.origin-facing \
  --query 'PrefixLists[0].PrefixListId' --output text)
echo "$CF_PREFIX_LIST"   # should start with pl-; if it is None the lookup failed — investigate before continuing
pl-31a34658

aws ec2 create-security-group \
  --group-name blocbuy-dev-alb-sg \
  --description "BlocBuy dev ALB - CloudFront origin only" \
  --vpc-id "$VPC_ID"
export ALB_SG_ID=$(aws ec2 describe-security-groups \
  --filters Name=group-name,Values=blocbuy-dev-alb-sg Name=vpc-id,Values="$VPC_ID" \
  --query 'SecurityGroups[0].GroupId' --output text)

# The simplified syntax does not support prefix lists (there is no --source-prefix-list option);
# --ip-permissions must be used.
aws ec2 authorize-security-group-ingress \
  --group-id "$ALB_SG_ID" \
  --ip-permissions "IpProtocol=tcp,FromPort=80,ToPort=80,PrefixListIds=[{PrefixListId=$CF_PREFIX_LIST}]"
```

> The prefix list is **global** (`com.amazonaws.global.cloudfront.origin-facing`) and has many entries — reference it by ID; do not expand it into a CIDR list.

### 3.2 Allow ALB → EC2:8589 (leave the existing public rule in place for now; close it after verification)

```bash
aws ec2 authorize-security-group-ingress \
  --group-id "$BACKEND_SG_ID" --protocol tcp --port 8589 --source-group "$ALB_SG_ID"
```

## 4. Create the target group and ALB

```bash
aws elbv2 create-target-group \
  --name blocbuy-dev-backend \
  --protocol HTTP --port 8589 --vpc-id "$VPC_ID" --target-type instance \
  --health-check-path /api/actuator/health \
  --health-check-protocol HTTP --health-check-port traffic-port \
  --health-check-interval-seconds 15 --health-check-timeout-seconds 5 \
  --healthy-threshold-count 2 --unhealthy-threshold-count 3 \
  --matcher HttpCode=200
export TG_ARN=$(aws elbv2 describe-target-groups --names blocbuy-dev-backend \
  --query 'TargetGroups[0].TargetGroupArn' --output text)

aws elbv2 register-targets --target-group-arn "$TG_ARN" --targets Id="$INSTANCE_ID"

aws elbv2 create-load-balancer \
  --name blocbuy-dev-backend \
  --subnets "$SUBNET_A" "$SUBNET_B" \
  --security-groups "$ALB_SG_ID" \
  --scheme internet-facing --type application --ip-address-type ipv4
export ALB_ARN=$(aws elbv2 describe-load-balancers --names blocbuy-dev-backend \
  --query 'LoadBalancers[0].LoadBalancerArn' --output text)
export ALB_DNS=$(aws elbv2 describe-load-balancers --names blocbuy-dev-backend \
  --query 'LoadBalancers[0].DNSName' --output text)
```

### 4.1 Listener: default 403, only requests carrying the secret header are forwarded

Without a domain there is no ACM certificate available on the ALB, so only an HTTP:80 listener is created (TLS is CloudFront's job). The default action returns 403; only traffic matching the `X-Origin-Secret` rule is forwarded to the backend — even someone who learns the ALB DNS name and connects directly gets nothing:

```bash
aws elbv2 create-listener \
  --load-balancer-arn "$ALB_ARN" --protocol HTTP --port 80 \
  --default-actions Type=fixed-response,FixedResponseConfig="{StatusCode=403,ContentType=text/plain,MessageBody=forbidden}"
export LISTENER_ARN=$(aws elbv2 describe-listeners --load-balancer-arn "$ALB_ARN" \
  --query 'Listeners[0].ListenerArn' --output text)

aws elbv2 create-rule \
  --listener-arn "$LISTENER_ARN" --priority 10 \
  --conditions Field=http-header,HttpHeaderConfig="{HttpHeaderName=X-Origin-Secret,Values=[$ORIGIN_SECRET]}" \
  --actions Type=forward,TargetGroupArn="$TG_ARN"
```

### 4.2 Verify the origin chain (the public 8589 rule still exists at this point; we only verify the ALB)

The target group health status should turn healthy (this requires the backend to have been redeployed with the `/api` context-path — see step 5):

```bash
aws elbv2 describe-target-health --target-group-arn "$TG_ARN"
curl --fail --silent -H "X-Origin-Secret: $ORIGIN_SECRET" "http://$ALB_DNS/api/actuator/health"
# should return {"status":"UP",...}
curl -s -o /dev/null -w '%{http_code}\n' "http://$ALB_DNS/api/actuator/health"   # without the secret this must be 403
```

## 5. Deploy the backend with the `/api` path prefix

> **Update (2026-09-07):** the `/api` prefix no longer comes from a servlet context-path
> (`SERVER_SERVLET_CONTEXT_PATH` was removed from `compose.yml` — it doubled the prefix to
> `/api/api/v1/...` against the controller mappings). It is now carried by the controller
> mappings (`ApiConstants.API_V1`) plus the management base-path
> (`management.endpoints.web.base-path: /api/actuator`) in `application.yml`. All URL shapes
> described in this runbook are unchanged.

This repository sets `SERVER_FORWARD_HEADERS_STRATEGY: framework` in `deployment/backend/compose.yml`, and the default URL in `health-check.sh` is `http://127.0.0.1:8589/api/actuator/health`.

The deployment bundle (compose.yml + scripts) is delivered over SSM by every deploy workflow run, so all you need to do is **trigger one regular deployment** (merge a change to `dev` or manually re-run the deploy job of `ci-backend`, with `ENABLE_EC2_DEPLOY=true`). The changed compose environment variables force-recreate the container, so they take effect automatically.

After the deployment, verify on the EC2 host:

```bash
aws ssm send-command --instance-ids "$INSTANCE_ID" \
  --document-name AWS-RunShellScript \
  --parameters commands='curl --fail --silent http://127.0.0.1:8589/api/actuator/health'
```

## 6. Frontend S3 bucket + OAC

```bash
export S3_BUCKET=blocbuy-dev-frontend-$ACCOUNT_ID
aws s3 mb "s3://$S3_BUCKET" --region "$AWS_REGION"
aws s3api put-public-access-block --bucket "$S3_BUCKET" --public-access-block-configuration \
  BlockPublicAcls=true,IgnorePublicAcls=true,BlockPublicPolicy=true,RestrictPublicBuckets=true

# OAC: lets CloudFront read the bucket privately
cat > /tmp/oac.json <<EOF
{
  "Name": "blocbuy-dev-frontend-oac",
  "Description": "OAC for blocbuy dev frontend bucket",
  "SigningProtocol": "sigv4",
  "SigningBehavior": "always",
  "OriginAccessControlOriginType": "s3"
}
EOF
aws cloudfront create-origin-access-control --origin-access-control-config file:///tmp/oac.json
export OAC_ID=$(aws cloudfront list-origin-access-controls \
  --query "OriginAccessControlList.Items[?Name=='blocbuy-dev-frontend-oac'].Id" --output text)
```

The bucket policy depends on the distribution ARN, so it is filled in **after** the distribution is created in step 7 (see 7.2).

## 7. Create the CloudFront distribution

### 7.1 Distribution configuration

```bash
cat > /tmp/cloudfront-dist.json <<EOF
{
  "CallerReference": "blocbuy-dev-$(date +%s)",
  "Comment": "BlocBuy dev - frontend S3 + backend ALB (/api)",
  "Enabled": true,
  "DefaultRootObject": "index.html",
  "HttpVersion": "http2",
  "IsIPV6Enabled": true,
  "PriceClass": "PriceClass_All",
  "Origins": {
    "Quantity": 2,
    "Items": [
      {
        "Id": "s3-frontend",
        "DomainName": "$S3_BUCKET.s3.$AWS_REGION.amazonaws.com",
        "OriginAccessControlId": "$OAC_ID",
        "S3OriginConfig": {
          "OriginAccessIdentity": ""
        }
      },
      {
        "Id": "alb-backend",
        "DomainName": "$ALB_DNS",
        "CustomOriginConfig": {
          "HTTPPort": 80,
          "HTTPSPort": 443,
          "OriginProtocolPolicy": "http-only",
          "OriginSslProtocols": {"Quantity": 1, "Items": ["TLSv1.2"]}
        },
        "CustomHeaders": {
          "Quantity": 1,
          "Items": [{"HeaderName": "X-Origin-Secret", "HeaderValue": "$ORIGIN_SECRET"}]
        }
      }
    ]
  },
  "DefaultCacheBehavior": {
    "TargetOriginId": "s3-frontend",
    "ViewerProtocolPolicy": "redirect-to-https",
    "AllowedMethods": {"Quantity": 2, "Items": ["GET", "HEAD"], "CachedMethods": {"Quantity": 2, "Items": ["GET", "HEAD"]}},
    "CachePolicyId": "658327ea-f89d-4fab-a63d-7e88639e58f6",
    "Compress": true
  },
  "CacheBehaviors": {
    "Quantity": 1,
    "Items": [
      {
        "PathPattern": "/api/*",
        "TargetOriginId": "alb-backend",
        "ViewerProtocolPolicy": "redirect-to-https",
        "AllowedMethods": {
          "Quantity": 7,
          "Items": ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"],
          "CachedMethods": {"Quantity": 2, "Items": ["GET", "HEAD"]}
        },
        "CachePolicyId": "4135ea2d-6df8-44a3-9df3-4b5a84be39ad",
        "OriginRequestPolicyId": "b689b0a8-53d0-40ab-baf2-68738e2966ac",
        "Compress": true
      }
    ]
  },
  "ViewerCertificate": {
    "CloudFrontDefaultCertificate": true,
    "MinimumProtocolVersion": "TLSv1.2_2021",
    "SSLSupportMethod": "vip"
  },
  "Restrictions": {"GeoRestriction": {"RestrictionType": "none", "Quantity": 0}}
}
EOF

aws cloudfront create-distribution --distribution-config file:///tmp/cloudfront-dist.json
export DIST_ID=$(aws cloudfront list-distributions \
  --query "DistributionList.Items[?Comment=='BlocBuy dev - frontend S3 + backend ALB (/api)'].Id" --output text)
export DIST_DOMAIN=$(aws cloudfront get-distribution --id "$DIST_ID" \
  --query 'Distribution.DomainName' --output text)
export DIST_ARN=$(aws cloudfront get-distribution --id "$DIST_ID" \
  --query 'Distribution.ARN' --output text)
echo "$DIST_DOMAIN"   # looks like dxxxxxxxxxxxx.cloudfront.net
```

Notes:

- `4135ea2d-...` is the managed **CachingDisabled** policy (the API needs `POST`/`PUT`, so nothing may be cached);
- `b689b0a8-...` is the managed **AllViewerExceptHostHeader** origin request policy, which forwards browser request headers (including `X-Forwarded-Proto`) to the backend; combined with `SERVER_FORWARD_HEADERS_STRATEGY=framework` this makes forwarded headers work;
- `OriginProtocolPolicy: http-only` is the key to the no-domain setup: CloudFront's built-in certificate handles user-facing TLS while the origin leg runs plain HTTP; security on that leg comes from the prefix-list security group + secret header + the private ALB→EC2 path.

### 7.2 Fill in the S3 bucket policy (OAC)

```bash
cat > /tmp/bucket-policy.json <<EOF
{
  "Version": "2012-10-17",
  "Statement": [{
    "Sid": "AllowCloudFrontOAC",
    "Effect": "Allow",
    "Principal": {"Service": "cloudfront.amazonaws.com"},
    "Action": "s3:GetObject",
    "Resource": "arn:aws:s3:::$S3_BUCKET/*",
    "Condition": {"StringEquals": {"AWS:SourceArn": "$DIST_ARN"}}
  }]
}
EOF
aws s3api put-bucket-policy --bucket "$S3_BUCKET" --policy file:///tmp/bucket-policy.json
```

### 7.3 (Superseded by §14) SPA routing fallback

> **Note (2026-09-16):** this step is no longer optional. The frontend uses history-mode routing, so
> the fallback is mandatory; follow §14, which versions the function source under
> `deployment/frontend/cloudfront/` and adds the verification probes. The text below is kept for
> the history of the original setup only. Tracked as blocker A1 (#99).

If the frontend uses history-mode routing (paths like `/groupbuy/123`), requests for non-static resources need to fall back to `index.html`. Create a CloudFront Function and attach it to the default behavior:

```bash
cat > /tmp/spa-fn.js <<'EOF'
function handler(event) {
  var request = event.request;
  var uri = request.uri;
  if (uri.endsWith('/')) { request.uri += 'index.html'; }
  else if (!uri.includes('.')) { request.uri = '/index.html'; }
  return request;
}
EOF
FN_ETAG=$(aws cloudfront create-function \
  --name blocbuy-dev-spa-fallback \
  --function-config '{"Comment":"SPA fallback","Runtime":"cloudfront-js-2.0"}' \
  --function-code fileb:///tmp/spa-fn.js \
  --query ETag --output text)
aws cloudfront publish-function --name blocbuy-dev-spa-fallback --if-match "$FN_ETAG"
```

`Runtime` is a `FunctionConfig` field (there is no `--runtime` option), and a new function stays in the `DEVELOPMENT` stage until `publish-function` is called with the ETag that `create-function` returned. Then associate it with the default behavior:

```bash
ETAG=$(aws cloudfront get-distribution-config --id "$DIST_ID" --query ETag --output text)
aws cloudfront get-distribution-config --id "$DIST_ID" \
  --query DistributionConfig --output json > /tmp/dist-config.json
# Add to DefaultCacheBehavior:
#   "FunctionAssociations": {"Quantity": 1, "Items": [{"EventType": "viewer-request",
#     "FunctionARN": "<FunctionMetadata.FunctionARN returned by create-function above>"}]}
# After editing, validate the JSON with python/jq, then update:
python3 -c "import json;d=json.load(open('/tmp/dist-config.json'));print(json.dumps(d))" > /tmp/dist-config-flat.json
aws cloudfront update-distribution --id "$DIST_ID" --if-match "$ETAG" \
  --distribution-config file:///tmp/dist-config-flat.json
```

## 8. Close the public 8589 inbound rule only after end-to-end verification

```bash
# 1) API path (wait for the distribution to finish deploying, Status=Deployed, usually 3-5 minutes)
curl --fail --silent "https://$DIST_DOMAIN/api/actuator/health" | grep '"status":"UP"'

# 2) Correctness: direct ALB access without the secret must return 403 (already verified in 4.2)

# 3) Close public 8589 — the core benefit of this tutorial
aws ec2 revoke-security-group-ingress \
  --group-id "$BACKEND_SG_ID" --protocol tcp --port 8589 --cidr 0.0.0.0/0

# 4) Confirm: direct public access to 8589 should time out/be refused, while the ALB chain still works
curl -s -o /dev/null -w '%{http_code}' --connect-timeout 5 "http://<EC2-public-IP>:8589/api/actuator/health" || echo " blocked (expected)"
curl --fail --silent "https://$DIST_DOMAIN/api/actuator/health" | grep '"status":"UP"'
```

Then trigger one more `ci-backend` deployment or a `rollback-backend` run; the built-in "Verify public backend health" workflow step now uses `https://<cloudfront-domain>/api/actuator/health` (see step 9). When it is fully green, the migration is complete.

## 9. Update GitHub variables

```bash
gh variable set BACKEND_URL_DEV --body "https://$DIST_DOMAIN"
```

`BACKEND_URL_DEV` is only used for the workflow's public health verification and as the environment URL display; the main deployment path (ECR → SSM → on-host health check) does not depend on it, so even a wrong value here does not affect releases themselves.

## 10. Frontend build and release conventions

- API clients must always use **relative paths** `/api/...` (e.g. `fetch('/api/actuator/health')`), never absolute URLs — same-origin deployment means no CORS, and a future custom domain will not require code changes;
- Build (Node 24 + pnpm 12 via Corepack; exact steps in `frontend/AGENTS.md`):

  ```bash
  cd frontend && corepack enable && pnpm install --frozen-lockfile && pnpm build
  ```

- Release (long cache for static assets, no caching for index.html):

  ```bash
  aws s3 sync dist/ "s3://$S3_BUCKET" --delete --exclude "index.html" \
    --cache-control "public,max-age=31536000,immutable"
  aws s3 cp dist/index.html "s3://$S3_BUCKET/index.html" \
    --cache-control "no-cache" --content-type "text/html"
  aws cloudfront create-invalidation --distribution-id "$DIST_ID" --paths "/index.html"
  ```

- Normal releases come from the `deploy-s3-dev` job in `ci-frontend.yml` (§16–§17); this manual path is the fallback and must use the same three commands.

## 11. Impact scope and notes

| Item | Notes |
|---|---|
| Swagger | URL is `https://<cloudfront-domain>/api/swagger-ui.html` (`springdoc.swagger-ui.path`) |
| Stripe webhook | The callback URL needs the `/api` prefix (if a public webhook is configured) |
| Local development | Same URL shape as dev: `application.yml` carries the `/api` management/swagger paths for every profile |
| CI smoke test | The docker smoke test in `ci-backend.yml` hits `localhost:8589/api/actuator/health` (management base-path applies to every boot) |
| Direct ALB access | The ALB DNS name is publicly resolvable, but the default 403 plus the prefix-list security group provide dual protection — business endpoints cannot be reached |
| prod environment | The ALB/target-group health-check paths in the prod Terraform guide should be updated to `/api/actuator/health` accordingly |

## 12. Upgrade path after buying a domain

1. Create a Route53 hosted zone and add an Alternate Domain Name (CNAME) to CloudFront;
2. Issue an ACM certificate for the domain (**must be in us-east-1**) and attach it to the CloudFront ViewerCertificate;
3. (Optional) Add an ACM certificate to the ALB (in the region), switch the CloudFront origin to `https-only`, add a 443 listener to the ALB, and change the 80 listener to a redirect;
4. No architectural rework is needed — this only upgrades the HTTP origin leg of the temporary no-domain setup to HTTPS.

## 13. Rollback and cleanup

Entry-layer rollback (re-opening 8589 restores the old access path):

```bash
aws ec2 authorize-security-group-ingress \
  --group-id "$BACKEND_SG_ID" --protocol tcp --port 8589 --cidr 0.0.0.0/0
```

Full cleanup (once you are certain the CloudFront setup is no longer needed):

```bash
aws cloudfront disable-distribution --id "$DIST_ID"
# After Status=Deployed:
aws cloudfront delete-distribution --id "$DIST_ID" --if-match "$(aws cloudfront get-distribution --id $DIST_ID --query ETag --output text)"
aws s3 rb "s3://$S3_BUCKET" --force
aws elbv2 delete-load-balancer --load-balancer-arn "$ALB_ARN"
aws elbv2 delete-target-group --target-group-arn "$TG_ARN"
aws ec2 delete-security-group --group-id "$ALB_SG_ID"
```

> ALB/CloudFront billing is hourly plus traffic; disable/delete them promptly if the dev environment sits idle long-term.

## 14. SPA routing fallback (mandatory for the frontend)

Blocker A1 (#99). Not attached as of the 2026-09-16 probe (`docs/agile/sprint-02/log.md`).

The frontend uses history-mode routing (`/products/<uuid>`, `/account`, `/auth/callback`). Without this
function a deep link or a browser refresh returns the S3 OAC 403. Distribution-wide custom error
responses are deliberately NOT used: they would also mask real `/api/*` errors.

Function source is versioned at `deployment/frontend/cloudfront/spa-fallback.js`.

```bash
# Runtime lives inside FunctionConfig (there is no --runtime option). A new function stays in
# DEVELOPMENT until publish-function is called with the ETag create-function returned.
FN_ETAG=$(aws cloudfront create-function \
  --name blocbuy-dev-spa-fallback \
  --function-config '{"Comment":"SPA fallback for BlocBuy dev frontend","Runtime":"cloudfront-js-2.0"}' \
  --function-code fileb://deployment/frontend/cloudfront/spa-fallback.js \
  --query ETag --output text)
aws cloudfront publish-function --name blocbuy-dev-spa-fallback --if-match "$FN_ETAG"
export SPA_FN_ARN=$(aws cloudfront describe-function --name blocbuy-dev-spa-fallback --stage LIVE \
  --query 'FunctionSummary.FunctionMetadata.FunctionARN' --output text)

# Attach to the DEFAULT behavior only (never to /api/*)
ETAG=$(aws cloudfront get-distribution-config --id "$DIST_ID" --query ETag --output text)
aws cloudfront get-distribution-config --id "$DIST_ID" --query DistributionConfig --output json > /tmp/dist-config.json
python3 - <<'PY'
import json
p='/tmp/dist-config.json'; d=json.load(open(p))
import os
d['DefaultCacheBehavior']['FunctionAssociations']={'Quantity':1,'Items':[{'EventType':'viewer-request','FunctionARN':os.environ['SPA_FN_ARN']}]}
json.dump(d,open(p,'w'))
PY
aws cloudfront update-distribution --id "$DIST_ID" --if-match "$ETAG" --distribution-config file:///tmp/dist-config.json
aws cloudfront wait distribution-deployed --id "$DIST_ID"
```

Verify (both must hold):

```bash
curl -sS -o /dev/null -w '%{http_code} %{content_type}\n' "https://$DIST_DOMAIN/products/00000000-0000-0000-0000-000000000000"
# 200 text/html
curl -sS -o /dev/null -w '%{http_code}\n' "https://$DIST_DOMAIN/assets/does-not-exist.js"
# 403 or 404 (asset paths with an extension are NOT rewritten)
curl -sS -o /dev/null -w '%{http_code}\n' "https://$DIST_DOMAIN/api/v1/products?page=1"
# 200 (the /api/* behavior has no function association)
```

The original §7.3 code rewrote `uri.endsWith('/')` to `uri + 'index.html'`; the frontend has a single `index.html` at the root, so both branches map to `/index.html`.

## 15. Security response headers (CSP, HSTS)

Blocker A2 (#100). Absent as of the 2026-09-16 probe (no CSP, no HSTS on `GET /`).

Policy source: `deployment/frontend/cloudfront/response-headers-policy.json`. The CSP is a starting
point that allows HeroUI's inline styles and remote product images; the frontend owner tightens it
after the first deploy (plan task T2-11) and re-applies with `update-response-headers-policy`.

```bash
aws cloudfront create-response-headers-policy \
  --response-headers-policy-config file://deployment/frontend/cloudfront/response-headers-policy.json
export RHP_ID=$(aws cloudfront list-response-headers-policies --type custom \
  --query "ResponseHeadersPolicyList.Items[?ResponseHeadersPolicy.ResponseHeadersPolicyConfig.Name=='blocbuy-dev-security-headers'].ResponseHeadersPolicy.Id" --output text)

# Attach to BOTH behaviors: HSTS must cover /api/* too; CSP on JSON responses is harmless.
ETAG=$(aws cloudfront get-distribution-config --id "$DIST_ID" --query ETag --output text)
aws cloudfront get-distribution-config --id "$DIST_ID" --query DistributionConfig --output json > /tmp/dist-config.json
python3 - <<'PY'
import json, os
p='/tmp/dist-config.json'; d=json.load(open(p)); rid=os.environ['RHP_ID']
d['DefaultCacheBehavior']['ResponseHeadersPolicyId']=rid
for b in d['CacheBehaviors']['Items']:
    b['ResponseHeadersPolicyId']=rid
json.dump(d,open(p,'w'))
PY
aws cloudfront update-distribution --id "$DIST_ID" --if-match "$ETAG" --distribution-config file:///tmp/dist-config.json
aws cloudfront wait distribution-deployed --id "$DIST_ID"
curl -sSI "https://$DIST_DOMAIN/" | grep -Ei 'strict-transport-security|content-security-policy|x-content-type-options|x-frame-options|referrer-policy'
```

Why each CSP source: `script-src 'self'` (Vite emits no inline scripts; React Compiler output is in the bundle); `style-src 'unsafe-inline'` (React Aria / HeroUI set inline `style` attributes for positioning; revisit in T2-11); `img-src https:` (product `coverImage` hosts are unknown until seed data is inspected; narrow in T2-11); `connect-src 'self'` (all API calls are same-origin); `form-action 'self'` (login is a GET navigation, not a form post); `frame-ancestors 'none'` replaces the older `X-Frame-Options` for modern browsers while `FrameOptions` covers the rest. `Preload: false` because the `*.cloudfront.net` domain cannot be preload-listed.

## 16. Frontend deploy role (GitHub OIDC)

Blocker A3 (#101).

The `deploy-s3-dev` job in `ci-frontend.yml` assumes a role restricted to the `frontend-dev`
GitHub environment. Policy examples: `deployment/frontend/iam/`. The OIDC provider already exists
(created for the backend pipeline).

```bash
sed -e "s/AWS_ACCOUNT_ID/$ACCOUNT_ID/g" \
  deployment/frontend/iam/github-frontend-deploy-trust-policy.json.example > /tmp/fe-trust.json
sed -e "s/AWS_ACCOUNT_ID/$ACCOUNT_ID/g" -e "s/FRONTEND_S3_BUCKET/$S3_BUCKET/g" -e "s/CLOUDFRONT_DISTRIBUTION_ID/$DIST_ID/g" \
  deployment/frontend/iam/github-frontend-deploy-policy.json.example > /tmp/fe-policy.json
aws iam create-role --role-name BlocBuyGitHubFrontendDeployDev --assume-role-policy-document file:///tmp/fe-trust.json
aws iam put-role-policy --role-name BlocBuyGitHubFrontendDeployDev --policy-name frontend-s3-cloudfront-deploy --policy-document file:///tmp/fe-policy.json
export FE_ROLE_ARN=$(aws iam get-role --role-name BlocBuyGitHubFrontendDeployDev --query Role.Arn --output text)
```

The trust policy `sub` must equal the subject the `frontend-dev` job actually presents, so read it
from a real token rather than copying another role's condition: the backend example carries an
id-suffixed subject (`repo:PPsyrius@<id>/iss-groupbuy-team8@<id>:environment:backend-dev`) and
the GitHub-documented plain form is `repo:PPsyrius/iss-groupbuy-team8:environment:frontend-dev`;
which one GitHub emits depends on the repository's OIDC subject customisation, and the wrong one
makes `deploy-s3-dev` fail at `AssumeRoleWithWebIdentity`. Decode a token once from a temporary
step in a job that has `id-token: write` and `environment: frontend-dev` (run it via
`workflow_dispatch` on `dev`, then delete the step):

```bash
# run: step; node is on ubuntu-latest and decodes the unpadded base64url payload
curl -sS -H "Authorization: bearer $ACTIONS_ID_TOKEN_REQUEST_TOKEN" \
  "$ACTIONS_ID_TOKEN_REQUEST_URL&audience=sts.amazonaws.com" \
  | node -e 'let d="";process.stdin.on("data",c=>d+=c).on("end",()=>console.log(JSON.parse(Buffer.from(JSON.parse(d).value.split(".")[1],"base64url")).sub))'
```

Put that exact string in the `sub` condition (edit `/tmp/fe-trust.json` before `create-role`, or
`aws iam update-assume-role-policy` afterwards). Keep the condition on the environment claim, not
on `ref`: the branch restriction comes from the `frontend-dev` environment's deployment branch
policy (§17), which only allows `dev`.

## 17. GitHub `frontend-dev` environment and variables

Blocker A4 (#102). Depends on §16 for `FE_ROLE_ARN`.

```bash
gh api -X PUT repos/PPsyrius/iss-groupbuy-team8/environments/frontend-dev \
  -f deployment_branch_policy[protected_branches]=false \
  -f deployment_branch_policy[custom_branch_policies]=true
gh api -X POST repos/PPsyrius/iss-groupbuy-team8/environments/frontend-dev/deployment-branch-policies -f name=dev
gh variable set FRONTEND_URL_DEV --body "https://$DIST_DOMAIN"
gh variable set FRONTEND_S3_BUCKET_DEV --body "$S3_BUCKET"
gh variable set FRONTEND_CLOUDFRONT_DIST_ID_DEV --body "$DIST_ID"
gh variable set AWS_FRONTEND_DEPLOY_ROLE_ARN_DEV --body "$FE_ROLE_ARN"
gh variable set ENABLE_S3_DEPLOY --body "true"   # set last, after one manual deploy succeeded
```
