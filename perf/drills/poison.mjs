// Offline payload preparation only. No AWS client, network, receive or publish operation.
import { randomUUID, createHash } from 'node:crypto';
import { mkdirSync, realpathSync, writeFileSync } from 'node:fs';
import { dirname, resolve, relative, isAbsolute } from 'node:path';
import { fileURLToPath } from 'node:url';

export function poison(runLabel, at = new Date().toISOString(), uuid = randomUUID) {
  if (!/^[a-zA-Z0-9-]{1,24}$/.test(runLabel || '')) throw new Error('Use a new RUN_LABEL of 1..24 letters, digits or dashes');
  const envelope = { eventId: uuid(), eventType: 'PaymentSucceeded', version: 1, occurredAt: at,
    producer: 'swe5001-v7-drill', traceId: `v7-${runLabel}`, aggregateId: uuid(), payload: {} };
  const attributes = { eventType: { DataType: 'String', StringValue: envelope.eventType },
    traceId: { DataType: 'String', StringValue: envelope.traceId } };
  const body = `${JSON.stringify(envelope, null, 2)}\n`;
  return { envelope, attributes, body, manifest: { schema: 1, runLabel, preparedAt: at,
    eventId: envelope.eventId, eventType: envelope.eventType, version: envelope.version, traceId: envelope.traceId,
    bodySha256: createHash('sha256').update(body).digest('hex'),
    reason: 'Supported PaymentSucceeded v1 with missing required payload fields; not an unsupported-version readiness probe',
    published: false, publishAuthorized: false } };
}

export function validControl(runLabel, recipient, at = new Date().toISOString(), uuid = randomUUID) {
  if (!/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i.test(recipient || '')) throw new Error('V7_CONTROL_RECIPIENT_ID must be the reviewed active synthetic identity UUID');
  const result = poison(runLabel, at, uuid);
  result.envelope.traceId = `v7-control-${runLabel}`;
  result.envelope.payload = { paymentId: result.envelope.aggregateId, intentId: uuid(), recipientUserId: recipient,
    operation: 'START', businessId: uuid(), confirmedAt: at };
  result.attributes.traceId.StringValue = result.envelope.traceId;
  result.body = `${JSON.stringify(result.envelope, null, 2)}\n`;
  result.manifest.traceId = result.envelope.traceId;
  result.manifest.bodySha256 = createHash('sha256').update(result.body).digest('hex');
  result.manifest.reason = 'Valid notification-transport control only; not a real Stripe payment or business-source event';
  return result;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    if (process.argv.length !== 4) throw new Error('usage: node perf/drills/poison.mjs RUN_LABEL NEW_PRIVATE_DIRECTORY');
    const result = poison(process.argv[2]);
    const control = process.env.V7_CONTROL_RECIPIENT_ID ? validControl(process.argv[2], process.env.V7_CONTROL_RECIPIENT_ID) : null;
    const repo = realpathSync(resolve(import.meta.dirname, '../..'));
    const destination = resolve(realpathSync(dirname(resolve(process.argv[3]))), resolve(process.argv[3]).split(/[\\/]/).pop());
    const rel = relative(repo, destination);
    if (!rel || (!isAbsolute(rel) && rel !== '..' && !rel.startsWith(`..${process.platform === 'win32' ? '\\' : '/'}`))) throw new Error('Use a private directory outside the checkout');
    mkdirSync(destination, { mode: 0o700 }); // Existing directory fails; no overwrite/repeat.
    const files = [['message.json', result.body], ['attributes.json', `${JSON.stringify(result.attributes, null, 2)}\n`],
      ['manifest.json', `${JSON.stringify(result.manifest, null, 2)}\n`]];
    if (control) files.push(['control-message.json', control.body], ['control-attributes.json', `${JSON.stringify(control.attributes, null, 2)}\n`],
      ['control-manifest.json', `${JSON.stringify(control.manifest, null, 2)}\n`]);
    for (const [name, content] of files) writeFileSync(resolve(destination, name), content, { flag: 'wx', mode: 0o600 });
    console.log('Prepared poison payload locally; nothing published. Live injection needs separate authorization.');
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}
