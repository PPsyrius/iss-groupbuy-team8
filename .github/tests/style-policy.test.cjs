const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { test } = require('node:test');

const root = path.resolve(__dirname, '../..');
const policy = JSON.parse(fs.readFileSync(path.join(root, 'deployment/frontend/cloudfront/response-headers-policy.json'), 'utf8'));
const press = '@layer {\n  [${PRESSABLE_ATTRIBUTE}] {\n    touch-action: pan-x pan-y pinch-zoom;\n  }\n}';
const scroll = '@layer {\n  * {\n    overscroll-behavior: contain;\n  }\n}';

for (const scenario of ['approved', 'changed CSS', 'unexpected interpolation', 'missing injection', 'unsafe-inline', 'missing hash', 'extra hash', 'extra origin', 'unsafe-hashes', 'style attribute permission', 'missing attribute denial', 'element override', 'uppercase element override', 'duplicate style source', 'duplicate attribute directive']) {
  test(`style dependency guard: ${scenario}`, () => {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'blocbuy-style-policy-'));
    function write(file, content) {
      const target = path.join(directory, file);
      fs.mkdirSync(path.dirname(target), { recursive: true });
      fs.writeFileSync(target, content);
    }
    try {
      write('frontend/scripts/verify-csp-styles.mjs', fs.readFileSync(path.join(root, 'frontend/scripts/verify-csp-styles.mjs')));
      write('frontend/node_modules/@heroui/react/index.js', '');
      write('frontend/node_modules/react-aria/package.json', JSON.stringify({ main: 'dist/exports/index.cjs' }));
      write('frontend/node_modules/react-aria/dist/exports/index.cjs', '');
      let css = press;
      if (scenario === 'changed CSS') css = css.replace('pinch-zoom', 'none');
      if (scenario === 'unexpected interpolation') css += '${unreviewed}';
      write('frontend/node_modules/react-aria/dist/private/interactions/usePress.mjs', scenario === 'missing injection' ? '' : `const PRESSABLE_ATTRIBUTE = 'data-react-aria-pressable';\nstyle.textContent = \`${css}\`.trim();`);
      write('frontend/node_modules/react-aria/dist/private/overlays/usePreventScroll.mjs', `style.textContent = \`${scroll}\`.trim();`);
      const candidate = structuredClone(policy);
      const csp = candidate.SecurityHeadersConfig.ContentSecurityPolicy;
      if (scenario === 'unsafe-inline') csp.ContentSecurityPolicy = csp.ContentSecurityPolicy.replace("style-src 'self'", "style-src 'self' 'unsafe-inline'");
      if (scenario === 'missing hash') csp.ContentSecurityPolicy = csp.ContentSecurityPolicy.replace(/ 'sha256-[^']+'/g, '');
      if (scenario === 'extra hash') csp.ContentSecurityPolicy = csp.ContentSecurityPolicy.replace("style-src 'self'", "style-src 'self' 'sha256-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA='");
      if (scenario === 'extra origin') csp.ContentSecurityPolicy = csp.ContentSecurityPolicy.replace("style-src 'self'", "style-src 'self' https://unreviewed.example");
      if (scenario === 'unsafe-hashes') csp.ContentSecurityPolicy = csp.ContentSecurityPolicy.replace("style-src 'self'", "style-src 'self' 'unsafe-hashes'");
      if (scenario === 'style attribute permission') csp.ContentSecurityPolicy = csp.ContentSecurityPolicy.replace("style-src-attr 'none'", "style-src-attr 'unsafe-inline'");
      if (scenario === 'missing attribute denial') csp.ContentSecurityPolicy = csp.ContentSecurityPolicy.replace("style-src-attr 'none';", '');
      if (scenario === 'element override') csp.ContentSecurityPolicy += "; style-src-elem 'unsafe-inline'";
      if (scenario === 'uppercase element override') csp.ContentSecurityPolicy += "; STYLE-SRC-ELEM 'unsafe-inline'";
      if (scenario === 'duplicate style source') csp.ContentSecurityPolicy += "; style-src 'unsafe-inline'";
      if (scenario === 'duplicate attribute directive') csp.ContentSecurityPolicy += "; style-src-attr 'unsafe-inline'";
      write('deployment/frontend/cloudfront/response-headers-policy.json', JSON.stringify(candidate));
      const result = spawnSync(process.execPath, [path.join(directory, 'frontend/scripts/verify-csp-styles.mjs')], { encoding: 'utf8' });
      if (scenario === 'approved') assert.equal(result.status, 0, result.stderr);
      else assert.notEqual(result.status, 0, 'Unreviewed style policy must fail the build guard');
    } finally {
      fs.rmSync(directory, { recursive: true, force: true });
    }
  });
}
