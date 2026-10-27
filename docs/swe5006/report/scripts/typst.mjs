// Markdown (the subset AGENTS.md allows) and Mermaid (classDiagram, sequenceDiagram,
// stateDiagram-v2) to Typst. Used by build.mjs and figure.mjs; no dependencies.

const MARKER = /\{\{(FILL|VERIFY|CONFIRM)(?::\s*([^}]*))?\}\}/;

// CommonMark code fences: a run of three or more backticks or tildes opens a fence, and only
// a run of the same character, at least as long and with nothing after it, closes it.
export function fenceOpen(line) {
  const m = line.match(/^ {0,3}(`{3,}|~{3,})\s*([^`\s]*)/);
  return m ? { ch: m[1][0], len: m[1].length, lang: m[2] } : null;
}

export function fenceCloses(line, fence) {
  const m = line.match(/^ {0,3}(`{3,}|~{3,})\s*$/);
  return Boolean(m && m[1][0] === fence.ch && m[1].length >= fence.len);
}

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

// Does the straight segment p-q cross the box around grid cell c? Boxes take about 0.8 of a
// column's width and 0.7 of a row's height (Liang-Barsky clipping).
function crosses(p, q, c, hx = 0.42, hy = 0.36) {
  let t0 = 0.04;
  let t1 = 0.96;
  const d = [q[0] - p[0], q[1] - p[1]];
  const sides = [
    [-d[0], p[0] - (c[0] - hx)],
    [d[0], c[0] + hx - p[0]],
    [-d[1], p[1] - (c[1] - hy)],
    [d[1], c[1] + hy - p[1]],
  ];
  for (const [pp, qq] of sides) {
    if (pp === 0) {
      if (qq < 0) return false;
    } else {
      const r = qq / pp;
      if (pp < 0) t0 = Math.max(t0, r);
      else t1 = Math.min(t1, r);
      if (t0 > t1) return false;
    }
  }
  return true;
}

// Route each edge: straight when nothing is in the way; an arc bulging away from the boxes it
// would cross otherwise; repeated edges between the same two classes fan out.
function routes(rels, pos) {
  const seen = new Map();
  return rels.map((r) => {
    const a = pos[r.a];
    const b = pos[r.b];
    const pair = [r.a, r.b].sort().join('\u0000');
    const n = seen.get(pair) ?? 0;
    seen.set(pair, n + 1);
    if (r.a === r.b) return 0;
    const others = Object.entries(pos).filter(([id]) => id !== r.a && id !== r.b).map(([, c]) => c);
    const blockers = others.filter((c) => crosses(a, b, c));
    if (!blockers.length) return n === 0 ? 0 : n % 2 ? 28 : -28;
    // Side of the blockers, walking from a to b in screen coordinates (y down): positive is to
    // the right. Fletcher's positive bend is clockwise, which bulges the arc to the left.
    const side = blockers.reduce((s, c) => s + Math.sign((b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])), 0);
    const sign = side >= 0 ? 1 : -1;
    // The smallest bend whose arc clears every box, sampled as chords of the arc.
    const len = Math.hypot(b[0] - a[0], b[1] - a[1]);
    const left = [(b[1] - a[1]) / len, -(b[0] - a[0]) / len];
    for (const deg of [25, 35, 45, 60]) {
      const h = (len / 2) * Math.tan((deg * Math.PI) / 360);
      const pt = (t) => [a[0] + t * (b[0] - a[0]) + 4 * h * t * (1 - t) * left[0] * sign, a[1] + t * (b[1] - a[1]) + 4 * h * t * (1 - t) * left[1] * sign];
      const ts = [0, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1];
      const clear = ts.slice(1).every((t, i) => others.every((c) => !crosses(pt(ts[i]), pt(t), c, 0.42, 0.36)));
      if (clear) return sign * deg;
    }
    return sign * 50;
  });
}

function classDiagram(lines, frame) {
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
      rels.push({ a, b, kind, label: label ? guillemets(label) : null, ma: ma ?? null, mb: mb ?? null });
    } else if ((m = l.match(/^([\w.]+)\s*:\s*(.+)$/))) member(get(m[1]), m[2]);
  }
  // Ranking: generalisation and realisation point up (parent above), the rest point down.
  const layoutEdges = rels.map((r) => (r.kind === 'inherit' || r.kind === 'realize' ? { a: r.b, b: r.a } : { a: r.a, b: r.b }));
  const pos = layout([...classes.keys()], layoutEdges, hintsFrom(lines), dir);
  const cls = [...classes.values()].map(
    (c) => `(id: ${str(c.id)}, name: ${str(c.name)}, stereo: ${c.stereo ? str(c.stereo) : 'none'}, attrs: ${typstArray(c.attrs.map(str))}, ops: ${typstArray(c.ops.map(str))}, abstract: ${c.abstract}, pos: (${pos[c.id][0]}, ${pos[c.id][1]}))`,
  );
  const bends = routes(rels, pos);
  const rs = rels.map(
    (r, i) =>
      `(from: ${str(r.a)}, to: ${str(r.b)}, kind: ${str(r.kind)}, label: ${r.label ? str(r.label) : 'none'}, mfrom: ${r.ma ? str(r.ma) : 'none'}, mto: ${r.mb ? str(r.mb) : 'none'}, bend: ${bends[i]}deg, vert: ${pos[r.a][0] === pos[r.b][0] && bends[i] === 0})`,
  );
  // The least gap between columns (rows) that leaves room for the labels and multiplicities of
  // the edges joining neighbours in one row (column): about 4.3 pt per character at 8.5 pt,
  // a label wrapping at 40 mm, the end marks, and air on both sides.
  const chars = (s) => (s ? [...s].length : 0);
  let gx = 0;
  let gy = 0;
  rels.forEach((r) => {
    const [ax, ay] = pos[r.a];
    const [bx, by] = pos[r.b];
    const marks = ['compose', 'aggregate'].includes(r.kind) ? 13 : ['inherit', 'realize'].includes(r.kind) ? 11 : 5;
    const ends = (chars(r.ma) + chars(r.mb)) * 4.3 + (r.ma || r.mb ? 14 : 0) + marks;
    if (ay === by && Math.abs(ax - bx) === 1) gx = Math.max(gx, Math.min(chars(r.label) * 4.3, 113) + 12, ends);
    if (ax === bx && Math.abs(ay - by) === 1 && (r.label || r.ma || r.mb)) gy = Math.max(gy, (r.label ? 14 : 0) + (r.ma ? 15 : 0) + (r.mb ? 15 : 0) + marks + 6);
  });
  const minGap = `(${Math.min(Math.round(gx), 100)}pt, ${Math.min(Math.round(gy), 60)}pt)`;
  return `#class-diagram(${typstArray(cls)}, ${typstArray(rs)}, frame: ${str(frame)}, min-gap: ${minGap})`;
}

function stateDiagram(lines, frame) {
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
    const bend = t.a === t.b ? '150deg' : back ? '35deg' : '0deg';
    return `(from: ${str(t.a)}, to: ${str(t.b)}, label: ${t.label ? str(t.label) : 'none'}, bend: ${bend})`;
  });
  return `#state-diagram(${typstArray(st)}, ${typstArray(tr)}, frame: ${str(frame)})`;
}

