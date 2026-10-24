// SWE5006 speaker script and demo run book: the chapters of dist/swe5006-presentation-script.pdf.
// The kit (docs/presentation-kit/script/build.js) supplies the helpers, wraps this body and writes dist/.
// Worked example and tone: docs/swe5001/presentation/src/script.js. The #566 run books (pipeline steps P1–P7,
// system demo steps 1–8, contingencies) are in git history (379af301:docs/swe5006/presentation/src/script.js) for reuse.
const { C, md, esc, markers, countM, box, tbl, pre, reqBox, slideCard, qaTable, segment, cover, runSheet } = require('../../../presentation-kit/script/build').helpers();

// Markers written directly in this file are counted here; slide cards and Q&A tables count their own.
// m(): plain text with markers (escaped); mh(): HTML that may contain markers (not escaped). Call each once per string.
const m = s => { countM(s); return md(s); };
const mh = s => { countM(s); return String(s).replace(/\{\{(?:FILL|VERIFY|CONFIRM):[^}]*\}\}/g, x => md(x)); };

const DEV = C.DEV;
const REPO = 'PPsyrius/iss-groupbuy-team8';
const seg = n => C.SEGMENTS.find(s => s.n === n);
const speakersOf = list => [...new Set(list.map(s => C.SPEAKERS[s.speaker].full.split(' (')[0]))].join(', ');

// ---------- run sheet: coverage of every guideline item and every rubric row, derived from SLIDES ----------
const coverageRows = C.SEGMENTS.flatMap(s => Object.entries(s.items).map(([k, v]) => {
  const sl = C.SLIDES.filter(x => x.item === k || (x.alsoItems || []).includes(k));
  return [`<b>${k}</b>`, esc(v), sl.map(x => x.n).join(', ') || '—', speakersOf(sl)];
}));
const rubricRows = ['1.1', '1.2', '1.3', '1.4', '1.5', '1.6', '1.7', '1.8', '2.1', '2.2', '2.3', '2.4', '2.5', '2.6', '3.1', '3.2', '3.3', '4.1', '4.2', '4.3', '4.4', '4.5', '4.6', '5.1', '5.2', '5.3', '5.4', '5.5']
  .map(r => { const sl = C.SLIDES.filter(x => (x.rubric || []).includes(r)); return [`<b>${r}</b>`, sl.map(x => x.n).join(', ') || '—', speakersOf(sl)]; });

// Tips: the four "Presentation Tips" on page 2 of the guidelines (verbatim); none conflicts with page 1 (rubric open decision 1).
const tips = [
  ['Summarize and highlight the significant achievements. Talk more about the significant and interesting parts of the solution and implementation. Your audience should remember the unique aspects of the solution and not similarities to other solutions.', 'Depth goes to what is unique: pay-first teams that refund themselves, money safety under concurrency and failure (UC4 design, statechart, design problems), and a pipeline from pull request to dev in minutes.'],
  ['Manage your time well. Do not present too many slides. Remember that the audience can read the text in the slide so refrain from reading them.', '39 main slides in 34 minutes, two of them live demos; slots checked at 130 wpm in the table above; sample wording adds to the slide instead of reading it; details sit in backups B1–B16.'],
  ['Explain your design in top-down manner. Make sure that your audience understands the requirements, the high-level design and the high-level decisions that you make before drilling into some details.', 'Problem, scope and quality attributes first (slides 2–3); then architecture (9), transition strategies (10–12), use cases (13), one use case from analysis to design with its statechart (14–17), design problems (18–23), schema (24).'],
  ['Ensure that the contents on the slides are readable from the distance at which your audience is sitting. This is particular true for face-to-face presentation.', 'Rubric V1–V7: one idea per slide, diagrams cropped to the part discussed, labels checked on a projector or TV before 26 Oct.'],
];

