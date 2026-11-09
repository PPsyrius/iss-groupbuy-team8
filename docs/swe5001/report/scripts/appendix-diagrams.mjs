#!/usr/bin/env node
// Appendix A diagrams: Mermaid sources -> Typst drawings in the SWE5006 UML kit (uml.typ).
//
//   node docs/swe5001/report/scripts/appendix-diagrams.mjs            all sources
//   node docs/swe5001/report/scripts/appendix-diagrams.mjs a-uc04-ac  only these ids
//   node docs/swe5001/report/scripts/appendix-diagrams.mjs --check    fail if gen/ is stale
//
// Reads diagrams/appendix-a/src/<id>.mmd and writes diagrams/appendix-a/gen/<id>.typ, which
// exports `kind` ("class" | "sequence" | "state") and `drawing(host: none)`. The appendix places
// a drawing with `afig` from sections/appendix-a/lib-a.typ. The translation is `mermaid()` from
// the SWE5006 report (docs/swe5006/report/scripts/typst.mjs); this script adds the checks that
// translator leaves out: it ignores any line it does not understand, so here every line must
// match a construct it supports, every participant and class must be declared, and every layout
// pin must name a declared element. See diagrams/appendix-a/README.md.
import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { basename, dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { mermaid } from '../../../swe5006/report/scripts/typst.mjs';

const reportDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repo = resolve(reportDir, '../../..');
const srcDir = join(reportDir, 'diagrams/appendix-a/src');
const genDir = join(reportDir, 'diagrams/appendix-a/gen');
const UML = '/docs/swe5006/report/typst/uml.typ';
const ID = /^a-[a-z0-9]+(?:-[a-z0-9]+)*$/;

class DiagramError extends Error {}

// ---- Validation ---------------------------------------------------------------

// The relation pattern of typst.mjs (CLASS_REL), kept in step with it.
const CLASS_REL = /^([\w.]+)\s*(?:"([^"]*)")?\s*(<\|--|--\|>|\*--|--\*|o--|--o|<\|\.\.|\.\.\|>|-->|<--|\.\.>|<\.\.|--|\.\.)\s*(?:"([^"]*)")?\s*([\w.]+)\s*(?::\s*(.*))?$/;
const PIN = /^%%\s*at\s+(\S+)\s+(-?[\d.]+)\s*,\s*(-?[\d.]+)\s*$/;

function pins(lines) {
  const out = [];
  lines.forEach((l, i) => {
    const t = l.trim();
    if (/^%%\s*at\b/.test(t)) {
      const m = t.match(PIN);
      if (!m) throw new DiagramError(`line ${i + 1}: malformed pin "${t}" (use "%% at Id col,row")`);
      out.push({ id: m[1], line: i + 1, col: Number(m[2]), row: Number(m[3]) });
    }
  });
  return out;
}

function checkClass(lines, start) {
  const declared = new Set();
  const used = [];
  let open = null;
  for (let i = start; i < lines.length; i++) {
    const l = lines[i].trim();
    const at = `line ${i + 1}`;
    if (!l || l.startsWith('%%')) continue;
    if (open) {
      if (l === '}') open = null;
      else if (/[{}]/.test(l)) throw new DiagramError(`${at}: brace inside class body of ${open}`);
      continue;
    }
    let m;
    if (/^direction\s+(TB|TD|BT|LR|RL)$/.test(l)) continue;
    if ((m = l.match(/^class\s+([\w.]+)(?:\["([^"]+)"\])?(?:~([^~]+)~)?\s*(\{)?\s*$/))) {
      declared.add(m[1]);
      if (m[4]) open = m[1];
      continue;
    }
    if ((m = l.match(/^<<(.+)>>\s+([\w.]+)$/))) {
      used.push({ id: m[2], at });
      continue;
    }
    if ((m = l.match(CLASS_REL))) {
      used.push({ id: m[1], at }, { id: m[5], at });
      continue;
    }
    if ((m = l.match(/^([\w.]+)\s*:\s*(.+)$/))) {
      used.push({ id: m[1], at });
      continue;
    }
    throw new DiagramError(`${at}: unsupported in classDiagram: "${l}" (no namespace, note, style, classDef, click, link or :::)`);
  }
  if (open) throw new DiagramError(`class ${open}: body not closed with "}"`);
  for (const u of used) if (!declared.has(u.id)) throw new DiagramError(`${u.at}: "${u.id}" is not declared; add "class ${u.id}" (catches typos)`);
  return declared;
}

function checkSequence(lines, start) {
  const declared = new Set();
  const stack = [];
  let count = 0;
  for (let i = start; i < lines.length; i++) {
    const l = lines[i].trim();
    const at = `line ${i + 1}`;
    if (!l || l.startsWith('%%')) continue;
    let m;
    const need = (id) => {
      if (!declared.has(id)) throw new DiagramError(`${at}: participant "${id}" is not declared; add "participant ${id} as …" first`);
    };
    if ((m = l.match(/^(create\s+)?(participant|actor)\s+([^\s]+)(?:\s+as\s+(.+))?$/))) {
      declared.add(m[3]);
      continue;
    }
    if ((m = l.match(/^(activate|deactivate|destroy)\s+(\S+)$/))) {
      need(m[2]);
      continue;
    }
    if (/^(autonumber|title)\b/.test(l)) continue;
    if ((m = l.match(/^(alt|opt|loop|par|critical|break|rect)\b\s*(.*)$/))) {
      if (m[1] === 'rect' && m[2]) throw new DiagramError(`${at}: "rect ${m[2]}": colours are not supported; use a plain "rect" or a fragment`);
      stack.push({ kind: m[1], at });
      continue;
    }
    if ((m = l.match(/^(else|and|option)\b\s*(.*)$/))) {
      const top = stack[stack.length - 1];
      const fits = { else: ['alt'], and: ['par'], option: ['critical'] }[m[1]];
      if (!top || !fits.includes(top.kind)) throw new DiagramError(`${at}: "${m[1]}" outside ${fits.join('/')}`);
      continue;
    }
    if (l === 'end') {
      if (!stack.pop()) throw new DiagramError(`${at}: "end" without an open fragment`);
      continue;
    }
    if ((m = l.match(/^note\s+(over|right of|left of)\s+([^:]+):\s*(.*)$/i))) {
      m[2].split(',').map((s) => s.trim()).forEach(need);
      continue;
    }
    if ((m = l.match(/^([^\s\-+<>]+?)\s*(-->>|->>|--\)|-\)|-->|->|--x|-x)\s*([+-]?)\s*([^\s:]+?)\s*:\s*(.*)$/))) {
      need(m[1]);
      need(m[4]);
      if (!m[5].trim()) throw new DiagramError(`${at}: message without a label`);
      count++;
      continue;
    }
    throw new DiagramError(`${at}: unsupported in sequenceDiagram: "${l}" (no box, link, ref, messages without ":" label)`);
  }
  if (stack.length) throw new DiagramError(`${stack[stack.length - 1].at}: "${stack[stack.length - 1].kind}" is never closed with "end"`);
  if (!count) throw new DiagramError('sequence diagram has no messages');
  return declared;
}

function checkState(lines, start) {
  const declared = new Set();
  let starts = 0;
  let inNote = false;
  for (let i = start; i < lines.length; i++) {
    const l = lines[i].trim();
    const at = `line ${i + 1}`;
    if (!l || l.startsWith('%%')) continue;
    if (inNote) {
      if (/^end note/.test(l)) inNote = false;
      continue;
    }
    let m;
    if (/^direction\s+(TB|TD|BT|LR|RL)$/.test(l)) continue;
    if (/^note\s/.test(l)) throw new DiagramError(`${at}: notes are dropped by the translator; put the text in the caption or a state's body`);
    if ((m = l.match(/^state\s+"([^"]+)"\s+as\s+(\w+)\s*$/))) {
      declared.add(m[2]);
      continue;
    }
    if ((m = l.match(/^state\s+(\w+)\s+<<(choice|fork|join)>>\s*$/))) {
      declared.add(m[1]);
      continue;
    }
    if (/^state\s+\w+\s*\{$/.test(l) || l === '--') throw new DiagramError(`${at}: composite and concurrent states are not supported; flatten them`);
    if ((m = l.match(/^(\[\*\]|\w+)\s*-->\s*(\[\*\]|\w+)\s*(?::\s*(.*))?$/))) {
      for (const s of [m[1], m[2]]) if (s !== '[*]') declared.add(s);
      if (m[1] === '[*]') declared.add(`__start${starts++}`);
      continue;
    }
    if ((m = l.match(/^(\w+)\s*:\s*(.+)$/))) {
      declared.add(m[1]);
      continue;
    }
    throw new DiagramError(`${at}: unsupported in stateDiagram-v2: "${l}"`);
  }
  // End pseudostates are numbered too; pins may name them.
  for (let k = 0; k < 20; k++) declared.add(`__end${k}`);
  return declared;
}

function validate(src) {
  const lines = src.split('\n');
  const frameLine = lines.findIndex((l) => l.trim());
  if (frameLine < 0 || !/^%%\s*frame:\s*\S/.test(lines[frameLine].trim())) {
    throw new DiagramError('first line must be "%% frame: <frame name>" (the heading in the diagram frame tab)');
  }
  const first = lines.findIndex((l) => l.trim() && !l.trim().startsWith('%%'));
  if (first < 0) throw new DiagramError('no diagram type line');
  const head = lines[first].trim();
  let kind;
  let declared;
  if (/^classDiagram\b/.test(head)) [kind, declared] = ['class', checkClass(lines, first + 1)];
  else if (/^sequenceDiagram\b/.test(head)) [kind, declared] = ['sequence', checkSequence(lines, first + 1)];
  else if (/^stateDiagram-v2\b/.test(head)) [kind, declared] = ['state', checkState(lines, first + 1)];
  else throw new DiagramError(`unsupported diagram type "${head}" (use classDiagram, sequenceDiagram or stateDiagram-v2)`);
  const ps = pins(lines);
  if (kind === 'sequence' && ps.length) throw new DiagramError(`line ${ps[0].line}: pins do nothing in a sequence diagram (participant order is declaration order)`);
  for (const p of ps) if (!declared.has(p.id)) throw new DiagramError(`line ${p.line}: pin names unknown element "${p.id}"`);
  const seen = new Map();
  for (const p of ps) {
    const key = `${p.col},${p.row}`;
    if (seen.has(key)) throw new DiagramError(`line ${p.line}: "${p.id}" pinned to ${key}, already taken by "${seen.get(key)}"`);
    seen.set(key, p.id);
  }
  return { kind, count: declared.size, pins: ps.length };
}

// ---- Cross-references -----------------------------------------------------------

// "@fig-a-uc04-ds-terms" (or @tbl-…, @sec-…) in a message, note or edge label becomes a real
// reference, so the drawing prints "Figure A.43" and follows renumbering. Each Typst string
// literal holding one is rewritten as content: "see @fig-x." -> [#"see "#ref(label("fig-x"))#"."].
const REF = /@((?:fig|tbl|sec)-[a-z0-9]+(?:-[a-z0-9]+)*)/g;

function checkRefs(src) {
  src.split('\n').forEach((l, i) => {
    const t = l.trim();
    if (/^(alt|opt|loop|par|critical|break|else|and|option)\b/.test(t) && REF.test(t)) {
      throw new DiagramError(`line ${i + 1}: references are not supported in fragment guards; put them in a Note`);
    }
    REF.lastIndex = 0;
    if (/^(participant|actor|create|class|state)\b/.test(t) && REF.test(t)) {
      throw new DiagramError(`line ${i + 1}: references are not supported in names`);
    }
    REF.lastIndex = 0;
  });
}

function linkRefs(call) {
  return call.replace(/"((?:[^"\\]|\\.)*)"/g, (lit, body) => {
    REF.lastIndex = 0;
    if (!REF.test(body)) return lit;
    REF.lastIndex = 0;
    const parts = [];
    let at = 0;
    for (const m of body.matchAll(REF)) {
      if (m.index > at) parts.push(`#"${body.slice(at, m.index)}"`);
      parts.push(`#ref(label("${m[1]}"))`);
      at = m.index + m[0].length;
    }
    if (at < body.length) parts.push(`#"${body.slice(at)}"`);
    return `[${parts.join('')}]`;
  });
}

