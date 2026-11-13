import { readFileSync } from 'node:fs';
import { browseResult } from './lib.mjs';

try {
  if (process.argv.length !== 3) throw new Error('usage: node perf/drills/analyze-browse.mjs summary.json');
  console.log(JSON.stringify(browseResult(JSON.parse(readFileSync(process.argv[2], 'utf8'))), null, 2));
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
