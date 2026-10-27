const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { test } = require('node:test');

const script = fs.readFileSync(path.resolve(__dirname, '../../frontend/scripts/clean-api-output.mjs'));
for (const scenario of ['missing output', 'stale output', 'output symlink', 'child symlink']) {
  test(`generated API cleanup: ${scenario}`, () => {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'blocbuy-api-output-'));
    const output = path.join(root, 'src/api/generated');
    const outside = path.join(root, 'reviewed');
    const cleaner = path.join(root, 'scripts/clean-api-output.mjs');
    try {
      fs.mkdirSync(path.dirname(cleaner), { recursive: true });
      fs.writeFileSync(cleaner, script);
      fs.mkdirSync(outside);
      fs.writeFileSync(path.join(outside, 'keep.ts'), 'reviewed input');
      fs.mkdirSync(path.dirname(output), { recursive: true });
      let inode;
      if (scenario === 'output symlink') {
        fs.symlinkSync(outside, output, 'dir');
      } else if (scenario !== 'missing output') {
        fs.mkdirSync(output);
        inode = fs.statSync(output).ino;
        fs.writeFileSync(path.join(output, 'stale.ts'), 'removed endpoint');
        fs.mkdirSync(path.join(output, 'removed-plugin'));
        fs.writeFileSync(path.join(output, 'removed-plugin/stale.ts'), 'removed plugin');
        if (scenario === 'child symlink') fs.symlinkSync(outside, path.join(output, 'linked'), 'dir');
      }
      const result = spawnSync(process.execPath, [cleaner], { encoding: 'utf8' });
      assert.equal(fs.readFileSync(path.join(outside, 'keep.ts'), 'utf8'), 'reviewed input');
      if (scenario === 'output symlink') {
        assert.notEqual(result.status, 0);
        assert.match(result.stderr, /must not be a symlink/);
      } else {
        assert.equal(result.status, 0, result.stderr);
        assert.deepEqual(fs.readdirSync(output), []);
        if (inode !== undefined) assert.equal(fs.statSync(output).ino, inode, 'Output directory must survive cleanup');
      }
    } finally {
      fs.rmSync(root, { recursive: true, force: true });
    }
  });
}
