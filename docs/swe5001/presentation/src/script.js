// SWE5001 speaker script and demo run book: the chapters of dist/swe5001-presentation-script.html.
// The kit (docs/presentation-kit/script/build.js) supplies the helpers, wraps this body and writes dist/.
const { C, md, esc, markers, box, tbl, pre, reqBox, slideCard, qaTable, segment, cover, runSheet } = require('../../../presentation-kit/script/build').helpers();

const coverage = [
  ['1a', 'Explain the context and the business problem that the solution as a whole is trying to achieve.', '1, 2', 'Lim'],
  ['1b', 'Highlight the platform-thinking aspects of your solution.', '3', 'Lim'],
  ['1c', 'Explain the scope of the project.', '4', 'Lim'],
  ['2a', 'Explain the current status of the project including outstanding issues.', '5', 'Lim'],
  ['2b', 'Describe the actual project milestones and the rough total effort expended by each team member.', '6', 'Lim'],
  ['3a', 'Highlight the key architectural decisions that influence the logical architecture.', '7, 8', 'Eric'],
  ['3b', 'Explain the logical components of the solution by showing a logical detail deployment diagram and other supporting views.', '9 (UML logical deployment view)', 'Eric'],
  ['3c', 'Where relevant, highlight the elements of Domain Driven Design … how the platform is designed to interact with the producers and consumers.', '10, 11', 'Eric'],
  ['4a', 'Highlight the key architectural decisions that influence the physical architecture.', '12', 'Liu Jin'],
  ['4b', 'Describe the physical architecture that includes the network, nodes, technologies and cloud services that are used in the solution.', '13 (UML deployment), 14', 'Liu Jin'],
  ['4c', 'Describe the persistence design of the solution.', '15', 'Liu Jin'],
  ['4d', 'Describe the detailed design of an architecturally representative user case or user story.', '16 (UML sequence), 17', 'Yorik'],
  ['5a', 'Describe the strategy to manage the source code and artifacts.', '18', 'Pan'],
  ['5b', 'Describe the DevOps pipeline and procedures.', '19, 20, 22 (security controls)', 'Pan'],
  ['5c', 'Highlight any technical findings and issues encountered.', '21', 'Pan'],
  ['6a', 'Demonstrate some of the significant use cases of the solution.', '23 + demo step 1', 'Eric'],
  ['6b', 'Demonstrate how the solution deals with some of the important quality attributes …', '24 + demo steps 2–6', 'Lim'],
];
const briefing = [
  ['Q1 pain points', '2'], ['Q2 platform, common services, reusable frameworks', '3, 9, 11; B5, B10'], ['Q3 benefit of building on the platform, demonstrated', '11 (measured reuse)'],
  ['Q4 scale and proof', '14 (capacity statement), demo step 2; B12'], ['Q5 demonstrate technical and non-technical benefits', '12 (measured cost), 14, 19, demo'],
  ['Q6 value of cloud native, demonstrated', '12 (US$0.49 measured), 13, 20'], ['Q7 SDLC automation reduces effort', '19 (5,252 runs), 20'], ['Q8 effort within guideline', '6'],
  ['Success criterion: seed, producers, consumers', '3'], ['Success criterion: more seeds in future', '3 (future seeds line)'], ['Success criterion: analytics', 'B11 (Q&amp;A)'], ['Module: robust API access; security controls', '11, B10; 22'],
];
const cueWords = [['1 MIN', 'Speaker has one minute of talk left in the segment', 'Lim'], ['TIME', 'Stop talking; move to questions or the hand-over', 'Lim'], ['K6 GO / K6 UP', 'Start the V3 load / load is running and healthy', 'Lim / Yorik'], ['SCALED n', 'Commerce reached n running tasks (from the dashboard)', 'Eric'], ['PLAN A / PLAN B', 'Burst decision at 16:40 (§6)', 'Liu Jin'], ['ID BACK', 'identity-svc is running again after the outage drill', 'Yorik'], ['CLIP n', 'Live step n failed; Liu Jin is ready to share the recording for step n', 'Eric → Liu Jin'], ['SHARE', 'Next person: start screen sharing now', 'Lim']];