const cueWords = [
  ['1 MIN', 'One minute of talk left in this segment', 'Lim'],
  ['TIME', 'Stop; finish the sentence and hand over', 'Lim'],
  ['Q: &lt;text&gt; → name', 'A question was parked; the named owner answers it in the buffer (17:04–17:10)', 'Lim'],
  ['BEHIND n / AHEAD n', 'The talk is n seconds behind or ahead of the run sheet; the next speaker applies the trims', 'Lim'],
  ['SHARE', 'Next person: start screen sharing now', 'Lim'],
  ['CLIP n', 'Live step n failed; play the recording of step n', 'Pan or Yorik'],
  ['EXPIRY OK / REFUND OK / REFUND LATE', 'State of the 16:05 expiry bloc (16:10 and 16:50)', 'Yorik'],
];

const handovers = C.SLIDES.filter((s, i) => i > 0 && s.speaker !== C.SLIDES[i - 1].speaker && s.slot !== null)
  .map(s => [String(s.n), `${C.SPEAKERS[C.SLIDES[s.n - 2].speaker].full} → ${C.SPEAKERS[s.speaker].full}`]);

const runSheetHtml = runSheet(`<p>34 minutes scripted, 16:30–17:04; the 6 minutes to 17:10 are the buffer for questions asked during the talk (rubric G1–G3). §1 runs 3 minutes and §2 6 minutes so that each member presents their own design problem (rubric G4, time budget).</p>
${box('warn', 'Interruption policy', `<ul style="margin:0;padding-left:12pt"><li>A lecturer may ask at any moment. The current speaker answers in <b>one or two sentences</b> (decision → reason → evidence or limitation), says “back to …” and continues from the same slide.</li><li>If the owner is someone else, the speaker says so and Lim posts <code>Q: &lt;question&gt; → owner</code> in the backchannel; parked questions are answered first in the buffer.</li><li>Hard checkpoints: 16:33 Eric, 16:39 Liu Jin, 16:42 Pan, 16:52 Yorik.</li></ul>`)}
<h2>Handovers (rubric G4: rehearse each one)</h2>
${tbl(['From slide', 'Speakers'], handovers)}
<h2>Every guideline item has slides and a speaker</h2>
${tbl(['Item', 'Guideline requirement (verbatim)', 'Slides', 'Speaker'], coverageRows)}
<h2>Every rubric row has a slide (rubric checklist)</h2>
${tbl(['Rubric row', 'Slides', 'Speaker'], rubricRows)}
<h2>Presentation tips checklist</h2>
${tbl(['Tip (guidelines page 2)', 'How this deck meets it'], tips)}
<h2>Cue words in the team backchannel (never the Zoom chat)</h2>
${tbl(['Message', 'Meaning', 'Sent by'], cueWords.map(r => [`<code>${r[0]}</code>`, r[1], r[2]]))}`);

// ---------- demo step cards ----------
const demoCard = d => `<div class="card demo"><div class="hd"><span>Step ${d.n} &nbsp; ${esc(d.t)}</span><small>${d.time}</small></div><div class="bd">
 <div class="row"><span class="lab">Proves</span><span>${d.proves}</span></div>
 <div class="row"><span class="lab cue">Operate</span><ol style="margin:0;padding-left:14pt">${d.op.map(o => `<li>${mh(o)}</li>`).join('')}</ol></div>
 <div class="row"><span class="lab say">Say</span><span class="say">${m(d.say)}</span></div>
 <div class="row"><span class="lab">Expect</span><span>${mh(d.exp)}</span></div>
 <div class="row"><span class="lab fb">Fallback</span><span>${mh(d.fb)}</span></div></div></div>`;

// ---------- segment 4: CI/CD pipeline demo (rubric 4.1–4.6) ----------
// Slides 27–32 run 16:42:00–16:46:30; the live part (slide 33) runs 16:46:30–16:52:00 (330 s).
const pipeSteps = C.DEMOS[33];

