// SWE5001 speaker script and demo run book: the chapters of dist/swe5001-presentation-script.pdf.
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
  ['3c', 'Where relevant, highlight the elements of Domain Driven Design that help to shape the design of the solution as a platform. In particular, describe how the platform is designed to interact with the producers and consumers.', '10, 11', 'Eric'],
  ['4a', 'Highlight the key architectural decisions that influence the physical architecture.', '12', 'Liu Jin'],
  ['4b', 'Describe the physical architecture that includes the network, nodes, technologies and cloud services that are used in the solution.', '13, 14 (UML deployment)', 'Liu Jin'],
  ['4c', 'Describe the persistence design of the solution.', '15', 'Liu Jin'],
  ['4d', 'Describe the detailed design of an architecturally representative user case or user story.', '16, 17 (UML sequence)', 'Yorik'],
  ['5a', 'Describe the strategy to manage the source code and artifacts.', '18', 'Pan'],
  ['5b', 'Describe the DevOps pipeline and procedures.', '19, 20, 22', 'Pan'],
  ['5c', 'Highlight any technical findings and issues encountered.', '21', 'Pan'],
  ['6a', 'Demonstrate some of the significant use cases of the solution.', '23 + demo step 1', 'Eric'],
  ['6b', 'Demonstrate how the solution deals with some of the important quality attributes e.g., scalability (such as demonstrating auto-scaling), performance (show using some scripted performance testing), maintainability (show/run the DevOps pipeline).', 'demo steps 2–4 + 24', 'Eric, Pan, Lim'],
];
const briefing = [
  ['Q1 Pain points', '2'], ['Q2 Platform, common services, reusable frameworks', '3, 9, 11; B5, B10'], ['Q3 Benefit of building on the platform, demonstrated', '11 (measured reuse)'],
  ['Q4 Scale, and proof', '14 (capacity statement), demo step 2; B12'], ['Q5 Technical and non-technical benefits, demonstrated', '12 (measured cost), 14, 19, demo'],
  ['Q6 Value of cloud-native design, demonstrated', '12 (US$0.49 measured), 13, 20'], ['Q7 Which parts of the lifecycle are automated, and the effort saved', '19 (5,252 runs), 20'], ['Q8 Effort within the guideline', '6'],
  ['Success criterion: seed, producers, consumers', '3'], ['Success criterion: more seeds in future', '3 (future seeds line)'], ['Success criterion: analytics', 'B11 (Q&amp;A)'], ['Module: robust API access; minimum security controls', '11, B10; 22'],
];
const cueWords = [['1 MIN', 'Speaker has one minute of talk left in the segment', 'Lim'], ['TIME', 'Stop talking; move to questions or the hand-over', 'Lim'], ['CLIP n', 'Live step n failed; Liu Jin is ready to share the recording for step n', 'Eric → Liu Jin'], ['SHARE', 'Next person: start screen sharing now', 'Lim']];

// ---- Liu Jin's extended Q&A bank (slides 12–15), shown after the segment 4 Q&A ----
const X = C.QA_EXTENDED[4];
const qaBankHtml = `<h3>${esc(X.title)}</h3>
${box('tip', 'Tip', esc(X.tip))}
${X.groups.map(g => `<h3>${esc(g.title)}</h3>` + tbl(['Likely question', 'Suggested answer (say it in 30–45 s)', 'Who answers'], g.rows.map(r => [esc(r[0]), md(r[1]), esc(r[2])]))).join('')}`;

