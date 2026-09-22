# First-Deploy One-Time Bootstrap Checklist

**Status:** one-time bootstrap completed on dev (delivery pipeline green); retained as first-deployment evidence. For steady-state policy see the operator guide — if they conflict, the guide wins.

**Use case:** setting up the deployment environment from scratch and completing the first pipeline deployment. Every step is executed exactly once.

**Authoritative reference:** for policy, rationale, and security constraints see the [existing EC2 backend deployment operator guide](backend-ec2-pipeline-operator-guide.md). If this checklist ever conflicts with that guide, the guide wins.

**Environment boundary:** reuses the existing EC2 instance and its PostgreSQL/Redis; this checklist creates and modifies no infrastructure resources.

Work through sections 0-6 in order. Sections 0-3 and 5 run on an **administrator workstation** (AWS credentials with IAM/EC2/ECR administrative permissions); section 4 runs on the **EC2 host**; section 6 is performed on **GitHub**.

## 0. Preparation: record values and set variables

Define the variables in your local shell first so later commands can be copy-pasted directly:

```bash
ACCOUNT_ID=402178232802            # replace: AWS Account ID
REGION=ap-southeast-1              # replace: region
INSTANCE_ID=i-0852b6c3475b19f05    # replace: existing EC2 Instance ID
ECR_REPOSITORY=dev/blocbuy
EC2_ROLE=BlocBuyEc2Runtime
ECR_PULL_POLICY=BlocBuyEc2EcrPull
GITHUB_ECR_ROLE=BlocBuyGitHubEcrPush
GITHUB_DEPLOY_ROLE=BlocBuyGitHubBackendDeploy
```

| Value | Example in this checklist |
|---|---|
| AWS Account ID | `123456789012` |
| Region | `ap-southeast-1` |
| Existing EC2 Instance ID | `i-0123456789abcdef0` |
| ECR Repository | `dev/blocbuy` |
| EC2 Runtime Role | `BlocBuyEc2Runtime` |
| GitHub ECR Role | `BlocBuyGitHubEcrPush` |
| GitHub Deploy Role | `BlocBuyGitHubBackendDeploy` |

Also prepare a working directory outside the repository to hold the IAM JSON pending placeholder substitution (the rendered IAM examples must not be left inside the repository):

```bash
mkdir -p ~/blocbuy-bootstrap && cd ~/blocbuy-bootstrap
cp <repo-path>/deployment/backend/iam/*.json.example .
```

Shell variables do not survive across sessions or hosts (CloudShell and a local workstation each need their own copy). Persist the section 0 variables to `env.sh` in the same directory and source it once per new session:

```bash
cat > ~/blocbuy-bootstrap/env.sh <<'EOF'
export ACCOUNT_ID=402178232802
export REGION=ap-southeast-1
export INSTANCE_ID=i-0852b6c3475b19f05
export ECR_REPOSITORY=dev/blocbuy
export EC2_ROLE=BlocBuyEc2Runtime
export ECR_PULL_POLICY=BlocBuyEc2EcrPull
export GITHUB_ECR_ROLE=BlocBuyGitHubEcrPush
export GITHUB_DEPLOY_ROLE=BlocBuyGitHubBackendDeploy
EOF

source ~/blocbuy-bootstrap/env.sh
# To auto-load in every new session (CloudShell is bash, a local Mac is zsh):
# echo 'source ~/blocbuy-bootstrap/env.sh' >> ~/.bashrc
```

Before running any later section's commands, confirm the variables are defined — when a variable is empty, `${ECR_REPOSITORY}` expands to an empty string, and sed will not error but will produce a broken policy:

```bash
printf 'account=%s region=%s repo=%s\n' "402178232802" "ap-southeast-1" "dev/blocbuy"
```

## 1. Create the ECR repository

- [ ] Repository exists, tags immutable, scan on push enabled, lifecycle policy applied

