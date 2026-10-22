// SWE5006 speaker script and demo run book: the chapters of dist/swe5006-presentation-script.pdf.
// The kit (docs/presentation-kit/script/build.js) supplies the helpers, wraps this body and writes dist/.
// Worked example and tone: docs/swe5001/presentation/src/script.js.
const { C, md, esc, markers, countM, box, tbl, pre, reqBox, slideCard, qaTable, segment, cover, runSheet } = require('../../../presentation-kit/script/build').helpers();

// Markers written directly in this file are counted here; slide cards and Q&A tables count their own.
// m(): plain text with markers (escaped); mh(): HTML that may contain markers (not escaped). Call each once per string.
const m = s => { countM(s); return md(s); };
const mh = s => { countM(s); return String(s).replace(/\{\{(?:FILL|VERIFY|CONFIRM):[^}]*\}\}/g, x => md(x)); };

const DEV = C.DEV;
const REPO = 'PPsyrius/iss-groupbuy-team8';
const slide = n => C.SLIDES.find(s => s.n === n);
const seg = n => C.SEGMENTS.find(s => s.n === n);

// ---------- run sheet: coverage of every guideline item (text from content.js, verbatim) ----------
const where = {
  '1a': ['1, 2', 'Lim'], '1b': ['3 (use case diagram)', 'Lim'], '1c': ['6', 'Lim'], '1d': ['5; B11', 'Lim'], '1e': ['4', 'Lim'],
  '1f': ['6 (what each retro changed)', 'Lim'], '1g': ['4 (artefacts named in the cues); B11', 'Lim'],
  '2a': ['7; B8, B9', 'Eric'], '2b': ['7 (bottom strip); B7', 'Eric'], '2c': ['3, referred to on 7', 'Lim, Eric'],
  '2d': ['7–12; B1, B2, B3, B4, B7', 'Eric, Liu Jin, Yorik, Pan, Lim'], '2e': ['8, 9, 10, 11, 12; B6', 'Liu Jin, Eric, Yorik, Pan, Lim'], '2f': ['8 (right); B5', 'Liu Jin'],
  '3a': ['13; B10', 'Liu Jin'], '3b': ['13, 14; B10', 'Liu Jin'],
  '4a': ['15', 'Pan'], '4b': ['16, 18, 20 + live pipeline demo (steps P1–P7); B12', 'Pan'], '4c': ['19, 14', 'Pan'],
  '4d': ['16, 17; B14', 'Pan'], '4e': ['20 (fallback box) + the 25 Oct recording', 'Pan'],
  '5a': ['21 + demo steps 1–4', 'Eric, Yorik'], '5b': ['demo steps 5–7; B4, B15, B16; slides 9, 10, 15–20', 'Yorik, Eric'], '5c': ['the 25 Oct recordings, one clip per step', 'Yorik, Eric'],
};
const coverageRows = C.SEGMENTS.flatMap(s => Object.entries(s.items).map(([k, v]) => [`<b>${k}</b>`, esc(v), where[k][0], where[k][1]]));

// Tips: the four "Presentation Tips" of "SWE5006 - Practice Project Presentation Guidelines" (verbatim), then the lecturer's note.
const tips = [
  ['Summarize and highlight the significant achievements. Talk more about the significant and interesting parts of the solution and implementation. Your audience should remember the unique aspects of the solution and not similarities to other solutions.', 'The unique parts get the time: pay-first teams that refund themselves (slides 2, 10), one codebase in two topologies (slide 7), money safety under crashes and races (slides 9, 10), a pipeline from PR to dev in minutes (slides 16, 20). Slide 22 repeats them.'],
  ['Manage your time well. Do not present too many slides. Remember that the audience can read the text in the slide so refrain from reading them.', '22 main slides in 37 minutes, two of them live demos; slots checked at 130 wpm in the table above; the sample wording adds to the slide instead of reading it; details sit in backups B1–B16.'],
  ['Explain your design in top-down manner. Make sure that your audience understands the requirements, the high-level design and the high-level decisions that you make before drilling into some details.', 'Requirements and scope first (slides 2–3), then the top-down architecture slide (7), then one use case and design problem per member (8–12).'],
  ['Ensure that the contents on the slides are readable from the distance at which your audience is sitting. This is particular true for face-to-face presentation.', 'On Zoom: body text ≥ 15 px on a 1280 × 720 canvas, diagram labels ≥ 14.5 px (STYLE.md); one message per slide title.'],
  ['Lecturers may ask questions during the presentation, please be prepared to respond accordingly and continue the presentation. (lecturer’s note)', 'Interruption policy above: answer in ≤ 30 s, then continue; the 3-minute buffer 17:07–17:10 absorbs them; Lim parks longer ones.'],
  ['Backup: Record a video in advance in case of any technical issues during the live demo. (lecturer’s note; items 4e and 5c)', 'Both demos recorded on dev by 12:00 on Sun 25 Oct; one clip per step, cued with <code>CLIP n</code> (§4, §5).'],
];