// ---- demo section: v3 run book (#571: no live load), spoken lines from notes/speaker-script-eric.md ----
const demoSteps = [
  { n: 1, t: 'Buy as a customer, across three services', time: '16:45:15 – 16:46:45', proves: 'Functionality · guideline <b>6a</b> · Eric',
    op: ['Tab 2: product list with team prices and countdowns (commerce).', 'Point at the signed-in user in the header (identity, Google sign-in).', 'Tab 3: admin product list (commerce reads the role from the shared session).', "Tab 4: refresh the buyer's notifications (notification service)."],
    say: "This is what a buyer sees: team prices and live countdowns, served by commerce. Up here, I'm signed in with Google, through the identity service. And because of that one login, this admin page works too: commerce reads my role from the shared login session, without calling identity. That's the one login session from slide 8. Last, these notifications. Remember, nothing needs to call notifications: the notification service created these from events it received. So that's three services, not five, behind one login. Now, those product pages come from commerce, and browsing them is the traffic commerce has to scale for. Let's see how it copes.",
    exp: 'All four views load; at least one notification is listed.',
    fb: 'Screenshots taken before the talk (fallback/1-use-cases).',
    fbSay: "This one's not loading in time, so here's the same step from our recording instead. What you're seeing now is recorded, not live.",
    ifs: [['No notifications for the demo account (tab 4)', 'Say: “This account has no notifications yet, so here’s the same view from our recording.” Then post CLIP 1.'],
      ['Product images missing (striped tiles on tab 2)', 'Say: “The product images aren’t loading on this environment, so you’ll see placeholder tiles, but the team prices and live countdowns are what commerce serves, and that’s the part to look at.”'],
      ['Running late', 'Show only the product list (tab 2) and the notifications (tab 4).']] },
  { n: 2, t: 'Auto-scaling, on the dashboard', time: '16:46:45 – 16:49:15', proves: 'Scalability, performance · guideline <b>6b</b> · Eric',
    op: ["Tab 5: live CloudWatch dashboard. Services row: commerce at 2 running tasks, its normal floor; Edge row: today's traffic.", 'Tab 6: the 19 October run on AWS (evidence page): commerce task count 1 → 2 → 3, then load balancer traffic and response time.'],
    say: "This is the live dashboard for the environment you've just used. It's quiet right now, so commerce is sitting at its usual two tasks. Rather than run a load test in the middle of our talk, here's the one we ran on AWS on 19 October. These graphs are saved from that run, not live. As the traffic ramped up, commerce went from one task to two, then to three, all on its own. And it stayed fast: even at about 455 requests a second, 95 percent of responses came back in under 50 milliseconds. That was the limit of the laptop sending the load, not of the service. So this is what Liu Jin showed on slide 14: commerce adds tasks as requests per task rise, by itself. But how did that commerce code get onto AWS in the first place? That's the pipeline. Pan?",
    exp: 'Live dashboard loads with commerce at 2 running tasks; the 19 Oct graphs show the task count going 1 → 2 → 3 and server p95 under 50 ms.',
    fb: 'The same 19 Oct graphs as local files (fallback/2-v3).', clip: false,
    fbSay: "The live dashboard isn't loading in time, so let's go straight to the saved graphs from our 19 October run. These are recorded, not live." },
  { n: 3, t: 'The pipeline that deployed it', time: '16:49:15 – 16:50:45', proves: 'Maintainability · guideline <b>6b</b> · Pan narrates; Eric clicks',
    op: ['Tab 7: promotion pull request #564 (19 Oct): required checks and approval.', 'Open its deploy run: build → create → release → destroy → evidence, with durations.', 'Open the evidence artifact list. Never click Merge.'],
    say: "Pan: Thanks, Eric. Remember, the only thing that deploys to production is merging a promotion pull request. This is the promotion that put today's code on AWS. It had to pass its required checks and get an approval before it could merge. Eric, could you open the deploy run? (Eric opens it.) So this is what that merge started. The run built the images, created the environment from scratch, tested it, released it and tore it down again. End to end, it took (read the total run time off the screen). And the evidence, please. (Eric opens the artifact list.) Here's the evidence it kept. So today's environment, the one you've just used, was built from exactly these images. Eric, back to you.",
    exp: 'Checks green; run green including destroy; artifact present.',
    fb: 'Screenshots of the pull request and run (fallback/3-pipeline); Pan carries on with the same narration over them.', planC: false,
    fbSay: "GitHub isn't cooperating, so here are the same pages from our recording. This part is recorded, not live." },
  { n: 4, t: 'Close the demo', time: '16:50:45 – 16:51:30', proves: 'Reliability (tested locally) · guideline <b>6b</b> · Eric, then Lim',
    op: ['Tab 1: back to the deck; open backup B12 if asked about failures.', 'Thank-you slide (slide 24) at 16:53.'],
    say: "What we didn't show live is failure handling. The identity outage from slide 8 and a bad message going to the dead-letter queue were tested locally, and the results are in our backup slides; stopping a server is still to run on AWS. So: the buying journey, auto-scaling and the pipeline. That's our demo.",
    exp: 'Nothing to operate. 16:51:30–16:53 is spare: questions on the demo.',
    fb: '' },
];
const demoCard = d => `<div class="card demo"><div class="hd"><span>Step ${d.n} &nbsp; ${esc(d.t)}</span><small>${d.time}</small></div><div class="bd">
 <div class="row"><span class="lab">Proves</span><span>${d.proves}</span></div>
 <div class="row"><span class="lab cue">Operate</span><ol style="margin:0;padding-left:14pt">${d.op.map(o => `<li>${esc(o)}</li>`).join('')}</ol></div>
 <div class="row"><span class="lab say">Say</span><span class="say">${esc(d.say)}</span></div>
 <div class="row"><span class="lab">Expect</span><span>${esc(d.exp)}</span></div>
 <div class="row"><span class="lab fb">Fallback</span><span>${!d.fb ? '—' : d.clip === false
  ? `${esc(d.fb)} If the live view is not as expected within 20 seconds, Eric opens them himself and says: <i>“${esc(d.fbSay)}”</i>`
  : `${esc(d.fb)} If the live view is not as expected within 20 seconds, Eric posts <code>CLIP ${d.n}</code> and says: <i>“${esc(d.fbSay)}”</i> ${d.planC === false ? 'With no recording, Eric shows those screenshots; never Plan C for this step.' : 'With no recording for this step, use Plan C (§7).'}`}</span></div>
 ${(d.ifs || []).map(([w, r]) => `<div class="row"><span class="lab">If</span><span><b>${esc(w)}:</b> ${esc(r)}</span></div>`).join('')}</div></div>`;

