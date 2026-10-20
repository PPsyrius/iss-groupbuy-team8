// svg(name) inlines a diagram: the deck's own src/diagrams/<name>.svg wins over the shared presentation-kit/diagrams/<name>.svg.
const fs = require('fs');
const path = require('path');
const { deck } = require('./context');

function svg(name) {
  const own = path.join(deck().dir, 'src', 'diagrams', name + '.svg');
  const file = fs.existsSync(own) ? own : path.join(__dirname, '..', 'diagrams', name + '.svg');
  return fs.readFileSync(file, 'utf8').replace(/<\?xml[^>]*>/, '');
}

module.exports = { svg };
