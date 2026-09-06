# Existing EC2 Backend Deployment Bundle

This bundle operates only the BlocBuy backend container on an existing Linux EC2 instance. It never
creates, starts, stops, or removes PostgreSQL, Redis, their Compose projects, or their volumes.

AWS, IAM, GitHub environment and first-deployment setup are documented in the
[existing EC2 operator guide](../../docs/deploy/backend-ec2-pipeline-operator-guide.md).

The backend CI workflow packages this non-secret deployment bundle, publishes the verified image to
ECR, and invokes `bin/deploy.sh` on the existing EC2 host through AWS Systems Manager. Runtime
secrets remain in `/etc/blocbuy/backend.env` on the host and are never included in the bundle.

## Secrets: dev vs prod

- **Dev:** `/etc/blocbuy/backend.env` is hand-edited on the host (see "Install the bundle" below).
- **Prod:** the file is rendered from SSM Parameter Store SecureStrings by `bin/fetch-env.sh`:

  ```bash
  sudo /opt/blocbuy/backend/bin/fetch-env.sh /blocbuy/prod
  ```

  The script fetches exactly the keys of `backend.env.example` in one `aws ssm get-parameters
  --with-decryption` call, rejects missing or placeholder values, and writes the file atomically as
  `root:root 0600`. The prod deploy job invokes it before every `deploy.sh` so secret rotation takes
  effect on the next deploy. It is **never invoked on dev** — dev keeps its hand-edited file.

## Runtime model

```text
EC2 host
├── existing PostgreSQL -> 127.0.0.1:5432
├── existing Redis      -> 127.0.0.1:6379
└── blocbuy-backend     -> host network, port 8589, Spring profile ec2
```

Docker host networking is Linux-only. The backend uses the host network so that its loopback
connections reach the existing host-published PostgreSQL and Redis ports. The backend Compose file
contains no database service, cache service, or volume declaration.

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

Replace every placeholder in `/etc/blocbuy/backend.env`. Do not commit or transmit the real file
through GitHub Actions.

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

Preflight is read-only. It validates host tooling, the environment-file permissions and required
keys, Compose rendering, loopback TCP reachability for PostgreSQL and Redis, disk headroom, and the
EC2 AWS identity.

## Deploy

Only immutable private-ECR image digests are accepted:

```bash
sudo --preserve-env=BLOCBUY_EXPECTED_AWS_ACCOUNT_ID,BLOCBUY_EXPECTED_AWS_REGION,BLOCBUY_EXPECTED_ECR_REPOSITORY \
  /opt/blocbuy/backend/bin/deploy.sh \
  '123456789012.dkr.ecr.ap-southeast-1.amazonaws.com/blocbuy-backend@sha256:<64-hex-digest>' \
  '<git-sha>'
```

The script locks concurrent releases, authenticates through the EC2 role, pulls the digest, and
recreates only the `backend` service in the `blocbuy-backend-app` Compose project. It records current
and previous immutable ECR images under `/opt/blocbuy/backend/state`. A legacy image that was not
deployed by digest may be used for immediate automatic rollback during the first release, but it is
not retained as a selectable manual rollback target.

Health is polled at `http://127.0.0.1:8589/api/actuator/health` for 90 seconds by default (the
backend serves everything under the `/api` servlet context on EC2 so CloudFront can route `/api/*`
to the backend origin). Override the
non-secret health settings when necessary:

```bash
export BLOCBUY_HEALTH_URL=http://127.0.0.1:8589/api/actuator/health
export BLOCBUY_HEALTH_ATTEMPTS=40
export BLOCBUY_HEALTH_INTERVAL_SECONDS=3
```

If the new container remains unhealthy, `deploy.sh` restores the previously running image and still
returns a failure so the deployment pipeline stays red.

## Manual rollback

Preferred remote operation: dispatch the `rollback-backend` GitHub Actions workflow from the `dev`
branch, provide an incident/change reason, select the confirmation checkbox, and leave
`target_image` empty to use the host's recorded previous image. The workflow uses the protected
`backend-dev` environment and the same non-cancellable concurrency lock as normal deployment.

An explicitly reviewed immutable digest may be supplied as `target_image`. The workflow rejects
tags and images outside the configured AWS account, Region, and ECR repository.

For host-local recovery, roll back to the recorded previous image:

```bash
sudo /opt/blocbuy/backend/bin/rollback.sh
```

Or select a reviewed immutable digest:

```bash
sudo /opt/blocbuy/backend/bin/rollback.sh \
  '123456789012.dkr.ecr.ap-southeast-1.amazonaws.com/blocbuy-backend@sha256:<64-hex-digest>'
```

An application rollback cannot reverse a Flyway database migration. Automatically deployed schema
changes must remain backward-compatible; destructive schema cleanup belongs in a later, separately
reviewed release.