const seg4Html = () => {
  const s4 = seg(4);
  return `<section class="chap"><h1>4 ${esc(s4.name)} (${s4.talk} minutes)</h1>
<div class="seghead"><table class="meta"><tr><td>Clock</td><td>16:42:00 – 16:52:00 (slides 27–32 until 16:46:30, then the live pipeline demo on slide 33 until 16:52:00)</td></tr><tr><td>Owner</td><td>${esc(s4.owner)}; Lim approves as the frontend code owner</td></tr><tr><td>Slides</td><td>${C.SLIDES.filter(s => s.seg === 4).map(s => s.n).join(', ')}</td></tr></table></div>
${reqBox(s4)}
${box('tip', 'Tip', 'Slides 27–32 take 270 s. Pan opens the demo pull request at 16:41, during slide 26, so its checks (median 4.8 min) are green when the live part starts at 16:46:30. A backend change cannot finish its 16-minute PR checks inside the segment, so a backend PR is opened at ≈ 16:20 and merged before 16:40; the demo walks through both of its finished runs (rubric 4.1).')}
${C.SLIDES.filter(s => s.seg === 4).map(slideCard).join('')}
<h2>Live pipeline demo (16:46:30 – 16:52:00, 330 s)</h2>
${tbl(['Step', 'Shows', 'Rubric'], pipeSteps.map(d => [d.n, esc(d.t), d.proves.split(' · ')[0]]))}
<h3>Preconditions (checked at 15:30 by Pan)</h3>
<ul><li>Demo branch pushed and up to date with <code>origin/dev</code>; PR body ready; no PR opened yet. The backend merge (by 16:40) moves <code>dev</code>, so at 16:40–16:41 Pan rebases the demo branch onto the new <code>origin/dev</code>, pushes it and only then opens the PR; its checks start at 16:41 on the updated branch. ${m('{{CONFIRM:the copy change, its page, its author and the approving code owner (open item 14)}}')}</li>
<li>Backend pre-run: a small backend PR opened at ≈ 16:20 by its author, approved by Liu Jin (backend code owner) and squash-merged by 16:40 so its deploy finishes before the segment. ${m('{{CONFIRM:the backend change and its author}}')}</li>
<li>Merge freeze in the team chat: nobody merges into <code>dev</code> from 15:00 to 17:15 except these two PRs. A moved <code>dev</code> makes the branch out of date and re-runs the checks (strict required checks), which is why the demo branch is updated after the backend merge and before its PR opens, and why nothing else may merge after 16:40.</li>
<li>Latest <code>ci-frontend</code> and <code>ci-backend</code> runs on <code>dev</code> green (A.1); deploy switches <code>ENABLE_S3_DEPLOY</code> and <code>ENABLE_EC2_DEPLOY</code> unchanged; GitHub Actions budget not exhausted (a budget stop hit run 39442654274 on 20 Oct).</li>
<li>Lim signed in to GitHub on his own machine with the PR list open; he is not sharing at 16:47.</li>
<li>Backup video (rubric G6, 4.6): recorded on dev by Sat 24 Oct, 1080p, one clip per step P1–P6, stored in the cloud and on a USB drive, open in a player on Pan’s machine and tested with Zoom “share sound” off. ${m('{{FILL:recording link and USB holder}}')}</li></ul>
<h3>Screen layout (Pan’s browser window, shared from 16:46:30; browser zoom 125–150 %, rubric V7)</h3>
${tbl(['Tab', 'Content', 'Prepared state'], [
  ['1', 'GitHub: the demo pull request', 'Opened at 16:41; Files changed tab ready'],
  ['2', 'GitHub Actions: <code>ci-frontend</code> filtered to branch <code>dev</code>', 'Refresh after the merge to see the new run'],
  ['3', 'GitHub Actions: the backend PR run (pre-triggered)', 'Run summary open; build-quality one click away'],
  ['4', 'GitHub Actions: the backend push run on <code>dev</code> (deploy)', 'Run graph open; deploy job one click away'],
  ['5', `Dev site <code>${DEV}</code>`, 'On the page the change touches; DevTools closed'],
  ['Player', 'Clips P1–P6 (24 Oct)', 'Paused at the start of clip P1'],
])}
<h3>Step by step</h3>
${pipeSteps.map(demoCard).join('')}
${box('warn', 'Never live', 'Clicking Merge on anything other than the demo PR; “Update branch” on the demo PR; re-running a failed job; running <code>rollback-backend.yml</code> or any <code>workflow_dispatch</code>; opening Settings → Secrets and variables or the ruleset editor; pushing to <code>dev</code>; any AWS console or SSM session on the shared screen. If the PR is not merged by 16:49, leave it open and merge after 17:15, so a deploy never lands during the system demo.')}
${box('tip', 'Tip', 'Narrate results, not clicks: “checks green in four minutes fifty”, not “now I click the checks tab”. No troubleshooting on air.')}
${qaTable(4)}
</section>`;
};

