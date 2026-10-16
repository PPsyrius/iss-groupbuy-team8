import test from 'node:test';
import assert from 'node:assert/strict';
import { BASELINE_RATES, configure, productIds, validResponse, browseChoice, plateauForIteration, baselineRequestLimit, validatePreflight, baselineResult, connectionBudget } from './lib.mjs';

const env = { BASE_URL: 'http://localhost:8080', TEST_SHA: 'a'.repeat(40), IMAGE_SHA: 'b'.repeat(40), RUN_LABEL: 'test', TOPOLOGY: 'monolith' };
const uuid = '00000000-0000-7000-8000-000000000001';
function report() {
  const metrics = { dropped_iterations: { values: { count: 0 } },
    browse_requests: { values: { count: BASELINE_RATES.reduce((sum, rps) => sum + rps * 60, 0) } } };
  BASELINE_RATES.forEach((rps, i) => {
    metrics[`browse_requests{plateau:${i}}`] = { values: { count: rps * 60 } };
    metrics[`browse_latency{plateau:${i}}`] = { values: { 'p(95)': 100 } };
    metrics[`browse_errors{plateau:${i}}`] = { values: { rate: 0 } };
  });
  return { schema: 1, context: configure(env), summary: { metrics } };
}

test('V2 uses one bounded VU pool and retains all ten step plateaus', () => {
  const config = configure(env);
  const scenarios = Object.values(config.options.scenarios);
  assert.equal(scenarios.length, 1);
  assert.equal(scenarios[0].preAllocatedVUs, 100);
  assert.equal(scenarios[0].maxVUs, 400);
  assert.deepEqual(scenarios[0].stages.filter(s => s.duration === '60s').map(s => s.target), BASELINE_RATES);
  assert.equal(scenarios[0].stages.filter(s => s.duration === '0s').length, 9);
  assert.equal(Object.keys(config.options.thresholds).length, 33);
});
test('V2 terminal scheduler guard retains an exact bounded HTTP budget', () => {
  for (const seconds of [1, 2, 60]) {
    const config = configure({ ...env, FIXTURE_STAGE_SECONDS: String(seconds) });
    assert.deepEqual(config.options.scenarios.baseline.stages.at(-1), { target: 200, duration: '1s' });
    assert.equal(baselineRequestLimit(seconds), 960 * seconds);
    assert.deepEqual(config.options.thresholds.browse_requests, [`count>=${960 * seconds}`, `count<=${960 * seconds}`]);
  }
});
test('V3 ramps to three times the knee and holds ten minutes', () => {
  const c = configure({ ...env, MODE: 'v3', TOPOLOGY: 'distributed', KNEE_RPS: '50' });
  assert.equal(c.options.scenarios.scaling.startRate, 10);
  assert.deepEqual(c.options.scenarios.scaling.stages[2], { target: 150, duration: '10m' });
});
test('remote execution requires an exact explicit HTTPS origin confirmation', () => {
  for (const url of ['https://shop.example', 'http://shop.example', 'https://user:secret@shop.example', 'https://shop.example/api', 'https://shop.example?token=secret']) {
    assert.throws(() => configure({ ...env, BASE_URL: url }));
  }
  assert.equal(configure({ ...env, BASE_URL: 'https://shop.example', CONFIRM_TARGET: 'https://shop.example', EXPECTED_IMAGE_DIGEST: `sha256:${'c'.repeat(64)}` }).baseUrl, 'https://shop.example');
});
test('invalid mode, topology, metadata and capacity fail before a request', () => {
  for (const patch of [{ MODE: 'v4' }, { TOPOLOGY: 'distributed' }, { MODE: 'v3', KNEE_RPS: '20' },
    { TEST_SHA: '' }, { IMAGE_SHA: 'dev' }, { RUN_LABEL: '../secret' }, { MAX_VUS: '0' },
    { PREALLOCATED_VUS: '401', MAX_VUS: '400' }, { BASE_URL: '' },
    { MODE: 'v3', TOPOLOGY: 'distributed', KNEE_RPS: '0' }]) assert.throws(() => configure({ ...env, ...patch }));
});
test('fixture smoke never pretends to be V2', () => {
  const c = configure({ ...env, MODE: 'smoke', TOPOLOGY: 'local-fixture' });
  assert.equal(c.options.scenarios.smoke.rate, 2);
  assert.throws(() => baselineResult({ ...report(), context: c }));
});
test('setup rejects empty pages, wrong envelope and malformed IDs', () => {
  const body = { code: 'COMMON_200_000', data: { items: [{ id: uuid }] } };
  assert.deepEqual(productIds(body), [uuid]);
  for (const b of [{}, { code: 'COMMON_200_000', data: { items: [] } }, { ...body, code: 'FAIL' },
    { ...body, data: { items: [{ id: '../secret' }] } }]) assert.throws(() => productIds(b));
});
test('HTML/incorrect envelope/empty list do not count as successful browse', () => {
  assert.equal(validResponse('<html>', false), false);
  assert.equal(validResponse({ code: 'COMMON_200_000', data: { items: [] } }, false), false);
  assert.equal(validResponse({ code: 'COMMON_200_000', data: { id: uuid } }, true), true);
});
test('70/30 mix cycles detail traffic across every seeded product', () => {
  const choices = Array.from({ length: 100 }, (_, i) => browseChoice(i, Array.from({ length: 10 }, (_, id) => String(id))));
  assert.equal(choices.filter(c => c.detail).length, 30);
  assert.equal(new Set(choices.filter(c => c.detail).map(c => c.id)).size, 10);
});
test('calibration converts RPS to requests/minute and reports unobserved saturation', () => {
  const result = baselineResult(report());
  assert.equal(result.kneeRps, 200);
  assert.equal(result.requestsPerTargetPerMinute, 12000);
  assert.equal(result.saturationObserved, false);
  assert.equal(result.hasAwsPreflight, false);
});
test('first saturation caps calibration even if a later plateau passes', () => {
  const r = report();
  r.summary.metrics['browse_latency{plateau:3}'].values['p(95)'] = 500;
  assert.equal(baselineResult(r).kneeRps, 40);
});
test('error threshold is strict and no passing plateau means no target', () => {
  const r = report();
  r.summary.metrics['browse_errors{plateau:0}'].values.rate = 0.01;
  assert.throws(() => baselineResult(r), /No passing/);
});
test('missing/invalid/under-delivered plateaus cannot produce calibration', () => {
  for (const field of ['count', 'p(95)', 'rate']) {
    const r = report();
    const name = field === 'count' ? 'browse_requests' : field === 'rate' ? 'browse_errors' : 'browse_latency';
    r.summary.metrics[`${name}{plateau:9}`].values[field] = null;
    assert.throws(() => baselineResult(r), /Incomplete/);
  }
  const r = report();
  r.summary.metrics['browse_requests{plateau:0}'].values.count = 598;
  assert.throws(() => baselineResult(r));
});
test('scheduled counts are exact; only one trailing end-boundary arrival is allowed', () => {
  const r = report();
  for (const count of [599, 601, 599.5]) {
    r.summary.metrics['browse_requests{plateau:0}'].values.count = count;
    assert.throws(() => baselineResult(r));
  }
  const trailing = report();
  trailing.summary.metrics['browse_requests{plateau:9}'].values.count++;
  trailing.summary.metrics.browse_requests.values.count++;
  assert.equal(baselineResult(trailing).plateaus[9].completed, 12001);
  delete trailing.summary.metrics.browse_requests;
  assert.throws(() => baselineResult(trailing), /total request count/);
});
test('generator drops invalidate otherwise passing server measurements', () => {
  const r = report();
  r.summary.metrics.dropped_iterations = { values: { count: 1 } };
  assert.throws(() => baselineResult(r), /Dropped iterations/);
});
test('missing, null or malformed drop counts are unknown evidence, never zero', () => {
  for (const metric of [undefined, { values: {} }, { values: { count: null } }, { values: { count: '0' } }]) {
    const r = report();
    r.summary.metrics.dropped_iterations = metric;
    assert.throws(() => baselineResult(r), /Dropped iterations/);
  }
});
test('arrival ordinals retain exact plateau membership despite wall-clock boundary skew', () => {
  assert.equal(plateauForIteration(599), 0);
  assert.equal(plateauForIteration(600), 1);
  assert.equal(plateauForIteration(57599), 9);
  assert.equal(plateauForIteration(57600), 9);
  const counts = Array(10).fill(0);
  for (let ordinal = 0; ordinal < 57600; ordinal++) counts[plateauForIteration(ordinal)]++;
  assert.deepEqual(counts, BASELINE_RATES.map(rps => rps * 60));
});
test('only local fixtures can use a shortened V2 schedule', () => {
  assert.equal(configure({ ...env, FIXTURE_STAGE_SECONDS: '1' }).stageSeconds, 1);
  assert.throws(() => configure({ ...env, BASE_URL: 'https://shop.example', CONFIRM_TARGET: 'https://shop.example',
    EXPECTED_IMAGE_DIGEST: `sha256:${'c'.repeat(64)}`, FIXTURE_STAGE_SECONDS: '1' }));
});
test('remote smoke bypasses and missing reviewed digests are refused', () => {
  const remote = { ...env, BASE_URL: 'https://shop.example', CONFIRM_TARGET: 'https://shop.example' };
  assert.throws(() => configure(remote), /EXPECTED_IMAGE_DIGEST/);
  assert.throws(() => configure({ ...remote, MODE: 'smoke' }), /local-fixture-only/);
});

