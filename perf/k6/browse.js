import http from 'k6/http';
import { check, fail } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import { configure, productIds, validResponse, browseChoice, plateauForIteration, baselineRequestLimit, validatePreflight } from './lib.mjs';

const config = configure(__ENV);
const preflight = config.remote ? JSON.parse(open(__ENV.PREFLIGHT_PATH || fail('Remote load requires PREFLIGHT_PATH'))) : null;
export const options = config.options;
const latency = new Trend('browse_latency', true);
const errors = new Rate('browse_errors');
const requests = new Counter('browse_requests');

export function setup() {
  validatePreflight(preflight, config);
  const response = http.get(`${config.baseUrl}/api/v1/products?page=1&size=20`, {
    tags: { name: 'setup catalogue' }, timeout: '10s', redirects: 0,
  });
  if (response.status !== 200) fail('Catalogue setup failed (expected 200)');
  try { return { ids: productIds(response.json()) }; }
  catch (_) { fail('Catalogue setup has no usable Result product page'); }
}

export default function (data) {
  // Deterministic 70/30 list/detail mix within each scenario, across all VUs.
  const iteration = exec.scenario.iterationInTest;
  // The terminal scheduler guard is not an extra load plateau. Surplus arrivals
  // perform no HTTP and emit no browse samples; missing requests/drops still fail.
  if (config.mode === 'v2' && iteration >= baselineRequestLimit(config.stageSeconds)) return;
  const { detail, id } = browseChoice(iteration, data.ids);
  const path = detail ? `/api/v1/products/${id}` : '/api/v1/products?page=1&size=20';
  const start = Date.now();
  // With one executor and zero accepted drops, its arrival ordinal identifies
  // the scheduled plateau without wall-clock boundary drift. Keep it on completion.
  const tags = config.mode === 'v2' ? { plateau: String(plateauForIteration(iteration, config.stageSeconds)) } : {};
  const response = http.get(`${config.baseUrl}${path}`, {
    timeout: '10s', redirects: 0, tags: { ...tags, name: detail ? 'product detail' : 'product list' },
  });
  let valid = false;
  try { valid = response.status === 200 && validResponse(response.json(), detail) && (!detail || response.json().data.id === id); }
  catch (_) { /* HTML, empty and malformed responses are failures, not successful latency samples. */ }
  latency.add(Date.now() - start, tags);
  errors.add(!valid, tags);
  requests.add(1, tags);
  check(valid, { '200 Result with expected catalogue data': result => result });
}

export function handleSummary(summary) {
  const { options: unused, ...context } = config;
  const report = { schema: 1, context, preflight, finishedAt: new Date().toISOString(), summary };
  return {
    [__ENV.SUMMARY_PATH || 'summary.json']: JSON.stringify(report, null, 2),
    stdout: `Saved ${config.mode} report (${config.runLabel}); inspect thresholds and generator drops before accepting evidence.\n`,
  };
}
