# UC9 — administrator listing management (#32)

## Scope and compatibility

API-only catalog CRUD, not a management UI, inventory adjustment, checkout or Stripe implementation.
The accepted deletion rule is: only never-published drafts with no business references can be
soft-deleted. Published listings can be delisted, never returned to draft or physically purged.

This increment builds on the merged Boot 4.1.1 / Java 21 baseline. The older pinned versions in
`.claude/skills/backend-constraints-skill` predate the merged Boot upgrade; this feature introduces
no framework/dependency upgrade. COLA layers, MyBatis, PostgreSQL 18, Sa-Token and the existing
HTTP 200 success `Result` convention remain unchanged. Failures retain their real HTTP status.
Existing buyer browse and first-team quote endpoints and response shapes remain unchanged.
The OpenAPI contract is generated from the application, never hand-edited.

## Design problems and actual patterns

- **State:** `ProductStatus` has concrete per-state behavior; `Product.publish`, `delist`, `revise`
  and `requireDeletable` delegate rules to that state. Persisted codes remain `draft`, `on_sale`,
  `off_sale`. There is no public status setter and no route back to draft. This is not merely an
  enum labelled as a pattern. A new state must define its behavior and transition tests.
- **Repository:** extend the existing `ProductRepository`, not a duplicate listing aggregate or
  persistence layer. Infrastructure owns DO/JSON conversion and versioned SQL; application owns
  transactions. Product reconstitution gains an additive version overload, retaining existing
  callers. Product and team-price-policy versions are separate concurrency tokens.
- **Strategy:** the existing `PricingStrategyResolver` / `PricingStrategy` remains authoritative
  for browsing and first-team quotes. UC9 does not duplicate the pricing formula, introduce a
  second strategy registry, or call a factory method a GoF Factory Method.
- **Cross-use-case boundary:** deletion consults catalog price-history persistence and groupbuy's
  public `GroupDealQueryService` boolean reference query, never another domain's mapper. The
  existing open-deal browse query is unsuitable because closed/deleted history still counts.

## HTTP contract

All routes are under `/api/v1/admin/products`. Every operation requires the existing `admin` role
AND `catalog:product:write` permission, currently granted only to administrators by V6. Reads are
also protected. The actor comes from Sa-Token, and application services recheck account activity.
No new auth framework, token domain or permission migration is introduced.
Generated OpenAPI declares the `blocbuyToken` header apiKey scheme on these six operations only;
it does not add a global security requirement or change anonymous buyer browsing. UC9 schema
patterns enforce complete strings (including rejecting trailing newlines) and positive money,
so JSON Schema substring matching does not weaken the documented request constraints.

| Method/path | Semantics |
| --- | --- |
| POST `/` | Create a draft with server UUIDv7, version 0, soldCount 0 and existing `dual` pricing mode. |
| GET `/` | Page all non-deleted listings; optional `status`; page >= 1, size 1–100; newest first with UUID tie-breaker. |
| GET `/{id}` | Read draft/on-sale/off-sale listing including its version; deleted/absent returns 404. |
| PUT `/{id}` | Replace `listing` content with required `expectedVersion`; do not accept productNo/status/counters/pricingMode. |
| PUT `/{id}/status` | Required `status` (`on_sale` or `off_sale`) and `expectedVersion`. |
| DELETE `/{id}?expectedVersion=N` | Conditional soft deletion of an unreferenced draft only; row remains for audit. |

Creation example:

```json
{
  "productNo": "TEA-001",
  "listing": {
    "title": "Green tea",
    "description": "Leaf tea",
    "images": [],
    "originalPrice": "12.00",
    "currency": "sgd",
    "stock": 5
  }
}
```

Optional content fields: subtitle, description, coverImage, images, category. Omission clears an
optional field on PUT; this is replacement, not PATCH. Images are display data, never fetched by
the server. Money is an exact positive decimal string, at most 16 integer and 2 fractional digits;
v1 creation/editing supports SGD consistently with first-team quotes. Unknown input fields are
rejected by the existing global Jackson policy.

