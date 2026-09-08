-- R__demo_catalog_seed.sql — demo catalog data for the local profile only (repeatable migration;
-- location db/seed is added to spring.flyway.locations in application-local.yml, never in docker/
-- ec2). Idempotent: fixed ids + ON CONFLICT (id) DO NOTHING, safe to re-run on checksum change.
-- 10 on_sale products across 3 categories; 6 open deals on 5 of them (product ...0001 carries two
-- open deals to exercise the lowest-deal-price rule; products ...0008..0010 have no deal, so the
-- listing shows groupPrice = null).

INSERT INTO biz_product (id, product_no, title, subtitle, description, cover_image, images,
                         category, original_price, currency, stock, sold_count, status,
                         pricing_mode)
VALUES
    ('a0000000-0000-7000-8000-000000000001', 'DEMO-P-0001', 'Wireless Noise-Cancelling Headphones',
     'Over-ear, 40h battery', 'Demo product: flagship headphones.',
     'https://cdn.example.com/demo/p1-cover.jpg', '["https://cdn.example.com/demo/p1-1.jpg"]',
     'electronics', 129.00, 'sgd', 50, 12, 'on_sale', 'dual'),
    ('a0000000-0000-7000-8000-000000000002', 'DEMO-P-0002', 'Mechanical Keyboard 75%',
     'Hot-swappable, RGB', 'Demo product: compact mechanical keyboard.',
     'https://cdn.example.com/demo/p2-cover.jpg', '["https://cdn.example.com/demo/p2-1.jpg"]',
     'electronics', 89.00, 'sgd', 80, 31, 'on_sale', 'dual'),
    ('a0000000-0000-7000-8000-000000000003', 'DEMO-P-0003', 'USB-C GaN Charger 65W',
     'Two-port fast charger', 'Demo product: travel GaN charger.',
     'https://cdn.example.com/demo/p3-cover.jpg', NULL,
     'electronics', 45.00, 'sgd', 200, 150, 'on_sale', 'dual'),
    ('a0000000-0000-7000-8000-000000000004', 'DEMO-P-0004', 'Smart Speaker Mini',
     'Voice assistant, Wi-Fi', 'Demo product: entry smart speaker.',
     'https://cdn.example.com/demo/p4-cover.jpg', NULL,
     'electronics', 59.00, 'sgd', 120, 44, 'on_sale', 'dual'),
    ('a0000000-0000-7000-8000-000000000005', 'DEMO-P-0005', 'Stainless Steel Cookware Set',
     '5-piece, induction-ready', 'Demo product: kitchen cookware bundle.',
     'https://cdn.example.com/demo/p5-cover.jpg', '["https://cdn.example.com/demo/p5-1.jpg"]',
     'home', 159.00, 'sgd', 40, 8, 'on_sale', 'dual'),
    ('a0000000-0000-7000-8000-000000000006', 'DEMO-P-0006', 'Robot Vacuum Cleaner',
     'LiDAR navigation', 'Demo product: mid-range robot vacuum.',
     'https://cdn.example.com/demo/p6-cover.jpg', NULL,
     'home', 299.00, 'sgd', 25, 17, 'on_sale', 'dual'),
    ('a0000000-0000-7000-8000-000000000007', 'DEMO-P-0007', 'Ergonomic Office Chair',
     'Mesh back, lumbar support', 'Demo product: home-office chair.',
     'https://cdn.example.com/demo/p7-cover.jpg', NULL,
     'home', 219.00, 'sgd', 30, 5, 'on_sale', 'dual'),
    ('a0000000-0000-7000-8000-000000000008', 'DEMO-P-0008', 'Vitamin C Serum 30ml',
     'Brightening, fragrance-free', 'Demo product: skincare serum.',
     'https://cdn.example.com/demo/p8-cover.jpg', NULL,
     'beauty', 39.00, 'sgd', 300, 210, 'on_sale', 'dual'),
    ('a0000000-0000-7000-8000-000000000009', 'DEMO-P-0009', 'Sunscreen SPF50+ 50ml',
     'Lightweight, no white cast', 'Demo product: daily sunscreen.',
     'https://cdn.example.com/demo/p9-cover.jpg', NULL,
     'beauty', 25.00, 'sgd', 500, 420, 'on_sale', 'dual'),
    ('a0000000-0000-7000-8000-000000000010', 'DEMO-P-0010', 'Hair Dryer Ionic',
     '1600W, cool shot', 'Demo product: ionic hair dryer.',
     'https://cdn.example.com/demo/p10-cover.jpg', NULL,
     'beauty', 79.00, 'sgd', 60, 23, 'on_sale', 'dual')
ON CONFLICT (id) DO NOTHING;

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
