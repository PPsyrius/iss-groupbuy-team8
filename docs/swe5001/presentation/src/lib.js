// Shared helpers: fonts, icons, markers.
const fs = require('fs');
const path = require('path');
const NM = path.join(__dirname, '..', 'node_modules');

function font(family, file, weight, style = 'normal', range = '') {
  const b64 = fs.readFileSync(path.join(NM, file)).toString('base64');
  return `@font-face{font-family:'${family}';font-style:${style};font-weight:${weight};font-display:block;src:url(data:font/woff2;base64,${b64}) format('woff2')${range ? ';unicode-range:' + range : ''}}`;
}
// BlocBuy type: Inter 400/500/600/700 only. JetBrains Mono is kept for code identifiers and draft markers; it borrows arrows/maths glyphs from Inter.
const SYM = 'U+00D7,U+2190-21FF,U+2212,U+2248,U+2260-2265,U+25CF';
function fontCSS() {
  return [
    font('Inter', 'inter-ui/web/Inter-Regular.woff2', 400),
    font('Inter', 'inter-ui/web/Inter-Medium.woff2', 500),
    font('Inter', 'inter-ui/web/Inter-SemiBold.woff2', 600),
    font('Inter', 'inter-ui/web/Inter-Bold.woff2', 700),
    font('Inter', 'inter-ui/web/Inter-Black.woff2', 900),
    font('Inter', 'inter-ui/web/Inter-Italic.woff2', 400, 'italic'),
    font('Inter', 'inter-ui/web/Inter-MediumItalic.woff2', 500, 'italic'),
    font('JetBrains Mono', 'inter-ui/web/Inter-Regular.woff2', 400, 'normal', SYM),
    font('JetBrains Mono', 'inter-ui/web/Inter-Bold.woff2', 700, 'normal', SYM),
    font('JetBrains Mono', '@fontsource/jetbrains-mono/files/jetbrains-mono-latin-400-normal.woff2', 400),
    font('JetBrains Mono', '@fontsource/jetbrains-mono/files/jetbrains-mono-latin-700-normal.woff2', 700),
  ].join('\n');
}

const iconCache = {};
function icon(name, cls = 'ic') {
  if (!iconCache[name]) {
    let f = null;
    for (const d of ['solid', 'regular', 'brands']) {
      const p = path.join(NM, '@fortawesome/fontawesome-free/svgs', d, name + '.svg');
      if (fs.existsSync(p)) { f = p; break; }
    }
    if (!f) throw new Error('icon ' + name);
    iconCache[name] = fs.readFileSync(f, 'utf8').replace(/<!--.*?-->/gs, '').replace('<svg ', '<svg fill="currentColor" ');
  }
  return iconCache[name].replace('<svg ', `<svg class="${cls}" `);
}

const MK = { FILL: 'mk-fill', VERIFY: 'mk-verify', CONFIRM: 'mk-confirm' };
function esc(s) { return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;'); }
// {{FILL:x}} -> chip. Text outside markers is HTML-escaped.
function md(s) {
  if (s == null) return '';
  const parts = String(s).split(/(\{\{(?:FILL|VERIFY|CONFIRM):[^}]*\}\})/);
  return parts.map(p => {
    const m = p.match(/^\{\{(FILL|VERIFY|CONFIRM):([^}]*)\}\}$/);
    if (m) return `<span class="mk ${MK[m[1]]}">[${m[1] === 'FILL' ? 'FILL 19 Oct' : m[1]}${m[2] ? ': ' + esc(m[2]) : ''}]</span>`;
    return esc(p);
  }).join('');
}
function mk(kind, text = '') { return md(`{{${kind}:${text}}}`); }
function words(s) { return String(s).replace(/\{\{(?:FILL|VERIFY|CONFIRM):([^}]*)\}\}/g, 'x').replace(/\[click\]/g, '').split(/\s+/).filter(Boolean).length; }
function svg(name) {
  return fs.readFileSync(path.join(__dirname, 'diagrams', name + '.svg'), 'utf8').replace(/<\?xml[^>]*>/, '');
}
module.exports = { fontCSS, icon, md, mk, esc, words, svg };
