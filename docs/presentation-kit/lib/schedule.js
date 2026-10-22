// Planned clock times shared by the speaker script and the deck's speaker companion. Pure: no deck context.
const { words } = require('./text');

// Speaking rate behind every time estimate.
const WPM = 130;

const hms = s => { const h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60; return `${h}:${String(m).padStart(2, '0')}:${String(x).padStart(2, '0')}`; };
const toS = t => { const [h, m, s] = t.split(':').map(Number); return h * 3600 + m * 60 + s; };
const est = s => Math.round(words(s.say) / WPM * 60);

// { [slide n]: [start, end] } in seconds since midnight. Slides run back to back from their segment's start;
// a Q&A slide (slot null) sits at the segment's start + talk and has no end.
function slideClock(segments, slides) {
  const clock = {};
  for (const seg of segments) {
    let t = toS(seg.start);
    for (const s of slides.filter(x => x.seg === seg.n)) {
      if (!s.slot) { clock[s.n] = [toS(seg.start) + seg.talk * 60, null]; continue; }
      clock[s.n] = [t, t + s.slot];
      t += s.slot;
    }
  }
  return clock;
}

module.exports = { WPM, hms, toS, est, slideClock };
