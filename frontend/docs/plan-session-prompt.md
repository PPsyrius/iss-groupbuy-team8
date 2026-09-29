# Planning-session prompt (historical — executed 2026-09-16; do not re-run)

Prompt used to produce `frontend/docs/plan.md` from `frontend/docs/blueprint.md` revision 2. Kept in the repo so the report can show how the plan was derived. Run from the repository root in a fresh Claude Code session on branch `feature/frontend-scaffold`.

```text
Produce frontend/docs/plan.md for the BlocBuy frontend. This is a planning session: no package installs, no scaffolding, no Terraform or runbook edits, no git commits other than the plan file and any ADR stubs it names.

Read, in this order, before anything else:
1. frontend/docs/blueprint.md (revision 2, 2026-09-15). Its "Settled decisions" are binding. Its OPEN list is the only set of things you may leave undecided.
2. frontend/docs/blueprint-review.md. Sections 1 (verified findings), 3 (blocker list B1-B5, A1-A4, T1-T4), and 4 (assumptions) are facts and constraints, not suggestions.
3. contracts/openapi.json, docs/deploy/dev-cloudfront-deployment.md, backend/blocbuy-bootstrap/src/main/resources/application.yml and application-ec2.yml, backend/blocbuy-adapter/src/main/java/com/blocbuy/identity/web/AuthController.java, .github/workflows/ci-backend.yml, .github/CODEOWNERS, CONTRIBUTING.md, .github/dependabot.yml.

Do not read docs/design/repository-structure.md as a source of fact; it describes an intended repository.

Where the plan names a TanStack Router, HeroUI, hey-api, oxlint, or Vite API, verify it against the current official documentation and cite the source in the task row. Re-verify every pinned version against the npm registry on the day the plan is produced; the blueprint pins were checked on 2026-09-15.

Constraints:
- Sprint 2 is 2026-09-14 to 2026-09-25. Tier 1 (toolchain, scaffold, agent config, CI, runbook steps, deploy to dev CloudFront, Playwright smoke) targets the week ending 2026-09-20. Tier 2 (list/detail, /account, logout, filter form, Terraform frontend-stack import, native-compiler spike, react-table evaluation for #31) targets the week ending 2026-09-25.
- Order tasks by dependency, then by risk. Validate the CloudFront /api behavior, the SPA fallback function, and the OAuth return (B1) as early as their owners allow. Where a task depends on B1-B5 or A1-A4, state the assumption you plan under and what changes if it resolves the other way.
- Every task row has: id, tier, purpose, files touched, exact verification command, depends-on (task ids or blocker ids), and the GitHub issue it advances (#44, #42, #47, #48, or a new issue you name).
- Every PR the plan implies must satisfy the evidence checklist in blueprint-review.md D9. Say which ADR(s) each PR cites.

Deliverables, all inside frontend/docs/plan.md:
1. Task table as above, tier 1 then tier 2.
2. The exact pnpm check chain and every package.json script it relies on.
3. Proposed frontend/AGENTS.md content in full (conventions plus the Intent guidance block placeholder), and the one-line frontend/CLAUDE.md.
4. Proposed root .claude/settings.json in full: hooks (PostToolUse oxfmt + oxlint --fix on the touched file, Stop typecheck, SessionStart pnpm install when the generated client dir is missing), all matchers restricted to frontend/**, and the permission allowlist. Verify the current hooks schema against the Claude Code docs.
5. Proposed .github/workflows/ci-frontend.yml job list with triggers, path filters, the required status check name, the import.meta.env grep, intent stale, and the gated deploy-s3-dev job. Proposed additions to security.yml, dependabot.yml, CODEOWNERS, .gitignore, and a .github/PULL_REQUEST_TEMPLATE.md. Mark each as needing @PPsyrius review.
6. The exact runbook steps to append to docs/deploy/dev-cloudfront-deployment.md for A1-A3 (CloudFront Function code, response-headers policy JSON with a starting CSP, OIDC trust and permission policy JSON under deployment/frontend/iam/), plus the change to section 10 from npm to pnpm.
7. ADR list numbered from 0001 in frontend/docs/adr/, each with title, one-sentence decision, alternatives considered, and the plan tasks it covers. Minimum set is in blueprint.md Deliverables item 3. Create the frontend/docs/adr/README.md index and empty numbered stubs with only the Nygard headers.
8. Blocker list: B1-B5, A1-A4, T1-T4 rewritten as ready-to-file GitHub issue bodies (title, owner, body, labels, which plan tasks they gate).
9. TanStack skills manifest per blueprint.md Deliverables item 5, reflecting that only router-core, router-plugin, and react-table ship Intent skills today. Include the verification task for transitive router-core discovery under pnpm.
10. A "deviations from settled decisions" section: anything you found wrong or outdated on the day you ran, with evidence and URL. Do not silently deviate.

Output style: tight prose, tables for tasks and manifests, no motivational filler. When finished, list the files you created and the single command to run the first task.
```
