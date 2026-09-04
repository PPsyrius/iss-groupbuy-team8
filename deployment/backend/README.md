# Existing EC2 Backend Deployment Bundle

This bundle operates only the BlocBuy backend container on an existing Linux EC2 instance. It never
creates, starts, stops, or removes PostgreSQL, Redis, their Compose projects, or their volumes.

The backend CI workflow packages this non-secret deployment bundle, publishes the verified image to
ECR, and invokes `bin/deploy.sh` on the existing EC2 host through AWS Systems Manager. Runtime
secrets remain in `/etc/blocbuy/backend.env` on the host and are never included in the bundle.

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

Health is polled at `http://127.0.0.1:8589/actuator/health` for 90 seconds by default. Override the
non-secret health settings when necessary:

```bash
export BLOCBUY_HEALTH_URL=http://127.0.0.1:8589/actuator/health
export BLOCBUY_HEALTH_ATTEMPTS=40
export BLOCBUY_HEALTH_INTERVAL_SECONDS=3
```

If the new container remains unhealthy, `deploy.sh` restores the previously running image and still
returns a failure so the deployment pipeline stays red.

## Manual rollback

Roll back to the recorded previous image:

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
