# Sprint 2 log (2026-09-14 to 2026-09-25)

Goal: see the `[T1] Sprint 2` container issue once filed (`frontend/docs/plan.md` T1-02). Tier 1
targets the week ending 2026-09-20, tier 2 the week ending 2026-09-25; code freeze 2026-09-24 EOD.

One entry per unit of work per member, newest at the bottom. Shape and rules:
`.claude/skills/agile-evidence-capture/references/templates.md`. Three to eight lines each.
Say what was verified and what was not.

## 2026-09-16 - @limjiechao - Evidence conventions and agile-evidence-capture skill

- Kind: ops, decision
- Changed: `.claude/skills/agile-evidence-capture/`, `.claude/settings.json` (commit hook),
  `docs/agile/`, `docs/quality/nfr-register.md`, `.github/ISSUE_TEMPLATE/`,
  `.github/PULL_REQUEST_TEMPLATE.md`, `CONTRIBUTING.md`,
  `frontend/docs/plan.md` (sprint log moved repo-wide, skill renamed)
- Verified: hook script exercised with sample commits (NFR config without evidence -> checklist;
  `style:` and `Evidence-skipped:` -> silent; enforce mode -> exit 2); snapshot script run against
  the live board into a scratch directory. Skill evaluated on four task prompts (login rate
  limiting, pin actions by SHA, UC1 `savingsPercent`, sprint close) in throwaway clones, each run
  with and without the skill and graded against 27 assertions: 27/27 with, 15/27 without; code
  quality equal in both, the difference is entirely the evidence artefacts. Not yet exercised by a
  teammate's session.
- Decisions: ADR `frontend/docs/adr/0008-documentation-and-evidence.md` (Accepted): sprint log and
  NFR register are repo-wide; ADRs stay a frontend practice, the backend keeps its D-table and
  design docs, cross-cutting decisions live in the owning `docs/` page
- Problems: Sprint 1 report shipped with TODO burndown and contribution cells; NFR changes
  (#84, #75, #45) had no issue or doc trail. Action: this change set. Status: in review.
- Next: file the Sprint 2 container issue (T1-02), create `nfr:*` labels, first board snapshot.

## 2026-09-16 - @limjiechao - T1-01 dev CloudFront day-1 probe (#44)

- Kind: ops
- Changed: this log only (zero code). Plan task T1-01 in `frontend/docs/plan.md`.
- Verified: against `https://d1b3np88z657cp.cloudfront.net` at about 13:00 UTC, health probe run
  three times with the same result. Script: three `curl` probes from the T1-01 task row plus the
  root-headers check.

| Probe | Result | Meaning |
|---|---|---|
| GET /api/actuator/health | `503 text/html`, `server: awselb/2.0`, `x-cache: Error from cloudfront` (3 of 3 runs) | `/api/*` behavior reaches the ALB; the ALB has no healthy target. Expected `200 application/json` |
| GET /api/v1/products?page=1 | `503 text/html` | same |
| GET /products/probe | `403 application/xml` (S3 OAC AccessDenied) | A1 SPA fallback attached: no |
| GET / and /index.html | `403 application/xml` | no frontend build in the bucket yet (expected before T1-14) |
| GET /api/v1/auth/oauth2/google | `503`, no `Location`, no `Set-Cookie` | B3 (Secure flag) and B5 (redirect_uri) evidence not obtainable today |
| GET / headers | CSP absent; HSTS absent | A2 absent |

- Problems: dev backend is down. The latest `ci-backend` run on `dev` (merge of #92,
  2026-09-16T12:23Z, run 35095495827) failed at job `deploy-existing-ec2` after `publish-ecr-dev`
  succeeded. Action: raised with @l0sgAi and @PPsyrius; re-run the OAuth probe once dev is healthy
  and append the B3/B5 rows here. Status: open.
- Decisions: none. Note for T2: `gh auth status` shows `read:project` on the current token and
  `gh project view 2 --owner PPsyrius` lists 21 items, so the board is readable at filing time.
- Next: T1-02 file the 13 blocker issues; T1-04 runtime pins.
