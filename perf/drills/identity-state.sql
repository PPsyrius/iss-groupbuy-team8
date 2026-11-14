-- Fixed read-only counts for one prepared Checkout. No IDs, provider payloads or amounts are returned.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
WITH fixture AS (
    SELECT p.id, p.intent_id, p.user_id, p.status, p.fulfilment_result, i.product_id
    FROM domain.biz_payment p
    JOIN domain.biz_purchase_intent i ON i.id = p.intent_id AND NOT i.deleted
    WHERE p.id = :'payment_id'::uuid AND NOT p.deleted
)
SELECT json_build_object(
    'schema', 1,
    'observedAt', clock_timestamp(),
    'fixturePayments', (SELECT count(*) FROM fixture),
    'paymentState', (SELECT status FROM fixture),
    'fulfilmentResult', (SELECT fulfilment_result FROM fixture),
    'intentsForActorProduct', (SELECT count(*) FROM domain.biz_purchase_intent i
        JOIN fixture f ON i.user_id = f.user_id AND i.product_id = f.product_id WHERE NOT i.deleted),
    'webhookReceiptsForPayment', (SELECT count(*) FROM domain.log_payment_webhook_event WHERE payment_id = :'payment_id'::uuid),
    'eventIdProvided', :'event_id' <> '',
    'webhookReceiptsForEvent', CASE WHEN :'event_id' = '' THEN NULL ELSE
        (SELECT count(*) FROM domain.log_payment_webhook_event WHERE event_id = :'event_id') END,
    'participationReceipts', (SELECT count(*) FROM domain.biz_group_participation_receipt WHERE payment_id = :'payment_id'::uuid),
    'members', (SELECT count(*) FROM domain.biz_group_member WHERE payment_id = :'payment_id'::uuid),
    'orders', (SELECT count(*) FROM domain.biz_order WHERE payment_id = :'payment_id'::uuid),
    'refunds', (SELECT count(*) FROM domain.biz_refund WHERE payment_id = :'payment_id'::uuid),
    'productStock', (SELECT p.stock FROM domain.biz_product p JOIN fixture f ON p.id = f.product_id WHERE NOT p.deleted)
);
COMMIT;