```bash
aws ecr create-repository \
  --repository-name "dev/blocbuy" \
  --image-tag-mutability IMMUTABLE \
  --image-scanning-configuration scanOnPush=true \
  --region "ap-southeast-1"

aws ecr put-lifecycle-policy \
  --repository-name "dev/blocbuy" \
  --lifecycle-policy-text file://<repo-path>/deployment/backend/ecr-lifecycle-policy.json \
  --region "ap-southeast-1"
```

If the repository already exists: verify it with `aws ecr describe-repositories` and set `IMMUTABLE` retroactively with `aws ecr put-image-tag-mutability`. The pipeline deploys strictly by `repository@sha256:...` and never uses mutable tags.

## 2. Configure the EC2 instance role

### 2.0 Determine the current state

- [ ] Decide whether to take path A or path B

```bash
aws ec2 describe-instances \
  --instance-ids "i-0852b6c3475b19f05" \
  --query 'Reservations[].Instances[].IamInstanceProfile' \
  --region "ap-southeast-1"
```

A `null` result means no role is attached — continue with **2.1B**. An Instance Profile ARN means a role exists — look up the role name and continue with **2.1A**:

```bash
aws iam get-instance-profile \
  --instance-profile-name <ProfileName> \
  --query 'InstanceProfile.Roles[0].RoleName' --output text
```

### 2.1A Role already exists: attach the SSM policy

- [ ] `AmazonSSMManagedInstanceCore` attached to the existing role

```bash
aws iam attach-role-policy \
  --role-name <existing-role-name> \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore
```

### 2.1B No role: create everything from scratch

- [ ] Role and Instance Profile created and associated with the EC2 instance

```bash
cat > ec2-trust.json <<'EOF'
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Principal": {"Service": "ec2.amazonaws.com"},
    "Action": "sts:AssumeRole"
  }]
}
EOF

aws iam create-role --role-name "BlocBuyEc2Runtime" \
  --assume-role-policy-document file://ec2-trust.json

aws iam attach-role-policy --role-name "BlocBuyEc2Runtime" \
  --policy-arn arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore

aws iam create-instance-profile --instance-profile-name "BlocBuy-dev"
# Propagation between the two commands occasionally takes a few seconds;
# on NoSuchEntity, wait briefly and retry.
aws iam add-role-to-instance-profile \
  --instance-profile-name "BlocBuy-dev" --role-name "BlocBuyEc2Runtime"

aws ec2 associate-iam-instance-profile \
  --instance-id "i-0852b6c3475b19f05" \
  --iam-instance-profile Name="BlocBuy-dev" \
  --region "ap-southeast-1"
```

### 2.2 Attach the ECR pull policy

The SSM policy only covers agent communication; pulling the image on the EC2 host additionally requires pull permission scoped to this repository only.

- [ ] Customer Managed Policy created with placeholders substituted and attached

```bash
sed -e "s/AWS_ACCOUNT_ID/402178232802/g" \
    -e "s/AWS_REGION/ap-southeast-1/g" \
    -e "s|ECR_REPOSITORY|dev/blocbuy|g" \
    ec2-ecr-pull-policy.json.example > ec2-ecr-pull-policy.json

aws iam create-policy \
  --policy-name "BlocBuyEc2EcrPull" \
  --policy-document file://ec2-ecr-pull-policy.json

aws iam attach-role-policy --role-name "BlocBuyEc2Runtime" \
  --policy-arn "arn:aws:iam::402178232802:policy/BlocBuyEc2EcrPull"
```

The `ECR_REPOSITORY` substitution uses `|` as the sed delimiter: ECR repository names may contain `/` (e.g. `dev/blocbuy`), and the default `/` delimiter would fail with `unknown option to 's'`. The substituted value must exactly match the repository name actually created in section 1, otherwise ECR will reject the EC2 pull. On the 2.1A path, replace `BlocBuyEc2Runtime` in the attach command with the existing role name.

