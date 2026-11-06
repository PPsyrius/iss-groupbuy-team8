// SWE5006 Team 8 — Project Progress Report, Sprint 4
// Build: typst compile sprint4-progress-report.typ ../SWE5006-Team8-Project-Progress-Report-Sprint4.pdf
// Written on 2026-11-06 from the Sprint 4 review, the reconstructed retrospective and the burndown data
// (docs/agile/sprint-04/review.md, retro.md, burndown.csv). Styling follows sprint2-progress-report.typ.

#let accent = rgb("#0a6fa8")
#let muted = rgb("#5f6b78")
#let hdr = rgb("#dde8f2")
#let ok = rgb("#1e7b3a")
#let part = rgb("#a86a00")
#let bad = rgb("#b3261e")

#let done = text(fill: ok, weight: "bold")[Completed]
#let partial = text(fill: part, weight: "bold")[Partially completed]
#let notdone = text(fill: bad, weight: "bold")[Not completed]
#let inprog = text(fill: part, weight: "bold")[In-progress]
#let resolved = text(fill: ok, weight: "bold")[Resolved]
#let open = text(fill: bad, weight: "bold")[Open]
#let partres = text(fill: part, weight: "bold")[Partially resolved]

#set document(title: "Project Progress Report for BlocBuy — Sprint 4", author: "SWE5006 Team 8")
#set page(
  paper: "a4",
  margin: (x: 2.2cm, top: 2.3cm, bottom: 2.3cm),
  footer: context {
    if counter(page).get().first() > 1 {
      set text(8pt, fill: muted)
      [BlocBuy — Sprint 4 Progress Report #h(1fr) Page #counter(page).display()]
    }
  },
)
#set text(font: ("Arial", "Helvetica"), size: 10.5pt, lang: "en")
#set par(justify: true, leading: 0.62em, spacing: 0.95em)
#set heading(numbering: "1.1")
#show heading.where(level: 1): it => {
  v(0.5em)
  set text(15pt, fill: accent)
  it
  v(0.25em)
}
#show heading.where(level: 2): it => {
  v(0.35em)
  set text(12pt, fill: accent)
  it
  v(0.1em)
}
#show heading.where(level: 3): it => {
  v(0.25em)
  set text(10.5pt)
  it
}
#set list(indent: 0.6em, spacing: 0.6em)

#set table(
  stroke: 0.5pt + rgb("#a7b4c2"),
  inset: (x: 5pt, y: 4.5pt),
  fill: (x, y) => if y == 0 { hdr },
  align: left + horizon,
)
#show table.cell.where(y: 0): set text(weight: "bold")
#show table: set text(8.8pt)
#show table: set par(justify: false, leading: 0.5em)
#show figure: set block(breakable: true)
#show figure.where(kind: table): set figure.caption(position: bottom)
#show figure.caption: set text(9pt, fill: muted, style: "italic")

// Burndown data: one source, docs/agile/sprint-04/burndown.csv
#let bd = csv("agile/sprint-04/burndown.csv", row-type: dictionary)
#let short(d) = if d == "start" { "Start" } else { str(int(d.slice(8, 10))) + " Oct" }

// ---------------------------------------------------------------- Title page
#page(footer: none)[
  #v(1.2cm)
  #align(center, image("nus-iss-logo.png", width: 7.5cm))
  #v(2.2cm)
  #align(center)[
    #text(26pt, weight: "bold", fill: accent)[Project Progress Report for BlocBuy]
    #v(0.3cm)
    #text(15pt)[Sprint 4]
    #v(0.9cm)
    #text(12pt)[Practice Module for Certificate in Designing Modern Software Systems]
    #v(0.2cm)
    #text(12pt)[SWE5006 — Team 8]
    #v(0.2cm)
    #text(11pt, fill: muted)[Reporting period: 12 October 2026 to 23 October 2026]
    #v(0.2cm)
    #text(10pt, fill: muted)[Written on 6 November 2026 from the sprint record]
  ]
  #v(2cm)
  #align(center)[
    #text(12pt, weight: "bold")[Members]
    #v(0.2cm)
    #set text(11pt)
    Lim Jiechao (limjiechao\@u.nus.edu) \
    Liu Jin (losgai\@u.nus.edu) \
    Panpakorn Siripanich (Pan) (panpakorn.s\@u.nus.edu) \
    Wang Ziang (Eric) (ziangw\@u.nus.edu) \
    Yu Qianshuo (Yorik) (qianshuoyu\@u.nus.edu)
  ]
]

