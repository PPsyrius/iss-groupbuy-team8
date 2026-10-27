-- Run with an authorized read-only PostgreSQL session before tightening image CSP.
-- Aggregate origins only: no user IDs, emails, order IDs, URL paths or query strings.
BEGIN TRANSACTION READ ONLY;
SET LOCAL statement_timeout = '30s';

WITH image_urls(source, url) AS (
    SELECT 'catalog_cover', cover_image FROM biz_product
    UNION ALL
    SELECT 'catalog_gallery', jsonb_array_elements_text(COALESCE(images, '[]'::jsonb)) FROM biz_product
    UNION ALL
    SELECT 'historical_order', product_image FROM biz_order
    UNION ALL
    SELECT 'purchase_intent', product_image FROM biz_purchase_intent
    UNION ALL
    SELECT 'user_avatar', avatar_url FROM sys_user
    UNION ALL
    SELECT 'oauth_avatar', avatar_url FROM sys_user_oauth
), authorities AS (
    SELECT source,
           CASE
               WHEN url IS NULL OR btrim(url) = '' THEN '(missing)'
               WHEN url ~* '^https?://[^/?#]*@' THEN '(invalid URL: credentials)'
               WHEN url ~* '^https?://[^/?#[:space:]]+' THEN
                   lower(substring(url FROM '(?i)^(https?)://')) || '://' ||
                   lower(regexp_replace(substring(url FROM '(?i)^https?://([^/?#[:space:]]+)'), '^.*@', ''))
               ELSE '(invalid URL)'
           END AS origin
    FROM image_urls
)
SELECT source, origin, count(*) AS occurrences
FROM authorities
GROUP BY source, origin
ORDER BY source, origin;

COMMIT;
