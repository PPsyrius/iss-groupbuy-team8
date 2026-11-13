// Pure configuration/evidence logic, shared by k6 and the Node regression tests.
export const BASELINE_RATES = [10, 20, 40, 60, 80, 100, 125, 150, 175, 200];

function integer(value, name, min, max) {
  if (!['number', 'string'].includes(typeof value) || String(value).trim() === '') {
    throw new Error(`${name} is required`);
  }
  const number = Number(value);
  if (!Number.isInteger(number) || number < min || number > max) {
    throw new Error(`${name} must be an integer in ${min}..${max}`);
  }
  return number;
}

export function configure(env) {
  const baseUrl = String(env.BASE_URL || '').replace(/\/$/, '');
  const match = /^(https?):\/\/([a-z0-9.-]+)(?::([0-9]+))?$/i.exec(baseUrl);
  if (!match) throw new Error('BASE_URL must be an origin, without credentials, path, query or fragment');
  const local = ['localhost', '127.0.0.1', 'host.docker.internal'].includes(match[2].toLowerCase());
  if (!local && (match[1] !== 'https' || env.CONFIRM_TARGET !== baseUrl)) {
    throw new Error('Remote load requires HTTPS and CONFIRM_TARGET equal to BASE_URL');
  }
  if (!/^[0-9a-f]{40}$/.test(env.TEST_SHA || '')) throw new Error('TEST_SHA must be the full tooling commit SHA');
  if (!/^[0-9a-f]{40}$/.test(env.IMAGE_SHA || '')) throw new Error('IMAGE_SHA must be the deployed source SHA');
  if (!/^[a-zA-Z0-9._-]{1,80}$/.test(env.RUN_LABEL || '')) throw new Error('RUN_LABEL is required (letters, digits, dot, dash, underscore)');
  const mode = env.MODE || 'v2';
  if (!['v2', 'v3', 'smoke'].includes(mode)) throw new Error('MODE must be v2, v3 or smoke');
  const topology = env.TOPOLOGY;
  if (!['monolith', 'distributed', 'local-fixture'].includes(topology)) throw new Error('TOPOLOGY is required');
  if (mode === 'v2' && topology !== 'monolith') throw new Error('V2 requires the monolith topology (#458)');
  if (mode === 'v3' && topology !== 'distributed') throw new Error('V3 requires the distributed topology');
  if (!local && topology === 'local-fixture') throw new Error('local-fixture cannot describe a remote run');
  const preAllocatedVUs = integer(env.PREALLOCATED_VUS || 100, 'PREALLOCATED_VUS', 1, 1000);
  const maxVUs = integer(env.MAX_VUS || 400, 'MAX_VUS', preAllocatedVUs, 1000);
  const knee = mode === 'v3' ? integer(env.KNEE_RPS, 'KNEE_RPS', 1, 200) : null;
  if (mode !== 'v3' && env.SCALING_REQUESTS_PER_TARGET !== undefined) throw new Error('SCALING_REQUESTS_PER_TARGET belongs only to V3');
  // Policy headroom is independent of the unchanged offered-load candidate.
  const scalingRequestsPerTarget = mode === 'v3'
    ? integer(env.SCALING_REQUESTS_PER_TARGET === undefined ? knee * 60 : env.SCALING_REQUESTS_PER_TARGET, 'SCALING_REQUESTS_PER_TARGET', 1, 12000) : null;
  if (env.CONFIRM_CPU_HEADROOM !== undefined && !['true', 'false'].includes(env.CONFIRM_CPU_HEADROOM)) {
    throw new Error('CONFIRM_CPU_HEADROOM must explicitly be true or false');
  }
  const cpuHeadroom = env.CONFIRM_CPU_HEADROOM === 'true'
    ? { threshold: integer(env.CPU_HEADROOM_THRESHOLD || 70, 'CPU_HEADROOM_THRESHOLD', 1, 99) } : null;
  if (cpuHeadroom && mode !== 'v3') throw new Error('CPU headroom confirmation belongs only to V3');
  const rate = mode === 'smoke' ? 2 : null;
  const remote = !local;
  if (remote && mode === 'smoke') throw new Error('Smoke mode is local-fixture-only; remote runs require a V2/V3 preflight');
  const stageSeconds = integer(env.FIXTURE_STAGE_SECONDS || 60, 'FIXTURE_STAGE_SECONDS', 1, 60);
  if (remote && env.FIXTURE_STAGE_SECONDS !== undefined) throw new Error('Short schedules are allowed only on local fixtures');
  if (remote && !/^sha256:[0-9a-f]{64}$/.test(env.EXPECTED_IMAGE_DIGEST || '')) {
    throw new Error('Remote load requires the reviewed EXPECTED_IMAGE_DIGEST');
  }
  // Leave time after the 10s HTTP timeout to record the final request metrics.
  const common = { timeUnit: '1s', preAllocatedVUs, maxVUs, gracefulStop: '15s' };
  let scenarios;
  if (mode === 'v2') {
    // One executor owns one VU pool, not ten independently preallocated pools.
    const stages = BASELINE_RATES.flatMap((rps, index) => index === 0
      ? [{ target: rps, duration: `${stageSeconds}s` }]
      : [{ target: rps, duration: '0s' }, { target: rps, duration: `${stageSeconds}s` }]);
    // k6 can stop just before the final arrival without reporting a drop.
    // Keep its scheduler alive briefly; browse.js caps HTTP at the exact budget.
    stages.push({ target: BASELINE_RATES[BASELINE_RATES.length - 1], duration: '1s' });
    scenarios = { baseline: { ...common, executor: 'ramping-arrival-rate', startRate: BASELINE_RATES[0], stages } };
  } else if (mode === 'smoke') {
    scenarios = { smoke: { ...common, executor: 'constant-arrival-rate', rate, duration: '10s', tags: { plateau: '0' } } };
  } else {
    scenarios = { scaling: { ...common, executor: 'ramping-arrival-rate', startRate: Math.max(1, Math.round(knee * 0.2)),
      stages: [{ target: knee, duration: '5m' }, { target: knee * 3, duration: '10m' },
        { target: knee * 3, duration: '10m' }, { target: 0, duration: '5m' }] } };
  }
  const thresholds = { browse_errors: ['rate<0.01'], dropped_iterations: ['count==0'] };
  if (mode !== 'v2') thresholds.browse_latency = [`p(95)<${mode === 'smoke' ? 500 : 800}`];
  if (mode === 'v2') {
    BASELINE_RATES.forEach((rps, index) => {
      // Threshold selectors materialize per-plateau statistics in handleSummary.
      thresholds[`browse_latency{plateau:${index}}`] = ['p(95)<500'];
      thresholds[`browse_errors{plateau:${index}}`] = ['rate<0.01'];
      thresholds[`browse_requests{plateau:${index}}`] = [`count>=${rps * stageSeconds}`];
    });
    const expected = baselineRequestLimit(stageSeconds);
    thresholds.browse_requests = [`count>=${expected}`, `count<=${expected}`];
  }
  return { baseUrl, mode, topology, knee, scalingRequestsPerTarget, remote, stageSeconds,
    generator: { preAllocatedVUs, maxVUs }, cpuHeadroom,
    evidenceKind: remote ? 'aws-preflight-required' : 'local-fixture', expectedImageDigest: env.EXPECTED_IMAGE_DIGEST || null,
    testSha: env.TEST_SHA, imageSha: env.IMAGE_SHA, runLabel: env.RUN_LABEL,
    options: { scenarios, thresholds, summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
      systemTags: ['status', 'method', 'name', 'scenario', 'expected_response'],
      setupTimeout: '30s', maxRedirects: 0, tags: { run: env.RUN_LABEL } } };
}

