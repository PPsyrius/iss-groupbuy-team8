# Production Backend Implementation Plan (Repository Changes)

**Status:** executable plan — the work items that land **inside this repository** before the first prod deployment can run

**Companion documents:** after this plan is merged and the pipeline is exercised, the human/AWS/GitHub steps live in the [operations runbook](backend-prod-operations-runbook.md). Policy and rationale: [design document](backend-prod-terraform-deployment.md) (authoritative — where this plan conflicts with it, escalate to the team and update the design doc, do not silently diverge).

**Scope boundary:** everything in this plan is a pull request against `dev`. Nothing here runs `terraform apply`, creates AWS resources, or edits GitHub settings — those are runbook steps.

## 0. Corrections to the design document found during implementation planning

These four points refine the design document; each is reflected in the phases below and was folded back into the design doc on 2026-09-21 (verified against `network.tf`, `health-check.sh`, `fetch-env.sh`, `user-data.sh.tftpl`, and the runbook):

1. **ALB target-group health check path must be `/api/actuator/health`.** There is no servlet context path: `/api/v1` comes from the controller mappings and actuator from `management.endpoints.web.base-path: /api/actuator` in `application.yml`, so a bare `/actuator/health` would 404 and the target group would mark the instance unhealthy. (An earlier revision of this note blamed a `SERVER_SERVLET_CONTEXT_PATH` that `compose.yml` never sets — corrected 2026-09-21.) The design doc §6.3 showed the wrong path; folded 2026-09-21.
2. **Secrets must be seeded into Parameter Store *before* `terraform apply`**, not after (design doc §9 step 4 orders it after). The user-data starts PostgreSQL/Redis at first boot and needs `POSTGRES_USER`/`POSTGRES_PASSWORD`/`REDIS_PASSWORD` immediately. The runbook reflects this.
3. **The infra Compose project must explicitly bind loopback.** With `network_mode: host`, the stock `postgres`/`redis` images listen on **all** interfaces; "loopback only" is an invariant we claim (design doc §11), so the compose file must set `command: postgres -c listen_addresses=127.0.0.1` and `--bind 127.0.0.1` for Redis explicitly. This matches the actual dev host state (bootstrap doc §4: listening on `127.0.0.1:5432/6379`).
4. **`fetch-env.sh` must be prod-conditional.** Dev keeps its hand-edited `/etc/blocbuy/backend.env`; if `fetch-env.sh` unconditionally pulled `/blocbuy/prod/*` on every deploy, dev would break. The script takes the parameter path as an argument and is invoked only by the prod deploy job's remote script.

## 1. Phase 1 — Terraform stack (`infra/terraform/`)

One PR. Creates the code only; nobody applies anything yet.

### 1.1 Files to create

```text
infra/terraform/
├── backend-app-stack/
│   ├── versions.tf          # terraform >= 1.9, aws provider pinned (~> 5.x, exact pin in lock)
│   ├── backend.tf           # partial S3 backend config: key + dynamodb_table; bucket passed via -backend-config
│   ├── main.tf              # locals (github_repo_slug), providers, data sources (default VPC, public subnets, AMI)
│   ├── variables.tf         # every value from design doc §4
│   ├── outputs.tf           # instance_id, ecr_push_role_arn, deploy_role_arn, backend_url, backend_sg_name, target_group_arn
│   ├── github-oidc.tf       # data lookup of existing OIDC provider + the two Prod roles (design doc §6.1)
│   ├── ecr.tf               # prod/blocbuy repository + lifecycle policy (reuses deployment/backend/ecr-lifecycle-policy.json)
│   ├── network.tf           # alb/backend security groups (CloudFront origin pattern), ALB, target group (health path /api/actuator/health), default-403 listener + secret-header rule
│   ├── ec2.tf               # role (SSM + ECR pull + ssm:GetParameters on /blocbuy/prod/*), instance profile, instance
│   └── templates/
│       └── user-data.sh.tftpl
├── envs/
│   └── prod.tfvars          # the values from the runbook's section 0
└── README.md                # init/plan/apply commands, state backend pointer, what this stack owns
```

