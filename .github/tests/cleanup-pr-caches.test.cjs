const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { test } = require('node:test');
const cleanup = require('../scripts/cleanup-pr-caches.cjs');

// Exercise the same module called by the workflow, without tokens or live writes.
async function run({ event = 'workflow_dispatch', inputs, number = 2, caches = [],
  states = {}, failure, listFailurePage, readFailure, ref = 'refs/heads/dev', action = 'closed', trace = [] } = {}) {
  const deleted = [];
  const lookups = [];
  const queries = [];
  const operations = trace;
  let summary;
  const github = {
    rest: {
      actions: {
        getActionsCacheList: async (params) => {
          queries.push(params);
          operations.push(`list:${params.page}`);
          if (params.page === listFailurePage) throw new Error('Cache listing failed');
          // Model pagination against mutable inventory: deletion would shift pages.
          const visible = caches.filter((cache) => !deleted.includes(cache.id));
          const start = (params.page - 1) * params.per_page;
          return { data: { actions_caches: visible.slice(start, start + params.per_page) } };
        },
        deleteActionsCacheById: async ({ cache_id }) => {
          operations.push(`delete:${cache_id}`);
          if (failure) throw Object.assign(new Error('API failure'), { status: failure });
          deleted.push(cache_id);
        },
      },
      pulls: { get: async ({ pull_number }) => {
        lookups.push(pull_number);
        if (readFailure) throw new Error('PR lookup failed');
        return { data: { state: states[pull_number] ?? 'open' } };
      } },
    },
  };
  const core = {
    info() {},
    summary: {
      addHeading() { return this; },
      addRaw(value) { summary = value; return this; },
      async write() {},
    },
  };
  await cleanup({ github, context: {
    repo: { owner: 'test', repo: 'test' }, eventName: event, ref,
    payload: { inputs, action, pull_request: { number }, repository: { default_branch: 'dev' } },
  }, core });
  return { deleted, lookups, queries, operations, summary };
}

const cache = (id, ref) => ({ id, ref, size_in_bytes: 100 });
const fixtures = [
  cache(1, 'refs/heads/dev'), cache(2, 'refs/heads/prod'),
  cache(3, 'refs/tags/v1'), cache(4, 'refs/pull/1/merge'),
  cache(5, 'refs/pull/2/merge'), cache(6, 'refs/pull/2/merge'),
  cache(7, 'refs/pull/2/head'), cache(8, 'refs/pull/2/merge/extra'),
];

test('manual cleanup defaults to dry run and preserves open PRs and non-merge refs', async () => {
  const result = await run({ caches: fixtures, states: { 2: 'closed' } });
  assert.deepEqual(result.deleted, []);
  assert.deepEqual(result.lookups, [1, 2]);
  assert.match(result.summary, /Dry run: eligible 2 caches, 200 bytes/);
  assert.equal(result.queries[0].per_page, 100);
});

test('explicit dry run never deletes', async () => {
  const result = await run({ inputs: { dry_run: 'true' }, caches: fixtures, states: { 2: 'closed' } });
  assert.deepEqual(result.deleted, []);
});

test('manual deletion requires explicit false and only deletes closed-PR caches', async () => {
  const result = await run({ inputs: { dry_run: 'false' }, caches: fixtures, states: { 2: 'closed' } });
  assert.deepEqual(result.deleted, [5, 6]);
  assert.match(result.summary, /Deleted 2 caches, 200 bytes/);
});

test('close event scopes both the API query and deletion to the event PR', async () => {
  const result = await run({ event: 'pull_request_target', caches: fixtures, states: { 1: 'closed', 2: 'closed' } });
  assert.equal(result.queries[0].ref, 'refs/pull/2/merge');
  assert.deepEqual(result.deleted, [5, 6]);
  assert.deepEqual(result.lookups, [2]);
});

test('stale close event preserves caches of a reopened PR', async () => {
  const result = await run({ event: 'pull_request_target', caches: fixtures });
  assert.deepEqual(result.deleted, []);
});