Before publication price and initial stock may change. After first publication, editorial content
may change, but originalPrice/currency/stock must match current values: generic catalog editing
must not become stock replenishment or silently invalidate configured prices. soldCount,
productNo and pricingMode are never writable here. Future stock or commercial-term changes need
their owning business command and explicit quote/deal compatibility rules, not relaxed setters.
Publication requires positive price and stock. Policy configuration remains a separate existing API.

POST is not replay-idempotent: duplicate live productNo returns `CATALOG_409_003`; database
uniqueness settles concurrent duplicates. Mutations require the current product version and
increment once. A stale retry returns `CATALOG_412_002` without mutation; clients must re-read,
not automatically overwrite. DELETE is effect-idempotent: repeating it returns 404, not another
mutation. Illegal lifecycle/term edits return `CATALOG_409_002`; business references return
`CATALOG_409_004`; invalid listing fields return `CATALOG_400_002` or existing protocol validation
codes. Omitting DELETE's required version returns HTTP 400 / `COMMON_400_001`, never 500.
No business exception is converted to a success envelope.

## Transactions, history and extension obligations

Commands use a 10-second bounded application transaction, locking `biz_product` first with the
same `FOR UPDATE` used by quotation configuration/issuance, then checking actor and version.
SQL also compares expectedVersion and deleted=false and checks affected rows. No provider call,
network call or retry loop occurs within the transaction. Stock edits on drafts are guarded by
the same row lock/version. create_by/update_by and update_time are maintained; no cascade exists.

Deletion checks zero soldCount plus ALL policy, quote and group-deal references, including expired,
closed and logically deleted rows. Nothing deletes or rewrites these snapshots. V8 adds historical
product lookup indexes for group deals and quotes; policies already have the required index prefix.
Two partial indexes support active admin pagination with and without status filtering. Pagination
retains the existing offset contract; these indexes do not eliminate the cost of very deep offsets.
V4–V7 remain untouched. V8 is transactional with bounded lock/statement timeouts and must be
scheduled appropriately on populated tables; large live tables require a reviewed concurrent
index rollout. No foreign keys or CHECK constraints are introduced, per the team DDL agreement.

Current order persistence is a placeholder: `backend/docs/db/order.sql` is design DDL, not a
migrated table. Payment references orders, not products. Before order persistence is enabled,
the order owner must expose its product-reference facade, wire that check into deletion, and
add integration tests. Do not add a fake always-false order check and claim order safety.

Quotation writers require on_sale under the product lock, so legitimate quote creation cannot
race with deletion of a never-published draft. `PaidGroupDealService.startConfirmed` remains a
trusted internal collaborator that accepts an already validated catalog/inventory snapshot.
Its future checkout caller must validate the on-sale product under the product-first transaction
protocol before creating a reference. This patch does NOT retrofit that unfinished payment/checkout
orchestration. Arbitrary SQL/imports that ignore the protocol can violate invariants; they are not
supported writers. Orphan repair and physical retention/purge remain owner-coordinated work.

## Verification contract

Pure domain tests cover lifecycle behavior, publication stock guards, frozen commercial terms
and money validation. Application tests cover product-first lock order, trusted actor validation,
references, stale preconditions and bounded pagination. `AdminListingFlowIT` uses MockMvc's servlet
request chain, real Sa-Token RBAC, Flyway, application services and PostgreSQL 18; sessions use the
existing test-only memory store. This is not a network/browser end-to-end test. It exercises CRUD,
authorization (including role-only and permission-only denials), input validation, retained
histories, omitted-field replacement, number reuse, generated contract metadata and competing
edits. Existing browse, quotation and paid-group tests remain regression gates.

Run `mvnw.cmd verify` and regenerate with `verify -Popenapi-export`; run the repository's API
compatibility check before a PR. Passing new tests alone is not proof of a green full build or
live Stripe/Google integration. Runtime verification results are reported separately.

