const { chromium } = require(process.cwd() + '/node_modules/@playwright/test');
const fs = require('node:fs');
const { createHash } = require('node:crypto');
const path = require('node:path');
const base = 'https://d1b3np88z657cp.cloudfront.net';
const output = process.argv[2] || path.join(process.env.LOCALAPPDATA, 'Temp/opencode/zap-policy-audit.json');
const cases = [
  { name: 'baseline' },
  { name: 'image-allowlist-and-low-headers', restrictImages: true, headers: true },
  { name: 'strict-style', restrictImages: true, headers: true, strictStyle: true },
  { name: 'hashed-react-aria-styles', restrictImages: true, headers: true, strictStyle: true, hashStyles: true },
  { name: 'coep-require-corp', restrictImages: true, headers: true, coep: 'require-corp' },
  { name: 'coep-credentialless', restrictImages: true, headers: true, coep: 'credentialless' },
];
const trustedStyles = [
  '@layer {\n  [data-react-aria-pressable] {\n    touch-action: pan-x pan-y pinch-zoom;\n  }\n}',
  '@layer {\n  * {\n    overscroll-behavior: contain;\n  }\n}',
];
const styleHashes = trustedStyles.map(text => `'sha256-${createHash('sha256').update(text).digest('base64')}'`).join(' ');
(async () => {
  const browser = await chromium.launch();
  const browserVersion = browser.version();
  const results = [];
  try {
    for (const policy of cases) {
      const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, serviceWorkers: 'block' });
      await context.addInitScript(() => {
        window.auditViolations = [];
        document.addEventListener('securitypolicyviolation', e => window.auditViolations.push({ directive: e.effectiveDirective, blockedURI: e.blockedURI, sample: e.sample }));
      });
      await context.route('**/*', async route => {
        if (!['GET', 'HEAD', 'OPTIONS'].includes(route.request().method())) return route.abort();
        if (!route.request().isNavigationRequest() || !route.request().url().startsWith(base + '/')) return route.continue();
        const response = await route.fetch();
        const headers = { ...response.headers() };
        if (policy.restrictImages) headers['content-security-policy'] = headers['content-security-policy'].replace("img-src 'self' https:", "img-src 'self' https://dcsjrkmummxi7.cloudfront.net https://lh3.googleusercontent.com");
        if (policy.strictStyle) headers['content-security-policy'] = headers['content-security-policy'].replace("style-src 'self' 'unsafe-inline'", "style-src 'self'");
        if (policy.hashStyles) headers['content-security-policy'] = headers['content-security-policy'].replace("style-src 'self'", "style-src 'self' " + styleHashes);
        if (policy.headers) {
          headers['permissions-policy'] = 'camera=(), microphone=(), geolocation=(), payment=(), usb=()';
          headers['cross-origin-opener-policy'] = 'same-origin';
          headers['cross-origin-resource-policy'] = 'same-origin';
        }
        if (policy.coep) headers['cross-origin-embedder-policy'] = policy.coep;
        await route.fulfill({ response, headers });
      });
      const page = await context.newPage();
      const errors = [];
      page.on('pageerror', e => errors.push(e.message));
      const listing = await context.request.get(base + '/api/v1/products?size=1');
      const product = (await listing.json()).data.items[0];
      for (const pagePath of ['/', '/products/' + product.id, '/login']) {
        const failed = [];
        const onFailed = request => failed.push({ url: request.url(), failure: request.failure()?.errorText });
        page.on('requestfailed', onFailed);
        const response = await page.goto(base + pagePath, { waitUntil: 'networkidle' });
        await page.locator('h1').first().waitFor();
        await page.evaluate(async () => {
          await Promise.all([...document.images].map(image => {
            image.loading = 'eager';
            return image.decode().catch(() => {});
          }));
        });
        const info = await page.evaluate(() => ({
          title: document.title,
          isolated: crossOriginIsolated,
          styleTags: [...document.querySelectorAll('style')].map(x => ({ text: x.textContent.slice(0, 220), nonce: x.nonce })),
          inlineStyles: [...document.querySelectorAll('[style]')].slice(0, 15).map(x => ({ tag: x.tagName, style: x.getAttribute('style'), label: x.getAttribute('aria-label') })),
          images: [...document.images].map(x => ({ src: x.currentSrc || x.src, loaded: x.complete && x.naturalWidth > 0 })),
          violations: window.auditViolations,
        }));
        if (pagePath === '/') {
          await page.evaluate(() => new Promise(resolve => {
            const img = new Image();
            img.onload = () => resolve(true);
            img.onerror = () => resolve(false);
            img.src = 'https://example.com/blocbuy-controlled-csp-probe.png';
            document.body.append(img);
          }));
          info.unapprovedOriginBlockedByCsp = await page.evaluate(() => window.auditViolations.some(x => x.directive === 'img-src' && x.blockedURI.startsWith('https://example.com')));
        }
        results.push({ policy: policy.name, pagePath, status: response.status(), ...info, failed, errors: [...errors] });
        page.off('requestfailed', onFailed);
      }
      await context.close();
    }
  } finally {
    await browser.close();
    fs.writeFileSync(output, JSON.stringify({ capturedAtUtc: new Date().toISOString(), target: base, browserVersion, styleHashes, cases, scope: 'Anonymous GET-only browsing with local response-header overrides; no AWS changes or real sign-in/payment. The image allowlist includes a provisional Google avatar host that has not been verified with a signed-in account.', results }, null, 2));
  }
  for (const r of results) console.log(JSON.stringify({ policy: r.policy, page: r.pagePath, status: r.status, loadedImages: r.images.filter(i => i.loaded).length, images: r.images.length, styleTags: r.styleTags.length, inlineStyles: r.inlineStyles.length, violations: r.violations, failed: r.failed, unapprovedOriginBlockedByCsp: r.unapprovedOriginBlockedByCsp, errors: r.errors }));
  console.log('Trusted style hashes:', styleHashes);
  console.log('Evidence:', output);
})().catch(e => { console.error(e); process.exitCode = 1; });