// ---- Generation ---------------------------------------------------------------

function generate(id) {
  const file = join(srcDir, `${id}.mmd`);
  if (!existsSync(file)) throw new DiagramError(`no source ${relative(repo, file)}`);
  const src = readFileSync(file, 'utf8');
  const info = validate(src);
  checkRefs(src);
  // Activation bars run unbroken through the fragments that close and reopen them (UML).
  const call = linkRefs(mermaid(src, '', { continuousActivations: true }));
  const m = call.match(/^#((?:class|state|sequence)-diagram\(.*)\)$/s);
  if (!m) throw new DiagramError('translator returned an unexpected call');
  const fn = { class: 'class-diagram', sequence: 'sequence-diagram', state: 'state-diagram' }[info.kind];
  const typ = [
    `// Generated by docs/swe5001/report/scripts/appendix-diagrams.mjs from ../src/${id}.mmd. Do not edit;`,
    '// edit the source and run the script again.',
    `#import "${UML}": ${fn}`,
    `#let kind = "${info.kind}"`,
    `#let drawing(host: none) = ${m[1]}, host: host)`,
    '',
  ].join('\n');
  return { typ, info };
}

const args = process.argv.slice(2);
const check = args.includes('--check');
const wanted = args.filter((a) => !a.startsWith('--'));
if (!existsSync(srcDir)) {
  console.error(`missing ${relative(process.cwd(), srcDir)}`);
  process.exit(1);
}
const all = readdirSync(srcDir).filter((f) => f.endsWith('.mmd')).map((f) => basename(f, '.mmd')).sort();
const ids = wanted.length ? wanted.map((w) => basename(w, '.mmd')) : all;
mkdirSync(genDir, { recursive: true });
let failed = 0;
let stale = 0;
for (const id of ids) {
  try {
    if (!ID.test(id)) throw new DiagramError('id must be lowercase kebab-case starting with "a-" (e.g. a-uc04-ac)');
    const { typ, info } = generate(id);
    const out = join(genDir, `${id}.typ`);
    const old = existsSync(out) ? readFileSync(out, 'utf8') : null;
    if (check) {
      if (old !== typ) {
        stale++;
        console.log(`STALE ${id}`);
      } else console.log(`ok    ${id}  ${info.kind}`);
      continue;
    }
    writeFileSync(out, typ);
    const what = info.kind === 'sequence' ? 'participants' : info.kind === 'class' ? 'classes' : 'states';
    console.log(`${old === typ ? 'same ' : 'wrote'} ${id}  ${info.kind}, ${info.count} ${what}, ${info.pins} pins, ${typ.length} bytes`);
  } catch (e) {
    failed++;
    console.error(`FAIL  ${id}  ${e instanceof DiagramError ? e.message : e.stack}`);
  }
}
if (check) {
  const genIds = existsSync(genDir) ? readdirSync(genDir).filter((f) => f.endsWith('.typ')).map((f) => basename(f, '.typ')) : [];
  for (const g of genIds) if (!all.includes(g)) {
    stale++;
    console.log(`ORPHAN ${g} (gen without src)`);
  }
}
if (failed || stale) {
  console.error(`${failed} failed, ${stale} stale`);
  process.exit(1);
}
