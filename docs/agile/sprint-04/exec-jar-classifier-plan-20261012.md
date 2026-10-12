# #455 PR 1: executable JAR classifier — implementation plan

Date: 2026-10-12 · Branch: `feature/microservice-20261011` · Issue: #455 (Sprint 4 container #465, task P2) · Owner: los gai · Related: step A1 of the [microservice split plan](microservice-split-plan-20261012.md), [Sprint 4 plan §4 item 3, §8, §9](plan.md), [SWE5001 handoff](../../swe5001/HANDOFF.md), [SWE5001 backend plan WP-B2 and risk table](../../swe5001/impl-backend-architecture.md), finding F1 in the same document

> **Status (2026-10-12): draft for review. Nothing implemented yet.**

## 1. Goal and acceptance criteria

`blocbuy-bootstrap` publishes two JARs from one build: a **plain library JAR** that the coming `services/*-svc` modules can depend on, and the **executable Spring Boot JAR** that every image and CI step runs today. This PR changes only the packaging and the places that consume the executable JAR; the SWE5006 `dev` image, its behaviour and its deploy path stay the same.

| # | Criterion | Delivered by |
| --- | --- | --- |
| AC1 | `./mvnw -B verify` produces `blocbuy-bootstrap/target/blocbuy-backend.jar` (plain classes, no `BOOT-INF/`) and `blocbuy-backend-exec.jar` (Spring Boot layout, `Main-Class: org.springframework.boot.loader.launch.JarLauncher`, `Start-Class: com.blocbuy.bootstrap.BlocBuyApplication`) | §4 C1, V2 |
| AC2 | Every image, workflow step and local Dockerfile that runs the application uses the `-exec` JAR; a leftover reference to the plain JAR fails the build loudly instead of shipping a non-runnable image | §4 C2–C8, §5 guards, V3–V5 |
| AC3 | CI on the PR is green: `build-quality` (verify, OpenAPI export from the JAR), `docker` (image build, EC2-profile smoke, Trivy), contract check, Sonar | V6 |
| AC4 | After merge, the `dev` push deploys and `GET /api/actuator/health` through CloudFront returns `UP` | V7 |

