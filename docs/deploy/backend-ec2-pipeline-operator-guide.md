# BlocBuy Backend CI/CD to AWS EC2: Complete Operator Guide

This is an implementation guide intended to be followed step by step by an operator. Starting from the
repository's current state, the goal is to implement the following automated flow:

```text
Open a PR from a feature branch to dev
  -> Quality, contract, image, and security gates
  -> Review and merge the PR
  -> Run the backend gates again on the dev branch
  -> All gates pass
  -> Bitovi Action creates or updates AWS resources
  -> Copy the repository to EC2 and run Docker Compose
  -> ELB checks /actuator/health
  -> Automated deployment completes
```

This guide deploys the `backend-dev` development environment. PostgreSQL, Redis, and the backend run on
the same EC2 instance. This setup is suitable for a course project, development, and demonstrations; it
is not a highly available production architecture.

## 1. Current Baseline and Remaining Work

The remote repository is `PPsyrius/iss-groupbuy-team8`, and the backend integration branch is `dev`.

Already available:

- `.github/workflows/ci-backend.yml`
  - `build-quality`: formatting, Checkstyle, tests, ArchUnit, SpotBugs, and JaCoCo;
  - `contract-check`: OpenAPI drift and breaking-change checks;
  - `sonar`: runs only when `SONAR_HOST_URL` is configured;
  - `docker`: builds the image, performs smoke tests with PostgreSQL 18 and Redis 8.10, and scans the image
    with Trivy.
- `.github/workflows/security.yml`: Gitleaks, Trivy dependency scanning, and optional CodeQL.
- `backend/Dockerfile`: Java 21, multi-stage build, non-root user, port `8589`.
- Docker Spring profile: connects to `postgres:5432` and `redis:6379`.
- Health check: `GET /actuator/health`.

You still need to:

- Create `docker-compose.yaml` and `.gha-ignore` in the repository root;
- Add the EC2 deployment job to the existing `ci-backend.yml`;
- Create the AWS deployment identity, budget, and credentials;
- Create the GitHub Environment, Secrets, and Variables;
- Configure branch protection for `dev`;
- Submit and merge a PR, observe the automated deployment, and verify it.

Final dependency graph:

```text
ci-backend.yml
├── build-quality ─────┬── docker ──────────────┐
├── contract-check ────┼────────────────────────┤
└── sonar (success or skipped) ─────────────────┤
                                                └── deploy-ec2 (push to dev only)

security.yml
├── gitleaks       ── required PR check
└── trivy-deps     ── required PR check
```

## 2. Fixed Deployment Parameters

Record the following values before proceeding. Do not change resource identifiers or Terraform state
names casually after they have been used.

| Parameter | Recommended value |
|---|---|
| GitHub Environment | `backend-dev` |
| Automatic deployment branch | `dev` |
| AWS Region | `ap-southeast-1` |
| AWS Availability Zone | `ap-southeast-1a` |
| AWS resource identifier | `ppsyrius-iss-groupbuy-team8-backend-dev` |
| Terraform state name | `ppsyrius-iss-groupbuy-team8-backend-dev` |
| VPC CIDR | `10.20.0.0/16` |
| Public subnet | `10.20.10.0/24` |
| EC2 | `t3.medium`, 30 GiB root volume |
| Database | PostgreSQL 18, database name `Blocbuy` |
| Redis | Redis 8.10 |
| Backend port | `8589` |

You must confirm that the Availability Zone belongs to the selected Region and that the public subnet is
within the VPC CIDR. PostgreSQL must be version 18 because the current migrations use `uuidv7()`.

## 3. AWS Setup

### 3.1 Account Security

1. Sign in to the AWS Console;
2. Enable MFA for the root user;
3. Confirm that the root user has no access keys;
4. Use a dedicated IAM identity for all subsequent steps, not the root user.

AWS officially recommends that you do not create access keys for the root user and that you prefer
temporary credentials. The current Bitovi interface accepts an Access Key ID, Secret Access Key, and an
optional Session Token, so you must use a dedicated deployment identity or temporary lab credentials.

### 3.2 Create a Cost Budget

