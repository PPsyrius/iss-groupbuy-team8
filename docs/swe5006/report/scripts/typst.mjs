// Markdown (the subset AGENTS.md allows) and Mermaid (classDiagram, sequenceDiagram,
// stateDiagram-v2) to Typst. Used by build.mjs and figure.mjs; no dependencies.

const MARKER = /\{\{(FILL|VERIFY|CONFIRM)(?::\s*([^}]*))?\}\}/;

export function str(s) {
  return `"${String(s).replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n').replace(/\t/g, '  ')}"`;
}

// ---- Inline -----------------------------------------------------------------

export function inline(text) {
  let out = '';
  let buf = '';
  const flush = () => {
    if (buf) out += `#${str(buf)}`;
    buf = '';
  };
  let i = 0;
  while (i < text.length) {
    const rest = text.slice(i);
    let m;
    if ((m = rest.match(/^\{\{(FILL|VERIFY|CONFIRM)(?::\s*([^}]*))?\}\}/))) {
      flush();
      out += `#marker(${str(m[1])}, ${str((m[2] || '').trim())})`;
      i += m[0].length;
    } else if ((m = rest.match(/^`([^`]+)`/))) {
      flush();
      out += `#raw(${str(m[1])})`;
      i += m[0].length;
    } else if ((m = rest.match(/^\*\*(.+?)\*\*/))) {
      flush();
      out += `#strong[${inline(m[1])}]`;
      i += m[0].length;
    } else if ((m = rest.match(/^\*([^*\s][^*]*?)\*/)) || (/^\W?$/.test(text[i - 1] || '') && (m = rest.match(/^_([^_\s][^_]*?)_(?!\w)/)))) {
      flush();
      out += `#emph[${inline(m[1])}]`;
      i += m[0].length;
    } else if ((m = rest.match(/^\[([^\]]+)\]\(([^)\s]+)\)/))) {
      flush();
      const url = m[2];
      out += /^https?:/.test(url) ? `#link(${str(url)})[${inline(m[1])}]` : inline(m[1]);
      i += m[0].length;
    } else if ((m = rest.match(/^<br\s*\/?>/i))) {
      flush();
      out += '#linebreak()';
      i += m[0].length;
    } else {
      buf += text[i];
      i += 1;
    }
  }
  flush();
  return out;
}

// ---- Mermaid ------------------------------------------------------------------

function typstArray(items) {
  return `(${items.join(', ')}${items.length === 1 ? ',' : ''})`;
}

function cleanType(s) {
  return s.replace(/~([^~]+)~/g, '<$1>').trim();
}

// Longest-path layering with cycle breaking, then barycentre ordering.
function layout(ids, edges, hints, dir) {
  const rank = Object.fromEntries(ids.map((id) => [id, 0]));
  const out = Object.fromEntries(ids.map((id) => [id, []]));
  for (const e of edges) if (e.a !== e.b && out[e.a]) out[e.a].push(e.b);
  // Break cycles: DFS, keep only forward and cross edges.
  const state = {};
  const dag = Object.fromEntries(ids.map((id) => [id, []]));
  const visit = (u) => {
    state[u] = 1;
    for (const v of out[u]) {
      if (state[v] === 1) continue;
      dag[u].push(v);
      if (!state[v]) visit(v);
    }
    state[u] = 2;
  };
  for (const id of ids) if (!state[id]) visit(id);
  // Topological longest path.
  const order = [];
  const seen = new Set();
  const topo = (u) => {
    if (seen.has(u)) return;
    seen.add(u);
    for (const v of dag[u]) topo(v);
    order.unshift(u);
  };
  for (const id of ids) topo(id);
  for (const u of order) for (const v of dag[u]) rank[v] = Math.max(rank[v], rank[u] + 1);
  const layers = [];
  for (const id of ids) (layers[rank[id]] ||= []).push(id);
  const pos = {};
  // Integer columns: fletcher sizes whole columns to their nodes, so fractional ones overlap.
  const widest = Math.max(1, ...layers.filter(Boolean).map((l) => l.length));
  const place = () => layers.forEach((l, r) => l.forEach((id, i) => (pos[id] = [Math.floor((widest - l.length) / 2) + i, r])));
  place();
  for (let pass = 0; pass < 3; pass++) {
    for (const l of layers) {
      if (!l) continue;
      const bary = (id) => {
        const ns = edges.filter((e) => e.a === id || e.b === id).map((e) => (e.a === id ? e.b : e.a)).filter((n) => pos[n] && pos[n][1] !== pos[id][1]);
        return ns.length ? ns.reduce((s, n) => s + pos[n][0], 0) / ns.length : pos[id][0];
      };
      l.sort((x, y) => bary(x) - bary(y));
    }
    place();
  }
  for (const [id, p] of Object.entries(hints)) if (pos[id]) pos[id] = p;
  if (dir === 'LR' || dir === 'RL') for (const id of ids) pos[id] = [pos[id][1], pos[id][0]];
  return pos;
}