**Never** use `AmazonSSMFullAccess`, `AmazonEC2ContainerRegistryFullAccess`, or any other broad policy.

### 2.3 Verify the instance is Online in SSM

- [ ] `PingStatus` is `Online`

```bash
aws ssm describe-instance-information \
  --filters "Key=InstanceIds,Values=i-0852b6c3475b19f05" \
  --region "ap-southeast-1" \
  --query 'InstanceInformationList[].{id:InstanceId,ping:PingStatus,agent:AgentVersion}'
```

After attaching the role, the agent needs a few minutes to refresh its credentials. If it is still `ConnectionLost` after 5 minutes, log in to the instance and run `sudo systemctl restart amazon-ssm-agent` (on an Ubuntu snap installation the service is `snap.amazon-ssm-agent.amazon-ssm-agent`), then retry.

## 3. Configure GitHub OIDC and the two AWS roles

### 3.1 Create the OIDC provider (once per AWS account)

- [ ] Provider exists

```bash
# Check for an existing provider first (at most one per account name)
aws iam get-open-id-connect-provider \
  --open-id-connect-provider-arn \
    "arn:aws:iam::402178232802:oidc-provider/token.actions.githubusercontent.com" \
  && echo "already exists, skip creation"

aws iam create-open-id-connect-provider \
  --url https://token.actions.githubusercontent.com \
  --client-id-list sts.amazonaws.com \
  --thumbprint-list 6938fd4d98bab03faadb97b34396831e3780aea1
```

Output after creation:

```json
{
    "OpenIDConnectProviderArn": "arn:aws:iam::402178232802:oidc-provider/token.actions.githubusercontent.com"
}
```

### 3.2 Create the ECR publisher role (trust scoped to this repository's `dev` branch)

- [ ] Role and permission policy created

```bash
sed "s/AWS_ACCOUNT_ID/402178232802/g" \
  github-ecr-push-trust-policy.json.example > github-ecr-push-trust-policy.json

sed -e "s/AWS_ACCOUNT_ID/402178232802/g" \
    -e "s/AWS_REGION/ap-southeast-1/g" \
    -e "s|ECR_REPOSITORY|dev/blocbuy|g" \
    github-ecr-push-policy.json.example > github-ecr-push-policy.json

aws iam create-role --role-name "BlocBuyGitHubEcrPush" \
  --assume-role-policy-document file://github-ecr-push-trust-policy.json

aws iam create-policy --policy-name "BlocBuyGitHubEcrPushPolicy" \
  --policy-document file://github-ecr-push-policy.json

aws iam attach-role-policy --role-name "BlocBuyGitHubEcrPush" \
  --policy-arn "arn:aws:iam::402178232802:policy/BlocBuyGitHubEcrPushPolicy"

# Record the ARN; it goes into GitHub Variables in section 5
aws iam get-role --role-name "BlocBuyGitHubEcrPush" --query 'Role.Arn' --output text
# Output
arn:aws:iam::402178232802:role/BlocBuyGitHubEcrPush
```

The example file's `sub` is already filled with this repository's verified real subject (confirmed 2026-09-05 by decoding the OIDC token with a temporary workflow): `repo:PPsyrius@19505219/iss-groupbuy-team8@1344446887:ref:refs/heads/dev`. Repositories created after 15 July 2026 (or with immutable OIDC subjects enabled) use the combined name+ID format for `sub`: `repo:<owner-name>@<ownerID>/<repo-name>@<repoID>:...` — this repository (created 2026-08-24) is one of them. Before recreating the role you must still verify the repository's actual subject; relaxing it to a wildcard is not allowed.

### 3.3 Create the SSM deploy role (trust scoped to the `backend-dev` environment)

- [ ] Role and permission policy created