// ---- demo section ----
const demoSteps = [
  { n: 1, t: 'Buy as a customer, across three services', time: '16:45:15 – 16:46:15', proves: 'Functionality · guideline <b>6a</b> · Eric', op: ['Tab 2: product list with team prices and countdowns (commerce).', 'Point at the signed-in user in the header (identity, Google sign-in).', 'Tab 3: admin product list (commerce reads the role from the shared session).', "Tab 4: refresh the buyer's notifications (notification service)."], say: "This is the buyer view: team prices and live countdowns, served by commerce. I'm signed in with Google through identity. This admin page works because commerce reads my role from the shared session, without calling identity. And these notifications were written by the notification service from events it received. Three services, one session.", exp: 'All four views load; at least one notification is listed.', fb: 'Screenshots from 19 Oct (fallback/1-use-cases).' },
  { n: 2, t: 'Auto-scaling under a load test', time: '16:46:15 – 16:47:45', proves: 'Scalability, performance · guideline <b>6b</b> · Eric (Yorik mirrors k6)', op: ['Tab 5: CloudWatch dashboard, Services row: commerce running tasks.', 'Edge row: request count and p95 response time.', 'Terminal: k6 live summary (requests per second, p95, error rate).'], say: 'Since 16:28 a k6 script has been ramping up browse traffic to three times our measured baseline, 600 requests a second. Commerce has scaled from two tasks to the number you see here. Response times rose while new tasks started and came back down once they joined, and the error rate stayed under one percent. (Read the task count, p95 and error rate off the screen.)', exp: 'Commerce running tasks above 2 (about 3–4 at 600 requests a second); k6 error rate below 1%.', fb: 'Recording of the 19 Oct load test (fallback/2-v3.mp4).' },
  { n: 3, t: 'Stop a server, nothing breaks', time: '16:47:45 – 16:48:30', proves: 'Availability · guideline <b>6b</b> · Yorik operates; Eric narrates', op: ['Yorik runs command A.3 (stop one commerce task).', 'Tab 6: ECS console, commerce tasks: one goes STOPPED, a replacement starts.', 'Terminal: k6 error counter.'], say: "Yorik has just stopped one commerce task in the middle of the load test. ECS has already started a replacement, and the load balancer routes around the missing one, so the error rate doesn't move. We'll check the replacement at the end.", exp: 'Replacement running within about 2 minutes; no visible error spike.', fb: 'Recording of the 19 Oct drill (fallback/3-v5.mp4).' },
  { n: 4, t: 'Identity goes down, payments stay safe', time: '16:48:30 – 16:50:00', proves: 'Consistency, security · guideline <b>6b</b> · Yorik operates; Eric narrates', op: ['Yorik runs command A.4 (identity desired count 0).', 'Tab 2: browsing still works.', "Tab 3: an admin change now fails with a server error, not 'user inactive'.", 'Yorik restores identity to 1 immediately.'], say: 'Now identity is down. Browsing still works, because it only needs commerce. But anything that needs to know the user is active now fails safely with a server error, not with "user inactive". That difference matters: for a Stripe payment, a server error rolls everything back and Stripe retries later, so no paying buyer is wrongly refunded. Yorik is bringing identity back now.', exp: 'Browse works; admin change returns 500; identity back to 1 task before 16:52:30.', fb: 'Recording of the 19 Oct identity drill (fallback/4-identity.mp4).' },
  { n: 5, t: 'Event backlog and the dead-letter queue', time: '16:50:00 – 16:51:30', proves: 'Scalability, reliability · guideline <b>6b</b> · Eric', op: ['Tab 5, Messaging row: published events, notification queue backlog; Services row: notification tasks.', 'Tab 7: dead-letter queue with 1 message; its alarm in ALARM.'], say: "Plan A: Here's our 19 October run: 2,000 teams expired at once, the queue backed up, notification scaled out and drained it to zero. And this is live: the dead-letter queue holds one broken message we sent this afternoon. It was tried five times, moved aside, and the alarm fired, while every other message kept flowing. Plan B: At 16:40 we expired 2,000 teams. You can see the spike, the backlog, notification scaling out and the queue draining.", exp: 'Dead-letter queue shows about 1 message; alarm in ALARM; main queue back to 0.', fb: 'Recording of the 19 Oct runs (fallback/5-v4-v7.mp4).' },
  { n: 6, t: 'The pipeline that deployed it', time: '16:51:30 – 16:52:30', proves: 'Maintainability · guideline <b>6b</b> · Pan narrates; Eric clicks', op: ['Tab 8: the 19 Oct promotion pull request: required checks and approval.', 'Open its deploy run: build → create → release → destroy → evidence, with durations.', 'Open the evidence artifact list. Never click Merge.'], say: "This is the promotion that put today's code on AWS: three required checks and one approval. The run built the images, created the environment from scratch, tested it, released it and destroyed it, and here's the evidence it saved. This morning's environment was created from exactly these release images. (Read the total run time off the screen.)", exp: 'Checks green; run green including destroy; artifact present.', fb: 'Screenshots of the pull request and run (fallback/6-pipeline).' },
  { n: 7, t: 'Close the loop', time: '16:52:30 – 16:53:00', proves: 'Availability · guideline <b>6b</b> · Eric, then Lim', op: ['Tab 6: commerce back to full strength; identity 1/1 running.', 'Tab 1: thank-you slide (slide 24).'], say: "The replacement commerce task is healthy and identity is back. That's our demo.", exp: 'All services at their desired count.', fb: "If identity isn't back yet, say so in one sentence and move on." },
];
const demoCard = d => `<div class="card demo"><div class="hd"><span>Step ${d.n} &nbsp; ${esc(d.t)}</span><small>${d.time}</small></div><div class="bd">
 <div class="row"><span class="lab">Proves</span><span>${d.proves}</span></div>
 <div class="row"><span class="lab cue">Operate</span><ol style="margin:0;padding-left:14pt">${d.op.map(o => `<li>${esc(o)}</li>`).join('')}</ol></div>
 <div class="row"><span class="lab say">Say</span><span class="say">${esc(d.say)}</span></div>
 <div class="row"><span class="lab">Expect</span><span>${esc(d.exp)}</span></div>
 <div class="row"><span class="lab fb">Fallback</span><span>${esc(d.fb)} If the live view is not as expected within 20 seconds, Eric posts <code>CLIP ${d.n}</code> and says: <i>“Here is the same step from our 19 October run.”</i> If there is no 19 Oct recording for this step, use Plan C (§7).</span></div></div></div>`;

