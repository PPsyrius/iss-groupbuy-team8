// Geometry and diagram helpers for slides that reproduce the v3 site deck (1920 x 1080) one to one.
// site(l, t, w, h) takes a box in site pixels, scales it by 2/3 and places it relative to .body (left 58, top 128).
// dg(name) inlines a site diagram (src/diagrams/<name>.svg, class "dg") sized to fill its wrapper.
const { svg } = require('../../../presentation-kit/lib');

const r2 = v => Math.round(v * 100) / 100;
const px = (l, t, w, h) => ({ l: r2(l * 2 / 3 - 58), t: r2(t * 2 / 3 - 128), w: r2(w * 2 / 3), h: r2(h * 2 / 3) });
const site = (l, t, w, h) => { const b = px(l, t, w, h); return `position:absolute;left:${b.l}px;top:${b.t}px;width:${b.w}px;height:${b.h}px`; };
const DG = 'width:100%;height:100%;display:block';
const dg = name => svg(name).replace('<svg class="dg"', `<svg class="dg" style="${DG}"`);

module.exports = { r2, px, site, dg, DG };