const cueWords = [
  ['1 MIN', 'One minute of talk left in this segment', 'Lim'],
  ['TIME', 'Stop; finish the sentence and hand over', 'Lim'],
  ['Q: &lt;text&gt; → name', 'A question was parked; the named owner answers it in the buffer or in Q&amp;A', 'Lim'],
  ['BEHIND n / AHEAD n', 'The talk is n seconds behind or ahead of the run sheet; the next speaker applies the §6 cuts', 'Lim'],
  ['EXPIRY OK / EXPIRY LATE', '16:10: the 16:05 bloc is open at 1 of 2 on /account, or it is not (retry rule in §6)', 'Yorik'],
  ['PR OPEN', 'Pan has opened the demo pull request (target 16:41)', 'Pan'],
  ['APPROVE', 'Lim approves the demo pull request now, from his own GitHub session', 'Pan → Lim'],
  ['REFUND OK / REFUND LATE', '16:50: the receipt of the 16:05 payment shows the refund, or it does not yet', 'Yorik'],
  ['SHARE', 'Next person: start screen sharing now (16:46:15 Lim → Pan; 16:53:00 Pan → Eric)', 'Lim'],
  ['CLIP n', 'Live step n failed; play the recording of step n (pipeline steps use <code>CLIP Pn</code>)', 'Pan or Eric'],
];

const background = [
  ['≈ 16:05', 'Yorik, as the leader account, starts the expiry bloc on <code>US34-04</code> (team of 2, 40 min) and pays with the Sandbox test card. It must expire ≈ 16:45 to show its refund by 16:53. Latest start 16:15.', 'Yorik'],
  ['16:10', '<code>EXPIRY OK</code> or <code>EXPIRY LATE</code> (bloc open at 1 of 2 on /account).', 'Yorik'],
  ['16:41', 'During slide 14, muted: Pan runs <code>gh pr create</code> for the prepared demo branch (A.5) and posts <code>PR OPEN</code>. Checks take ≈ 4.8 min.', 'Pan'],
  ['16:46:15', 'Share switch Lim → Pan (live pipeline demo).', 'Lim, Pan'],
  ['≈ 16:47:15', '<code>APPROVE</code>: Lim approves the demo pull request from his own machine.', 'Pan, Lim'],
  ['16:50', '<code>REFUND OK</code> or <code>REFUND LATE</code>.', 'Yorik'],
  ['16:53:00', 'Share switch Pan → Eric (system demo, deck tab at slide 21). Eric keeps sharing to the end (slide 22, backups).', 'Pan, Eric'],
];

const runSheetHtml = runSheet(`<p>Questions: during the talk (lecturers may ask at any time) · buffer <b>17:07–17:10</b> for questions asked during the talk · Q&amp;A <b>17:10–17:15</b>. Segment 4 slides end with slide 20 at 16:46:15; the live pipeline demo fills 16:46:15–16:53:00 (§4). Segment 5 is slide 21 plus the system demo (§5).</p>
${box('warn', 'Interruption policy', `<ul style="margin:0;padding-left:12pt"><li>A lecturer may ask at any moment. The current speaker stops, answers in <b>≤ 30 s</b> (decision → reason → evidence or limitation), says “back to …” and continues from the same slide. A short honest answer beats a long one.</li><li>If the right owner is someone else, the speaker says “<i>Yorik owns that; he’ll answer it at the end</i>” and Lim posts <code>Q: &lt;question&gt; → Yorik</code> in the backchannel. Parked questions are answered first in the buffer (17:07–17:10), then in Q&amp;A.</li><li>The <b>3-minute buffer</b> pays for questions during the talk. Lim tracks the clock against the run sheet and posts <code>BEHIND n</code> when a segment starts late; the next speaker applies the cuts in §6. Hard checkpoints: 16:34 Eric, 16:39 Liu Jin, 16:42 Pan, 16:53 Eric.</li><li>During a live demo the operator keeps operating while the narrator answers; a live step is never restarted because of a question.</li></ul>`)}
<h2>Background actions (off screen)</h2>
${tbl(['Time', 'Action', 'Who'], background)}
<h2>Every guideline item has slides and a speaker</h2>
${tbl(['Item', 'Guideline requirement (verbatim)', 'Slides and demo steps', 'Speaker'], coverageRows)}
<h2>Presentation tips checklist</h2>
${tbl(['Tip', 'How this deck meets it'], tips)}
<h2>Cue words in the team backchannel (never the Zoom chat)</h2>
${tbl(['Message', 'Meaning', 'Sent by'], cueWords.map(r => [`<code>${r[0]}</code>`, r[1], r[2]]))}`);

