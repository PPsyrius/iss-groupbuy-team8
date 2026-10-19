#!/usr/bin/env node
// node bin/build.js <deck-dir> <step...>
//   deck          dist/<outputs.deck>        HTML deck for presenting
//   deck-pdf      dist/<outputs.deckPdf>     needs deck
//   script        dist/<outputs.scriptPdf>   speaker script, built in memory and printed straight to PDF
//   screenshots [regex]   png/<slide id>.png + overflow report; needs deck
//   all           deck deck-pdf script
const context = require('../lib/context');

const [dir, ...args] = process.argv.slice(2);
if (!dir || !args.length) {
  console.error('usage: node bin/build.js <deck-dir> deck|deck-pdf|script|screenshots [regex]|all');
  process.exit(2);
}
context.use(dir);

(async () => {
  const steps = args[0] === 'all' ? ['deck', 'deck-pdf', 'script'] : args;
  for (let i = 0; i < steps.length; i++) {
    const step = steps[i];
    if (step === 'deck') require('../deck/build')();
    else if (step === 'deck-pdf') await require('../render').deckPdf();
    else if (step === 'script') await require('../render').scriptPdf(require('../script/build').buildScript());
    else if (step === 'screenshots') { await require('../render').screenshots(steps[i + 1]); break; }
    else throw new Error('unknown step ' + step);
  }
})().catch(e => { console.error(e); process.exit(1); });
