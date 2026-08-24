# Team PostgreSQL DDL conventions

Read this reference when creating or reviewing PostgreSQL tables, indexes, or migrations.

## 1. Object naming

Use lowercase `snake_case` without quoted identifiers. Use singular table names consistently within a project.

| Prefix | Table type | Example |
| --- | --- | --- |
| `biz_` | Core business and transaction data | `biz_order` |
| `sys_` | Users, permissions, menus, jobs, and system settings | `sys_user` |
| `rel_` | Many-to-many relationships | `rel_user_role` |
| `dict_` | Dictionaries and configurable classifications | `dict_order_status` |
| `log_` | Operational and access logs | `log_operation` |
| `audit_` | Immutable audit records | `audit_order_change` |
| `his_` | Historical snapshots and archives | `his_order` |
| `stg_` | Import and ETL staging data | `stg_customer_import` |

Use schemas for access boundaries when the project already does so. Do not duplicate a schema category in the table prefix unless the repository convention explicitly requires both.

Name objects explicitly:

- Primary key: `pk_<table>`
- Unique constraint or index: `uq_<table>_<key>`
- Ordinary index: `idx_<table>_<key>`
- Sequence, when unavoidable: `seq_<table>_<column>`

Do not create names beginning with `pg_`. Avoid reserved or ambiguous names such as `user`, `order`, `group`, `type`, `value`, `data`, `info`, `flag`, and `field1`.

## 2. Canonical column order

Use this order when the categories exist:

1. `id`
2. `tenant_id`
3. business identifiers such as `order_no`
4. reference IDs such as `customer_id`
5. core business attributes
6. status, amount, and descriptive fields
7. `create_time`, `create_by`, `update_time`, `update_by`
8. `deleted`, then optional `delete_time`, `delete_by`
9. optional optimistic-lock `version`

Do not add optional columns mechanically. `tenant_id`, deletion metadata, and `version` must correspond to an actual application capability.

## 3. Type rules

### Identifiers

- Default primary key: native `uuid` containing UUIDv7.
- PostgreSQL 18+: `id uuid NOT NULL DEFAULT uuidv7()`.
- PostgreSQL 17 and earlier: application-generated UUIDv7; omit the database default unless the repository already has an approved generator.
- All reference columns use the exact same type as the referenced ID.
- Business-facing numbers use `<entity>_no`; relational identifiers use `<entity>_id`.

UUIDv7 is approximately time ordered, not a strict business sequence and not an authorization secret. Keep an explicit `create_time`.

### Strings

Use `varchar(n)` with a length selected from `2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, ...`. The tier must cover the actual domain; it is a consistency convention, not a PostgreSQL storage optimization.

Typical starting tiers:

| Meaning | Typical type |
| --- | --- |
| Small code or discriminator | `varchar(16)` or `varchar(32)` |
| Name or short label | `varchar(64)` or `varchar(128)` |
| Business number | `varchar(64)` |
| Email | `varchar(512)` |
| URL or address | `varchar(1024)` |
| Remark | `varchar(1024)` or `text` |
| Long-form content | `text` |

Use the protocol's real maximum if it is itself a power of two; otherwise choose the next safe tier. Never rely on database truncation. Do not use `char(n)` for fixed-looking codes.

### Other values

- Timestamp representing an instant: `timestamptz`.
- Calendar-only date: `date`.
- Money: `numeric(precision, scale)`, normally `numeric(18, 2)` unless the domain says otherwise. Never use floating point for money.
- Boolean: `boolean`, named positively where practical (`enabled`, `visible`, `deleted`).
- Flexible extension data: `jsonb`, only when its members are not core keys, filters, relationships, or invariants.
- IP address and network: `inet` or `cidr`, not strings.
- Binary content: `bytea` for small database-owned values; use object storage for large files and store a bounded locator.

Do not use PostgreSQL enums for frequently changing business statuses. Do not hide important typed fields in `jsonb`.

## 4. Standard columns

Ordinary mutable business tables include:

```sql
create_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
create_by   uuid,
update_time timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
update_by   uuid,
deleted     boolean NOT NULL DEFAULT false
```