const demo = `<section class="chap"><h1>6 Demo (8+2 minutes)</h1>
<div class="seghead"><table class="meta"><tr><td>Clock</td><td>16:45:00 – 16:55:00 (8 min talk + 2 min Q&amp;A; talk ends 16:53:00)</td></tr><tr><td>Owner</td><td>Eric (drives), Pan (pipeline)</td></tr><tr><td>Slides</td><td>23</td></tr></table></div>
${reqBox(C.SEGMENTS[5])}
<h2>How the demo meets 6a and 6b</h2>
${tbl(['Step', 'Shows', 'Quality attribute (guideline 6b wording)', 'Evidence on screen'], demoSteps.map(d => [d.n, esc(d.t), d.proves.split(' · ')[0], esc(d.exp)]))}
<p>Scalability and performance are shown in step 2: the live dashboard, then the CloudWatch graphs of the 19 October k6 run on AWS. Maintainability is shown through the pipeline in step 3. Three drills were cut from the live demo on 19 October because they were not rehearsed on AWS: stopping a server, the identity outage and the event backlog with the dead-letter queue. Slide 23 says so in one line, and slide 5 and backup B12 show their results. The demo now ends at about 16:51:30; the spare time goes to questions.</p>
<h2>Principle: nothing changes on the environment during the talk</h2>
<p>The demo only shows results. No load test runs during the talk: a live V3 run needs the commerce floor lowered to one task and a fresh preflight a few minutes before the load (A.7), which is too much to operate on air (team decision, 20 Oct). Step 2 therefore shows the live dashboard, with commerce at its standing two tasks and little traffic, and then the graphs from the 19 October run on AWS. In that run the reviewed ramp took commerce from 1 to 2 tasks at 15:55 and from 2 to 3 at 15:58, all by itself, and at about 455 RPS the server answered 95% of requests in under 50 ms. The laptop sending the load hit its limit first, so 455 RPS is not the service’s ceiling. Say plainly that the graphs are from 19 October; never present them as live.</p>
<h2>Demo preconditions (checked at 15:30 by Liu Jin and Eric)</h2>
<ul><li>Environment up (the #564 promotion on 19 Oct took it down, so it is brought up again before the day), health <code>UP</code>, smoke green; commerce 2/2 (its standing floor), identity 1/1, notification 1/1 running.</li><li>Demo admin granted and signed in again; startable listings seeded (A.5).</li><li>Scaling targets <b>not suspended</b>: commerce 2–6, notification 1–4 (read back; see finding 3 on slide 21).</li><li>Notifications JSON for the demo buyer shows at least one entry (from the smoke run or a seeded expiry).</li><li>The 19 Oct evidence page (tab 6) is open with every graph loaded, and the same PNGs are ready locally in <code>fallback/2-v3</code>.</li><li>Fallback clips for steps 1 and 3 (recorded before the talk) open in a player on Liu Jin's machine, tested with Zoom “share sound” off. No recordings yet: any step that fails live uses Plan C (§7) until they are recorded.</li></ul>
<h2>Screen layout (Eric's demo window)</h2>
<p>One browser window, tabs left to right. Share the window, not the desktop; notifications off; browser zoom 125%.</p>
${tbl(['Tab', 'Content', 'Prepared state'], [
  ['1', 'HTML deck opened at slide 23 (<code>swe5001-presentation.html#23</code>)', 'Also used for slide 24 at the end; the backups are one key away (B)'],
  ['2', 'SPA <code>https://d3lvq02o2e7tcn.cloudfront.net</code>', 'Product list loaded; signed in as the demo admin (Google)'],
  ['3', 'Admin product list in the SPA', 'Admin listing visible'],
  ['4', '<code>/api/v1/notifications</code> for the demo buyer', 'JSON list with at least one notification'],
  ['5', 'CloudWatch dashboard <code>blocbuy-swe5001</code>', '1-hour window, auto-refresh 10 s; Edge, Services, Messaging rows visible'],
  ['6', 'GitHub: the 19 Oct evidence page <code>docs/swe5001/evidence/20261019-v3-autoscaling/README.md</code>', 'Scrolled to the task-count graph; the local PNGs in <code>fallback/2-v3</code> are the backup'],
  ['7', 'GitHub: promotion pull request #564 (19 Oct) (<code>dev</code> → <code>prod</code>) and its <code>swe5001-deploy</code> run', 'Checks tab and run graph open; never the Merge button'],
])}
<h2>Opening (slide 23)</h2>
${slideCard(C.SLIDES[22])}
<p class="small">The slide’s footer says the scaling graphs are from the 19 October load test, so nobody mistakes them for live data.</p>
<h2>Step by step</h2>
${demoSteps.map(demoCard).join('')}
${box('warn', 'Watch out', '<b>Never live:</b> clicking Merge on any PR into <code>prod</code> (a promotion destroys this environment); opening Parameter Store values, Terraform state or plan output; scaling commerce or notification by hand (autoscaling owns them); stopping tasks or services (the drills are not part of this demo).')}
${box('tip', 'Tip', 'Narrate results, not clicks: “task count went from one to three”, not “now I’m clicking on the services row”. No troubleshooting on air.')}
<div style="break-inside:avoid"><h2>Closing slide</h2>
${slideCard(C.SLIDES[23])}</div>
${qaTable(6)}
</section>`;

