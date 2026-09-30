-- Human-readable fallback numbers; UUIDv7 remains the product's relational identifier.
CREATE SEQUENCE domain.seq_biz_product_product_no AS bigint START WITH 100001 NO CYCLE;

COMMENT ON SEQUENCE domain.seq_biz_product_product_no IS
    'Fallback BB- product numbers; creation serializes allocation and skips existing numbers, including deleted history. Gaps are expected.';
