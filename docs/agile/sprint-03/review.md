# Sprint 3 review (2026-09-28 to 2026-10-09)

Status: draft written at code freeze on 2026-10-08 from GitHub issue state, the day logs and read-only checks of dev. The Sprint Review on 2026-10-09 confirms or corrects it; the demo notes are filled in then.

## Goal

From the container issue #232: by 2026-10-09, on the **deployed dev environment**, a buyer can open an invite link that names the sharer, start or join a bloc on a real admin-configured listing, pay through Stripe Sandbox exactly the quoted price, see the order and bloc on `/account`, and be refunded automatically when the bloc expires. Key actions have a measured p95 at 100 concurrent users, and the first production release is cut from a tag with a rehearsed rollback.

**At freeze: not met on dev.** Every code path in the goal is merged and tested on `dev`, but Checkout and scheduling are still off on dev (#223), dev had no admin-configured startable listing until the US34 seed ran on the freeze evening (#222 Phase B still open), and production has not been applied (#226). The p95 part is met on a local stack (#180).

## Plan vs actual

Planned hours are the container issue's. The board Estimate field was not filled during the sprint, so "Remaining h" is a reconstruction at freeze: 0 for closed work, the planned hours for work not started, and an estimate for partly done work based on its day-log entries.

| Work item | Owner | Planned h | Remaining h at freeze | Status | Evidence |
|---|---|---|---|---|---|
| US25 admin team terms and price policy | @l0sgAi | 12 | 0 | Done | #221, PR #242 |
| US34 demo listings and admin on dev | @l0sgAi | 4 | 1 | Open: Phase A done on dev 2026-10-08 (4 `US34-*` listings, quotes match); Phase B (paid START and JOIN) waits for #223 | #222, PR #325 |
| US29 retire legacy payment paths | @PPsyrius | 8 | 0 | Done | #216, PR #234 |
| US13 Checkout, scheduling, refunds on dev | @Yorick-Yu | 12 | 7 | Open: runbook and local rehearsal only | #223, [log 2026-10-03](log/2026-10-03.md) |
| US26 charge the quoted price | @l0sgAi, @limjiechao | 8 | 0 | Done (not yet shown on dev) | #183, PR #235 |
| US23 performance baseline | @EricWang1358 | 14 | 0 | Done on a local stack | #180, PR #289, `docs/perf/sprint3-baseline.md` |
| US31 production release and rollback drill | @PPsyrius | 14 | 14 | Open: blocked on NFR-37 (#227) | #226 |
| US14 refund idempotency on dev | @Yorick-Yu | 8 | 4 | Open: local recovery evidence only | #224 |
| US15b invite names the sharer | @l0sgAi, @limjiechao | 6 | 0 | Done | #201, #230 |
| US30 unpaid intent cleanup | @EricWang1358 | 8 | 0 | Done (job off on dev) | #225, PR #291 |
| US33 buyer-journey, refusal, quote e2e | @limjiechao | 10 | 0 | Done | #228 |
| US32 signed-in journey on dev | @limjiechao | 4 | 4 | Open: waits for #223 and #222 | #229 |
| US28a payment findings | @Yorick-Yu | 8 | 0 | Done | #130, #134, PR #339 |
| US28b security findings | @l0sgAi, @PPsyrius | 6 | 1 | Open: NFR-37 decision; #96 done (NFR-01 enforced 2026-10-08) | #227, #96 |
| US35 catalog read performance | @EricWang1358 | 6 | 0 | Done | #205, #204 |
| US36 Sonar literals and TODO triage | @PPsyrius | 2 | 0 | Done | PR #218 |
| US37 login heading and document titles | @limjiechao | 2 | 0 | Done | #231, PRs #272, #337 |
| SM Scrum Master duties | @limjiechao | 10 | 1 | Review and retro on 2026-10-09 | #232 |
| EV evidence | others | 8 | 1 | Retro and progress report | — |
| **Total** | | **150** | **33** | | |

Unplanned work that landed in the sprint: Redis caching and rate limits (#208, #274–#276), the admin console backend asks BA-1 to BA-12 (#246–#256), catalog listing progress (#245), receipt refund wording (#392), and the SWE5001 ECS release pipeline (#376). #376's first promotion ([run 37470479078](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37470479078)) and a manual `up` and `down` on 2026-10-07 passed; the Sprint 3 promotion is due on 2026-10-09.

## Environment and CI at close

| Check | Command | Result |
|---|---|---|
| dev health | `curl -s https://d1b3np88z657cp.cloudfront.net/api/actuator/health` | 200 `{"status":"UP"}` (2026-10-08) |
| dev page | `curl -s -o /dev/null -w '%{http_code}' https://d1b3np88z657cp.cloudfront.net/` | 200 |
| dev catalog | `curl -s 'https://d1b3np88z657cp.cloudfront.net/api/v1/products?page=1&size=50'` | 14 listings after the US34 seed: 4 `US34-*` plus 10 old `DEMO-P-*`; the `DEMO-D-*` deals on 5 of them have fewer member rows than `current_size` |
| backend CI | `gh run list --workflow ci-backend.yml --branch dev --limit 10` | 10 success |
| frontend CI | `gh run list --workflow ci-frontend.yml --branch dev --limit 10` | 10 success |
| security CI | `gh run list --workflow security.yml --branch dev --limit 10` | 10 success |

## Demo notes

To be filled in at the Sprint Review. Because Checkout is off on dev, the paid START, JOIN and refund steps of the goal can only be shown on the local full stack unless #223 lands first.

## Carried over

- US13 #223 and US14 #224: dev rollout and on-dev acceptance; critical path for the demo.
- US34 #222: run the seed and the admin grant on dev, then Phase B after #223.
- US32 #229: waits for #223 and #222.
- US31 #226: production apply and drill; blocked on the NFR-37 decision in #227.
- #421, #422, #423: code complete in draft PR #424 (CI green); merge or carry.
- #371: low-priority admin form follow-up to #264.