export function productIds(body) {
  if (body?.code !== 'COMMON_200_000' || !Array.isArray(body?.data?.items) || !body.data.items.length) {
    throw new Error('Catalogue setup must return a non-empty Result page');
  }
  const ids = body.data.items.map(item => item.id);
  if (!ids.every(id => typeof id === 'string' && /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(id))) {
    throw new Error('Catalogue contains an invalid product UUID');
  }
  return ids;
}

export function validResponse(body, detail) {
  return body?.code === 'COMMON_200_000' && (detail
    ? typeof body?.data?.id === 'string'
    : Array.isArray(body?.data?.items) && body.data.items.length > 0);
}

export function browseChoice(iteration, ids) {
  const slot = iteration % 10;
  const detail = slot >= 7;
  // Advance through all IDs instead of always reading IDs 7/8/9 for a ten-row seed.
  const detailIndex = Math.floor(iteration / 10) * 3 + slot - 7;
  return { detail, id: ids[detail ? detailIndex % ids.length : 0] };
}

export function plateauForIteration(iteration, stageSeconds = 60) {
  let boundary = 0;
  for (let index = 0; index < BASELINE_RATES.length - 1; index++) {
    boundary += BASELINE_RATES[index] * stageSeconds;
    if (iteration < boundary) return index;
  }
  return BASELINE_RATES.length - 1;
}