const demo = `<section class="chap"><h1>6 Demo (8+2 minutes)</h1>
<div class="seghead"><table class="meta"><tr><td>Clock</td><td>16:45:00 – 16:55:00 (8 min talk + 2 min Q&amp;A; talk ends 16:53:00)</td></tr><tr><td>Owner</td><td>Eric and Yorik (drive), Pan (pipeline)</td></tr><tr><td>Slides</td><td>23, 24</td></tr></table></div>
${reqBox(C.SEGMENTS[5])}
<h2>How the demo meets 6a and 6b</h2>
${tbl(['Step', 'Shows', 'Quality attribute (guideline 6b wording)', 'Evidence on screen'], demoSteps.slice(0, 6).map(d => [d.n, esc(d.t), d.proves.split(' · ')[0], esc(d.exp)]))}
<p>Scalability is shown twice (HTTP auto-scaling in step 2 and queue scaling in step 5), performance through the scripted k6 test in step 2, and maintainability through the pipeline in step 6, as the guideline asks. Steps 3–4 add availability and consistency, which are central to the decisions on slide 8.</p>
<h2>Principle: start everything slow before the demo</h2>
<p>The 8 minutes only <b>show results</b> plus two fast live actions (task stop, identity outage). The k6 V3 run starts at 16:28 and follows the reviewed ramp: 20% → 100% of the 200 RPS baseline over 5 minutes (16:28–16:33), up to 300% = 600 RPS over 10 minutes (16:33–16:43), hold 10 minutes (16:43–16:53), ramp down 5 minutes. At 600 RPS and a 200 RPS-per-task target, commerce should settle at 3–4 tasks, so scale-out events fall between about 16:35 and 16:45 — before step 2.</p>
<h2>Decision: Plan A or Plan B for the expiry burst (decide at the 20 Oct rehearsal)</h2>
${tbl(['', 'Plan A — default', 'Plan B — live burst'], [
  ['Poison message (V7)', 'Injected at 15:30; in the DLQ with the alarm in ALARM by about 15:40', 'Injected at about 16:41, right after the V4 seed is confirmed; reaches the DLQ after 5 receives × 60 s visibility, about 16:47; alarm by about 16:49'],
  ['Queue scaling (V4)', 'Shown from the 19 Oct V4 recording and its dashboard capture', 'Live: Liu Jin runs <code>v4-burst.sh</code> at 16:40 (2,000 teams due 60 s later)'],
  ['Preconditions', 'None beyond the normal session', 'A separately reviewed runtime apply with <code>v4_demo_enabled=true</code> before 12:00 (env-up does not set it); one stable notification task; quiet main queue and DLQ at 16:40; new run label'],
  ['Risk', 'Low', 'Tight timing; backlog may drain before scaling reacts (SCALING.md warns of this); fallback is the same recording as Plan A'],
])}
${box('warn', 'Watch out', 'Why the 14 Oct guideline timing does not work: <code>v4-burst.sh</code> refuses to seed unless the notification queue <b>and the DLQ</b> are quiet, so a poison message injected at 15:30 blocks a 16:40 burst. Without <code>v4_demo_enabled=true</code> the expiry sweep handles 100 teams a minute, which drains without any scale-out. Liu Jin posts <code>PLAN A</code> or <code>PLAN B</code> in the backchannel at 16:40.')}
<h2>Demo preconditions (checked at 15:30 by Liu Jin and Eric)</h2>
<ul><li>Environment up from release digests, smoke green, health <code>UP</code>; commerce 2/2, identity 1/1, notification 1/1 running.</li><li>Demo admin granted and signed in again; startable listings seeded (A.5).</li><li>Scaling targets <b>not suspended</b>: commerce 2–6, notification 1–4 (read back; see finding 3 on slide 21).</li><li>Plan A: DLQ shows 1 message, alarm <code>blocbuy-swe5001-notification-dlq-not-empty</code> in ALARM. Plan B: main queue and DLQ empty.</li><li>Notifications JSON for the demo buyer shows at least one entry (Plan A: from the morning smoke or a seeded expiry).</li><li>Load machine: Docker running, k6 image pulled, <code>BASE_URL</code>, digest and run label exported (A.7); a dry <code>http-preflight.sh</code> passes.</li><li>Fallback clips from 19 Oct open in a player on Liu Jin's machine, one per step, tested with Zoom “share sound” off. ${md('{{FILL:list which steps have no 19 Oct recording; those use Plan C}}')}</li></ul>
<h2>Screen layout (Eric's demo window)</h2>
<p>One browser window, tabs left to right, plus one terminal. Share the window, not the desktop; notifications off; browser zoom 125%.</p>
${tbl(['Tab', 'Content', 'Prepared state'], [
  ['1', 'HTML deck opened at slide 23 (<code>swe5001-presentation.html#23</code>)', 'Also used for slide 24 at the end; backups B1–B15 one key away (B)'],
  ['2', 'SPA <code>https://d3lvq02o2e7tcn.cloudfront.net</code>', 'Product list loaded; signed in as the demo admin (Google)'],
  ['3', 'Admin product list in the SPA', 'Admin listing visible; one harmless admin write prepared for step 4'],
  ['4', '<code>/api/v1/notifications</code> for the demo buyer', 'JSON list with at least one notification'],
  ['5', 'CloudWatch dashboard <code>blocbuy-swe5001</code>', '1-hour window, auto-refresh 10 s; Edge, Services, Messaging rows visible'],
  ['6', 'ECS console: cluster <code>blocbuy-swe5001</code>, service <code>commerce-svc</code> → Tasks', 'Filtered to running and stopped tasks'],
  ['7', 'SQS <code>blocbuy-swe5001-notification-dlq</code>; CloudWatch alarm <code>blocbuy-swe5001-notification-dlq-not-empty</code>', 'Plan A: 1 message, ALARM'],
  ['8', 'GitHub: 19 Oct promotion PR <code>dev</code> → <code>prod</code> and its <code>swe5001-deploy</code> run', 'Checks tab and run graph open; never the Merge button'],
  ['T', 'Terminal mirroring k6 on the load machine', 'Live summary: request rate, p95, error rate'],
])}
<h2>Opening (slide 23)</h2>
${slideCard(C.SLIDES[22])}
<h2>Step by step</h2>
${demoSteps.map(demoCard).join('')}
${box('warn', 'Watch out', '<b>Never live:</b> clicking Merge on any PR into <code>prod</code> (a promotion destroys this environment); opening Parameter Store values, Terraform state or plan output; scaling commerce or notification by hand (autoscaling owns them); running <code>env-up.sh</code> while k6 is running. The identity outage must be restored before step 6; if identity is not back by 16:52:30, say so in one sentence and move on.')}
${box('tip', 'Tip', 'Narrate results, not clicks: “task count went from two to four”, not “now I’m clicking on the services row”. No troubleshooting on air.')}
<div style="break-inside:avoid"><h2>Closing slide</h2>
${slideCard(C.SLIDES[23])}</div>
${qaTable(6)}
</section>`;

