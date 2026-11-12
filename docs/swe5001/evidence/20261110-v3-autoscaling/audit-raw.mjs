import fs from 'node:fs';
import readline from 'node:readline';
import crypto from 'node:crypto';
const root = '/results/';
const report = JSON.parse(fs.readFileSync(root + 'summary.json', 'utf8'));
const source = fs.createReadStream(root + 'samples.jsonl');
const hash = crypto.createHash('sha256');
source.on('data', chunk => hash.update(chunk));
const minute = new Map();
const totals = { browse_requests: 0, browse_errors: 0, browse_error_samples: 0, browse_latency: 0, dropped_iterations: 0, http_reqs: 0 };
const statuses = {};
let first = null, last = null;
for await (const line of readline.createInterface({ input: source, crlfDelay: Infinity })) {
  if (!line || !['browse_requests', 'browse_errors', 'browse_latency', 'dropped_iterations', 'http_reqs'].some(name => line.includes(`"metric":"${name}"`))) continue;
  const item = JSON.parse(line);
  if (item.type !== 'Point') continue;
  const { time, value, tags } = item.data;
  const key = time.slice(0, 16) + ':00Z';
  if (!minute.has(key)) minute.set(key, { at: key, requests: 0, errors: 0, dropped: 0, latency: [] });
  const row = minute.get(key);
  if (item.metric === 'browse_requests') {
    totals.browse_requests += value; row.requests += value;
    first ??= time; last = time;
  } else if (item.metric === 'browse_errors') {
    totals.browse_errors += value; totals.browse_error_samples++; row.errors += value;
  } else if (item.metric === 'browse_latency') {
    totals.browse_latency++; row.latency.push(value);
  } else if (item.metric === 'dropped_iterations') {
    totals.dropped_iterations += value; row.dropped += value;
  } else if (item.metric === 'http_reqs') {
    totals.http_reqs += value; const status = String(tags?.status ?? 'unknown');
    statuses[status] = (statuses[status] || 0) + value;
  }
}
const stats = fs.readFileSync(root + 'generator.jsonl', 'utf8').trim().split(/\r?\n/).map(JSON.parse);
let gap = 0;
for (let i = 1; i < stats.length; i++) gap = Math.max(gap, Date.parse(stats[i].observedAt) - Date.parse(stats[i-1].observedAt));
function quantile(array, p) {
  if (!array.length) return null;
  array.sort((a,b) => a-b);
  const at = (array.length - 1) * p, low = Math.floor(at), fraction = at - low;
  return array[low] + (array[Math.min(low + 1, array.length - 1)] - array[low]) * fraction;
}
const result = { schema: 1, runLabel: report.context.runLabel, evidenceLevel: 'Local raw/summary consistency audit of requests to AWS; AWS timeline correlation pending',
  samplesSha256: hash.digest('hex'), firstBrowseCompletion: first, lastBrowseCompletion: last,
  totals, httpStatusesIncludingSetup: statuses,
  matchesSummary: totals.browse_requests === report.summary.metrics.browse_requests.values.count &&
    totals.browse_errors === report.summary.metrics.browse_errors.values.passes &&
    totals.browse_error_samples === totals.browse_requests && totals.browse_latency === totals.browse_requests &&
    totals.dropped_iterations === report.summary.metrics.dropped_iterations.values.count &&
    totals.http_reqs === report.summary.metrics.http_reqs.values.count,
  generator: { samples: stats.length, maxGapSeconds: gap/1000,
    maxDockerCpuPercent: Math.max(...stats.map(x => parseFloat(x.stats.CPUPerc))),
    maxMemoryPercentOf8GiBLimit: Math.max(...stats.map(x => parseFloat(x.stats.MemPerc))) },
  minutes: [...minute.values()].sort((a,b) => a.at.localeCompare(b.at)).map(row => ({ at: row.at,
    completed: row.requests, errors: row.errors, dropped: row.dropped, p95Ms: quantile(row.latency, .95) })),
  acceptanceComplete: false, automaticRepeat: false };
fs.writeFileSync(root + 'raw-audit.json', JSON.stringify(result, null, 2), { flag: 'wx' });
console.log(JSON.stringify({ ...result, minutes: undefined }, null, 2));
