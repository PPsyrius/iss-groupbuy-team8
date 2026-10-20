import test from 'node:test';
import assert from 'node:assert/strict';
import { spawn, spawnSync } from 'node:child_process';
import { mkdtempSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, join } from 'node:path';
import { setTimeout } from 'node:timers/promises';

const script = resolve(import.meta.dirname, 'monitor-generator.mjs');

test('native generator monitor waits for creation/start, records parseable CPU/memory and preserves its container', { timeout: 60000 }, async () => {
  const dir = mkdtempSync(join(tmpdir(), 'blocbuy-monitor-'));
  const output = join(dir, 'stats.jsonl');
  const name = `blocbuy-monitor-${process.pid}-${Date.now()}`;
  const docker = args => {
    const result = spawnSync('docker', args, { encoding: 'utf8', timeout: 30000 });
    assert.equal(result.status, 0, result.stderr || result.error?.message);
    return result.stdout.trim();
  };
  let passed = false;
  try {
    docker(['create', '--name', name, '--network', 'none', 'node:24-bookworm', 'node', '-e', 'setTimeout(()=>{},10000)']);
    const child = spawn(process.execPath, [script, name, output]);
    let log = '';
    child.stdout.on('data', chunk => { log += chunk; });
    child.stderr.on('data', chunk => { log += chunk; });
    const done = new Promise((resolve, reject) => { child.on('error', reject); child.on('close', resolve); });
    await setTimeout(500);
    docker(['start', '-a', name]);
    assert.equal(await done, 0, log);
    const rows = readFileSync(output, 'utf8').trim().split(/\r?\n/).map(JSON.parse);
    assert.ok(rows.length >= 2);
    assert.ok(rows.every(row => Number.isFinite(Date.parse(row.observedAt)) && /^\d+(?:\.\d+)?%$/.test(row.stats.CPUPerc) && /^\d+(?:\.\d+)?%$/.test(row.stats.MemPerc)));
    assert.equal(docker(['inspect', '--format', '{{.State.Status}}', name]), 'exited');
    writeFileSync(output, 'preserve original evidence');
    const duplicate = spawnSync(process.execPath, [script, name, output], { encoding: 'utf8' });
    assert.equal(duplicate.status, 1);
    assert.equal(readFileSync(output, 'utf8'), 'preserve original evidence');
    passed = true;
  } finally {
    spawnSync('docker', ['rm', '-f', name], { timeout: 30000 });
    if (passed) rmSync(dir, { recursive: true, force: true });
    else console.error(`Retained monitor diagnostics: ${dir}`);
  }
});

test('monitor rejects missing/invalid targets rather than producing accepted empty evidence', () => {
  assert.equal(spawnSync(process.execPath, [script], { encoding: 'utf8' }).status, 1);
  const dir = mkdtempSync(join(tmpdir(), 'blocbuy-monitor-negative-'));
  try {
    const result = spawnSync(process.execPath, [script, 'blocbuy-no-such-monitor-container', join(dir, 'stats.jsonl')], { encoding: 'utf8', timeout: 30000 });
    assert.equal(result.status, 1);
    assert.match(result.stderr, /No such|not found/i);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});