test('already evicted caches do not fail cleanup or count as deleted', async () => {
  const result = await run({ event: 'pull_request_target', caches: fixtures, states: { 2: 'closed' }, failure: 404 });
  assert.match(result.summary, /Deleted 0 caches, 0 bytes/);
});

test('permission failures fail cleanup', async () => {
  await assert.rejects(run({ event: 'pull_request_target', caches: fixtures, states: { 2: 'closed' }, failure: 403 }), /API failure/);
});

test('empty cache inventory succeeds', async () => {
  const result = await run();
  assert.deepEqual(result.lookups, []);
  assert.match(result.summary, /eligible 0 caches/);
});

test('all pages are read before deleting against a mutable inventory', async () => {
  const caches = Array.from({ length: 205 }, (_, index) => cache(index + 1, 'refs/pull/2/merge'));
  const result = await run({ event: 'schedule', caches, states: { 2: 'closed' } });
  assert.deepEqual(result.queries.map(({ page }) => page), [1, 2, 3]);
  assert.deepEqual(result.operations.slice(0, 4), ['list:1', 'list:2', 'list:3', 'delete:1']);
  assert.equal(result.deleted.length, 205);
  assert.equal(result.deleted.at(-1), 205);
});

test('a full final page is followed by an empty page', async () => {
  const caches = Array.from({ length: 100 }, (_, index) => cache(index + 1, 'refs/pull/2/merge'));
  const result = await run({ caches, states: { 2: 'closed' } });
  assert.deepEqual(result.queries.map(({ page }) => page), [1, 2]);
  assert.match(result.summary, /eligible 100 caches/);
});

test('scheduled reconciliation catches caches saved after close-event cleanup', async () => {
  const caches = [cache(1, 'refs/pull/2/merge')];
  const first = await run({ event: 'pull_request_target', caches, states: { 2: 'closed' } });
  assert.deepEqual(first.deleted, [1]);
  const lateCache = cache(2, 'refs/pull/2/merge');
  const sweep = await run({ event: 'schedule', caches: [lateCache, cache(3, 'refs/pull/1/merge')], states: { 2: 'closed' } });
  assert.deepEqual(sweep.deleted, [2]);
});

test('cache inventory read failures abort cleanup', async () => {
  const caches = Array.from({ length: 101 }, (_, index) => cache(index + 1, 'refs/pull/2/merge'));
  const trace = [];
  await assert.rejects(run({ event: 'schedule', caches, states: { 2: 'closed' }, listFailurePage: 2, trace }), /Cache listing failed/);
  assert.deepEqual(trace, ['list:1', 'list:2']);
});

test('PR lookup failures do not treat an unknown PR as closed', async () => {
  const trace = [];
  await assert.rejects(run({ event: 'schedule', caches: fixtures, readFailure: true, trace }), /PR lookup failed/);
  assert.deepEqual(trace, ['list:1']);
});

test('manual and scheduled sweeps reject non-default branch execution', async () => {
  for (const event of ['schedule', 'workflow_dispatch']) {
    await assert.rejects(run({ event, ref: 'refs/heads/feature' }), /default branch/);
  }
});

test('unexpected events or malformed close events fail closed', async () => {
  await assert.rejects(run({ event: 'pull_request' }), /Unsupported cleanup event/);
  await assert.rejects(run({ event: 'pull_request_target', action: 'reopened' }), /Expected a closed pull request/);
  await assert.rejects(run({ event: 'pull_request_target', number: 0 }), /valid number/);
});

test('workflow invokes the tested module only from trusted checkout source', () => {
  const workflow = readFileSync(`${__dirname}/../workflows/cleanup-pr-caches.yml`, 'utf8');
  assert.match(workflow, /ref: \$\{\{ github\.sha \}\}/);
  assert.match(workflow, /persist-credentials: false/);
  assert.match(workflow, /require\('\.\/\.github\/scripts\/cleanup-pr-caches\.cjs'\)/);
  assert.match(workflow, /cron: '43 4 \* \* \*'/);
  assert.doesNotMatch(workflow, /pull_request\.head|refs\/pull\/.*\/merge|secrets\./);
});

