#!/usr/bin/env node
// Renders one section file alone to PNG pages, to check its figures and tables by eye.
// Usage: node scripts/figure.mjs sections/03-4-1-eric.md  ->  dist/preview/03-4-1-eric-<page>.png
import { execFileSync } from 'node:child_process';
import { mkdirSync, readFileSync, readdirSync, rmSync, writeFileSync } from 'node:fs';
import { basename, dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { markdown } from './typst.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repo = resolve(root, '../../..');
const file = resolve(process.argv[2] || '');
const name = basename(file, '.md');
const rootPath = (p) => `/${relative(repo, p).split('\\').join('/')}`;
const out = join(root, 'dist', 'preview');
mkdirSync(out, { recursive: true });
for (const f of readdirSync(out)) if (f.startsWith(`${name}-`)) rmSync(join(out, f));
const body = markdown(readFileSync(file, 'utf8'), { sectionDir: dirname(file), root: repo, resolvePath: (d, r) => rootPath(resolve(d, r)) });
const typ = join(out, `${name}.typ`);
writeFileSync(typ, `#import "${rootPath(join(root, 'typst', 'template.typ'))}": *\n#show: preview\n\n${body}\n`);
execFileSync('typst', ['compile', '--root', repo, '--ppi', '110', typ, join(out, `${name}-{p}.png`)], { stdio: 'inherit' });
console.log(readdirSync(out).filter((f) => f.startsWith(`${name}-`) && f.endsWith('.png')).map((f) => relative(process.cwd(), join(out, f))).join('\n'));
