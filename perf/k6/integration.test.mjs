import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync, spawn } from 'node:child_process';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { setTimeout } from 'node:timers/promises';
import { baselineResult } from './lib.mjs';

const root = resolve(import.meta.dirname, '../..');
const k6 = 'grafana/k6:1.6.1@sha256:a5ad6bc089a08d77c3ec49f3db8c6fa7a148e4073efcac44c675dbaf3568d8e1';
const node = 'node:24-bookworm';
function docker(args) {
  const result = spawnSync('docker', args, { encoding: 'utf8', timeout: 120000 });
  assert.equal(result.status, 0, result.stderr || result.error?.message);
  return result.stdout.trim();
}
async function run(args) {
  return new Promise((resolve, reject) => {
    const child = spawn('docker', args);
    let output = '';
    child.stdout.on('data', chunk => { output += chunk; });
    child.stderr.on('data', chunk => { output += chunk; });
    child.on('error', reject);
    child.on('close', status => resolve({ status, output }));
  });
}
async function fixture(mode, action) {
  const suffix = `${process.pid}-${Date.now()}`;
  const name = `blocbuy-k6-fixture-${suffix}`;
  const network = `blocbuy-k6-test-${suffix}`;
  const output = mkdtempSync(join(tmpdir(), 'blocbuy-k6-'));
  let passed = false;
  docker(['network', 'create', '--internal', network]);
  try {
    docker(['run', '-d', '--name', name, '--network', network,
      '--mount', `type=bind,source=${root},target=/repo,readonly`,
      '-e', `FIXTURE_MODE=${mode}`, node, 'node', '/repo/perf/k6/fixture.mjs']);
    let ready = false;
    for (let attempt = 0; attempt < 20; attempt++) {
      const result = spawnSync('docker', ['exec', name, 'node', '-e',
        'fetch("http://localhost:8080/stats").then(r=>{if(!r.ok)process.exit(1)}).catch(()=>process.exit(1))'], { timeout: 10000 });
      if (result.status === 0) { ready = true; break; }
      await setTimeout(250);
    }
    assert.ok(ready, 'fixture must become ready');
    await action({ name, output, args: ['run', '--rm', '--network', `container:${name}`,
      '--user', '0:0', '--mount', `type=bind,source=${root},target=/repo,readonly`,
      '--mount', `type=bind,source=${output},target=/results`,
      k6, 'run', '--quiet', '--env', 'BASE_URL=http://localhost:8080',
      '--env', `TEST_SHA=${'a'.repeat(40)}`, '--env', `IMAGE_SHA=${'b'.repeat(40)}`,
      '--env', 'RUN_LABEL=local-generator-contract', '--env', 'SUMMARY_PATH=/results/summary.json',
      '--env', 'PREALLOCATED_VUS=10', '--env', 'MAX_VUS=100'] });
    passed = true;
  } finally {
    // Only the exact test-owned container/network/directory are removed.
    spawnSync('docker', ['rm', '-f', name], { timeout: 30000 });
    spawnSync('docker', ['network', 'rm', network], { timeout: 30000 });
    if (passed) rmSync(output, { recursive: true, force: true });
    else console.error(`Retained local-fixture diagnostics: ${output}`);
  }
}

function counts(output) {
  try {
    const report = JSON.parse(readFileSync(join(output, 'summary.json'), 'utf8'));
    return JSON.stringify({ durationMs: report.summary?.state?.testRunDurationMs,
      metrics: Object.fromEntries(Object.entries(report.summary?.metrics || {}).filter(([key]) =>
        key.startsWith('browse_requests') || key === 'dropped_iterations').map(([key, metric]) => [key, metric.values])) });
  } catch (_) { return 'No parseable fixture summary'; }
}

