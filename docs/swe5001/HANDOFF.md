# SWE5001 Deployment Handoff

Current state of the SWE5001 distributed deployment (Amazon ECS, released by `dev` → `prod` promotion). Read this before picking up any item of [#376](https://github.com/PPsyrius/iss-groupbuy-team8/issues/376). Update it in the same PR whenever a step lands.

Last updated: 2026-10-04.

## Where to read the design

| Topic | Document |
| --- | --- |
| Release flow, ephemeral runtime, validation split | [Refactoring plan](microservice-refactoring-plan.md), sections 3.2–3.4 |
| Stacks, network, ECS, data stores, scripts, cost | [AWS infrastructure plan](impl-aws-infrastructure.md) |
| Environments, `prod` ruleset, workflows | [GitHub configuration plan](impl-github-configuration.md) |
| Smoke suite, drills, presentation checklist | [Verification plan](impl-verification-observability.md) |
| Operator commands (bootstrap, offline checks, `runtime` inputs) | [`infra/swe5001/README.md`](../../infra/swe5001/README.md) |

## Progress against the #376 PR breakdown

| # | Item | State |
| --- | --- | --- |
| 1 | Plan docs | Merged in [#377](https://github.com/PPsyrius/iss-groupbuy-team8/pull/377) |
| 2 | `foundation` stack, plan-only `infra-swe5001.yml` | Merged in #377; **not applied** |
| 3 | `runtime` part 1: network, security groups, ECS cluster, `ecs-service` module, ALB | Merged in #377 (from #379); **not applied** |
| 4 | `runtime` part 2: RDS, `db-init`, Valkey | Merged in #377 (from #379); **not applied** |
| 5 | `edge` stack: CloudFront and SPA bucket | Code ready on `feature/swe5001-edge-env-scripts-20261004` (one PR with item 6); **not applied** |
| 6 | `env-up.sh`, `env-down.sh`, `env-status.sh`, `lib.sh`; monolith as one ECS service; first up/down proof | Code ready on the same branch; smoke suite passes against SWE5006 dev; **up/down proof not run** |
| 7 | CI: `swe5001-service-images.yml`, `swe5001-release-gate.yml`, `swe5001-deploy.yml`, `swe5001-env.yml`; create `prod` and its ruleset | Not started |
| — | Sprint 3 promotion, Fri 2026-10-09 | Moves to Sprint 4 if item 6 is not green by Wed 2026-10-07 |
| 8–10 | Messaging, service split, scaling and alarms | Phase B, after the backend service split |

Nothing exists in AWS yet: no SWE5001 role, repository, budget or runtime resource. GitHub has no `swe5001-*` environment, no `prod` branch and no SWE5001 variable.

## Next actions

1. Review and merge the PR 5–6 branch into `dev`.
2. Operator, with admin credentials (the developer IAM users lack these permissions):
   1. Activate the `Course` cost tag now; it takes up to 24 hours.
   2. Check the state bucket's encryption (`aws s3api get-bucket-encryption --bucket blocbuy-tfstate-402178232802`); if it uses a customer-managed key, scope the plan role's `DecryptFoundationStateViaS3` to that key ARN in a small PR first.
   3. Bootstrap `foundation` (README steps 1–5) and run the IAM simulation. Set `SWE5001_TF_STATE_BUCKET` last; the next infra PR must show a `No changes.` plan for `foundation`.
   4. Create `edge` once (README, "`edge` creation"), then register the Google redirect URI and the Stripe webhook against its URL.
   5. Confirm in `ap-southeast-1`: PostgreSQL 18 on RDS (else `postgres_major_version = "17"`), Valkey 8.0 and the fck-nat arm64 AMI.
3. First up/down proof (#376 acceptance criterion), by Wed 2026-10-07 for the Sprint 3 promotion: push the monolith image by hand (README, "Environment sessions"), run `env-up.sh --candidate`, then `env-down.sh`; keep the timings and both exit codes as evidence.
4. PR 7: image workflow, release gate, deploy and env workflows; create `prod` and its ruleset.

## Decisions already made

- ECS runs only from `prod`, once per two-week sprint; every run creates `runtime`, validates it and destroys it (`if: always()`).
- Secrets: operator-seeded SSM values are never Terraform inputs. Database passwords are ephemeral and written through write-only arguments, so they never reach state. `ORIGIN_SECRET` and the Valkey AUTH token do reach the `runtime` state (no write-only path), accepted because they exist only while `runtime` does.
- Apply role guardrails: managed policies per service; IAM only under `/swe5001/` with the mandatory workload permissions boundary; tag-conditioned denies for non-SWE5001 EC2, ELB, CloudFront, RDS and ElastiCache resources (including snapshots and retained automated backups); name-based denies for log groups outside `/blocbuy/swe5001/*` and SNS topics or SQS queues not named `blocbuy-swe5001-*`.
- `runtime` selects its services by `topology` (`runtime/services.tf`): `none`, or `monolith` (strangler step 0: the SWE5006 image as one service on `/api/*`, own database `monolith`, profile `ec2`, demo seed). PR 9 adds `distributed` with the three services.
- `edge` is created once by an operator; the `prod`-only apply role only reads it and updates the distribution tagged `Course=swe5001`, because CloudFront origin access controls and response headers policies cannot be scoped by name or tag.
- The PR-assumable plan role can read the foundation state only, not the secret-bearing runtime state; explicit S3 and SSM denies retain that boundary if future grants are added. Runtime plans must be made by an operator or a reviewed `prod`-only workflow, so there is no pre-merge account-backed runtime plan today. The `edge` state also holds `ORIGIN_SECRET`, so the plan role cannot read it either.

## Known gaps and risks

| Gap | When it closes |
| --- | --- |
| No plan or apply has run against the account; trust policies, guardrail denies and the `plan` job are unexercised | Operator bootstrap (next action 2); test allowed foundation reads and denied runtime/other-SSM reads without fetching any secret value |
| The plan role's KMS decrypt is limited to calls via S3 but currently accepts any key in this account; its S3 GetObject deny limits readable data to foundation state | Check the state bucket's encryption key without printing state and scope the key ARN before enabling account-backed PR plans, then test the foundation plan |
| The apply role can update only the tagged distribution; whether that covers every call an `edge` re-apply makes is unproven | First `env-up.sh` run; add the missing read action if the apply fails with AccessDenied |
| CloudFront may reject the placeholder origin `runtime-absent.blocbuy-swe5001.invalid` | First `edge` creation; if rejected, override `absent_alb_domain` |
| `env-status.sh` may list stopped-task or other short-lived ARNs from the tagging API after a clean destroy | First `env-down.sh`; extend its ignore list only for free, self-expiring types |
| The apply role must strip the rules of the VPC default security group; this relies on the provider tagging it before revoking | First `runtime` apply |
| `db-init`, the monolith on ECS (Flyway as the owner role, Valkey TLS) and the scripts have only been checked offline | First `env-up.sh` run |
| `CloudWatchFullAccessV2` can still change alarms and dashboards outside SWE5001 | Open; fence before PR 10 adds alarms |
| NFR-51 (AWS isolation) is implemented but unverified in the account | Operator bootstrap and IAM simulation |

## Verification commands

Offline, no AWS credentials:

```bash
terraform fmt -check -recursive infra/swe5001
terraform -chdir=infra/swe5001/runtime init -backend=false
terraform -chdir=infra/swe5001/runtime test
terraform -chdir=infra/swe5001/edge init -backend=false
terraform -chdir=infra/swe5001/edge test
shellcheck -x infra/swe5001/scripts/*.sh perf/smoke/smoke.sh
checkov -d infra/swe5001 --framework terraform --quiet --compact
trivy config --severity HIGH,CRITICAL --exit-code 1 infra/swe5001
```

Last local result (2026-10-04): `validate` OK for both stacks, `terraform test` 3 passed, checkov 344 passed / 0 failed / 29 documented skips, Trivy 0 HIGH/CRITICAL, actionlint 0.