const contingencies = `<section class="chap"><h1>7 Contingencies and time recovery</h1>
<h2>If something fails</h2>
${tbl(['Situation', 'Detected by', 'Response and words', 'Who'], [
  ['Morning up fails', 'Workflow red', 'Read the cause, run down, retry once; deadline 12:00. If it fails again, present the demo entirely from recordings and say so on slide 5.', 'Liu Jin'],
  ["Presenter's audio or connection drops", 'Silence &gt; 5 s', 'Backup continues from the slide card: Lim ↔ Pan, Eric ↔ Yorik, Liu Jin ↔ Yorik.', 'Backup'],
  ['Lim cannot share', 'Black screen', 'Eric shares the HTML deck from tab 1 for segments 1–5 (PDF as a last resort).', 'Eric'],
  ['k6 does not start at 16:28', 'No <code>K6 UP</code> by 16:30', 'Yorik retries once before 16:31; otherwise step 2 uses the V3 recording.', 'Yorik'],
  ['Commerce has not scaled by 16:46', 'Dashboard at 2 tasks', 'Show what is visible honestly (“the alarm needs three minutes of data”), then the V3 recording.', 'Eric'],
  ['Stopped task not replaced', 'ECS tab', 'Show the ECS events list; then the V5 recording.', 'Eric'],
  ['Identity outage step misbehaves', 'Admin write not 500', 'Restore identity immediately; show the identity-outage recording.', 'Yorik'],
  ['DLQ empty or alarm OK', 'Tab 7', 'Show the V7 recording; mention the local redrive test (receive counts 1–5 then DLQ, 15 Oct).', 'Eric'],
  ['GitHub slow or logged out', 'Tab 8', 'Show the pipeline screenshots.', 'Pan'],
  ['<b>Plan C (new):</b> a step fails live and there is no 19 Oct recording for it', '—', 'Show the local integration-test result for that step and say: <i>“verified locally, not yet on AWS.”</i> Never present a local result as an AWS one.', 'Eric'],
  ['Assessor asks for something not prepared', '—', 'Offer to show it after the talk or in the report; do not improvise on the live environment. If a backup slide covers it, open it (B, then the row).', 'Lim'],
])}
<h2>If the clock slips</h2>
<p>Recover time in this order; never cut a guideline item, only depth.</p>
${tbl(['Segment', 'Cut first', 'Saves'], [
  ['1', 'Slide 3: skip the seed sentence; slide 4: drop "and we added event-driven notifications"', '15 s'],
  ['2', 'Slide 6: name only three milestone dates', '15 s'],
  ['3', 'Slide 8: name the decisions, skip the cost sentence; slide 11: drop the "That\'s how the platform grows" sentence', '35 s'],
  ['4', 'Slide 12: skip the Kubernetes sentence; slide 14: skip the chart narration; slide 15: skip the sensitive-data sentence', '45 s'],
  ['5', 'Slide 19: one sentence for all four checks; slide 21: two findings instead of three; slide 22: skip the disclosed-exception sentence (it is on the slide)', '50 s'],
  ['6', 'Step 3 and step 4 merged into one narration while Yorik runs both', '45 s'],
])}
</section>`;

