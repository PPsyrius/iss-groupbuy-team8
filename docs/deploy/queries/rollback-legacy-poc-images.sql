-- Run from the private APPLY directory; never use the dry-run backup.
\set ON_ERROR_STOP on
\if :{?apply}
\else
  \set apply off
\endif
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
SET LOCAL search_path = domain, pg_catalog;
LOCK TABLE biz_product, biz_order, biz_purchase_intent IN SHARE ROW EXCLUSIVE MODE;
CREATE TEMP TABLE poc_image_backup (
    source varchar(32) NOT NULL,
    id uuid NOT NULL,
    before_fields jsonb NOT NULL,
    after_fields jsonb NOT NULL,
    PRIMARY KEY (source, id)
) ON COMMIT DROP;
\copy poc_image_backup FROM 'legacy-image-backup.csv' WITH (FORMAT csv, HEADER true)

DO $$
BEGIN
    IF (SELECT count(*) FROM poc_image_backup) <> 13
       OR (SELECT count(*) FROM poc_image_backup WHERE source = 'catalog') <> 3
       OR (SELECT count(*) FROM poc_image_backup WHERE source = 'order') <> 5
       OR (SELECT count(*) FROM poc_image_backup WHERE source = 'intent') <> 5 THEN
        RAISE EXCEPTION 'Unexpected backup counts; stop';
    END IF;
    IF (SELECT count(*) FROM poc_image_backup b JOIN biz_product p ON p.id = b.id
        WHERE b.source = 'catalog' AND b.after_fields = jsonb_build_object(
            'cover_image', p.cover_image, 'images', p.images, 'version', p.version, 'update_time', p.update_time)) <> 3
       OR (SELECT count(*) FROM poc_image_backup b JOIN biz_order o ON o.id = b.id
           WHERE b.source = 'order' AND b.after_fields = jsonb_build_object(
               'product_image', o.product_image, 'version', o.version, 'update_time', o.update_time)) <> 5
       OR (SELECT count(*) FROM poc_image_backup b JOIN biz_purchase_intent i ON i.id = b.id
           WHERE b.source = 'intent' AND b.after_fields = jsonb_build_object(
               'product_image', i.product_image, 'version', i.version, 'update_time', i.update_time)) <> 5 THEN
        RAISE EXCEPTION 'Rows changed since apply, or wrong backup; no automatic rollback permitted';
    END IF;
END $$;

UPDATE biz_product p
SET cover_image = b.before_fields->>'cover_image',
    images = NULLIF(b.before_fields->'images', 'null'::jsonb),
    version = p.version + 1, update_time = CURRENT_TIMESTAMP
FROM poc_image_backup b WHERE b.source = 'catalog' AND p.id = b.id;
UPDATE biz_order o
SET product_image = b.before_fields->>'product_image', version = o.version + 1, update_time = CURRENT_TIMESTAMP
FROM poc_image_backup b WHERE b.source = 'order' AND o.id = b.id;
UPDATE biz_purchase_intent i
SET product_image = b.before_fields->>'product_image', version = i.version + 1, update_time = CURRENT_TIMESTAMP
FROM poc_image_backup b WHERE b.source = 'intent' AND i.id = b.id;
\if :apply
  COMMIT;
  \echo 'ROLLED BACK image substitution; versions remain monotonic; strict CSP must stay paused'
\else
  ROLLBACK;
  \echo 'DRY RUN: rollback rehearsal only; no database changes retained'
\endif