// ---------- segments 1–3 ----------
const segs13 = [
  segment(seg(1), {
    tip: 'Lim opens at 16:30:00 sharp, with the deck already shared and the notes window off the shared screen. Slide 6 carries the go/no-go: after 23 Oct, say in one sentence whether the payment steps of the demo are live or recorded, so nobody is surprised at 16:53.',
    warn: mh('Never say that payments or refunds work on dev unless the 24 Oct dry run proved it (§5 decision table). Re-check slide 6 on 25 Oct (open item 8). {{CONFIRM:slide 6 wording after the 23 Oct go/no-go}}'),
  }),
  segment(seg(2), {
    tip: 'Five speakers in five minutes: each design slide is 45 s. The next speaker unmutes during the previous speaker’s last sentence and gives their name once (“I’m Liu Jin.”). Each speaker answers questions about their own slide; backups B1–B9 sit one key away (B).',
    warn: 'If a lecturer asks a long design question here, answer the decision in one sentence and park the rest: segment 2 has no slack, and Liu Jin must start testing at 16:39:00.',
  }),
  segment(seg(3), {
    tip: 'Liu Jin has two 90-second slides. At 16:41, while slide 14 is on, Pan (muted) opens the demo pull request; Liu Jin does not wait for it. The hand-over line on slide 14 starts Pan’s segment; the share switch to Pan comes later, at 16:46:15.',
    warn: 'Recount the test numbers on 25 Oct (open item 13). Never present the 30 Sep workload numbers as a dev or production result: they are local.',
  }),
].join('');

// ---------- demo step cards (both demos) ----------
const demoCard = d => `<div class="card demo"><div class="hd"><span>Step ${d.n} &nbsp; ${esc(d.t)}</span><small>${d.time}</small></div><div class="bd">
 <div class="row"><span class="lab">Proves</span><span>${d.proves}</span></div>
 <div class="row"><span class="lab cue">Operate</span><ol style="margin:0;padding-left:14pt">${d.op.map(o => `<li>${mh(o)}</li>`).join('')}</ol></div>
 <div class="row"><span class="lab say">Say</span><span class="say">${m(d.say)}</span></div>
 <div class="row"><span class="lab">Expect</span><span>${mh(d.exp)}</span></div>
 <div class="row"><span class="lab fb">Fallback</span><span>${mh(d.fb)}</span></div></div></div>`;

// ---------- segment 4: CI/CD pipeline demo ----------
const pipeSteps = C.DEMOS[20];

const seg4 = seg(4);
const seg4Html = `<section class="chap"><h1>4 ${esc(seg4.name)} (${seg4.talk} minutes)</h1>
<div class="seghead"><table class="meta"><tr><td>Clock</td><td>16:42:00 – 16:53:00 (slides 15–20 until 16:46:15, then the live pipeline demo until 16:53:00)</td></tr><tr><td>Owner</td><td>${esc(seg4.owner)}; Lim approves as the frontend code owner</td></tr><tr><td>Slides</td><td>15, 16, 17, 18, 19, 20</td></tr></table></div>
${reqBox(seg4)}
${box('tip', 'Tip', 'Slides 15–20 take 255 s. Pan opens the pull request at 16:41, during slide 14, so its checks (median 4.8 min) are green when the live part starts at 16:46:15. The live part shows one real frontend change end to end and the backend deploy as a recorded run: a backend change could not finish its 16-minute PR checks inside the segment.')}
${C.SLIDES.filter(s => s.seg === 4).map(slideCard).join('')}
<h2>Live pipeline demo (16:46:15 – 16:53:00, ≈ 405 s)</h2>
${tbl(['Step', 'Shows', 'Items'], pipeSteps.map(d => [d.n, esc(d.t), d.proves.split(' · ')[0]]))}
<h3>Preconditions (checked at 15:30 by Pan)</h3>
<ul><li>Demo branch <code>feature/frontend-swe5006-live-demo</code> pushed and up to date with <code>origin/dev</code> (A.5); PR body file ready; no PR opened yet. ${m('{{CONFIRM:the copy change, its page and the approving code owner (open item 15)}}')}</li>
<li>Merge freeze announced in the team chat: nobody merges into <code>dev</code> from 15:00 to 17:15 except this PR. A moved <code>dev</code> makes the branch out of date and re-runs the checks (strict required checks).</li>
<li>Latest <code>ci-frontend</code> and <code>ci-backend</code> runs on <code>dev</code> green (A.1); deploy switches <code>ENABLE_S3_DEPLOY</code> and <code>ENABLE_EC2_DEPLOY</code> unchanged.</li>
<li>Lim signed in to GitHub on his own machine with the PR list open; he is not sharing at 16:47.</li>
<li>Fallback: the 25 Oct recording, cut into clips P1–P7, open in a player on Pan’s machine and tested with Zoom “share sound” off. ${m('{{FILL:recording link (open item 19)}}')}</li></ul>
<h3>Screen layout (Pan’s browser window, shared from 16:46:15)</h3>
${tbl(['Tab', 'Content', 'Prepared state'], [
  ['1', 'GitHub: the demo pull request', 'Opened at 16:41; Files changed tab ready'],
  ['2', 'GitHub Actions: <code>ci-frontend</code> filtered to branch <code>dev</code>', 'Refresh after the merge to see the new run'],
  ['3', 'GitHub Actions run 39133506399 (backend deploy, 18 Oct)', 'Run graph open; deploy job one click away'],
  ['4', '<code>.github/workflows/rollback-backend.yml</code> on <code>dev</code>', 'File view, not the Run workflow dialog'],
  ['5', `Dev site <code>${DEV}</code>`, 'On the page the change touches; DevTools closed'],
  ['Player', 'Clips P1–P7 (25 Oct)', 'Paused at the start of clip P1'],
])}
<h3>Step by step</h3>
${pipeSteps.map(demoCard).join('')}
${box('warn', 'Never live', 'Clicking Merge on anything other than the demo PR; “Update branch” on the demo PR; re-running a failed job; running <code>rollback-backend.yml</code> or any <code>workflow_dispatch</code>; opening Settings → Secrets and variables or the ruleset editor; pushing to <code>dev</code>; any AWS console or SSM session on the shared screen. If the PR is not merged by 16:50, leave it open and merge after 17:15, so a deploy never lands during the system demo.')}
${box('tip', 'Tip', 'Narrate results, not clicks: “checks green in four minutes fifty”, not “now I click the checks tab”. No troubleshooting on air.')}
${qaTable(4)}
</section>`;