const timeline = `<section class="chap"><h1>8 Timeline before, on and after the day</h1>
<h2>Before the day</h2>
${tbl(['Date', 'Do', 'Owner'], [
  ['Sun 18 Oct', 'Each owner reviews their slides and this script, and confirms their effort estimate on slide 6. Every marker has an owner in §9.', 'All; Lim collects'],
  ['Mon 19 Oct', 'Sprint 4 promotion. Evidence session: V3, V4, V5, V7, identity outage, buyer E2E (#460), smoke on ECS. <b>Record every demo step</b> (screen + k6 terminal, 1080p, no secrets visible) into <code>fallback/1-use-cases</code> … <code>fallback/6-pipeline</code>. Capture the CloudWatch crops for slide 14 and B11. Save evidence under <code>docs/swe5001/evidence/</code>. <code>env-down</code> + clean <code>env-status</code>.', 'All; Liu Jin operates'],
  ['Mon 19 Oct evening', 'Resolve every [FILL 19 Oct] from the evidence folder; move anything not run to "Still open" on slide 5 and to B12 as "not run on AWS; local result: …". Rebuild deck, PDF and this script.', 'Lim, with segment owners'],
  ['Tue 20 Oct', '<b>Full rehearsal</b> in Zoom, timed against the run sheet, with both share switches and the real 16:28 / 16:40 cues on the clock. <b>Decide Plan A or Plan B</b>, slide 10 Option A/B and the slide 16 go/no-go. Every speaker answers their Q&amp;A rows in ≤ 45 s. No marker may remain after this rehearsal.', 'All; Lim times'],
])}
<h2>Presentation day (Wed 21 Oct)</h2>
${tbl(['Time (SGT)', 'Do', 'Owner'], [
  ['≤ 10:30', 'A.1: nothing queued that could destroy the session. Plan B only: reviewed runtime apply with <code>v4_demo_enabled=true</code> first. A.2: dispatch <code>up</code> on <code>prod</code>; confirm SNS subscription emails during the 15-minute readiness wait; watch to green (15–25 min, ≈ 32 min for the distributed cold start).', 'Liu Jin'],
  ['≤ 12:00', 'SPA loads, health <code>UP</code>, smoke green, scaling targets read back as not suspended. Sign in with the demo Google account, A.5 grant-admin, sign out and in, seed listings. If up failed: cause → <code>down</code> → retry once.', 'Liu Jin'],
  ['13:30', 'Tech check in a test Zoom: mic, camera, share window, notifications off, wired network if possible. Load machine ready (A.7 exports, preflight).', 'All'],
  ['15:30', 'Plan A: inject the poison message (A.6); confirm DLQ = 1 and alarm in ALARM by 15:45. Open demo tabs; run through §6 preconditions.', 'Liu Jin, Eric'],
  ['16:00', 'Join the meeting as “Team 8 – Name”; backchannel open; fallback clips loaded.', 'All'],
  ['16:15', 'Start; follow the run sheet.', 'Lim'],
  ['16:28', '<code>K6 GO</code> → Yorik starts V3 (A.7).', 'Yorik'],
  ['16:40', '<code>PLAN A</code> (nothing to do) or <code>PLAN B</code>: run A.8, then A.6 once the seed is confirmed.', 'Liu Jin'],
  ['16:44:30', 'Share switch Lim → Eric.', 'Lim, Eric'],
  ['16:55', 'End; thank the assessors.', 'Lim'],
  ['≤ 17:30', 'Export dashboard screenshot, k6 summary, ECS events to the evidence folder. A.9: <code>down</code> and a clean <code>status</code>. Write every assessor remark into #461.', 'Liu Jin, Lim'],
])}
<h2>After the presentation</h2>
<ul><li>Teardown verified (A.9); record both run links in the day log. Evidence saved <b>before</b> teardown: log groups die with <code>runtime</code>.</li><li>Every assessor remark goes into #461 the same evening; each becomes a change or a justified non-change in the report (due 16 Nov).</li><li>Tag <code>swe5001-demo-20261021</code> on the <code>prod</code> commit that was shown (never a <code>v*</code> tag).</li></ul>
</section>`;

