#!/usr/bin/env node
// Assembles sections/*.md, in file-name order, into one Typst document and compiles the PDF.
// Usage: node scripts/build.mjs [--out <dir>]
//   REPORT_TEMPLATE=<course template .docx> (optional) supplies the NUS-ISS header logo,
//   which stays out of the repository like the template itself.
// Output: dist/report.typ (generated) and dist/swe5006-report.pdf; --out also copies the PDF there.
import { execFileSync } from 'node:child_process';
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { frontMatter, markdown, str } from './typst.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repo = resolve(root, '../../..');
const sectionsDir = join(root, 'sections');
const dist = join(root, 'dist');
const outIdx = process.argv.indexOf('--out');
const outDir = outIdx > -1 ? process.argv[outIdx + 1] : process.env.REPORT_OUT;

try {
  execFileSync('typst', ['--version'], { stdio: 'ignore' });
} catch {
  console.error('typst not found. Install it: brew install typst');
  process.exit(1);
}

const rootPath = (p) => `/${relative(repo, p).split('\\').join('/')}`;
const resolvePath = (dir, rel) => rootPath(resolve(dir, rel));

mkdirSync(dist, { recursive: true });
let logo = 'none';
const template = process.env.REPORT_TEMPLATE;
if (template && existsSync(template)) {
  const png = execFileSync('unzip', ['-p', template, 'word/media/image1.png']);
  writeFileSync(join(dist, 'logo.png'), png);
  logo = str(rootPath(join(dist, 'logo.png')));
}

const files = readdirSync(sectionsDir).filter((f) => f.endsWith('.md')).sort();
let meta = {};
const parts = [];
for (const f of files) {
  const src = readFileSync(join(sectionsDir, f), 'utf8');
  if (f.startsWith('00-')) {
    meta = frontMatter(src.replace(/^<!-- report\n[\s\S]*?\n-->\n*/, ''));
    continue;
  }
  try {
    parts.push(`// ---- ${f}\n${markdown(src, { sectionDir: sectionsDir, root: repo, resolvePath })}`);
  } catch (e) {
    console.error(`${f}: ${e.message}`);
    process.exit(1);
  }
}

const metaTyp = `(title: ${str(meta.title || '')}, subtitle: ${str(meta.subtitle || '')}, author: (${(meta.author || []).map(str).join(', ')},), date: ${str(meta.date || '')})`;
const typ = join(dist, 'report.typ');
writeFileSync(
  typ,
  `#import "${rootPath(join(root, 'typst', 'template.typ'))}": *\n#show: report.with(meta: ${metaTyp}, logo: ${logo})\n\n${parts.join('\n\n')}\n`,
);
const pdf = join(dist, 'swe5006-report.pdf');
execFileSync('typst', ['compile', '--root', repo, typ, pdf], { stdio: 'inherit' });
console.log(`Built ${relative(process.cwd(), pdf)} from ${files.length} sections.`);
if (outDir) {
  mkdirSync(outDir, { recursive: true });
  const dest = join(outDir, 'SWE5006-Team8-Final-Report-draft.pdf');
  copyFileSync(pdf, dest);
  console.log(`Copied to ${dest}`);
}
