# SWE5001 live SPA security headers

Observed directly by the agent at **9 November 2026, 16:05:25–26 UTC (10 November, 00:05 SGT)**. Three anonymous HTTPS GETs only; no browser execution, API request, authentication, deployment or load test. Refs NFR-16.

Target: `https://d3lvq02o2e7tcn.cloudfront.net`. The `swe5001-demo` GitHub variables confirmed this URL and configured distribution `E3N39GHVFV6CT3`; no authenticated AWS resource lookup was performed. The observed edge was `SIN3-P4`, not a global propagation check.

## Result

All nine expected security-header values matched the [shared policy](../../../../deployment/frontend/cloudfront/response-headers-policy.json) exactly, with one occurrence each: CSP, HSTS, X-Content-Type-Options, X-Frame-Options, Referrer-Policy, Permissions-Policy, COEP, COOP and CORP. The local policy was unchanged from the reviewed `prod` source; its SHA-256 is retained in [observations.json](observations.json).

| Request | HTTP | Security headers | Content/cache result |
| --- | --- | --- | --- |
| `/` | 200 | All match | HTML, `no-cache` |
| `/login` | 200 | All match | Same HTML hash as `/`; SPA deep-link fallback works at the HTTP layer |
| `/assets/index-DxnbuVTu.js` | 200 | All match | Discovered from the live HTML; JavaScript, `public,max-age=31536000,immutable` |

`Server: CloudFront` remained visible. This is [documented AWS behaviour](https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/understanding-response-headers-policies.html#understanding-response-headers-policies-remove-headers): removing the origin's Server header causes CloudFront to add its own. Do not report complete Server-header suppression or treat this value as a policy mismatch.

## Limits

- These observations verify viewer-facing static responses at one edge location. They do not prove API headers, origin TLS, authenticated flows, browser CSP compatibility or every deployed route/asset.
- `/login` was fetched as anonymous HTML only; no login was attempted and no JavaScript executed.
- The HTML and JavaScript advertised `Last-Modified: 20 October 2026`. This does not establish a November frontend release or bind the served bundle to a source commit.
- All three responses were cache misses. Cache directives were observed; no cache-hit rate, latency benefit or capacity was measured.
- Only selected response metadata and body hashes were retained. No bodies, cookies, credentials or origin-secret values are stored. The JSON records exact UTC observations, actual header values, cache metadata and response hashes.