const eff = [['Liu Jin', '12.5', 'AWS stacks, pipeline, service split'], ['Panpakorn (Pan)', '11.5', 'Messaging, scaling, login, load tests'], ['Yu Qianshuo (Yorik)', '9', 'Event outbox and relay, failure drills'], ['Lim Jiechao', '8.5', 'Lead; AWS Organization, Identity Center and guardrails; architecture study and proof of concept; presentation'], ['Wang Ziang (Eric)', '8.5', 'Notification consumer chain, DLQ redrive, demo lead']];

function openItems() {
  return `<section class="chap"><h1>9 Open items before Tuesday's rehearsal</h1>
<p>There are ${markers.FILL} <span class="mk mk-fill">[FILL 19 Oct]</span>, ${markers.VERIFY} <span class="mk mk-verify">[VERIFY]</span> and ${markers.CONFIRM} <span class="mk mk-confirm">[CONFIRM]</span> markers across the deck and this script (counted in the script; the deck shows the same ones on the slides). They stay visible until resolved: <b>FILL</b> values only exist after the 19 Oct session, so never present a guess; <b>VERIFY</b> claims exist in one source but conflict with or are unconfirmed by another, so check the repo or run logs; <b>CONFIRM</b> needs team agreement.</p>
${tbl(['#', 'Item', 'Owner', 'Blocks slide'], C.OPEN_ITEMS.map(r => r.map(esc)))}
<h2>Effort per member ${md('{{CONFIRM:all five agree}}')}</h2>
<p class="small">Estimated from the planned hours of the issues each person delivered, credited to whoever actually did the work. Total ≈ 50 person-days.</p>
${tbl(['Member', 'Person-days', 'Main area'], eff)}
</section>`;
}

