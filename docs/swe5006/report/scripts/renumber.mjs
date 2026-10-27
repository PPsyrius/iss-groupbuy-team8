#!/usr/bin/env node
// Renumbers figures and tables sequentially per chapter (Figure 3.1, 3.2, …; Figure A.1 in Appendix A)
// in report order, and rewrites every "Figure <old>" reference in all sections to match.
// Writers may number figures by their own section (Figure 3.4.3.2) so parallel drafts never clash.
// Usage: node scripts/renumber.mjs [--dry-run]
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { fenceCloses, fenceOpen } from './typst.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const sectionsDir = join(root, 'sections');
const dry = process.argv.includes('--dry-run');
const files = readdirSync(sectionsDir).filter((f) => f.endsWith('.md')).sort();
const NUM = '(?:\\d+|[A-E])(?:\\.\\d+)*';

// Captions: an image's alt text, or the first line of the paragraph after a Mermaid fence.
const maps = { Figure: new Map(), Table: new Map() };
const counters = { Figure: {}, Table: {} };
for (const f of files) {
  const lines = readFileSync(join(sectionsDir, f), 'utf8').split('\n');
  let fence = null;
  for (let i = 0; i < lines.length; i++) {
    const l = lines[i];
    const opener = !fence && fenceOpen(l);
    if (opener || (fence && fenceCloses(l, fence))) {
      const closing = fence;
      fence = opener || null;
      if (closing && closing.lang === 'mermaid') {
        let j = i + 1;
        while (j < lines.length && !lines[j].trim()) j++;
        const m = (lines[j] || '').match(new RegExp(`^Figure (${NUM}):`));
        if (m) assign('Figure', m[1], f);
      }
      continue;
    }
    if (fence) continue;
    for (const m of l.matchAll(new RegExp(`!\\[Figure (${NUM}):`, 'g'))) assign('Figure', m[1], f);
    const t = l.match(new RegExp(`^Table (${NUM}):`));
    if (t) assign('Table', t[1], f);
  }
}

function assign(kind, old, file) {
  const chapter = file.startsWith('06-') ? file[3].toUpperCase() : old.split('.')[0];
  const c = counters[kind];
  c[chapter] = (c[chapter] || 0) + 1;
  const next = `${chapter}.${c[chapter]}`;
  if (maps[kind].has(old)) console.warn(`duplicate ${kind} ${old} in ${file}; the first one keeps the number`);
  else maps[kind].set(old, next);
}

let changed = 0;
const contents = new Map(files.map((f) => [f, readFileSync(join(sectionsDir, f), 'utf8')]));
for (const kind of ['Figure', 'Table']) {
  const map = maps[kind];
  const olds = [...map.keys()].sort((a, b) => b.length - a.length);
  if (!olds.length) continue;
  const alt = olds.map((o) => o.replace(/\./g, '\\.')).join('|');
  const single = new RegExp(`\\b${kind} (${alt})(?!\\.?\\d)`, 'g');
  const plural = new RegExp(`\\b${kind}s (${NUM})((?:,? (?:and |to |– |-)?${NUM})+)`, 'g');
  for (const [f, src] of contents) {
    const out = src
      .replace(single, (_, old) => `${kind} ${map.get(old)}`)
      .replace(plural, (all) => all.replace(new RegExp(NUM, 'g'), (n) => map.get(n) ?? n));
    contents.set(f, out);
  }
  for (const [o, n] of map) if (o !== n) console.log(`${kind} ${o} -> ${n}`);
}
for (const [f, out] of contents) {
  const path = join(sectionsDir, f);
  if (out !== readFileSync(path, 'utf8')) {
    changed++;
    if (!dry) writeFileSync(path, out);
  }
}
console.log(`${maps.Figure.size} figures, ${maps.Table.size} tables, ${changed} files ${dry ? 'would change' : 'changed'}.`);