const contingencies = `<section class="chap"><h1>7 Contingencies and time recovery</h1>
<h2>If something fails</h2>
${tbl(['Situation', 'Detected by', 'Response and words', 'Who'], [
  ['Environment down or unhealthy on the day', 'Morning check', 'Run <code>up</code> on <code>prod</code> (about 32 min), deadline 12:00; if it fails, <code>down</code> and retry once. If it fails again, present the demo from the recordings and the 19 Oct graphs and say so.', 'Liu Jin'],
  ["Presenter's audio or connection drops", 'Silence &gt; 5 s', 'Backup continues from the slide card: Lim ↔ Pan, Eric ↔ Yorik, Liu Jin ↔ Yorik.', 'Backup'],
  ['Lim cannot share', 'Black screen', 'Eric shares the HTML deck from tab 1 for segments 1–5 (PDF as a last resort).', 'Eric'],
  ['Live dashboard slow or empty', 'Tab 5', 'Go straight to tab 6 (the 19 Oct graphs); say the live view only shows today’s quiet baseline.', 'Eric'],
  ['GitHub slow or logged out', 'Tabs 6–7', 'Show the local graph PNGs (<code>fallback/2-v3</code>) and the pipeline screenshots.', 'Eric, Pan'],
  ['<b>Plan C:</b> step 1 or 2 fails live and its fallback (§6 card) fails too', '—', 'Not for step 3: its screenshots (<code>fallback/3-pipeline</code>) are the fallback, and a local test cannot show checks, a deploy run or artifacts. Show the local integration-test result for that step and say: <i>“verified locally, not yet on AWS.”</i> Never present a local result as an AWS one.', 'Eric'],
  ['Assessor asks for something not prepared', '—', 'Offer to show it after the talk or in the report; do not improvise on the live environment. If a backup slide covers it, open it (B, then the row).', 'Lim'],
])}
<h2>If the clock slips</h2>
<p>Recover time in this order; never cut a guideline item, only depth.</p>
${tbl(['Segment', 'Cut first', 'Saves'], [
  ['1', 'Slide 3: skip the hand-picked offers sentence; slide 4: skip the last sentence (next step)', '15 s'],
  ['2', 'Slide 6: name only three milestone dates', '15 s'],
  ['3', 'Slide 8: name the decisions, skip the cost sentence; slide 11: drop the last sentence (the strip says it)', '35 s'],
  ['4', 'Slide 12: skip the Kubernetes sentence; slide 14: skip the chart; slide 15: skip the cache and card sentence', '45 s'],
  ['5', 'Slide 19: one sentence for all four groups; slide 21: two findings instead of three; slide 22: skip the disclosed exception (it is on the slide)', '50 s'],
  ['6', 'Step 1: show the product list and notifications only; the demo already ends 1.5 min early', '30 s'],
])}
</section>`;