Open **Billing and Cost Management -> Budgets -> Create budget**:

1. Select **Cost budget** and **Monthly**;
2. Enter the monthly limit acceptable to the team;
3. Add Actual cost email alerts at 50%, 80%, and 100%;
4. Enter the team email address and confirm the notification.

EC2, EBS, Classic Load Balancer, S3, CloudWatch, and data transfer can all incur charges.

### 3.3 Create a Deployment Identity

The recommended approach is to have an AWS administrator provide a dedicated deployment identity. It
must be able to manage the EC2, VPC, subnet, security group, ELB, IAM Instance Profile, S3 Terraform
state/logging, and CloudWatch resources enabled by this workflow.

If this is an isolated, short-lived course account with no other workloads, you can perform an initial
validation as follows:

1. Go to **IAM -> Users -> Create user**;
2. Use `github-actions-blocbuy-dev` as the username and do not enable Console access;
3. For the first experiment, attach a broad policy approved by the team;
4. Go to **Security credentials -> Create access key -> Third-party service**;
5. Save the Access Key ID and Secret Access Key immediately; the Secret is shown only once;
6. After validating the deployment, narrow the permissions based on CloudTrail and Terraform activity,
   and set a credential rotation date.

Do not retain `AdministratorAccess` long-term in a shared or production account. If the required
permissions are unclear, ask an AWS administrator to review them instead of repeatedly adding unknown
permissions.

When using AWS Academy, you will usually also receive an `AWS_SESSION_TOKEN`. When temporary credentials
expire, you must update all three GitHub Secrets together, or the automated deployment will fail to
authenticate.

## 4. Prepare the Repository Files

### 4.1 Update the Feature Branch

```bash
git status
git fetch origin
git checkout feature/backend-structure-20260830
git pull --ff-only origin feature/backend-structure-20260830
```

If the team has created a different deployment feature branch, use that branch instead. Do not overwrite
existing working-tree changes.

### 4.2 Create `docker-compose.yaml` in the Repository Root

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
      - postgres-data:/var/lib/postgresql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U \"$$POSTGRES_USER\" -d \"$$POSTGRES_DB\""]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 20s
    networks: [data]

  redis:
    image: redis:8.10
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
    networks: [data]

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

Notes: PostgreSQL and Redis do not expose host ports; backend port `8589:8589` is available to the ELB;
do not set a separate Compose logging driver so the Action's CloudWatch logging configuration can be
used; named volumes are not backups; do not enable `docker_full_cleanup`.

### 4.3 Create `.gha-ignore` in the Repository Root

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

Do not exclude the Compose file, Dockerfile, Maven Wrapper, POM files, or any backend module.

### 4.4 Validate Locally

Generate two different passwords:

```bash
openssl rand -hex 32
openssl rand -hex 32
```

Create a local-only `.env` in the repository root. It must not be committed:

```dotenv
SPRING_PROFILES_ACTIVE=docker
DB_USERNAME=blocbuy
DB_PASSWORD=<first-random-password>
REDIS_PASSWORD=<second-random-password>
STRIPE_API_KEY=
STRIPE_WEBHOOK_SECRET=
```

Run:

```bash
docker compose config --quiet
docker compose build backend
docker compose up -d
docker compose ps
curl --fail --show-error http://localhost:8589/actuator/health
```

Success criteria: PostgreSQL and Redis are healthy, the backend is running, and the health endpoint
returns `{"status":"UP"}`. If validation fails, run:

```bash
docker compose logs --tail=200 backend
docker compose logs --tail=100 postgres
docker compose logs --tail=100 redis
```

After validation, run `docker compose down`. Do not use `--volumes`. Before committing, confirm again
that `.env` is ignored.

## 5. Integrate with the Existing `ci-backend.yml`

### 5.1 Expand the Trigger Paths

Replace the `on` block at the top of `.github/workflows/ci-backend.yml` with:

```yaml
on:
  push:
    branches: [dev]
    paths:
      - "backend/**"
      - "contracts/**"
      - "docker-compose.yaml"
      - ".gha-ignore"
      - ".github/workflows/ci-backend.yml"
  pull_request:
    branches: [dev]
    paths:
      - "backend/**"
      - "contracts/**"
      - "docker-compose.yaml"
      - ".gha-ignore"
      - ".github/workflows/ci-backend.yml"
```

