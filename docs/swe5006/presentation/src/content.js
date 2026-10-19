// BlocBuy SWE5006 deck — single source for speaker wording, timings, cues and Q&A.
// SCAFFOLD: segments, items and slides below are placeholders that prove the pipeline; replace them from the
// SWE5006 presentation guideline (see AGENTS.md, "Start here").
// Markers: {{FILL:text}} {{VERIFY:text}} {{CONFIRM:text}} render as visible chips in deck and script.

const SPEAKERS = {
  Lim: { full: 'Lim Jiechao', init: 'LJ' },
  Eric: { full: 'Wang Ziang (Eric)', init: 'WZ' },
  'Liu Jin': { full: 'Liu Jin', init: 'LJ' },
  Yorik: { full: 'Yu Qianshuo (Yorik)', init: 'YQ' },
  Pan: { full: 'Panpakorn Siripanich (Pan)', init: 'PS' },
};

// talk/qa in minutes; start is the segment's clock time (SGT). Item texts are copied verbatim from the guideline.
const SEGMENTS = [
  { n: 1, name: 'Overview', head: 'Overview', short: 'Overview', talk: 2, qa: 0, start: '16:30:00', owner: 'Yu Qianshuo (Yorik)',
    items: { '1a': 'Placeholder: copy the guideline item text here.' } },
  { n: 2, name: 'Close', head: 'Close', short: 'Close', talk: 0, qa: 1, start: '16:32:00', owner: 'Yu Qianshuo (Yorik)',
    items: { '2a': 'Placeholder: copy the guideline item text here.' } },
];

// slot in seconds; null = Q&A
const SLIDES = [
  { n: 1, seg: 1, item: '1a', speaker: 'Yorik', slot: 15, title: 'BlocBuy',
    say: "Good afternoon, we're Team 8, and this is BlocBuy. {{FILL:opening line}}",
    cues: ['Yorik shares the HTML deck in Chrome and presses S for the notes window.'] },
  { n: 2, seg: 1, item: '1a', speaker: 'Yorik', slot: 60, title: 'Logical view: one direct call, everything else by events',
    say: '{{FILL:wording from the report section that owns this slide}}',
    cues: ['Shared diagram from docs/presentation-kit/diagrams/logical-view.svg; copy it into src/diagrams/ before changing it for SWE5006.'] },
  { n: 3, seg: 2, item: '2a', speaker: 'Yorik', slot: null, title: 'Thank you',
    say: 'Thank you. We are happy to take questions.' },
];

// Q&A tables per segment number: { window: '16:44–16:45', rows: [[question, answer, owner], ...] }
const QA = {};

// Backup index rows: [id, title, what it answers]
const BACKUPS = [
  ['B1', 'Context map', 'Bounded contexts and the events between them'],
];

// Open items: [#, item, owner, blocks slide]
const OPEN_ITEMS = [
  [1, 'Obtain the SWE5006 presentation guideline and replace SEGMENTS, items and slot times', 'Yorik', 'all'],
  [2, 'Storyline and demo script on the deployed dev environment (#464)', 'Yorik', 'all'],
];

module.exports = { SPEAKERS, SEGMENTS, SLIDES, QA, BACKUPS, OPEN_ITEMS };
