import { readFileSync } from 'node:fs';
import { analyzeV4 } from './scaling.mjs';

try {
  const [seed, observations, report, ...extra] = process.argv.slice(2);
  if (!seed || !observations || !report || (extra.length &&
      (extra.length !== 2 || extra[0] !== '--expected-count' || !['2000', '20000', '40000', '80000'].includes(extra[1])))) {
    throw new Error('Usage: node analyze-v4.mjs seed.json observations.jsonl database-report.json [--expected-count 2000|20000|40000|80000]');
  }
  const read = file => JSON.parse(readFileSync(file, 'utf8').replace(/^\uFEFF/, ''));
  const rows = readFileSync(observations, 'utf8').replace(/^\uFEFF/, '').trim().split(/\r?\n/).filter(Boolean).map(JSON.parse);
  const result = analyzeV4(read(seed), rows, read(report), extra.length ? Number(extra[1]) : 2000);
  if (!result.hasAwsDeploymentEvidence) throw new Error('Fixture reports cannot establish AWS V4 acceptance');
  process.stdout.write(JSON.stringify(result, null, 2) + '\n');
  if (!result.passed) process.exitCode = 2;
} catch (error) {
  process.stderr.write(`${error.message}\n`);
  process.exitCode = 1;
}
