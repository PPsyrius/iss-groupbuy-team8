// D9: logical data model per database (UML class notation for data: «table», key columns,
// multiplicities). Relationships are dashed: they are logical only, because the team's
// convention forbids foreign keys (pgsql-ddl skill). Source of the 3.2.3 figures.
// Column lists show keys and the columns the text refers to, not every column.
#import "style.typ": *

#let tbl(name, ..cols, colour: grey) = align(left, {
  set text(size: 6.8pt)
  block(inset: (x: 2pt, y: 1pt), below: 2pt, text(weight: "bold", size: 7.5pt, fill: colour, raw(name)))
  line(length: 100%, stroke: 0.4pt + grey)
  for c in cols.pos() { [#c \ ] }
})
#let pk(c) = [#text(fill: business, size: 6pt)[PK] #raw(c)]
#let uk(c) = [#text(fill: platform, size: 6pt)[UK] #raw(c)]
#let ref(c) = [#text(fill: grey, size: 6pt)[→] #raw(c)]
#let col(c) = [#h(9pt)#raw(c)]
#let ent(pos, name, ..cols, colour: grey, fill: white, id: none) = node(pos, tbl(name, ..cols, colour: colour),
  fill: fill, stroke: 0.6pt + colour, inset: 5pt, corner-radius: 2pt, shape: rect, name: label(if id == none { name } else { id }))

// UML association with one multiplicity at each end: `ends: (near first node, near last node)`.
#let assoc(..args, ends: ("", ""), stroke: (dash: "dashed")) = {
  let m(t, pos) = edge(..args, "-", stroke: stroke, label: text(size: 6.3pt, fill: grey, t), label-pos: pos, label-sep: 2pt)
  m(ends.at(0), 0.13)
  m(ends.at(1), 0.87)
}

#let d9-identity = {
  set text(font: ("Arial", "Helvetica"), fill: ink)
  diagram(spacing: (12mm, 9mm),
    ent((0, 0), "sys_user", pk("id"), uk("username"), uk("email"), col("nickname"), col("status"), col("deleted"), colour: platform),
    ent((2, 0), "sys_user_oauth", pk("id"), ref("user_id"), uk("(provider, provider_user_id)"), col("raw_profile"), colour: platform),
    ent((0, 1.3), "rel_user_role", pk("id"), uk("(user_id, role_id)"), colour: platform),
    ent((1, 2.6), "sys_role", pk("id"), uk("role_code"), colour: platform),
    ent((2, 1.3), "rel_role_permission", pk("id"), uk("(role_id, permission_id)"), colour: platform),
    ent((2, 3.2), "sys_permission", pk("id"), uk("perm_code"), colour: platform),
    assoc(<sys_user>, <sys_user_oauth>, ends: ([1], [0..\*])),
    assoc(<sys_user>, <rel_user_role>, ends: ([1], [0..\*])),
    assoc(<rel_user_role>, <sys_role>, ends: ([0..\*], [1])),
    assoc(<sys_role>, <rel_role_permission>, ends: ([1], [0..\*])),
    assoc(<rel_role_permission>, <sys_permission>, ends: ([0..\*], [1])),
  )
}

#let d9-notification = {
  set text(font: ("Arial", "Helvetica"), fill: ink)
  diagram(spacing: (14mm, 9mm),
    ent((0, 0), "biz_notification", pk("id"), col("event_id"), col("user_id"), col("reference_id"), col("notification_type"), col("create_time"), colour: platform),
    ent((2, 0), "sys_consumed_message", pk("(event_id, consumer)"), col("consumed_at"), colour: grey),
    edge(<biz_notification>, <sys_consumed_message>, "-", stroke: (dash: "dashed"), label: text(size: 6.3pt, fill: grey)[same `event_id`]),
  )
}

