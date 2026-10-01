# Existing EC2 Backend Deployment Bundle

This bundle operates only the BlocBuy backend container on an existing Linux EC2 instance. It never creates, starts, stops, or removes PostgreSQL, Redis, their Compose projects, or their volumes.

AWS, IAM, GitHub environment and first-deployment setup are documented in the [existing EC2 operator guide](../../docs/deploy/backend-ec2-pipeline-operator-guide.md). Runtime log viewing and searching (CloudWatch Logs, traceId search, SSM port forwarding) are documented in the [backend log observability guide](../../docs/deploy/backend-log-observability.md).

The backend CI workflow packages this non-secret deployment bundle, publishes the verified image to ECR, and invokes `bin/deploy.sh` on the existing EC2 host through AWS Systems Manager. Runtime secrets remain in `/etc/blocbuy/backend.env` on the host and are never included in the bundle.

## Secrets: dev vs prod

- **Dev:** `/etc/blocbuy/backend.env` is hand-edited on the host (see "Install the bundle" below).
- **Prod:** the file is rendered from SSM Parameter Store SecureStrings by `bin/fetch-env.sh`:

  ```bash
  sudo /opt/blocbuy/backend/bin/fetch-env.sh /blocbuy/prod
  ```

  The script fetches its required key allowlist in one `aws ssm get-parameters --with-decryption` call, rejects missing or placeholder values, and writes the file atomically as `root:root 0600`. The prod deploy job invokes it before every `deploy.sh` so rotation takes effect on the next deploy. It is **never invoked on dev** — dev keeps its hand-edited file.

  `/blocbuy/prod/BLOCBUY_STRIPE_CHECKOUT_ENABLED` is an optional SSM parameter that defaults to `false` if absent; leave it false until a separate production payment release. The other existing required parameters remain required.

## Runtime model

```text
EC2 host
├── existing PostgreSQL -> 127.0.0.1:5432
├── existing Redis      -> 127.0.0.1:6379
└── blocbuy-backend     -> host network, port 8589, Spring profile ec2
```

Docker host networking is Linux-only. The backend uses the host network so that its loopback connections reach the existing host-published PostgreSQL and Redis ports. The backend Compose file contains no database service, cache service, or volume declaration.

## Host prerequisites

- Linux EC2 host with Docker Engine and the Docker Compose plugin;
- AWS CLI v2;
- `curl`, `flock`, `timeout`, `grep`, and GNU `stat`;
- PostgreSQL reachable at `127.0.0.1:5432`;
- Redis reachable at `127.0.0.1:6379`;
- an EC2 instance role that can authenticate to and pull from the selected private ECR repository;
- at least 2 GiB free under `/opt/blocbuy/backend` by default.

## Install the bundle

Install the committed files at the fixed runtime location:

```bash
sudo install -d -m 0750 /opt/blocbuy/backend/bin /opt/blocbuy/backend/state /etc/blocbuy
sudo install -m 0644 deployment/backend/compose.yml /opt/blocbuy/backend/compose.yml
sudo install -m 0755 deployment/backend/bin/*.sh /opt/blocbuy/backend/bin/
sudo install -m 0600 deployment/backend/backend.env.example /etc/blocbuy/backend.env
sudoedit /etc/blocbuy/backend.env
```

Replace every placeholder in `/etc/blocbuy/backend.env`. Do not commit or transmit the real file through GitHub Actions.

### Change the scheduling flag

`BLOCBUY_SCHEDULING_ENABLED` defaults to `false`. Before enabling it, run:

```sql
SELECT count(*)
FROM domain.biz_group_deal
WHERE deleted = false AND status = 'open' AND expire_time <= now();
SELECT count(*)
FROM domain.biz_refund
WHERE deleted = false AND status IN ('pending', 'processing') AND next_attempt_at <= now();
```