// ---------- segment 5: system demo (rubric 5.1–5.5) ----------
// Slide 34 16:52:00–16:52:30; live demo (slide 35) 16:52:30–17:01:00 (510 s); slides 36–38 17:01:00–17:04:00.
// Eric shares one screen with two Chrome profiles and operates; Yorik speaks. US34-04 is a team of two with the
// shortest allowed window (2,400 s = PurchaseQuoteWindow.SECONDS; scripts/dev-demo-listings.json).
const demoSteps = C.DEMOS[35];

const seg5Html = () => {
  const s5 = seg(5);
  return `<section class="chap"><h1>5 ${esc(s5.name)} (${s5.talk} minutes, then ${s5.qa} minutes of buffer)</h1>
<div class="seghead"><table class="meta"><tr><td>Clock</td><td>16:52:00 – 17:04:00 (slide 34 to 16:52:30; live demo on slide 35 to 17:01:00; slides 36–38 to 17:04:00) · 17:04–17:10 buffer for parked questions</td></tr><tr><td>Owner</td><td>${esc(s5.owner)}; Lim chairs the buffer</td></tr><tr><td>Slides</td><td>${C.SLIDES.filter(s => s.seg === 5).map(s => s.n).join(', ')}</td></tr></table></div>
${reqBox(s5)}
${box('tip', 'Tip', 'Eric shares one screen at 16:52:00 (<code>SHARE</code> from Pan) with the deck in the Leader window, tab 1, and operates every window; Yorik speaks throughout. The expiry bloc is started at 16:05 so that D4 shows a refund that no one triggered by hand.')}
${slideCard(C.SLIDES.find(s => s.n === 34))}
<h2>Decision: live or recorded payment steps (go/no-go, Fri 23 Oct, 18:00)</h2>
${tbl(['Condition (Yorik decides with Lim; posted in #464)', 'GO: live on dev', 'NO-GO: recorded or local, said as such'], [
  ['#517 fixed: a new START on dev reaches paid within one minute and shows under My blocs', 'D1, D2 live; fallback = the 24 Oct clips', 'D1, D2 from the 24 Oct clips if they exist, else from local evidence (B11), said as “verified locally, not yet on dev”'],
  ['#223 done: Checkout and scheduling on for dev (<code>BLOCBUY_SCHEDULING_ENABLED</code>; default false in application-ec2.yml); a <code>US34-04</code> bloc started on 24 Oct expired within one sweep and refunded', 'D4 live with the 16:05 bloc', 'D4 from B11 (local); slide 34 rows read “recorded” or “local”'],
  ['Full dry run on dev on Sat 24 Oct passes in order, timed, and is recorded', 'Rehearse at the real clock on 25 Oct', 'Only the NO-GO path is rehearsed'],
  ['D3, D5, D6 do not depend on payments', 'live', 'live (D3 on an account that already has orders)'],
])}
${box('warn', 'Watch out', mh('On dev on 23 Oct, a paid START settled and a natural expiry refunded once (PR #664, merged 24 Oct, bef39b72); a second buyer’s paid JOIN on dev is not yet shown (#590), and #517 and #223 stay open. Every dev payment step here is conditional on the 24 Oct dry run. {{CONFIRM:go/no-go result of 23 Oct, per step D1, D2, D4 (open item 1)}}'))}
<h2>Preconditions (checked at 15:30 by Eric and Yorik)</h2>
<ul><li>Health <code>UP</code> (A.1); latest runs on <code>dev</code> green; merge freeze in force; the §4 demo PR is the only change that may land.</li>
<li>Two Google buyer accounts signed in on Eric’s machine in two Chrome profiles, “Leader” (account A) and “Joiner” (account B); the same two on Yorik’s machine as standby. ${m('{{CONFIRM:which two accounts and the display names shown on screen (open item 15)}}')}</li>
<li>At <b>≈ 16:05</b> (latest 16:07, so the refund lands before the 16:50 check and §5 starts at 16:52) Yorik, as account B, starts a bloc on <code>US34-04</code> and pays; nobody joins; receipt bookmarked in the Joiner window, tab 3. It is due ≈ 16:45; the sweep (every minute) expires it and the refund worker (every 30 s) refunds it ≈ 16:46–16:48. Post <code>EXPIRY OK</code> at 16:10 and <code>REFUND OK</code> / <code>REFUND LATE</code> at 16:50.</li>
<li>Listings converged: <code>scripts/seed-dev-demo-listings.sh --check</code> passes for the four <code>US34-*</code> listings (≤ 12:00, Yorik).</li>
<li>Terminal with font ≥ 18 pt and the curl line ready (D5); Joiner DevTools closed until D5.</li>
<li>IDE on a local checkout of <code>dev</code>, font ≥ 18 pt, with the D6 test run green after 15:00 and its file open; Docker running (rubric G10).</li>
<li>Backup clips D1–D6 recorded on dev on Sat 24 Oct at 1080p, stored in the cloud and on a USB drive, open in a player on Eric’s machine (copy on Yorik’s), tested with Zoom “share sound” off (rubric G6, 5.5). ${m('{{FILL:clip folder link and USB holder}}')}</li>
<li>Browser zoom 125–150 % (rubric V7); Do Not Disturb on; no password manager, email or bookmarks bar visible.</li></ul>
<h2>Screen layout (Eric’s shared screen)</h2>
${tbl(['Window · tab', 'Content', 'Prepared state'], [
  ['Leader · 1', 'Deck <code>swe5006-presentation.html#34</code>', 'Slide 34; slides 36–39 follow'],
  ['Leader · 2', `Listing <code>US34-04</code> on <code>${DEV}</code>`, 'Signed in as account A'],
  ['Joiner · 1', 'Empty tab for the invite link', 'Signed in as account B'],
  ['Joiner · 2', '<code>/account</code> (account B)', 'Loaded'],
  ['Joiner · 3', 'Receipt of the 16:05 payment', 'Bookmarked at 16:10; reload before D4'],
  ['Terminal', 'curl for D5', 'Command typed, not run'],
  ['IDE', 'D6 test, green', 'Test file and PaidGroupDealMapper.xml open'],
  ['Player', 'Clips D1–D6 (24 Oct)', 'Paused at clip D1'],
])}
<h2>Live system demo (16:52:30 – 17:01:00, 510 s)</h2>
${tbl(['Step', 'Shows', 'Rubric'], demoSteps.map(d => [d.n, esc(d.t), d.proves.split(' · ')[0]]))}
${slideCard(C.SLIDES.find(s => s.n === 35))}
<h3>Step by step</h3>
${demoSteps.map(demoCard).join('')}
${box('warn', 'Never live', 'Editing any deadline, price or listing on shared dev; any SSM command, database client or AWS console on the shared screen; the Stripe dashboard (keys and webhook secrets live there); the DevTools Application or Network tab with cookies or headers visible; racing two buyers or replaying a webhook on dev; a second payment to “try again”; pointing k6 at dev.')}
${box('tip', 'Plan C', 'A step fails live and has no recording: show its local evidence (B11 for payment and refund, the D6 test for the race, B12 for load) and say: <i>“verified locally, not yet on dev.”</i> Never present a local result as a dev one.')}
<h2>After the demo (17:01:00 – 17:04:00)</h2>
<h3>k6 pre-run for slide 36 (rubric 5.3)</h3>
<ul><li><b>Sat 24 Oct</b>, Pan: bring up the SWE5001 environment (applied per session, torn down nightly; open item 16), run <code>perf/k6/browse.js</code> (k6 1.6.1 by digest; ten 60 s plateaus, 10 → 200 requests/s) against the SWE5001 CloudFront, keep the k6 summary and the dashboard export, write the run into the day log, tear the environment down. ${m('{{FILL:pre-run date, label and summary path}}')}</li>
<li>Slide 36’s table is filled from that summary before the 25 Oct rehearsal; on the day the report file is opened from Eric’s machine if asked. Never point k6 at the SWE5006 dev host.</li></ul>
${C.SLIDES.filter(s => s.n >= 36).map(slideCard).join('')}
${qaTable(5)}
</section>`;
};

