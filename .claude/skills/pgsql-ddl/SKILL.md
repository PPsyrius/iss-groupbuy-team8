---
name: pgsql-ddl
description: Generate or review PostgreSQL table DDL, indexes, comments, and schema migrations for this team's web applications. Apply the team's UUIDv7, varchar sizing, table-prefix, audit-column, soft-delete, and application-managed consistency conventions. Never emit foreign keys or CHECK constraints.
---

# PostgreSQL DDL

Generate production-oriented PostgreSQL DDL that follows this team's conventions. Inspect the repository's PostgreSQL version, migration tool, and established naming before editing files. State any material assumption that cannot be discovered.

## Non-negotiable rules

- Never create `FOREIGN KEY`, `REFERENCES`, or `CHECK` constraints. If a request requires one, flag the conflict and design the application-layer validation instead.
- Keep mutable business rules out of database constraints. Represent statuses and categories with `varchar(n)` and validate allowed values in application code.
- Use native `uuid` UUIDv7 primary keys. Use `DEFAULT uuidv7()` only when PostgreSQL 18+ is confirmed; otherwise generate UUIDv7 in the application. Never store UUIDs as strings and never silently fall back to UUIDv4.
- Use lowercase `snake_case`, unquoted identifiers, explicit object names, and the table-type prefixes defined in [references/conventions.md](references/conventions.md).
- Use `varchar(n)` for bounded strings. Select `n` from the team's power-of-two size tiers and choose the smallest tier that safely covers the business limit. Use `text` only for genuinely unbounded content. Do not use `char(n)`.
- Every ordinary business table includes `create_time`, `update_time`, `create_by`, `update_by`, and `deleted`. Use `timestamptz` for timestamps and `boolean NOT NULL DEFAULT false` for `deleted`.
- Maintain `update_time`, actor fields, reference validity, tenant ownership, and deletion rules in the application. Database defaults do not update `update_time` automatically.
- Use only structural constraints by default: `PRIMARY KEY`, `UNIQUE`, and `NOT NULL`. Avoid exclusion constraints, domains containing constraints, generated business-rule columns, and business-validation triggers unless the user explicitly supplies a separate team standard for them.

## Generation workflow

1. Determine whether the request is a new schema or a migration of populated tables. Preserve the repository's migration format.
2. Model tables and columns using the canonical order and type rules in [references/conventions.md](references/conventions.md).
3. Add query-driven indexes. Index reference-ID columns used for lookup or application-side consistency checks; include `tenant_id` where tenant isolation requires it. Use partial indexes with `WHERE deleted = false` for active-row access and uniqueness.
4. Add table and non-obvious column comments, including units and identifier semantics.
5. For altered production tables, minimize lock duration and table rewrites. Separate `CREATE INDEX CONCURRENTLY` from transactional migrations.
6. Return the SQL plus a short **Application obligations** list covering validations or concurrency rules the database intentionally does not enforce.

## Output checks

Before finishing, verify that:

- no foreign key, `REFERENCES`, or `CHECK` appears in executable SQL;
- every table and column follows the naming and type conventions;
- UUIDv7 generation matches the confirmed PostgreSQL version;
- active-row uniqueness includes `tenant_id` when applicable and excludes logically deleted rows;
- reference-ID indexes support expected lookups and parent-deletion checks without duplicating another index prefix;
- monetary values use `numeric`, timestamps use `timestamptz`, and UUIDs use `uuid`;
- the output identifies required application validation, locking, deletion ordering, and orphan reconciliation;
- destructive or blocking migrations are not presented as safe without an explicit rollout plan.

Use [references/template.sql](references/template.sql) when a concrete starting point is useful; adapt it rather than copying unused columns.
