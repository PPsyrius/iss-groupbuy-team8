import { readFileSync } from 'node:fs';
import { connectionBudget } from './lib.mjs';

try {
  if (process.argv.length !== 3) throw new Error('Usage: node perf/k6/budget.mjs <observed-budget.json>');
  const result = connectionBudget(JSON.parse(readFileSync(process.argv[2], 'utf8')));
  console.log(JSON.stringify(result, null, 2));
  if (!result.fits) process.exitCode = 2;
} catch (error) {
  console.error(`Budget refused: ${error.message}`);
  process.exitCode = 1;
}