// ---------- segments ----------
const segsHtml = [
  segment(seg(1), {
    tip: 'Lim opens at 16:30:00 sharp, with the deck shared and the notes window off the shared screen. Eight slides in three minutes: the tables carry the facts, the wording carries only the point of each slide.',
    warn: mh('Never present planned hours as actual effort (slide 5). Paid start and the expiry refund were shown on dev on 23 Oct (PR #664); never say a second buyer’s paid join works on dev unless the 24 Oct dry run shows it (#590).'),
  }),
  segment(seg(2), {
    tip: 'Eric presents 2.1–2.4 and his own design problem; then Yorik, Pan and Lim each have one 20-second slide on their own problem; Liu Jin closes with the full-form State problem and the schema, and carries straight on into §3. The next speaker unmutes during the previous speaker’s last sentence.',
    warn: mh('Handover allowance: speech is about 300 s of the 360 s slots, so the four handovers inside §2 (slides 19, 20, 21, 22) get about 15 s each, rehearsed short: the next speaker unmutes and starts with their first line, no greeting beyond their name. Beyond that, segment 2 has no slack: answer design questions in one sentence and park the rest.'),
  }),
  segment(seg(3), {
    tip: 'Liu Jin continues from slide 24 without a handover.',
    warn: mh('Never present a local or SWE5001 result as a SWE5006 dev result: say where each number ran.'),
  }),
  seg4Html(),
  seg5Html(),
].join('');

