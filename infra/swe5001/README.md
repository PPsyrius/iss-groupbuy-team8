# SWE5001 Terraform

Infrastructure for the SWE5001 distributed deployment shape on Amazon ECS. It is separate from the SWE5006 stacks in [`infra/terraform/`](../terraform/README.md): own state keys, own VPC, own CloudFront distribution and own GitHub OIDC roles. The design is in the [AWS infrastructure plan](../../docs/swe5001/impl-aws-infrastructure.md); the release flow (`dev` → `prod` promotion, ephemeral `runtime`) is in the [refactoring plan](../../docs/swe5001/microservice-refactoring-plan.md), section 3.4. Tracking issue: #376; current progress and next steps: [handoff](../../docs/swe5001/HANDOFF.md).

| Stack | Lifecycle | State key | Applied by | Status |
| --- | --- | --- | --- | --- |
| [`foundation/`](foundation/) | Persistent | `swe5001/foundation.tfstate` | An operator, from a workstation (it creates the roles CI assumes) | Code ready; not applied |
| [`edge/`](edge/) | Persistent | `swe5001/edge.tfstate` | Created once by an operator; afterwards the promotion run and `env-up.sh` only re-point the distribution at the new ALB | Code ready (PR 5 in #376); not applied |
| [`runtime/`](runtime/) | Exists only during a promotion run or a manual session | `swe5001/runtime.tfstate` | Promotion run and `env-up.sh` / `env-down.sh` | Code ready (PRs 3–4 in #376); not applied. Messaging, scaling and alarms follow in PRs 8 and 10 |

All stacks use Terraform `~> 1.16.4`, AWS provider `~> 5.100`, the existing state bucket `blocbuy-tfstate-402178232802` and S3-native locking (`use_lockfile = true`). Non-secret inputs are in [`envs/demo.tfvars`](envs/demo.tfvars). Operators seed application secrets in SSM Parameter Store under `/blocbuy/swe5001/`. The origin secret is also held in the private `edge` and `runtime` Terraform states for CloudFront/ALB routing; the Valkey AUTH token is held in `runtime` state. The PR plan role cannot read either state.

Unlike the SWE5006 stacks, CI will apply `edge` and `runtime` only from the `prod` branch through the `swe5001-demo` environment. Pull requests run offline validation on all three stacks, but the optional account-backed plan runs only for `foundation` ([`infra-swe5001.yml`](../../.github/workflows/infra-swe5001.yml)); the PR-assumable plan role cannot read the secret-bearing `edge` and `runtime` states. The `prod`-only apply role may read `edge` and update only the distribution tagged `Course=swe5001`; it cannot create or change CloudFront functions, origin access controls or response headers policies, so an operator creates `edge`.

## Offline checks

Run these before opening a PR; `infra-swe5001.yml` runs the same checks.

```bash
terraform fmt -check -recursive infra/swe5001
terraform -chdir=infra/swe5001/foundation init -backend=false
terraform -chdir=infra/swe5001/foundation validate
terraform -chdir=infra/swe5001/runtime init -backend=false
terraform -chdir=infra/swe5001/runtime validate
terraform -chdir=infra/swe5001/runtime test
terraform -chdir=infra/swe5001/edge init -backend=false
terraform -chdir=infra/swe5001/edge validate
terraform -chdir=infra/swe5001/edge test
shellcheck -x infra/swe5001/scripts/*.sh perf/smoke/smoke.sh
bash infra/swe5001/scripts/test-env-status.sh
checkov -d infra/swe5001 --framework terraform --quiet --compact
trivy config --severity HIGH,CRITICAL --exit-code 1 infra/swe5001
```

## `foundation` bootstrap (one time)

Run with your own AWS credentials in account `402178232802`, region `ap-southeast-1`. Never paste a full plan, a parameter value or a decrypted response into a PR, issue or chat; share resource addresses and actions only.

### 1. Activate the cost allocation tag

The budget filters on the `Course` tag, which Billing ignores until it is activated (it can take up to 24 hours to apply):

```bash
aws ce update-cost-allocation-tags-status --cost-allocation-tags-status TagKey=Course,Status=Active
```

### 2. Seed the parameters

`BUDGET_ALERT_EMAILS` must exist before the first plan, because the budget reads it. The secrets are needed by `runtime` later and can be seeded now. Use `read -rs` so values stay out of shell history; add `--overwrite` to rotate a value.

```bash
aws ssm put-parameter --name /blocbuy/swe5001/shared/BUDGET_ALERT_EMAILS --type String --value '<a@example.com>,<b@example.com>'

for name in identity/GOOGLE_CLIENT_ID identity/GOOGLE_CLIENT_SECRET commerce/STRIPE_API_KEY commerce/STRIPE_WEBHOOK_SECRET; do
  read -rsp "$name: " value; echo
  aws ssm put-parameter --name "/blocbuy/swe5001/$name" --type SecureString --value "$value" > /dev/null
done

for name in shared/INTERNAL_TOKEN shared/ORIGIN_SECRET; do
  aws ssm put-parameter --name "/blocbuy/swe5001/$name" --type SecureString --value "$(openssl rand -hex 32)" > /dev/null
done
```

`STRIPE_WEBHOOK_SECRET` is only known after the Stripe endpoint is registered against the `edge` distribution (GitHub plan, section 8); seed a placeholder now and overwrite it then. Use Stripe test-mode keys only.

### 3. Plan and apply

```bash
terraform -chdir=infra/swe5001/foundation init \
  -backend-config=bucket=blocbuy-tfstate-402178232802 -backend-config=region=ap-southeast-1
terraform -chdir=infra/swe5001/foundation plan -var-file=../envs/demo.tfvars -out=foundation.tfplan
terraform -chdir=infra/swe5001/foundation apply foundation.tfplan
rm infra/swe5001/foundation/foundation.tfplan
```

Expect only creates on the first apply: 4 ECR repositories and lifecycle policies, 3 IAM roles with their policies and attachments, the workload permissions boundary policy, 1 budget.

### 4. Read back the trust policies

Confirm each role trusts exactly one environment subject in the immutable-ID form:

```bash
for role in plan apply deploy; do
  aws iam get-role --role-name "blocbuy-swe5001-gh-$role" \
    --query 'Role.AssumeRolePolicyDocument.Statement[0].Condition.StringEquals' --output json
done
```

`plan` must show `...:environment:swe5001-plan`; `apply` and `deploy` must show `...:environment:swe5001-demo`.

After `foundation` has been applied, simulate the plan and apply roles without reading a state object or parameter value. The foundation state and the budget-recipient parameter must evaluate `allowed`; the runtime state, origin secret and an existing SWE5006 CloudFront function must evaluate `implicitDeny` or `explicitDeny`. Replace only the function name placeholder with the existing function's non-secret name. IAM simulation is not a substitute for the first account-backed foundation plan.

```bash
account=402178232802
bucket=blocbuy-tfstate-402178232802
plan_role="arn:aws:iam::$account:role/blocbuy-swe5001-gh-plan"
apply_role="arn:aws:iam::$account:role/blocbuy-swe5001-gh-apply"
aws iam simulate-principal-policy --policy-source-arn "$plan_role" --action-names s3:GetObject \
  --resource-arns "arn:aws:s3:::$bucket/swe5001/foundation.tfstate" "arn:aws:s3:::$bucket/swe5001/runtime.tfstate" \
  --query 'EvaluationResults[].{Resource:EvalResourceName,Decision:EvalDecision}' --output table
aws iam simulate-principal-policy --policy-source-arn "$plan_role" --action-names ssm:GetParameter \
  --resource-arns "arn:aws:ssm:ap-southeast-1:$account:parameter/blocbuy/swe5001/shared/BUDGET_ALERT_EMAILS" "arn:aws:ssm:ap-southeast-1:$account:parameter/blocbuy/swe5001/shared/ORIGIN_SECRET" \
  --query 'EvaluationResults[].{Resource:EvalResourceName,Decision:EvalDecision}' --output table
aws iam simulate-principal-policy --policy-source-arn "$apply_role" --action-names cloudfront:UpdateFunction \
  --resource-arns "arn:aws:cloudfront::$account:function/<existing-SWE5006-function-name>" \
  --query 'EvaluationResults[].{Resource:EvalResourceName,Decision:EvalDecision}' --output table
```

### 5. GitHub settings

Create the environments. `swe5001-demo` admits only the `prod` branch, even before that branch exists:

```bash
repo=PPsyrius/iss-groupbuy-team8
gh api -X PUT "repos/$repo/environments/swe5001-plan" > /dev/null
gh api -X PUT "repos/$repo/environments/swe5001-demo" --input - > /dev/null <<'JSON'
{"deployment_branch_policy": {"protected_branches": false, "custom_branch_policies": true}}
JSON
gh api -X POST "repos/$repo/environments/swe5001-demo/deployment-branch-policies" -f name=prod -f type=branch > /dev/null
```

Copy the outputs into variables. They are non-secret; GitHub holds no SWE5001 secret.

```bash
out() { terraform -chdir=infra/swe5001/foundation output -raw "$1"; }
gh variable set SWE5001_TF_PLAN_ROLE_ARN  --env swe5001-plan --body "$(out plan_role_arn)"
gh variable set SWE5001_TF_APPLY_ROLE_ARN --env swe5001-demo --body "$(out apply_role_arn)"
gh variable set SWE5001_DEPLOY_ROLE_ARN   --env swe5001-demo --body "$(out deploy_role_arn)"
gh variable set SWE5001_ECR_REPOSITORY_PREFIX --body swe5001
gh variable set ENABLE_SWE5001_DEPLOY --body false
gh variable set ENABLE_SWE5001_NIGHTLY_DOWN --body true
gh variable set SWE5001_TF_STATE_BUCKET --body "$(out tf_state_bucket)"
```

Set `SWE5001_TF_STATE_BUCKET` last: it switches on the foundation-only `plan` job of `infra-swe5001.yml`. The next PR touching `infra/swe5001/**` should then show a `No changes.` plan comment for `foundation`, which proves the plan role, its trust policy and the foundation state access end to end. The role can read only that state object, SWE5001 foundation metadata and the non-secret budget-recipient parameter; explicit denies protect unrelated S3 objects and SSM parameters against later policy changes. No workflow plans `runtime` or `edge` against the account before a merge: the promotion run applies them after the merge to `prod`, and an operator plans them by hand when needed.

## `runtime` stack

Shared modules live in [`modules/`](modules/): `network` (VPC, subnets, S3 gateway endpoint, fck-nat), `ecs-cluster` (cluster, Service Connect namespace, shared execution role), `ecs-service` (the golden path for one service), `rds-postgres` and `cache`. The root stack adds the security groups, the ALB and the [`db-init`](db-init/init.sh) task definition.

| Input | Default | Set by |
| --- | --- | --- |
| `topology` | `none`: no ECS service, no database | `env-up.sh` (`SWE5001_TOPOLOGY`, default `monolith`). The service maps are in [`runtime/services.tf`](runtime/services.tf): `monolith` runs the SWE5006 image as one service on `/api/*` with database `monolith` (strangler step 0); PR 9 adds `distributed` |
| `frontend_url` | `""` | `env-up.sh`, from the `edge` output `url`; required when services run (`FRONTEND_URL`, Google redirect URI) |
| `image_digests` | `{}` | `env-up.sh`, from SSM `/blocbuy/swe5001/release/*` or `/candidate/*` |
| `services_enabled` | `false`: desired count 0 | `env-up.sh` sets `true` after `db-init` has succeeded |
| `postgres_major_version` | `18` | Set `17` if RDS does not offer 18 in the region |

Prerequisites: `foundation` applied (the workload permissions boundary must exist) and `/blocbuy/swe5001/shared/ORIGIN_SECRET` seeded (step 2 above).

Secrets in state: the RDS master and role passwords are ephemeral values written through write-only arguments (`password_wo`, `value_wo`), so they never reach the state. Two values do: `ORIGIN_SECRET`, because an ALB rule condition needs the literal value (as in the SWE5006 `backend-app-stack`), and the Valkey AUTH token, because the replication group has no write-only argument in provider 5.x. Both live only while `runtime` exists; the PR plan role cannot read that state object. Do not print, upload or attach a runtime plan made with trusted `prod` credentials.

`terraform test` plans the stack with a mocked AWS provider, so it needs no credentials. Apply and destroy `runtime` only through the environment scripts below.

## `edge` creation (one time, operator)

Needs `foundation` applied and `ORIGIN_SECRET` seeded. The first `edge` apply does not read `runtime` state: `/api/*` points at an unresolvable placeholder and returns 502 until `env-up.sh` supplies the new ALB DNS name as the non-secret `runtime_alb_dns_name` input.

```bash
terraform -chdir=infra/swe5001/edge init \
  -backend-config=bucket=blocbuy-tfstate-402178232802 -backend-config=region=ap-southeast-1
terraform -chdir=infra/swe5001/edge plan -var-file=../envs/demo.tfvars -out=edge.tfplan
terraform -chdir=infra/swe5001/edge apply edge.tfplan
rm infra/swe5001/edge/edge.tfplan
terraform -chdir=infra/swe5001/edge output -raw url
```

Expect only creates: the SPA bucket with its public access block, ownership controls, encryption and policy; the origin access control, SPA fallback function, response headers policy and distribution. The distribution domain is permanent; then:

1. Add `<url>/api/v1/auth/oauth2/google/callback` to the Google OAuth client's authorised redirect URIs.
2. Register a Stripe test-mode webhook endpoint at `<url>/api/v1/webhooks/stripe` and store its signing secret in `/blocbuy/swe5001/commerce/STRIPE_WEBHOOK_SECRET` (`--overwrite`).
3. Copy the `edge` outputs into the `swe5001-demo` environment, for the workflows below:

   ```bash
   edge() { terraform -chdir=infra/swe5001/edge output -raw "$1"; }
   gh variable set SWE5001_FRONTEND_URL       --env swe5001-demo --body "$(edge url)"
   gh variable set SWE5001_SPA_BUCKET         --env swe5001-demo --body "$(edge spa_bucket)"
   gh variable set SWE5001_CLOUDFRONT_DIST_ID --env swe5001-demo --body "$(edge distribution_id)"
   ```

The SPA bucket stays empty until the first promotion run syncs the frontend; until then the SPA routes return an S3 error and the API works.

## Environment sessions

[`scripts/`](scripts/) create and destroy `runtime` for a manual session; the promotion run calls the same scripts. Each one checks the account and region before doing anything.

| Script | Does | Exit 0 when |
| --- | --- | --- |
| `env-up.sh [--release\|--candidate]` | Reads the digests from SSM, applies `runtime` with services stopped, runs `db-init`, starts the services, reads the ALB DNS output and passes it to `edge` without sharing runtime state, waits for a stable service and runs [`perf/smoke/smoke.sh`](../../perf/smoke/smoke.sh) through CloudFront (up to five attempts). Re-running converges | The smoke suite passes |
| `env-down.sh` | Destroys `runtime` (one retry, usually for the Service Connect namespace), then runs `env-status.sh` | Nothing tagged `Stack=runtime` remains |
| `env-status.sh [--expect-up]` | Lists resources tagged `Course=swe5001,Stack=runtime` (ignoring deregistered task definitions and confirmed terminated instances) and the ECS service counts; AWS lookup failures stop verification instead of reporting a clean destroy | Nothing remains (or, with `--expect-up`, something does), and every lookup succeeded |

```bash
export AWS_PROFILE=<operator profile> AWS_REGION=ap-southeast-1 SWE5001_TF_STATE_BUCKET=blocbuy-tfstate-402178232802
infra/swe5001/scripts/env-up.sh --candidate   # about 20-25 minutes; prints the CloudFront URL
infra/swe5001/scripts/env-down.sh             # about 10-15 minutes; fails if anything is left
```

Set `SWE5001_TIMINGS_FILE=<path>` to record each step's duration (promotion evidence, verification plan V8). `env-up.sh` never prints the `edge` plan, because the origin diff carries `ORIGIN_SECRET` in clear; on failure it shows only Terraform's diagnostics.

The same sessions run from GitHub with [`swe5001-env.yml`](../../.github/workflows/swe5001-env.yml) (next section). Before the first promotion has written any digest, push the monolith image by hand once per commit (ECR tags are immutable):

```bash
registry=402178232802.dkr.ecr.ap-southeast-1.amazonaws.com
tag=manual-$(git rev-parse --short HEAD)
(cd backend && ./mvnw -B -q -DskipTests package)
aws ecr get-login-password | docker login --username AWS --password-stdin "$registry"
docker buildx build --platform linux/arm64 -f backend/Dockerfile.ci -t "$registry/swe5001/monolith:$tag" --push backend
digest=$(aws ecr describe-images --repository-name swe5001/monolith --image-ids imageTag="$tag" \
  --query 'imageDetails[0].imageDigest' --output text)
aws ssm put-parameter --name /blocbuy/swe5001/candidate/monolith --type String --value "$digest" --overwrite
```

## Workflows and the `prod` branch

ECS runs only from the `prod` branch, which moves only by a `dev` → `prod` promotion PR (merge commit), once per sprint. Workflow settings and rules: [GitHub configuration plan](../../docs/swe5001/impl-github-configuration.md), sections 5 and 6.

| Workflow | Trigger | Does |
| --- | --- | --- |
| [`swe5001-release-gate.yml`](../../.github/workflows/swe5001-release-gate.yml) | PRs into `prod` | Required checks `promotion-source` (head must be this repository's `dev`), `service-images` (ARM64 image build and Trivy scan, no push) and `infra-plan` (`infra-swe5001.yml` for every stack, unfiltered) |
| [`swe5001-deploy.yml`](../../.github/workflows/swe5001-deploy.yml) | Push to `prod` while `ENABLE_SWE5001_DEPLOY` is `true`; manual on `prod` with `force_failure` | `images` → `publish` (push the scanned images, write `/blocbuy/swe5001/candidate/*`) → `up` (`env-up.sh --candidate`) → `release` (copy to `/blocbuy/swe5001/release/*`, publish the SPA) → `down` (`env-down.sh`, always) → `evidence` (one artifact `swe5001-promotion-<sha>`: digests, logs with the smoke output, step timings, ECS service events) |
| [`swe5001-env.yml`](../../.github/workflows/swe5001-env.yml) | Manual on `prod` (`up`, `down`, `status`); daily 23:00 SGT | Runs the scripts above with the apply role. The schedule runs on `dev` with no AWS access and only dispatches `down` on `prod` while `ENABLE_SWE5001_NIGHTLY_DOWN` is `true` |
| [`swe5001-service-images.yml`](../../.github/workflows/swe5001-service-images.yml) | Called by the two workflows above | Builds and scans the runtime images; only the monolith until PR 9 |

Runs that write Terraform state share the concurrency group `swe5001-tfstate` and are never cancelled. GitHub keeps one running and one pending run per group, so a third queued run replaces the pending one; start a manual session only when no promotion is running.

### Create `prod` (one time, operator)

After the PR that adds these workflows is merged, with repository admin rights:

```bash
git fetch origin
git push origin origin/dev:refs/heads/prod
gh api -X POST repos/PPsyrius/iss-groupbuy-team8/rulesets --input infra/swe5001/github/prod-release-ruleset.json
```

[`github/prod-release-ruleset.json`](github/prod-release-ruleset.json) blocks deletion and force pushes, requires one CODEOWNERS approval with resolved threads, allows merge commits only and requires the three gate checks on an up-to-date branch. Its bypass list is empty.

Set `ENABLE_SWE5001_DEPLOY=true` only after a manual `env-up.sh --candidate` and `env-down.sh` have both succeeded. Then open the promotion PR (head `dev`, base `prod`, title `chore(release): promote dev to prod (<yyyy-mm-dd>)`) and merge it with a merge commit.
