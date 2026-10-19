// BlocBuy SWE5006 deck — single source for speaker wording, timings, cues and Q&A.
// Segment names and timings follow the lecturer's presentation sequence (Chandra, SWE5006). Item texts are copied
// verbatim from "SWE5006 - Practice Project Presentation Guidelines" and the briefing deck V1.0 (20 Aug 2026), slide 6.
// The talk is 40 minutes; questions may come during it; 5 minutes of Q&A follow (17:10–17:15).
// Markers: {{FILL:text}} {{VERIFY:text}} {{CONFIRM:text}} render as visible chips in deck and script.

const SPEAKERS = {
  Lim: { full: 'Lim Jiechao', init: 'JC' },
  Eric: { full: 'Wang Ziang (Eric)', init: 'WZ' },
  'Liu Jin': { full: 'Liu Jin', init: 'LJ' },
  Yorik: { full: 'Yu Qianshuo (Yorik)', init: 'YQ' },
  Pan: { full: 'Panpakorn Siripanich (Pan)', init: 'PS' },
};

// talk/qa in minutes; start is the segment's clock time (SGT). 4 + 5 + 3 + 11 + 14 = 37 minutes of talk, leaving
// 17:07–17:10 as a buffer for questions asked during the talk.
const SEGMENTS = [
  { n: 1, name: 'Project Introduction & Project Management Summary', head: 'Project introduction & project management', short: 'Intro & PM', talk: 4, qa: 0, start: '16:30:00', owner: 'Lim Jiechao',
    items: {
      '1a': 'Explain the context and the business problem that the solution as a whole is trying to achieve.',
      '1b': 'Explain the scope of the project.',
      '1c': 'Explain the current status of the project including outstanding issues.',
      '1d': 'Describe the actual project milestones and the total effort expended by each team member.',
      '1e': 'Explain how many sprints planned, Definition of Done (DoD) achieved.',
      '1f': 'Sprint retrospective & reflection in the next sprint',
      '1g': 'Agile process artifacts like product backlog, user stories, sprint plans, burndown charts, sprint reviews and sprint retrospectives.',
    } },
  { n: 2, name: 'Analysis & Design and Design Patterns', head: 'Analysis & design and design patterns', short: 'Design', talk: 5, qa: 0, start: '16:34:00', owner: 'Wang Ziang (Eric) with Liu Jin, Yu Qianshuo (Yorik), Panpakorn Siripanich (Pan), Lim Jiechao',
    items: {
      '2a': 'Software architectural decisions that influence the logical architecture.',
      '2b': 'Transition from analysis to design',
      '2c': 'Use case diagram',
      '2d': 'Analysis and design models',
      '2e': 'Design problems and patterns',
      '2f': 'Database schemas',
    } },
  { n: 3, name: 'Types of Testing Conducted', head: 'Types of testing conducted', short: 'Testing', talk: 3, qa: 0, start: '16:39:00', owner: 'Liu Jin',
    items: {
      '3a': 'Types of Testing Conducted',
      '3b': 'Well structured source code with unit test cases for classes with more complex behaviours.',
    } },
  { n: 4, name: 'CI/CD Pipeline Demo', head: 'CI/CD pipeline demo', short: 'CI/CD', talk: 11, qa: 0, start: '16:42:00', owner: 'Panpakorn Siripanich (Pan)',
    items: {
      '4a': 'Describe the strategy to manage the source code and artifacts.',
      '4b': 'Describe the DevSecOps pipeline and procedures.',
      '4c': 'Highlight any technical findings and issues encountered.',
      '4d': 'Design specification of the DevOps pipelines along with the use of appropriate tools with minimal security considerations.',
      '4e': 'Backup: Record a video in advance in case of any technical issues during the live demo.',
    } },
  { n: 5, name: 'System Demo', head: 'System demo', short: 'Demo', talk: 14, qa: 5, start: '16:53:00', owner: 'Wang Ziang (Eric) and Yu Qianshuo (Yorik) (drive)',
    items: {
      '5a': 'Demonstrate some of the significant use cases of the solution.',
      '5b': 'Demonstrate how the solution deals with some of the important quality attributes e.g. scalability (such as demonstrating auto-scaling), performance (show using some scripted performance testing), maintainability (show/run the DevOps pipeline).',
      '5c': 'Backup: Record a video in advance in case of any technical issues during the live demo.',
    } },
];

