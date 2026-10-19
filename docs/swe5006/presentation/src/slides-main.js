const { icon, md, mk, svg, lockup } = require('../../../presentation-kit/lib');
// Accent fills never carry text (design-screens.md §2.1): accent -> accent-soft + accent-soft-foreground; grey -> default + foreground.
// Layout reference: docs/swe5001/presentation/src/slides-main.js has a worked example of every card, chip and diagram pattern.

const todo = n => () => `<div style="margin-top:40px">${mk('FILL', 'slide ' + n + ' layout')}</div>`;

// ---------- slide 1 ----------
const s1 = () => `
<div style="position:absolute;left:80px;top:96px;font:600 16px Inter;letter-spacing:.1em;color:#FFB01F">SWE5006 PRACTICE PROJECT · TEAM 8</div>
${lockup()}
<div style="position:absolute;left:80px;top:300px;font:500 34px Inter;color:#fff">A scalable community group-buying platform</div>
<div style="position:absolute;left:80px;top:516px;font:500 19px Inter;color:#fff">Lim Jiechao · Liu Jin · Panpakorn Siripanich · Wang Ziang · Yu Qianshuo</div>
<div style="position:absolute;left:80px;top:548px;font:400 18px Inter;color:#B5ADA1">Graduate Certificate in Designing Modern Software Systems · 26 October 2026</div>`;

// ---------- slide 22 ----------
const s22 = () => `
<div style="position:absolute;left:76px;top:96px;font:700 120px/1 Inter;color:#fff">Thank you</div>
<div style="position:absolute;left:80px;top:236px;font:500 32px Inter;color:#fff">Questions?</div>
<div style="position:absolute;left:80px;top:604px;font:400 17px 'JetBrains Mono',monospace;color:#E6DFD3;display:flex;gap:10px;align-items:center"><span style="font-size:20px">${icon('github')}</span> github.com/PPsyrius/iss-groupbuy-team8</div>`;

const S = { 1: s1, 22: s22 };
for (let n = 2; n <= 21; n++) S[n] = todo(n);
module.exports = S;
