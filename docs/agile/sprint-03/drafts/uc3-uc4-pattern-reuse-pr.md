# fix(groupbuy): reuse admission rules and preserve receipt identity

## What

Pre-payment quoting duplicated paid admission rules and allowed legacy deals with no paid leader to reach checkout. Share the aggregate's admission guards while retaining each caller's error priority, inject the existing paid-deal Factory Method, and extract typed START/JOIN fingerprint encoding with historical v1 byte compatibility. Quotes now reject counts below one before payment.

## Issue

Refs UC3/UC4 (#26/#27); incremental to completed #186. Proposed tasks: [creator injection](uc3-inject-existing-creator.md), [shared admission guards](uc4-share-admission-guards.md), and [typed receipt fingerprints](uc3-uc4-type-receipt-fingerprints.md). Replace these local draft references with filed issue numbers before publication.

## Decisions

[Owning paid-participation amendment](../../../../backend/docs/design/paid-group-participation.md#reuse-and-receipt-compatibility-amendment-2026-10-07). Reuse existing Factory Method and aggregate lifecycle policy; preserve event ordering, locks, transaction deadlines, receipt versions and attribution boundary.

## Evidence

- [x] Verification: Java 21 / Docker-backed `mvnw.cmd -B -ntp verify` succeeded on `codex/uc3-uc4-pattern-reuse` (base `4cc1f6f`): 1,615 cases, zero failures/errors, one opt-in performance skip; final `mvnw.cmd -B -ntp -pl blocbuy-application -am verify` passed 887 cases; aggregate refresh confirmed 100% line/branch coverage across the five touched production files; native review retained zero findings.
- [x] Contract: unchanged; no controllers, DTOs, public endpoints, migrations or frontend changes.
- UI change: not applicable.
- [x] [October 7 sprint log](../log/2026-10-07.md) added.
- [x] NFR-56 added in the [register](../../../quality/nfr-register.md).

## Checklist

- [x] Small PR, one concern: consistent and reusable UC3/UC4 admission behavior.
- [x] No secrets committed; no new environment variables.
- [x] Generated files not committed; no frontend generation required.

## Verification limits

This is local branch evidence. Merge, deployment, live Stripe test-mode behavior and hosted SonarCloud analysis are not claimed.