// Commerce owns 21 tables, so its model is split in two figures; `payment_id`, `deal_id` and
// `product_id` columns that point into the other figure are shown as references (→) only.
#let d9-commerce-team = {
  set text(font: ("Arial", "Helvetica"), fill: ink)
  diagram(spacing: (9mm, 11mm),
    ent((0, 0), "log_product_team_terms", pk("id"), ref("product_id"), col("product_version"), col("new_target_size"), col("new_lifetime_seconds"), colour: grey),
    ent((1, 0), "biz_product", pk("id"), uk("product_no"), col("stock, sold_count"), col("team_target_size"), col("team_lifetime_seconds"), col("version"), colour: business),
    ent((2, 0), "biz_team_price_policy", pk("id"), ref("product_id"), uk("(product_id, version)"), col("amount, valid_until"), colour: business),
    ent((3, 0), "biz_team_price_quote", pk("id"), ref("product_id"), ref("owner_id"), col("policy_version"), col("amount, expires_at"), colour: business),
    ent((1, 1), "biz_group_deal", pk("id"), uk("deal_no"), ref("product_id"), ref("leader_user_id"), col("target_size, current_size"), col("status, expire_time"), col("version"), colour: business),
    ent((3, 1), "biz_share_credit", pk("id"), ref("deal_id"), uk("payment_id"), ref("sharer_user_id"), ref("invitee_user_id"), colour: business),
    ent((0, 2), "biz_share_invite", pk("id"), uk("invite_ref"), uk("(deal_id, sharer_user_id)"), colour: business),
    ent((1, 2), "biz_group_member", pk("id"), ref("deal_id"), uk("(deal_id, user_id)"), uk("payment_id"), ref("sharer_user_id"), colour: business),
    ent((2, 2), "biz_group_participation_receipt", pk("id"), uk("payment_id"), ref("deal_id"), col("request_fingerprint"), col("outcome, deal_status"), colour: business),
    assoc(<biz_product>, <log_product_team_terms>, ends: ([1], [0..\*])),
    assoc(<biz_product>, <biz_team_price_policy>, ends: ([1], [0..\*])),
    assoc(<biz_team_price_policy>, <biz_team_price_quote>, ends: ([1], [0..\*])),
    assoc(<biz_product>, <biz_group_deal>, ends: ([1], [0..\*])),
    assoc(<biz_group_deal>, <biz_share_credit>, ends: ([1], [0..\*])),
    assoc(<biz_group_deal>, <biz_share_invite>, ends: ([1], [0..\*])),
    assoc(<biz_group_deal>, <biz_group_member>, ends: ([1], [1..target])),
    assoc(<biz_group_deal>, <biz_group_participation_receipt>, ends: ([0..1], [0..\*])),
  )
}

#let d9-commerce-payment = {
  set text(font: ("Arial", "Helvetica"), fill: ink)
  diagram(spacing: (9mm, 11mm),
    ent((0, 0), "biz_purchase_intent", pk("id"), ref("user_id"), ref("deal_id, product_id"), col("operation, amount"), col("business_deadline"), uk("fulfilled_payment_id"), colour: business),
    ent((1, 0), "biz_order", pk("id"), uk("order_no"), uk("payment_id"), uk("(deal_id, user_id)"), col("status, total_amount"), colour: business),
    ent((2, 0), "biz_refund", pk("id"), uk("payment_id"), ref("intent_id"), uk("provider_refund_id"), col("status, next_attempt_at"), colour: business),
    ent((3, 0), "log_refund_attempt", pk("id"), ref("refund_id"), col("version, result"), colour: grey),
    ent((1, 1), "biz_payment", pk("id"), uk("payment_no"), ref("intent_id"), uk("checkout_session_id"), uk("(user_id, request_key)"), col("status, amount"), colour: business),
    ent((0, 2), "log_payment_webhook_event", pk("id"), uk("event_id"), ref("payment_id"), col("processing_result"), colour: grey),
    ent((1, 2), "log_payment_review", pk("id"), ref("payment_id"), col("reason"), colour: grey),
    ent((2, 2), "biz_payment_review_case", pk("case_id"), uk("payment_id"), col("reason, status"), col("next_check_at"), colour: business),
    ent((0, 3), "sys_outbox", pk("id  (= eventId)"), col("event_type, event_version"), col("aggregate_id, payload"), col("published_at, attempts"), colour: messaging),
    ent((1, 3), "sys_scheduler_lock", pk("id"), uk("lock_key"), col("owner, locked_until"), colour: grey),
    ent((2, 3), "sys_scheduled_item_retry", pk("id"), uk("(job_key, item_id)"), col("failure_count, retry_after"), colour: grey),
    ent((3, 3), "sys_checkout_expiry_cursor", pk("id"), uk("job_name"), col("last_payment_id"), colour: grey),
    assoc(<biz_purchase_intent>, <biz_payment>, ends: ([1], [0..\* (≤1 fulfilled)]), label-side: left),
    assoc(<biz_payment>, <biz_order>, ends: ([1], [0..1])),
    assoc(<biz_payment>, <biz_refund>, ends: ([1], [0..1])),
    assoc(<biz_refund>, <log_refund_attempt>, ends: ([1], [0..\*])),
    assoc(<biz_payment>, <log_payment_webhook_event>, ends: ([1], [0..\*])),
    assoc(<biz_payment>, <log_payment_review>, ends: ([1], [0..\*])),
    assoc(<biz_payment>, <biz_payment_review_case>, ends: ([1], [0..1])),
  )
}
