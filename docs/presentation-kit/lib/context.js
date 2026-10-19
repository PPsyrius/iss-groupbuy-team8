// The deck being built: its folder and its deck.config.js. bin/build.js selects it before any deck module loads.
const path = require('path');

let current = null;
function use(dir) {
  const abs = path.resolve(dir);
  current = { dir: abs, cfg: require(path.join(abs, 'deck.config.js')) };
  return current;
}
function deck() {
  if (!current) throw new Error('No deck selected: build through presentation-kit/bin/build.js');
  return current;
}
module.exports = { use, deck };
