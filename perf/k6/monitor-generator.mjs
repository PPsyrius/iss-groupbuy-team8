import { spawnSync } from 'node:child_process';
import { appendFileSync, existsSync, writeFileSync } from 'node:fs';
import { stripVTControlCharacters } from 'node:util';
import { setTimeout } from 'node:timers/promises';

try {
  const [name, output, ...extra] = process.argv.slice(2);
  if (!name || !/^[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127}$/.test(name) || !output || extra.length || existsSync(output)) {
    throw new Error('Usage: node monitor-generator.mjs container-name new-output.jsonl');
  }
  function docker(args) {
    const result = spawnSync('docker', args, { encoding: 'utf8', timeout: 15000, windowsHide: true });
    if (result.error || result.status !== 0) throw new Error(result.error?.message || result.stderr || 'Docker read failed');
    return stripVTControlCharacters(result.stdout).trim();
  }
  const deadline = Date.now() + 3660000;
  const startDeadline = Date.now() + 30000;
  let samples = 0;
  writeFileSync(output, '', { flag: 'wx', mode: 0o600 });
  while (Date.now() < deadline) {
    const status = docker(['inspect', '--format', '{{.State.Status}}', name]);
    if (status === 'created' && !samples && Date.now() < startDeadline) { await setTimeout(250); continue; }
    if (status !== 'running') {
      if (!samples) throw new Error('Container never reached running state within the startup window');
      break;
    }
    const stats = JSON.parse(docker(['stats', '--no-stream', '--format', '{{json .}}', name]));
    for (const key of ['CPUPerc', 'MemPerc']) {
      if (typeof stats[key] !== 'string' || !/^\d+(?:\.\d+)?%$/.test(stats[key])) throw new Error(`Invalid ${key} resource sample`);
    }
    appendFileSync(output, JSON.stringify({ observedAt: new Date().toISOString(), stats }) + '\n');
    if (++samples === 1) process.stderr.write('First CPU/memory sample saved successfully\n');
    await setTimeout(2000);
  }
  if (Date.now() >= deadline) throw new Error('Resource monitoring reached its bounded deadline; load completion is unverified');
  process.stderr.write(`Saved ${samples} generator samples; no container was stopped or removed\n`);
} catch (error) {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 1;
}
