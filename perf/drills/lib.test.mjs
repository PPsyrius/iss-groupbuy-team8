import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { configure, validatePreflight, browseResult } from './lib.mjs';
import { poison, validControl } from './poison.mjs';

const local = { BASE_URL: 'http://localhost:8080', TOPOLOGY: 'local-fixture', DRILL: 'v5', KNEE_RPS: '200',
  TEST_SHA: 'a'.repeat(40), IMAGE_SHA: 'b'.repeat(40), RUN_LABEL: 'fixture' };
const remote = { ...local, BASE_URL: 'https://d3lvq02o2e7tcn.cloudfront.net', CONFIRM_TARGET: 'https://d3lvq02o2e7tcn.cloudfront.net',
  TOPOLOGY: 'distributed', EXPECTED_IMAGE_DIGEST: `sha256:${'c'.repeat(64)}`, SCALING_REQUESTS_PER_TARGET: '6000' };

function preflight() {
  const alb = 'arn:aws:elasticloadbalancing:ap-southeast-1:402178232802:loadbalancer/app/blocbuy-swe5001/123';
  const group = 'arn:aws:elasticloadbalancing:ap-southeast-1:402178232802:targetgroup/commerce-svc/456';
  const listener = 'arn:aws:elasticloadbalancing:ap-southeast-1:402178232802:listener/app/blocbuy-swe5001/123/789';
  const resource = 'service/blocbuy-swe5001/commerce-svc';
  return { schema: 1, account: '402178232802', region: 'ap-southeast-1', cluster: 'blocbuy-swe5001', mode: 'v5',
    baseUrl: remote.BASE_URL, toolingSha: local.TEST_SHA, collectedAt: new Date().toISOString(),
    services: { failures: [], services: [{ serviceName: 'commerce-svc', status: 'ACTIVE', desiredCount: 2, runningCount: 2,
      pendingCount: 0, taskDefinition: 'revision:1', deployments: [{ rolloutState: 'COMPLETED' }],
      loadBalancers: [{ targetGroupArn: group, containerName: 'commerce-svc', containerPort: 8589 }] }] },
    tasks: { failures: [], tasks: ['a', 'b'].map((az, i) => ({ taskArn: `task:${i}`, taskDefinitionArn: 'revision:1',
      lastStatus: 'RUNNING', desiredStatus: 'RUNNING', availabilityZone: `ap-southeast-1${az}`,
      containers: [{ name: 'commerce-svc', imageDigest: remote.EXPECTED_IMAGE_DIGEST }] })) },
    targets: { ScalableTargets: [{ ResourceId: resource, MinCapacity: 2, MaxCapacity: 6,
      SuspendedState: { DynamicScalingInSuspended: false, DynamicScalingOutSuspended: false, ScheduledScalingSuspended: false } }] },
    policies: { ScalingPolicies: [{ ResourceId: resource, PolicyType: 'TargetTrackingScaling', TargetTrackingScalingPolicyConfiguration: {
      TargetValue: 6000, PredefinedMetricSpecification: { PredefinedMetricType: 'ALBRequestCountPerTarget',
        ResourceLabel: 'app/blocbuy-swe5001/123/targetgroup/commerce-svc/456' } } }] },
    routing: { edge: { id: 'E3N39GHVFV6CT3', domainName: 'd3lvq02o2e7tcn.cloudfront.net', enabled: true, status: 'Deployed',
      behaviors: [{ pathPattern: '/api/*', targetOriginId: 'alb-runtime', functions: 0, lambdas: 0,
        cachePolicyId: '4135ea2d-6df8-44a3-9df3-4b5a84be39ad' }],
      origins: [{ id: 'alb-runtime', domainName: 'runtime.elb.amazonaws.com', path: '', protocolPolicy: 'http-only', httpPort: 80 }] },
    targetGroups: [{ arn: group, loadBalancerArns: [alb], port: 8589 }],
    loadBalancers: [{ arn: alb, dnsName: 'runtime.elb.amazonaws.com', type: 'application', state: 'active' }],
    listeners: [{ arn: listener, loadBalancerArn: alb, protocol: 'HTTP', port: 80 }], rulesListenerArn: listener,
    rules: [{ priority: '30', isDefault: false, hasTransforms: false, hasPathRegex: false,
      conditionFields: ['http-header', 'path-pattern'], pathPatterns: ['/api/*'], originHeaderMatches: true,
      actions: [{ type: 'forward', targetGroupArn: group }] }, { isDefault: true, actions: [{ type: 'fixed-response' }] }] } };
}

