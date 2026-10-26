#!/usr/bin/env node
// Checks the report sections against the structure RUBRIC.md requires.
// Usage: node scripts/check.mjs [--strict]
//   default: structure, figure and language problems are errors; open markers are listed.
//   --strict: open markers are errors too (freeze gate, rubric checklist).
import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { mermaid } from './typst.mjs';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const sectionsDir = resolve(process.env.REPORT_SECTIONS || join(root, 'sections'));
const strict = process.argv.includes('--strict');

const MEMBERS = {
  1: 'Wang Ziang (Eric)',
  2: 'Yu Qianshuo (Yorik)',
  3: 'Panpakorn Siripanich (Pan)',
  4: 'Lim Jiechao',
  5: 'Liu Jin',
};

// Template headings in report order (rubric G2, G3). Titles are matched exactly unless a RegExp is given.
const EXPECTED = [
  [1, '1', 'Introduction'],
  [2, '1.1', 'Background'],
  [2, '1.2', 'Business Needs'],
  [2, '1.3', 'Stakeholders'],
  [2, '1.4', 'Project Scope'],
  [3, '1.4.1', 'Functionality in Scope'],
  [3, '1.4.2', 'Functionality out of Scope'],
  [3, '1.4.3', 'Quality Attributes'],
  [1, '2', 'Project Conduct'],
  [2, '2.1', 'Project Plan'],
  [2, '2.2', 'Project Status'],
  [2, '2.3', 'Project Metrics'],
  [1, '3', 'System Design'],
  [2, '3.1', 'Software Architecture'],
  [2, '3.2', 'Transition from Analysis to Design'],
  ...[1, 2, 3, 4, 5, 6, 7, 8, 9].map((n) => [3, `3.2.${n}`, /.+/]),
  [2, '3.3', 'Use Case Model'],
  [3, '3.3.1', 'Use Case Diagram'],
  ...[2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map((n) => [3, `3.3.${n}`, /.+/]),
  [2, '3.4', 'Analysis & Design Models'],
  ...[1, 2, 3, 4, 5].map((n) => [3, `3.4.${n}`, new RegExp(` by ${escape(MEMBERS[n])}$`)]),
  [2, '3.5', 'Design Problems and Patterns'],
  ...[1, 2, 3, 4, 5].map((n) => [3, `3.5.${n}`, new RegExp(` by ${escape(MEMBERS[n])}$`)]),
  [2, '3.6', 'Database Schemas'],
  [1, '4', 'DevSecOps and Development Lifecycle'],
  [2, '4.1', 'Source Control Strategy'],
  [2, '4.2', 'Continuous Integration'],
  [2, '4.3', 'Continuous Delivery'],
  [1, '5', 'Individual members activity contribution summary'],
];

// Fixed part headings per member subsection (rubric 3.4 and 3.5 checklists) and per strategy (3.2.N).
const PARTS = {
  '3.4': ['A Analysis class diagram', 'B Analysis sequence diagrams', 'C Design class diagram', 'D Design sequence diagrams', 'E Compliance with the transition strategies'],
  '3.5': ['1 Design problem', '2 Candidate design patterns', '3 Motivation', '4 Solution class diagram', '5 Solution sequence diagrams', '6 Implementation decisions'],
  '3.2': ['Analysis objects affected', 'Use cases affected', 'Static structure changes', 'Dynamic behaviour changes'],
};

const HEADER_KEYS = ['rubric', 'owner', 'plan', 'sources', 'status'];
const STATUSES = ['missing', 'draft', 'ready'];
const MARKER = /\{\{(FILL|VERIFY|CONFIRM)(?::\s*([^}]*))?\}\}/g;
const CJK = /[　-〿぀-ヿ㐀-䶿一-鿿가-힯＀-￯]/;

// The numbered section a "file:line" location falls in.
function sectionAt(at) {
  const [file, line] = at.split(':');
  let last = null;
  for (const h of headings) {
    if (!h.number || h.file !== file) continue;
    if (Number(h.at.split(':')[1]) <= Number(line)) last = h.number;
  }
  return last;
}