// ---------- segment 5: system demo ----------
const demoSteps = C.DEMOS[21];

const env = n => n <= 4 ? 'live on dev' : n === 5 ? 'dev outcome; races proven by tests' : n === 6 ? 'SWE5001 prod (ECS) and local, labelled' : 'live on dev (segment 4)';
const seg5 = seg(5);
const demoHtml = `<section class="chap"><h1>5 ${esc(seg5.name)} (${seg5.talk}+${seg5.qa} minutes)</h1>
<div class="seghead"><table class="meta"><tr><td>Clock</td><td>16:53:00 – 17:07:00 talk and demo · 17:07–17:10 buffer for questions asked during the talk · 17:10–17:15 Q&amp;A</td></tr><tr><td>Owner</td><td>${esc(seg5.owner)}; Lim closes and chairs the Q&amp;A</td></tr><tr><td>Slides</td><td>21, 22; backups B4, B15 and B16 may be used on air</td></tr></table></div>
${reqBox(seg5)}
<h2>How the demo meets 5a and 5b</h2>
${tbl(['Step', 'Shows', 'Quality attribute · item', 'Environment'], demoSteps.slice(0, 7).map(d => [d.n, esc(d.t), d.proves.split(' · ').slice(0, 2).join(' · '), env(d.n)]))}
<p>Use cases (5a) are steps 1–4 on the deployed dev site. The quality attributes named in 5b are covered as the guideline asks: <b>scalability</b> by SWE5001 auto-scaling on ECS (step 6, labelled; dev has none by design), <b>performance</b> by scripted load tests (step 6: k6 on ECS, the Node workload locally), <b>maintainability</b> by the pipeline run live in segment 4 (step 7). Consistency and reliability, central to BlocBuy’s money flow, are steps 2, 4 and 5 (slides 9, 10). Item 5c is the 25 Oct recording, one clip per step.</p>
<h2>Principle: start the slow part before the talk</h2>
<p>A bloc cannot expire faster than its team lifetime, and the minimum lifetime is the 2,400-second quote window (<code>ProductTeamTerms.MIN_LIFETIME_SECONDS</code>), so <code>US34-04</code> lives 40 minutes. The expiry sweep runs every minute and the refund worker every 30 s (one obligation per run), so the refund shows ≈ 1–2 minutes after the deadline. Started at <b>16:05</b>, the bloc expires ≈ 16:45 and its refund is visible well before step 4 at 16:58:50. Deadlines are never edited on shared dev (<code>backend/docs/payment/stripe-rollout-runbook.md</code>): the timer, not a manual call, is the evidence.</p>
<h2>Decision: live or recorded payment steps (go/no-go, Fri 23 Oct, 18:00)</h2>
${tbl(['Condition (Yorik decides with Lim; posted in #464)', 'GO: live on dev', 'NO-GO: recorded or local'], [
  ['#517 fixed: a new START on dev reaches paid within the receipt’s one-minute refresh, and the bloc shows under My blocs → Active', 'Steps 1–3 live; fallback = 25 Oct clips', 'Steps 1–2 from local evidence (B15, 20 Sep), said as “verified locally, not yet on dev”; step 3 live on existing data'],
  ['#223 done: Checkout and scheduling on; a <code>US34-04</code> bloc started on 24 Oct expired within one sweep and its refund reached succeeded on the receipt', 'Step 4 live with the 16:05 bloc', 'Step 4 from B15 (local); slide 21 footer changes (open item 23)'],
  ['Full dry run on dev on Sat 24 Oct passes in order, timed', 'Rehearse at the real clock times on 25 Oct', 'Only the NO-GO path is rehearsed'],
  ['Late fix (24–25 Oct)', 'GO only if the dry run and the recordings are done by Sun 25 Oct 12:00', 'After 12:00 on 25 Oct the NO-GO stands; no switch is changed on dev on 26 Oct'],
])}
${box('warn', 'Watch out', mh('No refund has ever been proven on dev (19 Oct): every dev payment and refund in this run book is conditional on the dry run. A GO for steps 1–3 with a NO-GO for step 4 is allowed; then only step 4 uses B15. {{CONFIRM:go/no-go result of 23 Oct and the slide 21 footer (open items 1, 23)}}'))}
<h2>Demo preconditions (checked at 15:30 by Eric and Yorik)</h2>
<ul><li>Health <code>UP</code> (A.1); latest <code>ci-backend</code> and <code>ci-frontend</code> runs on <code>dev</code> green; merge freeze in force.</li>
<li>Checkout and scheduling enabled on dev (#223) ${m('{{VERIFY:how to read the flags on the day without SSM: the capabilities endpoint (A.4) or the operator’s day-log entry}}')}; listings converged: <code>scripts/seed-dev-demo-listings.sh --check</code> passes for all four <code>US34-*</code> listings (A.2, Yorik, admin token).</li>
<li>Two Google buyer accounts signed in on Eric’s machine in two Chrome profiles, “Leader” (account A) and “Joiner” (account B), and the same two on Yorik’s machine as standby (sessions are concurrent and last 24 h). Neither is a member of an open <code>US34-02</code> bloc. ${m('{{CONFIRM:which two accounts and the display names shown on screen (open item 22)}}')}</li>
<li>The 16:05 <code>US34-04</code> bloc open at 1 of 2 (<code>EXPIRY OK</code> at 16:10); its receipt bookmarked in the Leader window, tab 4. Nobody else has its invite link.</li>
<li>Fallback clips 1–8 from the 25 Oct recording on Eric’s machine, tested with Zoom “share sound” off; a copy on Yorik’s. ${m('{{FILL:clip folder link (open item 20)}}')}</li>
<li>Do Not Disturb on; one monitor shared; browser zoom 125%; no password manager, email or bookmarks bar visible. This run book on a second device, not on the shared screen.</li></ul>
<h2>Screen layout (Eric shares one screen)</h2>
${tbl(['Window · tab', 'Content', 'Prepared state'], [
  ['Leader · 1', 'Deck <code>swe5006-presentation.html#21</code>', 'Slide 21; backups one key away (B); slide 22 at the end'],
  ['Leader · 2', `Listing <code>US34-02</code> on <code>${DEV}</code>`, 'Signed in as account A; product page loaded'],
  ['Leader · 3', '<code>/account</code> (account A)', 'Loaded; the Refunded chip shows the 16:05 bloc from ≈ 16:46'],
  ['Leader · 4', 'Receipt <code>/payments/{paymentId}</code> of the 16:05 payment', 'Bookmarked at 16:10; reload before step 4'],
  ['Leader · 5', 'Pan’s merged PR and the changed dev page', 'Opened after 16:52'],
  ['Joiner · 1', 'Empty tab for the invite link', 'Signed in as account B'],
  ['Joiner · 2', '<code>/account</code> (account B)', 'Loaded'],
  ['Player', 'Clips 1–8 (25 Oct)', 'Paused at the start of clip 1'],
])}
<h2>Opening (slide 21)</h2>
${slideCard(slide(21))}
<h2>Step by step</h2>
${demoSteps.map(demoCard).join('')}
${box('warn', 'Never live', 'Editing any deadline, price or listing on shared dev; any SSM command, port-forwarding session, database client or AWS console on the shared screen; opening the Stripe dashboard (keys and webhook secrets live there); notifications on dev (off by decision, ADR 0007: say so if asked); racing two buyers or replaying a webhook on dev; sharing the 16:05 bloc’s invite link; a second payment to “try again” on air.')}
${box('tip', 'Tip', 'Narrate what changed, not what you click: “one of three … two of three … refunded”. If a live step runs 20 seconds past its expectation, go to the clip; no troubleshooting on air.')}
<div style="break-inside:avoid"><h2>Closing slide</h2>
${slideCard(slide(22))}</div>
${qaTable(5)}
</section>`;

