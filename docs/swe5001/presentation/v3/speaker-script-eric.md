# Eric's spoken script, polished (SWE5001 final presentation, Team 8 BlocBuy)

This is Eric's part of the talk, reworded so it sounds natural when read aloud: segment 3 (slides 7 to 11) and the demo (slide 23, then steps 1 to 4). The facts, numbers, technical terms, timings and hand-overs are the same as in the source. Only the spoken glue changed: verbs, connectors, how sentences open, and how one slide leads into the next.

- **Source:** `swe5001-presentation-script-v3.pdf` (speaker script and demo run book v3, 19 Oct 2026, PR #556), plus the deck `swe5001-presentation-v3.html` for what is on each slide.
- **Reading speed:** 130 words a minute. Estimated seconds = words ÷ 130 × 60. Numbers are counted the way you say them ("455" counts as four words).
- **Target:** on slides 7–11 the spoken text fills about 75–85% of each slot, so there is time to point and pause. In demo steps 1–3 it fills about 50–60% of the slot, because switching tabs, waiting for pages and reading live numbers off the screen take the rest; step 4 has nothing to operate, so it is at 80%.
- **Notation:** text in [square brackets] is a cue: do it, don't say it. "[Lim: next slide]" means Lim, who shares the deck during segments 1–5, moves to the next slide as you start that line.
- **If my audio drops:** Yorik continues from this card (run book §7).

How to say the numbers:

| Written | Say |
|---|---|
| 16:28 | "four twenty-eight" |
| 50 to 55 person-days | "fifty to fifty-five person-days" |
| 455 requests a second | "about four hundred and fifty-five requests a second" |
| p95 | "p ninety-five" |

## Timing at a glance

| Part | Clock | Slot | Words | Est. at 130 wpm | Share of slot |
|---|---|---|---|---|---|
| Slide 7 | 16:20:00–16:21:30 | 90 s | 161 | 74 s | 83% |
| Slide 8 | 16:21:30–16:22:50 | 80 s | 142 | 66 s | 82% |
| Slide 9 | 16:22:50–16:24:20 | 90 s | 161 | 74 s | 83% |
| Slide 10 | 16:24:20–16:25:35 | 75 s | 136 | 63 s | 84% |
| Slide 11 | 16:25:35–16:27:00 | 85 s | 146 | 67 s | 79% |
| Segment 3 talk | 16:20:00–16:27:00 | 420 s | 746 | 344 s | 82% |
| Slide 23 opening | 16:45:00–16:45:15 | 15 s | 26 | 12 s | 80% |
| Step 1 | 16:45:15–16:46:45 | 90 s | 112 | 52 s | 57% |
| Step 2 | 16:46:45–16:49:15 | 150 s | 164 | 76 s | 50% |
| Step 3 (Pan + Eric) | 16:49:15–16:50:45 | 90 s | 104 | 48 s | 53% |
| Step 4 | 16:50:45–16:51:30 | 45 s | 78 | 36 s | 80% |

The hand-over line to Liu Jin comes after the 16:27–16:29 questions, so it is not inside any slot and not in these counts. Fallback and "if this happens" lines replace part of a step, so they are not counted either.

## The thread through segment 3

The seven minutes answer one question, "where do we split the system?", in five steps. Each slide's one point, in order:

1. **Slide 7 (the answer):** only split where no transaction crosses the line, so we have **three services, not five**. Ends on: this split raises three questions.
2. **Slide 8 (what the answer costs):** the other three decisions answer those questions (events, one login session, refuse when identity is down), and the price is a two-second lag and a purchase pause. Ends on: that pause comes from our one direct call.
3. **Slide 9 (what it looks like):** the logical view shows where that one direct call sits; everything else goes through events. Ends on: why are the lines exactly there?
4. **Slide 10 (why the lines are there):** following a team's life gives six bounded contexts; the four that share transactions stay in commerce; public events are the contract. Ends on: so who uses the platform?
5. **Slide 11 (what it buys us):** both sides use it, reuse is cheap and measured, and the close answers slide 7's opening question.

The demo then shows the same logic live: the three services and the one login (step 1), commerce scaling (step 2), the pipeline that put it there (step 3), and what wasn't shown live (step 4).

Callbacks (planted → paid off, in the same words):

| Planted | Paid off |
|---|---|
| Slide 7: "where to split the system" (first line) | Slide 11 close ("back to where I started: where to split the system?") |
| Slide 7: "only split where no transaction crosses the line" | Slide 8 ("Top left is that rule"); slide 10 ("Then we applied the rule"); slide 11 close |
| Slide 7: "three services, not five" | Slide 9 ("those three services"); slide 11 close; demo step 1 ("three services, not five, behind one login") |
| Slide 7: "this split raises three questions" | Slide 8 ("The other three answer its three questions") |
| Slide 7: identity "used through one small interface" | Slide 9 red arrow ("That's the one small interface from slide 7") |
| Slide 7: "nothing needs to call notifications" | Demo step 1 ("Remember, nothing needs to call notifications") |
| Slide 7: the payment, "four steps, one commit" | Slide 10 ("like the payment from slide 7") |
| Slide 8: "purchases pause while identity is down" | Slide 8 bridge ("That pause comes from our one direct call") → slide 9 red arrow; demo step 4 ("The identity outage from slide 8") |
| Slide 8: "one login session" | Slide 9 ("that's the one login session from slide 8"); demo step 1 (same words) |
| Slide 9: "an audit queue" | Slide 11 ("Remember the audit queue?") |
| Liu Jin, slide 14: "Commerce adds tasks as requests per task rise" | Demo step 2 (same words) |
| Pan, slide 20: "The only thing that deploys to production is merging a promotion pull request" | Demo step 3, Pan (same words) |
| Demo step 1: product pages "served by commerce" | Step 1 bridge into step 2 (the load test is browsing those pages) |
| Demo step 2: commerce scaling | Step 3, Pan ("the one you just watched scale") |
| Slide 23: "Three things" | Step 4 close ("the buying journey, auto-scaling and the pipeline") |

---

# Segment 3: Logical Architecture & Design

Clock 16:20:00–16:29:00 (7 min talk, then 2 min of questions from 16:27).

**Coming in:** after his questions, Lim says *"Eric will take you through the logical architecture."* Unmute and start straight away.

## Slide 7 — Why three services, not five

**Slot** 90 s (16:20:00–16:21:30) · **Words** 161 · **Est.** 74 s at 130 wpm (83% of slot)

> Thanks, Lim. I'm Eric. Our biggest decision was where to split the system.
>
> The obvious answer: one service per business area, like catalogue, orders and payments.
>
> But look at a Stripe payment. [point at the big box, top left] In one database transaction, we confirm the payment, create the order, take the stock and add the buyer to the team. Four steps, one commit. If any step fails, all of it is undone, and that's what keeps a buyer's money safe. [pause: this is the one idea they should remember]
>
> Put those steps in separate services, and we'd need complicated undo logic everywhere. [point at "Rejected: five services", bottom right] We estimated five services at 50 to 55 person-days, our whole budget, so we said no.
>
> [point at "Our rule", top right] So our rule: only split where no transaction crosses the line.
>
> [point at the two small boxes, bottom left] Identity and notifications pass that test. Identity is used through one small interface, and nothing needs to call notifications. That leaves two reusable platform services, and one core service, commerce, that keeps the money flow together. Three services, not five.
>
> But even this split raises three questions.

## Slide 8 — Four decisions shape the logical design

**Slot** 80 s (16:21:30–16:22:50) · **Words** 142 · **Est.** 66 s at 130 wpm (82% of slot)

> [Lim: next slide] Top left is that rule. The other three answer those three questions.
>
> [top right] How do services talk without a shared transaction? Through events. We save each event in the same transaction as the change itself, which means an event only goes out if the change really happened. We never announce a payment that was rolled back.
>
> [bottom left] How does each service know who you are? All services share one login session, so any service can check your role without asking identity.
>
> [bottom right] What if commerce can't reach identity? It refuses the purchase, because a purchase is either fully done, or not done at all.
>
> [point at the strip at the bottom] The price we pay: notifications arrive about two seconds later, and purchases pause while identity is down. We tested that outage locally; it's in our backup slides.
>
> That pause comes from our one direct call between services. Here's where it sits.

**If late:** name the four decisions and skip the strip at the bottom (the "price we pay" lines).

**Note:** the run book ends this slide with "We'll show you that live." Don't say it: the identity outage drill was cut from the live demo on 19 Oct. The line above points to the backup slides (B12) instead.

## Slide 9 — Logical view: one direct call, everything else by events

**Slot** 90 s (16:22:50–16:24:20) · **Words** 161 · **Est.** 74 s at 130 wpm (83% of slot)

> [Lim: next slide; walk it top to bottom] This is the logical view of those three services. The two shared platform services are at the top, the business application, commerce, is at the bottom, and the event system sits between them. Requests enter through CloudFront and a load balancer, which picks the right service.
>
> [point at the red arrow] The red arrow is that direct call. Commerce asks identity, "is this user still active?" That's the one small interface from slide 7. It's the only place where one service waits for another, so it's the only place we had to plan for that failure.
>
> [trace the event path] Everything else goes through events, so nothing else waits. Commerce saves each event in a small outbox table, a relay sends it to an SNS topic, and each listener gets its own queue. Today: notifications and an audit queue. [hold the audit queue: its cost comes on slide 11]
>
> Each service has its own database, and they all share one session store: that's the one login session from slide 8.
>
> So why are the lines exactly there? The domain model.

**If asked for a simpler picture:** backup B13 is the same view without UML notation.

## Slide 10 — Bounded contexts decide the services; events are the contract

**Slot** 75 s (16:24:20–16:25:35) · **Words** 136 · **Est.** 63 s at 130 wpm (84% of slot)

> [Lim: next slide] We found those lines by following the life of a team. It's created, people join and pay, then it succeeds, or expires and gets refunded. That gave us six bounded contexts, in Domain-Driven Design terms.
>
> [point at the commerce box] Then we applied the rule. Four of them, catalogue, group buying, orders and payments, share transactions, like the payment from slide 7. So they live together in commerce. [point at the two boxes below] Identity and notification each stand alone.
>
> [point at the event table on the right] Inside commerce, these areas talk through internal events, like "team expired". When an event leaves the service, it becomes a public event with a version number: "team expired, version 1". That's the contract between services. It only carries IDs, never names or emails, so personal data stays in identity. Any new service can listen to it without knowing how commerce works inside.
>
> So who uses the platform?

**If asked how the contexts relate:** open backup B3 (context map).

## Slide 11 — How producers and consumers use the platform, and what reuse costs

**Slot** 85 s (16:25:35–16:27:00) · **Words** 146 · **Est.** 67 s at 130 wpm (79% of slot)

> [Lim: next slide] Two sides. [point left] Producers: sellers and admins post listings, and buyers start and share teams. [point right] Consumers: they browse, join, track orders and get notified. Both sides use the same account.
>
> [point at the middle column] And because whatever happens in commerce becomes an event, reuse is cheap, and we can measure it.
>
> [point at the table, bottom left] Remember the audit queue? Two lines of configuration, and no change to commerce. A whole new service is one entry in our infrastructure template, which sets up everything it needs to run. [point at "One public contract"] All clients use one published API description, and our web app's client code is generated from it. [point at the strip at the bottom] And every new service starts with login, error handling, tracing and duplicate protection already built in.
>
> So, back to where I started: where to split the system? Only where no transaction crosses the line. That gave us three services, not five, two of them reusable platform services. Happy to take questions.

**If late:** drop the "every new service starts with" sentence; the strip at the bottom says it.

## Questions (16:27–16:29), then the hand-over

[16:27:00: questions, 2 minutes. Lim repeats each question in one line and names who answers. At 16:28 Yorik starts the k6 load test, muted; Lim confirms it in the backchannel. Suggested answers are in the run book, p. 10; keep each to 30–45 s.]

**Hand-over to Liu Jin (after the questions):**

> Thanks. That's the logical side; Liu Jin will show how these three services run on AWS.

---

# Between segments (backstage, muted)

- **Slides 12–22:** keep an eye on the CloudWatch dashboard (Tab 5). When commerce reaches a new task count, post **SCALED n** in the team backchannel (never the Zoom chat). The run book expects the third task around 16:41–16:44.
- **16:44:30:** Lim stops sharing. Share the demo window (not the desktop), with Tab 1 on slide 23. Do it even if Pan is still answering a question; his answer carries on over your screen. Stay muted.
- **Pan says *"Eric, over to you."*:** unmute and start.

Screen layout (one browser window, tabs left to right, plus one terminal; notifications off; zoom 125%):

| Tab | What's there |
|---|---|
| 1 | Deck at slide 23 (`swe5001-presentation-v3.html#23`); used again for slide 24 and the backups (press B) |
| 2 | SPA product list, signed in as the demo admin (Google) |
| 3 | Admin product list in the SPA |
| 4 | `/api/v1/notifications` for the demo buyer (JSON) |
| 5 | CloudWatch dashboard `blocbuy-swe5001` (1-hour window, auto-refresh 10 s; Edge, Services, Messaging rows) |
| 6 | GitHub: promotion pull request #534 (17 Oct), Checks tab, and its `swe5001-deploy` run. **Never click Merge.** |
| T | Terminal mirroring k6 on the load machine |

---

# Segment 6: Demo

Clock 16:45:00–16:55:00. The demo talk ends at about 16:51:30; the rest is questions.

For the whole demo: narrate results, not clicks ("task count went from two to three", not "now I'm clicking the services row"). No troubleshooting on air: if a view isn't right within 20 seconds, use the fallback line and move on.

## Slide 23 — Live demo (opening)

**Slot** 15 s (16:45:00–16:45:15) · **Words** 26 · **Est.** 12 s at 130 wpm (80% of slot)

> [Tab 1, slide 23] Thanks, Pan. Three things: the buying journey, auto-scaling under a load test running since 16:28, and the pipeline that deployed it. First, the use cases.

## Step 1 — Buy as a customer, across three services

**Slot** 90 s (16:45:15–16:46:45) · **Words** 112 · **Est.** 52 s at 130 wpm (57% of slot) · proves functionality (guideline 6a)

> [Tab 2: product list] This is what a buyer sees: team prices and live countdowns, served by commerce.
>
> [point at the signed-in user in the header] Up here, I'm signed in with Google, through the identity service.
>
> [Tab 3: admin product list] And because of that one login, this admin page works too: commerce reads my role from the shared login session, without calling identity. That's the one login session from slide 8.
>
> [Tab 4: refresh] Last, these notifications. Remember, nothing needs to call notifications: the notification service created these from events it received.
>
> So that's three services, not five, behind one login.
>
> [bridge] Now, those product pages come from commerce, and browsing them is exactly what the load test has been doing since 16:28. Let's see what that did to commerce.

**Expect:** all four views load; at least one notification is listed.

**Fallback** (a view isn't right within 20 seconds): [post **CLIP 1** in the backchannel; Liu Jin shares the clip; keep talking]

> This one's not loading in time, so here's the same step from our recording instead. What you're seeing now is recorded, not live.

[If Liu Jin shows the screenshots from `fallback/1-use-cases` instead of a clip, say "These are screenshots we took before the talk." If there is no recording, use Plan C (run book §7): show the local integration-test result and say "verified locally, not yet on AWS."]

**If late:** show only the product list (Tab 2) and the notifications (Tab 4).

## Step 2 — Auto-scaling under a load test

**Slot** 150 s (16:46:45–16:49:15) · **Words** 164 · **Est.** 76 s at 130 wpm (50% of slot) · proves scalability and performance (guideline 6b); Yorik mirrors k6

> [Tab 5: CloudWatch dashboard, Services row] Since 16:28, that test script has been sending browsing traffic, ramping up towards three times our measured baseline.
>
> [point at commerce running tasks] Commerce started with two tasks. As the traffic grew, it scaled up to what you see here: [read the task count off the screen] tasks.
>
> [Edge row: point at the request count, then the p95 response time] [say this only if the graph shows it] And it stayed fast. Response times went up while the new tasks were starting, then came back down. Right now the server's p95 is [read it off the screen] milliseconds, and almost no requests failed.
>
> [Terminal T: k6 live summary] Now, k6 on the laptop that sends the load may show about half a second and dropped requests. That's the laptop's limit, not the service, which is why we read the server numbers on the dashboard.
>
> [back to Tab 5] On 19 October, the same test took commerce from one task to three, at about 455 requests a second. So this is what Liu Jin showed on slide 14: commerce adds tasks as requests per task rise, on its own.
>
> [bridge] But how did that commerce code get onto AWS in the first place? That's the pipeline. Pan?

**Expect:** commerce running tasks above 2 (it reached 3 on 19 Oct); server p95 back under 100 ms once the new tasks run; almost no errors. k6 may show about 0.5 s and dropped requests: that's the laptop's limit, not the service.

**Fallback** (the live view isn't right within 20 seconds): [post **CLIP 2** in the backchannel; Liu Jin shares the clip]

> The live dashboard isn't playing along, so here's the same step from our recording. This part is recorded, not live.

[If Liu Jin shows the 19 Oct dashboard graphs (evidence folder `20261019-v3-autoscaling`) instead of a clip, say "These are the saved graphs from our 19 October run, not live: commerce goes from one task to three, and here's the server response time."]

**If commerce is still at two tasks at 16:46** (run book §7): show what is there honestly, then the 19 Oct graphs.

> Commerce is still at two tasks: the alarm needs three minutes of data before it acts. Here's what the same test did on 19 October: one task to three.

## Step 3 — The pipeline that deployed it

**Slot** 90 s (16:49:15–16:50:45) · **Words** 104 (Pan 102, Eric 2) · **Est.** 48 s at 130 wpm (53% of slot) · proves maintainability (guideline 6b). **Pan narrates; Eric clicks.**

[Eric: Tab 6, pull request #534, Checks tab. Never click Merge: a promotion destroys this environment.]

> **Pan:** Thanks, Eric. Remember, the only thing that deploys to production is merging a promotion pull request. This is the promotion that put today's code on AWS. It needed three required checks and one approval before it could merge. [point at the checks]
>
> **Pan:** Eric, could you open the deploy run? [Eric opens the `swe5001-deploy` run]
>
> **Pan:** So this is what that merge started. The run built the images, created the environment from scratch, tested it, released it and destroyed it. End to end, it took [read the total run time off the screen].
>
> **Pan:** And the evidence, please. [Eric opens the evidence artifact list] Here's the evidence it saved.
>
> **Pan:** So today's environment, the one you just watched scale, was built from exactly these images. Eric, back to you.
>
> **Eric:** Thanks, Pan.

**Expect:** checks green; run green including destroy; artifact present.

**Fallback** (GitHub slow or logged out, or the page isn't right within 20 seconds): [Eric posts **CLIP 3** in the backchannel; Liu Jin shares the clip, or Pan shows the screenshots from `fallback/3-pipeline`]

> **Eric:** GitHub isn't cooperating, so here are the same pages from our recording. This part is recorded, not live. [If it's the screenshots: "These are screenshots of the same pull request and run, taken before the talk."]

[Pan carries on with the same narration over the clip or screenshots.]

## Step 4 — Close the demo

**Slot** 45 s (16:50:45–16:51:30) · **Words** 78 · **Est.** 36 s at 130 wpm (80% of slot) · reliability (not shown live; two drills tested locally)

> [Tab 5: commerce task count] One last look: commerce is still at its peak, and when the load test ends, it'll scale back down to two tasks by itself.
>
> What we didn't show live is failure handling. The identity outage from slide 8 and a bad message going to the dead-letter queue were tested locally, and the results are in our backup slides; stopping a server is still to run on AWS.
>
> So: the buying journey, auto-scaling and the pipeline. That's our demo.

[16:51:30–16:53: questions on the demo; Lim routes them. If asked about failures, open backup B12 from Tab 1 (press B for the index). At 16:53 switch to Tab 1, slide 24, for Lim.]

**Note for the team:** this corrects the run book's step 4 wording ("We tested those locally" for all three drills). Slide 5, backup B12 and the 15 Oct local rehearsal evidence (`docs/swe5001/evidence/20261015-local-failure-rehearsal/`) show only the identity outage and the bad message as tested locally; stopping a server (self-healing) is "not run on AWS" with no local result. Slide 23's line "Failure handling: tested locally, not shown live" lists all three, so it has the same problem.

---

# If this happens

**(a) Step 1, Tab 4 shows no notifications for the demo account:**

> This account has no notifications yet, because payments are switched off on this test environment, so here's the same view from our recording.

[Then post CLIP 1, as in the step 1 fallback.]

**(b) Product images are missing on the storefront (Tab 2 shows striped tiles instead of photos):**

> The product images aren't loading on this environment, so you'll see placeholder tiles, but the team prices and live countdowns are what commerce serves, and that's the part to look at.