test('image DB caching is shared, isolated, daily and keeps scan freshness checks enabled', () => {
  const workflow = readFileSync(`${__dirname}/../workflows/ci-backend.yml`, 'utf8');
  const action = readFileSync(`${__dirname}/../actions/trivy-image-db/action.yml`, 'utf8');
  assert.match(action, /key: trivy-image-db-shared-v1-v0\.74\.0-.*steps\.date\.outputs\.date/);
  assert.match(workflow, /skip-setup-trivy: true\s+cache: false\s+cache-dir: \$\{\{ github\.workspace \}\}\/\.cache\/trivy-image/);
  assert.match(action, /\.cache\/trivy-image\/java-db/);
  assert.doesNotMatch(workflow, /key: trivy-db-.*github\.run_id|TRIVY_SKIP_(JAVA_)?DB_UPDATE/);
  const images = readFileSync(`${__dirname}/../workflows/swe5001-service-images.yml`, 'utf8');
  assert.match(images, /retention-days: 2/);
});

test('image DB consumers are restore-only; publication is guarded twice', () => {
  const workflow = readFileSync(`${__dirname}/../workflows/ci-backend.yml`, 'utf8').replace(/\r\n/g, '\n');
  const action = readFileSync(`${__dirname}/../actions/trivy-image-db/action.yml`, 'utf8');
  const dockerJob = workflow.split('\n  docker:\n')[1].split('\n  deploy-existing-ec2:')[0];
  assert.match(dockerJob, /uses: \.\/\.github\/actions\/trivy-image-db/);
  assert.doesNotMatch(dockerJob, /publish: 'true'|actions\/cache\/save|name: Cache Trivy/);
  assert.match(workflow, /name: Seed shared Trivy image databases \(trusted dev only\)[\s\S]*?github\.event_name == 'push'[\s\S]*?github\.event\.repository\.default_branch[\s\S]*?continue-on-error: true[\s\S]*?publish: 'true'/);
  assert.match(action, /default: 'false'/);
  assert.match(action, /\[ "\$EVENT_NAME" != push \] \|\| \[ "\$REF" != "refs\/heads\/\$DEFAULT_BRANCH" \]/);
  assert.match(action, /uses: actions\/cache\/restore@/);
  assert.match(action, /name: Publish trusted image databases\s+if: inputs\.publish == 'true' && steps\.lookup\.outputs\.cache-hit != 'true' && steps\.restore\.outputs\.cache-hit != 'true'/);
  assert.doesNotMatch(action, /uses: actions\/cache@|TRIVY_SKIP_(JAVA_)?DB_UPDATE|--skip-(java-)?db-update/);
});

test('database validation precedes save independently of later scan findings', () => {
  const action = readFileSync(`${__dirname}/../actions/trivy-image-db/action.yml`, 'utf8');
  assert.ok(action.indexOf('name: Download current databases') < action.indexOf('name: Validate downloaded databases'));
  assert.ok(action.indexOf('name: Validate downloaded databases') < action.indexOf('name: Publish trusted image databases'));
  assert.match(action, /--download-db-only/);
  assert.match(action, /--download-java-db-only/);
  assert.match(action, /version: v0\.74\.0\s+cache: true/);
});