function escape(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

const errors = [];
const figures = []; // { num, at, chapter }
const markers = [];
const words = new Map();
const headings = [];

const files = readdirSync(sectionsDir).filter((f) => f.endsWith('.md')).sort();
if (files.length === 0) errors.push('sections/: no Markdown files');

for (const file of files) {
  const path = join(sectionsDir, file);
  const text = readFileSync(path, 'utf8');
  const lines = text.split('\n');

  const header = text.match(/^<!-- report\n([\s\S]*?)\n-->/);
  if (!header) {
    errors.push(`${file}:1 missing the "<!-- report … -->" header comment`);
  } else {
    const fields = Object.fromEntries(
      header[1].split('\n').map((l) => l.match(/^(\w+):\s*(.*)$/)).filter(Boolean).map((m) => [m[1], m[2].trim()]),
    );
    for (const key of HEADER_KEYS) if (!(key in fields)) errors.push(`${file}:1 header has no "${key}:"`);
    if (fields.status && !STATUSES.includes(fields.status)) errors.push(`${file}:1 status "${fields.status}" is not one of ${STATUSES.join(', ')}`);
  }

  let inFence = false;
  let fence = null;
  let current = null;
  const chapterOf = () => (file.startsWith('06-') ? file[3].toUpperCase() : current ? current.split('.')[0] : (file.match(/^0(\d)/) || [])[1]);
  lines.forEach((line, i) => {
    const at = `${file}:${i + 1}`;
    if (/^```/.test(line)) {
      inFence = !inFence;
      if (inFence) fence = { lang: line.slice(3).trim(), start: at, body: [] };
      else if (fence.lang === 'mermaid') {
        try {
          mermaid(fence.body.join('\n'));
        } catch (e) {
          errors.push(`${fence.start} Mermaid: ${e.message}`);
        }
        let j = i + 1;
        while (j < lines.length && !lines[j].trim()) j++;
        const cap = (lines[j] || '').trim();
        const m = cap.match(/^Figure ((?:\d+|[A-E])(?:\.\d+)*): .+\. .+/);
        if (!m) errors.push(`${fence.start} Mermaid figure needs a caption paragraph right after the fence: "Figure N.M: <viewpoint>. <what to notice>." (rubric F2)`);
        else figures.push({ num: m[1], at: `${file}:${j + 1}`, chapter: chapterOf() });
      }
      return;
    }
    if (inFence) {
      fence.body.push(line);
      return;
    }

    const h = line.match(/^(#{1,4}) (.+?)\s*(\{[^}]*\})?$/);
    if (h) {
      const level = h[1].length;
      const title = h[2];
      const attrs = h[3] || '';
      const num = title.match(/^(\d+(?:\.\d+)*) (.+)$/);
      if (level <= 3 && num) {
        headings.push({ level, number: num[1], title: num[2], at, file });
        current = num[1];
        words.set(current, 0);
      } else if (level <= 3 && !/\.(unnumbered|appendix)/.test(attrs)) {
        errors.push(`${at} heading "${title}" has no template number (use {.unnumbered} or {.appendix} if intended)`);
      } else if (level === 4 && current) {
        headings.push({ level, number: null, title, at, file, parent: current });
      }
      return;
    }

    for (const m of line.matchAll(MARKER)) markers.push({ at, kind: m[1], note: (m[2] || '').trim() });
    if (CJK.test(line)) errors.push(`${at} non-English text (rubric G5)`);

    for (const m of line.matchAll(/!\[([^\]]*)\]\(([^)\s]+)[^)]*\)/g)) {
      const [, alt, src] = m;
      const fm = alt.match(/^Figure ((?:\d+|[A-E])(?:\.\d+)*): .+\. .+/);
      if (!fm) errors.push(`${at} figure caption must read "Figure N.M: <viewpoint>. <what to notice>." (rubric F2)`);
      else figures.push({ num: fm[1], at, chapter: chapterOf() });
      if (!/^https?:/.test(src) && !existsSync(resolve(sectionsDir, src))) errors.push(`${at} figure file not found: ${src}`);
    }

    if (current && line.trim() && !line.startsWith('<!--')) {
      const plain = line.replace(MARKER, '').replace(/[|*`#>-]/g, ' ');
      words.set(current, words.get(current) + plain.split(/\s+/).filter(Boolean).length);
    }
  });
}

// Template headings present, in order, at the right level.
const numbered = headings.filter((h) => h.number);
let cursor = 0;
for (const [level, number, title] of EXPECTED) {
  const idx = numbered.findIndex((h, i) => i >= cursor && h.number === number);
  if (idx === -1) {
    const anywhere = numbered.find((h) => h.number === number);
    errors.push(anywhere ? `${anywhere.at} ${number} is out of template order` : `missing template section ${number}`);
    continue;
  }
  const h = numbered[idx];
  cursor = idx + 1;
  if (h.level !== level) errors.push(`${h.at} ${number} should be heading level ${level}, found ${h.level}`);
  const ok = title instanceof RegExp ? title.test(h.title) : h.title === title;
  if (!ok) errors.push(`${h.at} ${number} title "${h.title}" should be ${title instanceof RegExp ? `like ${title}` : `"${title}"`}`);
}
const known = new Set(EXPECTED.map((e) => e[1]));
for (const h of numbered) if (!known.has(h.number)) errors.push(`${h.at} ${h.number} is not a template section; add it as a #### subheading instead (rubric G2)`);
const seen = new Map();
for (const h of numbered) {
  if (seen.has(h.number)) errors.push(`${h.at} ${h.number} duplicates ${seen.get(h.number)}`);
  else seen.set(h.number, h.at);
}

// Required parts under 3.2.N, 3.4.N and 3.5.N, plus two rationale lines per strategy.
for (const h of numbered.filter((x) => /^3\.[245]\.\d+$/.test(x.number))) {
  const family = h.number.slice(0, 3);
  const sub = headings.filter((x) => x.parent === h.number).map((x) => x.title);
  for (const part of PARTS[family]) if (!sub.includes(part)) errors.push(`${h.at} ${h.number} lacks the part "#### ${part}" (rubric ${family} checklist)`);
}
for (const file of files) {
  const text = readFileSync(join(sectionsDir, file), 'utf8');
  for (const block of text.split(/^(?=### 3\.2\.\d+ )/m).slice(1)) {
    const num = block.match(/^### (3\.2\.\d+)/)[1];
    const count = (block.match(/^Rationale:/gm) || []).length;
    if (count < 2) errors.push(`${file} ${num} needs a "Rationale:" line for both static and dynamic changes (rubric 3.2.N), found ${count}`);
  }
}

// Figure numbers: unique, and in the chapter they sit in (Figure 3.N in chapter 3).
const figSeen = new Map();
for (const f of figures) {
  if (figSeen.has(f.num)) errors.push(`${f.at} Figure ${f.num} is also used at ${figSeen.get(f.num)}`);
  else figSeen.set(f.num, f.at);
  if (f.chapter && f.num.split('.')[0] !== f.chapter) errors.push(`${f.at} Figure ${f.num} sits in chapter ${f.chapter}; number it ${f.chapter}.N`);
}

// Report.
const byKind = markers.reduce((acc, m) => ((acc[m.kind] = (acc[m.kind] || 0) + 1), acc), {});
const byFile = markers.reduce((acc, m) => {
  const f = m.at.split(':')[0];
  acc[f] = (acc[f] || 0) + 1;
  return acc;
}, {});

console.log(`Sections: ${files.length} files, ${numbered.length} template headings, ${figures.length} figures.`);
console.log(`Open markers: ${markers.length} (${Object.entries(byKind).map(([k, v]) => `${k} ${v}`).join(', ') || 'none'}).`);
if (markers.length) {
  for (const [f, n] of Object.entries(byFile)) console.log(`  ${String(n).padStart(3)}  ${f}`);
}
// A section with no open marker is claimed written; flag it if it is still nearly empty.
const openSections = new Set(markers.map((m) => sectionAt(m.at)));
const hasChildren = (h) => numbered.some((x) => x.number.startsWith(`${h.number}.`));
const thin = numbered.filter((h) => h.level > 1 && !hasChildren(h) && !openSections.has(h.number) && (words.get(h.number) || 0) < 30).map((h) => h.number);
if (thin.length) console.log(`Sections without markers but under 30 words: ${thin.join(', ')}`);

if (strict) for (const m of markers) errors.push(`${m.at} open ${m.kind} marker${m.note ? `: ${m.note}` : ''}`);

if (errors.length) {
  console.error(`\n${errors.length} error(s):`);
  for (const e of errors) console.error(`  ${e}`);
  process.exit(1);
}
console.log(strict ? '\nStrict check passed: ready to freeze.' : '\nStructure check passed.');
