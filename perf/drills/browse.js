import http from 'k6/http';
import { check, fail } from 'k6';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import { browseChoice, productIds, validResponse } from '../k6/lib.mjs';
import { configure, validatePreflight } from './lib.mjs';

const config = configure(__ENV);
const preflight = config.remote ? JSON.parse(open(__ENV.PREFLIGHT_PATH || fail('PREFLIGHT_PATH is required'))) : null;
export const options = config.options;
const errors = new Rate('browse_errors');
const non2xx = new Counter('browse_non_2xx');
const requests = new Counter('browse_requests');
const latency = new Trend('browse_latency', true);

export function setup() {
  validatePreflight(preflight, config);
  const response = http.get(`${config.baseUrl}/api/v1/products?page=1&size=20`, { timeout: '10s', redirects: 0, tags: { name: 'setup catalogue' } });
  if (response.status !== 200) fail('Catalogue setup failed');
  try { return { ids: productIds(response.json()) }; }
  catch (_) { fail('Catalogue setup has no usable Result product page'); }
}

export default function (data) {
  const iteration = exec.scenario.iterationInTest;
  if (iteration >= config.expectedRequests) return; // One-second scheduling guard sends no surplus HTTP.
  const { detail, id } = browseChoice(iteration, data.ids);
  const path = detail ? `/api/v1/products/${id}` : '/api/v1/products?page=1&size=20';
  const started = Date.now();
  const response = http.get(`${config.baseUrl}${path}`, { timeout: '10s', redirects: 0, tags: { name: detail ? 'product detail' : 'product list' } });
  let valid = false;
  try { valid = response.status === 200 && validResponse(response.json(), detail) && (!detail || response.json().data.id === id); }
  catch (_) { /* Malformed JSON/HTML and error envelopes fail, even with HTTP 200. */ }
  non2xx.add(response.status < 200 || response.status >= 300 ? 1 : 0);
  requests.add(1);
  errors.add(!valid);
  latency.add(Date.now() - started);
  check(valid, { '200 Result with expected catalogue data': result => result });
}

export function handleSummary(summary) {
  const { options: unused, ...context } = config;
  return { [__ENV.SUMMARY_PATH || 'summary.json']: JSON.stringify({ schema: 1, context, preflight, finishedAt: new Date().toISOString(), summary }, null, 2),
    stdout: `Saved ${config.mode} browse output. This driver neither injects faults nor proves their recovery.\n` };
}