// slot in seconds; null = Q&A. Draft outline: each segment's subagent replaces the stub wording.
const stub = '{{FILL:draft wording}}';
const SLIDES = [
  { n: 1, seg: 1, item: '1a', speaker: 'Lim', slot: 15, title: 'BlocBuy',
    say: "Good afternoon, we're Team 8, and this is BlocBuy. I'll cover the problem and how we ran the project; then come design, testing, our pipeline, and a live demo.",
    cues: ['16:30:00 — Lim opens swe5006-presentation.html in Chrome, presses S for the notes window (keep it off the shared screen) and T there to start the timer. In Zoom, share only the deck window.', 'Backchannel: the team chat stays open during the talk. Anyone who sees the share or audio drop posts there; Lim repeats any question asked during the talk in one line and names who answers (questions may come at any time, Q&A is 17:10–17:15).', 'Tagline source: proposal §4 (two prices, pay first, automatic refund).'],
    handover: 'So, what problem are we solving?' },
  { n: 2, seg: 1, item: '1a', speaker: 'Lim', slot: 40, title: 'Group buying only works if enough people join in time',
    say: "Small merchants can only offer a discount when volume is predictable, and a single buyer can never reach a bulk price. Advertising tries to bridge that gap, but it's expensive and not tied to real purchases. BlocBuy solves this with pay-first teams. Every listing has two prices. You pay upfront to start a team and share an invite link; friends join by paying too. If the team fills in time, everyone gets the team price. If not, everyone is refunded automatically.",
    cues: ['Source: SWE5006 proposal §4 (27 Aug; the pay-first mechanic, two prices, the early Pinduoduo playbook) and §6.1 (Stripe test mode only). The README describes generic organisers and sellers; the proposal is the scope we built, so the slide follows it.', 'What is unique: money is committed before the team exists, so every buyer has a reason to recruit, and the refund on expiry makes joining risk-free.'],
    trim: 'If over time, drop "Advertising tries to bridge that gap… real purchases."',
    handover: "Here's what we built to do that." },
  { n: 3, seg: 1, item: '1b', speaker: 'Lim', slot: 45, title: 'Scope: nine use cases on one platform',
    say: "This is our scope as a use case diagram. Buyers browse listings, sign in with Google, start or join a team, track their orders and teams, and receive notifications. Starting and joining both include paying upfront through Stripe Checkout. Joining through an invite link credits the person who shared it. A scheduler resolves each team, and an expired team triggers automatic refunds through Stripe. Admins manage listings and group terms. The initials show who owns each use case in our report. Out of scope: a merchant self-service GUI, analytics, recommendations, email or push, and live payments.",
    cues: ['Diagram: src/diagrams/use-case.svg. UC1–UC9 are the proposal\'s (§6.1); "Receive notifications" joined in Sprint 4 (#215, #446) and is Pan\'s report use case (#463). Out-of-scope list: proposal §6.2.', '«include»: START and JOIN share one Checkout path (CheckoutQuote accepts only START or JOIN, backend payment/model). «extend»: UC7 credits the sharer only when a join arrives through an invite link (proposal UC7); UC6 runs only when a team expires (proposal UC5, UC6).', 'Initials = report owner per #463: LJ Liu Jin, WZ Eric, YQ Yorik, PS Pan, JC Lim. Beyond the proposal: the API-only admin (UC9) gained an admin console in Sprint 3 (PRs #305–#307, 30 Sep).'],
    trim: 'If over time, drop "The initials show who owns each use case in our report."',
    handover: 'And this is how we ran the project.' },
  { n: 4, seg: 1, item: '1e', speaker: 'Lim', slot: 50, title: 'Four sprints, one board, one Definition of Done',
    say: "We planned four two-week sprints. Each sprint has a plan and a review, and from Sprint 3 we added a mid-sprint check; from Sprint 2, every working day has a dated log in the repository. The backlog is one GitHub Project board: 159 cards today, 132 of them done. We merged 356 pull requests, 70 of them Dependabot updates. We estimate in hours, 30 per member per sprint. Our Definition of Done grew after each retro: on dev means deployed, not local; pull requests stay under 1,500 lines; every quality gate blocks the merge. That strictness is why Sprint 3's goal does not count as met.",
    cues: ['Counted 19 Oct with gh: board "GroupBuy Kanban Board" (project 2) 159 cards = 132 Done, 7 In progress, 15 Ready, 5 Backlog; 159 issues = 134 closed (119 completed, 15 not planned), 25 open; 356 merged PRs = 263 team, 70 Dependabot, 23 board-snapshot PRs. Sprint windows by merge date (SGT), Monday to the Sunday before the next sprint; Sprint 1 includes 7 PRs from 24 Aug.', 'Sprint calendar: docs/agile/README.md (the proposal\'s dates differ by up to three days). Goals: Sprint 1 report, Sprint 2 plan (docx), docs/agile/sprint-03/plan.md §2, sprint-04/plan.md §2. Results: Sprint 2 report (9 of 11 stories, 134 of 150 h), sprint-03/review.md (117 of 150 h, goal not met on dev).', 'Artefacts (item 1g): backlog = board + issue templates (use case, task, NFR); user stories with acceptance criteria in each plan; sprint plans in docs/agile/sprint-0N/plan.md (Sprints 1–2 as docx); burndowns from docs/agile/metrics/ (backup B11); reviews and retros in the progress reports. DoD text: sprint-03/plan.md §8, sprint-04/plan.md §8.', 'Point at "Was it met?": the gates are required checks; the estimate field in the 12–19 Oct snapshots still holds each card\'s planned hours, not remaining hours.'],
    trim: 'If over time, drop "We estimate in hours, 30 per member per sprint." (it is on slide 5).',
    handover: "Here's how that adds up over time." },
  { n: 5, seg: 1, item: '1d', speaker: 'Lim', slot: 50, title: 'Milestones and effort per member',
    say: "Our milestones: the proposal on 27 August, the backend live on dev on 5 September, the web app on 22 September, and a release tag after each of the first three sprints. Sprint 2 finished 134 of 150 planned hours, Sprint 3 117 of 150. Sprint 4 carries 224.5 hours across two courses; by the 19 October snapshot, cards worth 118 planned hours were done, which is the burndown on the right. On the left are planned hours per member. We did not log actual hours per person, so this is the plan, against a guideline of about ten man-days each.",
    cues: ['Sources: proposal (27 Aug); Sprint 1 report (dev live 5 Sep); Sprint 2 report (web app on dev 22 Sep; 150/134 h); tags v0.1.0-sprint1/2/3 (14 Sep, 27 Sep, 11 Oct; git tag); sprint-03/review.md (US34 listings on dev 8 Oct; 150 h, 33 h remaining at freeze); board: #455 and #456 Done in the 15 Oct snapshot.', 'Burndown: docs/agile/metrics/2026-10-12…19.csv on the metrics/board-snapshots branch, the planned estimate of every Sprint 4 card not in Done (224.5 → 106.5 h); ideal line from sprint-04/plan.md §7. The 19 Oct file was captured early on 19 Oct, so it shows the state after the weekend.', 'Per-member hours: Sprint 2 plan (30 h each), sprint-03/plan.md §4 (30 h each), sprint-04/plan.md §5 as planned (51, 48, 46, 41.5, 38). {{VERIFY:open PR #538 moves #468 and #458 after Planning: Liu Jin 66, Eric 26, Pan 46.5; use those once it merges}}', '{{CONFIRM:actual effort per member, agreed by all five; never present the planned hours as actuals}} Board items Done per assignee (19 Oct) count cards, not effort; a card shared by two people counts for both, and 21 Done cards have no assignee.'],
    trim: 'If over time, drop the milestone dates after 5 September.',
    handover: 'So where does that leave us today?' },
  { n: 6, seg: 1, item: '1c', speaker: 'Lim', slot: 40, title: 'Status, open issues, and what each retro changed',
    say: "Where we stand on 19 October: the dev site is up, with the web app, sign-in and 14 listings. Stripe Checkout opens and returns, but the webhook never confirms the payment, so paid teams and refunds don't run on dev yet. That's issues 517 and 223, and our go/no-go for the live demo. Each retro changed the next sprint: no burndown in Sprint 1 led to daily board snapshots; Sprint 2's huge pull requests led to the 1,500-line rule and the mid-sprint check.",
    cues: ['Checked 19 Oct 14:14 SGT: curl https://d1b3np88z657cp.cloudfront.net/api/actuator/health → {"status":"UP"}; / → 200; /api/v1/products → 14 listings. Sign-in cookie: sprint-03/review.md (NFR-01 verified on dev, 8 Oct). #517 and #223 open on 19 Oct (gh issue view). Re-check all of this on 25 Oct and update the left column.', 'Retro sources: Sprint 1 report (retrospective), Sprint 2 report §Sprint Retrospective → sprint-03/plan.md §8 (three new DoD rules). Sprint 3 retro is owed in #451; Sprint 4 retro is on 23 Oct.'],
    trim: 'If over time, drop the second retro example (Sprint 2).',
    handover: 'Eric will take you through the design.' },
  { n: 7, seg: 2, item: '2a', speaker: 'Eric', slot: 75, title: 'Top-down: split by transaction, one codebase, two topologies', say: stub },
  { n: 8, seg: 2, item: '2e', speaker: 'Liu Jin', slot: 45, title: 'UC9 listings: State and Optimistic Offline Lock', say: stub },
  { n: 9, seg: 2, item: '2e', speaker: 'Eric', slot: 45, title: 'UC3/UC4 start and join: Factory Method', say: stub },
  { n: 10, seg: 2, item: '2e', speaker: 'Yorik', slot: 45, title: 'UC6 expiry refund: never refund twice', say: stub },
  { n: 11, seg: 2, item: '2e', speaker: 'Pan', slot: 45, title: 'Notifications: Observer, Outbox, Idempotent Receiver', say: stub },
  { n: 12, seg: 2, item: '2e', speaker: 'Lim', slot: 45, title: 'UC8 My blocs: a frontend design problem', say: stub },
  { n: 13, seg: 3, item: '3a', speaker: 'Liu Jin', slot: 90, title: 'Seven kinds of tests, from domain rules to the deployed site', say: stub },
  { n: 14, seg: 3, item: '3b', speaker: 'Liu Jin', slot: 90, title: 'Gates that block a merge, and what tests caught', say: stub },
  { n: 15, seg: 4, item: '4a', speaker: 'Pan', slot: 45, title: 'Source code and artefacts: one trunk, images by digest', say: stub },
  { n: 16, seg: 4, item: '4b', speaker: 'Pan', slot: 60, title: 'One pipeline from pull request to dev', say: stub },
  { n: 17, seg: 4, item: '4d', speaker: 'Pan', slot: 45, title: 'Security in the pipeline, no stored keys', say: stub },
  { n: 18, seg: 4, item: '4b', speaker: 'Pan', slot: 45, title: 'Where it runs: dev on EC2, prod on ECS', say: stub },
  { n: 19, seg: 4, item: '4c', speaker: 'Pan', slot: 45, title: 'Technical findings and issues', say: stub },
  { n: 20, seg: 4, item: '4b', speaker: 'Pan', slot: 15, title: 'Live: one change from pull request to dev', say: stub },
  { n: 21, seg: 5, item: '5a', speaker: 'Eric', slot: 20, title: 'Live demo: one bloc from invite to refund', say: stub },
  { n: 22, seg: 5, item: '5b', speaker: 'Lim', slot: null, title: 'Thank you', say: "Thank you. We're happy to take your questions." },
];