const remoteEnv = { ...env, BASE_URL: 'https://shop.example', CONFIRM_TARGET: 'https://shop.example', EXPECTED_IMAGE_DIGEST: `sha256:${'c'.repeat(64)}` };
function preflight(mode = 'v2') {
  const service = mode === 'v2' ? 'monolith' : 'commerce-svc';
  const resourceId = `service/blocbuy-swe5001/${service}`;
  const alb = 'arn:aws:elasticloadbalancing:ap-southeast-1:402178232802:loadbalancer/app/blocbuy-swe5001/123';
  const group = `arn:aws:elasticloadbalancing:ap-southeast-1:402178232802:targetgroup/${service}/456`;
  const listener = 'arn:aws:elasticloadbalancing:ap-southeast-1:402178232802:listener/app/blocbuy-swe5001/123/789';
  return { schema: 1, account: '402178232802', region: 'ap-southeast-1', cluster: 'blocbuy-swe5001',
    mode, baseUrl: remoteEnv.BASE_URL, collectedAt: new Date().toISOString(), toolingSha: env.TEST_SHA,
    services: { failures: [], services: [{ serviceName: service, status: 'ACTIVE', desiredCount: 1, runningCount: 1,
      pendingCount: 0, taskDefinition: 'task-definition:1', deployments: [{ rolloutState: 'COMPLETED' }],
      loadBalancers: [{ containerName: service, containerPort: 8589, targetGroupArn: group }] }] },
    tasks: { failures: [], tasks: [{ taskArn: 'task:1', taskDefinitionArn: 'task-definition:1', lastStatus: 'RUNNING', desiredStatus: 'RUNNING',
      containers: [{ name: service, imageDigest: remoteEnv.EXPECTED_IMAGE_DIGEST }] }] },
    targets: { ScalableTargets: mode === 'v2' ? [] : [{ ResourceId: resourceId, MinCapacity: 1, MaxCapacity: 6,
      SuspendedState: { DynamicScalingInSuspended: false, DynamicScalingOutSuspended: false, ScheduledScalingSuspended: false } }] },
    policies: { ScalingPolicies: mode === 'v2' ? [] : [{ ResourceId: resourceId, PolicyType: 'TargetTrackingScaling',
      TargetTrackingScalingPolicyConfiguration: { TargetValue: 3000,
        PredefinedMetricSpecification: { PredefinedMetricType: 'ALBRequestCountPerTarget',
          ResourceLabel: `app/blocbuy-swe5001/123/targetgroup/${service}/456` } } }] },
    routing: { edge: { id: 'E3N39GHVFV6CT3', domainName: 'shop.example', status: 'Deployed', enabled: true,
      behaviors: [{ pathPattern: '/api/*', targetOriginId: 'alb-runtime', functions: 0, lambdas: 0,
        cachePolicyId: '4135ea2d-6df8-44a3-9df3-4b5a84be39ad' }],
      origins: [{ id: 'alb-runtime', domainName: 'runtime.elb.amazonaws.com', path: '', protocolPolicy: 'http-only', httpPort: 80, httpsPort: 443 }] },
    targetGroups: [{ arn: group, loadBalancerArns: [alb], port: 8589, protocol: 'HTTP' }],
    loadBalancers: [{ arn: alb, dnsName: 'runtime.elb.amazonaws.com', type: 'application', state: 'active' }],
    listeners: [{ arn: listener, loadBalancerArn: alb, port: 80, protocol: 'HTTP' }], rulesListenerArn: listener,
    rules: [{ priority: '100', isDefault: false, hasTransforms: false, hasPathRegex: false,
      pathPatterns: ['/api/*'], conditionFields: ['path-pattern', 'http-header'], originHeaderMatches: true,
      actions: [{ type: 'forward', targetGroupArn: group, forwardGroups: [{ arn: group, weight: 1 }] }] },
    { priority: 'default', isDefault: true, actions: [{ type: 'fixed-response' }] }] } };
}
test('AWS preflight accepts only the stable observed task and reviewed digest', () => {
  const config = configure(remoteEnv);
  assert.equal(validatePreflight(preflight(), config).imageDigest, remoteEnv.EXPECTED_IMAGE_DIGEST);
});
test('AWS preflight rejects stale snapshots, wrong accounts, targets and source attribution', () => {
  for (const patch of [{ collectedAt: 'invalid' }, { collectedAt: new Date(Date.now() - 360000).toISOString() },
    { account: '999999999999' }, { baseUrl: 'https://other.example' }, { toolingSha: 'b'.repeat(40) },
    { mode: 'v3' }]) assert.throws(() => validatePreflight({ ...preflight(), ...patch }, configure(remoteEnv)));
});
test('AWS preflight rejects extra tasks, incomplete rollout, mixed revisions and wrong digests', () => {
  for (const mutate of [p => p.services.services[0].runningCount = 2,
    p => p.services.services[0].deployments[0].rolloutState = 'IN_PROGRESS',
    p => p.tasks.tasks.push(p.tasks.tasks[0]), p => p.tasks.tasks[0].taskDefinitionArn = 'task-definition:2',
    p => delete p.tasks.tasks[0].taskArn,
    p => p.tasks.tasks[0].containers[0].imageDigest = `sha256:${'d'.repeat(64)}`,
    p => p.tasks.failures.push({ reason: 'MISSING' }), p => p.services.failures.push({ reason: 'MISSING' })]) {
    const p = preflight(); mutate(p);
    assert.throws(() => validatePreflight(p, configure(remoteEnv)));
  }
});
test('V2 rejects active or incompletely suspended autoscaling', () => {
  const p = preflight();
  p.targets.ScalableTargets = [{ ResourceId: 'service/blocbuy-swe5001/monolith', SuspendedState: {
    DynamicScalingInSuspended: true, DynamicScalingOutSuspended: true, ScheduledScalingSuspended: true } }];
  validatePreflight(p, configure(remoteEnv));
  p.targets.ScalableTargets[0].SuspendedState.ScheduledScalingSuspended = false;
  assert.throws(() => validatePreflight(p, configure(remoteEnv)));
});
test('V3 verifies the active bounds, metric and calibrated per-minute target', () => {
  const config = configure({ ...remoteEnv, MODE: 'v3', TOPOLOGY: 'distributed', KNEE_RPS: '50' });
  validatePreflight(preflight('v3'), config);
  for (const mutate of [p => p.targets.ScalableTargets[0].MinCapacity = 2,
    p => p.targets.ScalableTargets[0].SuspendedState.DynamicScalingOutSuspended = true,
    p => p.policies.ScalingPolicies[0].TargetTrackingScalingPolicyConfiguration.TargetValue = 200,
    p => p.policies.ScalingPolicies[0].TargetTrackingScalingPolicyConfiguration.PredefinedMetricSpecification.ResourceLabel = 'app/other/targetgroup/other',
    p => p.policies.ScalingPolicies = []]) {
    const p = preflight('v3'); mutate(p);
    assert.throws(() => validatePreflight(p, config));
  }
});
test('preflight binds CloudFront API origin, ALB listener and ECS target group', () => {
  for (const mutate of [p => delete p.routing,
    p => p.routing.edge.behaviors[0].targetOriginId = 'other-origin',
    p => p.routing.edge.origins[0].domainName = 'other-runtime.elb.amazonaws.com',
    p => p.routing.edge.origins[0].path = '/other-deployment',
    p => p.routing.targetGroups[0].loadBalancerArns[0] = 'other-alb',
    p => p.services.services[0].loadBalancers[0].targetGroupArn = 'other-group',
    p => p.routing.listeners[0].port = 443,
    p => p.routing.rulesListenerArn = 'other-listener',
    p => p.routing.rules[0].actions[0].targetGroupArn = 'other-group',
    p => p.routing.rules[0].originHeaderMatches = false]) {
    const p = preflight(); mutate(p);
    assert.throws(() => validatePreflight(p, configure(remoteEnv)), /route/);
  }
});
test('routing rejects overrides, rewrites, caching and weighted alternate destinations', () => {
  for (const mutate of [p => p.routing.edge.status = 'InProgress',
    p => p.routing.edge.behaviors.unshift({ pathPattern: '/api/v1/products*', targetOriginId: 'other' }),
    p => p.routing.edge.behaviors[0].functions = 1,
    p => p.routing.edge.behaviors[0].lambdas = 1,
    p => p.routing.edge.behaviors[0].cachePolicyId = 'cached',
    p => p.routing.rules[0].hasTransforms = true,
    p => p.routing.rules[0].hasPathRegex = true,
    p => p.routing.rules[0].actions[0].forwardGroups.push({ arn: 'other-group', weight: 1 }),
    p => p.routing.rules.push({ isDefault: false, priority: '1', hasPathRegex: false,
      conditionFields: ['path-pattern'], pathPatterns: ['/api/v1/products*'], actions: [{ type: 'forward', targetGroupArn: 'other-group' }] })]) {
    const p = preflight(); mutate(p);
    assert.throws(() => validatePreflight(p, configure(remoteEnv)), /route/);
  }
});
test('declared disjoint service routes cannot intercept the catalogue route', () => {
  const p = preflight('v3');
  p.routing.rules.unshift({ isDefault: false, priority: '5', hasPathRegex: false,
    conditionFields: ['path-pattern'], pathPatterns: ['/internal/*', '/api/internal/*'] });
  p.routing.rules.unshift({ isDefault: false, priority: '10', hasPathRegex: false,
    conditionFields: ['path-pattern', 'http-header'], pathPatterns: ['/api/v1/auth/*', '/api/v1/users/*'] });
  p.routing.rules.unshift({ isDefault: false, priority: '20', hasPathRegex: false,
    conditionFields: ['path-pattern', 'http-header'], pathPatterns: ['/api/v1/notifications*'] });
  validatePreflight(p, configure({ ...remoteEnv, MODE: 'v3', TOPOLOGY: 'distributed', KNEE_RPS: '50' }));
});
test('direct and single-group forwards remain bound to the actual origin protocol/port', () => {
  const p = preflight();
  delete p.routing.rules[0].actions[0].forwardGroups;
  validatePreflight(p, configure(remoteEnv));
  p.routing.edge.origins[0].protocolPolicy = 'https-only';
  p.routing.listeners[0].protocol = 'HTTPS';
  p.routing.listeners[0].port = 443;
  validatePreflight(p, configure(remoteEnv));
  p.routing.edge.origins[0].protocolPolicy = 'match-viewer';
  validatePreflight(p, configure(remoteEnv));
  p.routing.rules[0].actions[0].forwardGroups = [{ arn: 'other-group', weight: 1 }];
  assert.throws(() => validatePreflight(p, configure(remoteEnv)), /route/);
});
test('remote calibration refuses missing preflight/timing and retains the reviewed digest', () => {
  const now = Date.now();
  const r = report();
  r.context = configure(remoteEnv);
  r.preflight = preflight();
  r.finishedAt = new Date(now + 600000).toISOString();
  r.summary.state = { testRunDurationMs: 600000 };
  const result = baselineResult(r);
  assert.equal(result.hasAwsPreflight, true);
  assert.equal(result.requiresOperatorReview, true);
  assert.equal(result.context.expectedImageDigest, remoteEnv.EXPECTED_IMAGE_DIGEST);
  // Observed k6 timer precision: the complete 600-second schedule may report
  // slightly under 600000 ms, while exact sample counts still prove completion.
  r.summary.state.testRunDurationMs = 599980.359439;
  r.finishedAt = new Date(now + 599980).toISOString();
  assert.equal(baselineResult(r).hasAwsPreflight, true);
  const missing = { ...r, preflight: null };
  assert.throws(() => baselineResult(missing));
  r.summary.state.testRunDurationMs = 10000;
  assert.throws(() => baselineResult(r), /full-duration/);
});
test('calibration requires source attribution and a usable metric schema', () => {
  for (const field of ['testSha', 'imageSha', 'runLabel', 'baseUrl']) {
    const r = report();
    delete r.context[field];
    assert.throws(() => baselineResult(r));
  }
  assert.throws(() => baselineResult({ ...report(), summary: {} }), /Missing legacy/);
});

