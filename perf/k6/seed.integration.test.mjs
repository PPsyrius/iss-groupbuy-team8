import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { setTimeout } from 'node:timers/promises';

const root = resolve(import.meta.dirname, '../..');
const migrations = 'backend/blocbuy-bootstrap/src/main/resources/db/migration';
const seed = '/repo/backend/blocbuy-bootstrap/src/main/resources/db/seed/load/v4-expiry.sql';
const recipient = '0199aaaa-0000-7000-8000-000000000001';

test('PostgreSQL 18 expiry fixture is bounded, atomic, UUIDv7 and never resets a run', { timeout: 120000 }, async () => {
  const name = `blocbuy-v4-seed-${process.pid}-${Date.now()}`;
  function docker(args, input) {
    const result = spawnSync('docker', args, { encoding: 'utf8', timeout: 30000, input });
    assert.equal(result.status, 0, result.stderr || result.error?.message);
    return result.stdout.trim();
  }
  const sql = text => docker(['exec', '-i', name, 'psql', '--no-psqlrc', '-h', '127.0.0.1', '-U', 'postgres', '-At', '-v', 'ON_ERROR_STOP=1'], text);
  const load = (label, profile = 'demo', user = recipient, revision) => spawnSync('docker', ['exec', name,
    'psql', '--no-psqlrc', '-h', '127.0.0.1', '-U', 'postgres', '-qAt', '-v', 'ON_ERROR_STOP=1',
    '-v', `run_label=${label}`, '-v', `profile=${profile}`, '-v', `recipient=${user}`,
    ...(revision ? ['-v', `expected_count=${revision.count}`, ...(revision.confirm === undefined ? [] : ['-v', `confirm_count=${revision.confirm}`])] : []),
    '-f', revision ? '/repo/perf/k6/v4-expiry.sql' : seed], { encoding: 'utf8', timeout: 30000 });
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
    const files = readdirSync(resolve(root, migrations)).filter(f => /^V\d+__.*\.sql$/.test(f))
      .sort((a, b) => Number(a.match(/^V(\d+)/)[1]) - Number(b.match(/^V(\d+)/)[1]));
    for (const file of files) docker(['exec', '-e', 'PGOPTIONS=-c search_path=domain,public', name,
      'psql', '--no-psqlrc', '-h', '127.0.0.1', '-U', 'postgres', '-q', '-v', 'ON_ERROR_STOP=1', '-f', `/repo/${migrations}/${file}`]);
    docker(['exec', '-e', 'PGOPTIONS=-c search_path=domain,public', name, 'psql', '--no-psqlrc', '-h', '127.0.0.1',
      '-U', 'postgres', '-q', '-v', 'ON_ERROR_STOP=1', '-f', '/repo/backend/blocbuy-bootstrap/src/main/resources/db/seed/R__demo_catalog_seed.sql']);
    for (const [label, profile, user] of [['not-demo', 'ec2', recipient], ['bad_label', 'demo', recipient], ['bad-user', 'demo', 'invalid']]) {
      const result = load(label, profile, user);
      assert.notEqual(result.status, 0, 'unsafe fixture inputs must fail');
    }
    const before = Number(sql('SELECT count(*) FROM domain.biz_group_deal;'));
    const result = load('local-fixture');
    assert.equal(result.status, 0, result.stderr);
    const receipt = result.stdout.split(/\r?\n/).filter(line => line.startsWith('{')).map(JSON.parse);
    assert.equal(receipt.length, 1);
    assert.equal(receipt[0].teams, 2000);
    assert.equal(receipt[0].members, 2000);
    assert.equal(Date.parse(receipt[0].expiresAt) - Date.parse(receipt[0].seededAt), 60000);
    assert.equal(Number(sql('SELECT count(*) FROM domain.biz_group_deal;')), before + 2000);
    assert.equal(sql("SELECT count(*) FROM domain.biz_group_deal WHERE deal_no LIKE 'V4-EXPIRY-%' AND uuid_extract_version(id)=7 AND current_size=0 AND status='open';"), '2000');
    assert.equal(sql("SELECT count(*) FROM domain.biz_group_member WHERE member_role='leader' AND status='joined' AND payment_id IS NULL AND order_id IS NULL;"), '2000');
    assert.equal(sql('SELECT count(*) FROM domain.sys_outbox;'), '0');
    assert.equal(sql('SELECT count(*) FROM domain.biz_payment;'), '0');
    const sourceFile = '/repo/backend/blocbuy-bootstrap/src/main/resources/db/seed/load/v4-source-report.sql';
    const notificationFile = '/repo/backend/blocbuy-bootstrap/src/main/resources/db/seed/load/v4-notification-report.sql';
    const sourceReport = () => JSON.parse(sql(`SET default_transaction_read_only=on;\n\\set run_label 'local-fixture'\n\\i ${sourceFile}\n`).split(/\r?\n/).find(line => line.startsWith('{')));
    assert.equal(sourceReport().expiredTeams, 0);
    assert.equal(sourceReport().outboxEvents, 0);
    // Query-contract fixture only: direct SQL effects here are NOT an expiry/outbox/consumer rehearsal.
    sql(`UPDATE domain.biz_group_deal SET status='expired' WHERE deal_no LIKE 'V4-EXPIRY-local-fixture-%';
      INSERT INTO domain.sys_outbox(event_type,event_version,aggregate_id,payload,trace_id,published_at)
      SELECT 'TeamExpired',1,id,'{}','query-fixture',clock_timestamp() FROM domain.biz_group_deal WHERE deal_no LIKE 'V4-EXPIRY-local-fixture-%';
      INSERT INTO domain.biz_notification(event_id,user_id,reference_id,notification_type,title,content)
      SELECT id,'${recipient}',aggregate_id,'TeamExpired','Fixture','Fixture' FROM domain.sys_outbox;
      INSERT INTO domain.sys_consumed_message(event_id,consumer) SELECT id,'notification' FROM domain.sys_outbox;`);
    const source = sourceReport();
    assert.equal(source.outboxEvents, 2000);
    assert.equal(source.publishedEvents, 2000);
    assert.equal(source.eventIds.length, 2000);
    const notificationReport = JSON.parse(sql(`SET default_transaction_read_only=on;\n\\set source '${JSON.stringify(source)}'\n\\i ${notificationFile}\n`).split(/\r?\n/).find(line => line.startsWith('{')));
    assert.equal(notificationReport.notifications, 2000);
    assert.equal(notificationReport.consumedEvents, 2000);
    assert.equal(notificationReport.eventIds, undefined, 'receipt must not disclose event-id arrays');
    const duplicate = load('local-fixture');
    assert.notEqual(duplicate.status, 0);
    assert.match(duplicate.stderr, /already used/);
    for (const count of ['', '2001', '20001', '020000', '2e4', '20000']) {
      const refused = load('unapproved', 'demo', recipient, { count });
      assert.notEqual(refused.status, 0, 'unsupported or unconfirmed revised count must fail');
      assert.equal(sql("SELECT count(*) FROM domain.biz_group_deal WHERE deal_no LIKE 'V4-EXPIRY-unapproved-%';"), '0');
    }
    const revised = { count: '20000', confirm: '20000' };
    for (const [label, profile, user] of [['revised-not-demo', 'ec2', recipient], ['revised_bad', 'demo', recipient], ['revised-bad-user', 'demo', 'invalid']]) {
      assert.notEqual(load(label, profile, user, revised).status, 0);
    }
    const larger = load('revised-fixture', 'demo', recipient, revised);
    assert.equal(larger.status, 0, larger.stderr);
    const largerReceipt = larger.stdout.split(/\r?\n/).filter(line => line.startsWith('{')).map(JSON.parse);
    assert.equal(largerReceipt.length, 1);
    assert.equal(largerReceipt[0].expectedCount, 20000);
    assert.equal(largerReceipt[0].teams, 20000);
    assert.equal(largerReceipt[0].members, 20000);
    assert.equal(Date.parse(largerReceipt[0].expiresAt) - Date.parse(largerReceipt[0].seededAt), 60000);
    assert.equal(sql("SELECT count(DISTINCT deal_no) FROM domain.biz_group_deal WHERE deal_no LIKE 'V4-EXPIRY-revised-fixture-%' AND uuid_extract_version(id)=7 AND current_size=0 AND status='open';"), '20000');
    assert.equal(sql("SELECT count(*) FROM domain.biz_group_deal WHERE deal_no='V4-EXPIRY-revised-fixture-20000';"), '1', 'five-digit suffix must not be truncated by lpad');
    assert.equal(sql("SELECT count(*) FROM domain.biz_group_member member JOIN domain.biz_group_deal deal ON deal.id=member.deal_id WHERE deal.deal_no LIKE 'V4-EXPIRY-revised-fixture-%' AND member.member_role='leader' AND member.status='joined' AND member.payment_id IS NULL AND member.order_id IS NULL;"), '20000');
    assert.equal(sql('SELECT count(*) FROM domain.sys_outbox;'), '2000', 'revised seed must not write outbox rows');
    assert.equal(sql('SELECT count(*) FROM domain.biz_payment;'), '0');
    const revisedSource = () => JSON.parse(sql("SET default_transaction_read_only=on;\n\\set run_label 'revised-fixture'\n\\set expected_count 20000\n\\i /repo/perf/k6/v4-source-report.sql\n").split(/\r?\n/).find(line => line.startsWith('{')));
    assert.equal(revisedSource().teams, 20000);
    assert.equal(revisedSource().expiredTeams, 0);
    assert.equal(revisedSource().outboxEvents, 0);
    // Query-contract effects only, deliberately separate from actual application pipeline tests.
    sql(`UPDATE domain.biz_group_deal SET status='expired' WHERE deal_no LIKE 'V4-EXPIRY-revised-fixture-%';
      INSERT INTO domain.sys_outbox(event_type,event_version,aggregate_id,payload,trace_id,published_at)
      SELECT 'TeamExpired',1,id,'{}','revised-query-fixture',clock_timestamp() FROM domain.biz_group_deal WHERE deal_no LIKE 'V4-EXPIRY-revised-fixture-%';
      INSERT INTO domain.biz_notification(event_id,user_id,reference_id,notification_type,title,content)
      SELECT id,'${recipient}',aggregate_id,'TeamExpired','Fixture','Fixture' FROM domain.sys_outbox WHERE trace_id='revised-query-fixture';
      INSERT INTO domain.sys_consumed_message(event_id,consumer) SELECT id,'notification' FROM domain.sys_outbox WHERE trace_id='revised-query-fixture';`);
    const revisedReport = revisedSource();
    assert.equal(revisedReport.expectedCount, 20000);
    assert.equal(revisedReport.outboxEvents, 20000);
    assert.equal(revisedReport.publishedEvents, 20000);
    assert.equal(revisedReport.eventIds.length, 20000);
    assert.ok(JSON.stringify(revisedReport).length > 131072, 'exercise Linux single-argument limit via stdin, never argv');
    const effects = () => JSON.parse(sql(`SET default_transaction_read_only=on;\n\\set source '${JSON.stringify(revisedReport)}'\n\\i ${notificationFile}\n`).split(/\r?\n/).find(line => line.startsWith('{')));
    assert.equal(effects().notifications, 20000, 'unrelated original-run effects must not be credited');
    assert.equal(effects().consumedEvents, 20000);
    assert.equal(effects().expectedCount, 20000);
    assert.equal(effects().eventIds, undefined);
    sql("UPDATE domain.biz_notification SET deleted=true WHERE event_id=(SELECT id FROM domain.sys_outbox WHERE trace_id='revised-query-fixture' LIMIT 1);");
    assert.equal(effects().notifications, 19999, 'missing expected effect cannot be replaced by unrelated rows');
    sql("DELETE FROM domain.sys_consumed_message WHERE event_id=(SELECT id FROM domain.sys_outbox WHERE trace_id='revised-query-fixture' LIMIT 1);");
    assert.equal(effects().consumedEvents, 19999);
    sql(`INSERT INTO domain.sys_outbox(event_type,event_version,aggregate_id,payload,trace_id,published_at)
      SELECT 'TeamExpired',1,id,'{}','surplus-query-fixture',clock_timestamp() FROM domain.biz_group_deal WHERE deal_no LIKE 'V4-EXPIRY-revised-fixture-%' LIMIT 2;`);
    assert.equal(revisedSource().outboxEvents, 20002);
    assert.equal(revisedSource().eventIds.length, 20001, 'retain bounded surplus detection, never cap at the expected count');
    assert.notEqual(load('revised-fixture', 'demo', recipient, revised).status, 0, 'never reuse a revised run label');
    // An insertion failure after team rows must roll back the whole seed transaction.
    sql('ALTER TABLE domain.biz_group_member ADD COLUMN fixture_required integer NOT NULL DEFAULT 1; ALTER TABLE domain.biz_group_member ALTER COLUMN fixture_required DROP DEFAULT;');
    const broken = load('rollback');
    assert.notEqual(broken.status, 0);
    assert.equal(sql("SELECT count(*) FROM domain.biz_group_deal WHERE deal_no LIKE 'V4-EXPIRY-rollback-%';"), '0');
    const revisedBroken = load('revised-rollback', 'demo', recipient, revised);
    assert.notEqual(revisedBroken.status, 0);
    assert.equal(sql("SELECT count(*) FROM domain.biz_group_deal WHERE deal_no LIKE 'V4-EXPIRY-revised-rollback-%';"), '0');
  } finally {
    spawnSync('docker', ['rm', '-fv', name], { timeout: 30000 });
  }
});
