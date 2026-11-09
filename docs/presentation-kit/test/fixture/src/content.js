// Fixture content: segment 1 holds slides 1–2, segment 2 holds the demo slide 3 in its Q&A slot.
const SPEAKERS = { Ann: { full: 'Ann Example', init: 'AE' }, Bo: { full: 'Bo Example', init: 'BE' } };
const SEGMENTS = [
  { n: 1, name: 'Opening', head: 'Opening', short: 'Open', talk: 1, qa: 0, start: '16:00:00', owner: 'Ann', items: { '1a': 'Open.' } },
  { n: 2, name: 'Demo', head: 'Demo', short: 'Demo', talk: 2, qa: 1, start: '16:01:00', owner: 'Bo', items: { '2a': 'Show it.' } },
];
const SLIDES = [
  { n: 1, seg: 1, item: '1a', speaker: 'Ann', slot: 20, title: 'Fixture title', say: 'Hello </script> & welcome.', handover: 'Over to the problem.' },
  { n: 2, seg: 1, item: '1a', speaker: 'Ann', slot: 40, title: 'The problem', say: 'First point. [click] Second point.',
    cues: ['Point at the {{VERIFY:chart}}.'], handover: '(pause) Bo takes over.' },
  { n: 3, seg: 2, item: '2a', speaker: 'Bo', slot: null, title: 'Live demo', say: 'Here it is.' },
];
const BACKUPS = [['B1', 'Extra detail', 'Why this design?', 'Bo', 'Because it is simpler.']];
const DEMOS = {
  3: [{ n: 1, t: 'Open the app', time: '16:01:00 – 16:02:00', proves: 'Functionality · <b>2a</b>', op: ['Tab 1: <code>/home</code>'], say: 'This is the home page.', exp: 'It loads.', fb: 'Show the screenshot.' }],
};
module.exports = { SPEAKERS, SEGMENTS, SLIDES, QA: {}, BACKUPS, OPEN_ITEMS: [], DEMOS };
