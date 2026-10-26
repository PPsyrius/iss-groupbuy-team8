<!-- report
rubric: C
owner: Liu Jin
plan: #681
sources: ../presentation/src/diagrams/full-erd.svg
status: draft
-->

# Appendix C Full ERD and Table List {.appendix}

This appendix completes section 3.6. Figures C.1 to C.5 show all 29 tables of the SWE5006 monolith, one figure per owning context or pair of contexts, and Table C.1 lists each table with its purpose, its owning context, the SWE5001 service that owns it, and the migration that created it. The conventions behind the schema (UUIDv7 keys, table prefixes, no foreign keys or CHECK constraints, soft delete, version columns) are explained in section 3.6 and are not repeated here.

The figures are UML class diagrams used as a data model, one «table» class per table, with the primary key (PK) and every logical foreign key (FK) column. Each FK column names the table it references after an arrow, whether or not a line is drawn; lines with multiplicities are drawn between tables that sit side by side. The deck's single-page crow's foot diagram of the same schema (`docs/swe5006/presentation/src/diagrams/full-erd.svg`) is too tall to print legibly on A4, so it is not reproduced here.

```mermaid
classDiagram
    class sys_user_oauth {
        <<table>>
        PK id
        FK user_id → sys_user
        provider_user_id
    }
    class sys_user {
        <<table>>
        PK id
        username
        email
        nickname
    }
    class rel_user_role {
        <<table>>
        PK id
        FK user_id → sys_user
        FK role_id → sys_role
    }
    class sys_role {
        <<table>>
        PK id
        role_code
    }
    class sys_permission {
        <<table>>
        PK id
        perm_code
    }
    class rel_role_permission {
        <<table>>
        PK id
        FK role_id → sys_role
        FK permission_id → sys_permission
    }
    %% at sys_user_oauth 0,0
    %% at sys_user 1,0
    %% at rel_user_role 2,0
    %% at sys_role 3,0
    %% at sys_permission 2,1
    %% at rel_role_permission 3,1
    sys_user "1" -- "0..*" sys_user_oauth
    sys_user "1" -- "0..*" rel_user_role
    sys_role "1" -- "0..*" rel_user_role
    sys_permission "1" -- "0..*" rel_role_permission
```

Figure C.1: Identity tables (SWE5001 owner identity-svc), UML class diagram used as a data model. Roles and permissions are many-to-many links through the two `rel_` tables, which are soft-deleted on revoke.

```mermaid
classDiagram
    class biz_team_price_policy {
        <<table>>
        PK id
        FK product_id → biz_product
        version
        amount
    }
    class biz_product {
        <<table>>
        PK id
        product_no
        status
        team_target_size
        stock
        version
    }
    class log_product_team_terms {
        <<table>>
        PK id
        FK product_id → biz_product
        product_version
    }
    class biz_team_price_quote {
        <<table>>
        PK id
        FK product_id → biz_product
        FK owner_id → sys_user
        policy_version
    }
    %% at biz_team_price_policy 0,0
    %% at biz_product 1,0
    %% at log_product_team_terms 2,0
    %% at biz_team_price_quote 0,1
    biz_product "1" -- "0..*" biz_team_price_policy
    biz_product "1" -- "0..*" log_product_team_terms
```

Figure C.2: Catalog tables (SWE5001 owner commerce-svc), UML class diagram used as a data model. A product's price is a series of revisions, and every administrator change to its team terms leaves an audit row.

```mermaid
classDiagram
    class biz_group_member {
        <<table>>
        PK id
        FK deal_id → biz_group_deal
        FK user_id → sys_user
        FK payment_id → biz_payment UQ
        FK order_id → biz_order
        FK sharer_user_id → sys_user
    }
    class biz_group_deal {
        <<table>>
        PK id
        FK product_id → biz_product
        FK leader_user_id → sys_user
        status
        expire_time
        version
    }
    class biz_share_invite {
        <<table>>
        PK id
        FK deal_id → biz_group_deal
        FK sharer_user_id → sys_user
        invite_ref UQ
    }
    class biz_group_participation_receipt {
        <<table>>
        PK id
        FK payment_id → biz_payment
        FK deal_id → biz_group_deal
        outcome
    }
    class biz_share_credit {
        <<table>>
        PK id
        FK deal_id → biz_group_deal
        FK payment_id → biz_payment UQ
        FK sharer_user_id → sys_user
        FK invitee_user_id → sys_user
    }
    %% at biz_group_member 0,0
    %% at biz_group_deal 1,0
    %% at biz_share_invite 2,0
    %% at biz_group_participation_receipt 0,1
    %% at biz_share_credit 1,1
    biz_group_deal "1" -- "1..*" biz_group_member
    biz_group_deal "1" -- "0..*" biz_share_invite
```