test('repeated trusted pushes use a lookup rather than download the existing daily payload', () => {
  const action = readFileSync(`${__dirname}/../actions/trivy-image-db/action.yml`, 'utf8');
  assert.match(action, /name: Look up today's trusted database cache[\s\S]*?lookup-only: true/);
  assert.match(action, /name: Install Trivy \(retain binary caching\)\s+if: inputs\.publish != 'true' \|\| steps\.lookup\.outputs\.cache-hit != 'true'/);
  assert.match(action, /name: Restore trusted image databases\s+id: restore\s+if: inputs\.publish != 'true' \|\| steps\.lookup\.outputs\.cache-hit != 'true'/);
});

test('PR image validation skips export/upload while retaining the build and scan gates', () => {
  const images = readFileSync(`${__dirname}/../workflows/swe5001-service-images.yml`, 'utf8');
  const gate = readFileSync(`${__dirname}/../workflows/swe5001-release-gate.yml`, 'utf8');
  const condition = "if: inputs.upload-images && github.event_name != 'pull_request' && github.event_name != 'merge_group'";
  assert.equal(images.split(condition).length - 1, 2);
  assert.match(images, /name: Export image\s+if: inputs\.upload-images/);
  assert.match(images, /name: Upload verified image\s+if: inputs\.upload-images/);
  assert.match(gate, /uses: \.\/\.github\/workflows\/swe5001-service-images\.yml\s+with:\s+upload-images: false/);
  assert.match(images, /name: Build image \(linux\/arm64\)\s+uses: docker\/build-push-action@/);
  assert.match(images, /name: Trivy image scan\s+uses: aquasecurity\/trivy-action@/);
  assert.match(images, /severity: HIGH,CRITICAL\s+ignore-unfixed: true\s+exit-code: '1'/);
});

test('deployment image handoff keeps default uploads and two-day retry retention', () => {
  const images = readFileSync(`${__dirname}/../workflows/swe5001-service-images.yml`, 'utf8');
  const deploy = readFileSync(`${__dirname}/../workflows/swe5001-deploy.yml`, 'utf8');
  assert.match(images, /upload-images:\s+description: [^\r\n]+\s+type: boolean\s+default: true/);
  assert.match(images, /retention-days: 2/);
  assert.match(deploy, /uses: \.\/\.github\/workflows\/swe5001-service-images\.yml/);
  assert.doesNotMatch(deploy, /upload-images: false/);
  assert.match(deploy, /pattern: swe5001-image-\*/);
});

test('immutable SWE5001 image tags distinguish new runs and retry attempts at the same SHA', () => {
  const deploy = readFileSync(`${__dirname}/../workflows/swe5001-deploy.yml`, 'utf8');
  const publish = deploy.split('\n  publish:')[1].split('\n  up:')[0];
  const template = publish.match(/^\s*IMAGE_TAG:\s*(.+)$/m)[1].trim();
  const render = (sha, run_id, run_attempt) => {
    const context = { sha, run_id, run_attempt };
    return template.replace(/\$\{\{\s*github\.(sha|run_id|run_attempt)\s*\}\}/g,
      (_, key) => String(context[key]));
  };
  const sha = 'e177a3fbb21bccaa9bd232f30ea7dbed6775d48c';
  const original = render(sha, 42587537517, 1);
  const fresh = render(sha, 42642746273, 1);
  const retry = render(sha, 42642746273, 2);
  assert.notEqual(original, fresh, 'new workflow runs reset run_attempt to 1');
  assert.notEqual(fresh, retry, 'job retries keep run_id but increment run_attempt');
  assert.notEqual(fresh, render('a'.repeat(40), 42642746273, 1));
  assert.equal(fresh, render(sha, 42642746273, 1));
  for (const tag of [original, fresh, retry]) {
    assert.match(tag, /^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$/);
    assert.equal(tag.includes('${{'), false);
    assert.ok(tag.includes(sha), 'retain the full source identity');
  }
});

test('SWE5001 publication retains immutable ECR tags and resolves the exact pushed tag', () => {
  const deploy = readFileSync(`${__dirname}/../workflows/swe5001-deploy.yml`, 'utf8');
  const ecr = readFileSync(`${__dirname}/../../infra/swe5001/foundation/ecr.tf`, 'utf8');
  assert.match(ecr, /image_tag_mutability\s*=\s*"IMMUTABLE"/);
  assert.ok(deploy.includes('image="${REGISTRY}/${repository}:${IMAGE_TAG}"'));
  assert.ok(deploy.includes('--image-ids "imageTag=${IMAGE_TAG}"'));
  assert.ok(deploy.includes('echo "${service} ${digest} ${IMAGE_TAG}"'));
  assert.doesNotMatch(deploy, /batch-delete-image|put-image-tag-mutability/);
});
