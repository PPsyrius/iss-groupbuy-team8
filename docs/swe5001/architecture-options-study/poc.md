# Proof of concept: proving the study on AWS

Status: record of 2026-10-17. Owner: @limjiechao. Repository: [`limjiechao/nus-iss-mtech-se34ft-swe5001-poc-microservices`](https://github.com/limjiechao/nus-iss-mtech-se34ft-swe5001-poc-microservices) (private; collaborators @PPsyrius and @l0sgAi). AWS: member account 161877660123 (`nus-iss-mtech-se34-ft-swe5001-poc`) in the Sandbox OU, created 2026-09-30 and closed 2026-10-16.

After the [options study](README.md) (25–26 Sep), @limjiechao built a proof of concept to prove its target platform on AWS before the team committed to it. On 2026-10-01 it brought a decomposed BlocBuy up from an empty account to a smoke-tested stack in about 20 minutes, entirely from code, and tore all 78 resources down in 7 minutes 2 seconds. Seven of its infrastructure elements reappear in the team's SWE5001 implementation with matching text or structure (see [What went into the team build](#what-went-into-the-team-build)).

## Timeline

| Date (SGT) | Event | Source |
| --- | --- | --- |
| 25–26 Sep | Options study | [README](README.md) |
| 27 Sep | POC design spec and plan, citing study 03 and 04 | POC `docs/superpowers/specs/2026-09-27-ephemeral-infra-poc-design.md` |
| 27–28 Sep | Services, Terraform, CI and ADRs 0001–0009 written | POC `main`, 42 commits |
| 28 Sep 12:28 | @PPsyrius added to the POC repository (write) | GitHub member event |
| 29–30 Sep | Team SWE5001 plan, #259 (@l0sgAi) | team PR |
| 30 Sep | POC account created and placed in the Sandbox OU | Organizations, CloudTrail |
| 1 Oct | POC `up` green after three failed attempts and an SCP fix; `down` clean | POC [run 36725939920](https://github.com/limjiechao/nus-iss-mtech-se34ft-swe5001-poc-microservices/actions/runs/36725939920), [run 36830401136](https://github.com/limjiechao/nus-iss-mtech-se34ft-swe5001-poc-microservices/actions/runs/36830401136) |
| 4–5 Oct | Team foundation, runtime, edge and release-gate PRs (#377–#384) | team PRs |
| 5 Oct 19:47 | @l0sgAi added to the POC repository (write) | GitHub member event |
| 5–6 Oct | POC PR #9 proposes ADRs 0011–0017 (open, a design proposal only) | POC PR #9 |
| 6 Oct | Team's first green promotion and its tagging-API teardown fix (#411, #416) | team PRs |
| 12–14 Oct | Team microservice split (#470–#472) | team PRs |
| 16 Oct | POC account closed | CloudTrail `CloseAccount` |

## What it built and measured

- **Terraform in three stacks** (POC ADR 0006): `bootstrap` (state bucket with `use_lockfile`, GitHub OIDC with plan, build and apply roles, workload permissions boundary, ECR, a USD 50 budget, GuardDuty), `persistent` (CloudFront with an SPA rewrite function, WAF, the SPA bucket, an API Gateway HTTP API) and `ephemeral` (VPC, fck-nat on `t4g.nano`, one RDS `db.t4g.micro` with a database and role per service, Valkey with TLS and AUTH, ECS Fargate ARM64, Cloud Map, VPC Link, a one-shot `db-init` task).
- **Services:** identity, catalog and team-purchase on Spring Boot and Java 21 with a shared Sa-Token session in Valkey; images built with Jib for arm64 and deployed by digest.
- **Pipeline:** `ci`, `up` and `down` workflows with Gitleaks, Semgrep, Trivy, Checkov, Syft and ZAP, and a nightly `down` at 23:00 SGT.
- **Results** (POC `docs/first-deploy-log.md`, `docs/validation.md`): `up` about 20 minutes (build 5:16, persistent 3:15, ephemeral 9:21 for 78 resources, `db-init` 53 s); smoke test 9 of 9 through CloudFront, including a 403 when the API is called directly; ZAP baseline 0 fail, 8 warn, 59 pass; Google sign-in honoured by all three services (checked by hand); `down` destroyed 78 resources in 7:02.
- **AWS cost** (Cost Explorer, linked account 161877660123): the full stack ran for about 5 hours on 1 Oct for USD 1.28 (7.0 Fargate vCPU-hours, RDS 4.8 h, Valkey 5 node-hours, NAT instance 4.9 h); about USD 0.26 a day afterwards for the WAF and ECR images left in place; USD 4.98 for October in total.

## What went into the team build

A file-level comparison of 45 POC elements against `origin/dev` (2026-10-17) found 7 carried over, 13 implemented later in the same way but generic, 8 that both took from the study, 8 that were the team's own, and 9 POC choices the team did not adopt.

Carried over (matching text or structure, not in the study):

| Element | POC | Team | Evidence |
| --- | --- | --- | --- |
| Workload permissions boundary and apply-role fence | `bootstrap/boundary.tf`, `oidc.tf` (28 Sep) | [`foundation/boundary.tf`](../../../infra/swe5001/foundation/boundary.tf), [`github-oidc.tf`](../../../infra/swe5001/foundation/github-oidc.tf) (#377, 4 Oct) | Name pattern `*-workload-boundary`; the same first five statements (ECR auth and pull, logs under `/blocbuy/`, SSM reads, KMS decrypt via SSM); `CreateRole` conditioned on the boundary, `PassRole` only to ECS tasks, a deny on removing the boundary, and roles kept outside the apply role's reach |
| `db-init` SQL | `infra/db-init/init.sql` (28 Sep) | [`db-init/`](../../../infra/swe5001/db-init/) (#377, 4 Oct) | Same idempotent `CREATE ROLE … WHERE NOT EXISTS … \gexec`, `GRANT … TO CURRENT_USER`, `CREATE DATABASE … OWNER`, `REVOKE ALL … FROM PUBLIC`, and the same comment on why the master user needs the owner role |
| `db-init` task | `dbinit.tf`, `scripts/run-db-init.sh` (28 Sep) | [`runtime/db-init.tf`](../../../infra/swe5001/runtime/db-init.tf), `env-up.sh` (#377, #380) | Same `public.ecr.aws/docker/library/postgres:*-alpine` image, environment (`PGHOST`, `PGDATABASE=postgres`, `PGSSLMODE=require`) and run-task, wait, exit-code sequence |
| RDS settings | `ephemeral/database.tf` (28 Sep) | [`modules/rds-postgres`](../../../infra/swe5001/modules/rds-postgres/main.tf) (#377) | `username = "blocbuy_admin"`, `rds.force_ssl`, and the same two Checkov skips (CKV_AWS_129, CKV2_AWS_30) with the same reason: logs would outlive the destroy and expose `db-init` passwords |
| One RDS, a database and role per service | ADR 0004 (28 Sep) | #259 (29 Sep) | The study proposed a schema per service |
| Lifecycle split | bootstrap / persistent / ephemeral (27 Sep) | foundation / edge / runtime (#259, 29 Sep) | CloudFront kept to preserve the Google redirect URI and everything else destroyed per session, including the load balancer the study kept; ECR in the persistent layer so images survive a destroy |
| Nightly teardown | `down.yml` `"0 15 * * *" # 23:00` (28 Sep) | `swe5001-env.yml` (#259, #384) | Same cron and comment behind a repository switch; not in the study |

The POC also changed the organisation's guardrails: its 1 Oct WAF deploy hit the Sandbox region pin, and the scoped `WafHomeRegionOrCloudFrontScope` statement written then is still in the root [`Region-Pin`](../../deploy/aws-account-governance/scp/region-pin.json) SCP.

Not adopted by the team: API Gateway with VPC Link and Cloud Map (the team uses an ALB with Service Connect, as the study proposed), WAF, GuardDuty, Jib, Semgrep, Syft and ZAP, the in-app origin check and `/wiring` probe, the internal-endpoint token, and the separate catalog service. The team went further than the POC on digest promotion (SSM candidate and release), the prod-branch release gate, a plan role that cannot read runtime state, and write-only secret arguments that keep database passwords out of Terraform state.

Found first by the POC and hit later by the team: the Resource Groups Tagging API listing deleted resources (POC 1 Oct, team #411 and #416 on 6 Oct); a smoke check counting a failed request as a status (POC 28 Sep, team #384 on 5 Oct); secrets leaking into plan or log output (POC 28 Sep, team #384 and #417); and the region-pin SCP blocking a us-east-1 dependency (POC WAF on 1 Oct, team CloudFront metrics on 16 Oct, #530).

## Access and attribution

GitHub records that @PPsyrius had write access from 28 Sep 12:28 SGT and @l0sgAi from 5 Oct 19:47 SGT; it records access, not reading, and neither left activity in the POC repository. The carried-over items in #259 and #377 were committed by @l0sgAi on 29 Sep and 4 Oct, before his own recorded access, so the route by which the POC reached the team plan (through @PPsyrius, @limjiechao or team meetings) is not recorded in git. The matching text and structure are the evidence.

## Caveats

- Every POC commit after the initial one is authored by Claude Code under @limjiechao's direction; the trailers name the models used.
- The POC account's own history cannot be recovered: the account is closed and refuses sign-in, and the organisation has no CloudTrail trail or central log bucket. The lifecycle above comes from the management account's Organizations and CloudTrail events and from Cost Explorer.
- Cost allocation tags were never activated, so the POC's own cost criterion (S6) was not measured by stack; the Cost Explorer figures above are by linked account.
- The POC repository is private; readers without access rely on this summary.
