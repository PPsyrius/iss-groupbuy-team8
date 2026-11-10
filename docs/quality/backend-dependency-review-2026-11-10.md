# Backend dependency release review — 10 November 2026

Refs #759–#762; NFR-06/NFR-26. The owner authorized adopting the five additional updates below, including releases inside Dependabot's seven-day cooldown. The cooldown in `.github/dependabot.yml` remains unchanged. This is local feature-branch evidence, not merge, deployment, hosted CI or real-provider acceptance.

## Release checks before execution

| Dependency | Previous local target → reviewed target | Official release | Findings and disposition |
|---|---|---|---|
| AWS SDK BOM | 2.57.11 → 2.57.12 | [9 November](https://github.com/aws/aws-sdk-java-v2/releases/tag/2.57.12) | Inside cooldown. Official tag follows the existing AWS automation lineage. Reviewed the account-ID credential-provider source changes; endpoint behavior changes are disclosed upstream. No compromise indicator or matching advisory found in the checks below. |
| Springdoc Maven plugin | 1.5 → 1.6 | [5 November](https://github.com/springdoc/springdoc-openapi-maven-plugin/releases/tag/v1.6) | Inside cooldown. Reviewed the maintainer's migration from Travis to GitHub Actions and the HTTP client changes. Publication still targets Maven Central; generation now has a 60-second request timeout. No compromise indicator or matching advisory found. |
| Spotless Maven plugin | 3.10.4 → 3.11.0 | [2 November](https://github.com/diffplug/spotless/releases/tag/maven/3.11.0) | Outside cooldown. Reviewed formatter integration and build-file changes. Reported [Gradle Markdown-Javadoc import issue #3161](https://github.com/diffplug/spotless/issues/3161) does not describe this Maven/Java 21 setup. No compromise indicator or matching advisory found. |
| Error Prone annotations | 2.51.0 → 2.52.0 | [2 November](https://github.com/google/error-prone/releases/tag/v2.52.0) | Outside cooldown. In the `annotations/` subtree, the upstream comparison changes only its POM version. The repository uses the annotations artifact, not the Error Prone compiler/checker plugin. No compromise indicator or matching advisory found. |
| google-java-format | 1.36.1 → 2.0.0 | [30 October](https://github.com/google/google-java-format/releases/tag/v2.0.0) | Outside cooldown. New layout engine and default non-whitespace cleanups are disclosed. [IntelliJ issue #1516](https://github.com/google/google-java-format/issues/1516) was fixed in IDE plugin 2.0.0.1; it is not a Maven artifact replacement. Adopt Maven 2.0.0 with AOSP and `cleanup=false`. |

For every target, checked the official repository's release/tag, source comparison, publishing-related changes and issues opened since the release. A cross-repository issue/PR search for malware, compromised, hijack and supply-chain reports updated since 30 October also returned zero results. Queried GitHub's global advisory API with `ecosystem=maven` and `affects=<groupId>:<artifactId>@<version>`: zero results for the five targets, and separately for AWS `auth`, `sns` and `sqs` 2.57.12. These are exact-package advisory lookups, not a complete transitive dependency scan or evidence that no undiscovered vulnerability exists.

Downloaded the following artifacts from `https://repo.maven.apache.org/maven2/`, recomputed their strongest available published checksum and sampled source JAR contents against the official tag. Sampled sources matched for Springdoc's `ApiDocsClient`, Spotless's Maven `GoogleJavaFormat` adapter, Error Prone's `CheckReturnValue`, google-java-format's `JavaFormatterOptions`, and AWS's `HttpCredentialsLoader`.

| Artifact | Central checksum matched | Locally computed SHA-256 |
|---|---|---|
| AWS BOM 2.57.12 POM | SHA-512 | `13e310d290a16209e74ad573c83830dd9bb45645c9c341fef925d881e71bba31` |
| AWS auth 2.57.12 JAR | SHA-512 | `6877e32184515a9d4da034d2c00c57c9dd03cb02ab7a06af96b9fc466a54849a` |
| Springdoc Maven plugin 1.6 JAR | SHA-512 | `f0b53747afda8c7666c1e5eedd864734ba0757a7a2c9eb2c72145829b6279f94` |
| Spotless Maven plugin 3.11.0 JAR | SHA-1 only | `30ca83165e4ef0c47038f70b553ca9d644408077e2b0cc59d629c93d3b63badb` |
| Error Prone annotations 2.52.0 JAR | SHA-512 | `24822b246a2da324006fc5a1c940b1d2cf9cd716e530155c39fea695d5ab01c4` |
| google-java-format 2.0.0 JAR | SHA-512 | `7a03f28f7379dcb32e5fc5a6ded62524c4a31e9616e3b477a2f71d7124c5ccad` |

All six artifacts have detached PGP signature files, but their signatures and signer trust chains were not independently verified. The release tags/commits inspected are unsigned according to GitHub. Matching a checksum from the same repository is consistency evidence, not independent authenticity; sampled source equality does not prove the published binary was reproducibly built from that source. The review found no observed compromise indicator, not proof of safety.

## Sequential adoption and verification

All stages use Docker's Temurin JDK 21 and the repository's Maven 3.10.0 wrapper distribution. Sources are copied into an isolated Linux named volume with CRLF normalized; its mount path equals the daemon-visible volume path so sibling Testcontainers brokers can read fixture binds. The Maven cache is separately copied from the existing cache, not shared writable with the other OpenCode session. No real AWS or Stripe credentials are supplied.

| Stage | Command from `backend/` | Observed result |
|---|---|---|
| AWS 2.57.12 | `./mvnw -B -ntp -pl blocbuy-infrastructure -am test -Dtest=SqsNotificationEventConsumerTest,SqsOutboxEventTransportTest,SnsOutboxEventTransportTest -Dsurefire.failIfNoSpecifiedTests=false` | 25 focused tests passed; dependency convergence and compilation passed. This does not exercise real ECS/IMDS credentials. |
| Springdoc 1.6 | `./mvnw -B -ntp verify -Popenapi-export -DskipTests` | All 11 modules passed; generated contract matched the original after CRLF normalization. Static/export verification only; no tests claimed. |
| Spotless 3.11.0 | `./mvnw -B -ntp validate` | All 11 modules passed with the prior explicit formatter pin. |
| Error Prone annotations 2.52.0 | `./mvnw -B -ntp -pl blocbuy-infrastructure -am test -Dtest=StripeCheckoutGatewayTest,SqsNotificationEventConsumerTest,SqsOutboxEventTransportTest,SnsOutboxEventTransportTest -Dsurefire.failIfNoSpecifiedTests=false` | 47 focused tests passed; dependency convergence and compilation passed. |
| google-java-format 2.0.0 | `./mvnw -B -ntp spotless:apply` then `./mvnw -B -ntp validate` | 109 Java files reformatted; all 11 modules passed validation. JDK 21 javac parse trees, excluding Javadoc, matched before/after for all 109 files, including parsed text-block values. All 608 working Java files match the reviewed formatter output after CRLF normalization. |
| Final combined gates | `./mvnw -B -ntp clean verify -Pci`, then `./mvnw -B -ntp verify -Popenapi-export -DskipTests` | All 11 modules passed full verification in 7 min 31 s. The authoritative 235 JUnit XML reports contain 2,058 tests, zero failures/errors and one existing opt-in performance skip (`perf.enabled` absent). Export passed in 46.571 s and matched the source contract. |
| Strict export confirmation | `./mvnw -B -ntp verify -Popenapi-export -DskipTests -Dspringdoc.failOnError=true` | Passed in 57.623 s after deleting only the disposable generated contract first. Newly generated output matched the read-only source contract after CRLF normalization; tests were not rerun in this export-only step. |

Formatting is machine-owned. Keep the formatter-only changes in a separate `style:` commit from version changes and the existing Stripe regression. The initially attempted direct wrapper invocation on the Windows bind mount failed on CRLF before Maven ran; formatting was then applied using the same cached Maven 3.10.0 distribution without changing the committed wrapper or Java source semantics.

Harness failures are retained separately from Maven outcomes: the first combined run omitted `scripts/payment/rollout-backlog.sql` from its isolated copy; the complete rerun included repository scripts without skipping or weakening the test. Both Maven commands then succeeded, but the outer shell hit a trailing script-read error after its running script was edited. The runner now executes an immutable per-launch script copy; the strict export confirmation subsequently completed with exit 0. The console-derived preliminary count double-counted ten tests; the JUnit XML total above is authoritative.

JaCoCo's monolith aggregate has 5,443 covered/zero missed lines and 2,316 covered/two missed branches (100% lines, 99.91% branches). The partial branches are in `IntegrationEventDecoder.java:57` and `SqsNotificationEventConsumer.java:395`; no production branch was added by these updates or the AST-preserving reformat. This is local aggregate coverage, not the hosted Sonar new-code gate. Full-verification log SHA-256: `5dfe0528c79dd5be8dc54169d6785eee809c0ab71dd7007fb80e011055b0b9f9`; strict-export log: `8380494a9c753e09001e6d84540f9e778c9404d6ba961c8578b86f57f2f32634`; aggregate XML: `63aa28f6a0949891c6161b19de0b61a16ea57db2b6b480c2161852a45ba2f856`.

Trivy 0.74.0's dev-inclusive POM scan (`fs --scanners vuln --include-dev-deps --ignore-unfixed --severity MEDIUM,HIGH,CRITICAL`) returned zero findings across all 11 POM targets. Its report SHA-256 is `8d03c68f33d4926204ed3780408fcf39e46dc88b97df6c4f3c1adda42891f12d`. A separate `rootfs --scanners vuln --ignore-unfixed --severity MEDIUM,HIGH,CRITICAL` scan inventoried all five downloaded JARs (AWS auth plus the four tool/annotation artifacts) at the intended versions, also with zero findings; report SHA-256 `c01a7bbd61d32b682d149ae7a10750bd8c02b90485f05b3d421bead5aedac056`. These scopes do not establish the complete runtime closure of every Maven plugin, OS-image safety, absence of unfixed/lower-severity findings, or malware absence. No suppression was added.

## Revision and limits

Checked on `feature/backend-dependency-updates-20261110`, based on `1a1fae0af3d7275ae4595a192289ca01eb6fbd99`, plus the restored backend changes and these uncommitted updates. The final verification snapshot contains 695 normalized backend source/build/resource hashes. Its manifest digest is `99778320f002a4cbca7fe1a18e5023621a699f53f31e4f79889372711b972358`: SHA-256 of sorted `path<TAB>file-sha256<LF>` records; file digests normalize CRLF to LF. The normalized parent POM digest is `a68842456ebfda034011f84bbbce96a44d9784919963be9f4469769a2027956d`.

Raw logs, source snapshots, artifact hashes and the pre-adoption patch are retained locally under the approved temporary directory's `backend-release-review-20261110/`. Hosted checks, production images, the complete Maven-plugin runtime dependency closure, real AWS credential-provider behavior and live Stripe settlement/refunds are not established by these release checks. The existing Stripe outbound-version and dev-auto-deployment precautions remain in the [payment boundary](../../backend/docs/payment/stripe-release-boundary.md#stripe-java-341-maintenance-update--2026-11-10).