// ---------- contingencies ----------
const contingencies = `<section class="chap"><h1>6 Contingencies and time recovery</h1>
<h2>If something fails</h2>
${tbl(['Situation', 'Detected by', 'Response and words', 'Who'], [
  ['Presenter’s audio or connection drops', 'Silence &gt; 5 s', 'The backup continues from the slide card: Lim ↔ Yorik (§1, slide 12); Eric covers slides 8, 10 and 11, Lim covers 7 and 9 (§2); Eric ↔ Liu Jin (§3); Lim ↔ Pan (§4: slides only, the live part goes to the recording); Yorik ↔ Eric (§5: Yorik shares his standby windows).', 'Backup'],
  ['Lim cannot share', 'Black screen', 'Eric shares the HTML deck (his tab 1) for segments 1–3; the PDF as a last resort.', 'Eric'],
  ['Dev site down or unhealthy at 15:30', 'A.1', 'No changes to dev after 15:30. Segments 4 and 5 run from the 25 Oct recordings; say so on slides 6 and 21. The operator investigates only after 17:15.', 'Pan, Yorik'],
  ['16:05 bloc not confirmed', '<code>EXPIRY LATE</code> at 16:10', 'Retry the START once by 16:15 (expires ≈ 16:55, refund ≈ 16:57). Not paid by 16:15: step 4 uses clip 4 or B15.', 'Yorik'],
  ['Refund not visible', '<code>REFUND LATE</code> at 16:50', 'Reload the receipt at 16:55 and before step 4. Still missing: clip 4, or B15 with “verified locally, not yet on dev”.', 'Yorik, Eric'],
  ['Checkout does not return, or the receipt keeps waiting (#517 symptom)', 'Step 1 or 2', '<code>CLIP 1</code> / <code>CLIP 2</code>; one sentence pointing to slide 6. Never retry a payment on air.', 'Eric'],
  ['Demo PR checks red or still running at 16:48', 'Tab 1', 'Say what the gate caught; <code>CLIP P2</code> onwards; merge after 17:15 only.', 'Pan'],
  ['GitHub slow or logged out', 'Tabs 1–4', 'Recording for the remaining P-steps.', 'Pan'],
  ['<b>Plan C:</b> a step fails live and there is no recording for it', '—', 'Show the local evidence for that step (B15 for payment and refund, the test names in step 5, B16 for load) and say: <i>“verified locally, not yet on dev.”</i> Never present a local result as a dev one.', 'Eric, Yorik'],
  ['A lecturer asks for something not prepared', '—', 'Offer to show it after the talk or point to the report; do not improvise on dev. If a backup covers it, open it (B, then the row).', 'Lim'],
])}
<h2>If the clock slips</h2>
<p>The 3-minute buffer absorbs questions during the talk. When a segment starts more than 30 s late (<code>BEHIND n</code>), cut in this order; never drop a guideline item, only depth.</p>
${tbl(['Segment', 'Cut first', 'Saves'], [
  ['1', 'The trims on slides 2–6 (each card names one)', '≈ 40 s'],
  ['2', 'The trims on slides 7–12 (one sentence each)', '≈ 45 s'],
  ['3', 'Slide 13: drop the PaidGroupDealTest example; slide 14: drop the ArchUnit example', '≈ 20 s'],
  ['4', 'Slides: the trims on 15–19. Live: skip P5 (rollback) and shorten P4 to one sentence on the recorded backend run', '≈ 90 s'],
  ['5', 'Step 3: skip the chip switching; step 5: first and last sentences only; step 7 folded into step 8', '≈ 120 s'],
])}
</section>`;

