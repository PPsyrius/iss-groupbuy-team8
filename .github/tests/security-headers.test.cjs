const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { test } = require('node:test');

const root = path.resolve(__dirname, '../..');
const source = JSON.parse(fs.readFileSync(path.join(root, 'deployment/frontend/cloudfront/response-headers-policy.json'), 'utf8'));
const { expectedHeaders, verifyHeaders, withBrowserEvidence } = require('../scripts/verify-dev-edge.cjs');

function render(policy) {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'blocbuy-headers-'));
  try {
    const file = path.join(directory, 'policy.json');
    fs.writeFileSync(file, JSON.stringify(policy));
    return spawnSync(process.execPath, [path.join(root, 'fullstack-localhost/cloudfront-headers.mjs'), file], { encoding: 'utf8' });
  } finally {
    fs.rmSync(directory, { recursive: true, force: true });
  }
}

test('canonical policy produces the actual Caddy header values', () => {
  const result = render(source);
  assert.equal(result.status, 0, result.stderr);
  for (const item of source.CustomHeadersConfig.Items) {
    assert.ok(result.stdout.includes(`\t${item.Header} ${JSON.stringify(item.Value)}\n`));
  }
  const expectedCsp = source.SecurityHeadersConfig.ContentSecurityPolicy.ContentSecurityPolicy
    .split(';').map(x => x.trim()).filter(x => x && x !== 'upgrade-insecure-requests').join('; ');
  assert.ok(result.stdout.includes(`\tContent-Security-Policy ${JSON.stringify(expectedCsp)}\n`));
  assert.ok(result.stdout.includes('\tdefer\n'));
  assert.ok(result.stdout.includes('\t-Server\n'));
  assert.ok(!result.stdout.includes('Strict-Transport-Security'));
});

test('multiple custom headers are rendered, not just the first entry', () => {
  const policy = structuredClone(source);
  policy.CustomHeadersConfig.Items.push({ Header: 'X-Policy-Test', Value: 'second value', Override: true });
  policy.CustomHeadersConfig.Quantity = 2;
  const result = render(policy);
  assert.equal(result.status, 0, result.stderr);
  assert.ok(result.stdout.includes('\tX-Policy-Test "second value"\n'));
  assert.ok(result.stdout.includes('\tPermissions-Policy '));
});

test('an empty custom-header set still renders the security controls', () => {
  const policy = structuredClone(source);
  policy.CustomHeadersConfig = { Quantity: 0, Items: [] };
  const result = render(policy);
  assert.equal(result.status, 0, result.stderr);
  assert.ok(result.stdout.includes('\tX-Frame-Options "DENY"\n'));
  assert.ok(!result.stdout.includes('Permissions-Policy'));
});

test('an invalid policy fails instead of generating a partial header block', () => {
  const result = render({});
  assert.notEqual(result.status, 0);
  assert.equal(result.stdout, '');
});

test('post-apply verifier accepts all exact values and rejects drift or missing headers', () => {
  const headers = expectedHeaders(source);
  verifyHeaders(headers, source);
  for (const name of Object.keys(headers)) {
    const missing = { ...headers };
    delete missing[name];
    assert.throws(() => verifyHeaders(missing, source), new RegExp(name));
    assert.throws(() => verifyHeaders({ ...headers, [name]: 'wrong' }, source), new RegExp(name));
  }
});

for (const target of [undefined, 'https://unreviewed.example']) {
  test(`invalid edge target is recorded before browser launch: ${target ?? 'missing'}`, () => {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'blocbuy-edge-target-'));
    try {
      const moduleDirectory = path.join(directory, 'node_modules/@playwright/test');
      fs.mkdirSync(moduleDirectory, { recursive: true });
      fs.writeFileSync(path.join(moduleDirectory, 'index.js'), "exports.chromium = { launch() { throw new Error('Browser must not launch'); } };\n");
      const env = { ...process.env };
      if (target === undefined) delete env.EDGE_BASE_URL;
      else env.EDGE_BASE_URL = target;
      const result = spawnSync(process.execPath, [
        path.join(root, '.github/scripts/verify-dev-edge.cjs'),
        path.join(root, 'deployment/frontend/cloudfront/response-headers-policy.json'),
        path.join(directory, 'evidence'),
      ], { cwd: directory, env, encoding: 'utf8' });
      assert.equal(result.status, 1);
      const report = JSON.parse(fs.readFileSync(path.join(directory, 'evidence/verification.json'), 'utf8'));
      assert.equal(report.status, 'failed');
      assert.match(report.failure, /Only the reviewed dev origin is supported/);
      assert.doesNotMatch(result.stderr, /Browser must not launch/);
    } finally {
      fs.rmSync(directory, { recursive: true, force: true });
    }
  });
}

for (const scenario of [
  { name: 'success' },
  { name: 'launch failure', launchError: true },
  { name: 'verification failure', verifyError: true },
  { name: 'shutdown failure', closeError: true },
  { name: 'verification and shutdown failures', verifyError: true, closeError: true },
]) {
  test(`browser evidence is retained on ${scenario.name}`, async () => {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'blocbuy-edge-evidence-'));
    const report = { status: 'failed' };
    let verified = false;
    let closed = false;
    try {
      const run = withBrowserEvidence(async () => {
        if (scenario.launchError) throw new Error('launch failed');
        return { close: async () => {
          closed = true;
          if (scenario.closeError) throw new Error('shutdown failed');
        } };
      }, async () => {
        verified = true;
        if (scenario.verifyError) throw new Error('verification failed');
      }, report, directory);
      const failure = scenario.launchError ? 'launch failed' : scenario.verifyError ? 'verification failed' : scenario.closeError ? 'shutdown failed' : undefined;
      if (failure) await assert.rejects(run, { message: failure });
      else await run;
      const saved = JSON.parse(fs.readFileSync(path.join(directory, 'verification.json'), 'utf8'));
      assert.equal(saved.status, failure ? 'failed' : 'passed');
      assert.equal(saved.failure, scenario.launchError ? 'launch failed' : scenario.verifyError ? 'verification failed' : undefined);
      assert.equal(saved.cleanupFailure, scenario.closeError ? 'shutdown failed' : undefined);
      assert.equal(verified, !scenario.launchError);
      assert.equal(closed, !scenario.launchError);
    } finally {
      fs.rmSync(directory, { recursive: true, force: true });
    }
  });
}
