## Passive DAST: COMPLETE

Required requests covered: 10/10.

| Check | Method | URL | Expected | Observed | Result |
|---|---|---|---|---|---|
| SPA root | GET | https://d1b3np88z657cp.cloudfront.net/ | 200 | 200 | PASS |
| Catalog | GET | https://d1b3np88z657cp.cloudfront.net/api/v1/products | 200 | 200 | PASS |
| Pagination | GET | https://d1b3np88z657cp.cloudfront.net/api/v1/products?page=2&size=1 | 200 | 200 | PASS |
| Category filter | GET | https://d1b3np88z657cp.cloudfront.net/api/v1/products?category=electronics | 200 | 200 | PASS |
| Keyword search | GET | https://d1b3np88z657cp.cloudfront.net/api/v1/products?keyword=Universal&ai=false | 200 | 200 | PASS |
| Product detail | GET | https://d1b3np88z657cp.cloudfront.net/api/v1/products/01a11acc-b84a-7134-90bd-a80c8c719739 | 200 | 200 | PASS |
| SPA product deep link | GET | https://d1b3np88z657cp.cloudfront.net/products/01a11acc-b84a-7134-90bd-a80c8c719739 | 200 | 200 | PASS |
| Invalid pagination | GET | https://d1b3np88z657cp.cloudfront.net/api/v1/products?page=0 | 400 | 400 | PASS |
| Anonymous orders denial | GET | https://d1b3np88z657cp.cloudfront.net/api/v1/orders/me | 401 | 401 | PASS |
| Anonymous admin denial | GET | https://d1b3np88z657cp.cloudfront.net/api/v1/admin/products | 401 | 401 | PASS |

Passive processing verified: 67/67 enabled rules ran; queue drained.

Findings (advisory): 0 High, 0 Medium, 0 Low, 6 Informational.

See the `zap-baseline` artifact for reports and `coverage.json` for visited URLs/methods/statuses. Static assets are not counted as required application coverage. No browser execution, active attacks or authenticated coverage is claimed.
