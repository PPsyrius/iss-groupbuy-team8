import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { configure } from './lib.mjs';
import { v3Accumulator, analyzeV4 } from './scaling.mjs';

const at = seconds => new Date(Date.parse('2026-10-16T00:00:00Z') + seconds * 1000).toISOString();
const sha = 'a'.repeat(40);
function v3() {
  const context = configure({ BASE_URL: 'http://localhost:8080', TEST_SHA: sha, IMAGE_SHA: 'b'.repeat(40),
    RUN_LABEL: 'v3-fixture', MODE: 'v3', TOPOLOGY: 'distributed', KNEE_RPS: '200' });
  const report = { schema: 1, context, finishedAt: at(1800), summary: { state: { testRunDurationMs: 1800000 }, metrics: {
    dropped_iterations: { values: { count: 0 } }, browse_requests: { values: { count: 6 } },
    browse_errors: { values: { rate: 0 } }, browse_latency: { thresholds: { 'p(95)<800': { ok: false } } },
  } } };
  const evidence = { schema: 1, toolingSha: sha, runLabel: 'v3-fixture', events: [
    { at: at(60), from: 1, to: 4, source: 'application-autoscaling', activityId: 'activity-1' },
  ] };
  const observations = Array.from({ length: 61 }, (_, i) => ({ observedAt: at(i * 30), failures: [],
    services: [{ name: 'commerce-svc', running: i < 3 ? 1 : 4, desired: i < 2 ? 1 : 4, pending: 0 }] }));
  const samples = [10, 60, 120, 239, 240, 1799].flatMap(seconds => [
    { type: 'Point', metric: 'browse_latency', data: { time: at(seconds), value: seconds >= 60 && seconds < 240 ? 2000 : 100 } },
    { type: 'Point', metric: 'browse_errors', data: { time: at(seconds), value: 0 } },
    { type: 'Point', metric: 'browse_requests', data: { time: at(seconds), value: 1 } },
  ]);
  return { report, evidence, observations, samples };
}
function analyze(input) {
  const accumulator = v3Accumulator(input.report, input.evidence);
  input.samples.forEach(row => accumulator.add(row));
  return accumulator.finish(input.observations);
}
test('V3 excludes exactly the three-minute post-scale-out window, not all-run errors', () => {
  const result = analyze(v3());
  assert.equal(result.excludedLatencySamples, 3);
  assert.equal(result.evaluatedLatencySamples, 3);
  assert.equal(result.outsideWindowP95Ms, 100);
  assert.equal(result.latencyAndScaleOutGatesPassed, true);
  assert.equal(result.originalAllRunLatencyThresholds['p(95)<800'].ok, false);
  assert.equal(result.acceptanceComplete, false);
  assert.equal(result.hasAwsPreflight, false);
  const input = v3();
  input.samples.find(row => row.metric === 'browse_errors' && row.data.time === at(120)).data.value = 1;
  input.report.summary.metrics.browse_errors.values.rate = 1 / 6;
  assert.equal(analyze(input).httpGatesPassed, false);
});
test('V3 observes real task growth instead of assuming an activity completed it', () => {
  const input = v3();
  input.observations.forEach(row => { row.services[0].running = 1; });
  assert.equal(analyze(input).scaleOutObserved, false);
  input.evidence.events = [];
  assert.equal(analyze(input).latencyAndScaleOutGatesPassed, false);
});
test('V3 analyser retains explicit policy target, historical fallback and drop refusal', () => {
  const input = v3();
  input.report.context.scalingRequestsPerTarget = 6000;
  assert.equal(analyze(input).latencyAndScaleOutGatesPassed, true);
  input.report.context.scalingRequestsPerTarget = 0;
  assert.throws(() => analyze(input), /SCALING_REQUESTS_PER_TARGET/);
  delete input.report.context.scalingRequestsPerTarget;
  assert.equal(analyze(input).latencyAndScaleOutGatesPassed, true);
  input.report.context.scalingRequestsPerTarget = 6000;
  input.report.summary.metrics.dropped_iterations.values.count = 1;
  assert.throws(() => analyze(input), /drops/);
});
test('V3 rejects incomplete samples, drops, mismatched metadata and bad observation coverage', () => {
  for (const mutate of [
    x => x.report.schema = 2, x => x.report.context.mode = 'v2', x => x.report.finishedAt = 'invalid',
    x => x.report.summary.state.testRunDurationMs = 0,
    x => x.report.summary.metrics.dropped_iterations.values.count = 1,
    x => delete x.report.summary.metrics.dropped_iterations,
    x => x.evidence.toolingSha = 'other', x => x.evidence.runLabel = 'other',
    x => x.samples.pop(), x => x.samples[0].data.value = -1,
    x => x.samples[2].data.value = 2, x => x.samples[1].data.value = 2,
    x => x.samples[0].data.time = at(-2),
    x => x.report.summary.metrics.browse_errors.values.rate = 0.5,
    x => x.observations.splice(10, 2), x => x.observations.shift(), x => x.observations.pop(),
    x => x.observations[1].observedAt = x.observations[0].observedAt,
    x => x.observations[1].failures.push({ reason: 'denied' }),
    x => x.observations[1].services = [], x => x.observations[1].services[0].running = 7,
    x => x.observations[1].services[0].name = 'identity-svc',
  ]) {
    const input = v3(); mutate(input);
    assert.throws(() => analyze(input));
  }
});
test('V3 rejects invented, duplicate, non-growth or out-of-run scale-out events', () => {
  for (const mutate of [e => e.source = 'guessed', e => e.activityId = '', e => e.from = 0,
    e => e.to = 1, e => e.to = 7, e => e.at = at(1801), e => e.at = 'invalid']) {
    const input = v3(); mutate(input.evidence.events[0]);
    assert.throws(() => analyze(input));
  }
  const input = v3(); input.evidence.events.push({ ...input.evidence.events[0] });
  assert.throws(() => analyze(input), /duplicate/);
});
test('V3 keeps overlapping exclusion windows bounded and requires outside-window latency', () => {
  const input = v3();
  input.evidence.events.push({ at: at(120), from: 4, to: 6, source: 'application-autoscaling', activityId: 'activity-2' });
  assert.equal(analyze(input).excludedLatencySamples, 4);
  input.evidence.events.push({ at: at(0), from: 1, to: 2, source: 'application-autoscaling', activityId: 'activity-3' });
  input.evidence.events.push({ at: at(1700), from: 2, to: 3, source: 'application-autoscaling', activityId: 'activity-4' });
  assert.throws(() => analyze(input), /no latency/);
});
function v4() {
  const seed = { schema: 1, evidenceKind: 'demo-expiry-seed', runLabel: 'v4-fixture', toolingSha: sha,
    teams: 2000, members: 2000, seededAt: at(0), expiresAt: at(60) };
  const observations = Array.from({ length: 11 }, (_, i) => ({ observedAt: at(i * 15), notificationTasks: i < 5 ? 1 : 4,
    notification: { visible: i >= 4 && i < 7 ? 100 : 0, notVisible: 0, delayed: 0,
      dlqVisible: 0, dlqNotVisible: 0, dlqDelayed: 0, oldestAgeSeconds: 0 } }));
  const report = { schema: 1, runLabel: seed.runLabel, observedAt: at(150), teams: 2000, expiredTeams: 2000,
    outboxEvents: 2000, publishedEvents: 2000, failedEvents: 0, notifications: 2000, consumedEvents: 2000 };
  return { seed, observations, report };
}
const analyze4 = x => analyzeV4(x.seed, x.observations, x.report);
test('V4 requires real source/effect counts, backlog, scale-to-four, stable drain and empty DLQ', () => {
  const result = analyze4(v4());
  assert.equal(result.passed, true);
  assert.equal(result.drainSecondsUpperBound, 45);
  for (const mutate of [x => x.report.notifications = 1999, x => x.report.failedEvents = 1,
    x => x.observations[2].notification.dlqNotVisible = 1,
    x => x.observations.at(-1).notification.delayed = 1,
    x => x.observations.forEach(row => row.notificationTasks = 1),
    x => x.observations.forEach(row => row.notification.visible = 0),
    x => x.observations[0].notification.notVisible = 1]) {
    const input = v4(); mutate(input); assert.equal(analyze4(input).passed, false);
  }
});
function largerV4() {
  const input = v4();
  Object.assign(input.seed, { expectedCount: 20000, teams: 20000, members: 20000 });
  Object.assign(input.report, { expectedCount: 20000, teams: 20000, expiredTeams: 20000,
    outboxEvents: 20000, publishedEvents: 20000, notifications: 20000, consumedEvents: 20000 });
  return input;
}
test('V4 larger fixture requires explicit expected count and matching seed/report bindings', () => {
  const input = largerV4();
  assert.throws(() => analyze4(input), /seed receipt/);
  const result = analyzeV4(input.seed, input.observations, input.report, 20000);
  assert.equal(result.passed, true);
  assert.equal(result.expectedCount, 20000);
  assert.equal(result.drainAfterScale, true);
  for (const expected of [0, 2001, 20001, '20000', null]) {
    assert.throws(() => analyzeV4(input.seed, input.observations, input.report, expected), /expected count/);
  }
  for (const mutate of [x => delete x.seed.expectedCount, x => delete x.report.expectedCount,
    x => x.seed.expectedCount = 2000, x => x.report.expectedCount = 2000,
    x => x.seed.expectedCount = '20000', x => x.report.expectedCount = null,
    x => x.seed.teams = 2000, x => x.seed.members = 2000]) {
    const changed = largerV4(); mutate(changed);
    assert.throws(() => analyzeV4(changed.seed, changed.observations, changed.report, 20000));
  }
});
test('V4 larger fixture keeps every exact source/effect counter and scale-before-final-drain gate', () => {
  for (const key of ['teams', 'expiredTeams', 'outboxEvents', 'publishedEvents', 'notifications', 'consumedEvents']) {
    for (const value of [2000, 19999, 20001]) {
      const input = largerV4(); input.report[key] = value;
      assert.equal(analyzeV4(input.seed, input.observations, input.report, 20000).passed, false, key);
    }
  }
  for (const mutate of [x => x.report.failedEvents = 1, x => x.observations[2].notification.dlqDelayed = 1,
    x => x.observations.forEach(row => row.notificationTasks = 1),
    x => x.observations.forEach((row, i) => row.notificationTasks = i >= 7 ? 4 : 1)]) {
    const input = largerV4(); mutate(input);
    assert.equal(analyzeV4(input.seed, input.observations, input.report, 20000).passed, false);
  }
  const historical = v4();
  assert.equal(analyze4(historical).passed, true);
  assert.throws(() => analyzeV4(historical.seed, historical.observations, historical.report, 20000));
});
test('V4 extra final bracketing sample preserves a proven quiet interval', () => {
  const input = v4();
  input.observations.push({ ...structuredClone(input.observations.at(-1)), observedAt: at(151) });
  assert.equal(analyze4(input).passed, true);
  assert.equal(analyze4(input).drainSecondsUpperBound, 45);
});
test('V4 rejects pre-existing four tasks, pre-expiry-only backlog and growth preceding backlog', () => {
  for (const mutate of [
    x => x.observations.forEach(row => row.notificationTasks = 4),
    x => x.observations.forEach((row, i) => row.notification.visible = i === 1 ? 100 : 0),
    x => x.observations.forEach((row, i) => row.notificationTasks = i >= 2 ? 4 : 1),
    x => x.observations.forEach((row, i) => row.notificationTasks = i < 2 ? 4 : 1),
  ]) {
    const input = v4(); mutate(input);
    assert.equal(analyze4(input).passed, false);
  }
});
test('V4 quiet confirmation restarts on delayed or in-flight messages', () => {
  for (const key of ['visible', 'notVisible', 'delayed']) {
    const input = v4(); input.observations.at(-2).notification[key] = 1;
    assert.equal(analyze4(input).drainObserved, false);
  }
});
test('V4 requires four tasks before the final quiet suffix begins', () => {
  assert.equal(analyze4(v4()).drainAfterScale, true);
  for (const firstFour of [7, 8]) {
    const input = v4();
    input.observations.forEach((row, i) => row.notificationTasks = i >= firstFour ? 4 : 1);
    const result = analyze4(input);
    assert.equal(result.scaleToFourObserved, true);
    assert.equal(result.drainObserved, true);
    assert.equal(result.drainAfterScale, false);
    assert.equal(result.passed, false);
  }
  const noScale = v4();
  noScale.observations.forEach(row => row.notificationTasks = 1);
  assert.equal(analyze4(noScale).drainAfterScale, false);
});
test('V4 refuses missing counters, malformed timestamps, wrong labels and partial observations', () => {
  for (const mutate of [x => x.seed.teams = 0, x => x.seed.toolingSha = 'wrong', x => x.seed.expiresAt = at(61),
    x => x.report.observedAt = at(30), x => x.report.runLabel = 'other',
    x => delete x.report.consumedEvents, x => delete x.observations[0].notification.visible,
    x => x.observations[0].notification.dlqVisible = null, x => x.observations[0].notificationTasks = 0,
    x => x.observations.splice(1, 4), x => x.observations.pop(), x => x.observations[0].observedAt = 'invalid']) {
    const input = v4(); mutate(input); assert.throws(() => analyze4(input));
  }
});
test('missing CloudWatch age remains explicitly unknown, never fabricated as zero', () => {
  const input = v4(); input.observations.forEach(row => row.notification.oldestAgeSeconds = null);
  const result = analyze4(input);
  assert.equal(result.queueAgeCoverageComplete, false);
  assert.equal(result.passed, true);
});

