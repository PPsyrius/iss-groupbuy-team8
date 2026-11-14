// Read-only steady browse support for #459. This never injects a fault.
import { validateRoute } from '../k6/lib.mjs';

const TARGET = 'https://d3lvq02o2e7tcn.cloudfront.net';
function integer(value, name, min, max) {
  const result = Number(value);
  if (!Number.isInteger(result) || result < min || result > max) throw new Error(`${name} must be ${min}..${max}`);
  return result;
}

export function configure(env) {
  const baseUrl = String(env.BASE_URL || '').replace(/\/$/, '');
  const local = /^http:\/\/(localhost|127\.0\.0\.1|host\.docker\.internal)(:[0-9]+)?$/.test(baseUrl);
  const mode = env.DRILL;
  if (!['v5', 'identity'].includes(mode)) throw new Error('DRILL must be v5 or identity');
  if (local ? env.TOPOLOGY !== 'local-fixture' : baseUrl !== TARGET || env.CONFIRM_TARGET !== TARGET || env.TOPOLOGY !== 'distributed') {
    throw new Error('Use an explicit local fixture or the confirmed SWE5001 distributed target');
  }
  for (const name of ['TEST_SHA', 'IMAGE_SHA']) if (!/^[0-9a-f]{40}$/.test(env[name] || '')) throw new Error(`${name} must be a full SHA`);
  if (!/^[a-zA-Z0-9._-]{1,80}$/.test(env.RUN_LABEL || '')) throw new Error('RUN_LABEL is required');
  if (!local && !/^sha256:[0-9a-f]{64}$/.test(env.EXPECTED_IMAGE_DIGEST || '')) throw new Error('EXPECTED_IMAGE_DIGEST is required');
  if (!local && env.FIXTURE_SECONDS !== undefined) throw new Error('FIXTURE_SECONDS cannot shorten AWS measurements');
  const kneeRps = mode === 'v5' ? integer(env.KNEE_RPS, 'KNEE_RPS', 1, 200) : null;
  const durationSeconds = local ? integer(env.FIXTURE_SECONDS || 10, 'FIXTURE_SECONDS', 2, 30) : mode === 'v5' ? 600 : 900;
  if (durationSeconds % 2 !== 0) throw new Error('Fixture duration must be even');
  const ratePerTwoSeconds = mode === 'v5' ? kneeRps : 4;
  const expectedRequests = ratePerTwoSeconds * durationSeconds / 2;
  const preAllocatedVUs = integer(env.PREALLOCATED_VUS || 100, 'PREALLOCATED_VUS', 1, 1000);
  const maxVUs = integer(env.MAX_VUS || 400, 'MAX_VUS', preAllocatedVUs, 1000);
  const scalingRequestsPerTarget = local ? null : integer(env.SCALING_REQUESTS_PER_TARGET, 'SCALING_REQUESTS_PER_TARGET', 1, 12000);
  return { baseUrl, mode, remote: !local, topology: env.TOPOLOGY, kneeRps, durationSeconds, expectedRequests,
    ratePerTwoSeconds, scalingRequestsPerTarget, testSha: env.TEST_SHA, imageSha: env.IMAGE_SHA,
    expectedImageDigest: env.EXPECTED_IMAGE_DIGEST || null, runLabel: env.RUN_LABEL,
    evidenceKind: local ? 'local-fixture' : 'aws-drill-preflight-required',
    generator: { preAllocatedVUs, maxVUs }, options: {
      scenarios: { browse: { executor: 'constant-arrival-rate', rate: ratePerTwoSeconds, timeUnit: '2s',
        duration: `${durationSeconds + 1}s`, gracefulStop: '15s', preAllocatedVUs, maxVUs } },
      thresholds: { browse_errors: ['rate==0'], browse_non_2xx: ['count==0'], dropped_iterations: ['count==0'],
        browse_requests: [`count>=${expectedRequests}`, `count<=${expectedRequests}`] },
      summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
      systemTags: ['status', 'method', 'name', 'scenario', 'expected_response'],
      setupTimeout: '30s', maxRedirects: 0, tags: { run: env.RUN_LABEL, drill: mode } } };
}