- The application sets `update_time` on every meaningful update; the default only initializes it.
- The application supplies actor IDs. Make actor columns `NOT NULL` only when every interactive, batch, import, and system write has a defined actor.
- Add `delete_time timestamptz` and `delete_by uuid` when deletion provenance matters.
- Add `version integer NOT NULL DEFAULT 0` for application-level optimistic locking where concurrent edits are expected.
- Immutable logs, pure relationship tables, and staging tables may use an explicitly documented reduced set.

## 5. Allowed integrity mechanisms

Use:

- named primary keys;
- `NOT NULL` where absence is structurally invalid;
- unique constraints or unique indexes for stable identity and idempotency;
- defaults that initialize values without encoding mutable business policy.

Do not use:

- foreign keys or the inline `REFERENCES` form;
- `CHECK` constraints, including status, amount, deletion-state, format, and version checks;
- domains that conceal `CHECK` constraints;
- database enums for mutable business choices;
- triggers to reproduce application business validation;
- `ON DELETE` or `ON UPDATE` referential actions, because there is no foreign key.

## 6. Application-managed consistency

Every logical relationship must have an owning service and documented behavior for create, update, soft delete, restore, and physical purge.

For a child write, the application must validate in the same logical operation that the parent:

- exists;
- is active and allowed to receive the relation;
- belongs to the same tenant;
- is in a business state that permits the operation.

A read-then-write check alone has a concurrency race. When parent deletion or state changes can race with child creation, use one of these deliberate protocols:

- serialize both paths with a shared application/distributed lock;
- when both tables are in one PostgreSQL transaction, lock the parent row with `SELECT ... FOR UPDATE` in both the child-write and parent-delete/state-change paths;
- use an application-owned aggregate version and optimistic compare-and-swap with bounded retries;
- accept eventual consistency only when the domain permits it, then use an outbox/event flow plus compensating repair.

All writers—including admin tools, imports, backfills, and maintenance scripts—must follow the same protocol. Add scheduled orphan and cross-tenant audits for important relationships. Define alerting, repair ownership, and whether the repair deletes, quarantines, or reconnects invalid rows.

Physical deletion is application-orchestrated. Delete children before parents or explicitly preserve them as history. Never assume ORM cascade is equivalent to database-enforced consistency.

## 7. Index rules

Design indexes from actual filters, joins, ordering, uniqueness, and consistency checks.

- Index frequently queried reference IDs even though they are not foreign keys.
- For multi-tenant access, normally lead with `tenant_id` when queries always scope by tenant.
- For logical deletion, use `WHERE deleted = false` on active-row indexes where it matches the query workload.
- Active-row business uniqueness normally uses a partial unique index:

```sql
CREATE UNIQUE INDEX uq_biz_order_tenant_order_no
    ON biz_order (tenant_id, order_no)
    WHERE deleted = false;
```

- Do not add a single-column index when an existing composite index has the same useful leading columns.
- Use expression indexes only when queries use the same stable expression.
- Use `INCLUDE` sparingly for demonstrated covering-index needs.
- Verify important indexes with representative `EXPLAIN (ANALYZE, BUFFERS)` output.

## 8. Migration safety

- Keep migrations small and versioned. Avoid broad `CASCADE` operations.
- Do not use `IF EXISTS` or `IF NOT EXISTS` to hide unexpected schema drift.
- Before altering populated tables, assess lock level, rewrite risk, table size, long transactions, replication lag, and disk headroom.
- Adding a nullable column is usually the safest expansion step. Backfill large tables in bounded batches before tightening `NOT NULL`.
- Adding a non-volatile default can be metadata-only on supported PostgreSQL versions; volatile defaults can rewrite a populated table.
- Create indexes on large live tables with `CREATE INDEX CONCURRENTLY`; it cannot run inside a transaction block and failure can leave an invalid index.
- Use expand-migrate-contract for column replacements and destructive changes. Do not drop the old structure until all deployed writers and readers have migrated.
- Set bounded `lock_timeout` in the deployment process so DDL fails instead of waiting indefinitely behind production traffic.

## 9. Required output notes

Alongside generated SQL, identify:

- the confirmed or assumed PostgreSQL version;
- validations performed by the application;
- relationship columns and their lookup indexes;
- concurrency protocol for reference creation versus deletion;
- tenant-isolation behavior;
- logical-delete and physical-purge order;
- orphan detection and repair expectations;
- any non-transactional or high-lock migration step.