const appendix = `<section class="chap"><h1>A Appendix — operator commands</h1>
<p>Run from an operator environment with the reviewed checkout (see the environment session runbook). Never paste outputs containing parameter values into chat or onto the shared screen.</p>
<h3>A.1 Nothing queued that could destroy the session</h3>${pre(`gh run list --repo PPsyrius/iss-groupbuy-team8 --workflow swe5001-env.yml --limit 5
gh run list --repo PPsyrius/iss-groupbuy-team8 --workflow swe5001-deploy.yml --limit 3`)}
<h3>A.2 Bring the environment up (release digests) and check health</h3>${pre(`gh workflow run swe5001-env.yml --repo PPsyrius/iss-groupbuy-team8 --ref prod -f action=up
curl -s https://d3lvq02o2e7tcn.cloudfront.net/api/actuator/health
aws application-autoscaling describe-scalable-targets --service-namespace ecs \\
  --query 'ScalableTargets[].{id:ResourceId,min:MinCapacity,max:MaxCapacity,suspended:SuspendedState}'`)}
<h3>A.3 V5 self-healing — stop one commerce task under load</h3>${pre(`aws ecs list-tasks --cluster blocbuy-swe5001 --service-name commerce-svc
aws ecs stop-task --cluster blocbuy-swe5001 --task <task-arn> --reason "V5 drill"
aws ecs describe-services --cluster blocbuy-swe5001 --services commerce-svc \\
  --query 'services[0].events[:5]'`)}
<h3>A.4 Identity outage drill — stop and restore</h3>${pre(`aws ecs update-service --cluster blocbuy-swe5001 --service identity-svc --desired-count 0
aws ecs update-service --cluster blocbuy-swe5001 --service identity-svc --desired-count 1`)}
<h3>A.5 Demo admin and startable listings</h3>${pre(`infra/swe5001/scripts/grant-admin.sh '<demo google email>'
BLOCBUY_BASE_URL=https://d3lvq02o2e7tcn.cloudfront.net/api/v1 \\
  BLOCBUY_TOKEN=<cookie value> scripts/seed-dev-demo-listings.sh`)}
<h3>A.6 V7 poison message</h3><p>Use the drill script delivered by #459 if it exists. Otherwise publish one malformed envelope to the topic with the routing attribute the notification subscription filters on (read the filter policy first; a message without it is filtered out and never reaches the queue):</p>${pre(`TOPIC=$(aws sns list-topics --query "Topics[?ends_with(TopicArn, ':blocbuy-swe5001-domain-events')].TopicArn" --output text)
aws sns list-subscriptions-by-topic --topic-arn "$TOPIC"     # find the notification subscription
aws sns get-subscription-attributes --subscription-arn <arn> --query 'Attributes.FilterPolicy'
aws sns publish --topic-arn "$TOPIC" --message '{"malformed":true}' \\
  --message-attributes '{"eventType":{"DataType":"String","StringValue":"PaymentSucceeded"}}'`)}
<p>Add any further attribute the filter policy requires. With a 60 s visibility timeout and maxReceiveCount 5, expect the DLQ after about 5–6 minutes and the alarm a few minutes later.</p>
<h3>A.7 V3 load (load machine, POSIX shell, from the repository root)</h3><p>Ramp: 20% → 100% of KNEE_RPS in 5 min, to 300% in 10 min, hold 10 min, down 5 min (30 min total).</p>${pre(`results=$(mktemp -d); chmod 700 "$results"
export BASE_URL=https://d3lvq02o2e7tcn.cloudfront.net CONFIRM_TARGET=https://d3lvq02o2e7tcn.cloudfront.net
export TEST_SHA=$(git rev-parse HEAD) IMAGE_SHA='<deployed commerce source sha>'
export EXPECTED_IMAGE_DIGEST='sha256:<deployed commerce digest>' RUN_LABEL=20261021-v3-presentation
docker run --rm --user "$(id -u):$(id -g)" \\
  --mount "type=bind,source=$PWD/perf/k6,target=/scripts,readonly" \\
  --mount "type=bind,source=$results,target=/results" \\
  --env BASE_URL --env CONFIRM_TARGET --env TEST_SHA --env IMAGE_SHA --env EXPECTED_IMAGE_DIGEST --env RUN_LABEL \\
  grafana/k6:1.6.1@sha256:a5ad6bc089a08d77c3ec49f3db8c6fa7a148e4073efcac44c675dbaf3568d8e1 run \\
  --env MODE=v3 --env TOPOLOGY=distributed --env KNEE_RPS=200 \\
  --env PREFLIGHT_PATH=/results/preflight.json --env SUMMARY_PATH=/results/summary.json \\
  --out json=/results/samples.jsonl /scripts/browse.js`)}
<h3>A.8 V4 burst (Plan B only; requires the reviewed v4_demo_enabled=true deployment)</h3>${pre(`export V4_RUN_LABEL=v4-20261021-presentation V4_RECIPIENT_ID=<already-active synthetic identity uuid>
export EXPECTED_IMAGE_DIGEST=sha256:<commerce digest> EXPECTED_NOTIFICATION_DIGEST=sha256:<notification digest>
export CONFIRM_V4_SEED=2000 V4_RESULTS_DIR=<new empty private directory outside the checkout>
bash infra/swe5001/scripts/v4-burst.sh`)}
<p>Restore <code>v4_demo_enabled=false</code> with a reviewed apply after the talk (or let <code>down</code> remove the runtime).</p>
<h3>A.9 Tear down and verify</h3>${pre(`gh workflow run swe5001-env.yml --repo PPsyrius/iss-groupbuy-team8 --ref prod -f action=down
gh workflow run swe5001-env.yml --repo PPsyrius/iss-groupbuy-team8 --ref prod -f action=status`)}
</section>
<section class="chap"><h1>B Appendix — backup slides (Q&amp;A only)</h1>
<p>Backups follow slide 24 in the HTML deck and the PDF. In the deck press <b>B</b> for the index (BX) and click a row; slide 24 stays up otherwise. Each backup gives the AD/AISS IDs in the Q&amp;A answers a slide to point to.</p>
${tbl(['ID', 'Slide', 'Answers'], C.BACKUPS.map(b => b.map(esc)))}
</section>`;