#counter(page).update(2)
#outline(title: [Contents], depth: 2, indent: 1.2em)
#pagebreak()

// ---------------------------------------------------------------- 1
= Introduction

== Project Name and Description
BlocBuy is a group-buying web application. A buyer starts or joins a _bloc_ (a buying team) on a listing, pays the team price up front through Stripe, and is refunded automatically if the bloc does not fill before its deadline. Administrators manage listings and team terms. The same code base also serves the SWE5001 module, where it runs as three cloud services on Amazon ECS.

== Project Methodology
The team used Scrum with two-week sprints, tracked on the GitHub Projects board through issues created from templates, with one sprint container issue per sprint, one dated log file per working day and a scheduled daily board snapshot. Sprint 4 was shared by two modules on one code base: SWE5006 (the deployed development environment, a single server) and SWE5001 (production on Amazon ECS, reached by promoting the development branch). It was the last sprint of the project.

== Project Summary
*Goal:* by 23 October 2026 the deployed development environment runs the complete buyer and admin system, including paid start, paid join and automatic refund; the same code runs as three services in SWE5001 production with scaling and failure evidence; and every member's use case and design problem is drafted. *Result:* partly met. The paid journey was shown on the development environment (start and refund on 23 October, a second buyer's join on 24 October), and the three-service release reached production on 17, 19 and 20 October; most of the planned failure evidence was rehearsed locally rather than captured in the cloud, and two of the five report drafts were ready by 23 October. *Key numbers:* 224.5 hours planned; 118 of the 214.5 hours of issue-backed work closed by 23 October (remaining 96.5 hours); all backend and frontend checks on the development branch green through the sprint. No member logged hours, so these are planned hours of closed work, not measured effort.

// ---------------------------------------------------------------- 2
= Project Progress Report

== Reporting Period
12 October 2026 to 23 October 2026 (ten working days).

== Sprint Number
Sprint 4

== Sprint Objectives
- Turn on Stripe Checkout, scheduling and expiry refunds on the deployed development environment and show a paid start, a paid join and an automatic refund there.
- Split the backend into three services (identity, commerce and notification), with the identity check reached over an internal call that fails closed, and run them in SWE5001 production.
- Deliver commerce events to the notification service through a transactional outbox, a topic and queues with a dead-letter queue.
- Capture HTTP scaling, queue-driven scaling, self-healing, an identity outage and a dead-letter drill as evidence before the SWE5001 presentation on 21 October.
- Draft each member's use case and design problem, and the slides, for the SWE5006 presentation on 26 October.

