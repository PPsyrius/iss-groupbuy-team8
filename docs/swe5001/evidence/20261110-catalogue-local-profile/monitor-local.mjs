// Local diagnostics only: selected container resources and low-cardinality Prometheus metrics.
import http from 'node:http';
import { appendFileSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
const names = ['commerce', 'postgres', 'redis', 'generator'].map(n => `blocbuy-profile-${n}-20261110`);
const end = Date.now() + 270000;
const previous = new Map();
function get(options) {
  return new Promise((resolve, reject) => {
    const request = http.get(options, response => {
      let body = '';
      response.setEncoding('utf8');
      response.on('data', chunk => { body += chunk; });
      response.on('end', () => response.statusCode === 200 ? resolve(body) : reject(new Error(`HTTP ${response.statusCode}`)));
    });
    request.setTimeout(6000, () => request.destroy(new Error('Diagnostic read timed out')));
    request.on('error', reject);
  });
}
let sample = 0;
while (Date.now() < end) {
  const at = new Date().toISOString();
  const resources = await Promise.all(names.map(async name => {
    try {
      const raw = JSON.parse(await get({ socketPath: '/var/run/docker.sock', path: `/v1.44/containers/${name}/stats?stream=false&one-shot=true` }));
      const prior = previous.get(name);
      const current = { cpu: raw.cpu_stats.cpu_usage.total_usage, system: raw.cpu_stats.system_cpu_usage };
      previous.set(name, current);
      const cpuPercentOneCore = prior && current.system > prior.system
        ? (current.cpu - prior.cpu) / (current.system - prior.system) * raw.cpu_stats.online_cpus * 100 : null;
      return { name, cpuPercentOneCore, memoryBytes: raw.memory_stats.usage, memoryLimitBytes: raw.memory_stats.limit,
        throttling: raw.cpu_stats.throttling_data, pids: raw.pids_stats.current };
    } catch (error) { return { name, error: error.message }; }
  }));
  let metrics;
  try {
    metrics = await get('http://127.0.0.1:8589/api/actuator/prometheus');
    writeFileSync(`/results/prometheus-${String(sample).padStart(3, '0')}.txt`, metrics);
  } catch (error) { metrics = `ERROR ${error.message}`; }
  const sql = "SELECT json_build_object('connections',(SELECT count(*) FROM pg_stat_activity WHERE datname=current_database()),'queries',(SELECT json_agg(x) FROM (SELECT queryid,calls,total_exec_time,mean_exec_time,rows,shared_blks_hit,shared_blks_read FROM pg_stat_statements ORDER BY total_exec_time DESC LIMIT 12) x));";
  const database = spawnSync('docker', ['exec', 'blocbuy-profile-postgres-20261110', 'psql', '-U', 'blocbuy_profile', '-d', 'Blocbuy', '-tAX', '-c', sql], { encoding: 'utf8', timeout: 8000 });
  appendFileSync('/results/local-monitor.jsonl', JSON.stringify({ at, sample, resources,
    prometheusAvailable: !metrics.startsWith('ERROR'), database: database.status === 0 ? JSON.parse(database.stdout) : { error: 'Local database diagnostics failed' } }) + '\n');
  sample++;
  await new Promise(resolve => setTimeout(resolve, 5000));
}
console.log(`Local diagnostic monitor finished: ${sample} samples`);