// Stereotypes and keywords take guillemets: "<<use>>" becomes «use», and a dependency label that
// is a UML standard keyword on its own ("use", "trace", "throws", ...) is wrapped in them.
const KEYWORDS = /^(use|trace|throws|create|instantiate|call|import|access|refine|derive|send|realize|permit|include|extend|bind)$/;
function guillemets(label) {
  const s = label.replace(/<<\s*([^>]+?)\s*>>/g, '«$1»').trim();
  return KEYWORDS.test(s) ? `«${s}»` : s;
}

function participantKind(label) {
  const m = label.match(/^(.*?)\s*(?:\(([^)]+)\)|«([^»]+)»|<<([^>]+)>>)\s*$/) || label.match(/^(?:«([^»]+)»|<<([^>]+)>>)\s*(.*)$/);
  if (!m) return { name: label, kind: 'participant' };
  if (label.trim().startsWith('«') || label.trim().startsWith('<<')) return { name: m[3].trim(), kind: (m[1] || m[2]).trim() };
  return { name: m[1].trim(), kind: (m[2] || m[3] || m[4]).trim() };
}

// Activation bars, when the Mermaid has no activate/deactivate (or +/-) of its own: a call
// (sync or async) opens an activation on its receiver, closed by the receiver's reply to the
// caller. Control returns to a sender when it sends again, so a callee that never replied is
// closed then; a callee still open at the end of the interaction closes after the last message.
// An alt/par branch starts from the activations open when the fragment began. The first
// sender, if it is not an actor, is active for the whole interaction.
function inferActivations(steps, parts) {
  const out = [];
  const stack = [];
  const isActor = (id) => parts.get(id)?.kind === 'actor';
  const closeTo = (n) => {
    while (stack.length > n) out.push({ t: 'deact', id: stack.pop().callee, implicit: true });
  };
  const calleeAt = (id) => stack.findLastIndex((e) => e.callee === id);
  const snaps = [];
  const frags = [];
  let first = true;
  // Participants that send or receive a message from step i on, up to the end of the current
  // branch (branch: stop at the next else/end of this fragment) or of the enclosing fragment.
  const usedAhead = (i, branch) => {
    const used = new Set();
    let depth = 0;
    for (let j = i + 1; j < steps.length; j++) {
      const t = steps[j];
      if (t.t === 'frag') depth++;
      else if (t.t === 'end' && depth-- === 0) break;
      else if (t.t === 'else' && depth === 0 && branch) break;
      else if (t.t === 'msg') used.add(t.from).add(t.to);
    }
    return used;
  };
  // Bring back the activations of snap above the first k entries, but only up to the innermost
  // one whose participant still takes part in what follows, so no bar is left as a stray stub.
  const reopen = (snap, k, used) => {
    const rest = snap.slice(k);
    const last = rest.findLastIndex((e) => used.has(e.callee));
    for (const e of rest.slice(0, last + 1)) {
      stack.push(e);
      out.push({ t: 'act', id: e.callee, here: true });
    }
  };
  for (const [i, s] of steps.entries()) {
    if (s.t === 'msg') {
      const { from: a, to: b } = s;
      if (first && s.style !== 'return' && a !== b && !isActor(a)) {
        s.pre = true;
        stack.push({ callee: a, caller: null });
      }
      first = false;
      // An asynchronous message back to the caller (a callback such as "paymentConfirmed")
      // ends the callee's activation like a reply does.
      const back = s.style === 'async' ? stack.findLastIndex((e) => e.callee === a && e.caller === b) : -1;
      if (s.style === 'return' || back >= 0) {
        // A reply closes the activation it answers; a dashed message to anyone else (Mermaid
        // sources also draw «create» that way) is an ordinary message from its sender.
        const i = stack.findLastIndex((e) => e.callee === a && e.caller === b);
        if (i >= 0) {
          closeTo(i + 1);
          stack.pop();
          s.deact = true;
        } else {
          const ia = calleeAt(a);
          if (ia >= 0) closeTo(ia + 1);
        }
        out.push(s);
        continue;
      }
      const ia = calleeAt(a);
      if (ia >= 0) closeTo(ia + 1);
      else if (s.style === 'sync' && a !== b) closeTo(0);
      if (a !== b && !isActor(b) && !s.destroy) {
        s.act = true;
        stack.push({ callee: b, caller: a });
      }
      out.push(s);
    } else if (s.t === 'frag') {
      snaps.push(stack.slice());
      out.push(s);
    } else if (s.t === 'else') {
      const snap = snaps[snaps.length - 1];
      let k = 0;
      while (k < stack.length && k < snap.length && stack[k] === snap[k]) k++;
      closeTo(k);
      out.push(s);
      reopen(snap, k, usedAhead(i, true));
    } else if (s.t === 'end') {
      // After a break the interaction goes on from where the fragment began.
      const snap = snaps.pop();
      const f = frags.pop();
      out.push(s);
      if (f?.kind === 'break') {
        let k = 0;
        while (k < stack.length && k < snap.length && stack[k] === snap[k]) k++;
        closeTo(k);
        reopen(snap, k, usedAhead(i, false));
      }
    } else out.push(s);
    if (s.t === 'frag') frags.push(s);
  }
  closeTo(0);
  return out;
}