function awsV3() {
  const input = v3();
  const digest = `sha256:${'c'.repeat(64)}`;
  input.report.context = configure({ BASE_URL: 'https://shop.example', CONFIRM_TARGET: 'https://shop.example',
    TEST_SHA: sha, IMAGE_SHA: 'b'.repeat(40), EXPECTED_IMAGE_DIGEST: digest,
    RUN_LABEL: 'v3-fixture', MODE: 'v3', TOPOLOGY: 'distributed', KNEE_RPS: '1' });
  const alb = 'arn:aws:elasticloadbalancing:ap-southeast-1:402178232802:loadbalancer/app/test/123';
  const group = 'arn:aws:elasticloadbalancing:ap-southeast-1:402178232802:targetgroup/test/456';
  const resourceId = 'service/blocbuy-swe5001/commerce-svc';
  input.report.preflight = { schema: 1, account: '402178232802', region: 'ap-southeast-1', cluster: 'blocbuy-swe5001',
    mode: 'v3', baseUrl: 'https://shop.example', collectedAt: at(0), toolingSha: sha,
    services: { failures: [], services: [{ serviceName: 'commerce-svc', status: 'ACTIVE', desiredCount: 1, runningCount: 1,
      pendingCount: 0, taskDefinition: 'revision:1', deployments: [{ rolloutState: 'COMPLETED' }],
      loadBalancers: [{ targetGroupArn: group, containerName: 'commerce-svc', containerPort: 8589 }] }] },
    tasks: { failures: [], tasks: [{ taskArn: 'task:1', taskDefinitionArn: 'revision:1', lastStatus: 'RUNNING', desiredStatus: 'RUNNING',
      containers: [{ name: 'commerce-svc', imageDigest: digest }] }] },
    targets: { ScalableTargets: [{ ResourceId: resourceId, MinCapacity: 1, MaxCapacity: 6,
      SuspendedState: { DynamicScalingInSuspended: false, DynamicScalingOutSuspended: false, ScheduledScalingSuspended: false } }] },
    policies: { ScalingPolicies: [{ ResourceId: resourceId, PolicyType: 'TargetTrackingScaling', TargetTrackingScalingPolicyConfiguration: {
      TargetValue: 60, PredefinedMetricSpecification: { PredefinedMetricType: 'ALBRequestCountPerTarget', ResourceLabel: 'app/test/123/targetgroup/test/456' } } }] },
    routing: { edge: { id: 'E3N39GHVFV6CT3', domainName: 'shop.example', status: 'Deployed', enabled: true,
      behaviors: [{ pathPattern: '/api/*', targetOriginId: 'alb-runtime', functions: 0, lambdas: 0, cachePolicyId: '4135ea2d-6df8-44a3-9df3-4b5a84be39ad' }],
      origins: [{ id: 'alb-runtime', domainName: 'runtime.elb.amazonaws.com', path: '', protocolPolicy: 'http-only', httpPort: 80 }] },
    targetGroups: [{ arn: group, loadBalancerArns: [alb], port: 8589 }],
    loadBalancers: [{ arn: alb, dnsName: 'runtime.elb.amazonaws.com', type: 'application', state: 'active' }],
    listeners: [{ arn: 'listener:1', loadBalancerArn: alb, port: 80, protocol: 'HTTP' }], rulesListenerArn: 'listener:1',
    rules: [{ isDefault: false, hasTransforms: false, hasPathRegex: false, conditionFields: ['path-pattern', 'http-header'],
      pathPatterns: ['/api/*'], originHeaderMatches: true, actions: [{ type: 'forward', targetGroupArn: group }] },
    { isDefault: true, actions: [{ type: 'fixed-response' }] }] } };
  input.observations.forEach(row => row.services[0].taskDefinition = 'revision:1');
  input.report.summary.metrics.browse_requests.values.count = 3750;
  input.samples = Array.from({ length: 3750 }, (_, i) => i * 1800 / 3750).flatMap(seconds => [
    { type: 'Point', metric: 'browse_latency', data: { time: at(seconds), value: seconds >= 60 && seconds < 240 ? 2000 : 100 } },
    { type: 'Point', metric: 'browse_errors', data: { time: at(seconds), value: 0 } },
    { type: 'Point', metric: 'browse_requests', data: { time: at(seconds), value: 1 } },
  ]);
  return input;
}