// ---------- timeline ----------
const timeline = `<section class="chap"><h1>6 Timeline before, on and after the day</h1>
<h2>Before the day</h2>
${tbl(['Date', 'Do', 'Owner'], [
  ['Wed 21 Oct', 'Deck re-planned from the rubric; §1 drafted.', 'Lim'],
  ['Fri 23 Oct', '<b>Slides due</b> (#464). Each owner reviews their slides, cards and Q&amp;A rows; the Sprint 4 review and retro feed slides 5 and 7. <b>18:00 go/no-go</b> for the live payment steps, posted in #464.', 'All; Yorik decides with Lim'],
  ['Sat 24 Oct', '<b>Full dry run on dev</b> of D1–D6 in order and timed: a <code>US34-04</code> bloc started ≈ 10:00 by account B expires and refunds (D4), START and JOIN on <code>US34-04</code> with both accounts (D1, D2); payment and refund ids in the day log (no secrets). <b>Backup videos</b> of both demos recorded on the final Sprint 4 build deployed to dev (Sprint 4 ended 23 Oct), 1080p, one clip per step, stored in the cloud and on a USB drive (rubric G6). <b>k6 pre-run</b> on the SWE5001 ECS environment; keep its summary for slide 36.', 'Yorik, Eric (system); Pan (pipeline, k6)'],
  ['Sun 25 Oct', '<b>Timed rehearsal</b> in Zoom at the real clock with injected questions; every diagram slide and its crops checked on a projector or TV from across the room (rubric V7); every rubric checklist box ticked; every marker resolved; rebuild deck and script.', 'All; Lim times'],
])}
<h2>Presentation day (Mon 26 Oct)</h2>
${tbl(['Time (SGT)', 'Do', 'Owner'], [
  ['≤ 12:00', 'Health and recent runs (A.1); listings check; §5 preconditions list started.', 'Yorik, Pan'],
  ['14:30', 'Tech check in a test Zoom: mic, camera, sharing, Do Not Disturb.', 'All'],
  ['15:00', 'Merge freeze on <code>dev</code> until 17:15.', 'Lim'],
  ['15:30', 'Check the §4 and §5 preconditions lists.', 'Pan (§4); Eric, Yorik (§5)'],
  ['≈ 16:05 (≤ 16:07)', 'Start the expiry bloc on <code>US34-04</code> as account B and pay; bookmark the receipt. <code>EXPIRY OK</code> at 16:10, <code>REFUND OK</code> / <code>REFUND LATE</code> at 16:50.', 'Yorik'],
  ['16:30', 'Start; follow the run sheet. The scribe logs every remark verbatim (rubric G9).', 'Lim; scribe'],
  ['16:40 – 16:41', 'Backend pre-run PR merged; rebase the demo branch onto the new <code>origin/dev</code>, push it, then open the demo PR at 16:41.', 'Pan'],
  ['17:04 / 17:10', 'Slide 39; parked questions; end.', 'Lim'],
])}
<h2>After the presentation</h2>
<ul><li>The same evening: every lecturer remark goes into #463 as a report task (by 27 Oct, rubric G9).</li><li>Day log under <code>docs/agile/sprint-04/log/</code>: the demo PR, run links and any payment or refund ids shown (no secrets).</li></ul>
</section>`;