// ---------- timeline ----------
const timeline = `<section class="chap"><h1>7 Timeline before, on and after the day</h1>
<h2>Before the day</h2>
${tbl(['Date', 'Do', 'Owner'], [
  ['Mon 19 Oct', 'Draft deck, script and both run books (this document). <b>Done.</b>', 'Lim, Yorik'],
  ['Wed 21 Oct', 'SWE5001 presentation (16:15–16:55). Keep its scale-out and load numbers, or the 19 Oct session results, for B16 (open item 21).', 'Pan, Lim'],
  ['Fri 23 Oct', '<b>Slides due</b> (#464). Each owner reviews their slides, cards and Q&amp;A rows; the Sprint 4 review and retro feed slides 4–6. <b>18:00 go/no-go</b> for the live payment steps (§5 decision table), posted in #464.', 'All; Yorik decides with Lim'],
  ['Sat 24 Oct', '#223 and #517 fixed. <b>Full dry run on dev:</b> START on <code>US34-04</code> and let it expire and refund; START and JOIN on <code>US34-02</code> with both accounts; /account; payment and refund ids in the day log (no secrets). Pan rehearses P1–P3 on a throwaway PR and closes it unmerged.', 'Yorik (payments), Eric, Pan'],
  ['Sun 25 Oct, by 12:00', '<b>Recordings</b> of both demos on dev, 1080p, no secrets on screen: start a <code>US34-04</code> bloc at ≈ 10:00, record steps 1–8 from ≈ 10:45; Pan records P1–P7 with a real one-line change (not Monday’s). Cut one clip per step.', 'Yorik, Eric (system); Pan (pipeline)'],
  ['Sun 25 Oct, afternoon', '<b>Timed rehearsal</b> in Zoom at the real clock: expiry bloc at 16:05, talk at 16:30, both share switches, cue words, and lecturer questions injected during the talk. Every marker resolved after this rehearsal; rebuild deck and script.', 'All; Lim times'],
])}
<h2>Presentation day (Mon 26 Oct)</h2>
${tbl(['Time (SGT)', 'Do', 'Owner'], [
  ['≤ 12:00', 'A.1 health and runs; A.2 listings check; A.3 AWS read-only checks. Pan rebases the demo branch on <code>dev</code> if needed and pushes it (A.5).', 'Yorik, Pan'],
  ['14:30', 'Tech check in a test Zoom: mic, camera, sharing one window (Lim, Pan) or one screen (Eric), Do Not Disturb, wired network if possible.', 'All'],
  ['15:00', 'Merge freeze on <code>dev</code> until 17:15, announced in the team chat.', 'Lim'],
  ['15:30', '§4 and §5 preconditions; demo windows and tabs prepared; clips loaded.', 'Pan; Eric, Yorik'],
  ['16:00', 'Join the meeting as “Team 8 – Name”; backchannel open.', 'All'],
  ['≈ 16:05', 'Start the expiry bloc on <code>US34-04</code> as account A; pay; bookmark the receipt. Latest 16:15.', 'Yorik'],
  ['16:10', '<code>EXPIRY OK</code> / <code>EXPIRY LATE</code>.', 'Yorik'],
  ['16:30', 'Start; follow the run sheet.', 'Lim'],
  ['16:41', '<code>PR OPEN</code>.', 'Pan'],
  ['16:46:15', 'Share switch Lim → Pan.', 'Lim, Pan'],
  ['16:50', '<code>REFUND OK</code> / <code>REFUND LATE</code>.', 'Yorik'],
  ['16:53:00', 'Share switch Pan → Eric.', 'Pan, Eric'],
  ['17:07 / 17:10 / 17:15', 'Slide 22; parked questions; Q&amp;A; end and thank the lecturers.', 'Lim'],
])}
<h2>After the presentation</h2>
<ul><li>The same evening: every lecturer remark goes into #463, each to become a report change or a justified non-change; tick the #464 acceptance criteria that are met.</li><li>Day log under <code>docs/agile/sprint-04/log/</code>: the dev payment and refund ids shown, the demo PR and its run links (no secrets).</li><li>The <code>US34-02</code> bloc started on air stays open for 24 h, then expires and refunds both members on its own: check it on 27 Oct as further evidence for #223.</li><li>Lift the merge freeze; Pan merges the demo PR after 17:15 if it was not merged on air.</li></ul>
</section>`;