test('AWS V3 requires the verified route/digest/revision and complete nominal ramp count', () => {
  assert.equal(analyze(awsV3()).hasAwsPreflight, true);
  for (const mutate of [x => x.report.summary.state.testRunDurationMs = 1000,
    x => delete x.report.preflight, x => x.observations[5].services[0].taskDefinition = 'other',
    x => x.report.summary.metrics.browse_requests.values.count = 3000]) {
    const input = awsV3(); mutate(input); assert.throws(() => analyze(input));
  }
});
test('AWS analyser reconstructs the explicitly reviewed policy rather than the workload candidate', () => {
  const input = awsV3();
  input.report.context.scalingRequestsPerTarget = 30;
  input.report.preflight.policies.ScalingPolicies[0].TargetTrackingScalingPolicyConfiguration.TargetValue = 30;
  assert.equal(analyze(input).hasAwsPreflight, true);
  input.report.preflight.policies.ScalingPolicies[0].TargetTrackingScalingPolicyConfiguration.TargetValue = 60;
  assert.throws(() => analyze(input), /explicitly bound target/);
  delete input.report.context.scalingRequestsPerTarget;
  assert.equal(analyze(input).hasAwsPreflight, true);
});

test('V3 analysis retains explicit CPU guard confirmation without changing acceptance gates', () => {
  const input = awsV3();
  const arn = 'arn:aws:autoscaling:ap-southeast-1:402178232802:scalingPolicy:cpu';
  input.report.context.cpuHeadroom = { threshold: 70 };
  input.report.preflight.policies.ScalingPolicies.push({ PolicyName: 'blocbuy-swe5001-commerce-cpu-headroom',
    PolicyARN: arn, ResourceId: 'service/blocbuy-swe5001/commerce-svc', PolicyType: 'StepScaling',
    StepScalingPolicyConfiguration: { AdjustmentType: 'ChangeInCapacity', Cooldown: 60, MetricAggregationType: 'Average',
      StepAdjustments: [{ MetricIntervalLowerBound: 0, ScalingAdjustment: 1 }] } });
  input.report.preflight.cpuHeadroom = { alarms: [{ name: 'blocbuy-swe5001-commerce-cpu-headroom', namespace: 'AWS/ECS',
    metric: 'CPUUtilization', statistic: 'Average', period: 60, evaluationPeriods: 1, datapointsToAlarm: 1, threshold: 70,
    comparison: 'GreaterThanOrEqualToThreshold', missingData: 'notBreaching', actionsEnabled: true, actions: [arn],
    dimensions: [{ Name: 'ClusterName', Value: 'blocbuy-swe5001' }, { Name: 'ServiceName', Value: 'commerce-svc' }] }] };
  assert.equal(analyze(input).latencyAndScaleOutGatesPassed, true);
  assert.equal(analyze(input).acceptanceComplete, false);
  input.report.context.cpuHeadroom = null;
  assert.throws(() => analyze(input), /explicit CPU headroom confirmation/);
  input.report.context.cpuHeadroom = { threshold: 65 };
  assert.throws(() => analyze(input), /CPU headroom policy/);
});