export function validatePreflight(snapshot, config, now = Date.now()) {
  if (!config.remote) return;
  const age = now - Date.parse(snapshot?.collectedAt);
  if (snapshot?.schema !== 1 || snapshot.account !== '402178232802' || snapshot.region !== 'ap-southeast-1' ||
      snapshot.cluster !== 'blocbuy-swe5001' || snapshot.mode !== config.mode || snapshot.baseUrl !== config.baseUrl ||
      snapshot.toolingSha !== config.testSha || !Number.isFinite(age) || age < -30000 || age > 300000) {
    throw new Error('Missing, stale or mismatched drill preflight');
  }
  const current = snapshot.services?.services?.[0];
  const tasks = snapshot.tasks?.tasks;
  if (snapshot.services?.failures?.length !== 0 || snapshot.tasks?.failures?.length !== 0 ||
      snapshot.services?.services?.length !== 1 || current.serviceName !== 'commerce-svc' || current.status !== 'ACTIVE' ||
      current.desiredCount !== 2 || current.runningCount !== 2 || current.pendingCount !== 0 ||
      current.deployments?.length !== 1 || current.deployments[0].rolloutState !== 'COMPLETED' || !current.taskDefinition ||
      tasks?.length !== 2 || new Set(tasks.map(t => t.taskArn)).size !== 2 || tasks.some(t => !t.taskArn ||
        t.taskDefinitionArn !== current.taskDefinition || t.lastStatus !== 'RUNNING' || t.desiredStatus !== 'RUNNING' ||
        t.containers?.filter(c => c.name === 'commerce-svc' && c.imageDigest === config.expectedImageDigest).length !== 1) ||
      tasks.some(t => typeof t.availabilityZone !== 'string' || !t.availabilityZone.startsWith('ap-southeast-1')) ||
      new Set(tasks.map(t => t.availabilityZone)).size !== 2) throw new Error('Require two stable reviewed commerce tasks in two AZs');
  const route = validateRoute(snapshot, current, 'commerce-svc');
  const targets = snapshot.targets?.ScalableTargets;
  const resource = 'service/blocbuy-swe5001/commerce-svc';
  const target = targets?.[0];
  if (targets?.length !== 1 || target.ResourceId !== resource || target.MinCapacity !== 2 || target.MaxCapacity !== 6 ||
      target.SuspendedState?.DynamicScalingInSuspended !== false || target.SuspendedState?.DynamicScalingOutSuspended !== false ||
      target.SuspendedState?.ScheduledScalingSuspended !== false) throw new Error('Require active commerce scaling at the normal 2..6 bounds');
  const policies = snapshot.policies?.ScalingPolicies;
  const policy = policies?.[0], tracking = policy?.TargetTrackingScalingPolicyConfiguration;
  if (policies?.length !== 1 || policy.ResourceId !== resource || policy.PolicyType !== 'TargetTrackingScaling' ||
      tracking?.TargetValue !== config.scalingRequestsPerTarget ||
      tracking?.PredefinedMetricSpecification?.PredefinedMetricType !== 'ALBRequestCountPerTarget' ||
      tracking?.PredefinedMetricSpecification?.ResourceLabel !== route.resourceLabel) {
    throw new Error('Require the reviewed request policy; extra policies need a separate tooling review, not silent removal');
  }
}

export function browseResult(report) {
  const c = report?.context, metrics = report?.summary?.metrics;
  if (report?.schema !== 1 || !['v5', 'identity'].includes(c?.mode) || !Number.isInteger(c.expectedRequests) || c.expectedRequests <= 0) throw new Error('Invalid drill report');
  const count = name => metrics?.[name]?.values?.count;
  const errors = metrics?.browse_errors?.values?.rate;
  if (count('browse_requests') !== c.expectedRequests || count('dropped_iterations') !== 0 ||
      count('browse_non_2xx') !== 0 || errors !== 0) throw new Error('Incomplete browse workload, generator drops or failed responses');
  return { browseGatesPass: true, evidenceKind: c.evidenceKind, requests: c.expectedRequests,
    awsAcceptanceComplete: false, remaining: c.mode === 'v5' ? 'Review fault time, task identities, health and <=120s replacement with continuous observations' :
      'Review actual identity outage, START/webhook failures, database rollback and provider redelivery after restoration' };
}
