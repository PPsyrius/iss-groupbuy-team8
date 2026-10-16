import { readFileSync, createReadStream } from 'node:fs';
import { createInterface } from 'node:readline';
import { v3Accumulator } from './scaling.mjs';

try {
  const [summary, samples, observations, events, ...extra] = process.argv.slice(2);
  if (!summary || !samples || !observations || !events || extra.length) throw new Error('Usage: node analyze-v3.mjs summary.json samples.json observations.jsonl scale-outs.json');
  const read = file => JSON.parse(readFileSync(file, 'utf8').replace(/^\uFEFF/, ''));
  const report = read(summary);
  if (report.context?.remote !== true) throw new Error('Fixture reports cannot establish AWS V3 acceptance');
  const accumulator = v3Accumulator(report, read(events));
  const lines = createInterface({ input: createReadStream(samples), crlfDelay: Infinity });
  for await (const line of lines) if (line.trim()) accumulator.add(JSON.parse(line));
  const rows = readFileSync(observations, 'utf8').replace(/^\uFEFF/, '').trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
  const result = accumulator.finish(rows);
  process.stdout.write(JSON.stringify(result, null, 2) + '\n');
  if (!result.latencyAndScaleOutGatesPassed) process.exitCode = 2;
} catch (error) {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 1;
}