## Manual local acceptance checklist

This checklist is prepared for the maintainer; automated MockMvc coverage does not mark it complete.
Use the normal local application profile, an isolated local database and an existing administrator
session with `catalog:product:write`. Do not add a login bypass or grant production permissions
just to test this API. The local Swagger entry is `/api/swagger-ui.html`; send the normal
`blocbuy-token` header with management requests.

1. Create a uniquely numbered draft using the example above; record its id and version 0.
2. Read/edit it as administrator, then retry with the old version: expect 412 and no overwrite.
3. Confirm buyers cannot read the draft. Publish with the latest version; buyer detail becomes 200.
4. Delist it; buyer detail becomes 404. The admin read and unchanged historical data remain.
5. Attempt to delete the delisted item: expect 409. Create a second, unreferenced draft and delete
   that instead; verify it disappears from admin reads while its database audit row remains.
6. Repeat management calls with no session and a buyer session: expect 401 and 403 respectively.

## Operational handoff for a future PR

No deployment is performed by this local increment. Before release, the backend deploy owner must
check migration-number collisions against the current target branch and assess table size/write
load before V8: the bounded transactional index build is not a zero-downtime promise.
Confirm Flyway reaches version 8, then verify `/api/actuator/health` and the manual create/read/
edit/publish/delist cycle in the deployment's test environment using its normal authentication.

During that smoke cycle, inspect the existing traceId-correlated application logs for unexpected
500s or database lock timeouts. Expected 409 lifecycle rejections and 412 stale writes are not by
themselves rollback triggers. Unexpected buyer visibility, lost revisions, or deleted historical
rows are stop-release conditions. Traffic thresholds and a longer monitoring window are not
defined by this repository and must be agreed with the deploy owner rather than invented here.

An application rollback must not purge listing/history rows or rewrite applied Flyway migrations.
V8 adds only indexes, so leave it applied during an application rollback; any later index removal
needs a separate reviewed migration. Keep writes paused if invariant violations are suspected.

## Local verification record — 2026-09-11

Baseline: `origin/dev` at `e163558d886812f6ea3fbc61d0e662447042a1a9`, refreshed before isolated
verification. Java 21 on Windows with Docker Desktop / PostgreSQL 18; no WSL shell required.

- Full working-tree `mvnw.cmd -B -ntp verify`: **203 tests**, zero failures/errors/skips. This includes
  eight pre-existing local Stripe tests outside the UC9 change; that payment WIP was not modified.
- Independent `origin/dev` worktree with only the 29 UC9 changed/new files copied verbatim:
  **195 tests**, zero failures/errors/skips; full `verify` succeeded without payment WIP or QA files.
  The temporary worktree was removed after checking every copied file still matched the main tree.
- UC9 adds **32 tests**: 3 domain, 6 application and 23 PostgreSQL-backed acceptance tests. The
  missing-delete-version test first reproduced 500 instead of 400, then passed after the fix.
- Spotless, Checkstyle, SpotBugs, ArchUnit and migration/integration gates passed. Existing
  non-failing Checkstyle warnings remain; no thresholds or CI configuration were relaxed.
- OpenAPI regenerated twice from final source with identical SHA-256:
  `B5D47DA967264E9DDD4EEBBCDC907588FD78A650535F7927A73A8E045059B180`.
  The CI-pinned `openapi-diff` 2.1.7 reported backward compatibility. Deep comparison confirmed
  every old path and component schema unchanged; ECMAScript checks also verified all five new
  pattern constraints and the six scoped admin authentication declarations.
- Nine independent review lenses completed. Three validated findings were applied in two
  file-scoped fix batches: missing query error mapping, schema pattern fidelity and auth metadata.
  Both additional RBAC denial scenarios were tested; no accepted review findings remain unresolved.

This is local verification, not a remote CI/CD, deployed-service, browser/manual or real
Google/Stripe-provider acceptance result. No commit, push, PR, issue or kanban update was made.
