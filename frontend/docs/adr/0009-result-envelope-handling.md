# 0009. Result envelope handling

Date: 2026-09-16

## Status

Proposed

## Context

## Decision

2026-09-16: `OK_CODE = 'COMMON_200_000'`, not the brief's `'0'` — confirmed against
`backend/blocbuy-domain/.../CommonErrorCode.java` (`SUCCESS("COMMON_200_000", ...)`);
`envelope.ts` and `envelope.test.ts` use the real value (T1-07, `48805af`).

## Consequences
