-- Operator-only dev PoC repair, not a Flyway migration. See legacy-poc-images.md.
-- Run psql -X from a NEW private working directory (umask 077).
\set ON_ERROR_STOP on
\if :{?apply}
\else
  \set apply off
\endif
\if :{?replacement_url}
\else
  DO $$ BEGIN RAISE EXCEPTION 'replacement_url is required; stop'; END $$;
\endif
\! test "$(umask)" = 0077 || test "$(umask)" = 077
\if :SHELL_ERROR
  DO $$ BEGIN RAISE EXCEPTION 'Launch psql with umask 077; stop'; END $$;
\endif
\! test ! -e legacy-image-backup.csv && test ! -L legacy-image-backup.csv
\if :SHELL_ERROR
  DO $$ BEGIN RAISE EXCEPTION 'Backup already exists; use a new private working directory'; END $$;
\endif

BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30s';
SET LOCAL search_path = domain, pg_catalog;
-- Block concurrent writes briefly; ordinary SELECTs remain possible.
LOCK TABLE biz_product, biz_order, biz_purchase_intent IN SHARE ROW EXCLUSIVE MODE;

CREATE TEMP TABLE poc_image_config (replacement_url text NOT NULL) ON COMMIT DROP;
INSERT INTO poc_image_config VALUES (:'replacement_url');
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM poc_image_config c
        WHERE length(c.replacement_url) <= 1024
          AND c.replacement_url ~ '^https://dcsjrkmummxi7[.]cloudfront[.]net/[^[:space:]?#]+$'
          AND EXISTS (
              SELECT 1 FROM biz_product p
              WHERE NOT p.deleted AND p.status = 'on_sale'
                AND p.cover_image = c.replacement_url
          )
    ) THEN
        RAISE EXCEPTION 'Replacement must be an existing on-sale cover on the approved CDN, without query/fragment';
    END IF;
END $$;

-- Private backup contains only row identity, image fields and concurrency/audit metadata.
CREATE TEMP TABLE poc_image_backup (
    source varchar(32) NOT NULL,
    id uuid NOT NULL,
    before_fields jsonb NOT NULL,
    after_fields jsonb NOT NULL,
    PRIMARY KEY (source, id)
) ON COMMIT DROP;

INSERT INTO poc_image_backup
SELECT 'catalog', p.id,
       jsonb_build_object('cover_image', p.cover_image, 'images', p.images,
                          'version', p.version, 'update_time', p.update_time),
       jsonb_build_object(
           'cover_image', CASE WHEN p.cover_image ~* '^https://loremflickr[.]com([/:?#]|$)'
                               THEN c.replacement_url ELSE p.cover_image END,
           'images', CASE WHEN p.images IS NULL THEN NULL ELSE (
               SELECT COALESCE(jsonb_agg(CASE WHEN image.url ~* '^https://loremflickr[.]com([/:?#]|$)'
                                    THEN c.replacement_url ELSE image.url END ORDER BY image.position), '[]'::jsonb)
               FROM jsonb_array_elements_text(p.images) WITH ORDINALITY AS image(url, position)
           ) END,
           'version', p.version + 1, 'update_time', CURRENT_TIMESTAMP)
FROM biz_product p CROSS JOIN poc_image_config c
WHERE p.cover_image ~* '^https://loremflickr[.]com([/:?#]|$)'
   OR EXISTS (SELECT 1 FROM jsonb_array_elements_text(COALESCE(p.images, '[]'::jsonb)) AS image(url)
              WHERE image.url ~* '^https://loremflickr[.]com([/:?#]|$)');

INSERT INTO poc_image_backup
SELECT 'order', o.id,
       jsonb_build_object('product_image', o.product_image, 'version', o.version, 'update_time', o.update_time),
       jsonb_build_object('product_image', c.replacement_url, 'version', o.version + 1, 'update_time', CURRENT_TIMESTAMP)
FROM biz_order o CROSS JOIN poc_image_config c
WHERE o.product_image ~* '^https://loremflickr[.]com([/:?#]|$)';

INSERT INTO poc_image_backup
SELECT 'intent', i.id,
       jsonb_build_object('product_image', i.product_image, 'version', i.version, 'update_time', i.update_time),
       jsonb_build_object('product_image', c.replacement_url, 'version', i.version + 1, 'update_time', CURRENT_TIMESTAMP)
FROM biz_purchase_intent i CROSS JOIN poc_image_config c
WHERE i.product_image ~* '^https://loremflickr[.]com([/:?#]|$)';

DO $$
BEGIN
    IF (SELECT count(*) FROM poc_image_backup WHERE source = 'catalog') <> 3
       OR (SELECT count(*) FROM poc_image_backup WHERE source = 'order') <> 5
       OR (SELECT count(*) FROM poc_image_backup WHERE source = 'intent') <> 5 THEN
        RAISE EXCEPTION 'Inventory changed: expected 3 products, 5 orders and 5 intents; re-inventory and review';
    END IF;
    IF (SELECT count(*) FROM biz_product WHERE cover_image ~* '^https://loremflickr[.]com([/:?#]|$)') <> 3
       OR (SELECT count(*) FROM biz_product p,
               LATERAL jsonb_array_elements_text(COALESCE(p.images, '[]'::jsonb)) AS image(url)
           WHERE image.url ~* '^https://loremflickr[.]com([/:?#]|$)') <> 6 THEN
        RAISE EXCEPTION 'Catalog reference counts changed: expected 3 covers and 6 gallery entries';
    END IF;
END $$;

SELECT source, count(*) AS affected_rows FROM poc_image_backup GROUP BY source ORDER BY source;
-- psql writes this BEFORE any update/commit; failure aborts the session/transaction.
\copy poc_image_backup TO 'legacy-image-backup.csv' WITH (FORMAT csv, HEADER true)

UPDATE biz_product p
SET cover_image = b.after_fields->>'cover_image', images = NULLIF(b.after_fields->'images', 'null'::jsonb),
    version = (b.after_fields->>'version')::integer,
    update_time = (b.after_fields->>'update_time')::timestamptz
FROM poc_image_backup b WHERE b.source = 'catalog' AND p.id = b.id;
UPDATE biz_order o
SET product_image = b.after_fields->>'product_image', version = (b.after_fields->>'version')::integer,
    update_time = (b.after_fields->>'update_time')::timestamptz
FROM poc_image_backup b WHERE b.source = 'order' AND o.id = b.id;
UPDATE biz_purchase_intent i
SET product_image = b.after_fields->>'product_image', version = (b.after_fields->>'version')::integer,
    update_time = (b.after_fields->>'update_time')::timestamptz
FROM poc_image_backup b WHERE b.source = 'intent' AND i.id = b.id;

\if :apply
  COMMIT;
  \echo 'APPLIED: retain the private backup; run inventory and image-load checks'
\else
  ROLLBACK;
  \echo 'DRY RUN: all database changes rolled back; backup describes the rehearsal only'
\endif