Figure C.3: Groupbuy tables, including share invites and referral credits (SWE5001 owner commerce-svc), UML class diagram used as a data model. A team links to its product, members and payments only through identifier columns, and a referral credit exists at most once per payment.

```mermaid
classDiagram
    class biz_purchase_intent {
        <<table>>
        PK id
        FK user_id → sys_user
        FK deal_id → biz_group_deal
        FK product_id → biz_product
        FK fulfilled_payment_id → biz_payment UQ
        FK sharer_user_id → sys_user
    }
    class biz_payment {
        <<table>>
        PK id
        FK intent_id → biz_purchase_intent
        FK user_id → sys_user
        FK business_id → biz_group_deal
        FK order_id → biz_order
        status
        version
    }
    class biz_refund {
        <<table>>
        PK id
        FK payment_id → biz_payment UQ
        FK intent_id → biz_purchase_intent
        FK deal_id → biz_group_deal
        status
        version
    }
    class biz_order {
        <<table>>
        PK id
        FK payment_id → biz_payment UQ
        FK deal_id → biz_group_deal
        FK user_id → sys_user
        FK product_id → biz_product
    }
    class log_payment_webhook_event {
        <<table>>
        PK id
        event_id UQ
        FK payment_id → biz_payment
    }
    class log_refund_attempt {
        <<table>>
        PK id
        FK refund_id → biz_refund
        version
        result
    }
    class log_payment_review {
        <<table>>
        PK id
        FK payment_id → biz_payment
        operator_id
    }
    class biz_payment_review_case {
        <<table>>
        PK case_id
        FK payment_id → biz_payment UQ
        status
    }
    class sys_checkout_expiry_cursor {
        <<table>>
        PK id
        FK last_payment_id → biz_payment
    }
    %% at biz_purchase_intent 0,0
    %% at biz_payment 1,0
    %% at biz_refund 2,0
    %% at biz_order 0,1
    %% at log_payment_webhook_event 1,1
    %% at log_refund_attempt 2,1
    %% at log_payment_review 0,2
    %% at biz_payment_review_case 1,2
    %% at sys_checkout_expiry_cursor 2,2
    biz_purchase_intent "1" -- "0..*" biz_payment
    biz_payment "1" -- "0..1" biz_refund
```

Figure C.4: Order (purchase intents and orders) and payment tables (SWE5001 owner commerce-svc), UML class diagram used as a data model. Every payment-side table points back to `biz_payment`, and UQ marks the columns that allow at most one row per payment.

```mermaid
classDiagram
    class biz_notification {
        <<table>>
        PK id
        event_id
        FK user_id → sys_user
        reference_id
    }
    class sys_consumed_message {
        <<table>>
        PK event_id, consumer
    }
    class sys_outbox {
        <<table>>
        PK id
        aggregate_id
        event_type
    }
    class sys_scheduler_lock {
        <<table>>
        PK id
        lock_key UQ
        locked_until
        version
    }
    class sys_scheduled_item_retry {
        <<table>>
        PK id
        job_key
        item_id
    }
    %% at biz_notification 0,0
    %% at sys_consumed_message 1,0
    %% at sys_outbox 0,1
    %% at sys_scheduler_lock 1,1
    %% at sys_scheduled_item_retry 2,1
```

Figure C.5: Notification tables (top row, SWE5001 owner notification-svc) and the shared job and outbox tables (commerce-svc), UML class diagram used as a data model. These tables reference business rows only by opaque identifiers, so no logical foreign key is drawn.

Table C.1: All 29 tables, by owning context. "SWE5001 owner" is the service whose database role may write the table on the SWE5001 environment (backend ADR 0008); on the SWE5006 `dev` host all tables live in one database.