test('V5 offers exactly half the candidate, including fractional RPS, for ten minutes', () => {
  for (const knee of [125, 200]) {
    const config = configure({ ...remote, KNEE_RPS: String(knee) });
    assert.equal(config.ratePerTwoSeconds, knee);
    assert.equal(config.durationSeconds, 600);
    assert.equal(config.expectedRequests, knee * 300);
    assert.equal(config.options.scenarios.browse.timeUnit, '2s');
    assert.equal(config.options.scenarios.browse.duration, '601s');
    assert.deepEqual(config.options.thresholds.browse_errors, ['rate==0']);
  }
});
test('identity uses light browse only, with no authenticated or payment workload', () => {
  const config = configure({ ...remote, DRILL: 'identity' });
  assert.equal(config.expectedRequests, 1800);
  assert.equal(config.durationSeconds, 900);
  assert.equal(config.ratePerTwoSeconds, 4);
});
test('wrong targets, missing metadata, shortened live schedules and invalid VUs are refused', () => {
  for (const patch of [{ BASE_URL: 'https://shop.example' }, { BASE_URL: 'http://d3lvq02o2e7tcn.cloudfront.net' },
    { CONFIRM_TARGET: '' }, { TOPOLOGY: 'monolith' }, { DRILL: 'v3' }, { TEST_SHA: '' }, { IMAGE_SHA: '' },
    { EXPECTED_IMAGE_DIGEST: '' }, { FIXTURE_SECONDS: '2' }, { MAX_VUS: '1' }, { SCALING_REQUESTS_PER_TARGET: '' },
    { KNEE_RPS: '201' }, { BASE_URL: 'http://localhost:8080', TOPOLOGY: 'distributed' }]) assert.throws(() => configure({ ...remote, ...patch }));
  assert.throws(() => configure({ ...local, FIXTURE_SECONDS: '3' }));
});
test('fresh route-bound two-AZ two-task preflight is accepted for each drill', () => {
  for (const mode of ['v5', 'identity']) {
    const snapshot = preflight(); snapshot.mode = mode;
    validatePreflight(snapshot, configure({ ...remote, DRILL: mode }));
  }
});
test('partial, stale, mismatched, single-AZ, mixed-revision and duplicate-task evidence is refused', () => {
  for (const mutate of [s => s.collectedAt = 'invalid', s => s.collectedAt = new Date(Date.now() - 360000).toISOString(),
    s => s.account = '000000000000', s => s.toolingSha = 'd'.repeat(40), s => s.mode = 'v3',
    s => s.tasks.tasks.pop(), s => s.tasks.tasks[1].taskArn = s.tasks.tasks[0].taskArn,
    s => s.tasks.tasks[1].availabilityZone = 'ap-southeast-1a', s => delete s.tasks.tasks[1].availabilityZone,
    s => s.tasks.tasks[1].containers[0].imageDigest = 'wrong', s => s.tasks.tasks[1].taskDefinitionArn = 'revision:2',
    s => s.services.services[0].runningCount = 3, s => s.services.services[0].deployments[0].rolloutState = 'IN_PROGRESS',
    s => s.routing.rules[0].originHeaderMatches = false, s => s.tasks.failures.push({ reason: 'MISSING' })]) {
    const snapshot = preflight(); mutate(snapshot);
    assert.throws(() => validatePreflight(snapshot, configure(remote)));
  }
});
test('wrong scaling floor, suspension, target, route metric and unexpected policies are refused', () => {
  for (const mutate of [s => s.targets.ScalableTargets[0].MinCapacity = 1,
    s => s.targets.ScalableTargets[0].SuspendedState.ScheduledScalingSuspended = true,
    s => s.policies.ScalingPolicies[0].TargetTrackingScalingPolicyConfiguration.TargetValue = 12000,
    s => s.policies.ScalingPolicies[0].TargetTrackingScalingPolicyConfiguration.PredefinedMetricSpecification.ResourceLabel = 'other',
    s => s.policies.ScalingPolicies.push({ PolicyType: 'StepScaling' })]) {
    const snapshot = preflight(); mutate(snapshot);
    assert.throws(() => validatePreflight(snapshot, configure(remote)));
  }
});
test('browse analysis requires explicit complete counts and zero errors, but never claims AWS drill acceptance', () => {
  const context = configure(local);
  const report = { schema: 1, context, summary: { metrics: { browse_requests: { values: { count: context.expectedRequests } },
    browse_non_2xx: { values: { count: 0 } }, dropped_iterations: { values: { count: 0 } }, browse_errors: { values: { rate: 0 } } } } };
  assert.equal(browseResult(report).awsAcceptanceComplete, false);
  for (const key of Object.keys(report.summary.metrics)) {
    const copy = structuredClone(report); delete copy.summary.metrics[key];
    assert.throws(() => browseResult(copy));
    const nonzero = structuredClone(report);
    const metric = nonzero.summary.metrics[key].values;
    metric[key === 'browse_errors' ? 'rate' : 'count'] += 1;
    assert.throws(() => browseResult(nonzero));
  }
});
test('offline poison keeps supported routing/version but omits all required payload fields', () => {
  const payload = poison('v7-first');
  assert.equal(payload.envelope.eventType, 'PaymentSucceeded');
  assert.equal(payload.envelope.version, 1);
  assert.deepEqual(payload.envelope.payload, {});
  assert.equal(payload.attributes.eventType.StringValue, 'PaymentSucceeded');
  assert.equal(payload.manifest.published, false);
  assert.match(payload.manifest.bodySha256, /^[0-9a-f]{64}$/);
  for (const label of ['', '../bad', 'a'.repeat(25), 'spaces not allowed']) assert.throws(() => poison(label));
  const control = validControl('v7-first', '0199aaaa-0000-7000-8000-000000000001');
  assert.notEqual(control.envelope.eventId, payload.envelope.eventId);
  assert.equal(control.envelope.version, 1);
  assert.deepEqual(Object.keys(control.envelope.payload), ['paymentId', 'intentId', 'recipientUserId', 'operation', 'businessId', 'confirmedAt']);
  assert.equal(control.envelope.payload.paymentId, control.envelope.aggregateId);
  assert.equal(control.attributes.traceId.StringValue, control.envelope.traceId);
  assert.match(control.manifest.reason, /not a real Stripe/);
  assert.throws(() => validControl('v7-first', 'invalid'));
});
test('offline poison CLI refuses overwrites and repository destinations', () => {
  const parent = mkdtempSync(join(tmpdir(), 'blocbuy-v7-payload-'));
  const destination = join(parent, 'payload');
  const cli = resolve(import.meta.dirname, 'poison.mjs');
  try {
    const result = spawnSync(process.execPath, [cli, 'v7-fixture', destination], { encoding: 'utf8',
      env: { ...process.env, V7_CONTROL_RECIPIENT_ID: '0199aaaa-0000-7000-8000-000000000001' } });
    assert.equal(result.status, 0, result.stderr);
    const original = readFileSync(join(destination, 'message.json'), 'utf8');
    assert.equal(JSON.parse(readFileSync(join(destination, 'control-message.json'), 'utf8')).payload.operation, 'START');
    assert.equal(spawnSync(process.execPath, [cli, 'v7-fixture', destination]).status, 1);
    assert.equal(readFileSync(join(destination, 'message.json'), 'utf8'), original);
    assert.equal(spawnSync(process.execPath, [cli, 'v7-fixture', resolve(import.meta.dirname, 'must-not-create')]).status, 1);
  } finally { rmSync(parent, { recursive: true, force: true }); }
});
