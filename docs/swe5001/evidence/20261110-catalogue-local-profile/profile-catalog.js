// Local diagnostic only. Never an AWS V2/V3 acceptance campaign.
import http from 'k6/http';
import exec from 'k6/execution';
import { fail } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { productIds, validResponse, browseChoice } from '/repo/perf/k6/lib.mjs';

const base = 'http://127.0.0.1:8589'; // k6 shares only the local commerce container's network namespace.
const requests = new Counter('browse_requests');
const errors = new Rate('browse_errors');
const latency = new Trend('browse_latency', true);
const plan = [
  { name: 'warmup', rate: 25, seconds: 30, start: 0 },
  { name: 'r100', rate: 100, seconds: 60, start: 30 },
  { name: 'r200', rate: 200, seconds: 90, start: 90 },
  { name: 'r400', rate: 400, seconds: 60, start: 180 },
];
export const options = {
  scenarios: Object.fromEntries(plan.map(p => [p.name, { executor: 'constant-arrival-rate', rate: p.rate,
    timeUnit: '1s', duration: `${p.seconds}s`, startTime: `${p.start}s`, preAllocatedVUs: 100,
    maxVUs: 400, gracefulStop: '15s', tags: { phase: p.name } }])),
  thresholds: Object.fromEntries(plan.filter(p => p.name !== 'warmup').flatMap(p => [
    [`browse_latency{phase:${p.name}}`, ['p(95)<800']],
    [`browse_errors{phase:${p.name}}`, ['rate<0.01']],
    [`browse_requests{phase:${p.name}}`, [`count>=${p.rate * p.seconds}`]],
    [`dropped_iterations{scenario:${p.name}}`, ['count==0']],
  ])),
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(50)', 'p(95)', 'p(99)'],
  systemTags: ['status', 'method', 'name', 'scenario', 'expected_response'],
};
export function setup() {
  const response = http.get(`${base}/api/v1/products?page=1&size=20`, { timeout: '10s', redirects: 0 });
  if (response.status !== 200) fail('Local catalogue setup failed');
  return { ids: productIds(response.json()) };
}
export default function(data) {
  const { detail, id } = browseChoice(exec.scenario.iterationInTest, data.ids);
  const response = http.get(`${base}${detail ? `/api/v1/products/${id}` : '/api/v1/products?page=1&size=20'}`,
    { timeout: '10s', redirects: 0, tags: { name: detail ? 'product detail' : 'product list' } });
  let valid = false;
  try { valid = response.status === 200 && validResponse(response.json(), detail) && (!detail || response.json().data.id === id); }
  catch (_) { /* Malformed envelopes remain failures. */ }
  requests.add(1);
  errors.add(!valid);
  latency.add(response.timings.duration);
}
export function handleSummary(summary) {
  return { '/results/local-summary.json': JSON.stringify({ evidenceKind: 'local-diagnostic-not-acceptance',
    base, mix: '70% list / 30% detail', plan, summary }, null, 2) };
}