// Q&A tables per segment number: { window: '16:44–16:45', rows: [[question, answer, owner], ...] }
const QA = {
  1: { window: 'during the talk / 17:10–17:15', rows: [
    ['Why does Sprint 3 count as not met when the code was merged?', 'Our Definition of Done says "on dev" means shown on the deployed environment, and a local run does not count. Checkout and scheduling were still off on dev at the freeze (#223), so the review recorded the goal as not met and carried it into Sprint 4 as the critical path. Evidence: sprint-03/review.md.', 'Lim'],
    ['Are these hours actuals?', 'No. We planned in hours per story; done hours are planned hours of finished work (Sprint 2 report, Sprint 3 review), and the Sprint 4 burndown sums planned estimates of cards not yet Done. Actual hours per person were not logged, which is a limitation we state.', 'Lim'],
    ['Was the effort balanced across the five members?', 'The plans gave everyone 30 h in Sprints 2 and 3; Sprint 4 started at 38–51 h each and was rebalanced after Planning (PR #538). Board cards differ in size, for example one Stripe pull request was about 10,000 lines, so card counts are not effort. {{CONFIRM:the effort split all five agree on}}', 'Lim'],
    ['How did the retrospectives change what you did?', 'Each retro produced a rule in the next plan: daily board snapshots after Sprint 1; after Sprint 2, PRs under 1,500 lines, a mid-sprint check and remaining hours on the board. The last one was only partly kept: the estimate field still holds planned hours. Sources: Sprint 2 report, sprint-03/plan.md §8.', 'Lim'],
    ['What is your biggest open risk for the demo?', 'The Stripe webhook on dev (#517): payments stay waiting, so paid START, JOIN and refunds do not complete on dev yet. Go/no-go for the live payment demo is 23 Oct, with a fallback recording by 25 Oct.', 'Lim → Yorik'],
  ] },
};