function sequenceDiagram(lines, frame) {
  const parts = new Map();
  let steps = [];
  let explicit = false;
  const destroying = new Set();
  const part = (id, label, actor, created = false) => {
    if (parts.has(id)) return;
    const { name, kind } = participantKind(label ?? id);
    parts.set(id, { id, name, kind: actor ? 'actor' : kind, created, made: false });
  };
  for (const raw of lines) {
    const l = raw.trim();
    if (!l || l.startsWith('%%')) continue;
    let m;
    if ((m = l.match(/^(create\s+)?(participant|actor)\s+([^\s]+)(?:\s+as\s+(.+))?$/))) part(m[3], m[4], m[2] === 'actor', Boolean(m[1]));
    else if ((m = l.match(/^(activate|deactivate)\s+(\S+)$/))) {
      explicit = true;
      part(m[2]);
      steps.push(m[1] === 'activate' ? { t: 'act', id: m[2] } : { t: 'deact', id: m[2] });
    } else if ((m = l.match(/^destroy\s+(\S+)$/))) destroying.add(m[1]);
    else if (/^(autonumber|title)\b/.test(l)) continue;
    else if ((m = l.match(/^(alt|opt|loop|par|critical|break|rect)\b\s*(.*)$/))) steps.push({ t: 'frag', kind: m[1], cond: m[1] === 'rect' ? null : m[2] });
    else if ((m = l.match(/^(else|and|option)\b\s*(.*)$/))) steps.push({ t: 'else', cond: m[2] });
    else if (l === 'end') steps.push({ t: 'end' });
    else if ((m = l.match(/^note\s+(?:over|right of|left of)\s+([^:]+):\s*(.*)$/i))) {
      const ids = m[1].split(',').map((s) => s.trim());
      ids.forEach((id) => part(id));
      steps.push({ t: 'note', over: ids, text: m[2].replace(/<br\s*\/?>/gi, '\n') });
    } else if ((m = l.match(/^([^\s\-+<>]+?)\s*(-->>|->>|--\)|-\)|-->|->|--x|-x)\s*([+-]?)\s*([^\s:]+?)\s*:\s*(.*)$/))) {
      const [, a, arrow, sign, b, label] = m;
      part(a);
      part(b);
      const style = arrow.startsWith('--') ? 'return' : arrow === '->>' || arrow === '-x' ? 'sync' : 'async';
      const s = { t: 'msg', from: a, to: b, label: guillemets(label.replace(/<br\s*\/?>/gi, '\n')), style };
      if (sign) explicit = true;
      if (sign === '+') s.act = true;
      if (sign === '-') s.deact = true;
      const pb = parts.get(b);
      if (pb.created && !pb.made && a !== b) {
        s.create = true;
        pb.made = true;
      }
      for (const d of [b, a]) {
        if (destroying.has(d)) {
          s.destroy = d;
          destroying.delete(d);
          break;
        }
      }
      steps.push(s);
    }
  }
  if (!explicit) steps = inferActivations(steps, parts);
  // Each combined fragment spans the lifelines its messages and notes touch, a message to
  // itself with the lifeline to the right (where its label goes), nested ones inset by depth.
  const order = [...parts.keys()];
  const open = [];
  for (const s of steps) {
    if (s.t === 'frag') {
      s.depth = open.length;
      open.push(s);
    } else if (s.t === 'end') open.pop();
    else if (s.t === 'msg' || s.t === 'note') {
      const ids = s.t === 'msg' ? [s.from, s.to] : s.over;
      const xs = ids.map((id) => order.indexOf(id));
      if (s.t === 'msg' && s.from === s.to) xs.push(Math.min(xs[0] + 1, order.length - 1));
      for (const f of open) {
        f.lo = Math.min(f.lo ?? Infinity, ...xs);
        f.hi = Math.max(f.hi ?? -Infinity, ...xs);
      }
    }
  }
  const val = (v) => (v === null || v === undefined ? 'none' : typeof v === 'string' ? str(v) : typeof v === 'boolean' || typeof v === 'number' ? String(v) : typstArray(v.map(val)));
  const keep = ([k, v]) => v !== undefined && (!['lo', 'hi'].includes(k) || Number.isFinite(v));
  const dict = (o) => `(${Object.entries(o).filter(keep).map(([k, v]) => `${k}: ${val(v)}`).join(', ')})`;
  const ps = [...parts.values()].map((p) => `(id: ${str(p.id)}, name: ${str(p.name)}, kind: ${str(p.kind)}, created: ${p.created && p.made})`);
  return `#sequence-diagram(${typstArray(ps)}, ${typstArray(steps.map(dict))}, frame: ${str(frame)})`;
}

