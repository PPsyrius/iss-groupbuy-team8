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
  { n: 1, seg: 1, item: '1a', speaker: 'Lim', slot: 15, title: 'BlocBuy', say: stub },
  { n: 2, seg: 1, item: '1a', speaker: 'Lim', slot: 40, title: 'Group buying only works if enough people join in time', say: stub },
  { n: 3, seg: 1, item: '1b', speaker: 'Lim', slot: 45, title: 'Scope: nine use cases on one platform', say: stub },
  { n: 4, seg: 1, item: '1e', speaker: 'Lim', slot: 50, title: 'Four sprints, one board, one Definition of Done', say: stub },
  { n: 5, seg: 1, item: '1d', speaker: 'Lim', slot: 50, title: 'Milestones and effort per member', say: stub },
  { n: 6, seg: 1, item: '1c', speaker: 'Lim', slot: 40, title: 'Status, open issues, and what each retro changed', say: stub },
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
const QA = {};

// Backup index rows: [id, title, what it answers]
const BACKUPS = [];

// Open items: [#, item, owner, blocks slide]
const OPEN_ITEMS = [
  [1, 'Enable Checkout, scheduling and expiry refunds on dev (#223) and fix the Stripe webhook (#517); go/no-go for the live payment demo on 23 Oct', 'Yorik', '21'],
  [2, 'Fallback recordings of both demos on the deployed dev environment by 25 Oct', 'Yorik (system), Pan (pipeline)', '20, 21'],
];

module.exports = { SPEAKERS, SEGMENTS, SLIDES, QA, BACKUPS, OPEN_ITEMS };