Otherwise, changes limited to the Compose file will not trigger CI or deployment.

### 5.2 Append `deploy-ec2` to the End of the File

It must be at the same level as the existing four jobs; do not place it inside `docker.steps`.

```yaml
  deploy-ec2:
    name: deploy-ec2
    needs:
      - build-quality
      - contract-check
      - sonar
      - docker
    if: >-
      always() &&
      github.event_name == 'push' &&
      github.ref == 'refs/heads/dev' &&
      needs.build-quality.result == 'success' &&
      needs.contract-check.result == 'success' &&
      needs.docker.result == 'success' &&
      (needs.sonar.result == 'success' || needs.sonar.result == 'skipped')
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
          aws_session_token: ${{ secrets.AWS_SESSION_TOKEN }}
          aws_default_region: ${{ vars.AWS_REGION }}

          aws_resource_identifier: ppsyrius-iss-groupbuy-team8-backend-dev
          tf_state_file_name: ppsyrius-iss-groupbuy-team8-backend-dev
          aws_additional_tags: '{"Project":"blocbuy","Environment":"dev","ManagedBy":"github-actions"}'

          aws_ec2_instance_type: t3.medium
          aws_ec2_instance_root_vol_size: 30
          aws_ec2_instance_root_vol_preserve: true
          aws_ec2_instance_public_ip: true

          aws_vpc_create: true
          aws_vpc_cidr_block: 10.20.0.0/16
          aws_vpc_public_subnets: 10.20.10.0/24
          aws_vpc_availability_zones: ${{ vars.AWS_AVAILABILITY_ZONE }}

          aws_elb_create: true
          aws_elb_app_port: 8589
          aws_elb_app_protocol: HTTP
          aws_elb_listen_port: 80
          aws_elb_listen_protocol: HTTP
          aws_elb_healthcheck: HTTP:8589/actuator/health

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

Behavior: it is skipped for PRs and deploys only on a push to `dev`; all three required gates must
succeed; Sonar may be skipped when it is not configured, but must succeed once configured.

`@v1` matches the current Bitovi documentation. After the development deployment has proven stable,
consider pinning it to a full commit SHA reviewed by the team.

### 5.3 Expand the Paths in `security.yml`

Add the following to both `push.paths` and `pull_request.paths` in
`.github/workflows/security.yml`:

```yaml
      - "docker-compose.yaml"
      - ".github/workflows/ci-backend.yml"
```

The recommended complete path block for each event is:

```yaml
    paths:
      - "backend/**"
      - "docker-compose.yaml"
      - ".gitleaks.toml"
      - ".github/workflows/ci-backend.yml"
      - ".github/workflows/security.yml"
