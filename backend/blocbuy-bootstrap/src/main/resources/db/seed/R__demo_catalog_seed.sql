-- R__demo_catalog_seed.sql — demo catalog data (repeatable migration). Location db/seed is added
-- to spring.flyway.locations in application-local.yml and, through SPRING_FLYWAY_LOCATIONS, on the
-- SWE5001 ECS services (infra/swe5001/runtime/services.tf); never on the dev EC2 host.
-- Idempotent fixed ids and policies, safe to re-run on checksum change.
-- Cover images are the same files the dev site serves from its media CloudFront host
-- (products/<product_no>/cover.jpg), so every environment seeded here shows real images (#557).
-- 10 on_sale products across 3 categories; 6 open deals on 5 of them (product ...0001 carries two
-- open deals to exercise the lowest-deal-price rule; products ...0008..0010 have no deal, so the
-- listing shows groupPrice = null).

INSERT INTO biz_product (id, product_no, title, subtitle, description, cover_image, images,
                         category, original_price, currency, stock, sold_count, status,
                         pricing_mode)
VALUES
    ('a0000000-0000-7000-8000-000000000001', 'DEMO-P-0001', 'Wireless Noise-Cancelling Headphones',
     'Over-ear, 40h battery', 'Demo product: flagship headphones.',
     'https://dcsjrkmummxi7.cloudfront.net/products/DEMO-P-0001/cover.jpg', NULL,
     'electronics', 129.00, 'sgd', 50, 12, 'on_sale', 'dual'),
    ('a0000000-0000-7000-8000-000000000002', 'DEMO-P-0002', 'Mechanical Keyboard 75%',
     'Hot-swappable, RGB', 'Demo product: compact mechanical keyboard.',
     'https://dcsjrkmummxi7.cloudfront.net/products/DEMO-P-0002/cover.jpg', NULL,
     'electronics', 89.00, 'sgd', 80, 31, 'on_sale', 'dual'),
    ('a0000000-0000-7000-8000-000000000003', 'DEMO-P-0003', 'USB-C GaN Charger 65W',
     'Two-port fast charger', 'Demo product: travel GaN charger.',
     'https://dcsjrkmummxi7.cloudfront.net/products/DEMO-P-0003/cover.jpg', NULL,
     'electronics', 45.00, 'sgd', 200, 150, 'on_sale', 'dual'),
    ('a0000000-0000-7000-8000-000000000004', 'DEMO-P-0004', 'Smart Speaker Mini',
     'Voice assistant, Wi-Fi', 'Demo product: entry smart speaker.',
     'https://dcsjrkmummxi7.cloudfront.net/products/DEMO-P-0004/cover.jpg', NULL,
     'electronics', 59.00, 'sgd', 120, 44, 'on_sale', 'dual'),
    ('a0000000-0000-7000-8000-000000000005', 'DEMO-P-0005', 'Stainless Steel Cookware Set',
     '5-piece, induction-ready', 'Demo product: kitchen cookware bundle.',
     'https://dcsjrkmummxi7.cloudfront.net/products/DEMO-P-0005/cover.jpg', NULL,
     'home', 159.00, 'sgd', 40, 8, 'on_sale', 'dual'),
    ('a0000000-0000-7000-8000-000000000006', 'DEMO-P-0006', 'Robot Vacuum Cleaner',
     'LiDAR navigation', 'Demo product: mid-range robot vacuum.',
     'https://dcsjrkmummxi7.cloudfront.net/products/DEMO-P-0006/cover.jpg', NULL,
     'home', 299.00, 'sgd', 25, 17, 'on_sale', 'dual'),
    ('a0000000-0000-7000-8000-000000000007', 'DEMO-P-0007', 'Ergonomic Office Chair',
     'Mesh back, lumbar support', 'Demo product: home-office chair.',
     'https://dcsjrkmummxi7.cloudfront.net/products/DEMO-P-0007/cover.jpg', NULL,
     'home', 219.00, 'sgd', 30, 5, 'on_sale', 'dual'),
    ('a0000000-0000-7000-8000-000000000008', 'DEMO-P-0008', 'Vitamin C Serum 30ml',
     'Brightening, fragrance-free', 'Demo product: skincare serum.',
     'https://dcsjrkmummxi7.cloudfront.net/products/DEMO-P-0008/cover.jpg', NULL,
     'beauty', 39.00, 'sgd', 300, 210, 'on_sale', 'dual'),
    ('a0000000-0000-7000-8000-000000000009', 'DEMO-P-0009', 'Sunscreen SPF50+ 50ml',
     'Lightweight, no white cast', 'Demo product: daily sunscreen.',
     'https://dcsjrkmummxi7.cloudfront.net/products/DEMO-P-0009/cover.jpg', NULL,
     'beauty', 25.00, 'sgd', 500, 420, 'on_sale', 'dual'),
    ('a0000000-0000-7000-8000-000000000010', 'DEMO-P-0010', 'Hair Dryer Ionic',
     '1600W, cool shot', 'Demo product: ionic hair dryer.',
     'https://dcsjrkmummxi7.cloudfront.net/products/DEMO-P-0010/cover.jpg', NULL,
     'beauty', 79.00, 'sgd', 60, 23, 'on_sale', 'dual')
ON CONFLICT (id) DO NOTHING;

-- Rows seeded before #557 still point at cdn.example.com, a reserved domain that serves nothing.
-- Only those untouched seed values are repointed; an image an admin has changed stays as it is,
-- so the gallery is cleared only while every URL in it is still a placeholder.
UPDATE biz_product
SET cover_image = 'https://dcsjrkmummxi7.cloudfront.net/products/' || product_no || '/cover.jpg',
    images = CASE
                 WHEN jsonb_typeof(images) <> 'array' THEN images
                 WHEN EXISTS (SELECT 1
                              FROM jsonb_array_elements_text(images) AS url
                              WHERE url NOT LIKE 'https://cdn.example.com/%') THEN images
             END,
    update_time = CURRENT_TIMESTAMP,
    update_by = 'c0000000-0000-7000-8000-000000000001',
    version = version + 1
WHERE product_no LIKE 'DEMO-P-%'
  AND cover_image LIKE 'https://cdn.example.com/%';

-- Existing local demo rows receive terms only when at least one term is missing.
UPDATE biz_product
SET team_target_size = COALESCE(team_target_size, 3),
    team_lifetime_seconds = COALESCE(team_lifetime_seconds, 86400),
    update_time = CURRENT_TIMESTAMP,
    update_by = 'c0000000-0000-7000-8000-000000000001',
    version = version + 1
WHERE product_no LIKE 'DEMO-P-%'
  AND status = 'on_sale'
  AND (team_target_size IS NULL OR team_lifetime_seconds IS NULL);

-- First policy revision for each local demo product. The quote window fits inside this interval.
INSERT INTO biz_team_price_policy
    (id, product_id, amount, currency, valid_from, valid_until, create_by, update_by, version)
SELECT uuidv7(), product.id, prices.amount, product.currency,
       '2026-01-01T00:00:00Z', '2030-01-01T00:00:00Z',
       'c0000000-0000-7000-8000-000000000001',
       'c0000000-0000-7000-8000-000000000001', 1
FROM (VALUES
    ('DEMO-P-0001', 89.00), ('DEMO-P-0002', 69.00),
    ('DEMO-P-0003', 35.00), ('DEMO-P-0004', 49.00),
    ('DEMO-P-0005', 119.00), ('DEMO-P-0006', 249.00),
    ('DEMO-P-0007', 179.00), ('DEMO-P-0008', 29.00),
    ('DEMO-P-0009', 19.00), ('DEMO-P-0010', 59.00)
) AS prices(product_no, amount)
JOIN biz_product product ON product.product_no = prices.product_no
WHERE product.status = 'on_sale'
  AND prices.amount < product.original_price
ON CONFLICT (product_id, version) DO NOTHING;

INSERT INTO biz_group_deal (id, deal_no, product_id, leader_user_id, deal_price, currency,
                            target_size, current_size, status, start_time, expire_time)
VALUES
    ('b0000000-0000-7000-8000-000000000001', 'DEMO-D-0001', 'a0000000-0000-7000-8000-000000000001',
     'c0000000-0000-7000-8000-000000000001', 99.00, 'sgd', 5, 2, 'open',
     '2026-09-01T00:00:00Z', '2027-12-31T23:59:59Z'),
    -- second open deal on the same product at a lower price: browse must surface 89.00, not 99.00
    ('b0000000-0000-7000-8000-000000000002', 'DEMO-D-0002', 'a0000000-0000-7000-8000-000000000001',
     'c0000000-0000-7000-8000-000000000002', 89.00, 'sgd', 3, 1, 'open',
     '2026-09-05T00:00:00Z', '2027-12-31T23:59:59Z'),
    ('b0000000-0000-7000-8000-000000000003', 'DEMO-D-0003', 'a0000000-0000-7000-8000-000000000002',
     'c0000000-0000-7000-8000-000000000001', 69.00, 'sgd', 4, 3, 'open',
     '2026-09-01T00:00:00Z', '2027-12-31T23:59:59Z'),
    ('b0000000-0000-7000-8000-000000000004', 'DEMO-D-0004', 'a0000000-0000-7000-8000-000000000003',
     'c0000000-0000-7000-8000-000000000002', 35.00, 'sgd', 10, 6, 'open',
     '2026-09-01T00:00:00Z', '2027-12-31T23:59:59Z'),
    ('b0000000-0000-7000-8000-000000000005', 'DEMO-D-0005', 'a0000000-0000-7000-8000-000000000005',
     'c0000000-0000-7000-8000-000000000001', 119.00, 'sgd', 5, 1, 'open',
     '2026-09-01T00:00:00Z', '2027-12-31T23:59:59Z'),
    ('b0000000-0000-7000-8000-000000000006', 'DEMO-D-0006', 'a0000000-0000-7000-8000-000000000006',
     'c0000000-0000-7000-8000-000000000002', 249.00, 'sgd', 3, 2, 'open',
     '2026-09-01T00:00:00Z', '2027-12-31T23:59:59Z')
ON CONFLICT (id) DO NOTHING;
