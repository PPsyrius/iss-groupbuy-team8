// Geometry and diagram helpers for slides that reproduce the v3 site deck (1920 x 1080) one to one.
// site(l, t, w, h) takes a box in site pixels, scales it by 2/3 and places it relative to .body (left 58, top 128).
// fill(markup, viewBox) makes a kit diagram (svg(name)) fill its wrapper: the root's fixed width and height give way to
// 100% of the box, and viewBox (in the drawing's own units) is the part of the drawing v3 showed in that box, with the
// default xMidYMid meet. On 9, 13 and 16 v3 drew the kit SVG at its natural size and the box crops it slightly; on the
// backups it was scaled to fit with a 4 px margin (8 site px), which appears as a negative viewBox origin.
const r2 = v => Math.round(v * 100) / 100;
const px = (l, t, w, h) => ({ l: r2(l * 2 / 3 - 58), t: r2(t * 2 / 3 - 128), w: r2(w * 2 / 3), h: r2(h * 2 / 3) });
const site = (l, t, w, h) => { const b = px(l, t, w, h); return `position:absolute;left:${b.l}px;top:${b.t}px;width:${b.w}px;height:${b.h}px`; };
const fill = (markup, viewBox) => markup.replace(/<svg\b[^>]*>/, tag => tag.replace(/\s(width|height|viewBox)="[^"]*"/g, '')
  .replace('<svg', `<svg viewBox="${viewBox}" style="width:100%;height:100%;display:block"`));

module.exports = { site, fill };
