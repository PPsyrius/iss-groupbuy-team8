#!/usr/bin/env node
// Proves that a change to the shared diagram kit (scripts/typst.mjs, typst/uml.typ) or the
// template leaves the SWE5006 report exactly as it was: builds the report from a base commit and
// from the working tree, renders every page of each to PNG, and compares the pages byte for byte.
// Usage: node scripts/render-compare.mjs [--base <ref>] [--ppi <n>] [--keep]
//   --base  commit to compare against (default: the merge base of HEAD and origin/dev)
//   --ppi   page resolution (default 100)
//   --keep  keep the two builds and their pages (printed path) instead of deleting them
// Exits 0 when both builds have the same pages, 1 when any page differs, 2 when a build fails.
// The working tree includes uncommitted and untracked files under docs/ (ignored files excepted).
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { cpSync, existsSync, mkdirSync, mkdtempSync, readdirSync, readFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const report = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repo = resolve(report, '../../..');
const arg = (name, fallback) => {
  const i = process.argv.indexOf(name);
  return i > -1 ? process.argv[i + 1] : fallback;
};
const git = (...args) => execFileSync('git', ['-C', repo, ...args], { encoding: 'utf8' }).trim();
const base = arg('--base', null) ?? git('merge-base', 'HEAD', 'origin/dev');
const ppi = arg('--ppi', '100');
const keep = process.argv.includes('--keep');
const work = mkdtempSync(join(tmpdir(), 'swe5006-render-'));

// Two copies of docs/: the base commit's and the working tree's.
const baseDir = join(work, 'base');
const headDir = join(work, 'head');
mkdirSync(baseDir);
mkdirSync(headDir);
execFileSync('git', ['-C', repo, 'archive', '--format=tar', '-o', join(work, 'base.tar'), base, 'docs']);
execFileSync('tar', ['-xf', join(work, 'base.tar'), '-C', baseDir]);
for (const f of git('ls-files', '--cached', '--others', '--exclude-standard', 'docs').split('\n').filter(Boolean)) {
  if (existsSync(join(repo, f))) cpSync(join(repo, f), join(headDir, f), { recursive: true });
}

// The same build as `npm run build` (without the checks), from a clean environment, then pages.
const env = { ...process.env };
delete env.REPORT_TEMPLATE;
delete env.REPORT_LOGO;
delete env.REPORT_OUT;
const render = (dir) => {
  execFileSync('node', [join(dir, 'docs/swe5006/report/scripts/build.mjs')], { cwd: dir, env, stdio: ['ignore', 'ignore', 'inherit'] });
  const pages = join(dir, 'pages');
  mkdirSync(pages);
  execFileSync('typst', ['compile', '--root', dir, join(dir, 'docs/swe5006/report/dist/report.typ'), join(pages, '{0p}.png'), '--ppi', ppi], { stdio: ['ignore', 'ignore', 'inherit'] });
  const files = readdirSync(pages).sort();
  return {
    source: readFileSync(join(dir, 'docs/swe5006/report/dist/report.typ'), 'utf8'),
    pages: files.map((f) => createHash('sha256').update(readFileSync(join(pages, f))).digest('hex')),
    dir: pages,
  };
};

let a;
let b;
try {
  a = render(baseDir);
  b = render(headDir);
} catch (e) {
  console.error(`render-compare: a build failed (${e.message.split('\n')[0]}); files kept in ${work}`);
  process.exit(2);
}

const short = base.slice(0, 8);
const diff = [];
for (let i = 0; i < Math.max(a.pages.length, b.pages.length); i++) if (a.pages[i] !== b.pages[i]) diff.push(i + 1);
const same = diff.length === 0;
console.log(`SWE5006 report: ${short} has ${a.pages.length} pages, the working tree ${b.pages.length}.`);
console.log(`Generated source (dist/report.typ): ${a.source === b.source ? 'identical' : 'differs'}.`);
if (same) console.log(`All ${b.pages.length} pages render identically at ${ppi} ppi.`);
else {
  console.log(`${diff.length} page(s) differ: ${diff.slice(0, 20).join(', ')}${diff.length > 20 ? ', …' : ''}.`);
  console.log(`Pages: ${a.dir} (base) and ${b.dir} (working tree).`);
}
if (keep || !same) console.log(`Kept: ${work}`);
else rmSync(work, { recursive: true, force: true });
process.exit(same ? 0 : 1);