```

The security workflow runs independently and cannot be referenced directly with `needs` from another
workflow. Use branch protection to ensure that it succeeds before merging.

## 6. Create the GitHub Environment

Go to **Settings -> Environments -> New environment** and enter `backend-dev` as the name.

1. Under Deployment branches and tags, allow only `dev`;
2. For fully automatic deployment, do not configure Required reviewers;
3. If a reviewer is configured, the workflow starts automatically but pauses for approval;
4. Some GitHub plans restrict Environment secrets and reviewers for private repositories. If those
   controls are unavailable, fall back to Repository Actions secrets, but retain branch protection and
   the deploy job's push/`dev` condition.

## 7. Configure GitHub Variables and Secrets

Go to **Settings -> Environments -> backend-dev**.

### 7.1 Variables

| Name | Value |
|---|---|
| `AWS_REGION` | `ap-southeast-1` |
| `AWS_AVAILABILITY_ZONE` | `ap-southeast-1a` |

If you select another Region, you must update both values together.

### 7.2 AWS Secrets

| Name | Value |
|---|---|
| `AWS_ACCESS_KEY_ID` | Access Key ID for the deployment identity |
| `AWS_SECRET_ACCESS_KEY` | Corresponding Secret Access Key |
| `AWS_SESSION_TOKEN` | Required only for temporary credentials |

Do not create `AWS_SESSION_TOKEN` when using long-lived IAM User credentials. Do not put Secrets in
Variables, source code, Issues, or PRs.

### 7.3 Application Secret

Create `BACKEND_DOT_ENV` with the following contents:

```dotenv
SPRING_PROFILES_ACTIVE=docker
DB_USERNAME=blocbuy
DB_PASSWORD=<long-random-database-password>
REDIS_PASSWORD=<different-long-random-Redis-password>
STRIPE_API_KEY=
STRIPE_WEBHOOK_SECRET=
```

Requirements: the passwords must differ, there must be no placeholders, lines must not begin with
`export`, and there must be no spaces around the equals sign. Leave Stripe values empty when Stripe is
not integrated; when it is integrated, start with test mode. The webhook path is
`/api/v1/webhooks/stripe`.

Bitovi merges `env_ghs` into the `.env` file on EC2. Do not commit an actual `repo_env` or `.env` file.

### 7.4 Optional Sonar/CodeQL

- When `SONAR_HOST_URL` is not set, Sonar is skipped and deployment continues;
- Once it is set, you must also configure `SONAR_TOKEN`; SonarCloud additionally requires
  `SONAR_ORGANIZATION`;
- A failed Sonar Quality Gate blocks deployment;
- CodeQL is enabled only when `ENABLE_CODEQL=true`; private repositories usually require GitHub Advanced
  Security.

Do not configure only some of the Sonar parameters.

## 8. Actions Permissions and Branch Protection

### 8.1 Actions Permissions

Go to **Settings -> Actions -> General**. Confirm that Actions is enabled. If only selected Actions are
allowed, allow at least:

- `actions/checkout`, `actions/setup-java`, `actions/cache`, and `actions/upload-artifact`;
- `aquasecurity/trivy-action`;
- `github/codeql-action`;
- `gitleaks/gitleaks-action`;
- `bitovi/github-actions-deploy-docker-to-ec2`.

### 8.2 Protect the `dev` Branch

Go to **Settings -> Rules -> Rulesets** and configure the following for `dev`:

1. Require changes to be merged through a PR;
2. Require at least one approval;
3. Require status checks;
4. Require the branch to be up to date before merging;
5. Prevent regular members from pushing directly to `dev`;
6. Preferably, do not allow unrecorded bypasses.

Let the checks run on this PR once before selecting their exact names. Require at least:

```text
build-quality
contract-check
docker
gitleaks
trivy-deps
```

After Sonar is fully configured, also make `sonar` required. Do not require CodeQL while it is skipped by
default.

## 9. Submit the Configuration PR

First, confirm that no secrets have entered Git:

```bash
git status --short
git status --ignored --short
git diff --check
git diff -- docker-compose.yaml .gha-ignore .github/workflows/ci-backend.yml .github/workflows/security.yml
```

Then run:

```bash
git add docker-compose.yaml .gha-ignore
git add .github/workflows/ci-backend.yml .github/workflows/security.yml
git add docs/deploy/backend-ec2-pipeline-operator-guide.md
git diff --cached --check
git diff --cached
git commit -m "feat(ci): deploy backend to EC2 after quality gates"
git push origin feature/backend-structure-20260830
```

If you use another feature branch, replace the branch name in the final command. On GitHub, create a PR
with `dev` as the base and the actual feature branch as the compare branch. Record the AWS Region, fixed
resource identifier, local health-check result, cost risks, and rollback owner in the PR, but never
record credentials.

## 10. Verify the PR Stage

After creating the PR, you should see:

| Job | Expected result |
|---|---|
| `build-quality` | Succeeds |
| `contract-check` | Succeeds |
| `docker` | Build, health check, and Trivy succeed |
| `sonar` | Skipped when unconfigured; succeeds when configured |
| `gitleaks` | Succeeds |
| `trivy-deps` | Succeeds |
| `deploy-ec2` | **Skipped** |

Skipping deployment at the PR stage is correct. If Gitleaks detects a real secret, rotate it immediately;
removing it only from the latest commit does not eliminate its exposure in Git history.

## 11. Merge and Trigger the First Automated Deployment

Before merging, confirm that:

- The Budget has been created;
- The `backend-dev` Environment exists;
- Secrets and Variables are saved, and temporary credentials have not expired;
- All required PR checks have succeeded;
- No other deployment is running.

After merging into `dev`:

1. GitHub emits a push event;
2. `ci-backend` runs from the beginning;
3. `deploy-ec2` waits for its four dependency jobs;
4. Bitovi creates the VPC, ELB, EC2, state, and logging resources;
5. The Action installs Docker, copies the repository, generates `.env`, and runs Compose;
6. On success, the GitHub Environment displays `vm_url`.

The first run creates infrastructure, downloads images, resolves Maven dependencies, and builds on EC2,
so it is usually noticeably slower than subsequent deployments. Do not change the resource identifier
and rerun merely because it is slow.

## 12. Post-Deployment Verification

Under **Actions -> ci-backend -> latest dev push -> deploy-ec2**, record `vm_url`. Then run:

```bash
curl --fail --show-error "http://<ELB_HOST>/actuator/health"
curl --fail --show-error "http://<ELB_HOST>/v3/api-docs" -o /tmp/blocbuy-openapi.json
curl --fail --show-error --head "http://<ELB_HOST>/swagger-ui.html"
```

The health endpoint must return `{"status":"UP"}`.

Check the following in the AWS Console:

1. The EC2 instance is running, with the correct type, volume, and tags;
2. The Classic ELB forwards HTTP port 80 to port 8589, with the health check set to
   `HTTP:8589/actuator/health`;
3. The instance is healthy/in service in the ELB;
4. The security group does not expose ports 5432 or 6379 to the internet, and nobody has manually added
   `0.0.0.0/0:22`;
5. The CloudWatch Docker log group exists, has 14-day retention, and contains no secrets;
6. Block Public Access is enabled on the S3 Terraform state/log bucket;
7. Do not manually delete Terraform state objects.

Record non-sensitive deployment evidence: Git SHA, Actions Run URL, Region, EC2 ID, ELB DNS name,
verification time, and operator.

## 13. Common Failure Handling

| Symptom | Resolution |
|---|---|
| `ExpiredToken`/`InvalidClientTokenId` | Update all three temporary lab credentials, or check whether the IAM key is Active |
| `AccessDenied` | Record the denied API/ARN and ask an administrator to add the minimum permission; do not change resource names to bypass the issue |
| Availability Zone failure | Confirm that the Region and AZ match and that the AZ supports `t3.medium` |
| Compose file not found | Confirm that the file is in the repository root, `docker_repo_app_directory: /` is set, and `.gha-ignore` does not exclude it |
| Build killed or timed out | Check EC2 memory, disk, and network; retain the 900-second timeout and increase the instance size if needed |
| Cannot resolve postgres/redis | Confirm the Docker profile, service names, and `data` network |
| ELB unhealthy | Confirm that Spring, Dockerfile, Compose, and ELB all use port 8589, then inspect the backend startup logs |
| Sonar causes deployment to be skipped | Configure Token/Organization completely and fix the Gate; if not using Sonar yet, remove `SONAR_HOST_URL` |

The database password applies only when an empty volume is initialized for the first time. Changing the
GitHub Secret later does not automatically update the PostgreSQL role password. You must first change the
password in the database under controlled conditions and then update the Secret; do not solve this by
deleting the data volume.

## 14. EC2 Diagnostics, Backup, and Rollback

Prefer AWS Systems Manager Session Manager. If SSH is required, allow only the operator's single public
IP temporarily and remove the rule afterward. Common commands in the application directory:

```bash
docker compose ps
docker compose logs --tail=200 backend
docker compose logs --tail=100 postgres
docker compose logs --tail=100 redis
docker stats --no-stream
df -h
docker system df
```

Do not run `docker compose down --volumes` or `docker system prune` with `--volumes`, and do not print the
`.env` file generated by the Action.

Back up before a high-risk release:

```bash
docker compose exec -T postgres sh -c \
  'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' \
  > "blocbuy-$(date +%Y%m%d-%H%M%S).dump"
