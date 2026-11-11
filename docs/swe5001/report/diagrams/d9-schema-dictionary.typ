// Bounded supporting dictionary for D9; definitions are read from released V1-V28.
#let migration-root = "https://github.com/PPsyrius/iss-groupbuy-team8/blob/3a893036d70d028b29ae13ebf820283571c1ae00/backend/blocbuy-bootstrap/src/main/resources/db/migration/"
#let migrations = (
  "2": "payment", "3": "identity", "4": "catalog", "5": "groupbuy",
  "6": "catalog_team_price", "7": "groupbuy_paid_participation", "9": "stripe_checkout",
  "10": "payment_review_case", "11": "refund_execution", "12": "order",
  "13": "scheduler_lock", "14": "product_team_terms", "15": "purchase_intent",
  "17": "scheduled_item_retry", "18": "share_attribution", "19": "purchase_intent_sharer",
  "20": "product_team_terms_log", "22": "purchase_intent_expiry", "25": "checkout_expiry_cursor",
  "26": "transactional_outbox", "27": "notification", "28": "sys_consumed_message",
)
#let vs(..numbers) = numbers.pos().map(n => link(migration-root + "V" + n + "__" + migrations.at(n) + ".sql")[V#n]).join[, ]
#let dictionary(..rows) = table(
  columns: (1.6fr, 1.9fr, 1.3fr),
  table.header([Table / migrations], [Selected fields: SQL type], [Key / consistency rule]),
  ..rows.pos().flatten(),
)

