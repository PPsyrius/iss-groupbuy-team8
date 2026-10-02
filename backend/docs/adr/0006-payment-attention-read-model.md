# 0006: Payment owns the admin compensation-attention read model

Date: 2026-10-02
Status: Accepted
Issue: #252

## Context

The read-only admin table needs payment amounts, groupbuy compensation reasons, bloc references, buyer nicknames and refund progress in a single paged snapshot. Ordinary aggregate repositories must not reach into another domain's persistence internals.

## Decision

Payment owns the explicit `PaymentAttention` projection and `PaymentAttentionQuery` port. Its dedicated infrastructure mapper joins only the required persisted facts, never another context's Mapper, DO or aggregate. This narrowly scoped read model does not authorize cross-domain writes or joins in ordinary aggregate repositories.

`GET /api/v1/admin/payments/attention` lists non-deleted, successfully charged payments with a non-deleted `compensation_required` participation receipt and no succeeded non-deleted refund. Missing/deleted refund records appear as `pending`; stored `pending`, `processing`, `failed` and `review` statuses remain visible. Successfully refunded rows disappear. Open payment review cases (#134) alone are excluded because provider uncertainty is not proof of a charged, rejected participation. Applied participations refunded after team expiry are also excluded.

Rows sort by receipt creation time descending, then payment UUID descending. Count and page share a read-only repeatable-read transaction to prevent refund completion between the two reads from making totals inconsistent. The existing compensation receipt index, unique payment/refund keys and primary-key joins support the query; no new index or write workflow is introduced. Pagination is 1-based, default size 20, maximum 100, with a long offset.

The controller requires Sa-Token login, the admin role and the additive `payment:read` capability seeded to admin, matching the role-plus-capability pattern of other admin reads. The application also checks that the account is active. The query selects only the buyer's nickname; it never selects their email for display or returns their user ID. Missing, blank or deleted profiles and email-shaped or email-local-part fallback nicknames display `Unknown buyer` without dropping a financial row. Bloc IDs and numbers are retained even for soft-deleted blocs; either can be absent when no bloc exists (for example rejected START) or historical data is incomplete.

## Consequences

Two database reads serve every page, with no per-item enrichment or provider calls. The endpoint works while Stripe is disabled and cannot refund, retry, assign or dismiss a case. Its snapshot is informational: refund execution and review recovery continue through their existing flows. Query-model source columns must evolve alongside their owning contexts' migrations.