const timeline = `<section class="chap"><h1>8 Timeline before, on and after the day</h1>
<h2>Before the day</h2>
${tbl(['Date', 'Do', 'Owner'], [
  ['Sun 18 Oct', 'Each owner reviews their slides and this script and confirms their effort figure on slide 6; send changes to Lim.', 'All; Lim collects'],
  ['Mon 19 Oct', 'Done: V3 auto-scaling ran on AWS (evidence in PR #555); the other drills stay local and were cut from the live demo. Decision: keep the environment up until the talk; no promotion to <code>prod</code> before it (a promotion destroys the environment).', 'All; Liu Jin operates'],
  ['Mon 19 Oct evening', 'Done: 19 Oct results filled in; deck, PDF and this script rebuilt with no gaps left.', 'Lim, with segment owners'],
  ['Tue 20 Oct', 'Decision: no load test during the talk; demo step 2 shows the live dashboard and the 19 Oct graphs. Record the fallback clips for demo steps 1 and 3 on the running environment (1080p, no secrets visible) into <code>fallback/1-use-cases</code> and <code>fallback/3-pipeline</code>. <b>Full rehearsal</b> in Zoom, timed against the run sheet, with both share switches. Confirm slide 16 (UML) or its fallback B15. Every speaker answers their Q&amp;A rows in ≤ 45 s.', 'All; Lim times'],
])}
<h2>Presentation day (Wed 21 Oct)</h2>
${tbl(['Time (SGT)', 'Do', 'Owner'], [
  ['≤ 10:30', 'A.1: nothing queued that could destroy the session. Check the environment with A.2’s health and scaling commands, and run <code>up</code> on <code>prod</code> if it is down (about 32 min; the 19 Oct promotion took it down).', 'Liu Jin'],
  ['≤ 12:00', 'SPA loads, health <code>UP</code>, smoke green; scaling targets read back as not suspended. Sign in with the demo Google account, A.5 grant-admin, sign out and in, seed listings. If up failed: cause → <code>down</code> → retry once.', 'Liu Jin'],
  ['13:30', 'Tech check in a test Zoom: mic, camera, share window, notifications off, wired network if possible.', 'All'],
  ['15:30', 'Open the demo tabs; run through the §6 preconditions.', 'Liu Jin, Eric'],
  ['16:00', 'Join the meeting as “Team 8 – Name”; backchannel open; fallback clips loaded.', 'All'],
  ['16:15', 'Start; follow the run sheet.', 'Lim'],
  ['16:44:30', 'Share switch Lim → Eric.', 'Lim, Eric'],
  ['16:55', 'End; thank the assessors.', 'Lim'],
  ['≤ 17:30', 'Export the dashboard screenshot to the evidence folder. A.9: <code>down</code> and a clean <code>status</code>. Write every assessor remark into #461.', 'Liu Jin, Lim'],
])}
<h2>After the presentation</h2>
<ul><li>Teardown verified (A.9); record both run links in the day log. Save evidence <b>before</b> teardown: log groups die with <code>runtime</code>.</li><li>Every assessor remark goes into #461 the same evening; each becomes a change or a justified non-change in the report (due 16 Nov).</li><li>Tag <code>swe5001-demo-20261021</code> on the <code>prod</code> commit that was shown (never a <code>v*</code> tag).</li></ul>
</section>`;