```bash
sed "s/AWS_ACCOUNT_ID/402178232802/g" \
  github-deploy-trust-policy.json.example > github-deploy-trust-policy.json

sed -e "s/AWS_ACCOUNT_ID/402178232802/g" \
    -e "s/AWS_REGION/ap-southeast-1/g" \
    -e "s/BACKEND_EC2_INSTANCE_ID/i-0852b6c3475b19f05/g" \
    github-ssm-deploy-policy.json.example > github-ssm-deploy-policy.json

aws iam create-role --role-name "BlocBuyGitHubBackendDeploy" \
  --assume-role-policy-document file://github-deploy-trust-policy.json

aws iam create-policy --policy-name "BlocBuyGitHubBackendDeployPolicy" \
  --policy-document file://github-ssm-deploy-policy.json

aws iam attach-role-policy --role-name "BlocBuyGitHubBackendDeploy" \
  --policy-arn "arn:aws:iam::402178232802:policy/BlocBuyGitHubBackendDeployPolicy"

# Record the ARN; it goes into GitHub Environment Variables in section 5
aws iam get-role --role-name "BlocBuyGitHubBackendDeploy" --query 'Role.Arn' --output text
# Output
arn:aws:iam::402178232802:role/BlocBuyGitHubBackendDeploy
```

- [ ] Both trust policies validated with IAM Access Analyzer

Do not attach the ECR push policy to the deploy role, and do not merge the two GitHub roles into one. The SSM deploy role's `ssm:SendCommand` executes as root on the instance — it is a high privilege and must not be shared with other repositories, forks, or PRs.

## 4. Bootstrap the EC2 host

The commands below run **on the EC2 instance** (via SSH or an SSM Session, either works). Host prerequisites: Linux, Docker Engine, the Docker Compose plugin, AWS CLI v2, the SSM Agent online, at least 2 GiB free under `/opt/blocbuy/backend`, and PostgreSQL/Redis listening on `127.0.0.1:5432/6379`.

- [ ] Host meets the prerequisites above
- [ ] `/etc/blocbuy/backend.env` created with permissions `root:root 600` and no leftover placeholders

```bash
sudo install -d -m 0750 /etc/blocbuy
sudo install -m 0600 /dev/null /etc/blocbuy/backend.env
sudo chown root:root /etc/blocbuy/backend.env
sudoedit /etc/blocbuy/backend.env   # fill in all secrets per deployment/backend/backend.env.example
```

Keys in `backend.env.example`: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET`. `BACKEND_IMAGE` does not belong in this file; the deploy script injects it by digest.

```bash
# Verify
sudo stat -c '%U:%G %a %n' /etc/blocbuy/backend.env   # must be root:root 600
sudo ss -lnt | grep -E '127\.0\.0\.1:(5432|6379)'      # must listen on loopback only
grep -r 'replace-with' /etc/blocbuy/backend.env        # must produce no output
```

Security group check: PostgreSQL/Redis must not be open to the public internet; `8589` may only allow the reverse-proxy/load-balancer security group or the team's testing CIDR.

The deployment bundle (`compose.yml` and scripts) is installed automatically to `/opt/blocbuy/backend` by SSM **on the first workflow run**; the EC2 host does not need a clone of the repository. Once installed you can run the preflight manually:

```bash
sudo /opt/blocbuy/backend/bin/preflight.sh
```

- [ ] All host, IAM, and network changes recorded in the team change log

## 5. Configure GitHub

### 5.1 Create the `backend-dev` environment

- [ ] Environment created, deployment branches restricted to `dev`; add a Required Reviewer if manual approval is desired

Settings -> Environments -> New environment -> `backend-dev`.

### 5.2 Configure repository variables (Settings -> Secrets and variables -> Actions -> Variables)

- [ ] The following variables created (the ECR push job does not reference an environment, so they must live at repository level)

| Variable | Value | Required |
|---|---|---|
| `AWS_ACCOUNT_ID` | Account ID from section 0 | Yes |
| `AWS_REGION` | Region from section 0 | Yes |
| `ECR_REPOSITORY_DEV` | `dev/blocbuy` | Yes |
| `AWS_ECR_PUSH_ROLE_ARN` | Role ARN output in 3.2 | Yes |
| `ENABLE_EC2_DEPLOY` | `false` until validation is complete | Yes |

### 5.3 Configure environment variables (inside the `backend-dev` environment)

- [ ] The following variables created

| Variable                      | Value | Required |
|-------------------------------|---|---|
| `AWS_DEPLOY_ROLE_ARN_DEV`     | Role ARN output in 3.3 | Yes |
| `BACKEND_EC2_INSTANCE_ID_DEV` | Instance ID from section 0 | Yes |
| `BACKEND_URL_DEV`             | Backend public URL, e.g. `https://api-dev.example.com` | No |

