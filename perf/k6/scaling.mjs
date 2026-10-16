import { configure, validatePreflight } from './lib.mjs';

function timestamp(value, name) {
  const time = typeof value === 'string' ? Date.parse(value) : NaN;
  if (!Number.isFinite(time)) throw new Error(`Missing or invalid ${name}`);
  return time;
}

function count(value, name, max = Number.MAX_SAFE_INTEGER) {
  if (!Number.isSafeInteger(value) || value < 0 || value > max) throw new Error(`Invalid ${name}`);
  return value;
}

function orderedObservations(rows, start, end) {
  if (!Array.isArray(rows) || rows.length < 2) throw new Error('Missing observation coverage');
  const times = rows.map(row => timestamp(row.observedAt, 'observation timestamp'));
  if (times[0] > start || times.at(-1) < end || times.some((time, i) => i > 0 && (time <= times[i - 1] || time - times[i - 1] > 45000))) {
    throw new Error('Observations must bracket execution with strictly ordered gaps <=45s');
  }
  return times;
}

function verifiedV3(report) {
  if (report?.schema !== 1 || report.context?.mode !== 'v3') throw new Error('Expected a V3 report');
  const context = report.context;
  const config = configure({ BASE_URL: context.baseUrl, CONFIRM_TARGET: context.baseUrl,
    TEST_SHA: context.testSha, IMAGE_SHA: context.imageSha, EXPECTED_IMAGE_DIGEST: context.expectedImageDigest,
    RUN_LABEL: context.runLabel, MODE: 'v3', TOPOLOGY: context.topology, KNEE_RPS: String(context.knee) });
  const end = timestamp(report.finishedAt, 'run finish');
  const duration = report.summary?.state?.testRunDurationMs;
  if (!Number.isFinite(duration) || duration <= 0 || (config.remote && duration < 1799000)) throw new Error('Incomplete V3 timing');
  validatePreflight(report.preflight, config, end - duration);
  if (count(report.summary?.metrics?.dropped_iterations?.values?.count, 'dropped iterations') !== 0) throw new Error('Generator drops invalidate V3 evidence');
  return { config, start: end - duration, end };
}

