# Bounded best-offer query evidence

Date: 2026-09-27. Author: Eric Wang (@EricWang1358). Issue: #205. Baseline: dev `c30ed5a1658f5836c76342961937e217eeb14c37`; candidate: `codex/catalog-offer-bound`.

Catalog list, search and detail reads need one lowest-priced open deal per product. The previous batch query returned all matching deals and discarded losing rows after constructing domain objects. PostgreSQL now selects at most one row per distinct requested product with `DISTINCT ON (product_id)`, ordered by price and then ascending UUID. The UUID tie-break makes equal-price selection deterministic. Deleted, status and database-clock expiry predicates are unchanged, including the existing treatment of a full deal that has not yet transitioned. No HTTP contract, payment write, schema or index changes.

## Reproduce

Use an empty, disposable PostgreSQL 18 database. The fixture creates the `domain` schema and loads the committed V5 schema; never run it against a shared development or production database. From the repository root, run `psql "$DISPOSABLE_DATABASE_URL" -f docs/perf/fixtures/best-open-deals.sql` with a locally supplied connection string. The fixture uses synthetic UUIDs and no account, session or payment data.

The measured environment was PostgreSQL 18.6 on Linux x86_64 in local Docker Desktop on Windows, with the V5 product/status indexes, default database settings and `ANALYZE` after loading. The fixture contains 10,000 open, unexpired deals across 25 products, 400 deals per product and two equal-price candidates per price. Each query requests 20 products. Counts run first; the following plans are one warmed `EXPLAIN (ANALYZE, BUFFERS)` sample, not a throughput or latency baseline.

| Measurement | Previous query | Bounded query |
|---|---:|---:|
| Rows returned to the application | 8,000 | 20 |
| Matching rows scanned | 8,000 | 8,000 |
| Shared buffer hits | 172 | 172 |
| Database execution time, one warmed sample | 0.947 ms | 4.471 ms |
| Additional plan work | None | Incremental Sort + Unique |

Returned rows fall by 99.75%, bounding transfer and domain-object construction to the product page size. Database execution was slower in this sample because winner selection moved into SQL; both queries still scan all matching candidates. This evidence does not establish an end-to-end speedup, lower p95, or the 100-user requirement in #180. A composite index is deferred until a representative workload justifies its write/storage cost.

Captured output (trailing whitespace trimmed): [query plans](fixtures/best-open-deals-plan.txt). Executable fixture: [SQL](fixtures/best-open-deals.sql). The measured SQL uses the same selected columns, predicates and ordering as the mapper.

## Regression evidence

`BestOpenDealRepositoryIT` first failed all four scenarios against the old implementation, including 603 eligible rows returned where two winners were required. After the change, all four PostgreSQL tests and 14 application/repository unit tests passed. They cover lowest price, stable ties, multiple and duplicate product IDs, empty input, deleted/expired/closed/unrequested rows and preserved fullness eligibility. The parent verification run records the full suite in the daily log.

## After deployment

During the first catalog smoke check, compare products with no open deal, one open deal and equal-price open deals. Displayed lowest prices must agree with the database and the same tie must select the same deal. If prices or eligibility differ, revert the PR; do not change payment data. Eric owns this verification after deployment. No deployment or 100-user run was performed for this PR.