// ---------- open items (rendered after every counted chapter) ----------
const openItems = () => `<section class="chap"><h1>8 Open items before the 25 Oct rehearsal</h1>
<p>There are ${markers.FILL} <span class="mk mk-fill">[FILL]</span>, ${markers.VERIFY} <span class="mk mk-verify">[VERIFY]</span> and ${markers.CONFIRM} <span class="mk mk-confirm">[CONFIRM]</span> markers in this script (slide cards, Q&amp;A tables and the run books; the deck shows the slide markers too). <b>FILL</b> is a value that does not exist yet, so never present a guess; <b>VERIFY</b> is a claim to check against the repository, the report or run logs; <b>CONFIRM</b> needs team agreement. All must be gone after the 25 Oct rehearsal.</p>
${tbl(['#', 'Item', 'Owner', 'Blocks slide'], C.OPEN_ITEMS.map(r => r.map(esc)))}
</section>`;

// ---------- appendix ----------
const appendix = `<section class="chap"><h1>A Appendix — check and demo commands</h1>
<p>Every command is read-only unless marked. Run them off the shared screen, from a checkout of <code>dev</code> at the repository root. Never paste tokens, cookie values, emails or AWS output with account details into chat or onto the shared screen.</p>
<h3>A.1 Health and recent runs (anyone; ≤ 12:00 and at 15:30)</h3>${pre(`curl -s ${DEV}/api/actuator/health          # expect "status":"UP"
gh run list --repo ${REPO} --workflow ci-frontend.yml --branch dev --limit 3
gh run list --repo ${REPO} --workflow ci-backend.yml --branch dev --limit 3
gh pr list --repo ${REPO} --base dev --state open      # what could still merge before the freeze`)}
<h3>A.2 Listings check (Yorik; ≤ 12:00; the admin's blocbuy-token, typed at the prompt)</h3>${pre(`read -rs -p 'blocbuy-token: ' BLOCBUY_TOKEN; echo; export BLOCBUY_TOKEN
scripts/seed-dev-demo-listings.sh --check   # one quote per US34-* listing; no admin writes; non-zero on a mismatch
unset BLOCBUY_TOKEN`)}
<p>A policy with fewer than 7 days left needs a normal seed run (audited admin API writes) before 26 Oct; runbook <code>docs/deploy/backend-dev-api-agent-verification.md</code>. The admin grant (<code>scripts/grant-dev-admin.sql</code> over an SSM tunnel) is once per database and is done before the day by the operator, never on the day.</p>
<h3>A.3 AWS read-only checks (Pan; ≤ 12:00; profile <code>blocbuy</code>, Region <code>ap-southeast-1</code>)</h3>${pre(`aws ec2 describe-instance-status --instance-ids i-0852b6c3475b19f05 --profile blocbuy --region ap-southeast-1
aws ssm describe-instance-information --filters Key=InstanceIds,Values=i-0852b6c3475b19f05 --profile blocbuy --region ap-southeast-1
aws cloudfront get-distribution --id E3U5W7UWDL8LRV --query 'Distribution.Status' --profile blocbuy
aws elbv2 describe-target-health --target-group-arn <dev target group arn> --profile blocbuy --region ap-southeast-1`)}
<h3>A.4 Payment capability (Yorik; at 15:30)</h3>${pre(`curl -s ${DEV}/api/v1/payments/capabilities`)}<p>${m('{{VERIFY:whether this endpoint answers without a session and reports Checkout as enabled; otherwise open it in the Leader browser}}')}</p>
<h3>A.5 Demo pull request (Pan)</h3><p><b>Before the day (25 Oct evening); writes to a feature branch only:</b></p>${pre(`git fetch origin
git switch -c feature/frontend-swe5006-live-demo origin/dev
# edit one line of user-facing copy under frontend/src (open item 15)
git commit -am "feat(frontend): <the copy change> (#464)"
git push -u origin feature/frontend-swe5006-live-demo`)}
<p><b>26 Oct, ≤ 12:00</b>, only if <code>dev</code> moved since: <code>git fetch origin</code>, then <code>git rebase origin/dev</code>, then <code>git push --force-with-lease</code>. Never after 15:00.</p>
<p><b>16:41, during slide 14 (Pan, muted):</b></p>${pre(`gh pr create --repo ${REPO} --base dev --head feature/frontend-swe5006-live-demo \\
  --title "feat(frontend): <the copy change> (#464)" --body-file <prepared body file>`)}
<p><b>≈ 16:47:15, Lim on his own machine:</b> <code>gh pr review &lt;number&gt; --repo ${REPO} --approve</code> (or the GitHub UI). <b>16:48, Pan:</b> Squash and merge in the UI, on the shared screen.</p>
<h3>A.6 After the talk (Yorik, Pan)</h3>${pre(`gh run list --repo ${REPO} --workflow ci-frontend.yml --branch dev --limit 1   # the demo delivery run, for the day log
gh pr view <number> --repo ${REPO} --json url,mergedAt,mergeCommit`)}
</section>
<section class="chap"><h1>B Appendix — backup slides (Q&amp;A only)</h1>
<p>Backups follow slide 22 in the HTML deck and the PDF. In the deck press <b>B</b> for the index (BX) and click a row, or open <code>#B15</code> directly; slide 22 stays up otherwise. B4, B15 and B16 may also be used on air in §5.</p>
${tbl(['ID', 'Slide', 'Answers'], C.BACKUPS.map(b => b.map(esc)))}
</section>`;

// ---------- cover ----------
const coverHtml = cover({
  kicker: 'SWE5006 Practice Project · Graduate Certificate in Designing Modern Software Systems',
  title: 'Speaker Script and Live Demo Run Book',
  subtitle: 'BlocBuy — Team 8 · Monday 26 October 2026, 16:30–17:15 SGT (Zoom)',
  meta: [
    ['Responsible', 'Yu Qianshuo (Yorik), presentation lead and storyline (#464); Lim Jiechao, slide master.'],
    ['Companion files', '<code>swe5006-presentation.html</code> — the deck (22 slides + backups BX, B1–B16; open in Chrome or Edge; <b>S</b> notes window with this script and a timer, <b>T</b> start timer, <b>O</b> overview, <b>F</b> fullscreen, <b>B</b> backup index) · <code>swe5006-presentation.pdf</code> — the same deck as a PDF for submission (39 pages: 22 main, then BX and B1–B16).'],
    ['Single source', '<code>src/content.js</code> holds wording, timings, cues, Q&amp;A, backups and open items; <code>src/slides-main.js</code> and <code>src/slides-backup.js</code> hold the slide layouts and <code>src/script.js</code> this document; shared UML diagrams come from <code>docs/presentation-kit/diagrams/</code>. Run <code>npm ci</code> once in <code>docs/presentation-kit/</code>, then <code>npm --prefix docs/swe5006/presentation run all</code> from the repository root.'],
    ['Facts as of', '19 Oct 2026. Dev health <code>UP</code>; #223 and #517 open, so no payment has been confirmed and no refund proven on dev yet. Everything that depends on the 23 Oct go/no-go or the 24 Oct dry run is marked (§8).'],
  ],
  after: `${box('', 'How to use this document', `<ul><li><b>Speakers:</b> read your segment (§1–§4) aloud against a timer at least twice. Each slide card shows the slot and the estimated speaking time at 130 words a minute; cards marked <span class="over">over by N s</span> need a faster read or the listed trim. Rephrase in your own words; never read the slide.</li><li><b>Pipeline demo (Pan, Lim):</b> §4 after slide 20: preconditions, screen layout, then steps P1–P7 with what to operate, say and expect, and when to switch to the recording.</li><li><b>System demo (Eric, Yorik):</b> §5: the go/no-go table, the 16:05 expiry bloc, preconditions, screen layout, steps 1–8. §6 adds Plan C and time recovery.</li><li><b>Lead and slide master:</b> the run sheet below (interruption policy, background actions, coverage, cue words), §6, the day timeline in §7 and the open items in §8.</li></ul>`)}
${box('', 'Markers', `${md('{{FILL:}}')} value that does not exist yet; never present a guess · ${md('{{VERIFY:}}')} claim to check against the repository, report or run logs · ${md('{{CONFIRM:}}')} needs team agreement. They are visible on the slides on purpose and must all be gone after the 25 Oct rehearsal.`)}
<p class="small" style="margin-top:8pt">Structure follows the lecturer’s presentation sequence: one chapter per segment (1 Project introduction and management, 2 Analysis and design, 3 Testing, 4 CI/CD pipeline demo, 5 System demo), each opening with the guideline’s own items, copied verbatim.</p>`,
});

// Segment and demo chapters render before openItems(), so its counts include their markers.
const body = segs13 + seg4Html + demoHtml + contingencies + timeline;
module.exports = coverHtml + '\n' + runSheetHtml + body + openItems() + appendix;