/** Validate evidence before reading a potentially large k6 JSONL sample file. */
export function v3Accumulator(report, evidence) {
  const { config, start, end } = verifiedV3(report);
  if (evidence?.schema !== 1 || evidence.toolingSha !== config.testSha || evidence.runLabel !== config.runLabel || !Array.isArray(evidence.events)) {
    throw new Error('Scale-out evidence must identify the same run and tooling SHA');
  }
  const ids = new Set();
  const windows = evidence.events.map(event => {
    const at = timestamp(event.at, 'scale-out timestamp');
    if (event.source !== 'application-autoscaling' || typeof event.activityId !== 'string' || !event.activityId || ids.has(event.activityId) ||
        count(event.from, 'previous task count', 6) < 1 || count(event.to, 'new task count', 6) <= event.from || at < start || at > end) {
      throw new Error('Invalid or duplicate sourced scale-out event');
    }
    ids.add(event.activityId);
    return { start: at, end: at + 180000, activityId: event.activityId, from: event.from, to: event.to };
  }).sort((a, b) => a.start - b.start);
  const outside = [];
  let requests = 0, latencies = 0, errorSamples = 0, errors = 0, excluded = 0;
  return {
    add(sample) {
      if (sample.type !== 'Point' || !['browse_latency', 'browse_errors', 'browse_requests'].includes(sample.metric)) return;
      const at = timestamp(sample.data?.time, 'sample timestamp');
      const value = sample.data?.value;
      if (at < start - 1000 || at > end + 1000 || !Number.isFinite(value) || value < 0) throw new Error('Invalid or out-of-run browse sample');
      if (sample.metric === 'browse_requests') {
        if (value !== 1) throw new Error('Expected one request per browse iteration');
        requests++;
      } else if (sample.metric === 'browse_errors') {
        if (value !== 0 && value !== 1) throw new Error('Invalid browse error sample');
        errorSamples++; errors += value;
      } else {
        latencies++;
        if (windows.some(window => at >= window.start && at < window.end)) excluded++;
        else outside.push(value);
      }
    },
    finish(observations) {
      orderedObservations(observations, start, end);
      const revision = report.preflight?.services?.services?.[0]?.taskDefinition;
      const running = observations.map(row => {
        if (!Array.isArray(row.services) || row.services.length !== 1 || !Array.isArray(row.failures) || row.failures.length) throw new Error('Missing or failed service observation');
        const service = row.services[0];
        if (service.name !== 'commerce-svc' || count(service.running, 'running count', 6) < 1 ||
            count(service.desired, 'desired count', 6) < 1 || count(service.pending, 'pending count', 6) > 6 ||
            (config.remote && service.taskDefinition !== revision)) throw new Error('Unverified commerce revision or count');
        return service.running;
      });
      const total = count(report.summary?.metrics?.browse_requests?.values?.count, 'summary request count');
      const rate = report.summary?.metrics?.browse_errors?.values?.rate;
      const expectedRequests = 150 * Math.max(1, Math.round(config.knee * 0.2)) + 3600 * config.knee;
      // The ramp's continuous arrival integral can differ by one boundary arrival.
      // Never accept a materially under-driven V3 merely because its samples agree.
      if (config.remote && Math.abs(total - expectedRequests) > 1) throw new Error('Incomplete V3 arrival schedule');
      if (!total || requests !== total || latencies !== total || errorSamples !== total || !outside.length ||
          !Number.isFinite(rate) || Math.abs(rate - errors / total) > 1e-9) throw new Error('Incomplete/inconsistent raw samples or no latency outside exclusions');
      outside.sort((a, b) => a - b);
      const index = (outside.length - 1) * 0.95;
      const lower = Math.floor(index), upper = Math.ceil(index);
      const p95 = outside[lower] + (outside[upper] - outside[lower]) * (index - lower);
      const scaleOutObserved = windows.some(window => observations.some((row, i) => timestamp(row.observedAt, 'observation') >= window.start && running[i] >= window.to));
      return { schema: 1, runLabel: config.runLabel, toolingSha: config.testSha, hasAwsPreflight: config.remote,
        requests: total, expectedRequests, errors, errorRate: errors / total, excludedLatencySamples: excluded,
        evaluatedLatencySamples: outside.length, outsideWindowP95Ms: p95, exclusionSeconds: 180,
        windows: windows.map(w => ({ ...w, start: new Date(w.start).toISOString(), end: new Date(w.end).toISOString() })),
        maxObservedRunningTasks: Math.max(...running), scaleOutObserved,
        httpGatesPassed: errors / total < 0.01 && p95 < 800,
        latencyAndScaleOutGatesPassed: errors / total < 0.01 && p95 < 800 && scaleOutObserved,
        originalAllRunLatencyThresholds: report.summary.metrics.browse_latency?.thresholds ?? null,
        acceptanceComplete: false,
        remaining: 'Requires reviewed connection budget, ALB throughput/one-to-four-task linearity, generator resources, post-run route/digest checks and dashboard evidence. Offered rate is not capacity.' };
    },
  };
}

