# Existing EC2 Backend Deployment Operator Guide

**Status:** current implementation

**Environment:** `backend-dev`

**Provisioning model:** reuse one existing EC2 instance; no Terraform or Bitovi execution

**Application delivery:** GitHub Actions -> Amazon ECR -> AWS Systems Manager Run Command

This is the canonical runbook for the BlocBuy backend deployment. It configures delivery to an EC2
instance that already exists and already hosts PostgreSQL and Redis. It does not create, update, or
destroy EC2, VPC, subnet, security group, load balancer, PostgreSQL, Redis, or storage resources.

For first-time environment setup, follow the step-by-step
[first-deploy bootstrap checklist](backend-first-deploy-bootstrap.md); this guide
remains the authoritative policy reference.

## 1. Architecture and responsibility boundary

```text
Pull request
  ├── ci-backend: build, test, contract, smoke test, Trivy image scan
  └── security: secrets, dependencies, config, workflow and shell policy

push to dev (all gates green and ENABLE_EC2_DEPLOY=true)
  -> GitHub OIDC assumes ECR publisher role
  -> tested image is pushed and resolved to sha256 digest
  -> protected backend-dev environment
  -> GitHub OIDC assumes single-instance SSM deploy role
  -> SSM installs the non-secret bundle under /opt/blocbuy/backend
  -> deploy.sh pulls and starts only blocbuy-backend
  -> health check; automatic application rollback on failure

existing EC2
  ├── PostgreSQL       127.0.0.1:5432 (pre-existing, not pipeline-managed)
  ├── Redis            127.0.0.1:6379 (pre-existing, not pipeline-managed)
  ├── backend          host network, port 8589
  └── runtime secrets  /etc/blocbuy/backend.env, root:root 0600
```

| Pipeline manages | Pipeline does not manage |
|---|---|
| Backend image build, scan, ECR publication and digest | EC2 lifecycle, AMI, EBS, VPC or security groups |
| Backend Compose file and lifecycle scripts | PostgreSQL/Redis containers, services, data or volumes |
| Backend container replacement and health check | Database backup, restore or Flyway reversal |
| Automatic and operator-approved application rollback | DNS, TLS, load balancer or reverse proxy |

The database and Redis boundary is also enforced in CI: the deployment Compose model must contain
exactly one `backend` service and the SSM archive has an exact file allowlist.

## 2. Repository components

| Path | Purpose |
|---|---|
| [`.github/workflows/ci-backend.yml`](../../.github/workflows/ci-backend.yml) | Quality gates, image publication and automatic deployment |
| [`.github/workflows/security.yml`](../../.github/workflows/security.yml) | Secret, dependency, config, workflow and script checks |
| [`.github/workflows/rollback-backend.yml`](../../.github/workflows/rollback-backend.yml) | Controlled manual application rollback |
| [`deployment/backend/compose.yml`](../../deployment/backend/compose.yml) | Backend-only EC2 runtime definition |
| [`deployment/backend/bin/`](../../deployment/backend/bin/) | Preflight, deploy, health and rollback scripts |
| [`deployment/backend/backend.env.example`](../../deployment/backend/backend.env.example) | Non-secret host environment template |
| [`deployment/backend/iam/`](../../deployment/backend/iam/) | Replace-before-use least-privilege IAM examples |

## 3. Values to collect

Record these values in the team password manager or operations record before configuring anything:

| Value | Example |
|---|---|
| AWS account ID | `123456789012` |
| AWS Region | `ap-southeast-1` |
| Existing instance ID | `i-0123456789abcdef0` |
| ECR repository | `blocbuy-backend` |
| Public/backend URL | `https://api-dev.example.com` or blank |
| EC2 instance-role name | `BlocBuyEc2Runtime` |
| GitHub ECR-role name | `BlocBuyGitHubEcrPush` |
| GitHub deploy-role name | `BlocBuyGitHubBackendDeploy` |

Do not record database, Redis, or Stripe secrets in GitHub variables, workflow inputs, IAM files, or
SSM command parameters.

## 4. Prepare Amazon ECR

Create one private repository, enable immutable tags, and retain enough images for rollback:

