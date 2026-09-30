import http from 'node:http';
import { performance } from 'node:perf_hooks';
import { setTimeout as sleep } from 'node:timers/promises';

// Credentials arrive on stdin from the test-only Java fixture and are never printed or saved.
let input = '';
for await (const chunk of process.stdin) input += chunk;
const config = JSON.parse(input);
const { baseUrl, users, productId, dealId } = config;
const vus = config.vus ?? 100;
const warmupSeconds = config.warmupSeconds ?? 10;
const durationSeconds = config.durationSeconds ?? 60;
const thinkMs = config.thinkMs ?? 200;
if (!Number.isInteger(vus) || vus < 1 || users.length < vus ||
    ![warmupSeconds, durationSeconds, thinkMs].every(Number.isFinite) ||
    warmupSeconds < 0 || durationSeconds <= 0 || thinkMs < 0) {
  throw new Error('Invalid workload configuration');
}
const agent = new http.Agent({ keepAlive: true, maxSockets: vus });
const endpoints = {
  browse: [
    { path: '/api/v1/products?page=1&size=20', valid: data => data.items?.length > 0 },
    { path: '/api/v1/products/' + productId, valid: data => data.id === productId },
  ],
  join_intent: [{
    path: '/api/v1/payments/intents', method: 'POST',
    body: JSON.stringify({ operation: 'JOIN', businessId: dealId }),
    valid: data => typeof data.intentId === 'string',
  }],
  my_orders: [{
    path: '/api/v1/orders/me?page=1&size=20',
    valid: (data, user) => data.total === 10 && data.items?.length === 10 &&
      data.items.every(item => user.orderNos.includes(item.orderNo)),
  }],
};

async function request(endpoint, user) {
  return new Promise(resolve => {
    const headers = endpoint.body ? {
      'Content-Type': 'application/json', 'X-BlocBuy-Request': 'checkout',
    } : {};
    if (user) headers['blocbuy-token'] = user.token;
    const req = http.request(new URL(endpoint.path, baseUrl), {
      method: endpoint.method ?? 'GET', agent, headers,
    }, res => {
      let body = '';
      res.setEncoding('utf8');
      res.on('data', chunk => { body += chunk; });
      res.on('end', () => {
        let ok = false;
        try {
          const payload = JSON.parse(body);
          ok = res.statusCode === 200 && payload.code === 'COMMON_200_000' &&
            endpoint.valid(payload.data ?? {}, user);
        } catch {}
        resolve({ ok, status: res.statusCode });
      });
      res.on('error', () => resolve({
        ok: false, status: 'response_error',
      }));
    });
    req.on('error', () => resolve({
      ok: false, status: 'network_error',
    }));
    req.setTimeout(5000, () => req.destroy());
    if (endpoint.body) req.write(endpoint.body);
    req.end();
  });
}

async function phase(name, seconds, measured) {
  const start = performance.now();
  const deadline = start + seconds * 1000;
  const samples = [];
  let iterations = 0;
  const statuses = {};
  await Promise.all(Array.from({ length: vus }, async (_, index) => {
    while (performance.now() < deadline) {
      // A browse action is list -> detail. Its latency includes both responses and parsing.
      const actionStart = performance.now();
      let ok = true;
      for (const endpoint of endpoints[name]) {
        const result = await request(endpoint, name === 'browse' ? null : users[index]);
        ok = result.ok && ok;
        statuses[result.status] = (statuses[result.status] ?? 0) + 1;
      }
      if (measured) samples.push({ ms: performance.now() - actionStart, ok });
      iterations++;
      await sleep(thinkMs);
    }
  }));
  const elapsedSeconds = (performance.now() - start) / 1000;
  if (!measured) return { iterations, statuses };
  const ordered = samples.map(sample => sample.ms).sort((a, b) => a - b);
  const percentile = p => ordered[Math.max(0, Math.ceil(p * ordered.length) - 1)] ?? null;
  const errors = samples.filter(sample => !sample.ok).length;
  return {
    vus, configuredSeconds: seconds, elapsedSeconds, actions: samples.length,
    p50Ms: percentile(0.5), p95Ms: percentile(0.95), p99Ms: percentile(0.99),
    errors, errorRate: errors / samples.length,
    actionsPerSecond: samples.length / elapsedSeconds,
    requestsPerSecond: samples.length * endpoints[name].length / elapsedSeconds,
    statuses, p95TargetMet: percentile(0.95) < 2000,
  };
}

const summary = {
  startedAt: new Date().toISOString(), node: process.version,
  model: 'closed-loop, sequential scenarios, one session per VU',
  vus, warmupSeconds, durationSeconds, thinkMs, scenarios: {},
};
try {
  for (const name of Object.keys(endpoints)) {
    process.stderr.write('Starting ' + name + ' with ' + vus + ' VUs\n');
    const warmup = await phase(name, warmupSeconds, false);
    const measured = await phase(name, durationSeconds, true);
    summary.scenarios[name] = { warmup, ...measured };
  }
  summary.finishedAt = new Date().toISOString();
  process.stdout.write(JSON.stringify(summary, null, 2) + '\n');
  // Missed latency targets remain reportable measurements; broken scenarios fail the run.
  if (Object.values(summary.scenarios).some(s => !s.actions || s.errors > 0)) process.exitCode = 1;
} finally {
  agent.destroy();
}
