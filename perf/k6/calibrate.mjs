import { readFileSync } from 'node:fs';
import { baselineResult } from './lib.mjs';

try {
  if (process.argv.length !== 3) throw new Error('Usage: node perf/k6/calibrate.mjs <v2-summary.json>');
  const result = baselineResult(JSON.parse(readFileSync(process.argv[2], 'utf8')));
  if (!result.hasAwsPreflight) throw new Error('Local fixture reports cannot produce deployment calibration');
  console.log(JSON.stringify(result, null, 2));
} catch (error) {
  console.error(`Calibration refused: ${error.message}`);
  process.exitCode = 1;
}
