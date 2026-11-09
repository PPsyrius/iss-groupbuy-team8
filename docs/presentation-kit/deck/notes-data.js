// Data for the speaker companion (the deck opened with ?notes, see notes-runtime.js): one render-ready entry per slide,
// in deck order, built at deck build time so the runtime only places text. Every HTML field comes from md() or chips(),
// which escape the content text. Pure apart from md() reading the deck's fillLabel.
const { md, clicks } = require('../lib/text');
const { hms, toS, slideClock } = require('../lib/schedule');

const MARKER = /\{\{(?:FILL|VERIFY|CONFIRM):[^}]*\}\}/g;
// Marker chips inside a field that is already HTML (demo steps): only the markers are rendered.
const chips = html => String(html ?? '').replace(MARKER, m => md(m));
// A hand-over in parentheses is a stage direction; anything else is a line to say, so it is quoted (as in the script).
const handover = h => (h ? (/^\(/.test(h) ? md(h) : `"${md(h)}"`) : '');

function demoHtml(steps) {
  if (!steps || !steps.length) return '';
  return '<h3>Live demo steps</h3>' + steps.map(d => `<div class="dstep"><b>${chips(d.time)} · Step ${md(d.n)} · ${md(d.t)}</b>`
    + (d.proves ? ` <i>${chips(d.proves)}</i>` : '')
    + `<div>${(d.op || []).map(chips).join(' · ')}</div><p>${md(d.say)}</p>`
    + `<small>Fallback: ${d.fb ? chips(d.fb) : '—'}</small></div>`).join('');
}

/**
 * @param C        the deck's src/content.js exports (SEGMENTS, SLIDES, BACKUPS?, DEMOS?)
 * @param backups  the deck's backup slides as built ([{ id, title, speaker? }]), in deck order
 * @returns {{ total: number, talkStart: string, slides: object[] }}
 */
function buildNotesData(C, backups = []) {
  const clock = slideClock(C.SEGMENTS, C.SLIDES);
  const talkStart = toS(C.SEGMENTS[0].start);
  const total = C.SLIDES.length;
  const segs = new Map(C.SEGMENTS.map(g => [g.n, g]));

  const main = C.SLIDES.map((s, k) => {
    const [a, b] = clock[s.n];
    const nx = C.SLIDES[k + 1];
    return {
      id: String(s.n),
      label: `Slide ${s.n} / ${total}`,
      title: s.title,
      meta: `${s.seg} · ${segs.get(s.seg).name} · item ${s.item} · ${s.speaker}`,
      slot: b == null ? `${hms(a)} · Q&A` : `${hms(a)}–${hms(b)} · ${s.slot} s`,
      secs: s.slot || 0,
      due: a - talkStart,
      script: clicks(md(s.say)),
      cues: (s.cues || []).map(md),
      handover: handover(s.handover),
      demo: demoHtml(C.DEMOS && C.DEMOS[s.n]),
      next: nx ? `Next · ${nx.n} — ${nx.title} (${nx.speaker})` : 'End of the talk; backups follow',
    };
  });

  // BACKUPS rows: [id, title, question it answers, owner?, what to say?]
  const rows = new Map((C.BACKUPS || []).map(r => [String(r[0]), r]));
  const back = backups.map(b => {
    const r = rows.get(String(b.id));
    return {
      id: String(b.id),
      label: `Backup ${b.id}`,
      title: b.title,
      meta: b.speaker ? `Q&A only · ${b.speaker}` : 'Q&A only',
      slot: '',
      secs: 0,
      due: null,
      script: r && r[4] ? md(r[4]) : '',
      cues: r && r[2] ? [md(`Answers: ${r[2]}`)] : [],
      handover: `Back to slide ${total}: press End, or B for the index.`,
      demo: '',
      next: '',
    };
  });

  return { total, talkStart: hms(talkStart), slides: main.concat(back) };
}

// JSON safe to inline in <script type="application/json">: no "</script>", "<!--" or line separators can end it early.
const jsonForScript = v => JSON.stringify(v).replace(/[<>&\u2028\u2029]/g, c => '\\u' + c.charCodeAt(0).toString(16).padStart(4, '0'));

module.exports = { buildNotesData, jsonForScript, chips };