| Table | Purpose | Context | SWE5001 owner | Created in |
| --- | --- | --- | --- | --- |
| `sys_user` | User accounts: login identity and profile | identity | identity-svc | V3 |
| `sys_user_oauth` | Links a user to a Google account (one row per provider account) | identity | identity-svc | V3 |
| `sys_role` | RBAC roles, loaded into Sa-Token at sign-in | identity | identity-svc | V3 |
| `sys_permission` | RBAC permission points, such as reading payments in the admin console | identity | identity-svc | V3 |
| `rel_user_role` | Which user has which role; revoked rows are soft-deleted | identity | identity-svc | V3 |
| `rel_role_permission` | Which role grants which permission; revoked rows are soft-deleted | identity | identity-svc | V3 |
| `biz_product` | Sellable products (listings) with their team terms: target size and team lifetime | catalog | commerce-svc | V4 (team terms V14) |
| `biz_team_price_policy` | Append-only price revisions for a product's first team; the latest version is the current price | catalog | commerce-svc | V6 |
| `biz_team_price_quote` | Immutable price quotations shown to buyers | catalog | commerce-svc | V6 |
| `log_product_team_terms` | Audit of every administrator change to a product's team terms | catalog | commerce-svc | V20 |
| `biz_group_deal` | Teams (blocs): product, leader, target and current size, status and deadline | groupbuy | commerce-svc | V5 |
| `biz_group_member` | Members of a team, one active row per buyer per team, linked to the payment and order | groupbuy | commerce-svc | V5 (payment link V7) |
| `biz_group_participation_receipt` | Immutable result of applying one payment to a team, kept even if the team is deleted; marks payments that need compensation | groupbuy | commerce-svc | V7 |
| `biz_share_invite` | Invite references a member shares, one active invite per sharer per team | groupbuy | commerce-svc | V18 |
| `biz_share_credit` | Immutable referral credits, one per paid participation that came through an invite | groupbuy | commerce-svc | V18 |
| `biz_purchase_intent` | Server-owned price and terms for one START or JOIN checkout attempt; Stripe Checkout is built only from this row | order | commerce-svc | V15 |
| `biz_order` | Buyer orders; the database, not Redis, is the source of truth for order state | order | commerce-svc | V12 |
| `biz_payment` | Payment attempts and their Stripe Checkout sessions; provider state is mirrored here | payment | commerce-svc | V2 (Checkout columns V9) |
| `log_payment_webhook_event` | Every Stripe webhook event received, inserted before processing so a replay is ignored | payment | commerce-svc | V2 |
| `biz_refund` | Refund obligations for expired teams or provider-mandated refunds, with lease and retry state | payment | commerce-svc | V9 (execution columns V11) |
| `log_refund_attempt` | Immutable audit of each refund attempt; holds no customer or card details | payment | commerce-svc | V11 |
| `log_payment_review` | Audit of operator decisions on payments under review | payment | commerce-svc | V9 |
| `biz_payment_review_case` | Work queue of payments that need human inspection | payment | commerce-svc | V10 |
| `sys_checkout_expiry_cursor` | Saved position of the operator-run sweep that expires stale Checkout sessions | payment | commerce-svc | V25 |
| `biz_notification` | Immutable in-app notifications (off on `dev`) | notification | notification-svc | V27 |
| `sys_consumed_message` | Integration events already applied by a consumer, so a duplicate is ignored | notification | notification-svc | V28 |
| `sys_scheduler_lock` | Lease rows that let only one instance run each scheduled job | common (jobs) | commerce-svc | V13 |
| `sys_scheduled_item_retry` | Back-off state for items that failed inside a scheduled job | common (jobs) | commerce-svc | V17 |
| `sys_outbox` | Transactional queue of integration events waiting for the relay (off on `dev`) | common (integration) | commerce-svc | V26 |

The remaining migrations create no table: V1 is a placeholder that states the naming rules, V8, V16 and V23 add or narrow indexes, V14, V19 and V22 add columns (product team terms, the purchase intent's sharer and its expiry), V21 adds the product-number sequence, and V24 grants the admin payment-read permission.
