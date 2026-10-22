// Escaping, draft markers and word counts shared by slides, notes and the speaker script.
const { deck } = require('./context');

const MK = { FILL: 'mk-fill', VERIFY: 'mk-verify', CONFIRM: 'mk-confirm' };
function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
// {{FILL:x}} -> chip. Text outside markers is HTML-escaped. The FILL chip label comes from deck.config.js (fillLabel).
function md(s) {
  if (s == null) return '';
  const parts = String(s).split(/(\{\{(?:FILL|VERIFY|CONFIRM):[^}]*\}\})/);
  return parts.map(p => {
    const m = p.match(/^\{\{(FILL|VERIFY|CONFIRM):([^}]*)\}\}$/);
    if (m) return `<span class="mk ${MK[m[1]]}">[${m[1] === 'FILL' ? deck().cfg.fillLabel : m[1]}${m[2] ? ': ' + esc(m[2]) : ''}]</span>`;
    return esc(p);
  }).join('');
}
function mk(kind, text = '') { return md(`{{${kind}:${text}}}`); }
// A fragment step in `say`: [click], or numbered as [click 2/3] (step 2 of the slide's 3).
const CLICK = /\[click(?: \d+\/\d+)?\]/g;
const clicks = html => String(html).replace(CLICK, '<b class="clk">$&</b>');
function words(s) { return String(s).replace(/\{\{(?:FILL|VERIFY|CONFIRM):([^}]*)\}\}/g, 'x').replace(CLICK, '').split(/\s+/).filter(Boolean).length; }

module.exports = { esc, md, mk, words, clicks };
