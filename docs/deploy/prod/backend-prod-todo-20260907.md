# Production Deployment TODO — 2026-09-07

**Purpose:** point-in-time snapshot of the prod rollout against
[backend-prod-handover.md](backend-prod-handover.md) §1–§10. Update this file as steps land;
delete it once the first release (§10) is green.

**Scope:** AWS account `402178232802`, region `ap-southeast-1`, repo `PPsyrius/iss-groupbuy-team8`.
Release model: `v*` tag push → `publish-ecr-prod` (environment `backend-prod-publish`) →
`deploy-existing-ec2-prod` (environment `backend-prod`, ≥2 reviewers). Freeze switch:
`ENABLE_EC2_DEPLOY_PROD`.

---

## Progress board

| § | Step | Status | Notes |
|---|---|---|---|
| — | Repo code: Terraform stack + workflow extension + docs | **Done, uncommitted** | On branch `chore/prod-pipeline-build-20260906`; includes the Option-A OIDC fix (ECR push role trusts `environment:backend-prod-publish`, never a tag ref). actionlint/YAML/pinned-SHA checks passed locally. |
| 1 | Pipeline PR merged into `dev` | **Blocked — next action** | Working tree still has 6 modified files (workflow, 2 terraform files, 3 docs). Commit → PR → merge. Merging triggers a normal dev deploy only; prod jobs stay unreachable (tag-push + freeze switch). |
| 2 | `envs/prod.tfvars` decisions pinned | **Done** | `release_branch=dev`, `ami_id=ami-0ba4172b23e57d5a8` (Ubuntu 24.04 amd64), `key_name` empty (SSM-only), `acm_certificate_arn` empty (CloudFront entry pattern). |
| 3 | Terraform state backend (S3 `blocbuy-tfstate-402178232802` + DynamoDB `blocbuy-tflock`) | **Verify** | `head-bucket` returned 404 on first check; confirm both resources now exist before §6 (`head-bucket` + `aws dynamodb describe-table --table-name blocbuy-tflock`). |
| 4 | GitHub environments `backend-prod` + `backend-prod-publish` | **Not started** | Deferred by decision below. Hard deadline: **before the first `v*` tag push** — GitHub auto-creates missing environments with zero protection, which would silently strip the approval gate from `backend-prod`. |
| 5 | Seed 11 SSM parameters under `/blocbuy/prod/` | **Done** | 9 generated values seeded. `STRIPE_API_KEY` / `STRIPE_WEBHOOK_SECRET` seeded as `replace-with-…` placeholders — see "Open items". |
| 6 | `terraform init / plan / team review / apply` | **Not started** | Unblocked now (§5 done, `ami_id` pinned). Plan-review checklist in handover §6 — verify both OIDC `sub` strings, SG rules, listener 403 + `X-Origin-Secret`, health path `/api/actuator/health`. |
| 7 | Post-apply verification | Not started | SSM Online, 5432/6379 loopback-only, direct-to-ALB = 403. Record `instance_id`, role ARNs, `backend_url`. |
| 8 | GitHub variables from tf outputs | Not started | `ECR_REPOSITORY_PROD`, `AWS_ECR_PUSH_ROLE_ARN_PROD`, `ENABLE_EC2_DEPLOY_PROD=false`; env-scoped `AWS_DEPLOY_ROLE_ARN_PROD`, `BACKEND_EC2_INSTANCE_ID_PROD` on `backend-prod`. |
| 9 | Prod CloudFront distribution + `BACKEND_URL_PROD` | Not started | Manual per dev tutorial §6–7; origin header `X-Origin-Secret` = the `ORIGIN_SECRET` seeded in §5. |
| 10 | First deployment drill | Not started | Closed-switch rehearsal (`v0.0.0-rc1`, expect no publish/deploy) → flip freeze switch → real tag `v0.1.0` → reviewer approval → health checks. |

## Open items (do not lose track)

1. **Stripe live values.** `STRIPE_API_KEY` and `STRIPE_WEBHOOK_SECRET` are placeholders today.
   `fetch-env.sh` hard-fails any prod deploy while a value contains `replace-with-` — this is
   deliberate, it makes "seed the real keys" a forced checkpoint rather than a silent breakage.
   Overwrite with `--overwrite` once available; the webhook secret only exists after the Stripe
   webhook endpoint is created (handover §9 territory). Both must be real **before the first
   real release tag**, not before §6–§9.
2. **Uncommitted changes on `chore/prod-pipeline-build-20260906`** (6 files). They are the PR;
   nothing in §4–§10 works until the tag points at a commit containing them.
3. **Environment creation timing.** §4 is the only remaining step that, if forgotten, fails
   *silently* (auto-created unprotected environments). Everything else fails loudly.

## Decisions locked this session (2026-09-07)

- **OIDC design fix (Option A) adopted:** the prod ECR push role trusts
  `repo:…:environment:backend-prod-publish` (exact match). Tag-name refs vary per release and can
  never be exact-matched; an environment subject is the only exact-match option that keeps the
  "no wildcards in trust policies" red line intact. Side benefit: removes the old over-permission
  that let dev-branch pushes assume the prod push role.
- **Two environments, deliberately different protection:** `backend-prod-publish` has no
  reviewers (ECR publish is not a production mutation); `backend-prod` keeps the ≥2-reviewer
  approval gate. Both restricted to `v*` tags.
- **`main` is not involved.** `dev` is the release branch; prod triggers on `v*` tag pushes only.
  Merging the pipeline PR into `dev` cannot trigger anything prod-side.
- **Stripe keys deferred** (see open item 1).
- **Remaining handover work is all manual console/CLI** — no further repo changes are expected
  before §10.

## Next action (single thread)

1. Commit the 6 modified files on `chore/prod-pipeline-build-20260906` → open PR into `dev` → merge. (§1)
2. Verify §3 state backend exists (one `head-bucket` + one `describe-table`).
3. Create the two GitHub environments per handover §4.
4. `terraform init` → `plan` → team review against the §6 checklist → `apply`. (§6)
5. Continue §7 → §8 → §9, then the §10 drill.