export function baselineRequestLimit(stageSeconds = 60) {
  return BASELINE_RATES.reduce((sum, rps) => sum + rps * stageSeconds, 0);
}

export function validateRoute(snapshot, current, service) {
  const route = snapshot.routing;
  const edge = route?.edge;
  const reject = () => { throw new Error('Unverified CloudFront/ALB route to the observed ECS service'); };
  // Validate the declared one-behavior topology, not a partial simulation of
  // arbitrary CloudFront matching/rewrites. Extra behaviors fail closed.
  if (edge?.id !== 'E3N39GHVFV6CT3' || edge.status !== 'Deployed' || edge.enabled !== true ||
      `https://${edge.domainName}` !== snapshot.baseUrl || edge.behaviors?.length !== 1) reject();
  const behavior = edge.behaviors[0];
  if (behavior.pathPattern !== '/api/*' || behavior.targetOriginId !== 'alb-runtime' ||
      behavior.functions !== 0 || behavior.lambdas !== 0 ||
      behavior.cachePolicyId !== '4135ea2d-6df8-44a3-9df3-4b5a84be39ad') reject();
  const origins = edge.origins?.filter(o => o.id === behavior.targetOriginId);
  if (origins?.length !== 1 || (origins[0].path ?? '') !== '') reject();
  const origin = origins[0];
  const attachment = current.loadBalancers?.[0];
  if (current.loadBalancers?.length !== 1 || attachment.containerName !== service || !attachment.targetGroupArn ||
      !Number.isInteger(attachment.containerPort) || attachment.containerPort < 1 || attachment.containerPort > 65535) reject();
  const group = route.targetGroups?.[0];
  if (route.targetGroups?.length !== 1 || group.arn !== attachment.targetGroupArn ||
      group.port !== attachment.containerPort || group.loadBalancerArns?.length !== 1) reject();
  const alb = route.loadBalancers?.[0];
  if (route.loadBalancers?.length !== 1 || alb.arn !== group.loadBalancerArns[0] ||
      alb.type !== 'application' || alb.state !== 'active' || typeof alb.dnsName !== 'string' ||
      typeof origin.domainName !== 'string' || alb.dnsName.toLowerCase() !== origin.domainName.toLowerCase()) reject();
  const protocol = origin.protocolPolicy === 'http-only' ? 'HTTP'
    : ['https-only', 'match-viewer'].includes(origin.protocolPolicy) ? 'HTTPS' : null;
  const port = protocol === 'HTTP' ? origin.httpPort : origin.httpsPort;
  const listeners = route.listeners?.filter(l => l.loadBalancerArn === alb.arn && l.protocol === protocol && l.port === port);
  if (!protocol || !Number.isInteger(port) || listeners?.length !== 1 ||
      listeners[0].arn !== route.rulesListenerArn || !Array.isArray(route.rules) ||
      route.rules.filter(r => r.isDefault === true).length !== 1) reject();
  const forwardsToGroup = rule => {
    if (rule.actions?.length !== 1 || rule.actions[0].type !== 'forward') return false;
    const action = rule.actions[0];
    if (action.targetGroupArn && action.targetGroupArn !== group.arn) return false;
    const groups = action.forwardGroups;
    if (groups?.length) return groups.length === 1 && groups[0].arn === group.arn && (groups[0].weight ?? 1) > 0;
    return action.targetGroupArn === group.arn;
  };
  const matches = route.rules.filter(r => r.isDefault === false && r.hasTransforms === false && r.hasPathRegex === false &&
    r.pathPatterns?.length === 1 && r.pathPatterns[0] === '/api/*' &&
    r.conditionFields?.length === 2 && r.conditionFields.includes('path-pattern') &&
    r.conditionFields.includes('http-header') && r.originHeaderMatches === true && forwardsToGroup(r));
  if (matches.length !== 1) reject();
  // Other declared service routes cannot match any public product-list/detail
  // path. Reject unknown/overlapping patterns (even lower-priority ones) rather
  // than guessing around headers, regex, weighted forwards or URL rewrites.
  const disjointPaths = ['/internal/*', '/api/internal/*', '/api/v1/auth/*', '/api/v1/users/*', '/api/v1/notifications*'];
  for (const rule of route.rules) {
    if (rule.isDefault === true || rule === matches[0]) continue;
    if (rule.isDefault !== false || rule.hasPathRegex !== false || !rule.conditionFields?.includes('path-pattern') ||
        !rule.pathPatterns?.length || !rule.pathPatterns.every(path => disjointPaths.includes(path))) reject();
  }
  if (typeof alb.arn !== 'string' || !alb.arn.includes(':loadbalancer/') || !group.arn.includes(':targetgroup/')) reject();
  return { resourceLabel: `${alb.arn.split(':loadbalancer/')[1]}/${group.arn.split(':').pop()}` };
}