test('real k6 runner emits legacy metrics and the per-ten-request 70/30 GET mix', { timeout: 120000 }, async () => {
  await fixture('good', async ({ name, output, args }) => {
    const result = await run([...args, '--env', 'MODE=smoke', '--env', 'TOPOLOGY=local-fixture', '/repo/perf/k6/browse.js']);
    assert.equal(result.status, 0, result.output);
    const report = JSON.parse(readFileSync(join(output, 'summary.json'), 'utf8'));
    const count = report.summary.metrics.browse_requests.values.count;
    // Constant-arrival-rate may include the exact duration boundary (t=0..10).
    assert.ok(count === 20 || count === 21, `expected 20 requests plus at most one boundary request, got ${count}`);
    assert.equal(report.summary.metrics.browse_errors.values.rate, 0);
    assert.equal(report.context.mode, 'smoke');
    assert.throws(() => baselineResult(report));
    const stats = JSON.parse(docker(['exec', name, 'node', '-e', 'fetch("http://localhost:8080/stats").then(r=>r.text()).then(console.log)']));
    assert.deepEqual(stats, { list: count - 6 + 1, detail: 6, other: 0 }); // one setup GET; an optional trailing list request
  });
});
test('real k6 refuses a broken setup envelope', { timeout: 120000 }, async () => {
  await fixture('bad-setup', async ({ args }) => {
    const result = await run([...args, '--env', 'MODE=smoke', '--env', 'TOPOLOGY=local-fixture', '/repo/perf/k6/browse.js']);
    assert.notEqual(result.status, 0);
    assert.match(result.output, /no usable Result/);
  });
});
test('real k6 fails thresholds for 200 error envelopes during the workload', { timeout: 120000 }, async () => {
  await fixture('bad-runtime', async ({ output, args }) => {
    const result = await run([...args, '--env', 'MODE=smoke', '--env', 'TOPOLOGY=local-fixture', '/repo/perf/k6/browse.js']);
    assert.notEqual(result.status, 0, result.output);
    const report = JSON.parse(readFileSync(join(output, 'summary.json'), 'utf8'));
    assert.equal(report.summary.metrics.browse_errors.values.rate, 1);
  });
});
test('fixed budget SQL executes on PostgreSQL 18 in a read-only transaction', { timeout: 120000 }, async () => {
  const name = `blocbuy-budget-sql-${process.pid}-${Date.now()}`;
  try {
    docker(['run', '--rm', '-d', '--name', name, '--network', 'none',
      '-e', 'POSTGRES_HOST_AUTH_METHOD=trust', 'postgres:18']);
    let ready = false;
    for (let attempt = 0; attempt < 30; attempt++) {
      // initdb temporarily exposes a Unix socket, then restarts. TCP becomes
      // ready only on the final server, avoiding that transient readiness race.
      const result = spawnSync('docker', ['exec', name, 'pg_isready', '-h', '127.0.0.1', '-U', 'postgres'], { timeout: 10000 });
      if (result.status === 0) { ready = true; break; }
      await setTimeout(250);
    }
    assert.ok(ready);
    const source = readFileSync(join(root, 'infra/swe5001/scripts/connection-budget.sh'), 'utf8');
    const sql = source.match(/SELECT json_build_object\([\s\S]*?;/)?.[0];
    assert.ok(sql, 'exercise the actual fixed settings query');
    const result = docker(['exec', '-e', 'PGOPTIONS=-c default_transaction_read_only=on', name,
      'psql', '--no-psqlrc', '-h', '127.0.0.1', '-U', 'postgres', '-d', 'postgres', '-At', '-c', sql]);
    const settings = JSON.parse(result);
    assert.ok(settings.maxConnections > settings.reservedConnections);
    assert.ok(Number.isInteger(settings.reservedConnections));
    assert.equal(docker(['exec', '-e', 'PGOPTIONS=-c default_transaction_read_only=on', name,
      'psql', '--no-psqlrc', '-h', '127.0.0.1', '-U', 'postgres', '-d', 'postgres', '-At', '-c', 'SHOW default_transaction_read_only;']), 'on');
  } finally {
    spawnSync('docker', ['rm', '-fv', name], { timeout: 30000 });
  }
});
test('compressed local V2 exercises all plateaus but cannot produce deployment calibration', { timeout: 120000 }, async () => {
  await fixture('good', async ({ output, args }) => {
    const result = await run([...args, '--env', 'MODE=v2', '--env', 'TOPOLOGY=monolith',
      '--env', `FIXTURE_STAGE_SECONDS=${process.env.CONTRACT_STAGE_SECONDS || 1}`, '/repo/perf/k6/browse.js']);
    assert.equal(result.status, 0, `${result.output}\n${counts(output)}`);
    const reportPath = join(output, 'summary.json');
    const report = JSON.parse(readFileSync(reportPath, 'utf8'));
    const candidate = baselineResult(report);
    assert.equal(candidate.plateaus.length, 10);
    assert.equal(candidate.hasAwsPreflight, false);
    const cli = spawnSync(process.execPath, [join(root, 'perf/k6/calibrate.mjs'), reportPath], { encoding: 'utf8' });
    assert.equal(cli.status, 1);
    assert.match(cli.stderr, /fixture reports cannot produce deployment calibration/);
    assert.equal(cli.stdout, '');
  });
});
test('full-duration V2 materializes every plateau and converts the candidate unit', {
  skip: process.env.RUN_FULL_V2 !== '1', timeout: 900000,
}, async () => {
  await fixture('good', async ({ output, args }) => {
    const defaults = args.map(arg => arg === 'PREALLOCATED_VUS=10' ? 'PREALLOCATED_VUS=100'
      : arg === 'MAX_VUS=100' ? 'MAX_VUS=400' : arg);
    const result = await run([...defaults, '--env', 'MODE=v2', '--env', 'TOPOLOGY=monolith', '/repo/perf/k6/browse.js']);
    console.log(`Full V2 fixture counters: ${counts(output)}`);
    assert.equal(result.status, 0, `${result.output}\n${counts(output)}`);
    const report = JSON.parse(readFileSync(join(output, 'summary.json'), 'utf8'));
    const candidate = baselineResult(report);
    assert.equal(candidate.plateaus.length, 10);
    assert.equal(candidate.requestsPerTargetPerMinute, 12000);
    assert.deepEqual(report.context.generator, { preAllocatedVUs: 100, maxVUs: 400 });
    assert.equal(candidate.hasAwsPreflight, false);
    // Fixture throughput is never persisted as application/AWS capacity evidence.
  });
});
