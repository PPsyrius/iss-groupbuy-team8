// Anonymous GET-only audit with local document-header overrides, never a deployment.
// Run from frontend/ with Playwright engines installed; pass policy and output directory.
const { chromium, firefox, webkit } = require(process.cwd() + '/node_modules/@playwright/test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const [policyPath, output] = process.argv.slice(2);
const target = 'https://d1b3np88z657cp.cloudfront.net';
(async () => {
  const policy = JSON.parse(fs.readFileSync(policyPath, 'utf8'));
  const baseline = process.env.AUDIT_BASELINE === 'true';
  const selected = (process.env.AUDIT_BROWSERS ?? 'chromium,firefox,webkit').split(',');
  assert.ok(selected.every(name => ['chromium', 'firefox', 'webkit'].includes(name)));
  const report = { checkedAtUtc: new Date().toISOString(), target, scope: baseline ? 'Anonymous deployed-header baseline, no overrides or authenticated coverage' : 'Anonymous real public images under local candidate document-header overrides; no AWS changes, account, transaction or deployed-remediation claim', results: [] };
  fs.mkdirSync(output, { recursive: true });
  try {
    const response = await fetch(target + '/api/v1/products?size=1', { signal: AbortSignal.timeout(30000) });
    assert.equal(response.status, 200);
    const product = (await response.json()).data.items[0];
    assert.ok(product?.id, 'Public catalog must not be empty');
    for (const [name, engine] of Object.entries({ chromium, firefox, webkit })) {
      if (!selected.includes(name)) continue;
      const browser = await engine.launch();
      try {
        const context = await browser.newContext({ serviceWorkers: 'block' });
        await context.addInitScript(() => {
          window.candidateStyleViolations = [];
          window.candidateImageFailures = [];
          document.addEventListener('securitypolicyviolation', event => {
            if (event.effectiveDirective.startsWith('style-src')) window.candidateStyleViolations.push(event.effectiveDirective);
          });
          document.addEventListener('error', event => {
            if (event.target instanceof HTMLImageElement) window.candidateImageFailures.push(new URL(event.target.currentSrc || event.target.src, location.href).origin);
          }, true);
        });
        await context.route('**/*', async route => {
          const request = route.request();
          if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method())) return route.abort();
          if (baseline || !request.isNavigationRequest() || new URL(request.url()).origin !== target) return route.continue();
          const response = await route.fetch();
          const headers = { ...response.headers(), 'content-security-policy': policy.SecurityHeadersConfig.ContentSecurityPolicy.ContentSecurityPolicy };
          for (const item of policy.CustomHeadersConfig.Items) headers[item.Header.toLowerCase()] = item.Value;
          return route.fulfill({ response, headers });
        });
        const page = await context.newPage();
        const pageErrors = [];
        const failedRequests = [];
        page.on('pageerror', error => pageErrors.push(error.message));
        page.on('requestfailed', request => failedRequests.push({ origin: new URL(request.url()).origin, type: request.resourceType(), error: request.failure()?.errorText }));
        for (const [surface, resource] of [['catalog', '/'], ['detail', '/products/' + encodeURIComponent(product.id)], ['login', '/login']]) {
          const navigation = await page.goto(target + resource, { waitUntil: 'networkidle', timeout: 45000 });
          try { await page.locator('h1').first().waitFor({ timeout: 10000 }); }
          catch (error) {
            report.results.push({ browser: name, version: browser.version(), surface, status: navigation?.status(), title: await page.title(), bodyTextLength: await page.locator('body').evaluate(body => body.textContent.length), pageErrors, failedRequests });
            throw error;
          }
          const observed = await page.evaluate(async () => {
            await Promise.all([...document.images].map(image => { image.loading = 'eager'; return image.decode().catch(() => {}); }));
            return { isolated: crossOriginIsolated, images: [...document.images].map(image => ({ origin: new URL(image.currentSrc || image.src, location.href).origin, loaded: image.complete && image.naturalWidth > 0 })), styleViolations: window.candidateStyleViolations, imageFailures: window.candidateImageFailures };
          });
          const result = { browser: name, version: browser.version(), surface, status: navigation.status(), ...observed };
          report.results.push(result);
          assert.equal(result.status, 200);
          // WebKit may gain credentialless support; record it, never require its absence.
          if (!baseline && name !== 'webkit') assert.equal(observed.isolated, true);
          assert.deepEqual(observed.styleViolations, []);
          assert.deepEqual(observed.imageFailures, []);
          assert.ok(observed.images.every(image => image.loaded));
          if (surface !== 'login') assert.ok(observed.images.some(image => image.origin === 'https://dcsjrkmummxi7.cloudfront.net'));
          if (name === 'chromium' && surface === 'catalog') await page.screenshot({ path: path.join(output, 'catalog-local-candidate.png'), fullPage: true });
        }
      } finally { await browser.close(); }
    }
    report.status = 'passed';
  } catch (error) { report.status = 'failed'; report.failure = error.message; throw error; }
  finally { fs.writeFileSync(path.join(output, 'public-browser-audit.json'), JSON.stringify(report, null, 2) + '\n'); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