export function validatePreflight(snapshot, config, now = Date.now()) {
  if (!config.remote) return null;
  if (!snapshot || snapshot.schema !== 1 || snapshot.account !== '402178232802' ||
      snapshot.region !== 'ap-southeast-1' || snapshot.cluster !== 'blocbuy-swe5001' || snapshot.mode !== config.mode ||
      snapshot.baseUrl !== config.baseUrl) {
    throw new Error('Missing or mismatched AWS preflight identity');
  }
  const age = now - Date.parse(snapshot.collectedAt);
  if (!Number.isFinite(age) || age < -30000 || age > 300000) throw new Error('AWS preflight must be collected within five minutes before load');
  if (snapshot.toolingSha !== config.testSha) throw new Error('Preflight tooling SHA does not match this run');
  const service = config.mode === 'v2' ? 'monolith' : 'commerce-svc';
  const services = snapshot.services;
  const tasks = snapshot.tasks;
  if (!Array.isArray(services?.failures) || services.failures.length || !Array.isArray(tasks?.failures) || tasks.failures.length ||
      services?.services?.length !== 1 || tasks?.tasks?.length !== 1) throw new Error('Preflight must contain exactly one service and one task, with no API failures');
  const current = services.services[0];
  const task = tasks.tasks[0];
  if (current.serviceName !== service || current.status !== 'ACTIVE' || current.desiredCount !== 1 ||
      current.runningCount !== 1 || current.pendingCount !== 0 || current.deployments?.length !== 1 ||
      current.deployments[0].rolloutState !== 'COMPLETED' || !current.taskDefinition ||
      task.taskDefinitionArn !== current.taskDefinition || !task.taskArn || typeof task.taskArn !== 'string' ||
      task.lastStatus !== 'RUNNING' || task.desiredStatus !== 'RUNNING') {
    throw new Error('Preflight requires a stable one-task deployment of the expected service');
  }
  const containers = task.containers?.filter(c => c.name === service);
  if (containers?.length !== 1 || containers[0].imageDigest !== config.expectedImageDigest) {
    throw new Error('Live task digest does not match the reviewed image');
  }
  const routing = validateRoute(snapshot, current, service);
  const resourceId = `service/blocbuy-swe5001/${service}`;
  const targets = snapshot.targets?.ScalableTargets;
  const policies = snapshot.policies?.ScalingPolicies;
  if (!Array.isArray(targets) || !Array.isArray(policies) || targets.some(t => t.ResourceId !== resourceId)) throw new Error('Missing or mismatched scaling evidence');
  if (config.mode === 'v2') {
    if (targets.length > 1 || (targets.length === 0 && policies.length > 0) || targets.some(t =>
      t.SuspendedState?.DynamicScalingInSuspended !== true || t.SuspendedState?.DynamicScalingOutSuspended !== true ||
      t.SuspendedState?.ScheduledScalingSuspended !== true)) throw new Error('V2 scaling must be absent or fully suspended');
  } else {
    const target = targets[0];
    const trackingPolicies = policies.filter(p => p.PolicyType === 'TargetTrackingScaling');
    const policy = trackingPolicies[0];
    const tracking = policy?.TargetTrackingScalingPolicyConfiguration;
    if (targets.length !== 1 || target.MinCapacity !== 1 || target.MaxCapacity !== 6 ||
        target.SuspendedState?.DynamicScalingInSuspended !== false || target.SuspendedState?.DynamicScalingOutSuspended !== false ||
        target.SuspendedState?.ScheduledScalingSuspended !== false || trackingPolicies.length !== 1 ||
        policy.ResourceId !== resourceId || policy.PolicyType !== 'TargetTrackingScaling' ||
        tracking?.PredefinedMetricSpecification?.PredefinedMetricType !== 'ALBRequestCountPerTarget' ||
        tracking?.PredefinedMetricSpecification?.ResourceLabel !== routing.resourceLabel ||
        tracking?.TargetValue !== config.scalingRequestsPerTarget) throw new Error('V3 requires active 1..6 HTTP scaling at the explicitly bound target');
    const extra = policies.filter(p => p !== policy);
    if (!config.cpuHeadroom && extra.length) throw new Error('Additional scaling policy requires explicit CPU headroom confirmation');
    if (config.cpuHeadroom) {
      const cpu = extra[0], step = cpu?.StepScalingPolicyConfiguration, adjustment = step?.StepAdjustments?.[0];
      const alarms = snapshot.cpuHeadroom?.alarms, alarm = alarms?.[0];
      const dimensions = alarm?.dimensions;
      if (extra.length !== 1 || cpu.PolicyName !== 'blocbuy-swe5001-commerce-cpu-headroom' ||
          cpu.ResourceId !== resourceId || cpu.PolicyType !== 'StepScaling' ||
          typeof cpu.PolicyARN !== 'string' || !cpu.PolicyARN.startsWith('arn:aws:autoscaling:ap-southeast-1:402178232802:scalingPolicy:') ||
          step?.AdjustmentType !== 'ChangeInCapacity' || step.Cooldown !== 60 || step.MetricAggregationType !== 'Average' ||
          step.StepAdjustments?.length !== 1 || adjustment?.ScalingAdjustment !== 1 ||
          adjustment.MetricIntervalLowerBound !== 0 || adjustment.MetricIntervalUpperBound != null ||
          alarms?.length !== 1 || alarm.name !== 'blocbuy-swe5001-commerce-cpu-headroom' ||
          alarm.namespace !== 'AWS/ECS' || alarm.metric !== 'CPUUtilization' || alarm.statistic !== 'Average' ||
          alarm.period !== 60 || alarm.evaluationPeriods !== 1 || alarm.datapointsToAlarm !== 1 ||
          alarm.threshold !== config.cpuHeadroom.threshold || alarm.comparison !== 'GreaterThanOrEqualToThreshold' ||
          alarm.missingData !== 'notBreaching' || alarm.actionsEnabled !== true ||
          alarm.actions?.length !== 1 || alarm.actions[0] !== cpu.PolicyARN || dimensions?.length !== 2 ||
          dimensions.filter(d => d.Name === 'ClusterName' && d.Value === 'blocbuy-swe5001').length !== 1 ||
          dimensions.filter(d => d.Name === 'ServiceName' && d.Value === service).length !== 1) {
        throw new Error('CPU headroom policy/alarm is not bound to the reviewed commerce-only scale-out guard');
      }
    }
  }
  return { service, taskArn: task.taskArn, imageDigest: containers[0].imageDigest, collectedAt: snapshot.collectedAt };
}