/** Analyze sampled queue drain plus exact source/effect counts; empty queues alone never pass. */
export function analyzeV4(seed, observations, report) {
  if (seed?.schema !== 1 || seed.evidenceKind !== 'demo-expiry-seed' || seed.teams !== 2000 || seed.members !== 2000 ||
      typeof seed.runLabel !== 'string' || !/^[a-zA-Z0-9-]{1,24}$/.test(seed.runLabel) || !/^[0-9a-f]{40}$/.test(seed.toolingSha || '')) throw new Error('Invalid V4 seed receipt');
  const start = timestamp(seed.seededAt, 'seed timestamp'), expires = timestamp(seed.expiresAt, 'expiry timestamp');
  const end = timestamp(report?.observedAt, 'database report timestamp');
  if (expires - start !== 60000 || end < expires || report.schema !== 1 || report.runLabel !== seed.runLabel) throw new Error('Mismatched V4 report or timing');
  const times = orderedObservations(observations, start, end);
  const values = observations.map(row => {
    const q = row.notification;
    if (!q) throw new Error('Missing notification queue evidence');
    for (const key of ['visible', 'notVisible', 'delayed', 'dlqVisible', 'dlqNotVisible', 'dlqDelayed']) count(q[key], key);
    if (q.oldestAgeSeconds !== null) count(q.oldestAgeSeconds, 'oldestAgeSeconds');
    if (count(row.notificationTasks, 'notification running count', 4) < 1) throw new Error('Missing running consumer');
    return q;
  });
  const empty = q => q.visible === 0 && q.notVisible === 0 && q.delayed === 0;
  const dlqEmpty = q => q.dlqVisible === 0 && q.dlqNotVisible === 0 && q.dlqDelayed === 0;
  const quietStart = empty(values[0]) && dlqEmpty(values[0]) && observations[0].notificationTasks === 1;
  // Ignore pre-expiry activity: it cannot have been caused by these teams.
  const backlogIndex = values.findIndex((q, i) => times[i] >= expires && !empty(q));
  const backlogObserved = backlogIndex >= 0;
  const scaleIndex = observations.findIndex((row, i) => backlogObserved && i > backlogIndex && row.notificationTasks === 4);
  const scaleToFourObserved = quietStart && scaleIndex >= 0 &&
    observations.slice(0, backlogIndex + 1).every(row => row.notificationTasks < 4);
  // Use the entire final quiet suffix, not exactly three rows: a final bracketing
  // sample must not erase an already-established thirty-second quiet interval.
  let quietIndex = values.length;
  while (quietIndex > 0 && empty(values[quietIndex - 1]) && times[quietIndex - 1] >= expires) quietIndex--;
  const drainObserved = backlogObserved && quietIndex > backlogIndex && values.length - quietIndex >= 3 &&
    times.at(-1) - times[quietIndex] >= 30000;
  const drainAfterScale = scaleIndex >= 0 && quietIndex > scaleIndex;
  const sourceAndEffectsMatch = ['teams', 'expiredTeams', 'outboxEvents', 'publishedEvents', 'notifications', 'consumedEvents']
    .map(key => count(report[key], key)).every(value => value === 2000) && count(report.failedEvents, 'failed outbox events') === 0;
  const dlqStayedEmpty = values.every(dlqEmpty);
  return { schema: 1, runLabel: seed.runLabel, toolingSha: seed.toolingSha, quietStart, sourceAndEffectsMatch,
    hasAwsDeploymentEvidence: seed.account === '402178232802' && seed.region === 'ap-southeast-1' && seed.cluster === 'blocbuy-swe5001' &&
      seed.profile === 'demo' && /^sha256:[0-9a-f]{64}$/.test(seed.commerceDigest || '') && typeof seed.commerceRevision === 'string' && !!seed.commerceRevision,
    backlogObserved, scaleToFourObserved, drainObserved, drainAfterScale, dlqStayedEmpty,
    queueAgeCoverageComplete: values.every(q => q.oldestAgeSeconds !== null),
    drainSecondsUpperBound: drainObserved ? (times[quietIndex] - expires) / 1000 : null,
    passed: quietStart && sourceAndEffectsMatch && backlogObserved && scaleToFourObserved && drainObserved && drainAfterScale && dlqStayedEmpty,
    note: 'Queue attributes are approximate sampled observations, not a guarantee of future emptiness; retain source/effect reports, task graphs and original CloudWatch evidence.' };
}