```bash
aws ecr create-repository \
  --repository-name blocbuy-backend \
  --image-tag-mutability IMMUTABLE \
  --image-scanning-configuration scanOnPush=true \
  --region ap-southeast-1

aws ecr put-lifecycle-policy \
  --repository-name blocbuy-backend \
  --lifecycle-policy-text file://deployment/backend/ecr-lifecycle-policy.json \
  --region ap-southeast-1
```

If the repository already exists, verify it and enable immutability separately:

```bash
aws ecr describe-repositories --repository-names blocbuy-backend --region ap-southeast-1
aws ecr put-image-tag-mutability \
  --repository-name blocbuy-backend \
  --image-tag-mutability IMMUTABLE \
  --region ap-southeast-1
```

The workflow deploys `repository@sha256:...`, never a tag. Tag immutability is an additional control,
not a substitute for digest deployment.

## 5. Prepare the existing EC2 instance role

The instance must be a Systems Manager managed node and must pull from only the backend repository.

1. Attach the AWS-managed `AmazonSSMManagedInstanceCore` policy to the EC2 instance role (or use
   Default Host Management Configuration if that is the account standard).
2. Replace `AWS_REGION`, `AWS_ACCOUNT_ID`, and `ECR_REPOSITORY` in
   [`ec2-ecr-pull-policy.json.example`](../../deployment/backend/iam/ec2-ecr-pull-policy.json.example).
3. Attach the resulting customer-managed policy to the same EC2 role.
4. Confirm that an instance profile containing that role is attached to the existing EC2 instance.

Do not attach `AmazonEC2ContainerRegistryFullAccess` or `AmazonSSMFullAccess`. The host only needs SSM
agent communication and pull access to one ECR repository.

Verify the managed-node connection from an administrator workstation:

```bash
aws ssm describe-instance-information \
  --filters Key=InstanceIds,Values=i-0123456789abcdef0 \
  --region ap-southeast-1
```

The instance must appear online before enabling deployment.

## 6. Configure GitHub OIDC roles

Create the GitHub OIDC provider once per AWS account:

- provider URL: `https://token.actions.githubusercontent.com`;
- audience: `sts.amazonaws.com`.

Create two roles rather than one combined role:

| Role | Trust scope | Permission example |
|---|---|---|
| ECR publisher | This repository's `dev` branch | [`github-ecr-push-policy.json.example`](../../deployment/backend/iam/github-ecr-push-policy.json.example) |
| EC2 deploy/rollback | This repository's `backend-dev` environment | [`github-ssm-deploy-policy.json.example`](../../deployment/backend/iam/github-ssm-deploy-policy.json.example) |

Use the corresponding trust-policy examples in the same directory. Replace every uppercase
placeholder and validate the policies with IAM Access Analyzer before attaching them.

The included trust examples use GitHub's conventional repository subject format. GitHub repositories
created after 15 July 2026, or repositories that opted into immutable OIDC subjects, include owner and
repository IDs in `sub`. Confirm the actual subject format for this repository and update both trust
policies exactly; do not replace it with a repository-wide wildcard.

The deployment trust subject contains the environment, not the branch. Therefore the GitHub
`backend-dev` environment must independently allow only the `dev` branch.

The SSM permission is intentionally limited to `AWS-RunShellScript` and one instance ID. Treat this
role as privileged: SSM Agent executes Linux Run Command with root permissions. Do not grant this role
to PR workflows, forks, IAM users, or unrelated repositories.

## 7. Bootstrap the host

Required software:

- Linux with SSM Agent online;
- Docker Engine and the Docker Compose plugin;
- AWS CLI v2;
- Bash, `awk`, `curl`, `df`, `flock`, `grep`, `install`, `stat`, `timeout` and core utilities;
- at least 2 GiB free under `/opt/blocbuy/backend`;
- PostgreSQL reachable at `127.0.0.1:5432`;
- Redis reachable at `127.0.0.1:6379`.

Create only the secret environment file. The first deployment installs Compose and scripts through
SSM, so cloning the repository on EC2 is not required.

```bash
sudo install -d -m 0750 /etc/blocbuy
sudo install -m 0600 /dev/null /etc/blocbuy/backend.env
sudo chown root:root /etc/blocbuy/backend.env
sudoedit /etc/blocbuy/backend.env
```

