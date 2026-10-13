# SWE5001 Environment Session Runbook

How to bring the SWE5001 ECS environment up for a short test, demonstration or presentation, check it, and destroy it afterwards. For the design behind it see the [AWS infrastructure plan](impl-aws-infrastructure.md); for the scripts themselves see [`infra/swe5001/README.md`](../../infra/swe5001/README.md#environment-sessions).

Last verified: 2026-10-06, promotion [run 37470479078](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37470479078) (up, smoke, release and down all green).

## 1. What exists when

| Part | Lifecycle | Cost while idle |
| --- | --- | --- |
| `edge`: CloudFront distribution `E3N39GHVFV6CT3`, SPA bucket | Always on | A few cents a month |
| `foundation`: ECR repositories, GitHub OIDC roles | Always on | ECR storage, under USD 1 a month |
| `runtime`: VPC, NAT instance, ALB, ECS on Fargate, RDS, Valkey | **Only during a session or a promotion run** | None when absent; about USD 0.12–0.15 an hour when up with one service (list-price estimate); two more Fargate tasks with `distributed` |

`runtime` runs one of two service maps (`topology`, [`services.tf`](../../infra/swe5001/runtime/services.tf)): `distributed`, the default since #456, with `identity-svc`, `commerce-svc` and `notification-svc`, each on its own database ([backend ADR 0008](../../backend/docs/adr/0008-dual-deployment-topology.md)); or `monolith`, the fallback, with the SWE5006 image as one service.

URL: <https://d3lvq02o2e7tcn.cloudfront.net>. Without `runtime`, the SPA loads but every `/api/*` call returns **502 Bad Gateway**: CloudFront still points at the last, destroyed ALB. That is the normal idle state, not a fault.

A promotion run (`dev` → `prod` merge) creates `runtime`, smoke-tests it, publishes the release and destroys it again. A **session** keeps `runtime` up until you destroy it.

## 2. Before the first session (one time)

- [ ] A release exists for every service of the topology: `/blocbuy/swe5001/release/<service>` holds a digest (`monolith` first written by run 37470479078; the three services by the first promotion after #456). Before that promotion, see "First distributed session" in section 4.
- [ ] The application parameters are seeded (`identity/GOOGLE_CLIENT_ID`, `identity/GOOGLE_CLIENT_SECRET`, `commerce/STRIPE_API_KEY`, `commerce/STRIPE_WEBHOOK_SECRET`; [README](../../infra/swe5001/README.md#2-seed-the-parameters)). A missing one stops the task with `ResourceInitializationError: ... invalid ssm parameters`.
- [ ] For Google sign-in: `https://d3lvq02o2e7tcn.cloudfront.net/api/v1/auth/oauth2/google/callback` is an authorised redirect URI of the Google OAuth client. Without it, browsing works but sign-in fails. The URI is the same for both topologies; with `distributed` the ALB routes it to `identity-svc`.
- [ ] You can dispatch workflows in the repository (`gh auth status`; write access).
- [ ] Stripe checkout is off in this environment (`BLOCBUY_STRIPE_CHECKOUT_ENABLED=false`); do not plan a payment demo.

## 3. Timeline for a presentation

| When | Do |
| --- | --- |
| At least one day before | Full rehearsal: sections 4–6, then section 7 |
| T − 30 min | Section 4 (start) |
| T − 15 min | Section 5 (check) |
| T | Present; follow section 6 |
| Right after | Section 7 (destroy) |

T − 30 min is the minimum lead time. For 21 October (16:15–16:55 SGT) the [verification plan](impl-verification-observability.md), section 7, starts the session before 12:00, which leaves time for a second `up` after a failure and gives the dashboard a full hour of data.

## 4. Start

1. Make sure nothing else holds the Terraform state queue: no promotion running and no stale queued `down`, which would destroy the session when it reaches the front of the queue.

   ```bash
   gh run list --repo PPsyrius/iss-groupbuy-team8 --workflow swe5001-env.yml --limit 5
   ```

   ```bash
   gh run list --repo PPsyrius/iss-groupbuy-team8 --workflow swe5001-deploy.yml --limit 3
   ```

   A `down` that is `queued`, `pending` or `waiting` is stale: cancel it with `gh run cancel <id>`. Wait for a running promotion to finish.

2. Dispatch `up` with the release digests (the default):

   ```bash
   gh workflow run swe5001-env.yml --repo PPsyrius/iss-groupbuy-team8 --ref prod -f action=up
   ```

   Use `-f digests=candidate` only to test an image that a promotion published but did not release, and `-f topology=monolith` only for the fallback.

3. Watch it (about 13–15 minutes: `runtime` apply about 8.5, `db-init` 1, service start, `edge` about 1, stable service, smoke suite about 1):

   ```bash
   gh run watch --repo PPsyrius/iss-groupbuy-team8 $(gh run list --repo PPsyrius/iss-groupbuy-team8 --workflow swe5001-env.yml --limit 1 --json databaseId --jq '.[0].databaseId')
   ```

The run is green only after every service is stable and the smoke suite passed through CloudFront (health, product list, unauthenticated `/auth/me`, trace header, internal path blocked). With `distributed`, health and the product list come from `commerce-svc` and `/auth/me` from `identity-svc`; `notification-svc` is covered by the stable-service wait and its target group until it has an endpoint (#215). The job summary shows the URL, the topology and the step timings.

**First distributed session (before the first promotion after #456).** `prod` and the release digests do not have the three services yet, so an operator runs the session from a `dev` checkout instead of the workflow: push the three images by hand and write their candidate digests ([README](../../infra/swe5001/README.md#environment-sessions)), then:

```bash
SWE5001_TOPOLOGY=distributed infra/swe5001/scripts/env-up.sh --candidate
```

`commerce-svc` and `notification-svc` start only once session roles (#467) and the remote identity check (#468) are merged; before that the run stops at the stable-service wait. Destroy with `env-down.sh` from the same checkout (section 8, fallback destroy).

## 5. Check

- Open <https://d3lvq02o2e7tcn.cloudfront.net>: the product list loads.
- Health from a terminal:

  ```bash
  curl -s https://d3lvq02o2e7tcn.cloudfront.net/api/actuator/health
  ```

  Expect `"status":"UP"`.

### Demo data (distributed)

Each service has its own database. The demo catalogue (10 products, deals, policies) is seeded only into `commerce`; `identity` starts without users and admins. For admin or START/JOIN demos, after section 5:

1. Sign in once with Google through the session URL; this creates the account in `identity`.
2. Grant the admin role, as an operator with the same environment as `env-up.sh`:

   ```bash
   infra/swe5001/scripts/grant-admin.sh '<google email>'
   ```

   It runs [`scripts/grant-dev-admin.sql`](../../scripts/grant-dev-admin.sql) as a one-off `db-init` task against `identity` and prints `granted = 1` (or `already_admin = 1` on a rerun); it fails when the account has not signed in yet. The email is written to the `db-init` log group.
3. Sign out and sign in again: roles travel in the session (#467), so the grant takes effect at the next sign-in. An admin request such as the admin product list (`GET /api/v1/admin/products`) now succeeds; it is served by `commerce-svc` from the role in the session.
4. Make listings startable (team terms and a team price policy in force) through the audited admin API, with the admin's `blocbuy-token` cookie value:

   ```bash
   BLOCBUY_BASE_URL=https://d3lvq02o2e7tcn.cloudfront.net/api/v1 BLOCBUY_TOKEN=<cookie value> scripts/seed-dev-demo-listings.sh
   ```

   It is idempotent and prints a summary; `--check` makes no writes. A buyer account can then START a deal on one of those listings.

## 6. During the session

- **Do not merge into `prod`.** A promotion starts `swe5001-deploy`, whose `down` job destroys the session.
- **Finish before 23:00 SGT.** The nightly `down` (`ENABLE_SWE5001_NIGHTLY_DOWN=true`) destroys `runtime` then.
- Do not change `runtime` resources in the console or run Terraform locally against `runtime`; the next `down` would leave drift or leftovers.
- The databases start empty apart from the demo catalogue seed (`commerce` only with `distributed`); data created in a session, including the admin grant, is lost at `down`.

## 7. Destroy

1. Dispatch `down` (about 9–10 minutes):

   ```bash
   gh workflow run swe5001-env.yml --repo PPsyrius/iss-groupbuy-team8 --ref prod -f action=down
   ```

2. Confirm in the run log: `Destroy complete! Resources: <n> destroyed.` (71 with `monolith`) followed by `no runtime resource tagged Stack=runtime`. The run fails if anything is left.
3. Optional second check at any later time:

   ```bash
   gh workflow run swe5001-env.yml --repo PPsyrius/iss-groupbuy-team8 --ref prod -f action=status
   ```

   Green means nothing tagged `Stack=runtime` exists. Deleted resources stay listed in the tagging API for over an hour; `env-status.sh` checks each one with its own describe call, so they do not count.

After `down`, `/api/*` returns 502 again (section 1).

## 8. When something goes wrong

| Symptom | Meaning | Do |
| --- | --- | --- |
| `up` red | `runtime` **stays up and keeps costing** | Read the cause (below), then run section 7 |
| `up` red at `wait for services to be stable` or the smoke suite | A service does not start or does not answer | In CloudShell (admin), per service (`identity-svc`, `commerce-svc`, `notification-svc`, or `monolith`): `aws logs tail /blocbuy/swe5001/<service> --since 1h`, and `aws ecs describe-services --cluster blocbuy-swe5001 --services <service> --query 'services[0].events[:10]'` for placement errors such as missing parameters. `commerce-svc` answering 500 on purchases while the others are healthy usually means `identity-svc` is unreachable (fail-closed by design) |
| `commerce-svc` purchases or admin listing actions answer 500 and its log shows `UnknownHostException: identity-svc` or connection refused | Its tasks were deployed before `identity-svc` registered its Service Connect endpoint; Terraform creates commerce after identity to prevent this, but the endpoint registration itself is unverified on AWS | Operator: `aws ecs update-service --cluster blocbuy-swe5001 --service commerce-svc --force-new-deployment`, then wait for it to be stable |
| `up` or `down` waits in `queued` | Another state-writing run is ahead in `swe5001-tfstate` | Wait; check section 4, step 1 for a stale `down` |
| `down` red with `Destroy complete!` | Verification found a resource type it does not know | Look at the listed ARNs in the console; if they are gone, re-run `status` later |
| `down` red before `Destroy complete!` | Destroy failed (often a permission or dependency error) | Re-run `down` once; if it fails again, use the fallback below |
| Terraform reports a state lock | A cancelled run left a lock | Do not force-unlock while a run is active; ask an operator to check `s3://blocbuy-tfstate-402178232802/swe5001/` for `runtime.tfstate.tflock` |

Fallback destroy, as an operator in CloudShell, from a clone of the repository with Terraform 1.16 on `PATH`:

```bash
export TF_DATA_DIR="$(mktemp -d /tmp/swe5001-down.XXXXXX)"
```

```bash
AWS_REGION=ap-southeast-1 SWE5001_TF_STATE_BUCKET=blocbuy-tfstate-402178232802 infra/swe5001/scripts/env-down.sh
```

It is the script the workflows run and ends with the same verification. Never paste plan output, parameter values or decrypted responses into a chat, issue or PR.

## 9. Cost guards

- A session of two hours costs well under USD 1; a forgotten one is capped by the nightly `down` at under a day (about USD 3).
- Once a week, check Cost Explorer for the last seven days by service: RDS, ElastiCache, EC2 and Elastic Load Balancing should be near zero outside sessions. The AWS budget is off (`enable_budget = false`).
- Keep `ENABLE_SWE5001_NIGHTLY_DOWN=true`.