// ---- assemble (segments first so markers are counted before §9) ----
const segs = [
  segment(C.SEGMENTS[0]),
  segment(C.SEGMENTS[1], { warn: 'Slide 5 must show the real state on 21 Oct. After the 19 Oct session, give a green tick only to items with evidence in <code>docs/swe5001/evidence/</code>; move everything else to “Still open”. Never claim a result that the evidence folder does not record.' }),
  segment(C.SEGMENTS[2], { tip: 'Slide 7 is the most important slide of the talk: it is the one decision assessors will remember. Rehearse it until it fits in 90 seconds without rushing.' }),
  segment(C.SEGMENTS[3], { tip: 'Liu Jin owns slides 12–15 (about 4.5 minutes); Yorik owns 16–17 (about 2.7 minutes). The hand-over line is on slide 15. Yorik unmutes during the last sentence. Slide 16 is the UML version only if Yorik gives the go after rehearsing it; otherwise present B15 and talk through its numbered steps.' }),
  segment(C.SEGMENTS[4], { warn: 'At 16:44:30 Lim stops sharing and Eric starts sharing the demo window, even if a question is still being answered. The answer continues over Eric’s screen.', tip: 'Segment 5 now has five slides in the same 6 minutes: 18 (65 s) + 19 (70 s) + 20 (95 s) + 21 (75 s) + 22 (55 s) = 360 s. The hand-over to the demo moved from slide 21 to slide 22.' }),
];
const demoHtml = demo;

const coverHtml = cover({
  kicker: 'SWE5001 Practice Project · Graduate Certificate in Architecting Scalable Systems',
  title: 'Speaker Script and Live Demo Run Book',
  subtitle: 'BlocBuy — Team 8 · Wednesday 21 October 2026, 16:15–16:55 SGT (Zoom)',
  meta: [
    ['Responsible', 'Lim Jiechao (presentation lead, #461); reviewer Liu Jin.'],
    ['Companion files', '<code>swe5001-presentation.html</code> — the deck (24 slides + backups BX, B1–B15; open in Chrome or Edge; <b>S</b> notes window with this script and a timer, <b>T</b> start timer, <b>O</b> overview, <b>F</b> fullscreen, <b>B</b> backup index) · <code>swe5001-presentation.pdf</code> — the same deck as a PDF (41 pages: 24 main, then backups).'],
    ['Single source', '<code>src/content.js</code> holds wording, timings, cues, Q&amp;A and open items; <code>src/slides-main.js</code> and <code>src/slides-backup.js</code> hold the slide layouts; <code>src/diagrams/*.svg</code> the UML diagrams. From <code>docs/swe5001/presentation/</code> run <code>npm ci</code> once, then <code>npm run all</code> to rebuild everything in <code>dist/</code>.'],
    ['Facts as of', '18 Oct 2026. Evidence from 17 Oct; everything that depends on the 19 Oct session is marked and must be resolved Monday evening (§9).'],
  ],
  after: `${box('', 'How to use this document', `<ul><li><b>Speakers:</b> read your segment (§1–§5) aloud against a timer at least twice. Each slide card shows the slot and the estimated speaking time of the sample wording at 130 words a minute. Cards marked <span class="over">over by N s</span> need either a faster read or the listed trim. Rephrase in your own words; never read the slide.</li><li><b>Demo team:</b> §6 is the run book: preconditions, the Plan A/B decision, screen layout, then every step with what to operate, what to say, what you should see and when to switch to the fallback. §7 adds Plan C.</li><li><b>Lead:</b> the run sheet and coverage check below, the contingencies in §7, the day timeline in §8 and the open items in §9 are yours.</li></ul>`)}
${box('', 'Markers', `${md('{{FILL:}}')} value only exists after the Monday evidence session; do not present a guess · ${md('{{VERIFY:}}')} claim unconfirmed or conflicting between sources; check the repo or run log · ${md('{{CONFIRM:}}')} needs team agreement. They are visible on the slides on purpose and must all be gone after Tuesday's rehearsal.`)}
<p class="small" style="margin-top:8pt">Structure follows “04 Project Presentation Guidelines &amp; Schedule” V2.8 exactly: one chapter per segment (1 Overview, 2 Project Conduct, 3 Logical, 4 Physical, 5 DevOps, 6 Demo), each opening with the guideline’s own sub-items.</p>`,
});

const runSheetHtml = runSheet(`<p>Q&amp;A windows (inside the 40 minutes): 16:17–16:18 · 16:19:30–16:20 · 16:27–16:29 · 16:36–16:38 · 16:44–16:45 · 16:53–16:55. Background actions: <b>16:28</b> Yorik starts k6 V3 · <b>16:40</b> expiry-burst decision point (§6) · <b>16:44:30</b> screen-share switch Lim → Eric.</p>
<h2>Every guideline item has slides and a speaker</h2>
${tbl(['Item', 'Guideline requirement', 'Slides', 'Speaker'], coverage.map(r => [`<b>${r[0]}</b>`, esc(r[1]), r[2], r[3]]))}
<h2>Every briefing question has an answer on a slide</h2>
${tbl(['Briefing', 'Where'], briefing)}
<h2>Cue words in the team backchannel (never the Zoom chat)</h2>
${tbl(['Message', 'Meaning', 'Sent by'], cueWords.map(r => [`<code>${r[0]}</code>`, r[1], r[2]]))}`);

module.exports = coverHtml + '\n' + runSheetHtml + segs.join('') + demoHtml + contingencies + timeline + openItems() + appendix;