export function baselineResult(report) {
  if (report?.schema !== 1 || report?.context?.mode !== 'v2' || report.context.topology !== 'monolith') {
    throw new Error('Expected a V2 monolith report');
  }
  const context = report.context;
  const verifiedConfig = configure({ BASE_URL: context.baseUrl, CONFIRM_TARGET: context.baseUrl, MODE: 'v2', TOPOLOGY: 'monolith',
    TEST_SHA: context.testSha, IMAGE_SHA: context.imageSha, RUN_LABEL: context.runLabel,
    EXPECTED_IMAGE_DIGEST: context.expectedImageDigest,
    ...(!context.remote && context.stageSeconds !== undefined ? { FIXTURE_STAGE_SECONDS: context.stageSeconds } : {}) });
  const metrics = report.summary?.metrics;
  if (!metrics) throw new Error('Missing legacy k6 metrics; use the pinned runner');
  const value = (metric, field) => metrics[metric]?.values?.[field];
  const drops = value('dropped_iterations', 'count');
  if (!Number.isFinite(drops) || drops !== 0) throw new Error('Dropped iterations invalidate calibration; increase generator capacity and rerun');
  const plateaus = BASELINE_RATES.map((rps, index) => {
    const suffix = `{plateau:${index}}`;
    const count = value(`browse_requests${suffix}`, 'count');
    const p95 = value(`browse_latency${suffix}`, 'p(95)');
    const errors = value(`browse_errors${suffix}`, 'rate');
    const expected = rps * verifiedConfig.stageSeconds;
    const endBoundary = index === BASELINE_RATES.length - 1 ? 1 : 0;
    if (![count, p95, errors].every(Number.isFinite) || !Number.isInteger(count) ||
        count < expected || count > expected + endBoundary || p95 < 0 || errors < 0 || errors > 1) {
      throw new Error(`Incomplete or invalid plateau ${index}; never calibrate a partial run`);
    }
    return { rps, completed: count, p95Ms: p95, errorRate: errors, passed: p95 < 500 && errors < 0.01 };
  });
  const total = value('browse_requests', 'count');
  if (!Number.isInteger(total) || total !== plateaus.reduce((sum, p) => sum + p.completed, 0)) {
    throw new Error('Missing or inconsistent total request count');
  }
  // A later isolated pass cannot erase an earlier saturation point.
  let knee = null;
  for (const plateau of plateaus) {
    if (!plateau.passed) break;
    knee = plateau.rps;
  }
  if (knee === null) throw new Error('No passing baseline plateau; no scaling target can be recommended');
  if (verifiedConfig.remote) {
    const duration = report.summary?.state?.testRunDurationMs;
    // k6's reported execution duration can be milliseconds shorter than its
    // nominal schedule. Exact sample counts and zero drops remain mandatory.
    if (!Number.isFinite(duration) || duration < 599000 || !Number.isFinite(Date.parse(report.finishedAt))) {
      throw new Error('Missing full-duration run timing');
    }
    validatePreflight(report.preflight, verifiedConfig, Date.parse(report.finishedAt) - duration);
  }
  return { schema: 1, context: report.context, hasAwsPreflight: verifiedConfig.remote, requiresOperatorReview: true,
    kneeRps: knee, requestsPerTargetPerMinute: knee * 60,
    saturationObserved: plateaus.some(p => !p.passed),
    note: 'Candidate derived from monolith browse capacity; distributed V3 must validate it. No automatic AWS/GitHub writes.', plateaus };
}