Use [`backend.env.example`](../../deployment/backend/backend.env.example) as the source, replace all
placeholders, and verify:

```bash
sudo stat -c '%U:%G %a %n' /etc/blocbuy/backend.env
sudo ss -lnt | grep -E '127\.0\.0\.1:(5432|6379)'
```

Expected permissions are `root:root 600`. PostgreSQL and Redis must not be exposed to the public
internet. Port `8589` must not be reachable from the public internet: deploy the CloudFront + ALB
entry layer per [dev CloudFront deployment](dev-cloudfront-deployment.md) and allow inbound
`8589` only from the ALB security group; do not open database/cache ports in the EC2 security group.

After the first workflow has installed the bundle, run:

```bash
sudo /opt/blocbuy/backend/bin/preflight.sh
```

## 8. Configure GitHub

Create the `backend-dev` GitHub environment first. Allow deployments only from `dev`; add a required
reviewer if the team wants approval-gated development deployment.

### Repository Actions variables

These must be repository variables because the image-publishing job does not use an environment:

| Variable | Example | Required |
|---|---|---|
| `AWS_ACCOUNT_ID` | `123456789012` | yes |
| `AWS_REGION` | `ap-southeast-1` | yes |
| `ECR_REPOSITORY_DEV` | `dev/blocbuy` | yes |
| `AWS_ECR_PUSH_ROLE_ARN` | `arn:aws:iam::123456789012:role/BlocBuyGitHubEcrPush` | yes |
| `ENABLE_EC2_DEPLOY` | `false`, then `true` after validation | yes |

### `backend-dev` environment variables

| Variable | Example | Required |
|---|---|---|
| `AWS_DEPLOY_ROLE_ARN_DEV` | `arn:aws:iam::123456789012:role/BlocBuyGitHubBackendDeploy` | yes |
| `BACKEND_EC2_INSTANCE_ID_DEV` | `i-0123456789abcdef0` | yes |
| `BACKEND_URL_DEV` | `https://api-dev.example.com` | optional |

No AWS access key, secret access key, `BACKEND_DOT_ENV`, database password, Redis password, or Stripe
key belongs in GitHub. AWS access is issued temporarily through OIDC; application secrets remain on
the host.

### Branch protection

Protect `dev` and require a pull request, CODEOWNERS review, no force push, and these checks:

- `build-quality`, `contract-check`, `docker`;
- `workflow-lint`, `deployment-bundle`, `gitleaks`, `trivy-deps`, `trivy-config`;
- `codeql` only after GHAS is enabled consistently.

The deployment job is push-only and should not be a PR-required check.

## 9. First deployment

1. Keep `ENABLE_EC2_DEPLOY=false` while validating ECR, IAM, SSM and the host.
2. Merge the deployment changes into protected `dev` through a pull request.
3. Confirm every CI and security check is green.
4. Set `ENABLE_EC2_DEPLOY=true`.
5. Push or merge a backend/deployment change to `dev`.
6. Approve the `backend-dev` environment if reviewers are configured.
7. Inspect the `deploy-existing-ec2` job summary and SSM command output.

The job publishes the image that was actually smoke-tested, captures its digest, sends the non-secret
bundle to the one configured instance, and waits for `/api/actuator/health` to report `UP`.

Verify independently through the CloudFront distribution (see
[dev CloudFront deployment](dev-cloudfront-deployment.md)):

```bash
curl --fail --silent https://<cloudfront-domain>/api/actuator/health

aws ssm get-command-invocation \
  --command-id '<command-id-from-job-summary>' \
  --instance-id i-0123456789abcdef0 \
  --region ap-southeast-1
```

On the host, the current digest is recorded in
`/opt/blocbuy/backend/state/current-image`. Deployment never restarts or removes the PostgreSQL or
Redis service.

## 10. Normal release and automatic recovery

For each backend or deployment change:

1. open a PR to `dev`;
2. obtain review and pass all required checks;
3. merge;
4. approve `backend-dev` if configured;
5. verify the digest and health in the job summary.

If the new container does not become healthy, `deploy.sh` attempts to restore the previously running
digest and leaves the workflow red. Investigate the failed deployment even when automatic rollback
succeeds.

