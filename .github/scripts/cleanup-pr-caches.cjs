module.exports = async function cleanup({ github, context, core }) {
  const { owner, repo } = context.repo;
  const event = context.eventName;
  if (!['pull_request_target', 'schedule', 'workflow_dispatch'].includes(event)) {
    throw new Error(`Unsupported cleanup event: ${event}`);
  }
  if (event !== 'pull_request_target' &&
      context.ref !== `refs/heads/${context.payload.repository.default_branch}`) {
    throw new Error('Cache sweeps must run on the default branch');
  }
  const dryRun = event === 'workflow_dispatch' &&
    context.payload.inputs?.dry_run !== 'false';
  const closedNumber = event === 'pull_request_target'
    ? context.payload.pull_request.number : undefined;
  if (event === 'pull_request_target' &&
      (context.payload.action !== 'closed' || !Number.isSafeInteger(closedNumber) || closedNumber < 1)) {
    throw new Error('Expected a closed pull request with a valid number');
  }

  // Snapshot all pages before deletion; deleting while paging shifts later entries.
  const caches = [];
  for (let page = 1; ; page++) {
    const { data } = await github.rest.actions.getActionsCacheList({
      owner, repo, page, per_page: 100,
      ...(closedNumber ? { ref: `refs/pull/${closedNumber}/merge` } : {}),
    });
    caches.push(...data.actions_caches);
    if (data.actions_caches.length < 100) break;
  }

  const states = new Map();
  let count = 0;
  let bytes = 0;
  for (const cache of caches) {
    const match = /^refs\/pull\/([1-9][0-9]*)\/merge$/.exec(cache.ref);
    if (!match) continue;
    const number = Number(match[1]);
    if (!Number.isSafeInteger(number) || (closedNumber && number !== closedNumber)) continue;
    if (!states.has(number)) {
      // A stale close event must not remove caches from an already-reopened PR.
      const { data: pr } = await github.rest.pulls.get({ owner, repo, pull_number: number });
      states.set(number, pr.state);
    }
    if (states.get(number) !== 'closed') continue;
    if (!dryRun) {
      try {
        await github.rest.actions.deleteActionsCacheById({ owner, repo, cache_id: cache.id });
      } catch (error) {
        // Concurrent eviction or another sweep can already have removed this cache.
        if (error.status === 404) continue;
        throw error;
      }
    }
    count++;
    bytes += cache.size_in_bytes;
    core.info(`${dryRun ? 'Would delete' : 'Deleted'} cache ${cache.id} (${cache.ref}, ${cache.size_in_bytes} bytes)`);
  }
  await core.summary
    .addHeading('Closed pull request cache cleanup')
    .addRaw(`${dryRun ? 'Dry run: eligible' : 'Deleted'} ${count} caches, ${bytes} bytes. Branch, tag and open-PR caches are preserved.`)
    .write();
};
