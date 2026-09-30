-- Used only in the disposable Testcontainers database of PerformanceBaselineIT.
INSERT INTO sys_user(id, nickname, register_source, status)
SELECT md5('perf-user-' || n)::uuid, 'Synthetic buyer ' || n, 'local', 'active'
FROM generate_series(0, 100) n;

INSERT INTO biz_product(id, product_no, title, original_price, currency, stock, status,
                        team_target_size, team_lifetime_seconds)
SELECT md5('perf-product-' || n)::uuid, 'PERF-' || n, 'Synthetic product ' || n,
       20, 'sgd', 100000, 'on_sale', 200, 7200
FROM generate_series(1, 1000) n;

-- One hot team is shared by all JOIN VUs. Intent issuance does not consume a paid seat.
INSERT INTO biz_group_deal(id, deal_no, product_id, leader_user_id, deal_price, currency,
                           target_size, current_size, status, start_time, expire_time)
SELECT md5('perf-open-' || n)::uuid, 'PERF-OPEN-' || n, md5('perf-product-' || n)::uuid,
       md5('perf-user-0')::uuid, 10, 'sgd', 200, 1, 'open',
       clock_timestamp() - interval '1 hour', clock_timestamp() + interval '1 day'
FROM generate_series(1, 130) n;

UPDATE biz_group_deal SET deal_no = 'PERF-EXPIRY-' || n, target_size = 2
FROM generate_series(1, 30) n
WHERE id = md5('perf-open-' || (100 + n))::uuid;

-- Ten completed teams hold ten real order rows per buyer. No provider is contacted.
INSERT INTO biz_group_deal(id, deal_no, product_id, leader_user_id, deal_price, currency,
                           target_size, current_size, status, start_time, expire_time, success_time)
SELECT md5('perf-history-' || n)::uuid, 'PERF-HISTORY-' || n, md5('perf-product-' || n)::uuid,
       md5('perf-user-1')::uuid, 10, 'sgd', 100, 100, 'succeeded',
       clock_timestamp() - interval '2 hours', clock_timestamp() + interval '1 day', clock_timestamp()
FROM generate_series(1, 10) n;

INSERT INTO biz_order(id, order_no, payment_id, user_id, deal_id, product_id, product_title,
                      quantity, unit_price, total_amount, currency, status, paid_time)
SELECT md5('perf-order-' || u || '-' || d)::uuid, 'PERF-ORDER-' || u || '-' || d,
       md5('perf-payment-' || u || '-' || d)::uuid, md5('perf-user-' || u)::uuid,
       md5('perf-history-' || d)::uuid, md5('perf-product-' || d)::uuid, 'Synthetic product ' || d,
       1, 10, 10, 'sgd', 'confirmed', clock_timestamp() - interval '1 hour'
FROM generate_series(1, 100) u CROSS JOIN generate_series(1, 10) d;

INSERT INTO biz_order(id, order_no, payment_id, user_id, deal_id, product_id, product_title,
                      quantity, unit_price, total_amount, currency, status, paid_time)
SELECT md5('perf-leader-order-' || deal_no)::uuid, 'PERF-LEADER-' || deal_no,
       md5('perf-leader-payment-' || deal_no)::uuid, leader_user_id, id, product_id,
       'Synthetic product', 1, 10, 10, 'sgd', 'pending', clock_timestamp() - interval '1 hour'
FROM biz_group_deal WHERE status = 'open';

INSERT INTO biz_purchase_intent(id,user_id,operation,deal_id,product_id,product_title,
                                amount,currency,target_size,quote_valid_until,business_deadline,
                                fulfilled_payment_id)
SELECT md5('perf-leader-intent-' || deal_no)::uuid, leader_user_id, 'START', id, product_id,
       'Synthetic product', 10, 'sgd', target_size, clock_timestamp() - interval '1 hour',
       expire_time, md5('perf-leader-payment-' || deal_no)::uuid
FROM biz_group_deal WHERE status = 'open';

INSERT INTO biz_payment(id, payment_no, order_id, user_id, provider, amount, currency, status,
                        paid_time, fulfilment_result, fulfilled_deal_id)
SELECT payment_id, 'PERF-PAY-' || order_no, id, user_id, 'stripe', 10, 'sgd',
       'succeeded', paid_time, 'FULFILLED', deal_id FROM biz_order;

UPDATE biz_payment p SET intent_id = i.id, operation = 'START', business_id = i.product_id,
       quote_valid_until = i.quote_valid_until, business_deadline = i.business_deadline,
       provider_payment_intent_id = 'pi_perf_' || p.id
FROM biz_purchase_intent i WHERE p.id = i.fulfilled_payment_id;

INSERT INTO biz_group_member(deal_id, order_id, payment_id, user_id, member_role, status, join_time)
SELECT deal_id, id, payment_id, user_id, CASE WHEN user_id = md5('perf-user-1')::uuid
       OR user_id = md5('perf-user-0')::uuid THEN 'leader' ELSE 'member' END,
       'paid', paid_time FROM biz_order;

ANALYZE;