For viewing and searching runtime logs (CloudWatch Logs Insights, traceId-based search, SSM port
forwarding, and the on-box `docker logs` fallback), see the
[backend log observability guide](backend-log-observability.md).

## 11. Manual rollback

From **Actions -> rollback-backend -> Run workflow**:

1. select the `dev` branch;
2. leave `target_image` blank to use the recorded previous digest, or paste a reviewed
   `repository@sha256:...` from the same configured ECR repository;
3. enter an incident/change reason;
4. select the confirmation checkbox;
5. approve the `backend-dev` environment if required;
6. verify the resulting digest and health in the summary.

Rollback and deployment share the same non-cancellable concurrency group, so they cannot change the
host simultaneously. Tags, other repositories, other accounts, and other Regions are rejected.

An application rollback does not reverse a Flyway migration. Every automatically deployed migration
must remain backward-compatible. Destructive schema recovery requires a separate reviewed database
backup/restore plan.

## 12. Disable delivery safely

For an incident freeze, set `ENABLE_EC2_DEPLOY=false`. CI, security scans and image smoke tests still
run, but new images are not published or deployed. The manual rollback workflow remains available for
recovery and still requires its confirmation and environment controls.

Disabling delivery does not stop the existing backend, PostgreSQL, or Redis.

## 13. Troubleshooting

| Symptom | Check |
|---|---|
| ECR role cannot be assumed | OIDC provider audience, exact branch `sub`, repository variable ARN |
| Deploy role cannot be assumed | Exact environment `sub`, `backend-dev` branch rule, role ARN |
| SSM command cannot be sent | Instance ID/Region, `AWS-RunShellScript` and instance ARNs in policy |
| Instance not online | SSM Agent, instance profile, SSM endpoints/NAT, managed-node status |
| ECR pull denied | EC2 role has token plus pull actions for the exact repository |
| Preflight rejects environment | File exists, `root:root 600`, required keys non-empty, no placeholders |
| PostgreSQL/Redis unreachable | Both listen on host loopback at ports 5432/6379 |
| Backend unhealthy | SSM stdout/stderr, `docker logs blocbuy-backend`, Flyway and credentials |
| Bundle policy fails | Only `compose.yml` and the four reviewed scripts may be transported |
| Rollback has no previous image | Supply an explicitly reviewed immutable digest from ECR |

Useful host diagnostics:

```bash
sudo docker ps --filter name=blocbuy-backend
sudo docker logs --tail 200 blocbuy-backend
sudo cat /opt/blocbuy/backend/state/current-image
curl --fail --silent http://127.0.0.1:8589/api/actuator/health
```

Do not paste `/etc/blocbuy/backend.env`, Docker credential files, or application secrets into GitHub
issues or workflow logs.

## 14. Audit evidence and limitations

Retain these as DevSecOps evidence:

- PR review and protected-branch history;
- green quality/security checks and SARIF results where GHAS is available;
- immutable ECR digest in the release and deployment summaries;
- GitHub environment approval and named operator;
- SSM command ID/status and CloudTrail role-assumption/SendCommand records;
- automatic/manual rollback output and resulting digest;
- database backup/restore test evidence maintained outside this application pipeline.

This phase deliberately codifies application delivery, policy gates and IAM examples but not EC2/VPC
resource creation. The existing infrastructure still has manual drift risk. Record host/IAM/network
changes in the team change log. A later, separately reviewed Terraform phase may import or recreate
infrastructure only after state ownership, backups and destructive-change controls are agreed.

## 15. Authoritative references

- [GitHub: Configuring OIDC in AWS](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-aws)
- [GitHub: Deployments and environments](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments)
- [AWS: IAM permissions for pushing to ECR](https://docs.aws.amazon.com/AmazonECR/latest/userguide/image-push-iam.html)
- [AWS: Configure EC2 permissions for Systems Manager](https://docs.aws.amazon.com/systems-manager/latest/userguide/setup-instance-permissions.html)
- [AWS: Systems Manager IAM policy examples](https://docs.aws.amazon.com/systems-manager/latest/userguide/security_iam_id-based-policy-examples.html)
- [AWS: Restrict root-level commands through SSM Agent](https://docs.aws.amazon.com/systems-manager/latest/userguide/ssm-agent-restrict-root-level-commands.html)
