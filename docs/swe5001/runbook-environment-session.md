# SWE5001 Environment Session Runbook

How to bring the SWE5001 ECS environment up for a short test, demonstration or presentation, check it, and destroy it afterwards. For the design behind it see the [AWS infrastructure plan](impl-aws-infrastructure.md); for the scripts themselves see [`infra/swe5001/README.md`](../../infra/swe5001/README.md#environment-sessions).

Last verified: 2026-10-06, promotion [run 37470479078](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37470479078) (up, smoke, release and down all green).

## 1. What exists when

| Part | Lifecycle | Cost while idle |
| --- | --- | --- |
| `edge`: CloudFront distribution `E3N39GHVFV6CT3`, SPA bucket | Always on | A few cents a month |
| `foundation`: ECR repositories, GitHub OIDC roles | Always on | ECR storage, under USD 1 a month |
| `runtime`: VPC, NAT instance, ALB, ECS on Fargate, RDS, Valkey | **Only during a session or a promotion run** | None when absent; about USD 0.12–0.15 an hour when up (list-price estimate) |

URL: <https://d3lvq02o2e7tcn.cloudfront.net>. Without `runtime`, the SPA loads but every `/api/*` call returns **502 Bad Gateway**: CloudFront still points at the last, destroyed ALB. That is the normal idle state, not a fault.

A promotion run (`dev` → `prod` merge) creates `runtime`, smoke-tests it, publishes the release and destroys it again. A **session** keeps `runtime` up until you destroy it.

## 2. Before the first session (one time)

- [ ] A release exists: `/blocbuy/swe5001/release/monolith` holds a digest (first written by run 37470479078).
- [ ] The application parameters are seeded (`identity/GOOGLE_CLIENT_ID`, `identity/GOOGLE_CLIENT_SECRET`, `commerce/STRIPE_API_KEY`, `commerce/STRIPE_WEBHOOK_SECRET`; [README](../../infra/swe5001/README.md#2-seed-the-parameters)). A missing one stops the task with `ResourceInitializationError: ... invalid ssm parameters`.
- [ ] For Google sign-in: `https://d3lvq02o2e7tcn.cloudfront.net/api/v1/auth/oauth2/google/callback` is an authorised redirect URI of the Google OAuth client. Without it, browsing works but sign-in fails.
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

   Use `-f digests=candidate` only to test an image that a promotion published but did not release.

3. Watch it (about 13–15 minutes: `runtime` apply about 8.5, `db-init` 1, service start, `edge` about 1, stable service, smoke suite about 1):

   ```bash
   gh run watch --repo PPsyrius/iss-groupbuy-team8 $(gh run list --repo PPsyrius/iss-groupbuy-team8 --workflow swe5001-env.yml --limit 1 --json databaseId --jq '.[0].databaseId')
   ```

The run is green only after the smoke suite passed through CloudFront (health, product list, unauthenticated `/auth/me`, trace header, internal path blocked). The job summary shows the URL and the step timings.

## 5. Check

- Open <https://d3lvq02o2e7tcn.cloudfront.net>: the product list loads.
- Health from a terminal:

  ```bash
  curl -s https://d3lvq02o2e7tcn.cloudfront.net/api/actuator/health
  ```

  Expect `"status":"UP"`.

## 6. During the session

- **Do not merge into `prod`.** A promotion starts `swe5001-deploy`, whose `down` job destroys the session.
- **Finish before 23:00 SGT.** The nightly `down` (`ENABLE_SWE5001_NIGHTLY_DOWN=true`) destroys `runtime` then.
- Do not change `runtime` resources in the console or run Terraform locally against `runtime`; the next `down` would leave drift or leftovers.
- The backend database starts empty apart from the demo catalogue seed; data created in a session is lost at `down`.

## 7. Destroy

1. Dispatch `down` (about 9–10 minutes):

   ```bash
   gh workflow run swe5001-env.yml --repo PPsyrius/iss-groupbuy-team8 --ref prod -f action=down
   ```

2. Confirm in the run log: `Destroy complete! Resources: 71 destroyed.` followed by `no runtime resource tagged Stack=runtime`. The run fails if anything is left.
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
| `up` red at `wait for services to be stable` or the smoke suite | The service does not start or does not answer | In CloudShell (admin): `aws logs tail /blocbuy/swe5001/monolith --since 1h`, and `aws ecs describe-services --cluster blocbuy-swe5001 --services monolith --query 'services[0].events[:10]'` for placement errors such as missing parameters |
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
