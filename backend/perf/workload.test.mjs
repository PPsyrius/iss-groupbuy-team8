import assert from 'node:assert/strict';
import http from 'node:http';
import { spawn } from 'node:child_process';
import { once } from 'node:events';
import { test } from 'node:test';
import { fileURLToPath } from 'node:url';
import { setTimeout as sleep } from 'node:timers/promises';

async function runFixture(t, mode = 'success') {
  let active = 0;
  let peak = 0;
  const seen = new Set();
  const sessions = Array.from({ length: 4 }, (_, i) => ({
    token: 'ephemeral-session-' + i,
    orderNos: Array.from({ length: 10 }, (_, j) => 'ORDER-' + i + '-' + j),
  }));
  const server = http.createServer(async (req, res) => {
    active++;
    peak = Math.max(peak, active);
    await sleep(20);
    seen.add(req.url.split('?')[0]);
    let data;
    const buyer = sessions.find(u => u.token === req.headers['blocbuy-token']);
    if (req.url.startsWith('/api/v1/orders/me')) {
      assert.ok(buyer);
      data = { total: 10, items: buyer.orderNos.map(orderNo => ({ orderNo })) };
      if (mode === 'foreign_order') data.items[0].orderNo = 'someone-else';
    } else if (req.url === '/api/v1/payments/intents') {
      assert.ok(buyer);
      assert.equal(req.method, 'POST');
      assert.equal(req.headers['x-blocbuy-request'], 'checkout');
      let body = '';
      for await (const chunk of req) body += chunk;
      assert.deepEqual(JSON.parse(body), { operation: 'JOIN', businessId: 'team' });
      data = { intentId: 'issued' };
    } else if (req.url === '/api/v1/products/product') {
      assert.equal(req.headers['blocbuy-token'], undefined);
      data = { id: 'product' };
    } else {
      data = { items: [{ id: 'product' }] };
      if (mode === 'empty_catalog') data.items = [];
    }
    res.writeHead(mode === 'unauthorized' ? 401 : 200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ code: mode === 'unauthorized' ? 'COMMON_401_001' : 'COMMON_200_000', data }));
    active--;
  });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  t.after(() => server.close());
  const child = spawn(process.execPath, [fileURLToPath(new URL('./workload.mjs', import.meta.url))]);
  let stdout = '';
  let stderr = '';
  child.stdout.on('data', chunk => { stdout += chunk; });
  child.stderr.on('data', chunk => { stderr += chunk; });
  child.stdin.end(JSON.stringify({
    baseUrl: 'http://127.0.0.1:' + server.address().port,
    users: sessions, productId: 'product', dealId: 'team',
    vus: 4, durationSeconds: 0.15, warmupSeconds: 0, thinkMs: 1,
  }));
  const [exitCode] = await once(child, 'close');
  assert.ok(!stdout.includes('ephemeral-session'));
  assert.ok(!stderr.includes('ephemeral-session'));
  return { summary: JSON.parse(stdout), exitCode, peak, seen };
}

test('real HTTP workload drives concurrent VUs and reports every scenario without credentials', async t => {
  const { summary, exitCode, peak, seen } = await runFixture(t);
  assert.equal(exitCode, 0);
  assert.equal(peak, 4);
  assert.equal(seen.size, 4);
  for (const s of Object.values(summary.scenarios)) {
    assert.equal(s.vus, 4);
    assert.ok(s.actions >= 4);
    assert.equal(s.errorRate, 0);
    assert.ok(s.p50Ms > 0 && s.p50Ms <= s.p95Ms && s.p95Ms <= s.p99Ms);
    assert.ok(s.actionsPerSecond > 0);
  }
  const browse = summary.scenarios.browse;
  assert.equal(browse.requestsPerSecond, browse.actionsPerSecond * 2);
});

for (const mode of ['unauthorized', 'empty_catalog', 'foreign_order']) {
  test(mode + ' remains a failed measurement even with fast HTTP responses', async t => {
    const { summary, exitCode } = await runFixture(t, mode);
    assert.equal(exitCode, 1);
    const name = mode === 'foreign_order' ? 'my_orders' : 'browse';
    assert.equal(summary.scenarios[name].errorRate, 1);
    assert.ok(summary.scenarios[name].p95TargetMet);
  });
}
