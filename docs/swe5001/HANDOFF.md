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
| 1 | Plan docs | In [#377](https://github.com/PPsyrius/iss-groupbuy-team8/pull/377) (draft until its CI is green on the combined code), awaiting review |
| 2 | `foundation` stack, plan-only `infra-swe5001.yml` | In #377 (merged into it from #378); code ready, **not applied** |
| 3 | `runtime` part 1: network, security groups, ECS cluster, `ecs-service` module, ALB | In #377 (merged into it from #379); code ready, **not applied** |
| 4 | `runtime` part 2: RDS, `db-init`, Valkey | Same branch as item 3 |
| 5 | `edge` stack: CloudFront and SPA bucket | Not started |
| 6 | `env-up.sh`, `env-down.sh`, `env-status.sh`, `lib.sh`; monolith as one ECS service; first up/down proof | Not started |
| 7 | CI: `swe5001-service-images.yml`, `swe5001-release-gate.yml`, `swe5001-deploy.yml`, `swe5001-env.yml`; create `prod` and its ruleset | Not started |
| — | Sprint 3 promotion, Fri 2026-10-09 | Moves to Sprint 4 if item 6 is not green by Wed 2026-10-07 |
| 8–10 | Messaging, service split, scaling and alarms | Phase B, after the backend service split |

Nothing exists in AWS yet: no SWE5001 role, repository, budget or runtime resource. GitHub has no `swe5001-*` environment, no `prod` branch and no SWE5001 variable.

## Next actions

1. Wait for #377's CI to pass on the combined code, mark it ready (`gh pr ready 377`), get a review and squash-merge it into `dev`.
2. Start every later PR from `dev` after that merge, not from the #377 branch.
3. Operator: bootstrap `foundation` (README steps 1–5): activate the `Course` cost tag, seed the SSM parameters, apply, read back the trust policies, create the environments and variables. Set `SWE5001_TF_STATE_BUCKET` last; the next infra PR must then show a `No changes.` plan for `foundation`.
4. Before the first `runtime` apply, confirm in `ap-southeast-1`: PostgreSQL 18 on RDS (else set `postgres_major_version = "17"`), Valkey 8.0 on ElastiCache, and the fck-nat arm64 AMI.
5. PR 5 (`edge`) and PR 6 (scripts and monolith service) can start in parallel; PR 6 is on the critical path.

## Decisions already made

- ECS runs only from `prod`, once per two-week sprint; every run creates `runtime`, validates it and destroys it (`if: always()`).
- Secrets: operator-seeded SSM values are never Terraform inputs. Database passwords are ephemeral and written through write-only arguments, so they never reach state. `ORIGIN_SECRET` and the Valkey AUTH token do reach the `runtime` state (no write-only path), accepted because they exist only while `runtime` does.
- Apply role guardrails: managed policies per service; IAM only under `/swe5001/` with the mandatory workload permissions boundary; tag-conditioned denies for non-SWE5001 EC2, ELB, CloudFront, RDS and ElastiCache resources (including snapshots and retained automated backups); name-based denies for log groups outside `/blocbuy/swe5001/*` and SNS topics or SQS queues not named `blocbuy-swe5001-*`.
- `runtime` ships with an empty `services` map; PR 6 adds the monolith (strangler step 0), PR 9 swaps in the three services.

## Known gaps and risks

| Gap | When it closes |
| --- | --- |
| No plan or apply has run against the account; trust policies, guardrail denies and the `plan` job are unexercised | Operator bootstrap (next action 3) |
| The apply role must strip the rules of the VPC default security group; this relies on the provider tagging it before revoking | First `runtime` apply |
| `db-init` has only been checked with shellcheck, not run against PostgreSQL | First `env-up.sh` run (PR 6) |
| `CloudWatchFullAccessV2` can still change alarms and dashboards outside SWE5001 | Open; fence before PR 10 adds alarms |
| No NFR register rows for SWE5001 yet | With the evidence of the first apply |

## Verification commands

Offline, no AWS credentials:

```bash
terraform fmt -check -recursive infra/swe5001
terraform -chdir=infra/swe5001/runtime init -backend=false
terraform -chdir=infra/swe5001/runtime test
checkov -d infra/swe5001 --framework terraform --quiet --compact
trivy config --severity HIGH,CRITICAL --exit-code 1 infra/swe5001
```

Last local result (2026-10-04): `validate` OK for both stacks, `terraform test` 3 passed, checkov 344 passed / 0 failed / 29 documented skips, Trivy 0 HIGH/CRITICAL, actionlint 0.
