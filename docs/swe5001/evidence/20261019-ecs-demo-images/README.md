# Demo product images on the SWE5001 session — 2026-10-19

Refs #557. Observed while recording the demo fallback clips on the running SWE5001 distributed session. Every product image on the storefront fails to load. Demo step 1 opens on this page.

| Item | Observed value |
| --- | --- |
| Public target | `https://d3lvq02o2e7tcn.cloudfront.net` |
| Environment | [environment run 39198123242](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39198123242) on `prod` at `5e83d63` |
| Captured | 2026-10-19 17:18 SGT, headless Chromium at 1920x1080, signed out |
| Storefront images | 10 `<img>`, 10 broken, all on `cdn.example.com` |
| SWE5006 dev storefront, same check | 15 `<img>`, 0 broken, 14 on the dev media CloudFront host |
| `cdn.example.com/demo/p1-cover.jpg` | no connection from the capture workstation (curl status `000`) |

## Files

| File | What it shows |
| --- | --- |
| [`ecs-session-home.png`](ecs-session-home.png) | SWE5001 session storefront: grey hatched boxes where the cover images should be |
| [`swe5006-dev-home.png`](swe5006-dev-home.png) | SWE5006 dev storefront, same frontend: every image loads |

## Why

- `commerce-svc` loads the demo seed with the schema: `SPRING_FLYWAY_LOCATIONS = "classpath:db/migration,classpath:db/seed"` in [`infra/swe5001/runtime/services.tf`](../../../../infra/swe5001/runtime/services.tf).
- That seed, [`R__demo_catalog_seed.sql`](../../../../backend/blocbuy-bootstrap/src/main/resources/db/seed/R__demo_catalog_seed.sql), sets every `cover_image` and `images` value to `https://cdn.example.com/demo/p<N>-*.jpg`. `example.com` is a reserved documentation domain and serves no images.
- The dev site is seeded differently, through [`scripts/seed-dev-demo-listings.sh`](../../../../scripts/seed-dev-demo-listings.sh), with images on its own media host, so it is not affected.