```

Copy the backup to an encrypted, versioned, access-restricted S3 bucket; do not leave it only on the same
EC2 root volume. Perform restore drills regularly.

For a code rollback, find the most recent successful SHA, create a `git revert` for the faulty commit,
merge it into `dev` through a PR, and let the pipeline redeploy. Keep the resource identifier and state
name unchanged. A code rollback does not roll back Flyway; database migrations require a separate plan.

## 15. Routine Automated Deployment Flow

After the initial configuration is complete:

1. Open a PR from a feature branch to `dev`;
2. Pass the required checks and review;
3. Merge the PR;
4. The push to `dev` automatically runs `ci-backend`;
5. `deploy-ec2` automatically updates the same EC2 environment;
6. Check Actions, ELB health, and representative APIs;
7. Record the SHA and verification result.

The current CI concurrency setting cancels older runs for the same ref. Avoid merging multiple high-risk
deployment changes in quick succession; wait for the current deployment to complete.

## 16. HTTPS Phase Two

Complete the initial HTTP deployment first. If the domain is hosted in Route 53, add the following to the
deploy job afterward:

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

Replace the example with the real domain. The Bitovi documentation notes that Terraform-managed
certificates are destroyed with the stack; follow its two-phase process when changing certificate
sources. After HTTPS is enabled, configure the Stripe Webhook as:

```text
https://api-dev.example.com/api/v1/webhooks/stripe
```

## 17. Safe Teardown

Before teardown, confirm the resource identifier, complete an off-instance database backup, obtain team
approval, and record which EBS, S3, and logging resources must be retained. Bitovi uses
`tf_stack_destroy: true` to destroy the stack. The teardown workflow must use the same Region, resource
identifier, and state name as the deployment.

After teardown, check for and handle EBS volumes, snapshots, S3 buckets, CloudWatch logs, Secrets Manager
secrets, ACM certificates, Elastic IPs, and IAM Instance Profiles that continue to incur charges. Do not
directly delete unknown Terraform state, or remaining resources will lose their management relationship.

## 18. Final Completion Checklist

- [ ] Root-user MFA, dedicated deployment identity, and AWS Budget are complete;
- [ ] `docker-compose.yaml` builds locally and passes the health check;
- [ ] `.env`, AWS keys, database passwords, and Stripe keys have not entered Git;
- [ ] `ci-backend.yml` includes the Compose path and `deploy-ec2`;
- [ ] `security.yml` covers deployment configuration changes;
- [ ] The `backend-dev` Environment, AWS Secrets, `BACKEND_DOT_ENV`, and Region/AZ are configured;
- [ ] `dev` branch protection and the five required checks are configured;
- [ ] `deploy-ec2` is correctly skipped at the PR stage;
- [ ] `deploy-ec2` runs automatically and succeeds after merge;
- [ ] The ELB is healthy and the public health endpoint returns `UP`;
- [ ] Ports 5432/6379 are not exposed, and CloudWatch contains no secrets;
- [ ] Evidence from the first deployment, key rotation, database backup, and rollback ownership are
  recorded.

## References

- [Bitovi Deploy Docker to AWS EC2](https://github.com/bitovi/github-actions-deploy-docker-to-ec2)
- [GitHub Deployments and Environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments)
- [GitHub Actions Secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)
- [AWS Root User Best Practices](https://docs.aws.amazon.com/IAM/latest/UserGuide/root-user-best-practices.html)
- [AWS Secure Access Keys](https://docs.aws.amazon.com/IAM/latest/UserGuide/securing_access-keys.html)
- [AWS: Creating a Cost Budget](https://docs.aws.amazon.com/cost-management/latest/userguide/create-cost-budget.html)
- [Backend Pipeline Guide for This Repository](../ci/backend-pipeline.md)
- [Backend Quality Gates for This Repository](../quality/quality-gates.md)
- [English EC2 Deployment Reference](backend-ec2-bitovi.md)

Third-party Action parameters and defaults may change. Before changing `@v1`, enabling Route 53, ALB,
WAF, EFS, or RDS, or running `tf_stack_destroy`, recheck the upstream Bitovi documentation and validate
the change in the development environment first.