const eff = [['Liu Jin', '12.5', 'AWS stacks, pipeline, service split'], ['Panpakorn (Pan)', '11.5', 'Messaging, scaling, login, load tests'], ['Yu Qianshuo (Yorik)', '9', 'Event outbox and relay, failure drills'], ['Lim Jiechao', '8.5', 'Lead; AWS accounts and guardrails; architecture study and proof of concept; presentation'], ['Wang Ziang (Eric)', '8.5', 'Notification consumer, dead-letter redrive, demo lead']];

function openItems() {
  const left = markers.FILL + markers.VERIFY + markers.CONFIRM;
  return `<section class="chap"><h1>9 Checks and open items before the rehearsal</h1>
${left ? `<p>There are ${markers.FILL} <span class="mk mk-fill">[FILL]</span>, ${markers.VERIFY} <span class="mk mk-verify">[VERIFY]</span> and ${markers.CONFIRM} <span class="mk mk-confirm">[CONFIRM]</span> markers left across the deck and this script; resolve them before the rehearsal.</p>` : ''}
<h2>Checked against the repository (closed)</h2>
${tbl(['#', 'Item', 'Finding and source', 'Slide'], C.CHECKED_ITEMS.map(r => r.map(esc)))}
<h2>Still open</h2>
${tbl(['#', 'Item', 'Owner', 'Blocks slide'], C.OPEN_ITEMS.map(r => r.map(esc)))}
<div style="break-inside:avoid"><h2>Effort per member</h2>
<p class="small">Estimated from the planned hours of the issues each person delivered, credited to whoever actually did the work, and adjusted by the team. Total ≈ 50 person-days; each member confirms their figure.</p>
${tbl(['Member', 'Person-days', 'Main area'], eff)}</div>
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
<h3>A.3 V5 self-healing — stop one commerce task under load (not used in the demo)</h3>${pre(`aws ecs list-tasks --cluster blocbuy-swe5001 --service-name commerce-svc
aws ecs stop-task --cluster blocbuy-swe5001 --task <task-arn> --reason "V5 drill"
aws ecs describe-services --cluster blocbuy-swe5001 --services commerce-svc \\
  --query 'services[0].events[:5]'`)}
<h3>A.4 Identity outage drill — stop and restore (not used in the demo)</h3>${pre(`aws ecs update-service --cluster blocbuy-swe5001 --service identity-svc --desired-count 0
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
<h3>A.7 V3 load (not used in the talk; the 19 October procedure, for reference)</h3><p>Load machine, POSIX shell, repository root. Ramp: 20% → 100% of KNEE_RPS in 5 min, to 300% in 10 min, hold 10 min, down 5 min (30 min total). The k6 preflight accepts only a stable one-task commerce deployment with scaling bounds 1..6 (<code>perf/k6/lib.mjs</code>), so the standing floor of 2 is lowered first, as on 19 October.</p>
<p>(a) Operator signed in through Identity Center with administrator rights: lower the commerce floor to 1 and wait for a stable 1/1 deployment.</p>${pre(`aws application-autoscaling register-scalable-target --service-namespace ecs \\
  --resource-id service/blocbuy-swe5001/commerce-svc --scalable-dimension ecs:service:DesiredCount \\
  --min-capacity 1 --max-capacity 6
aws ecs update-service --cluster blocbuy-swe5001 --service commerce-svc --desired-count 1
aws ecs wait services-stable --cluster blocbuy-swe5001 --services commerce-svc`)}
<p>(b) Load machine: capture the V3 preflight in the clean reviewed checkout whose HEAD becomes TEST_SHA, with the operator’s AWS environment (<code>perf/k6/README.md</code>). It is read-only, and k6 rejects it if it is more than five minutes old when the load starts.</p>${pre(`results=$(mktemp -d); chmod 700 "$results"
umask 077
bash infra/swe5001/scripts/http-preflight.sh v3 > "$results/preflight.json"`)}
<p>(c) Start the load within five minutes of the preflight.</p>${pre(`export BASE_URL=https://d3lvq02o2e7tcn.cloudfront.net CONFIRM_TARGET=https://d3lvq02o2e7tcn.cloudfront.net
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
<p>Afterwards, set the commerce floor back to 2, or let A.9 <code>down</code> remove the runtime:</p>${pre(`aws application-autoscaling register-scalable-target --service-namespace ecs \\
  --resource-id service/blocbuy-swe5001/commerce-svc --scalable-dimension ecs:service:DesiredCount \\
  --min-capacity 2 --max-capacity 6`)}
<h3>A.8 V4 burst (not used in the demo; requires the reviewed v4_demo_enabled=true deployment)</h3>${pre(`export V4_RUN_LABEL=v4-20261021-presentation V4_RECIPIENT_ID=<already-active synthetic identity uuid>
export EXPECTED_IMAGE_DIGEST=sha256:<commerce digest> EXPECTED_NOTIFICATION_DIGEST=sha256:<notification digest>
export CONFIRM_V4_SEED=2000 V4_RESULTS_DIR=<new empty private directory outside the checkout>
bash infra/swe5001/scripts/v4-burst.sh`)}
<p>Restore <code>v4_demo_enabled=false</code> with a reviewed apply after the talk (or let <code>down</code> remove the runtime).</p>
<h3>A.9 Tear down and verify</h3>${pre(`gh workflow run swe5001-env.yml --repo PPsyrius/iss-groupbuy-team8 --ref prod -f action=down
sleep 5   # let the run appear
RUN=$(gh run list --workflow swe5001-env.yml --repo PPsyrius/iss-groupbuy-team8 --branch prod --limit 1 --json databaseId -q ".[0].databaseId")
gh run watch "$RUN" --repo PPsyrius/iss-groupbuy-team8 --exit-status   # status has its own concurrency group: wait until down has succeeded
gh workflow run swe5001-env.yml --repo PPsyrius/iss-groupbuy-team8 --ref prod -f action=status`)}
</section>
<section class="chap"><h1>B Appendix — backup slides (Q&amp;A only)</h1>
<p>The backups follow slide 24 in the HTML deck and the PDF. In the deck, press <b>B</b> for the index (BX) and click a row; slide 24 stays up otherwise. Each backup gives the AD and AISS IDs in the Q&amp;A answers a slide to point to.</p>
${tbl(['ID', 'Slide', 'Answers', 'Owner', 'What to say'], C.BACKUPS.map(b => b.map(esc)))}
</section>`;

// ---- assemble (segments first so markers are counted before §9) ----
const segs = [
  segment(C.SEGMENTS[0]),
  segment(C.SEGMENTS[1], { warn: 'Slide 5 shows the real state: only auto-scaling ran on AWS (tick); the drills say “not run on AWS”, with their local result where there is one. Never claim a result the evidence folder does not record.' }),
  segment(C.SEGMENTS[2], { tip: 'Slide 7 is the most important slide of the talk: it is the one decision assessors will remember. Rehearse it until it fits in 90 seconds without rushing.' }),
  segment(C.SEGMENTS[3], { tip: 'Liu Jin owns slides 12–15 (about 4.3 minutes); Yorik owns 16–17 (about 2.7 minutes). The hand-over line is on slide 15; Yorik unmutes during its last sentence. Slide 16 is the UML sequence with two click reveals; if it does not feel right at the 20 Oct rehearsal, present backup B15 (numbered steps) with the same words.' }).replace(/<\/section>\s*$/, `${qaBankHtml}\n</section>`),
  segment(C.SEGMENTS[4], { warn: 'At 16:44:30 Lim stops sharing and Eric starts sharing the demo window, even if a question is still being answered. The answer continues over Eric’s screen.', tip: 'Segment 5 now has five slides in the same 6 minutes. The hand-over to the demo moved from slide 21 to slide 22.' }),
];

const coverHtml = cover({
  kicker: 'SWE5001 Practice Project · Graduate Certificate in Architecting Scalable Systems',
  title: 'Speaker Script and Live Demo Run Book',
  subtitle: 'BlocBuy — Team 8 · Wednesday 21 October 2026, 16:15–16:55 SGT (Zoom)',
  meta: [
    ['Responsible', 'Lim Jiechao (presentation lead, #461); reviewer Liu Jin.'],
    ['Companion files', '<code>swe5001-presentation.html</code>: the deck, 24 slides plus 17 backup slides. Open it in Chrome or Edge. Press <b>S</b> for the notes window with this script and a pace timer (<b>T</b> starts it), <b>B</b> for the backup index, <b>O</b> for the overview, <b>F</b> for fullscreen. <code>swe5001-presentation.pdf</code> is the same deck as a PDF (41 pages: 24 main, then backups). Eric’s spoken wording for segment 3 and the demo comes from <code>notes/speaker-script-eric.md</code>.'],
    ['Single source', '<code>src/content.js</code> holds wording, timings, cues, Q&amp;A and open items; <code>src/slides-main.js</code> and <code>src/slides-backup.js</code> hold the slide layouts and <code>src/script.js</code> this document; the UML diagrams are shared from <code>docs/presentation-kit/diagrams/</code>. Run <code>npm ci</code> once in <code>docs/presentation-kit/</code>, then <code>npm run all</code> in <code>docs/swe5001/presentation/</code> to rebuild everything in <code>dist/</code>.'],
    ['Facts as of', '19 Oct 2026, with the 20 Oct demo decision (no live load test). V3 ran on AWS; every other test is shown as “not run on AWS” with its local result. No gaps remain.'],
  ],
  after: `${box('', 'How to use this document', `<ul><li><b>Speakers:</b> read your segment (§1–§5) aloud against a timer at least twice. Each slide card shows the slot and how long the sample wording takes at 130 words a minute. Rephrase in your own words; never read the slide.</li><li><b>Demo team:</b> §6 is the run book: preconditions, screen layout, then every step with what to operate, what to say, what you should see and when to fall back. §7 adds Plan C.</li><li><b>Lead:</b> the run sheet and coverage checks below, the contingencies in §7, the timeline in §8 and the checks and open items in §9 are yours.</li></ul>`)}
<p class="small" style="margin-top:8pt">Structure follows “04 Project Presentation Guidelines &amp; Schedule” V2.8 exactly: one chapter per segment (1 Overview, 2 Project Conduct, 3 Logical, 4 Physical, 5 DevOps, 6 Demo), each opening with the guideline’s own sub-items.</p>`,
});

const runSheetHtml = runSheet(`<p class="small">The wording fills about 70% of each slot; the rest is for pointing at the slide and a natural pace.</p>
<p>Q&amp;A windows (inside the 40 minutes): 16:17–16:18 · 16:19:30–16:20 · 16:27–16:29 · 16:36–16:38 · 16:44–16:45 · 16:53–16:55. Background action: <b>16:44:30</b> screen-share switch Lim → Eric. No load test runs during the talk.</p>
<h2>Every guideline item has slides and a speaker</h2>
${tbl(['Item', 'Guideline requirement', 'Slides', 'Speaker'], coverage.map(r => [`<b>${r[0]}</b>`, esc(r[1]), r[2], r[3]]))}
<h2>Every briefing question has an answer on a slide</h2>
${tbl(['Briefing question or success criterion', 'Where'], briefing)}
<h2>Cue words in the team backchannel (never the Zoom chat)</h2>
${tbl(['Message', 'Meaning', 'Sent by'], cueWords.map(r => [`<code>${r[0]}</code>`, r[1], r[2]]))}`);

module.exports = coverHtml + '\n' + runSheetHtml + segs.join('') + demo + contingencies + timeline + openItems() + appendix;