function hintsFrom(lines) {
  const hints = {};
  for (const l of lines) {
    const m = l.trim().match(/^%%\s*at\s+(\S+)\s+(-?[\d.]+)\s*,\s*(-?[\d.]+)/);
    if (m) hints[m[1]] = [Number(m[2]), Number(m[3])];
  }
  return hints;
}

const CLASS_REL = /^([\w.]+)\s*(?:"([^"]*)")?\s*(<\|--|--\|>|\*--|--\*|o--|--o|<\|\.\.|\.\.\|>|-->|<--|\.\.>|<\.\.|--|\.\.)\s*(?:"([^"]*)")?\s*([\w.]+)\s*(?::\s*(.*))?$/;

function classDiagram(lines) {
  const classes = new Map();
  const rels = [];
  let dir = 'TB';
  const get = (id) => {
    if (!classes.has(id)) classes.set(id, { id, name: id, stereo: null, attrs: [], ops: [], abstract: false });
    return classes.get(id);
  };
  const member = (c, m) => {
    m = m.trim();
    if (!m) return;
    const st = m.match(/^<<(.+)>>$/);
    if (st) {
      c.stereo = st[1].trim();
      if (/abstract|interface/i.test(c.stereo)) c.abstract = /abstract/i.test(c.stereo);
      return;
    }
    const text = cleanType(m.replace(/\$$/, '').replace(/\*$/, ''));
    (/\(/.test(m) ? c.ops : c.attrs).push(text);
  };
  let open = null;
  for (const raw of lines) {
    const l = raw.trim();
    if (!l || l.startsWith('%%')) continue;
    if (open) {
      if (l === '}') open = null;
      else member(open, l);
      continue;
    }
    let m;
    if ((m = l.match(/^direction\s+(TB|TD|BT|LR|RL)/))) dir = m[1];
    else if ((m = l.match(/^class\s+([\w.]+)(?:\["([^"]+)"\])?(?:~([^~]+)~)?(?::::\w+)?\s*(\{)?\s*$/))) {
      const c = get(m[1]);
      if (m[2]) c.name = m[2];
      if (m[3]) c.name += `<${m[3]}>`;
      if (m[4]) open = c;
    } else if ((m = l.match(/^<<(.+)>>\s+([\w.]+)$/))) member(get(m[2]), `<<${m[1]}>>`);
    else if ((m = l.match(CLASS_REL))) {
      let [, a, ma, op, mb, b, label] = m;
      get(a);
      get(b);
      const kinds = {
        '<|--': ['inherit', true], '--|>': ['inherit', false], '<|..': ['realize', true], '..|>': ['realize', false],
        '*--': ['compose', false], '--*': ['compose', true], 'o--': ['aggregate', false], '--o': ['aggregate', true],
        '-->': ['nav', false], '<--': ['nav', true], '..>': ['dep', false], '<..': ['dep', true], '--': ['assoc', false], '..': ['dashed', false],
      };
      const [kind, rev] = kinds[op];
      if (rev) [a, b, ma, mb] = [b, a, mb, ma];
      rels.push({ a, b, kind, label: label ? label.trim() : null, ma: ma ?? null, mb: mb ?? null });
    } else if ((m = l.match(/^([\w.]+)\s*:\s*(.+)$/))) member(get(m[1]), m[2]);
  }
  // Ranking: generalisation and realisation point up (parent above), the rest point down.
  const layoutEdges = rels.map((r) => (r.kind === 'inherit' || r.kind === 'realize' ? { a: r.b, b: r.a } : { a: r.a, b: r.b }));
  const pos = layout([...classes.keys()], layoutEdges, hintsFrom(lines), dir);
  const cls = [...classes.values()].map(
    (c) => `(id: ${str(c.id)}, name: ${str(c.name)}, stereo: ${c.stereo ? str(c.stereo) : 'none'}, attrs: ${typstArray(c.attrs.map(str))}, ops: ${typstArray(c.ops.map(str))}, abstract: ${c.abstract}, pos: (${pos[c.id][0]}, ${pos[c.id][1]}))`,
  );
  const rs = rels.map((r) => {
    const [ax, ay] = pos[r.a];
    const [bx, by] = pos[r.b];
    // An edge that skips a layer in the same column (or row) would run through the box between: bend it.
    const skips = (ax === bx && Math.abs(ay - by) > 1) || (ay === by && Math.abs(ax - bx) > 1);
    return `(from: ${str(r.a)}, to: ${str(r.b)}, kind: ${str(r.kind)}, label: ${r.label ? str(r.label) : 'none'}, mfrom: ${r.ma ? str(r.ma) : 'none'}, mto: ${r.mb ? str(r.mb) : 'none'}, bend: ${skips ? '60deg' : '0deg'})`;
  });
  return `#class-diagram(${typstArray(cls)}, ${typstArray(rs)})`;
}

function stateDiagram(lines) {
  const states = new Map();
  const trans = [];
  let dir = 'TB';
  let starts = 0;
  let ends = 0;
  const get = (id, kind = 'state') => {
    if (!states.has(id)) states.set(id, { id, name: id, kind, body: [] });
    return states.get(id);
  };
  let inNote = false;
  for (const raw of lines) {
    const l = raw.trim();
    if (!l || l.startsWith('%%')) continue;
    if (inNote) {
      if (/^end note/.test(l)) inNote = false;
      continue;
    }
    let m;
    if ((m = l.match(/^direction\s+(TB|TD|BT|LR|RL)/))) dir = m[1];
    else if (/^note\s/.test(l)) inNote = !/:/.test(l);
    else if ((m = l.match(/^state\s+"([^"]+)"\s+as\s+(\w+)/))) get(m[2]).name = m[1];
    else if ((m = l.match(/^state\s+(\w+)\s+<<(choice|fork|join)>>/))) get(m[1], 'choice');
    else if (/^state\s+\w+\s*\{$/.test(l) || l === '}' || l === '--') continue;
    else if ((m = l.match(/^(\[\*\]|\w+)\s*-->\s*(\[\*\]|\w+)\s*(?::\s*(.*))?$/))) {
      let [, a, b, label] = m;
      if (a === '[*]') {
        a = `__start${starts++}`;
        get(a, 'start');
      } else get(a);
      if (b === '[*]') {
        b = `__end${ends++}`;
        get(b, 'end');
      } else get(b);
      // A self-transition is drawn as a UML internal transition inside the state.
      if (a === b) get(a).body.push(label ? label.trim() : '(self)');
      else trans.push({ a, b, label: label ? label.trim() : null });
    } else if ((m = l.match(/^(\w+)\s*:\s*(.+)$/))) get(m[1]).body.push(m[2].trim());
  }
  const pos = layout([...states.keys()], trans.map((t) => ({ a: t.a, b: t.b })), hintsFrom(lines), dir);
  const st = [...states.values()].map(
    (s) => `(id: ${str(s.id)}, name: ${str(s.name)}, kind: ${str(s.kind)}, pos: (${pos[s.id][0]}, ${pos[s.id][1]}), body: ${s.body.length ? str(s.body.join('\n')) : 'none'})`,
  );
  const tr = trans.map((t) => {
    const back = trans.some((u) => u.a === t.b && u.b === t.a);
    const bend = t.a === t.b ? '150deg' : back ? '25deg' : '0deg';
    return `(from: ${str(t.a)}, to: ${str(t.b)}, label: ${t.label ? str(t.label) : 'none'}, bend: ${bend})`;
  });
  return `#state-diagram(${typstArray(st)}, ${typstArray(tr)})`;
}

function participantKind(label) {
  const m = label.match(/^(.*?)\s*(?:\(([^)]+)\)|«([^»]+)»|<<([^>]+)>>)\s*$/) || label.match(/^(?:«([^»]+)»|<<([^>]+)>>)\s*(.*)$/);
  if (!m) return { name: label, kind: 'participant' };
  if (label.trim().startsWith('«') || label.trim().startsWith('<<')) return { name: m[3].trim(), kind: (m[1] || m[2]).trim() };
  return { name: m[1].trim(), kind: (m[2] || m[3] || m[4]).trim() };
}

function sequenceDiagram(lines) {
  const parts = new Map();
  const steps = [];
  const part = (id, label, actor) => {
    if (parts.has(id)) return;
    const { name, kind } = participantKind(label ?? id);
    parts.set(id, { id, name, kind: actor ? 'actor' : kind });
  };
  for (const raw of lines) {
    const l = raw.trim();
    if (!l || l.startsWith('%%')) continue;
    let m;
    if ((m = l.match(/^(participant|actor)\s+([^\s]+)(?:\s+as\s+(.+))?$/))) part(m[2], m[3], m[1] === 'actor');
    else if (/^(autonumber|activate|deactivate|title|box|create|destroy)\b/.test(l)) continue;
    else if ((m = l.match(/^(alt|opt|loop|par|critical|break|rect)\b\s*(.*)$/))) steps.push(`(t: "frag", kind: ${str(m[1])}, cond: ${m[1] === 'rect' ? 'none' : str(m[2])})`);
    else if ((m = l.match(/^(else|and|option)\b\s*(.*)$/))) steps.push(`(t: "else", cond: ${str(m[2])})`);
    else if (l === 'end') steps.push('(t: "end")');
    else if ((m = l.match(/^note\s+(?:over|right of|left of)\s+([^:]+):\s*(.*)$/i))) {
      const ids = m[1].split(',').map((s) => s.trim());
      ids.forEach((id) => part(id));
      steps.push(`(t: "note", over: ${typstArray(ids.map(str))}, text: ${str(m[2].replace(/<br\s*\/?>/gi, '\n'))})`);
    } else if ((m = l.match(/^([^\s\-+<>]+?)\s*(-->>|->>|--\)|-\)|-->|->|--x|-x)\s*[+-]?\s*([^\s:]+?)\s*:\s*(.*)$/))) {
      const [, a, arrow, b, label] = m;
      part(a);
      part(b);
      const style = arrow.startsWith('--') ? 'return' : arrow === '->>' || arrow === '-x' ? 'sync' : 'async';
      steps.push(`(t: "msg", from: ${str(a)}, to: ${str(b)}, label: ${str(label.replace(/<br\s*\/?>/gi, '\n'))}, style: ${str(style)})`);
    }
  }
  const ps = [...parts.values()].map((p) => `(id: ${str(p.id)}, name: ${str(p.name)}, kind: ${str(p.kind)})`);
  return `#sequence-diagram(${typstArray(ps)}, ${typstArray(steps)})`;
}

export function mermaid(src) {
  const lines = src.split('\n');
  const first = lines.findIndex((l) => l.trim() && !l.trim().startsWith('%%') && l.trim() !== '---');
  const kind = lines[first].trim();
  const body = lines.slice(first + 1);
  if (kind.startsWith('classDiagram')) return classDiagram(body);
  if (kind.startsWith('sequenceDiagram')) return sequenceDiagram(body);
  if (kind.startsWith('stateDiagram')) return stateDiagram(body);
  throw new Error(`unsupported Mermaid diagram "${kind}" (use classDiagram, sequenceDiagram or stateDiagram-v2)`);
}

// ---- Blocks -------------------------------------------------------------------

const FIGURE_CAPTION = /^Figure ((?:\d+|[A-E])(?:\.\d+)*):\s*(.+)$/;

function figure(body, caption) {
  const m = caption.match(FIGURE_CAPTION);
  const num = m ? m[1] : '';
  const text = m ? m[2] : caption;
  return `#report-figure(${str(num)}, [${body}], [${inline(text)}])`;
}

function table(rows) {
  const split = (r) => r.trim().replace(/^\|/, '').replace(/\|$/, '').split(/(?<!\\)\|/).map((c) => c.trim().replace(/\\\|/g, '|'));
  const head = split(rows[0]);
  const body = rows.slice(2).map(split);
  const n = head.length;
  const cells = [...head.map((c) => `[*${inline(c)}*]`), ...body.flatMap((r) => Array.from({ length: n }, (_, i) => `[${inline(r[i] ?? '')}]`))];
  // Column widths in proportion to content: the longest cell's length, damped by a square root
  // so one long column cannot squeeze the others below a readable width.
  const plain = (c) => c.replace(/[`*_]|\{\{[^}]*\}\}/g, '').length;
  const weights = Array.from({ length: n }, (_, i) => Math.max(...[head[i] ?? '', ...body.map((r) => r[i] ?? '')].map(plain)));
  const cols = weights.map((w) => `${Math.max(1, Math.round(Math.sqrt(Math.max(w, 4)) * 10) / 10)}fr`);
  return `#report-table((${cols.join(', ')},), ${cells.join(', ')})`;
}

// sectionPath: absolute path of the section file; root: repository root (Typst --root).
export function markdown(src, { sectionDir, root, resolvePath }) {
  const lines = src.replace(/^<!-- report\n[\s\S]*?\n-->\n*/, '').split('\n');
  const out = [];
  let para = [];
  let i = 0;
  const flushPara = () => {
    if (!para.length) return;
    const text = para.join(' ');
    if (/^Table (?:\d+|[A-E])(\.\d+)*:/.test(text)) out.push(`#table-caption[${inline(text)}]`);
    else out.push(inline(text));
    out.push('');
    para = [];
  };
  const nextCaption = (j) => {
    while (j < lines.length && !lines[j].trim()) j++;
    if (j < lines.length && FIGURE_CAPTION.test(lines[j].trim())) {
      let k = j;
      const cap = [];
      while (k < lines.length && lines[k].trim()) cap.push(lines[k++].trim());
      return { caption: cap.join(' '), next: k };
    }
    return null;
  };
  while (i < lines.length) {
    const line = lines[i];
    let m;
    if (/^<!--/.test(line.trim())) {
      flushPara();
      while (i < lines.length && !lines[i].includes('-->')) i++;
      i++;
      continue;
    }
    if ((m = line.match(/^```\s*(\w*)/))) {
      flushPara();
      const lang = m[1];
      const buf = [];
      i++;
      while (i < lines.length && !/^```\s*$/.test(lines[i])) buf.push(lines[i++]);
      i++;
      if (lang === 'mermaid') {
        const cap = nextCaption(i);
        const body = mermaid(buf.join('\n'));
        if (cap) {
          out.push(figure(body, cap.caption));
          i = cap.next;
        } else out.push(`#figure-frame[${body}]`);
      } else out.push(`#raw(block: true, lang: ${str(lang || 'text')}, ${str(buf.join('\n'))})`);
      out.push('');
      continue;
    }
    if ((m = line.match(/^(#{1,4}) (.+?)\s*(\{[^}]*\})?\s*$/))) {
      flushPara();
      const level = m[1].length;
      const attrs = m[3] || '';
      const title = m[2].replace(/^\d+(?:\.\d+)*\s+/, '');
      const numbered = !/\.(unnumbered|appendix)/.test(attrs) && /^\d/.test(m[2]);
      out.push(`#heading(level: ${level}, ${numbered && level <= 3 ? '' : 'numbering: none, '}outlined: ${level <= 3 || /\.appendix/.test(attrs)})[${inline(title)}]`);
      out.push('');
      i++;
      continue;
    }
    if (/^\|/.test(line.trim()) && i + 1 < lines.length && /^\|?\s*:?-{3,}/.test(lines[i + 1].trim())) {
      flushPara();
      const rows = [];
      while (i < lines.length && /^\|/.test(lines[i].trim())) rows.push(lines[i++]);
      out.push(table(rows));
      out.push('');
      continue;
    }
    if ((m = line.match(/^!\[([^\]]*)\]\(([^)\s]+)[^)]*\)\s*(\{\.landscape\})?\s*$/))) {
      flushPara();
      const path = resolvePath(sectionDir, m[2]);
      // {.landscape}: a wide drawing gets a landscape page of its own, about 1.6 times larger.
      if (m[3]) out.push(figure(`#image(${str(path)}, width: 100%, height: 13cm, fit: "contain")`, m[1]).replace('#report-figure(', '#landscape-figure('));
      else out.push(figure(`#image(${str(path)}, width: 100%)`, m[1]));
      out.push('');
      i++;
      continue;
    }
    if ((m = line.match(/^(\s*)([-*]|\d+\.)\s+(.*)$/))) {
      flushPara();
      const items = [];
      while (i < lines.length) {
        const lm = lines[i].match(/^(\s*)([-*]|\d+\.)\s+(.*)$/);
        if (lm) {
          items.push({ indent: Math.floor(lm[1].length / 2), ordered: /\d/.test(lm[2]), text: lm[3] });
          i++;
        } else if (lines[i].trim() && /^\s{2,}/.test(lines[i]) && items.length) {
          items[items.length - 1].text += ` ${lines[i].trim()}`;
          i++;
        } else break;
      }
      for (const it of items) out.push(`${'  '.repeat(it.indent)}${it.ordered ? '+' : '-'} ${inline(it.text)}`);
      out.push('');
      continue;
    }
    if (!line.trim()) {
      flushPara();
      i++;
      continue;
    }
    para.push(line.trim());
    i++;
  }
  flushPara();
  return out.join('\n');
}

export function frontMatter(src) {
  const m = src.match(/^---\n([\s\S]*?)\n---/m);
  const meta = { title: '', subtitle: '', author: [], date: '' };
  if (!m) return meta;
  let key = null;
  for (const line of m[1].split('\n')) {
    let k;
    if ((k = line.match(/^(\w[\w-]*):\s*(.*)$/))) {
      key = k[1];
      const v = k[2].replace(/^"(.*)"$/, '$1');
      if (v) meta[key] = v;
      else meta[key] = [];
    } else if ((k = line.match(/^\s+-\s+(.*)$/)) && key) meta[key].push(k[1].replace(/^"(.*)"$/, '$1'));
  }
  return meta;
}

export { MARKER };