test('analysis CLIs preserve all-run failures, reject fixture acceptance and report failed gates', () => {
  const dir = mkdtempSync(join(tmpdir(), 'blocbuy-analysis-'));
  const file = (name, value, jsonl = false) => {
    const target = join(dir, name);
    writeFileSync(target, jsonl ? value.map(row => JSON.stringify(row)).join('\n') : JSON.stringify(value));
    return target;
  };
  const cli = (name, args) => spawnSync(process.execPath, [resolve(import.meta.dirname, name), ...args], { encoding: 'utf8' });
  try {
    for (const name of ['analyze-v3.mjs', 'analyze-v4.mjs']) assert.equal(cli(name, []).status, 1);
    const input = awsV3();
    const args = [file('summary.json', input.report), file('samples.jsonl', input.samples, true),
      file('observations.jsonl', input.observations, true), file('events.json', input.evidence)];
    const passed = cli('analyze-v3.mjs', args);
    assert.equal(passed.status, 0, passed.stderr);
    assert.equal(JSON.parse(passed.stdout).acceptanceComplete, false);
    input.evidence.events = []; file('events.json', input.evidence);
    assert.equal(cli('analyze-v3.mjs', args).status, 2);
    file('summary.json', v3().report);
    const fixture = cli('analyze-v3.mjs', args);
    assert.equal(fixture.status, 1); assert.equal(fixture.stdout, '');
    const v4input = v4();
    Object.assign(v4input.seed, { account: '402178232802', region: 'ap-southeast-1', cluster: 'blocbuy-swe5001',
      profile: 'demo', commerceDigest: `sha256:${'c'.repeat(64)}`, commerceRevision: 'revision:1' });
    const args4 = [file('seed.json', v4input.seed), file('queue.jsonl', v4input.observations, true), file('report.json', v4input.report)];
    assert.equal(cli('analyze-v4.mjs', args4).status, 0);
    v4input.report.notifications = 1999; file('report.json', v4input.report);
    assert.equal(cli('analyze-v4.mjs', args4).status, 2);
    file('seed.json', v4().seed);
    const fixture4 = cli('analyze-v4.mjs', args4);
    assert.equal(fixture4.status, 1); assert.equal(fixture4.stdout, '');
    const larger = largerV4();
    Object.assign(larger.seed, { account: '402178232802', region: 'ap-southeast-1', cluster: 'blocbuy-swe5001',
      profile: 'demo', commerceDigest: `sha256:${'c'.repeat(64)}`, commerceRevision: 'revision:1' });
    file('seed.json', larger.seed); file('queue.jsonl', larger.observations, true); file('report.json', larger.report);
    assert.equal(cli('analyze-v4.mjs', args4).status, 1);
    const largerArgs = [...args4, '--expected-count', '20000'];
    assert.equal(cli('analyze-v4.mjs', largerArgs).status, 0);
    for (const value of ['20001', '020000', '2e4', '']) assert.equal(cli('analyze-v4.mjs', [...args4, '--expected-count', value]).status, 1);
    assert.equal(cli('analyze-v4.mjs', [...args4, '--expected-count']).status, 1);
    larger.observations.forEach(row => row.notificationTasks = 1); file('queue.jsonl', larger.observations, true);
    assert.equal(cli('analyze-v4.mjs', largerArgs).status, 2);
    delete larger.seed.account; file('seed.json', larger.seed);
    assert.equal(cli('analyze-v4.mjs', largerArgs).status, 1);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});
