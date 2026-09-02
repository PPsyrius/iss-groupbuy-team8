# Deploy the BlocBuy Backend to AWS EC2 with Bitovi

This runbook deploys the repository's Spring Boot backend to an AWS EC2 virtual machine with
[`bitovi/github-actions-deploy-docker-to-ec2`](https://github.com/bitovi/github-actions-deploy-docker-to-ec2).
It is based on the supplied Bitovi action notes and the current BlocBuy backend configuration.

The action provisions the selected AWS resources, copies the application directory to EC2, installs
Docker, merges configuration into a `.env` file, and runs Docker Compose. The deployment described
here runs three containers on one EC2 host:

```text
Internet -> Classic Load Balancer :80 -> EC2 :8589 -> blocbuy-backend
                                                     |-> PostgreSQL 18
                                                     `-> Redis 8.1
```

> **Scope:** This is a practical single-VM deployment suitable for development, demonstrations, and
> small workloads. PostgreSQL and Redis run on the same host as the application. For a production
> system requiring high availability, independent database backups, and low-risk upgrades, use RDS
> and a managed Redis service or follow the repository's broader
> [AWS deployment design](../design/aws-dev-prod-deployment-guide.md).

## 1. Project-specific deployment facts

| Item | BlocBuy value |
|---|---|
| Docker build context | `backend/` |
| Dockerfile | `backend/Dockerfile` |
| Runtime | Java 21, Spring Boot 3.5 |
| Application port | `8589` |
| Health endpoint | `GET /actuator/health` |
| Docker Spring profile | `docker` |
| PostgreSQL host and port | `postgres:5432` |
| PostgreSQL database | `Blocbuy` |
| PostgreSQL version | **18**; migration V2 uses `uuidv7()` |
| Redis host and port | `redis:6379` |
| Required variables | `SPRING_PROFILES_ACTIVE`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_PASSWORD` |
| Optional Stripe variables | `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET` |

The repository already has a production multi-stage Dockerfile. It does **not** currently have the
repository-root `docker-compose.yaml` required by the Bitovi action, so the first preparation step is
mandatory.

## 2. Before starting

You need:

- an AWS account with billing enabled;
- permission to create EC2, VPC, security-group, load-balancer, IAM, S3 state/logging, and CloudWatch
  resources used by the selected action options;
- a dedicated AWS deployment identity and access key;
- administrator access to this GitHub repository's Actions secrets and variables;
- Docker with the Compose plugin for local validation;
- a protected deployment branch; this repository currently uses `dev` for backend CI.

Do not use the AWS root user. Use a dedicated identity for this deployment and scope it to a sandbox
account whenever possible. The Bitovi action runs Terraform and Ansible, so its first deployment
needs more than ordinary EC2 read/write access. Review the action's current permissions and generated
Terraform before granting access in a shared or production AWS account.

This deployment creates billable resources. At minimum, expect an EC2 instance, EBS storage, a load
balancer, S3 storage, and CloudWatch usage. Set an AWS Budget before the first run and follow the
teardown section when the environment is no longer needed.

## 3. Add the root Docker Compose file

Create `docker-compose.yaml` in the **repository root**:

```yaml
name: blocbuy-backend

services:
  postgres:
    image: postgres:18
    restart: unless-stopped
    environment:
      POSTGRES_DB: Blocbuy
      POSTGRES_USER: ${DB_USERNAME:?DB_USERNAME is required}
      POSTGRES_PASSWORD: ${DB_PASSWORD:?DB_PASSWORD is required}
    volumes:
      - postgres-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U \"$$POSTGRES_USER\" -d \"$$POSTGRES_DB\""]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 20s
    networks:
      - data

  redis:
    image: redis:8.1
    restart: unless-stopped
    environment:
      REDIS_PASSWORD: ${REDIS_PASSWORD:?REDIS_PASSWORD is required}
    command:
      - sh
      - -c
      - exec redis-server --appendonly yes --requirepass "$$REDIS_PASSWORD"
    volumes:
      - redis-data:/data
    healthcheck:
      test: ["CMD-SHELL", "redis-cli --no-auth-warning -a \"$$REDIS_PASSWORD\" ping | grep -q PONG"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 10s
    networks:
      - data

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    restart: unless-stopped
    env_file:
      - .env
    ports:
      - "8589:8589"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    stop_grace_period: 30s
    networks:
      - edge
      - data

networks:
  edge:
  data:
    internal: true

volumes:
  postgres-data:
  redis-data:
```

Why these details matter:

- The backend build context must be `./backend`; the multi-module Maven build cannot be built from
  the repository root Docker context.
- PostgreSQL must be version 18 because the payment migration uses PostgreSQL 18's `uuidv7()`.
- PostgreSQL and Redis have no published host ports, so they are not reachable directly from the
  internet.
- The backend joins both networks: `edge` lets it reach the internet and accept load-balancer traffic;
  `data` lets it reach PostgreSQL and Redis.
- The named volumes survive ordinary `docker compose up`, replacement, and restart operations.
- The backend image has no in-image health-check utility. The load balancer checks Spring Actuator
  from outside the container instead.
- No service overrides Docker's logging driver, allowing the Bitovi CloudWatch daemon configuration
  to apply when `docker_cloudwatch_enable` is enabled.

Never set `docker_full_cleanup: true` for this stack unless destroying the PostgreSQL and Redis Docker
volumes is intentional. That option runs a Docker prune including volumes.

## 4. Optionally reduce what is copied to EC2

The action supports a `.gha-ignore` file. A conservative repository-root version is:

```gitignore
.git/**
.codegraph/**
.idea/**
.agents/**
frontend/**
docs/**
contracts/**
backend/**/target/**
**/.DS_Store
```

Do not exclude `docker-compose.yaml`, `backend/Dockerfile`, any backend module, Maven wrapper files,
or Maven POM files.

## 5. Validate the stack locally

Create a temporary repository-root `.env`. The existing `.gitignore` already excludes it:

```dotenv
SPRING_PROFILES_ACTIVE=docker
DB_USERNAME=blocbuy
DB_PASSWORD=replace-with-a-long-random-local-password
REDIS_PASSWORD=replace-with-a-different-long-random-local-password
STRIPE_API_KEY=
STRIPE_WEBHOOK_SECRET=
```

Then run from the repository root:

```bash
docker compose config --quiet
docker compose build backend
docker compose up -d
docker compose ps
curl --fail --show-error http://localhost:8589/actuator/health
```

Expected health response:

```json
{"status":"UP"}
```

If startup fails:

```bash
docker compose logs --tail=200 backend
docker compose logs --tail=100 postgres
docker compose logs --tail=100 redis
```

Stop the local stack without deleting data:

```bash
docker compose down
```

Only use `docker compose down --volumes` when the local database and Redis data may be deleted.
Remove the temporary `.env` after testing.

## 6. Create the AWS deployment credentials

Create a dedicated IAM deployment identity for GitHub Actions. Generate an access-key ID and secret
access key for that identity, not for the AWS root user. The action documentation used by this guide
accepts these inputs:

```text
aws_access_key_id
aws_secret_access_key
aws_session_token       # only for temporary session credentials
aws_default_region
```

The required AWS permissions depend on the enabled inputs. This guide asks the action to manage a VPC,
public subnet, EC2 instance, instance profile, security groups, Classic Load Balancer, S3 Terraform
state/access logs, and CloudWatch Docker logs. Use a separate AWS sandbox account for initial
validation. After the first successful run, use CloudTrail and the action's generated Terraform to
derive a narrower deployment policy.

Record the AWS account ID and chosen region. The examples use Singapore, `ap-southeast-1`; change it
consistently if the workload belongs elsewhere.

## 7. Configure the GitHub deployment environment

In GitHub, open **Settings -> Environments** and create `backend-dev`. Add a required reviewer if the
environment should not deploy automatically.

Add these environment secrets:

| Secret | Content |
|---|---|
| `AWS_ACCESS_KEY_ID` | Dedicated deployment access-key ID |
| `AWS_SECRET_ACCESS_KEY` | Corresponding secret access key |
| `BACKEND_DOT_ENV` | Multiline application configuration shown below |

Add this environment variable:

| Variable | Example |
|---|---|
| `AWS_REGION` | `ap-southeast-1` |

Set `BACKEND_DOT_ENV` to a multiline `KEY=VALUE` document:

```dotenv
SPRING_PROFILES_ACTIVE=docker
DB_USERNAME=blocbuy
DB_PASSWORD=<long-random-database-password>
REDIS_PASSWORD=<different-long-random-redis-password>
STRIPE_API_KEY=
STRIPE_WEBHOOK_SECRET=
```

Use Stripe **test-mode** credentials until the application has undergone a separate payment-security
review. The application tolerates blank Stripe values at startup, but Stripe operations will fail
until `STRIPE_API_KEY` is configured. Webhook verification also requires
`STRIPE_WEBHOOK_SECRET`.

The Bitovi action merges environment sources into the EC2 `.env` file in this order, with later
sources taking precedence:

1. Terraform-generated variables;
2. repository file supplied through `env_repo` (`repo_env` by default);
3. GitHub Actions variables supplied through `env_ghv`;
4. GitHub Actions secrets supplied through `env_ghs`;
5. AWS Secrets Manager JSON secrets supplied through `env_aws_secret`.

Do not commit a real `repo_env` or `.env`. This guide uses only `env_ghs` for application secrets.
For stricter secret management, create one AWS Secrets Manager secret whose value is a JSON object
with the keys above, pass its name through `env_aws_secret`, and remove `BACKEND_DOT_ENV`.

## 8. Add the deployment workflow

Create `.github/workflows/deploy-backend-ec2.yml`:

```yaml
name: deploy-backend-ec2

on:
  push:
    branches: [dev]
    paths:
      - "backend/**"
      - "docker-compose.yaml"
      - ".gha-ignore"
      - ".github/workflows/deploy-backend-ec2.yml"
  workflow_dispatch:

concurrency:
  group: deploy-backend-ec2-dev
  cancel-in-progress: false

permissions:
  contents: read

jobs:
  verify:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: maven

      - name: Verify backend
        run: ./mvnw -B verify

  deploy:
    needs: verify
    runs-on: ubuntu-latest
    timeout-minutes: 45
    environment:
      name: backend-dev
      url: ${{ steps.deploy.outputs.vm_url }}
    steps:
      - id: deploy
        name: Provision EC2 and deploy Docker Compose
        uses: bitovi/github-actions-deploy-docker-to-ec2@v1
        with:
          aws_access_key_id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws_secret_access_key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws_default_region: ${{ vars.AWS_REGION }}

          # Keep these identifiers stable for the lifetime of this environment.
          aws_resource_identifier: blocbuy-team8-backend-dev
          tf_state_file_name: blocbuy-team8-backend-dev
          aws_additional_tags: '{"Project":"blocbuy","Environment":"dev","ManagedBy":"github-actions"}'

          # A Maven/JDK image build plus PG and Redis needs more than the action's small defaults.
          aws_ec2_instance_type: t3.medium
          aws_ec2_instance_root_vol_size: 30
          aws_ec2_instance_root_vol_preserve: true

          # Isolated network for this environment.
          aws_vpc_create: true
          aws_vpc_cidr_block: 10.20.0.0/16
          aws_vpc_public_subnets: 10.20.10.0/24
          aws_vpc_availability_zones: ap-southeast-1a

          # Public HTTP -> backend :8589. Add Route 53/TLS in the later section.
          aws_elb_create: true
          aws_elb_app_port: 8589
          aws_elb_app_protocol: HTTP
          aws_elb_listen_port: 80
          aws_elb_listen_protocol: HTTP
          aws_elb_healthcheck: HTTP:8589/actuator/health

          # The compose file is at the repository root and builds ./backend.
          docker_repo_app_directory: /
          docker_repo_app_directory_cleanup: true
          docker_backup_retention: 5
          docker_remove_orphans: true
          docker_full_cleanup: false
          ansible_start_docker_timeout: 900

          env_ghs: ${{ secrets.BACKEND_DOT_ENV }}

          docker_cloudwatch_enable: true
          docker_cloudwatch_retention_days: 14
```

Notes about the workflow:

- It deploys only after this workflow's `verify` job succeeds. The repository's existing
  `ci-backend.yml` still provides the fuller CI, container smoke test, and image scan.
- The explicit resource and state names prevent a branch rename from silently creating a second
  environment. Changing either value can orphan or duplicate resources.
- `t3.medium` and 30 GiB are starting points, not performance guarantees. The action builds the
  Maven image on EC2, while PostgreSQL and Redis also consume memory and disk.
- `aws_ec2_instance_root_vol_preserve: true` protects the volume from automatic deletion with the
  instance, but an orphaned EBS volume continues to cost money and is not a backup.
- The default hosted-runner path requires the instance to have a public IP so Ansible can reach it.
  Do not disable the public IP unless using a self-hosted runner with private VPC connectivity.
- The action needs SSH during provisioning and enables port 22 by default. Do not add a separate
  `0.0.0.0/0` SSH rule. Inspect the resulting security group after deployment.
- `@v1` follows the supplied guide. For a controlled production pipeline, test a release and replace
  `@v1` with its full commit SHA to prevent upstream tag movement.
- If `ap-southeast-1a` is not available to the account, select an available AZ in the same region and
  keep the VPC subnet configuration consistent.

## 9. Perform the first deployment

1. Commit `docker-compose.yaml`, optional `.gha-ignore`, and the workflow.
2. Open a pull request into `dev` and wait for `ci-backend` to pass.
3. Merge the pull request.
4. Open **Actions -> deploy-backend-ec2** and monitor both jobs.
5. The first run is slower because it provisions AWS resources, installs Docker, downloads base
   images, resolves Maven dependencies, and builds the backend.
6. Open the environment URL recorded by the deploy step.

The action's useful outputs include:

| Output | Meaning |
|---|---|
| `vm_url` | Public application/load-balancer URL |
| `instance_endpoint` | EC2 endpoint |
| `aws_vpc_id` | Selected or created VPC |
| `ec2_sg_id` | EC2 security-group ID |

Do not repeatedly change the resource identifier to work around a failed run. Fix the underlying
failure and rerun with the same identifier so Terraform can converge the same stack.

## 10. Verify the deployed backend

From a terminal, substitute the workflow's `vm_url`:

```bash
curl --fail --show-error "http://<load-balancer-host>/actuator/health"
curl --fail --show-error "http://<load-balancer-host>/v3/api-docs" -o /tmp/blocbuy-openapi.json
curl --fail --show-error --head "http://<load-balancer-host>/swagger-ui.html"
```

Verify in AWS as well:

- the load balancer reports the EC2 target healthy;
- its health check targets `HTTP:8589/actuator/health`;
- the EC2 security group does not expose ports 5432 or 6379;
- PostgreSQL and Redis have no public endpoints;
- Docker logs arrive in the expected CloudWatch log group;
- the root EBS volume is encrypted and has adequate free space;
- AWS tags identify the project and environment.

The Actuator endpoint can be public for this course deployment because only health and basic status
are returned. Before exposing richer Actuator endpoints, restrict them with Spring Security or the
load-balancer/security-group configuration.

## 11. Inspect or debug the EC2 host

Prefer AWS Systems Manager Session Manager if it is configured for the instance. If it is not,
connect only through a tightly restricted temporary SSH rule and remove the rule afterward.

In the copied application directory, the most useful commands are:

```bash
docker compose ps
docker compose logs --tail=200 backend
docker compose logs --tail=100 postgres
docker compose logs --tail=100 redis
docker stats --no-stream
df -h
docker system df
```

Do not print or copy the generated `.env` into an issue, chat, screenshot, or GitHub Actions log.

## 12. Add a domain and HTTPS

First complete and verify the HTTP deployment. Then, if the domain is hosted in Route 53, add these
inputs to the deploy step:

```yaml
          aws_r53_enable: true
          aws_r53_domain_name: example.com
          aws_r53_sub_domain_name: api-dev
          aws_r53_create_sub_cert: true

          aws_elb_app_port: 8589
          aws_elb_app_protocol: HTTP
          aws_elb_listen_port: 443
          aws_elb_listen_protocol: SSL
          aws_elb_healthcheck: HTTP:8589/actuator/health
```

This yields a hostname such as `api-dev.example.com`. The action documentation warns that
Terraform-managed certificates are destroyed with the stack. It also requires a two-run procedure
when changing certificate sources: disable certificate use for one successful run, then configure
the new certificate and enable it in a second run.

After HTTPS works, update the frontend/API client and Stripe webhook endpoint to use the HTTPS URL.
Never configure a live Stripe webhook over plain HTTP.

## 13. Deploy a separate production environment

Never reuse the development identifier, Terraform state, database volume, secrets, or GitHub
Environment for production. Create a separate workflow or parameterized reusable workflow with at
least these changes:

```yaml
environment:
  name: backend-prod

with:
  aws_resource_identifier: blocbuy-team8-backend-prod
  tf_state_file_name: blocbuy-team8-backend-prod
  aws_additional_tags: '{"Project":"blocbuy","Environment":"prod","ManagedBy":"github-actions"}'
  aws_vpc_cidr_block: 10.30.0.0/16
  aws_vpc_public_subnets: 10.30.10.0/24
```

Also:

- create `backend-prod` as a protected GitHub Environment with required reviewers;
- use distinct AWS and application secrets;
- use a production domain and certificate;
- pin the action to a reviewed full commit SHA;
- increase instance/EBS sizes based on measurements;
- enable automated PostgreSQL backups and test restoration;
- consider moving PostgreSQL to RDS and Redis to a managed service;
- deploy a known Git commit or immutable image rather than rebuilding an unpinned branch.

The action's default identifier incorporates organization, repository, and branch. Explicit names are
safer for multiple environments because branch renames otherwise change resource identity.

## 14. Updates and rollback

Normal update:

1. Merge a tested backend or Compose change into `dev`.
2. The workflow verifies the commit.
3. The action copies the new source and runs Compose again.
4. Compose rebuilds/replaces the backend and retains named data volumes.
5. Confirm Actuator health and a representative API request.

Application rollback:

1. Identify the last known-good Git SHA from Actions.
2. Revert the bad commit on the protected branch, or dispatch a reviewed rollback workflow that
   checks out the known-good SHA.
3. Deploy with the **same** resource identifier and Terraform state name.
4. Verify application health and logs.

A code rollback does not reverse Flyway migrations. Database migrations must be backward compatible,
or the rollback must include a separately reviewed database recovery plan. Never delete the
PostgreSQL volume as a rollback technique.

## 15. Database backup and restore expectations

A named Docker volume protects data from routine container replacement, but it does not protect
against EC2/EBS loss, operator error, corruption, or action-driven destruction. Before any risky
deployment, run a logical backup and copy it off the instance:

```bash
docker compose exec -T postgres sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' \
  > "blocbuy-$(date +%Y%m%d-%H%M%S).dump"
```

Do not leave the only backup on the same EC2 root volume. Upload it to an encrypted, versioned S3
bucket with access controls and retention. Periodically restore a backup into a disposable database
and verify Flyway/application startup. For production, automate this process or use RDS automated
backups and snapshots.

## 16. Common failures

### `docker-compose.yaml` not found

The action looks in `docker_repo_app_directory`. Keep the file at the repository root and use `/`, or
move both the file and its relative build paths together.

### Backend Docker build is killed or times out

The Maven build likely exhausted RAM/disk or exceeded the Ansible timeout. Use at least the suggested
`t3.medium`, verify free disk space, and keep `ansible_start_docker_timeout: 900`. The action default
instance/root volume may be too small for this multi-module build plus three running services.

### Backend cannot resolve `postgres` or `redis`

Confirm `SPRING_PROFILES_ACTIVE=docker`, service names are exactly `postgres` and `redis`, and all
three services join the `data` network.

### PostgreSQL authentication fails

`DB_USERNAME` and `DB_PASSWORD` initialize PostgreSQL only when `postgres-data` is empty. Changing
the GitHub secret later does not alter an existing PostgreSQL role password. Change it inside
PostgreSQL in a controlled procedure, then update the deployment secret.

### Flyway fails on `uuidv7()`

The database is older than PostgreSQL 18. Use `postgres:18` or update the migration only after a
database-design review.

### Load balancer target is unhealthy

Check that:

- Compose publishes `8589:8589`;
- `aws_elb_app_port` is `8589`;
- the health-check target is `HTTP:8589/actuator/health`;
- PostgreSQL and Redis are healthy, because their failures can make Spring health report `DOWN`;
- the backend logs show a completed Spring Boot startup.

### A second set of AWS resources appears

The resource identifier or Terraform state name changed. Restore the original values before the next
run and inspect both Terraform states before deleting anything.

### Stripe webhook returns a signature error

Set `STRIPE_WEBHOOK_SECRET` to the signing secret for the exact deployed endpoint. Do not use a Stripe
API secret in place of a webhook signing secret. The public route is
`POST /api/v1/webhooks/stripe`.

## 17. Safe teardown

Teardown is destructive. First create and test an off-host PostgreSQL backup, record the exact
resource identifier/state name, and review resources marked for retention.

The Bitovi action supports `tf_stack_destroy: true`. A separate manually dispatched workflow should
use the same AWS credentials, region, `aws_resource_identifier`, and `tf_state_file_name` as the
deployment:

```yaml
name: destroy-backend-ec2-dev

on:
  workflow_dispatch:
    inputs:
      confirmation:
        description: Type blocbuy-team8-backend-dev to destroy this stack
        required: true

permissions:
  contents: read

jobs:
  destroy:
    if: inputs.confirmation == 'blocbuy-team8-backend-dev'
    runs-on: ubuntu-latest
    environment: backend-dev
    steps:
      - uses: bitovi/github-actions-deploy-docker-to-ec2@v1
        with:
          aws_access_key_id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws_secret_access_key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws_default_region: ${{ vars.AWS_REGION }}
          aws_resource_identifier: blocbuy-team8-backend-dev
          tf_state_file_name: blocbuy-team8-backend-dev
          tf_stack_destroy: true
```

After Terraform finishes, inspect AWS for retained EBS volumes, snapshots, S3 state/log buckets,
CloudWatch log groups, secrets, certificates, and Elastic IPs. `aws_ec2_instance_root_vol_preserve`
can intentionally leave a billable EBS volume behind. Delete retained data only after confirming the
backup and retention requirements.

## 18. Deployment checklist

Before the first deployment:

- [ ] `docker-compose.yaml` exists at repository root.
- [ ] Local Compose configuration, build, and Actuator health checks pass.
- [ ] PostgreSQL is version 18 and Redis requires authentication.
- [ ] No real `.env`, `repo_env`, AWS key, database password, or Stripe secret is committed.
- [ ] AWS Budget and billing alerts exist.
- [ ] A dedicated AWS deployment identity is stored in the protected GitHub Environment.
- [ ] `BACKEND_DOT_ENV` contains the Docker profile and strong, distinct passwords.
- [ ] Resource identifier and Terraform state name are explicit and recorded.
- [ ] The workflow deploys only after verification passes.

After every deployment:

- [ ] GitHub reports the deploy step successful.
- [ ] Load-balancer target status is healthy.
- [ ] `/actuator/health` returns `UP` through the public URL.
- [ ] PostgreSQL and Redis are not publicly exposed.
- [ ] CloudWatch receives Docker logs without secret values.
- [ ] A representative API call succeeds.
- [ ] Production deployments have a current off-host database backup and rollback record.

## References

- [Bitovi: Deploy Docker to AWS VM (EC2)](https://github.com/bitovi/github-actions-deploy-docker-to-ec2)
- [Backend Dockerfile](../../backend/Dockerfile)
- [Spring Boot base configuration](../../backend/blocbuy-bootstrap/src/main/resources/application.yml)
- [Spring Boot Docker profile](../../backend/blocbuy-bootstrap/src/main/resources/application-docker.yml)
- [Backend CI pipeline](../../.github/workflows/ci-backend.yml)
- [Repository AWS development/production design](../design/aws-dev-prod-deployment-guide.md)

The Bitovi action's inputs and defaults can change. Recheck the upstream action README and test an
upgrade in development before changing the pinned action reference or enabling additional resources
such as Route 53, ALB/WAF, EFS, or action-managed RDS.
