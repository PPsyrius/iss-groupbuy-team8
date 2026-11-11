// Bounded local diagnostic, not an acceptance driver. Network namespace fixes the target to loopback.
import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';
import { fail } from 'k6';
import { productIds, validResponse, browseChoice } from '/repo/perf/k6/lib.mjs';
const label = __ENV.LOCAL_CASE;
if (!/^[a-z0-9-]{1,70}$/.test(label || '')) throw new Error('Invalid local case label');
const port = Number(__ENV.LOCAL_PORT || 8589), rate = Number(__ENV.LOCAL_RATE || 200), seconds = Number(__ENV.LOCAL_SECONDS || 60);
if (![8588,8589].includes(port) || !Number.isInteger(rate) || rate<1 || rate>400 || !Number.isInteger(seconds) || seconds<10 || seconds>180) throw new Error('Local diagnostic bounds exceeded');
const base = `http://127.0.0.1:${port}`;
const requests = new Counter('browse_requests'), errors = new Rate('browse_errors');
const latency = new Trend('browse_latency', true);
const lists = new Counter('list_requests'), details = new Counter('detail_requests');
export const options = { scenarios: { diagnostic: { executor: 'constant-arrival-rate', rate, timeUnit: '1s', duration: `${seconds}s`,
  preAllocatedVUs: 200, maxVUs: 1000, gracefulStop: '15s' } },
  thresholds: { browse_errors: ['rate<0.01'], browse_latency: ['p(95)<800'], dropped_iterations: ['count==0'] },
  summaryTrendStats: ['avg','med','min','max','p(95)','p(99)'],
  systemTags: ['status','method','name','scenario','expected_response'] };
export function setup() {
  const response = http.get(`${base}/api/v1/products?page=1&size=20`, { timeout: '10s', redirects: 0 });
  if(response.status!==200) fail('Local setup did not return catalogue');
  return { ids: productIds(response.json()) };
}
export default function(data) {
  const {detail,id} = browseChoice(exec.scenario.iterationInTest,data.ids);
  const start = Date.now();
  const response = http.get(`${base}${detail?`/api/v1/products/${id}`:'/api/v1/products?page=1&size=20'}`, { timeout: '10s', redirects: 0,
    tags: {name:detail?'product detail':'product list'} });
  let valid=false;
  try { valid=response.status===200 && validResponse(response.json(),detail) && (!detail || response.json().data.id===id); } catch (_) {}
  requests.add(1); errors.add(!valid); latency.add(Date.now()-start); if(detail)details.add(1);else lists.add(1);
}
export function handleSummary(summary) {
  return { [`/results/matrix-v2/${label}/summary.json`]:JSON.stringify({evidenceKind:'local-diagnostic-not-AWS-acceptance',label,rate,seconds,base,finishedAt:new Date().toISOString(),summary},null,2) };
}