export function connectionBudget(input) {
  const maxConnections = integer(input.maxConnections, 'maxConnections', 1, 100000);
  const reservedConnections = integer(input.reservedConnections, 'reservedConnections', 0, maxConnections);
  const ownerConnections = integer(input.ownerConnections, 'ownerConnections', 1, maxConnections);
  const operatorConnections = integer(input.operatorConnections, 'operatorConnections', 1, maxConnections);
  const headroomConnections = integer(input.headroomConnections, 'headroomConnections', 1, maxConnections);
  if (!Array.isArray(input.services) || input.services.length !== 3 ||
      new Set(input.services.map(s => s.name)).size !== 3 ||
      !['identity-svc', 'commerce-svc', 'notification-svc'].every(name => input.services.some(s => s.name === name))) {
    throw new Error('Budget must include each of the three distributed services exactly once');
  }
  const services = input.services.map(service => ({ name: service.name,
    maxTasks: integer(service.maxTasks, `${service.name}.maxTasks`, 1, service.name === 'commerce-svc' ? 6 : service.name === 'notification-svc' ? 4 : 1),
    poolSize: integer(service.poolSize, `${service.name}.poolSize`, 1, 1000) }));
  const pooledConnections = services.reduce((sum, service) => sum + service.maxTasks * service.poolSize, 0);
  const required = pooledConnections + ownerConnections + operatorConnections + reservedConnections + headroomConnections;
  return { maxConnections, reservedConnections, ownerConnections, operatorConnections, headroomConnections,
    services, pooledConnections, required, remaining: maxConnections - required, fits: required <= maxConnections };
}