const budget = { maxConnections: 150, reservedConnections: 3, ownerConnections: 4,
  operatorConnections: 2, headroomConnections: 20, services: [
    { name: 'identity-svc', maxTasks: 1, poolSize: 10 },
    { name: 'commerce-svc', maxTasks: 6, poolSize: 10 },
    { name: 'notification-svc', maxTasks: 4, poolSize: 10 },
  ] };
test('connection budget counts all pools, owners, operators, reservations and headroom', () => {
  const result = connectionBudget(budget);
  assert.equal(result.pooledConnections, 110);
  assert.equal(result.required, 139);
  assert.equal(result.remaining, 11);
  assert.equal(result.fits, true);
});
test('connection budget fails capacity without silently reducing pools or task maxima', () => {
  assert.equal(connectionBudget({ ...budget, maxConnections: 138 }).fits, false);
  assert.equal(connectionBudget({ ...budget, maxConnections: 139 }).fits, true);
});
test('connection budget refuses incomplete, duplicated or nonsensical service plans', () => {
  for (const patch of [{ services: [] }, { services: [budget.services[0], budget.services[0], budget.services[2]] },
    { headroomConnections: 0 }, { ownerConnections: 0 }, { maxConnections: null },
    { services: budget.services.map(s => ({ ...s, maxTasks: 7 })) }]) assert.throws(() => connectionBudget({ ...budget, ...patch }));
});