// The frame heading names the diagram after its caption's subject: the first sentence, without
// the "UML design class diagram of" lead-in and without long asides in brackets.
export function frameName(caption) {
  if (!caption) return '';
  let s = caption.replace(/^Figure [\dA-E.]+:\s*/, '').replace(/`/g, '');
  s = s.split(/\.\s/)[0].replace(/\.$/, '');
  s = s.replace(/\s*\(([^()]*)\)/g, (all, inner) => (inner.length <= 12 && !/UML/.test(inner) ? all : ''));
  s = s.replace(/^UML\s+/i, '');
  s = s.replace(/^(?:(?:analysis|design)\s+)?(?:UML\s+)?(?:class diagram|sequence diagram|sequence|statechart|state machine)\b(?:,\s*|\s+of\s+(?:the\s+)?|\s+for\s+)?/i, '');
  s = s.replace(/,?\s*(?:UML\s+)?(?:class|sequence|state)\s+(?:diagram|machine)\b.*$/i, '');
  s = s.replace(/^before\b.*?\band after (?:the )?(.+)$/i, '$1, before and after');
  s = s.replace(/^the\s+/i, '').trim();
  if (s.length > 64) s = `${s.slice(0, 64).replace(/[\s,;:]+\S*$/, '')} …`;
  return s ? s[0].toUpperCase() + s.slice(1) : '';
}

export function mermaid(src, caption = '') {
  const lines = src.split('\n');
  const first = lines.findIndex((l) => l.trim() && !l.trim().startsWith('%%') && l.trim() !== '---');
  const kind = lines[first].trim();
  const body = lines.slice(first + 1);
  const own = lines.map((l) => l.trim().match(/^%%\s*frame:\s*(.+)$/)).find(Boolean);
  const frame = own ? own[1].trim() : frameName(caption);
  if (kind.startsWith('classDiagram')) return classDiagram(body, frame);
  if (kind.startsWith('sequenceDiagram')) return sequenceDiagram(body, frame);
  if (kind.startsWith('stateDiagram')) return stateDiagram(body, frame);
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
  // A column of short values (a sprint number, "#", a status) is sized to its content, so its bold
  // header word never runs into the next column.
  const cols = weights.map((w) => (w <= 10 ? 'auto' : `${Math.max(1, Math.round(Math.sqrt(Math.max(w, 4)) * 10) / 10)}fr`));
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
    // A paragraph of bold text alone is a label for what follows (see lead-label in template.typ).
    else if (/^\*\*[^*]+\*\*$/.test(text)) out.push(`#lead-label[${inline(text)}]`);
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
    const open = fenceOpen(line);
    if (open) {
      flushPara();
      const lang = open.lang;
      const buf = [];
      i++;
      while (i < lines.length && !fenceCloses(lines[i], open)) buf.push(lines[i++]);
      i++;
      if (lang === 'mermaid') {
        const cap = nextCaption(i);
        const body = mermaid(buf.join('\n'), cap ? cap.caption : '');
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
    if ((m = line.match(/^!\[([^\]]*)\]\(([^)\s]+)[^)]*\)\s*(\{\.(landscape|logo)\})?\s*$/))) {
      flushPara();
      const path = resolvePath(sectionDir, m[2]);
      // {.logo}: a brand mark, 7 cm wide and flush with the left margin of the text, with no frame, caption or number.
      if (m[4] === 'logo') out.push(`#align(left, image(${str(path)}, width: 7cm, alt: ${str(m[1])}))`);
      // {.landscape}: a wide drawing gets a landscape page of its own, about 1.6 times larger.
      else if (m[3]) out.push(figure(`#image(${str(path)}, width: 100%, height: 13cm, fit: "contain")`, m[1]).replace('#report-figure(', '#landscape-figure('));
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
  const meta = { title: '', subtitle: '' };
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
