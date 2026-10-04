# SWE5001 Terraform

Infrastructure for the SWE5001 distributed deployment shape on Amazon ECS. It is separate from the SWE5006 stacks in [`infra/terraform/`](../terraform/README.md): own state keys, own VPC, own CloudFront distribution and own GitHub OIDC roles. The design is in the [AWS infrastructure plan](../../docs/swe5001/impl-aws-infrastructure.md); the release flow (`dev` → `prod` promotion, ephemeral `runtime`) is in the [refactoring plan](../../docs/swe5001/microservice-refactoring-plan.md), section 3.4. Tracking issue: #376.

| Stack | Lifecycle | State key | Applied by | Status |
| --- | --- | --- | --- | --- |
| [`foundation/`](foundation/) | Persistent | `swe5001/foundation.tfstate` | An operator, from a workstation (it creates the roles CI assumes) | Code ready; not applied |
| `edge/` | Persistent | `swe5001/edge.tfstate` | Promotion run and `env-up.sh` | Not started (PR 5 in #376) |
| `runtime/` | Exists only during a promotion run or a manual session | `swe5001/runtime.tfstate` | Promotion run and `env-up.sh` / `env-down.sh` | Not started (PRs 3–4 in #376) |

All stacks use Terraform `~> 1.16.4`, AWS provider `~> 5.100`, the existing state bucket `blocbuy-tfstate-402178232802` and S3-native locking (`use_lockfile = true`). Non-secret inputs are in [`envs/demo.tfvars`](envs/demo.tfvars). Secrets are never Terraform inputs or state: an operator seeds them in SSM Parameter Store under `/blocbuy/swe5001/`.

Unlike the SWE5006 stacks, CI applies `edge` and `runtime`, but only from the `prod` branch through the `swe5001-demo` environment. Pull requests only plan ([`infra-swe5001.yml`](../../.github/workflows/infra-swe5001.yml)).

## Offline checks

Run these before opening a PR; `infra-swe5001.yml` runs the same checks.

```bash
terraform fmt -check -recursive infra/swe5001
terraform -chdir=infra/swe5001/foundation init -backend=false
terraform -chdir=infra/swe5001/foundation validate
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
gh variable set SWE5001_TF_STATE_BUCKET --body "$(out tf_state_bucket)"
```

Set `SWE5001_TF_STATE_BUCKET` last: it switches on the `plan` job of `infra-swe5001.yml`. The next PR touching `infra/swe5001/**` should then show a `No changes.` plan comment for `foundation`, which proves the plan role, its trust policy and the state access end to end.
