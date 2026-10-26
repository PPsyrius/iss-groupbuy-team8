#!/usr/bin/env node
// Assembles sections/*.md, in file-name order, into the course .docx template with pandoc.
// Usage: REPORT_TEMPLATE=/path/to/template.docx node scripts/build.mjs
// Output: dist/swe5006-report.md (the assembled source) and dist/swe5006-report.docx.
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const sectionsDir = join(root, 'sections');
const dist = join(root, 'dist');
const template = process.env.REPORT_TEMPLATE;

function need(cmd, hint) {
  try {
    execFileSync(cmd, ['--version'], { stdio: 'ignore' });
  } catch {
    console.error(`${cmd} not found. ${hint}`);
    process.exit(1);
  }
}

if (!template || !existsSync(template)) {
  console.error('Set REPORT_TEMPLATE to the course template "SWE5006 - Project Report Template for Practice Module.docx" (kept out of the repository; ask the editor).');
  process.exit(1);
}
need('pandoc', 'Install it: brew install pandoc (or https://pandoc.org/installing.html).');
need('rsvg-convert', 'SVG figures need it: brew install librsvg.');

const files = readdirSync(sectionsDir).filter((f) => f.endsWith('.md')).sort();
const body = files
  .map((f) => readFileSync(join(sectionsDir, f), 'utf8').replace(/^<!-- report\n[\s\S]*?\n-->\n*/, ''))
  .join('\n\n');

mkdirSync(dist, { recursive: true });
const md = join(dist, 'swe5006-report.md');
const docx = join(dist, 'swe5006-report.docx');
writeFileSync(md, body);

execFileSync(
  'pandoc',
  [
    md,
    '--from=markdown',
    '--to=docx',
    `--reference-doc=${template}`,
    `--resource-path=${sectionsDir}`,
    `--lua-filter=${join(root, 'scripts', 'strip-numbers.lua')}`,
    '--toc',
    '--toc-depth=3',
    `--output=${docx}`,
  ],
  { stdio: 'inherit' },
);
console.log(`Built ${docx} from ${files.length} sections. Open it in Word, update fields and the contents table, then export the PDF.`);