Record both backlogs before enabling scheduling: under the `ec2` profile this switch starts the expiry sweep and the refund worker, which attempts one due obligation every 30 seconds and can send sandbox refunds when `STRIPE_API_KEY` is configured with a `sk_test_` key. Checkout is a separate `BLOCBUY_STRIPE_CHECKOUT_ENABLED` switch and remains disabled by default. On dev, edit `/etc/blocbuy/backend.env` directly. On prod, update `/blocbuy/prod/BLOCBUY_SCHEDULING_ENABLED` in SSM Parameter Store and rerun `sudo /opt/blocbuy/backend/bin/fetch-env.sh /blocbuy/prod` to refresh the host env file. For either value and environment, run an approved backend deployment or the reviewed [`deploy.sh` command](#deploy) with the current immutable image digest: it force-recreates the backend container and reloads the env file. `docker compose restart` and `docker restart` retain the old container environment. To pause, set `false` and recreate; an in-flight provider call may finish before the container stops.

### Change the Sandbox Checkout flag

On dev only, set `BLOCBUY_STRIPE_CHECKOUT_ENABLED=true` in `/etc/blocbuy/backend.env` along with a `sk_test_` API key, the test webhook signing secret, and both `BLOCBUY_PAYMENT_CHECKOUT_*_URL` values pointing to the dev frontend's `/payments/return` route. Complete the [Stripe pre-live checklist](../../backend/docs/payment/stripe-release-boundary.md) and configure dev product team terms through the audited admin API before force-recreating the backend with an approved deployment. Keep production's optional SSM flag false; no live-key rollout is included. A plain container restart does not load changed environment variables.

To enable the optional Jev listing re-ranker on dev, set `TYPESAFE_ENABLED=true` and place the TypeSafe credential in `TYPESAFE_API_KEY`. The ordinary listing search remains available when Jev is disabled or temporarily unavailable; clients request page-local semantic ordering with `ai=true`. The repository's `typesafe-smoke` workflow uses its separate Actions secret only for a manually dispatched provider check and does not provision this host file.

Optional deployment constraints may be supplied to `deploy.sh` by its caller:

```bash
export BLOCBUY_EXPECTED_AWS_ACCOUNT_ID=123456789012
export BLOCBUY_EXPECTED_AWS_REGION=ap-southeast-1
export BLOCBUY_EXPECTED_ECR_REPOSITORY=blocbuy-backend
```

The deployment job sets all three constraints before invoking the script.

## Preflight

Run this before the first deployment and after host-level changes:

```bash
sudo /opt/blocbuy/backend/bin/preflight.sh
```

Preflight is read-only. It validates host tooling, the environment-file permissions and required keys, Compose rendering, loopback TCP reachability for PostgreSQL and Redis, disk headroom, and the EC2 AWS identity.

## Deploy

Only immutable private-ECR image digests are accepted:

```bash
sudo --preserve-env=BLOCBUY_EXPECTED_AWS_ACCOUNT_ID,BLOCBUY_EXPECTED_AWS_REGION,BLOCBUY_EXPECTED_ECR_REPOSITORY \
  /opt/blocbuy/backend/bin/deploy.sh \
  '123456789012.dkr.ecr.ap-southeast-1.amazonaws.com/blocbuy-backend@sha256:<64-hex-digest>' \
  '<git-sha>'
```

The script locks concurrent releases, authenticates through the EC2 role, pulls the digest, and recreates only the `backend` service in the `blocbuy-backend-app` Compose project. It records current and previous immutable ECR images under `/opt/blocbuy/backend/state`. A legacy image that was not deployed by digest may be used for immediate automatic rollback during the first release, but it is not retained as a selectable manual rollback target.

Health is polled at `http://127.0.0.1:8589/api/actuator/health` for 90 seconds by default (the backend serves everything under the `/api` servlet context on EC2 so CloudFront can route `/api/*` to the backend origin). Override the non-secret health settings when necessary:

```bash
export BLOCBUY_HEALTH_URL=http://127.0.0.1:8589/api/actuator/health
export BLOCBUY_HEALTH_ATTEMPTS=40
export BLOCBUY_HEALTH_INTERVAL_SECONDS=3
```

If the new container remains unhealthy, `deploy.sh` restores the previously running image and still returns a failure so the deployment pipeline stays red.

## Manual rollback

Preferred remote operation: dispatch the `rollback-backend` GitHub Actions workflow from the `dev` branch, provide an incident/change reason, select the confirmation checkbox, and leave `target_image` empty to use the host's recorded previous image. The workflow uses the protected `backend-dev` environment and the same non-cancellable concurrency lock as normal deployment.

An explicitly reviewed immutable digest may be supplied as `target_image`. The workflow rejects tags and images outside the configured AWS account, Region, and ECR repository.

For host-local recovery, roll back to the recorded previous image:

```bash
sudo /opt/blocbuy/backend/bin/rollback.sh
```

Or select a reviewed immutable digest:

```bash
sudo /opt/blocbuy/backend/bin/rollback.sh \
  '123456789012.dkr.ecr.ap-southeast-1.amazonaws.com/blocbuy-backend@sha256:<64-hex-digest>'
```

An application rollback cannot reverse a Flyway database migration. Automatically deployed schema changes must remain backward-compatible; destructive schema cleanup belongs in a later, separately reviewed release.