#let d9-dictionary = [
  #figure(
    dictionary(
      ([`sys_user` \ #vs("3")], [`username? varchar(64)`; `email? varchar(512)`; `status varchar(16)`], [UUID `id` PK; active username/email unique (partial)]),
      ([`sys_user_oauth` \ #vs("3")], [`user_id uuid`; `provider varchar(32)`; `provider_user_id varchar(256)`; `raw_profile? jsonb`], [UUID `id` PK; active provider/subject unique]),
      ([`sys_role` \ #vs("3")], [`role_code varchar(64)`; `role_name varchar(64)`], [UUID `id` PK; active role code unique]),
      ([`sys_permission` \ #vs("3")], [`perm_code varchar(128)`; `perm_name varchar(64)`], [UUID `id` PK; active permission code unique]),
      ([`rel_user_role` \ #vs("3")], [`user_id uuid`; `role_id uuid`; `deleted boolean`], [UUID `id` PK; active pair unique; no update columns]),
      ([`rel_role_permission` \ #vs("3")], [`role_id uuid`; `permission_id uuid`; `deleted boolean`], [UUID `id` PK; active pair unique; no update columns]),
      ([`biz_notification` \ #vs("27")], [`event_id, user_id, reference_id uuid`; `notification_type varchar(32)`; `title varchar(128)`; `content varchar(1024)`], [UUID `id` PK; event/user UNIQUE includes deleted rows]),
      ([`sys_consumed_message` \ #vs("28")], [`event_id uuid`; `consumer varchar(64)`; `consumed_at timestamptz`], [Composite PK `(event_id, consumer)`; immutable ledger; no surrogate ID, actors, updates or soft delete]),
    ),
    kind: table,
    caption: [D9 supporting dictionary: identity and notification, including the reduced-column deduplication ledger. All rows trace to released migrations, not a live database inspection.],
  ) <tbl-schema-platform>

  #figure(
    dictionary(
      ([`biz_product` \ #vs("4", "14")], [`product_no varchar(64)`; `original_price numeric(18,2)`; `stock integer`; `team_target_size?, team_lifetime_seconds? integer`], [UUID `id` PK; active product number unique; domain validates terms/stock]),
      ([`biz_team_price_policy` \ #vs("6")], [`product_id uuid`; `amount numeric(18,2)`; `valid_from, valid_until timestamptz`; `version integer`], [UUID `id` PK; active product/version unique]),
      ([`biz_team_price_quote` \ #vs("6")], [`product_id, owner_id uuid`; `policy_version integer`; `amount numeric(18,2)`; `expires_at timestamptz`], [UUID `id` PK; application checks ownership/expiry]),
      ([`log_product_team_terms` \ #vs("20")], [`product_id uuid`; `product_version integer`; `old_target_size? integer`; `new_target_size integer`; `create_by uuid`], [UUID `id` PK; append-only business history, no update/soft-delete columns]),
      ([`biz_group_deal` \ #vs("5")], [`product_id, leader_user_id uuid`; `target_size, current_size integer`; `status varchar(16)`; `expire_time timestamptz`], [UUID `id` PK; active deal number unique; row locking arbitrates admission/expiry]),
      ([`biz_group_member` \ #vs("5", "7", "18")], [`deal_id, user_id uuid`; `payment_id?, sharer_user_id? uuid`; `status varchar(16)`], [UUID `id` PK; active deal/user unique; payment ID unique when non-null]),
      ([`biz_group_participation_receipt` \ #vs("7")], [`payment_id uuid`; `request_fingerprint varchar(64)`; `deal_id? uuid`; `outcome? varchar(32)`], [UUID `id` PK; payment ID UNIQUE (not partial)]),
      ([`biz_share_invite` \ #vs("18")], [`invite_ref varchar(32)`; `deal_id, sharer_user_id uuid`], [UUID `id` PK; active invite reference and deal/sharer pairs unique]),
      ([`biz_share_credit` \ #vs("18")], [`deal_id, payment_id uuid`; `sharer_user_id, invitee_user_id uuid`; `credit_time timestamptz`], [UUID `id` PK; active payment unique; application treats credit as immutable]),
    ),
    kind: table,
    caption: [D9 supporting dictionary: catalogue, team purchase and sharing (selected invariant-bearing fields).],
  ) <tbl-schema-commerce>

  #figure(
    dictionary(
      ([`biz_purchase_intent` \ #vs("15", "19", "22")], [`operation varchar(8)`; `user_id, deal_id, product_id uuid`; `amount numeric(18,2)`; `fulfilled_payment_id?, sharer_user_id? uuid`; `expired_at? timestamptz`], [UUID `id` PK; active fulfilled payment unique; START deal may not yet exist]),
      ([`biz_payment` \ #vs("2", "9")], [`user_id uuid`; `order_id?, intent_id? uuid`; `request_key? varchar(128)`; `amount numeric(18,2)`; `status varchar(16)`], [UUID `id` PK; conditional request-key/pending-intent/session uniqueness. V9 makes order ID nullable]),
      ([`biz_order` \ #vs("12")], [`payment_id, deal_id, user_id, product_id uuid`; `quantity integer`; `total_amount numeric(18,2)`; `status varchar(16)`], [UUID `id` PK; active payment and deal/user unique]),
      ([`biz_refund` \ #vs("9", "11")], [`payment_id, intent_id uuid`; `amount numeric(18,2)`; `status varchar(16)`; `provider_refund_id? varchar(256)`; `next_attempt_at timestamptz`], [UUID `id` PK; payment UNIQUE; non-null provider refund ID unique. Local work, not completed repayment]),
      ([`log_refund_attempt` \ #vs("11")], [`refund_id uuid`; `version integer`; `result varchar(64)`; `create_time timestamptz`], [UUID `id` PK; attempt history without actor/update/soft-delete columns]),
      ([`biz_payment_review_case` \ #vs("10")], [`case_id uuid`; `payment_id uuid`; `owner? varchar(64)`; `status varchar(16)`; `next_check_at timestamptz`], [`case_id` PK, not `id`; payment UNIQUE; reduced-column review record]),
      ([`log_payment_review` \ #vs("9")], [`payment_id uuid`; `reason, operator_id varchar(64)`; `create_time timestamptz`], [UUID `id` PK; append-only decision record; no update/soft delete]),
      ([`log_payment_webhook_event` \ #vs("2", "9")], [`event_id varchar(128)`; `event_type varchar(64)`; `payload? jsonb`; `payment_id? uuid`; `processing_result? varchar(32)`], [UUID `id` PK; provider event ID unique; no update/soft-delete columns]),
      ([`sys_scheduler_lock` \ #vs("13")], [`lock_key varchar(64)`; `owner varchar(128)`; `locked_until timestamptz`; `version integer`], [UUID `id` PK; active lock key unique; TTL/renewal managed by application]),
      ([`sys_scheduled_item_retry` \ #vs("17")], [`job_key varchar(64)`; `item_id uuid`; `failure_count integer`; `retry_after timestamptz`], [UUID `id` PK; active job/item unique]),
      ([`sys_checkout_expiry_cursor` \ #vs("25")], [`job_name varchar(64)`; `last_create_time? timestamptz`; `last_payment_id? uuid`], [UUID `id` PK; job name UNIQUE (not partial)]),
      ([`sys_outbox` \ #vs("26")], [`event_type varchar(64)`; `event_version integer`; `aggregate_id uuid`; `payload jsonb`; `trace_id varchar(64)`; `published_at?, failed_at? timestamptz`], [UUID `id` PK is event ID; unpublished retry index; reduced delivery-state columns, no actor/update/soft delete]),
    ),
    kind: table,
    caption: [D9 supporting dictionary: settlement and technical work. Uniqueness constrains local rows; external Stripe/SNS effects require separate protocols.],
  ) <tbl-schema-settlement>
]