// ---------- open items (rendered after every counted chapter) ----------
const openItems = () => `<section class="chap"><h1>7 Open items before the 25 Oct rehearsal</h1>
<p>There are ${markers.FILL} <span class="mk mk-fill">[FILL]</span>, ${markers.VERIFY} <span class="mk mk-verify">[VERIFY]</span> and ${markers.CONFIRM} <span class="mk mk-confirm">[CONFIRM]</span> markers in this script. <b>FILL</b> is a value that does not exist yet, so never present a guess; <b>VERIFY</b> is a claim to check against the repository, the report or run logs; <b>CONFIRM</b> needs team agreement. All must be gone after the 25 Oct rehearsal.</p>
${tbl(['#', 'Item', 'Owner', 'Blocks slide'], C.OPEN_ITEMS.map(r => r.map(esc)))}
</section>`;

// ---------- appendix ----------
const appendix = `<section class="chap"><h1>A Appendix — check commands</h1>
<p>Every command is read-only. Run them off the shared screen, from a checkout of <code>dev</code> at the repository root.</p>
<h3>A.1 Health and recent runs</h3>${pre(`curl -s ${DEV}/api/actuator/health          # expect "status":"UP"
gh run list --repo ${REPO} --workflow ci-frontend.yml --branch dev --limit 3
gh run list --repo ${REPO} --workflow ci-backend.yml --branch dev --limit 3
gh pr list --repo ${REPO} --base dev --state open      # what could still merge before the freeze`)}
</section>
<section class="chap"><h1>B Appendix — backup slides (questions only)</h1>
<p>Backups follow slide 39 in the HTML deck and the PDF. In the deck press <b>B</b> for the index (BX) and click a row, or open <code>#B6</code> directly.</p>
${tbl(['ID', 'Slide', 'Answers'], C.BACKUPS.map(b => b.map(esc)))}
</section>`;

// ---------- cover ----------
const coverHtml = cover({
  kicker: 'SWE5006 Practice Project · Graduate Certificate in Designing Modern Software Systems',
  title: 'Speaker Script and Live Demo Run Book',
  subtitle: 'BlocBuy — Team 8 · Monday 26 October 2026, 16:30–17:10 SGT (Zoom)',
  meta: [
    ['Responsible', 'Yu Qianshuo (Yorik), presentation lead and storyline (#464); Lim Jiechao, slide master.'],
    ['Specification', '<code>docs/swe5006/presentation/RUBRIC.md</code>: every slide records the rubric rows it covers; the coverage tables in the run sheet are derived from them.'],
    ['Companion files', '<code>swe5006-presentation.html</code> — the deck (39 slides + backups BX, B1–B16, the 1:1 diagram detail pages D9-1 … D31-3 (index DX) and the full-size diagrams F1–F21 (index FX); Chrome or Edge; <b>S</b> notes, <b>T</b> timer, <b>O</b> overview, <b>F</b> fullscreen, <b>B</b> backup index) · <code>swe5006-presentation.pdf</code> — the same deck for submission.'],
    ['Facts as of', '23 Oct 2026. Dev health <code>UP</code>; a paid START and a natural expiry refund shown on dev (PR #664, merged 24 Oct, bef39b72); a second buyer’s paid JOIN on dev not yet shown (#590), so #223 and #517 stay open.'],
  ],
  after: `${box('', 'Markers', `${md('{{FILL:}}')} value that does not exist yet; never present a guess · ${md('{{VERIFY:}}')} claim to check against the repository, report or run logs · ${md('{{CONFIRM:}}')} needs team agreement. They must all be gone after the 25 Oct rehearsal.`)}`,
});

// Segment chapters render before openItems(), so its counts include their markers.
const body = segsHtml + timeline;
module.exports = coverHtml + '\n' + runSheetHtml + body + openItems() + appendix;