// Backup index rows: [id, title, what it answers]
const BACKUPS = [
  ['B11', 'Sprint 2 and 3 burndowns', 'How did earlier sprints burn down?'],
];

// Open items: [#, item, owner, blocks slide]
const OPEN_ITEMS = [
  [1, 'Enable Checkout, scheduling and expiry refunds on dev (#223) and fix the Stripe webhook (#517); go/no-go for the live payment demo on 23 Oct', 'Yorik', '21'],
  [2, 'Fallback recordings of both demos on the deployed dev environment by 25 Oct', 'Yorik (system), Pan (pipeline)', '20, 21'],
  [3, 'Sprint 1 planned and done hours: none were recorded; reconstruct from #49 or state "not tracked"', 'Pan', '5'],
  [4, 'Sprint 3 retrospective (#451): fill the Sprint 3 → 4 row', 'Lim', '6'],
  [5, 'Sprint 4 result, done hours, retrospective and the v0.1.0-sprint4 tag date after the 23 Oct review', 'Eric', '4, 5, 6'],
  [6, 'Sprint 4 per-member planned hours once PR #538 merges (Liu Jin 66, Eric 26, Pan 46.5)', 'Eric', '5'],
  [7, 'Actual effort per member, agreed by all five', 'Lim', '5, Q&A 1'],
  [8, 'Re-check slide 6 status on 25 Oct (dev health, #517, #223, listings)', 'Lim', '6'],
];

module.exports = { SPEAKERS, SEGMENTS, SLIDES, QA, BACKUPS, OPEN_ITEMS };