Non-goals: the `services/*-svc` modules, `MyBatisConfig` conditional, ArchUnit and `RoleCode`, platform starter, grants, ADR 0008 (later PRs of #455); any runtime or configuration change; renaming the image or the ECR repository.

## 2. Baseline (read on 2026-10-12 from `3a0f6908`)

| Piece | State | Consequence |
| --- | --- | --- |
| `backend/blocbuy-bootstrap/pom.xml` | Spring Boot 4.1.1; today's JAR manifest has `Main-Class: org.springframework.boot.loader.launch.JarLauncher`, `Start-Class: com.blocbuy.bootstrap.BlocBuyApplication`; `<finalName>blocbuy-backend</finalName>`; `spring-boot-maven-plugin` with `<skip>false</skip>` and the parent's default `repackage` execution, no classifier | The repackaged JAR **replaces** the main artifact, so no other module can use bootstrap as a dependency (F1). |
| Root `backend/pom.xml` | `spring-boot-maven-plugin` `<skip>true</skip>` for every other module | Unchanged. |
| `openapi-export` Maven profile | `spring-boot:start`/`stop` from classes | Unaffected by the artifact name. |
| JaCoCo `report-aggregate` | Bound to `verify` in bootstrap; output `target/site/jacoco-aggregate/jacoco.xml` | Unaffected; Sonar path unchanged. |
| Consumers of `blocbuy-backend.jar` (10) | See §4 C2–C8 | Each must move to `blocbuy-backend-exec.jar`. |
| `backend/.dockerignore` | Allows only `blocbuy-bootstrap/target/blocbuy-backend.jar` into the build context, plus a stale `blocbuy-bootstrap-*.jar` exception that no Dockerfile uses | Becomes the main guard (§5). |
| Docs | Only the JaCoCo path is mentioned in living docs (`backend/README.md`, `docs/ci/devsecops-pipeline-plan.md`, skill references); historical sprint plans mention the old JAR | No living doc names the runnable JAR; historical records stay untouched. |

## 3. Decision: repackage with the `exec` classifier

Chosen: configure the `repackage` goal with `<classifier>exec</classifier>`. Maven's main artifact becomes the plain JAR (`blocbuy-backend.jar`), and Spring Boot writes the executable one alongside it (`blocbuy-backend-exec.jar`). This is the layout Spring Boot documents for a module that is both an application and a dependency, and the one WP-B2 already describes.

| Alternative | Why not |
| --- | --- |
| Attach a second plain JAR with `maven-jar-plugin` and a `lib` classifier, keep the executable JAR as the main artifact (zero consumer changes) | Non-standard; a classified dependency is not resolvable from the reactor in builds that stop before `package` (`./mvnw test`, IDE imports), so the service modules would break the everyday developer loop. |
| Move `db/migration`, `application*.yml`, Stripe configuration, scheduling and `TraceIdFilter` out of bootstrap into a new library module | Same end state with a far larger diff in the SWE5006 code path one week before two presentations; WP-B3 can still extract a starter later. |
| One image for all three services, role chosen by a property (`BLOCBUY_SERVICE`) and `@ConditionalOnProperty` | No build change at all, but deviates from the proposal's split-by-assembly design and weakens the platform-reuse story SWE5001 grades. Kept as the fallback if this PR destabilises `dev` (§7). |
| Rename `finalName` so the plain JAR gets a different stem | Costs more renames and diverges from the documented `blocbuy-backend-exec.jar` name; the guards in §5 already catch stale references. |

## 4. Changes (one PR, about 12 files)

| # | File | Change |
| --- | --- | --- |
| C1 | `backend/blocbuy-bootstrap/pom.xml` | Add `<classifier>exec</classifier>` to the `spring-boot-maven-plugin` configuration; keep `finalName`, `skip=false`, the Lombok exclude and the profiles. Comment: plain main artifact for `services/*-svc`, executable `-exec` for images. |
| C2 | `backend/Dockerfile` | `COPY blocbuy-bootstrap/target/blocbuy-backend-exec.jar app.jar`; header comment names the `-exec` JAR. |
| C3 | `backend/Dockerfile.ci` | Same `COPY` change and comment (line 3). |
| C4 | `backend/.dockerignore` | Allow-list `!blocbuy-bootstrap/target/blocbuy-backend-exec.jar` only; drop the old `blocbuy-backend.jar` and the stale `blocbuy-bootstrap-*.jar` exceptions. |
| C5 | `.github/workflows/ci-backend.yml` | `build-quality`: upload `blocbuy-backend-exec.jar` as `backend-application`; OpenAPI export runs `java -jar` on it. `docker`: verify step checks the `-exec` file. `package-dev`: upload the `-exec` JAR as `backend-application-dev-<sha>`. Artifact names and download paths stay the same. |
| C6 | `.github/workflows/swe5001-service-images.yml` | Matrix `jar: blocbuy-bootstrap/target/blocbuy-backend-exec.jar` for `monolith`. |
| C7 | `fullstack-localhost/backend.Dockerfile` | `COPY --from=build .../blocbuy-backend-exec.jar app.jar`. |
| C8 | `.github/scripts/assert-boot-jar.sh` (new) | `assert-boot-jar.sh <jar>`: fails unless the file exists, its manifest has `Main-Class: org.springframework.boot.loader.launch.JarLauncher` and a `Start-Class:`, and it contains `BOOT-INF/lib/`. Called after packaging in `build-quality`, `package-dev` and the SWE5001 `Package` step. |
| C9 | `docs/swe5001/impl-backend-architecture.md` | WP-B2: mark the classifier step done with the PR link; F1 consequence updated. |
| C10 | `docs/agile/sprint-04/log/2026-10-12.md`, `log.md` | Day-log entry. |

`ci-backend.yml` path filters list individual `.github/scripts/*` files (for example `validate-trivy-db.py`); C8's script is added to them so a change to it re-runs the backend pipeline.

## 5. Guards against shipping the plain JAR

The plain JAR keeps the old file name, so a missed reference would still find a file. Three independent guards make that fail early:

1. **Docker context allow-list (C4):** only `blocbuy-backend-exec.jar` enters the build context, so any `COPY …/blocbuy-backend.jar` fails with "not found" in every Dockerfile, including the `dev` delivery job that has no smoke test.
2. **Manifest check (C8):** each job that produces a deployable JAR asserts the Spring Boot launcher layout before uploading it.
3. **Existing runtime checks:** the OpenAPI export (`java -jar`) and the EC2-profile container smoke in `docker` fail on a non-executable JAR.

## 6. Verification

| # | Check | Command | Proves |
| --- | --- | --- | --- |
| V1 | Baseline before the change | `./mvnw -B -q -DskipTests package && ls -l blocbuy-bootstrap/target/*.jar` | Today only `blocbuy-backend.jar` (executable) exists |
| V2 | Both artifacts after C1 | same, then `unzip -l blocbuy-backend.jar \| grep -c BOOT-INF` = 0 and `.github/scripts/assert-boot-jar.sh blocbuy-bootstrap/target/blocbuy-backend-exec.jar` passes; the same script fails on `blocbuy-backend.jar` | AC1, C8 works in both directions |
| V3 | Full build | `./mvnw -B verify` with Docker running | Tests, Spotless, Checkstyle, SpotBugs, ArchUnit, JaCoCo aggregate unchanged |
| V4 | Images build and boot | `docker build -f Dockerfile.ci -t blocbuy-backend:exec-check .` and `docker build -f Dockerfile .`; run the CI smoke command locally (EC2 profile, `/api/actuator/health`) | C2–C4 |
| V5 | Guard really guards | temporarily point `Dockerfile.ci` at `blocbuy-backend.jar`; `docker build` must fail on the missing file; revert | §5 guard 1 |
| V6 | PR CI | `build-quality`, `docker`, `contract-check`, `security`, both Sonar gates green on the PR head | AC3 |
| V7 | After merge | `gh run watch` on the `dev` push of `ci-backend.yml` (package-dev → publish-ecr-dev → deploy); then `curl -s https://d1b3np88z657cp.cloudfront.net/api/actuator/health` returns `{"status":"UP"}` | AC4 |

Not verified by this PR: `swe5001-service-images.yml` (C6) runs only on PRs into `prod`; it is first exercised by the next promotion PR. The change is the same one-line path pattern as C5 and C8 guards it.

## 7. Risks and rollback

| Risk | Impact | Mitigation |
| --- | --- | --- |
| A consumer outside this list runs the old JAR name | Non-runnable image | §5 guards; a repository-wide `git grep -n 'blocbuy-backend.jar'` after the change must return only historical docs and the plain-JAR assertions |
| `dev` deploy breaks after merge | SWE5006 demo environment down | Merge in a quiet window, watch V7; rollback is `git revert` of this single PR (no data, schema or configuration change) |
| Repeated breakage of the build path | Blocks #455 | Switch to the single-image fallback (§3, third alternative) and record it in ADR 0008 |

## 8. Evidence and decisions

- Decision record: the classifier choice is part of WP-B2; it is recorded in backend ADR 0008 (dual deployment topology), which #455 adds in its next PR, and cross-linked from this plan. No new ADR for this PR alone.
- NFR register: no new row proposed; this PR keeps the existing "Docker packages the exact verified JAR" behaviour (NFR-11 artifact reuse) and adds a guard. Reviewer to confirm.
- Day log entry with V1–V7 results; PR body follows `.github/PULL_REQUEST_TEMPLATE.md` (`Refs #455`, not `Closes`).

## 9. Questions for the reviewer

1. Is the `-exec` name acceptable, or should the executable JAR keep `blocbuy-backend.jar` and the plain one take a new name (more renames, see §3)?
2. Put the manifest check in `.github/scripts/assert-boot-jar.sh` (proposed) or inline in each workflow step?
3. Any objection to dropping the stale `blocbuy-bootstrap-*.jar` exception from `.dockerignore` in this PR?