== Sprint Accomplishment
#figure(
  table(
    columns: (1.25fr, 3fr, 0.95fr),
    table.header[Work Plan][Actual][Status],
    [Paid journey on the development environment], [Checkout, scheduling and refunds were turned on and a paid start with its automatic expiry refund was shown on 23 October, nine days after the 14 October target, after a webhook configuration fix. A second buyer's paid join was shown on 24 October in the demo recording. The refund idempotency record from local evidence was not written.], [#partial],
    [Three-service split], [Identity, commerce and notification packaged as separate services; session roles read outside the identity service; the remote identity check from commerce; the admin attention query moved behind the identity port. All closed on 13–14 October, ahead of the 16 October SWE5001 freeze.], [#done],
    [Event delivery], [Transactional outbox, relay to the queue or topic, an idempotent consumer creating in-app notifications, and the messaging infrastructure with a dead-letter queue and alarm, all merged by 16 October. Effectively-once delivery under failure was not evidenced in the cloud.], [#partial],
    [Production on ECS], [Three-service release promoted to production on 17, 19 and 20 October, the last with the smoke suite green; HTTP scaling and the dashboard in place.], [#done],
    [Scaling and failure evidence], [HTTP baseline on ECS on 16 October; the 19 October scaling run did not meet its own acceptance check; queue-driven scaling, self-healing, identity outage and dead-letter drill rehearsed locally only.], [#notdone],
    [SWE5001 presentation], [Presented on 21 October, with the demo cut to three steps because the cloud drills had not been rehearsed.], [#done],
    [SWE5006 drafts and slides], [Two of five use-case and design-problem drafts merged by 23 October; slides scaffolded on 19 October and drafted on 20 October; the remaining drafts were finished after the sprint.], [#partial],
    [Agile artefacts], [Day logs kept every working day and the board snapshotted daily; the Sprint 3 review was left as a draft, and the Sprint 4 review, retrospective and burndown were reconstructed after the sprint.], [#partial],
  ),
  caption: [Sprint 4 planned work against actual delivery, grouped by theme],
)

== Sprint Burndown Chart

#let burndown() = {
  let labels = bd.map(r => short(r.date))
  let ideal = bd.map(r => float(r.ideal_h))
  let actual = bd.map(r => float(r.remaining_h))
  let w = 14.2cm
  let h = 6.6cm
  let ymax = 240
  let n = labels.len()
  let ox = 1.5cm
  let oy = 1.1cm
  let px(i) = ox + w * i / (n - 1)
  let py(v) = oy + h - h * v / ymax
  let blue = rgb("#0a72b0")
  let grey = rgb("#7d8793")
  block(width: 100%, height: h + 2.4cm, {
    place(dx: ox, dy: 0cm, text(10pt, weight: "bold")[Sprint 4 Burndown Chart])
    place(dx: ox, dy: 0.45cm, text(8pt, fill: muted)[12–23 October 2026, remaining planned hours of issue-backed work])
    place(dx: ox + 8.2cm, dy: 0.2cm, line(length: 0.8cm, stroke: (paint: grey, thickness: 1.5pt, dash: "dashed")))
    place(dx: ox + 9.1cm, dy: 0.05cm, text(8pt)[Ideal remaining])
    place(dx: ox + 11.3cm, dy: 0.2cm, line(length: 0.8cm, stroke: 2.2pt + blue))
    place(dx: ox + 12.2cm, dy: 0.05cm, text(8pt)[Actual remaining])
    for t in range(0, ymax + 1, step: 40) {
      place(dx: ox, dy: py(t), line(length: w, stroke: 0.4pt + rgb("#d5dde5")))
      place(dx: ox - 1cm, dy: py(t) - 0.17cm, box(width: 0.85cm, align(right, text(7.5pt)[#t])))
    }
    place(dx: ox, dy: oy, line(angle: 90deg, length: h, stroke: 0.6pt + rgb("#a7b4c2")))
    place(dx: ox, dy: oy + h, line(length: w, stroke: 0.6pt + rgb("#a7b4c2")))
    place(dx: 0cm, dy: oy + h / 2 + 1.3cm, rotate(-90deg, origin: top + left, text(8pt)[Remaining effort (hours)]))
    for i in range(n) {
      place(dx: px(i) - 0.65cm, dy: oy + h + 0.15cm, box(width: 1.3cm, align(center, text(7.5pt, labels.at(i)))))
    }
    for i in range(n - 1) {
      place(line(start: (px(i), py(ideal.at(i))), end: (px(i + 1), py(ideal.at(i + 1))), stroke: (paint: grey, thickness: 1.5pt, dash: "dashed")))
    }
    for i in range(n) {
      place(dx: px(i) - 1.6pt, dy: py(ideal.at(i)) - 1.6pt, circle(radius: 1.6pt, fill: grey, stroke: none))
    }
    for i in range(n - 1) {
      place(line(start: (px(i), py(actual.at(i))), end: (px(i + 1), py(actual.at(i + 1))), stroke: (paint: blue, thickness: 2.2pt, cap: "round")))
    }
    for i in range(n) {
      place(dx: px(i) - 2.8pt, dy: py(actual.at(i)) - 2.8pt, circle(radius: 2.8pt, fill: blue, stroke: none))
    }
    place(dx: px(n - 1) - 0.9cm, dy: py(actual.at(n - 1)) - 0.6cm, text(8pt, weight: "bold", fill: blue)[#bd.last().remaining_h h])
    place(dx: ox, dy: oy + h + 0.75cm, text(7.5pt, fill: muted, style: "italic")[No hours were logged; each item's planned hours burn on the daily board snapshot where its issues are closed.])
  })
}

#figure(burndown(), caption: [Sprint 4 burndown from 12 to 23 October 2026], kind: image)

The sprint planned 224.5 hours over ten working days. No member logged hours, and the board's estimate field never decreased: the 47 cards that carried an estimate on 12 October kept the same value in every daily snapshot. The actual line is therefore reconstructed by the same method as Sprint 3: each backlog item keeps its planned hours while its issue is open in that day's board snapshot and burns in full on the snapshot where it is closed. The 10 hours of evidence work had no issue and are left out, so the chart starts at 214.5 hours.

The line runs below the ideal on 15 October, when the service split closed ahead of the SWE5001 freeze, then stays at 96.5 hours from 19 October. Fifteen items were still open on 23 October: the report and presentation work, which ran past the sprint by design, and several items whose work was done or presented (the SWE5001 presentation, the production smoke run, the accepted risk, the architecture decision record) but whose cards were not closed. The flat tail therefore understates delivery.

#figure(
  table(
    columns: (0.9fr, 1fr, 1fr, 0.8fr),
    align: (left, center, center, center),
    table.header[Date][Ideal remaining h][Actual remaining h][Open items],
    ..bd.map(r => (short(r.date), r.ideal_h, r.remaining_h, r.open_items)).flatten(),
  ),
  caption: [Daily burndown data],
)

== Problems Encountered, Action Plan and Status
#figure(
  table(
    columns: (2fr, 2.2fr, 0.9fr),
    table.header[Problem][Action][Status],
    [Payments did not settle on the development environment: the webhook signing configuration did not match, so the paid demo slipped to the last day of the sprint.], [Webhook version and signing secret cut over, scheduling enabled, three stuck sessions recovered through the operator path; a runbook section and dated acceptance record written.], [#resolved],
    [The cloud evidence session of 19 October captured only one scaling run, which missed its acceptance check.], [Presentation demo cut to three steps; the failure drills were presented as local rehearsals, not cloud evidence.], [#partres],
    [Cards were not closed when their work was done, so the burndown went flat from 19 October.], [Review lists each open item and the work behind it; closing or splitting them is a retrospective action.], [#open],
    [The board estimates were never updated, so the burndown had to be reconstructed again.], [Reconstructed from issue state and the daily snapshots; the method is stated with the chart.], [#partres],
    [The timed rehearsal on 25 October ran 47 minutes for 34 minutes of slots.], [Each member trimmed their own slides and script the same day; the run book was retimed.], [#resolved],
  ),
  caption: [Problems in Sprint 4],
)

== Sprint Retrospective
No retrospective meeting was held. This section was reconstructed on 6 November 2026 from the sprint record (day logs, board snapshots, pull request and workflow history) and is for the team to confirm.

=== What went well
The plan's priority order held: the three-service split closed on 13–14 October, ahead of the SWE5001 freeze, and the release reached production three times before the SWE5001 presentation, the last with the smoke suite green. The paid journey that Sprint 3 could show only locally was shown on the development environment. The development branch stayed green through the sprint. Blocked carry-overs from Sprint 3 were closed as not planned instead of being carried again, as the Sprint 3 retrospective asked. Slide feedback ran through ten tracked items, all closed before the rehearsal.

=== What could have done better
Cards were not closed when their work was done, and the Sprint 3 action to keep remaining hours current on the board was not kept: no estimate changed all sprint. The development-environment payments arrived nine days after their target. The cloud evidence session captured less than planned. The mid-sprint check, the sprint review and the retrospective were not recorded at the time, and the tracking item for the review was closed by accident by a keyword in an unrelated pull request. Only two of five report drafts were ready by the end of the sprint, and the rehearsal ran 38% over its slots the day before the talk.

=== What will try next
Sprint 4 was the last sprint, so these actions cannot be tested in a later sprint; their owners are to be confirmed by the team. Close each card, or split off what remains, on the day its evidence is committed. Decide before the SWE5001 submission on 16 November whether the outstanding cloud evidence is captured or reported as a known limitation. Use closing keywords in pull requests only for the items a pull request finishes. Hold and record the review and retrospective before the next planning.

// ---------------------------------------------------------------- 3
= Project Team Members Contribution Summary

== Contribution Summary Table
#figure(
  table(
    columns: (1fr, 3.2fr, 0.9fr, 1.6fr),
    table.header[Project Member Name][Activity Description][Achievement Status][Remarks],
    [Wang Ziang (Eric)], [*UC3 Initiate a team and UC4 Join a team.* *Analysis:* one major flow each, a paid start and a paid join; refusals before payment stop the flow, refusals after payment create a refund obligation. *Design:* terms frozen before payment; admission only after the provider confirms the payment. *Design Pattern:* Factory Method, so the settlement service depends only on an abstract team creator. Sprint 4 work: admission refactor, idempotent notification consumer, remote identity check, k6 load scripts, Scrum Master.], [#done], [Draft merged on 23 October. Load runs: baseline on ECS; scaling run below its acceptance check.],
    [Yu Qianshuo (Yorik)], [*UC6 Refund on team expiry.* *Analysis:* expire the team and record refunds in one transaction; a worker pays refunds back. *Design:* temporary provider failures retried with the same request identity; unsafe cases parked for operator review. *Design Pattern:* Observer through in-process domain events, so team outcomes reach order and payment without direct calls. Sprint 4 work: transactional outbox and relay, admin nicknames through the identity port, payments turned on in the development environment.], [#done], [Draft merged on 14 October. Failure drills rehearsed locally; the refund idempotency record is outstanding.],
    [Panpakorn Siripanich (Pan)], [*UC1 Browse listings and UC2 Register and sign in.* *Analysis:* anonymous browsing; Google sign-in registers a local buyer on first use. *Design:* a server-side session for every money action. *Design Pattern:* Template Method for the two outbox transports (queue and topic), which share one delivery algorithm. Sprint 4 work: notification persistence and list endpoint, session roles outside identity, messaging infrastructure, HTTP scaling and dashboard, quality register sweep.], [#inprog], [Draft completed after the sprint, for the final report.],
    [Lim Jiechao], [*UC8 View my orders and teams and UC7 Share attribution.* *Analysis:* one screen lists the buyer's teams and orders with stored status; attribution and crediting kept as separate controls. *Design:* the invite link names the sharer and grants nothing on its own. *Design Pattern:* Strategy for pricing modes, shared by browsing and the first-team quote. Sprint 4 work: SWE5001 presentation lead, SWE5006 slide master, report editor, production smoke checks.], [#inprog], [Use case and design problem completed after the sprint. SWE5001 presentation delivered on 21 October.],
    [Liu Jin], [*UC9 Admin listing management and UC5 Team lifecycle resolution.* *Analysis:* one control serves every admin command; a stale version is its own flow; the team alone owns its transition rule. *Design:* team terms and price in one command; expiry selects teams then resolves one per transaction. *Design Pattern:* State for the listing lifecycle, with Optimistic Offline Lock for concurrent admin edits. Sprint 4 work: three service assemblies and the distributed runtime on ECS, cloud scaling session.], [#inprog], [Draft completed after the sprint, for the final report.],
  ),
  caption: [Contribution summary at the end of Sprint 4 (23 October 2026)],
)

// ---------------------------------------------------------------- 4
= Other Things to Be Highlighted

== Effort per Member
No member logged hours in Sprint 4, so actual hours per member are not available and none are given. The table shows each member's planned hours from the Sprint 4 plan and, as a delivery measure rather than effort, the planned hours of their own items whose issues were closed by 23 October. Shared report drafting (41 hours) and evidence work (10 hours) are in the planned column but cannot be attributed by closure.

#figure(
  table(
    columns: (1.6fr, 1fr, 1.4fr, 1fr),
    align: (left, center, center, left),
    table.header[Member][Planned hours][Planned hours of own items closed by 23 Oct][Actual hours],
    [Liu Jin], [51], [34 of 43], [Not logged],
    [Yu Qianshuo (Yorik)], [48], [28 of 40], [Not logged],
    [Wang Ziang (Eric)], [46], [29 of 38], [Not logged],
    [Panpakorn Siripanich (Pan)], [41.5], [27 of 28.5], [Not logged],
    [Lim Jiechao], [38], [0 of 24], [Not logged],
    [*Team*], [*224.5*], [*118 of 173.5*], [Not logged],
  ),
  caption: [Planned hours per member and planned hours of closed items],
)

Lim's own items (the SWE5001 presentation, production smoke checks, the slide master and the Sprint 3 closure) were delivered or partly delivered, but their issues were still open on 23 October, so the closure measure shows zero. On 19 October the five members agreed an effort split for the whole project in person-days; it is an agreed estimate, not a measurement, and covers more than this sprint.

== Measurement Scope
Work counts as complete when its issue is closed in the daily board snapshot. Demonstrations on the development environment and in production are reported separately with their dates. Items completed after 23 October (the remaining report drafts, the SWE5006 presentation on 26 October) are described as such and are not counted in the burndown.

== Plan for the Next Two Weeks
Sprint 4 was the last sprint. The SWE5006 report was frozen on 29 October for submission by 31 October. The SWE5001 final report is due on 16 November; before then the team decides whether the outstanding cloud evidence (queue-driven scaling, self-healing, identity outage, dead-letter drill and the refund idempotency record) is captured in one session or reported as a known limitation, and closes or splits the Sprint 4 items whose work is done.
