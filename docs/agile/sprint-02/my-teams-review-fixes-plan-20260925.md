# My Teams (#182) review fixes — plan

Date: 2026-09-25 · PR: [#182](https://github.com/PPsyrius/iss-groupbuy-team8/pull/182) (`feature/view-my-teams-20260924`, issue #179, UC8) · Reviewer: @PPsyrius · Status: **C3/C4 implemented on the #182 branch itself**

> **Implementation notes (2026-09-25).** The fix landed in #182 rather than in a follow-up PR, because the PR stays well under 50 files. It departs from §4 in these ways:
>
> - `PageResult` lives in the framework-free domain module and cannot carry `@Schema`. The paging semantics went into the `/group-deals/me` `@Operation` description instead, a description-only contract diff.
> - The C3 regression test corrupts `images` to a JSON object and sets `status` and `pricing_mode` to unknown codes on one live row, and asserts that its title and cover still come back.
> - The local branch had an unpushed duplicate `dev` merge (`29ed922`), which was dropped by resetting to the PR head `64edc62`; the remote already held all of its content.

## 1. Verdict per comment

| # | Where | Comment | Real problem? | Blocking? | Action |
| --- | --- | --- | --- | --- | --- |
| C1 | `GroupDealRepositoryImplTest:119` | Sonar `java:S5778`: the lambda passed to `assertThatThrownBy` makes two calls that can throw (`membershipRow` and `toMembership`), so the test could pass on the wrong exception | Yes, a test smell. The assertion would still pass if `membershipRow` itself threw | Review "last fixes" | **Already fixed.** PPsyrius applied both suggestions in `64edc62` on the PR branch. Only a pull is needed (§2) |
| C2 | `GroupDealRepositoryImplTest:121` | Same rule, second assertion | Same | Same | Same commit |
| C3 | `BizProductMapper.xml:87` (`selectActiveByIds`) | Select only `id`, `title`, `cover_image`. Loading full products lets malformed unrelated fields break My Teams | **Yes** (§3.1) | No ("non-blocking, could be its own PR") | Narrow projection, follow-up PR |
| C4 | `GroupDealRepositoryImpl:57` (`pageMembershipsByUserId`) | Rows and count are two queries; under `READ COMMITTED` they can see different snapshots, so `total` can disagree with `items` | **Yes** (§3.2), low impact | No | One snapshot through a read-only `REPEATABLE READ` transaction, plus doc, follow-up PR |

Recommendation: merge #182 once the local branch has `64edc62`. Handle C3 and C4 in one small follow-up PR, as the reviewer suggested.

## 2. C1/C2 — already fixed upstream

The local branch is 3 commits behind the PR head (`29ed922` locally, `64edc62` on the remote). Steps:

1. `git pull --ff-only` on `feature/view-my-teams-20260924`. This is a fast-forward and needs no merge.
2. Reply on both threads with something like "applied in 64edc62, thanks", then resolve them.
3. Run nothing extra. That commit changes only a test file, and CI and Sonar have already analyzed it: the PR's Sonar comment reports the quality gate passed. Check the Sonar PR page after the pull to confirm that `S5778` is no longer among the new issues.

## 3. C3 and C4 — analysis

### 3.1 C3: My Teams depends on every product row being fully valid

The path is `GroupbuyListingDisplayAdapter.findByProductIds`, then `ProductRepository.findByIds`, then `selectActiveByIds`, which selects all 20 columns. `ProductConverter.toDomain` then rebuilds the full `Product` aggregate. That rebuild **throws** in any of these cases:

- `images` holds JSON that is not a string array, for example `{"a":1}` in the `jsonb` column: `IllegalStateException("corrupt biz_product.images payload")`;
- `status` or `pricing_mode` holds an unknown code (both are `varchar` with no CHECK, by design): `IllegalArgumentException` from `fromCode`;
- `title` is blank, `original_price` is negative or null, or `currency` is blank: `IllegalArgumentException` from `Product`'s constructor.

**Failure scenario.** One such row — from a seed, hand-written SQL or a future migration — means every buyer with a team on that product gets a **500 for their whole My Teams page**, not just a missing title. My Teams needs only two display strings, yet it inherits the invariants of the whole aggregate.

It also reads `description` and `images` for nothing.

**Why it is not blocking.** Every normal write goes through the aggregate, so such rows should not exist today.

### 3.2 C4: `total` can disagree with `items`

`pageMembershipsByUserId` runs `selectMembershipPageByUserId`, then `countMembershipsByUserId`. `GroupDealAppServiceImpl.pageMyTeams` is `@Transactional(readOnly = true)` at the default `READ COMMITTED` level. PostgreSQL takes a **new snapshot per statement** at that level, even inside one transaction, so a join or new team committed between the two statements is counted but not listed.

**Impact.**

- `total` can be one or more higher than the rows it describes. `PageResult.hasNext()` (`page * size < total`) can then report a next page that turns out empty.
- A (rarely) lower total is possible only if a membership is soft-deleted; nothing deletes memberships today.
- UI effect: a wrong count badge or one extra empty page. No data leaks, and no money is involved.

**What a single snapshot does *not* fix.** Offset pagination over `join_time DESC` still shifts **across** requests. A buyer who joins a team between loading page 1 and page 2 sees one row repeated on page 2. Only keyset (cursor) paging fixes that; `PageResult.nextCursor` exists for it. That is out of scope and is documented in §4.2 instead.

**The same pattern exists elsewhere.** `OrderAppServiceImpl.pageMyOrders` (My Orders) has the same page-plus-count shape. See §6.

## 4. Fix design (follow-up PR)

Branch: `fix/my-teams-review-followups-20260925`, cut from `dev` after #182 merges. It lives in the catalog and groupbuy domains; no API or contract change.

### 4.1 C3: a narrow display projection

This follows the existing projection precedent, `ProductRepository.findTeamTerms` returning `ProductTeamTerms`: a repository port may return a read-only value of its aggregate.

| Layer | Change |
| --- | --- |
| domain `catalog.model` | New `record ProductDisplay(UUID productId, String title, String coverImage)`. It is a plain value with only a null check on the id: no aggregate invariants, and nothing to fail on the fields My Teams does not read |
| domain `catalog.repository` | **Replace** `List<Product> findByIds(Collection<UUID>)` with `List<ProductDisplay> findDisplaysByIds(Collection<UUID>)`. The adapter is its only caller (checked on the PR head); `NotificationRepository` and `PaymentRepository` have their own, unrelated `findByIds`. Same contract: active rows only, any status, unknown ids absent, empty input gives an empty list, one batch query |
| infrastructure | `BizProductMapper.selectDisplaysByIds` returns `SELECT id, title, cover_image FROM biz_product WHERE deleted = false AND id IN (…)` into a small `BizProductDisplayDO`, the way `BizProductTeamTermsDO` does. Delete `selectActiveByIds`. `ProductRepositoryImpl` keeps the empty-`IN ()` guard and maps rows by hand |
| application | `GroupbuyListingDisplayAdapter` maps `ProductDisplay` to `ListingDisplay`. Its behaviour is unchanged: deleted or unknown products stay absent, and My Teams shows a null title for them |
| docs | Update the `ProductRepository` Javadoc and the §19 "Catalog enrichment" line in `backend-skeleton-plan.md` |

Tests:

- `ProductRepositoryImplTest`: projection mapping, and the empty-input guard with no mapper call.
- `ProductSearchRepositoryIT` (real PostgreSQL), a regression test for C3: insert one product whose `images` is `'{"not":"an array"}'` and whose `status` is `'weird'`. `findDisplaysByIds` still returns its title and cover. Also assert that a soft-deleted product is absent.
- `GroupbuyListingDisplayAdapterTest`: switch to the new port method.
- Coverage: 100% on touched files, checked with the JaCoCo aggregate script in `backend-constraints-skill`.

**Alternative rejected:** catching the conversion error per row in the adapter. It hides corrupt data and still over-fetches.

### 4.2 C4: one snapshot per response

On `GroupDealAppServiceImpl.pageMyTeams`:

```java
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 5)
```

**Why this option.**

- Under `REPEATABLE READ`, PostgreSQL takes one snapshot at the first statement and every later statement in the transaction uses it. The rows query, the count and the product-title lookup therefore all read the same data.
- A read-only transaction at this level can never raise a serialization failure, so it needs no retry loop.
- The same pattern already exists at `PaymentBrowserService.receipt` (`readOnly = true, isolation = REPEATABLE_READ, timeout = 5`).
- The product lookup joins this transaction: `GroupbuyListingDisplayAdapter`'s `@Transactional(readOnly = true)` uses `REQUIRED`, so it adopts the outer transaction's isolation.

**Cost.** Nothing measurable: the same two queries plus one product query, inside one short transaction.

**Alternatives considered.**

| Option | Why not chosen |
| --- | --- |
| `COUNT(*) OVER ()` in the page query | Also one snapshot, but a page past the end returns no rows and so no total. That needs a second count query, which reintroduces the race in that case and changes the DO and mapper |
| Document the total as best-effort only | Leaves a known inconsistency that one annotation removes |
| Keyset/cursor paging | Fixes cross-page shifting too, but it is a contract change (`cursor` parameter, `nextCursor`) and belongs with the frontend; recorded as a follow-up |

**Docs.**

- Javadoc on `pageMyTeams` and `pageMembershipsByUserId`: `total` and `items` come from one snapshot. Offset pages can still repeat or skip a row if the buyer joins a team between requests.
- In `GroupDealController`, add `@Schema(description = …)` on the response so the frontend knows `total` is exact per response and pages are offset-based. This is a description-only contract diff, so regenerate `contracts/openapi.json` and let `openapi-diff` confirm it is non-breaking.

**Tests.**

- `GroupDealAppServiceImplTest`: a reflection assertion that `pageMyTeams` is annotated read-only with `REPEATABLE_READ`, so that a later edit cannot silently drop it.
- The PostgreSQL snapshot behaviour itself is platform semantics. It is not re-proven by a flaky two-connection race test.
- Existing Testcontainers tests for paging and ordering must stay green.

## 5. Evidence and records

- Add one sprint-log entry to `docs/agile/sprint-02/log/2026-09-25.md` for the follow-up PR (Kind: fix; Verified with `./mvnw -B verify` counts).
- **NFR-34** (My Teams) row in `docs/quality/nfr-register.md`: add "one snapshot per page; display read limited to title and cover" to its Enforced/Evidence columns. No new NFR ID.
- No ADR is needed. The first fix follows existing precedent (the projection method on the repository) and so does the second (the RR read-only transaction). The PR body cites both.
- Link #182 and #179 from the PR body. Reply on the C3/C4 threads with the follow-up PR link, then resolve them.

## 6. Out of scope, for decision

| Item | Recommendation | Owner |
| --- | --- | --- |
| `OrderAppServiceImpl.pageMyOrders` has the same page+count race | Apply the same one-line `REPEATABLE_READ` fix in the follow-up PR for consistency, **or** leave it to a separate issue. Ask the reviewer | UC8 owner |
| Offset paging repeats or skips rows across pages | Keyset paging over `(join_time, id)` with `nextCursor`; contract change, pair with frontend T3-03 | backlog |
| CodeRabbit "docstring coverage 43.84%" | Advisory, not a repository gate (Sonar and Checkstyle pass). Ignore unless the team adopts it | none |

## 7. Order of work

1. Pull `64edc62`, reply to and resolve C1/C2, and get #182 approved and merged.
2. Rebase the UC7 branch (`feature/shared-attribution-backend-20260924`) onto the new `dev`. `GroupDealController` conflicts additively: keep both `myTeams` and `createInvite`. Renumber UC7's NFR row from NFR-35 to **NFR-36**: `dev` has already taken NFR-35 (agile-evidence integrity, #188), and #182 holds NFR-34. Update the UC7 sprint-log line to match. Migrations V18 and V19 are still free on `dev` (latest is V17); re-check just before merge.
3. Open the follow-up PR with §4.1 and §4.2. Verify with `./mvnw -B verify`, the JaCoCo aggregate check and contract regeneration.
