import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { setTimeout } from 'node:timers/promises';

const root = resolve(import.meta.dirname, '../..');
test('identity state SQL runs read-only on the real schema and counts unbound event receipts', { timeout: 120000 }, async () => {
  const name = `blocbuy-identity-report-${process.pid}-${Date.now()}`;
  function docker(args, input) {
    const result = spawnSync('docker', args, { input, encoding: 'utf8', timeout: 30000 });
    assert.equal(result.status, 0, result.stderr || result.error?.message);
    return result.stdout.trim();
  }
  const sql = text => docker(['exec', '-i', name, 'psql', '--no-psqlrc', '-h', '127.0.0.1', '-U', 'postgres', '-qAt', '-v', 'ON_ERROR_STOP=1'], text);
  const id = '0199aaaa-0000-7000-8000-000000000001';
  const intent = '0199aaaa-0000-7000-8000-000000000002';
  const report = (event = '') => JSON.parse(sql(`\\set payment_id '${id}'\n\\set event_id '${event}'\n\\i /repo/perf/drills/identity-state.sql\n`));
  try {
    docker(['run', '--rm', '-d', '--name', name, '--network', 'none', '-e', 'POSTGRES_HOST_AUTH_METHOD=trust',
      '--mount', `type=bind,source=${root},target=/repo,readonly`, 'postgres:18']);
    let ready = false;
    for (let attempt = 0; attempt < 40; attempt++) {
      if (spawnSync('docker', ['exec', name, 'pg_isready', '-h', '127.0.0.1', '-U', 'postgres']).status === 0) { ready = true; break; }
      await setTimeout(250);
    }
    assert.ok(ready);
    sql('CREATE SCHEMA domain;');
    const path = 'backend/blocbuy-bootstrap/src/main/resources/db/migration';
    const migrations = readdirSync(resolve(root, path)).filter(f => /^V\d+__.*\.sql$/.test(f))
      .sort((a, b) => Number(a.match(/^V(\d+)/)[1]) - Number(b.match(/^V(\d+)/)[1]));
    for (const file of migrations) docker(['exec', '-e', 'PGOPTIONS=-c search_path=domain,public', name,
      'psql', '--no-psqlrc', '-h', '127.0.0.1', '-U', 'postgres', '-q', '-v', 'ON_ERROR_STOP=1', '-f', `/repo/${path}/${file}`]);
    docker(['exec', '-e', 'PGOPTIONS=-c search_path=domain,public', name, 'psql', '--no-psqlrc', '-h', '127.0.0.1',
      '-U', 'postgres', '-q', '-v', 'ON_ERROR_STOP=1', '-f', '/repo/backend/blocbuy-bootstrap/src/main/resources/db/seed/R__demo_catalog_seed.sql']);
    assert.equal(report().fixturePayments, 0);
    sql(`INSERT INTO domain.biz_purchase_intent(id,user_id,operation,deal_id,product_id,product_title,amount,currency,target_size,quote_valid_until,business_deadline)
      SELECT '${intent}','${id}','START','${id}',id,title,1,'sgd',2,clock_timestamp()+interval '40 minutes',clock_timestamp()+interval '40 minutes' FROM domain.biz_product ORDER BY id LIMIT 1;
      INSERT INTO domain.biz_payment(id,payment_no,user_id,provider,amount,currency,status,intent_id,operation)
      VALUES('${id}','identity-report-fixture','${id}','stripe',1,'sgd','created','${intent}','START');`);
    const before = report();
    assert.equal(before.fixturePayments, 1);
    assert.equal(before.paymentState, 'created');
    assert.equal(before.intentsForActorProduct, 1);
    assert.equal(before.eventIdProvided, false);
    assert.equal(before.webhookReceiptsForEvent, null);
    assert.ok(Number.isInteger(before.productStock));
    // Query-contract fixture only: this is deliberately NOT a real webhook or outage rehearsal.
    sql("INSERT INTO domain.log_payment_webhook_event(event_id,event_type) VALUES('evt_fixture123','checkout.session.completed');");
    const withEvent = report('evt_fixture123');
    assert.equal(withEvent.webhookReceiptsForPayment, 0);
    assert.equal(withEvent.webhookReceiptsForEvent, 1, 'an unbound event claim must not masquerade as zero receipts');
    assert.equal(withEvent.productStock, before.productStock);
    assert.deepEqual(['participationReceipts', 'members', 'orders', 'refunds'].map(key => withEvent[key]), [0, 0, 0, 0]);
    assert.equal(withEvent.eventId, undefined, 'output excludes raw fixture identifiers');
  } finally { spawnSync('docker', ['rm', '-fv', name], { timeout: 30000 }); }
});
