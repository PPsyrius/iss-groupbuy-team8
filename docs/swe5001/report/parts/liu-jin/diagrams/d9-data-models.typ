// D9: logical data model per database (UML class notation for data: «table», key columns,
// multiplicities). Relationships are dashed: they are logical only, because the team's
// convention forbids foreign keys (pgsql-ddl skill). Source of the 3.2.3 figures.
// Column lists show keys and the columns the text refers to, not every column.
#import "../../../diagrams/style.typ": *

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

#let d9-identity = {
  set text(font: ("Arial", "Helvetica"), fill: ink)
  diagram(spacing: (12mm, 9mm),
    ent((0, 0), "sys_user", pk("id"), uk("username"), uk("email"), col("nickname"), col("status"), col("deleted"), colour: platform),
    ent((2, 0), "sys_user_oauth", pk("id"), ref("user_id"), uk("(provider, provider_user_id)"), col("raw_profile"), colour: platform),
    ent((0, 1.3), "rel_user_role", pk("id"), uk("(user_id, role_id)"), colour: platform),
    ent((1, 2.6), "sys_role", pk("id"), uk("role_code"), colour: platform),
    ent((2, 1.3), "rel_role_permission", pk("id"), uk("(role_id, permission_id)"), colour: platform),
    ent((2, 3.2), "sys_permission", pk("id"), uk("perm_code"), colour: platform),
    edge(<sys_user>, <sys_user_oauth>, "-", stroke: (dash: "dashed"), label: text(size: 6.3pt, fill: grey)[1 · 0..\*]),
    edge(<sys_user>, <rel_user_role>, "-", stroke: (dash: "dashed"), label: text(size: 6.3pt, fill: grey)[1 · 0..\*]),
    edge(<rel_user_role>, <sys_role>, "-", stroke: (dash: "dashed"), label: text(size: 6.3pt, fill: grey)[0..\* · 1]),
    edge(<sys_role>, <rel_role_permission>, "-", stroke: (dash: "dashed"), label: text(size: 6.3pt, fill: grey)[1 · 0..\*]),
    edge(<rel_role_permission>, <sys_permission>, "-", stroke: (dash: "dashed"), label: text(size: 6.3pt, fill: grey)[0..\* · 1]),
  )
}

#let d9-notification = {
  set text(font: ("Arial", "Helvetica"), fill: ink)
  diagram(spacing: (14mm, 9mm),
    ent((0, 0), "biz_notification", pk("id"), col("event_id"), col("user_id"), col("reference_id"), col("notification_type"), col("create_time"), colour: platform),
    ent((2, 0), "sys_consumed_message", pk("(event_id, consumer)"), col("consumed_at"), colour: grey),
    edge(<biz_notification>, <sys_consumed_message>, "-", stroke: (dash: "dashed"), label: text(size: 6.3pt, fill: grey)[same `event_id` · written in one transaction]),
  )
}

#let d9-commerce = {
  set text(font: ("Arial", "Helvetica"), fill: ink)
  let d = (dash: "dashed")
  diagram(spacing: (12mm, 12mm),
    ent((0, 0), "biz_product", pk("id"), uk("product_no"), col("stock, sold_count"), col("team_target_size"), col("team_lifetime_seconds"), col("version"), colour: business),
    ent((0, 1.5), "biz_team_price_policy", pk("id"), ref("product_id"), uk("(product_id, version)"), col("amount, valid_until"), colour: business),
    ent((1.6, 0), "biz_group_deal", pk("id"), uk("deal_no"), ref("product_id"), ref("leader_user_id"), col("target_size, current_size"), col("status, expire_time"), col("version"), colour: business),
    ent((1.6, 1.5), "biz_group_member", pk("id"), ref("deal_id"), uk("(deal_id, user_id)"), uk("payment_id"), ref("sharer_user_id"), colour: business),
    ent((3.2, 0), "biz_purchase_intent", pk("id"), ref("user_id"), ref("deal_id, product_id"), col("operation, amount"), col("business_deadline"), uk("fulfilled_payment_id"), colour: business),
    ent((3.2, 1.5), "biz_payment", pk("id"), uk("payment_no"), ref("intent_id"), uk("checkout_session_id"), uk("(user_id, request_key)"), col("status, amount"), colour: business),
    ent((4.8, 0), "biz_order", pk("id"), uk("order_no"), uk("payment_id"), uk("(deal_id, user_id)"), col("status, total_amount"), colour: business),
    ent((4.8, 1.5), "biz_refund", pk("id"), uk("payment_id"), ref("intent_id"), uk("provider_refund_id"), col("status, next_attempt_at"), colour: business),
    ent((0.8, 3), "biz_share_invite", pk("id"), uk("invite_ref"), uk("(deal_id, sharer_user_id)"), colour: business),
    ent((2.2, 3), "biz_share_credit", pk("id"), ref("deal_id"), uk("payment_id"), ref("sharer_user_id"), ref("invitee_user_id"), colour: business),
    ent((3.2, 3), "log_payment_webhook_event", pk("id"), uk("event_id"), ref("payment_id"), col("processing_result"), colour: grey),
    ent((4.8, 3), "sys_outbox", pk("id  (= eventId)"), col("event_type, event_version"), col("aggregate_id, payload"), col("published_at, attempts"), colour: messaging),
    ent((0, 3), "sys_scheduler_lock", pk("id"), uk("lock_key"), col("owner, locked_until"), colour: grey),
    edge(<biz_product>, <biz_team_price_policy>, "-", stroke: d, label: text(size: 6.3pt, fill: grey)[1 · 0..\*]),
    edge(<biz_product>, <biz_group_deal>, "-", stroke: d, label: text(size: 6.3pt, fill: grey)[1 · 0..\*]),
    edge(<biz_group_deal>, <biz_group_member>, "-", stroke: d, label: text(size: 6.3pt, fill: grey)[1 · 1..target]),
    edge(<biz_group_deal>, <biz_purchase_intent>, "-", stroke: d, label: text(size: 6.3pt, fill: grey)[0..1 · 0..\*]),
    edge(<biz_purchase_intent>, <biz_payment>, "-", stroke: d, label: text(size: 6.3pt, fill: grey)[1 · 0..\*; ≤1 fulfilled]),
    edge(<biz_payment>, <biz_order>, "-", stroke: d, label: text(size: 6.3pt, fill: grey)[1 · 0..1]),
    edge(<biz_payment>, <biz_refund>, "-", stroke: d, label: text(size: 6.3pt, fill: grey)[1 · 0..1]),
    edge(<biz_payment>, <biz_group_member>, "-", stroke: d, label: text(size: 6.3pt, fill: grey)[1 · 0..1]),
    edge(<biz_group_deal>, <biz_share_invite>, "-", stroke: d, label: text(size: 6.3pt, fill: grey)[1 · 0..\*]),
    edge(<biz_group_deal>, (2.4, 0.75), (2.4, 2.4), <biz_share_credit>, "-", stroke: d, label: text(size: 6.3pt, fill: grey)[1 · 0..\*], label-pos: 0.36, label-side: left),
    edge(<biz_payment>, <biz_share_credit>, "-", stroke: d, label: text(size: 6.3pt, fill: grey)[1 · 0..1]),
    edge(<biz_payment>, <log_payment_webhook_event>, "-", stroke: d, label: text(size: 6.3pt, fill: grey)[1 · 0..\*]),
  )
}