No long-lived AWS access keys and no `BACKEND_DOT_ENV` are needed on GitHub. Real secrets exist only in `/etc/blocbuy/backend.env` on the EC2 host.

### 5.4 Protect the `dev` branch

- [ ] Branch protection enabled: require PRs, CODEOWNERS review, forbid force push, and select the required checks listed in [backend-pipeline.md](../ci/backend-pipeline.md#required-pr-checks). Do not set the push-only deploy job (`deploy-existing-ec2`) as a PR required check.

## 6. First deployment rehearsal

Execute in order; on any failure, troubleshoot before continuing:

- [ ] 1. Keep `ENABLE_EC2_DEPLOY=false` unchanged and merge the deployment changes into protected `dev` via PR
- [ ] 2. Confirm both the `ci-backend` and `security` workflows are fully green
- [ ] 3. Change the repository variable `ENABLE_EC2_DEPLOY` to `true`
- [ ] 4. Merge or push a backend/deployment change to `dev` to trigger the pipeline
- [ ] 5. If a Required Reviewer is configured, approve the `backend-dev` environment
- [ ] 6. Inspect the `deploy-existing-ec2` job: image digest, SSM command output, health check passing
- [ ] 7. Business verification:

```bash
curl --fail --silent "https://<backend-address>/api/actuator/health"
```

The currently deployed image digest is recorded on the EC2 host in `/opt/blocbuy/backend/state/current-image`. Deployment does not restart or delete PostgreSQL/Redis.

## Rollback and freeze (after the first deployment is confirmed working)

- **Rollback:** Actions -> `rollback-backend` -> Run workflow -> select `dev` -> leave `target_image` empty to use the previous digest recorded on the host (or fill in `repository@sha256:...` from the same ECR repository) -> fill in the reason -> tick the confirmation -> complete the environment approval.
- **Freeze:** set `ENABLE_EC2_DEPLOY=false`. CI and security scanning keep running, but nothing is published or deployed; manual rollback remains available.
- An application rollback cannot reverse Flyway migrations; all migrations shipped by automatic deployment must be backward compatible.

## Quick troubleshooting reference

See [operator guide section 13](backend-ec2-pipeline-operator-guide.md#13-troubleshooting) for detailed diagnosis.

| Symptom | Most common cause |
|---|---|
| ECR role cannot be assumed | Wrong OIDC audience / `sub` does not match the branch / ARN copied incorrectly into repository variables |
| Deploy role cannot be assumed | `sub` not using the environment format / `backend-dev` not restricted to the `dev` branch |
| SSM SendCommand denied | Region mismatch / instance ID placeholder not substituted in the deploy policy |
| Instance not online | SSM agent not running / instance profile not attached / missing NAT or VPC endpoint |
| ECR pull denied | Section 2.2 policy not attached or placeholders not substituted |
| Preflight failed | env file permissions not `root:root 600` / leftover placeholders / insufficient disk |
| Bundle policy failed | Deployment bundle contains files outside the allowlist (only `compose.yml` and the five lifecycle scripts are allowed — see `security.yml`) |

**Security red lines:** never paste `/etc/blocbuy/backend.env`, Docker credentials, or application secrets into issues, PRs, workflow logs, or any file in this repository; the substituted IAM JSON must only live in a private directory outside the repository.
