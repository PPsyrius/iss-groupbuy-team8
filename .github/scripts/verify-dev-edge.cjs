// Read-only post-apply check. Run from frontend/ inside its Docker build-stage image.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

function expectedHeaders(policy) {
  const s = policy.SecurityHeadersConfig;
  const h = s.StrictTransportSecurity;
  return {
    'content-security-policy': s.ContentSecurityPolicy.ContentSecurityPolicy,
    'strict-transport-security': `max-age=${h.AccessControlMaxAgeSec}${h.IncludeSubdomains ? '; includeSubDomains' : ''}${h.Preload ? '; preload' : ''}`,
    'x-content-type-options': 'nosniff',
    'x-frame-options': s.FrameOptions.FrameOption,
    'referrer-policy': s.ReferrerPolicy.ReferrerPolicy,
    ...Object.fromEntries(policy.CustomHeadersConfig.Items.map(h => [h.Header.toLowerCase(), h.Value])),
  };
}

function verifyHeaders(actual, policy) {
  for (const [name, value] of Object.entries(expectedHeaders(policy))) {
    assert.equal(actual[name], value, `Unexpected or missing ${name}`);
  }
}

async function withBrowserEvidence(launch, verify, report, outputDirectory) {
  fs.mkdirSync(outputDirectory, { recursive: true });
  let browser;
  let verificationFailed = false;
  try {
    browser = await launch();
    await verify(browser);
    report.status = 'passed';
  } catch (error) {
    verificationFailed = true;
    report.status = 'failed';
    report.failure = error.message;
    throw error;
  } finally {
    try {
      await browser?.close();
    } catch (error) {
      report.status = 'failed';
      report.cleanupFailure = error.message;
      // Preserve the original launch/check error if shutdown also fails.
      if (!verificationFailed) throw error;
    } finally {
      fs.writeFileSync(path.join(outputDirectory, 'verification.json'), JSON.stringify(report, null, 2) + '\n');
    }
  }
}

async function main() {
  const { chromium } = require(path.join(process.cwd(), 'node_modules/@playwright/test'));
  const [policyPath, outputDirectory] = process.argv.slice(2);
  assert.ok(policyPath && outputDirectory, 'Pass canonical policy path and output directory');
  const target = process.env.EDGE_BASE_URL;
  assert.equal(target, 'https://d1b3np88z657cp.cloudfront.net', 'Only the reviewed dev origin is supported');
  const policy = JSON.parse(fs.readFileSync(policyPath, 'utf8'));
  const report = { target, checkedAtUtc: new Date().toISOString(), scope: 'Read-only anonymous post-apply check; no real account or transaction coverage', checks: [], status: 'failed' };
  await withBrowserEvidence(() => chromium.launch(), async browser => {
    const context = await browser.newContext({ serviceWorkers: 'block' });
    await context.route('**/*', route => ['GET', 'HEAD', 'OPTIONS'].includes(route.request().method()) ? route.continue() : route.abort());
    for (const resource of ['/', '/api/v1/products?size=1']) {
      const response = await context.request.get(target + resource, { timeout: 30000, maxRedirects: 0 });
      assert.equal(response.status(), 200, `Unexpected status for ${resource}`);
      verifyHeaders(response.headers(), policy);
      report.checks.push({ resource, exactHeaders: true });
    }
    const page = await context.newPage();
    // Failed images can be removed by the fallback component before DOM inspection.
    // Retain their origins independently without retaining URL paths or query strings.
    await page.addInitScript(() => {
      window.__edgeImageFailures = [];
      document.addEventListener('error', event => {
        if (event.target instanceof HTMLImageElement) {
          window.__edgeImageFailures.push(new URL(event.target.currentSrc || event.target.src, location.href).origin);
        }
      }, true);
    });
    await page.goto(target, { waitUntil: 'networkidle', timeout: 30000 });
    const link = page.locator('a[href^="/products/"]').first();
    await link.waitFor({ timeout: 15000 });
    const href = await link.getAttribute('href');
    assert.ok(href && new URL(href, target).origin === target);
    async function checkImages(surface) {
      const images = await page.evaluate(async () => {
        await Promise.all([...document.images].map(image => {
          image.loading = 'eager';
          return image.decode().catch(() => {});
        }));
        return [...document.images].map(image => ({ origin: new URL(image.currentSrc || image.src, location.href).origin, loaded: image.complete && image.naturalWidth > 0 }));
      });
      const failedOrigins = await page.evaluate(() => window.__edgeImageFailures);
      assert.deepEqual(failedOrigins, [], `${surface}: an image failed, even if its fallback removed it`);
      assert.ok(images.some(i => i.origin === 'https://dcsjrkmummxi7.cloudfront.net'), `${surface}: no product images covered`);
      assert.ok(images.every(i => i.loaded), `${surface}: image failed to load`);
      report.checks.push({ surface, loadedImageCount: images.length, imageOrigins: [...new Set(images.map(i => i.origin))] });
      await page.screenshot({ path: path.join(outputDirectory, `${surface}.png`), fullPage: true });
    }
    await checkImages('catalog');
    await page.goto(new URL(href, target).href, { waitUntil: 'networkidle', timeout: 30000 });
    await checkImages('detail');
    const probe = 'https://unapproved.example.test/edge-policy-probe.svg';
    let requested = false;
    await context.route(probe, route => {
      requested = true;
      return route.fulfill({ contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="1" height="1"/>' });
    });
    const directive = await page.evaluate(url => new Promise(resolve => {
      const timer = setTimeout(() => resolve('timeout'), 5000);
      document.addEventListener('securitypolicyviolation', event => {
        if (event.blockedURI === url) { clearTimeout(timer); resolve(event.effectiveDirective); }
      });
      const image = new Image();
      image.addEventListener('load', () => { clearTimeout(timer); resolve('not-blocked'); });
      image.src = url;
      document.body.append(image);
    }), probe);
    assert.equal(directive, 'img-src');
    assert.equal(requested, false, 'Unapproved image was requested');
    report.checks.push({ unapprovedImageBlockedBeforeRequest: true });
  }, report, outputDirectory);
}

module.exports = { expectedHeaders, verifyHeaders, withBrowserEvidence };
if (require.main === module) main().catch(error => { console.error(error.message); process.exitCode = 1; });