`envs/dev.tfvars` (design doc §10) is deliberately **out of scope** — the dev environment stays hand-managed until the team explicitly schedules the import.

### 1.2 Implementation notes per file

- **`github-oidc.tf`** — copy the trust documents from design doc §6.1 verbatim. The `sub` values are the fixed `environment:backend-prod-publish` suffix (ECR push — tag-name refs can never be exact-matched, so the publish identity is an environment subject) and the fixed `environment:backend-prod` suffix (deploy). No `.json.example`, no sed, no wildcards.
- **`network.tf`** — ingress rules exactly as §6.3 (CloudFront entry pattern): the ALB SG accepts the CloudFront managed prefix list on the listener port only (no CIDR blocks); 8589 on the backend SG referencing `aws_security_group.alb.id` only; **no** rules for 22/5432/6379 anywhere. The listener is default-403 with a single `X-Origin-Secret` forward rule (secret read from `/blocbuy/prod/ORIGIN_SECRET` at plan time — seed it **before the first plan**). `acm_certificate_arn` drives `listener_port`/`protocol` for the later HTTPS-origin upgrade (design doc §6.4); the CloudFront pattern keeps it empty and runs HTTP :80.
- **`ec2.tf`** — one deviation from §6.5: the instance role needs a **second inline policy** granting `ssm:GetParameters` on `arn:aws:ssm:${region}:${account}:parameter/blocbuy/prod/*` (required by §6.7 fetch-env and by first-boot user-data). Everything else per §6.5: IMDSv2 required, encrypted 30 GiB root volume, `ManagedBy = "terraform"` tag.
- **`templates/user-data.sh.tftpl`** — the design doc §6.6 script, extended:
  - install `docker.io`, `docker-compose-v2`, AWS CLI v2 (exact §6.6 sequence);
  - `aws ssm get-parameters` (with retry loop — instance-profile credentials need a few seconds to propagate) for `POSTGRES_USER`, `POSTGRES_PASSWORD`, `REDIS_PASSWORD`, then write `/etc/blocbuy/infra.env` as `root:root 0600`;
  - write `/opt/blocbuy/infra/compose.yml` (`name: blocbuy-infra`; postgres with `listen_addresses=127.0.0.1`, redis with `--bind 127.0.0.1 --requirepass`; both `network_mode: host`, `restart: unless-stopped`; correction #3 above);
  - `docker compose --project-directory /opt/blocbuy/infra up --detach`;
  - create `/opt/blocbuy/backend/{bin,state}` and `/etc/blocbuy` so the pipeline's first bundle install lands in existing directories;
  - **do not** create `/etc/blocbuy/backend.env` (secrets stay out of user-data);
  - end with a self-verification block (docker alive, `ss -lnt` shows 5432/6379 on loopback only) so a failed bootstrap is visible in the `terraform apply` output / console logs.
- **`versions.tf` / lock file** — commit `.terraform.lock.hcl` so every workstation and future re-run uses identical provider versions (design doc §12 "Reproducibility" row).

### 1.3 Acceptance criteria

- [ ] `terraform fmt -check` clean; `terraform validate` passes with `-backend=false`
- [ ] `terraform plan` against the real account (read-only) shows the intended resources — the plan itself is attached to the PR for review (this doubles as the §12 audit-trail evidence)
- [ ] Grep proofs: no `i-0` literal instance IDs, no account IDs hardcoded outside tfvars examples, no wildcard in any trust policy

## 2. Phase 2 — `fetch-env.sh` and the shared deployment bundle

One PR. Adds the prod secret-rendering script and updates the bundle allowlist **for both environments** (dev and prod ship the same bundle).

### 2.1 `deployment/backend/bin/fetch-env.sh` (new)

- Usage: `fetch-env.sh <ssm-parameter-path-prefix>` (e.g. `/blocbuy/prod`).
- Fetches the required runtime keys: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`, `BLOCBUY_SCHEDULING_ENABLED`, `STRIPE_API_KEY`, `STRIPE_WEBHOOK_SECRET` — via one `aws ssm get-parameters --with-decryption` call; fails if **any** parameter is missing or any value still looks like a placeholder. Scheduling starts as `false` and is enabled only after checking overdue teams and due refund obligations; under `ec2` it also starts the sandbox refund worker.
- Writes `/etc/blocbuy/backend.env` atomically (temp file + `mv`), `root:root 0600`.
- Refuses to run as non-root; never echoes parameter values.
- `bash -n` clean and shellcheck-clean to the standard of the existing four scripts.

### 2.2 Bundle allowlist updates

- `ci-backend.yml` bundle steps (dev job today, prod job from Phase 3) gain `bin/fetch-env.sh` in the `expected_entries` list, and the size budget stays at 9000 bytes (fetch-env.sh must fit — keep it small).
- `deployment/backend/README.md` documents the fifth script and the prod-only invocation rule.

### 2.3 Acceptance criteria

- [ ] Unit-test locally: render against throwaway parameters, verify permissions and placeholder rejection
- [ ] Dev deploy job still green after the allowlist change (fetch-env.sh is shipped but never invoked on dev)

## 3. Phase 3 — Workflow extension

One PR (two files): `ci-backend.yml` and `rollback-backend.yml`.

### 3.1 `ci-backend.yml`

**Trigger:**

```yaml
on:
  push:
    branches: [dev]
    paths: [ ...existing... ]
    tags: ['v*']   # path/branch filters are not evaluated for tag pushes
```

**`docker` job — dev-only publication.** The build/smoke/scan steps are unchanged. The four ECR steps (validate / assume / login / publish) gate on `push && refs/heads/dev && ENABLE_EC2_DEPLOY` and publish only to the dev repository. On `v*` tag pushes the job additionally exports the built image as a run artifact (`docker save` + upload-artifact) so the prod publish job never rebuilds the expensive multi-stage image.

**New `publish-ecr-prod` job** — prod publication lives here, not in `docker`:

- `needs: [build-quality, contract-check, sonar-backend, docker]` with the same `always() && ...result` gating chain as the dev deploy job;
- condition: `github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v') && vars.ENABLE_EC2_DEPLOY_PROD == 'true'`;
- `environment: backend-prod-publish` (v* tags only, **no** reviewers — publishing to ECR is not a production mutation; the human gate stays on the deploy job). This environment subject is what the ECR push role trusts (design doc §6.1): a tag-name `ref` subject varies per release and can never be exact-matched, so an environment subject is the only exact-match option;
- steps: download the image artifact, `docker load`, then the same four ECR steps as dev (validate / assume / login / publish by digest) against `vars.ECR_REPOSITORY_PROD` and `vars.AWS_ECR_PUSH_ROLE_ARN_PROD`; outputs `image_uri`.

**New `deploy-existing-ec2-prod` job:**

- `needs: [build-quality, contract-check, sonar-backend, docker, publish-ecr-prod]` with the same `always() && ...result` gating chain as the dev job;
- condition adds `github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v') && vars.ENABLE_EC2_DEPLOY_PROD == 'true' && needs.publish-ecr-prod.outputs.image_uri != ''`;
- `environment: backend-prod`, `url: ${{ vars.BACKEND_URL_PROD }}` — this is the approval gate;
- `concurrency: deploy-existing-ec2-backend-prod`, `cancel-in-progress: false`;
- variables: `ECR_REPOSITORY_PROD`, `AWS_DEPLOY_ROLE_ARN_PROD`, `BACKEND_EC2_INSTANCE_ID_PROD` (same validation regexes as the dev job);
- bundle step: copy of the dev step **plus `bin/fetch-env.sh`** in the allowlist;
- remote script: identical to the dev job's, with one inserted line before `deploy.sh`:

  ```
  BLOCBUY_EXPECTED_AWS_ACCOUNT_ID='...' BLOCBUY_EXPECTED_AWS_REGION='...' /opt/blocbuy/backend/bin/fetch-env.sh '/blocbuy/prod'
  ```

so each prod deploy re-renders the env file from Parameter Store before `deploy.sh` (which then re-validates permissions and placeholders, unchanged);
- final health verification: keep the SSM invocation status check (deploy.sh already runs the local `127.0.0.1:8589` health check with automatic rollback); optionally curl `${BACKEND_URL_PROD}/actuator/health` from the runner when the variable is set — `BACKEND_URL_PROD` is the CloudFront domain including the `/api` path (runbook section 6.5), the same URL users hit, so no security-group opening is involved.

**Dev job untouched except** the shared bundle-allowlist line from Phase 2.

### 3.2 `rollback-backend.yml`

- New input `target` (`dev` | `prod`, default `dev`).
- New job `rollback-existing-ec2-prod` mirroring the dev job with: prod `_PROD` variables, `environment: backend-prod` (reviewers required), `concurrency: deploy-existing-ec2-backend-prod` (shared with the deploy job so a rollback and a deploy can never interleave), and the branch guard allowing dispatch from the release branch as well as `dev`.
- The dev job gains `if: inputs.target == 'dev'` so dispatching a prod rollback never touches dev.

### 3.3 Housekeeping in the same PR

- `security.yml` path filters gain `infra/terraform/**` so `trivy-config` scans the Terraform and `workflow-lint` covers the new job (verify trivy's config scanners are enabled for `.tf` — if not, add `--scanners misconfig` coverage or a `terraform validate` CI step).
- Branch protection (runbook section, not repo): `deploy-existing-ec2-prod` is push-only and must **not** become a required PR check (same rule as the dev deploy job).

### 3.4 Acceptance criteria

- [ ] `workflow-lint` green; YAML validated with actionlint if available
- [ ] A `v*` tag push to a throwaway ref (or `github.event_name` simulation) shows: gates run, `publish-ecr-prod` publishes to `ECR_REPOSITORY_PROD`, prod deploy job correctly skipped while `ENABLE_EC2_DEPLOY_PROD=false`
- [ ] A dev push after merge behaves identically to before (no regression in dev deploy)

## 4. Phase 4 — Freeze switch rehearsal

No new code. With the runbook's AWS/GitHub steps complete up to (but not including) the first tag, the team verifies the closed-switch path:

- [ ] `ENABLE_EC2_DEPLOY_PROD=false`, push a `v0.0.0-rc1` tag: gates green, **no** ECR publish to prod, **no** deploy job run
- [ ] Flip `ENABLE_EC2_DEPLOY_PROD=true`, cut the real first release tag — full drill per the runbook section 7

## 5. Phase 5 — Documentation

One PR:

- [ ] Design doc: status `proposed` → `implemented`, fold in the four corrections from section 0, link the runbook
- [ ] `deployment/backend/README.md`: fetch-env.sh + prod parameter path
- [ ] `infra/terraform/README.md` written (Phase 1 deliverable, referenced here for the changelog)
- [ ] Team change log entry: environment creation = these PRs + one `terraform apply` (runbook)

## 6. PR sequence and effort estimate

| PR | Phase | Contents | Est. |
|---|---|---|---|
| #1 | 1 | Terraform stack + templates + tfvars + README | 1 day |
| #2 | 2 | fetch-env.sh + bundle allowlist (dev job) | 0.5 day |
| #3 | 3 | workflow extension (ci-backend + rollback + security paths) | 0.5–1 day |
| #4 | 4 | freeze-switch rehearsal (no code, tagged run) | 0.5 day |
| #5 | 5 | docs alignment | 0.25 day |

Dependencies are strictly sequential: #3's deploy job embeds #2's allowlist; #4 needs #1–#3 merged plus the runbook's AWS/GitHub setup; #5 last. Total: **2.5–3.5 working days** of implementation, runnable within a single sprint alongside normal feature work.

## 7. What this plan deliberately does not do

- No `terraform apply` from any workflow — Terraform runs are human, reviewed, and one-time (design doc §2 boundary).
- No dev-environment import into Terraform (§10, deferred).
- No secrets in the repository, in tfvars, in user-data, or in workflow logs. The only secret values exist in Parameter Store and, rendered, in `/etc/blocbuy/backend.env` on the host.
- No SonarQube/CodeQL enablement work (already optional/orthogonal in the dev pipeline).
