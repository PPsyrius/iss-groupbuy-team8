# Eric's Q&A prep (SWE5001 final presentation, Team 8 BlocBuy)

For Wang Ziang (Eric). Wednesday 21 Oct 2026, Zoom. Covers the segment 3 questions (slides 7 to 11, Q&A 16:27 to 16:29) and the demo questions (slides 23 to 24; Q&A 16:53 to 16:55, plus the spare time from 16:51:30). Pairs with [`speaker-script-eric.md`](speaker-script-eric.md) (the talk). This file is for the questions.

How to use it:

- Each answer is written to be said in 30 to 45 seconds (65 to 100 words). Put it in your own words.
- Lim repeats each question in one line and names who answers. If the name is not yours, say the bridge line and hand over. Do not answer for someone else at length.
- "Open" is the backup slide to bring up (press B for the index, click the row). Open one only if it helps.
- "Source" says where the answer comes from, so you can check it. Short names are listed below.
- Where something is only tested locally, or not built, the answer says so. Keep that wording. The run book rule is: never present a local result as an AWS result.
- State read for this file: run book and deck v3 (19 Oct, #556) and `dev` on 19 Oct.

Source key:

| Short name | File |
|---|---|
| RB | `swe5001-presentation-script-v3.pdf` (page numbers given) |
| DECK | `swe5001-presentation-v3.html` (slide or backup number given) |
| ADR 0005, 0007, 0008 | `backend/docs/adr/0005-stripe-checkout-env-flag.md`, `0007-cross-service-events-sns-sqs.md`, `0008-dual-deployment-topology.md` |
| PLAN | `docs/swe5001/microservice-refactoring-plan.md` |
| BACKEND-PLAN | `docs/swe5001/impl-backend-architecture.md` |
| AWS-PLAN | `docs/swe5001/impl-aws-infrastructure.md` |
| NOTIF | `backend/docs/design/notification-persistence.md` |
| DLQ-RUNBOOK | `backend/docs/operations/notification-dlq-redrive.md` |
| RELAY-RUNBOOK | `backend/docs/operations/outbox-relay-recovery.md` |
| EV-16OCT | `docs/swe5001/evidence/20261016-http-baseline-dashboard/README.md` |
| EV-19OCT | `docs/swe5001/evidence/20261019-v3-autoscaling/README.md` |
| EV-IMAGES | `docs/swe5001/evidence/20261019-ecs-demo-images/README.md` |
| EV-LOCAL | `docs/swe5001/evidence/20261015-local-failure-rehearsal/README.md` |
| TF-SERVICES | `infra/swe5001/runtime/services.tf` |
| TF-MESSAGING | `infra/swe5001/modules/messaging/main.tf` |

---

## Read this first (five things that can trip you on the day)

1. **"We'll show you that live."** The run book's slide 8 line ends with it (RB p. 8), and Yorik's slide 17 line says "You'll see the dead-letter queue in the demo" (RB p. 13), and his identity-down answer says "We show this live" (RB p. 13). The three failure drills were cut from the live demo (RB p. 17, slide 23). Your polished script (speaker-script-eric.md) already fixes slide 8. Tell Lim and Yorik about the other two.
2. **"We tested those locally" in Step 4 includes "stopping a server".** Slide 5 and backup B12 say self-healing is "not run on AWS" with no local result. Only three things have a local result: the identity outage, the bad message to the dead-letter queue, and queue scaling. Say "tested locally" for those three only.
3. **Step 3: "built from exactly these images".** The repo ties today's environment to environment run 39198123242 at commit `5e83d63` (EV-19OCT, EV-IMAGES). Nothing in the repo links it to promotion #534 (17 Oct). Check this with Liu Jin at 15:30. If you cannot confirm, say "this is the promotion that released these images".
4. **The environment on 19 Oct:** checkout is switched off (`BLOCBUY_STRIPE_CHECKOUT_ENABLED = "false"`, TF-SERVICES lines 34 and 111), so Start and Join are disabled. The demo account had no notifications. Every storefront image is broken, because the commerce seed uses `cdn.example.com` placeholders (issue #557). Run book Step 1 expects at least one notification. Check at 15:30. If it is still so, use answers F5 and F6 and the two "If this happens" lines in speaker-script-eric.md, and say it before they ask.
5. **Slide 11's bottom strip** says every new service starts with outbox and duplicate protection. In the repo the outbox is wired only into commerce and the duplicate ledger lives only in notification. If asked, use E3's wording, not the strip's.

## Numbers to have in your head

| Fact | Value | Source |
|---|---|---|
| Services / contexts | 3 services; 6 bounded contexts; 4 of them (catalogue, group buying, orders, payments) in commerce | RB p. 8 to 9 |
| Rejected option | Five services, 50 to 55 person-days, the whole budget (about 50 person-days in total) | RB p. 8; PLAN section 2.2 |
| Architecture rules | 18, run with the unit tests | DECK B5 (18 `@ArchTest` rules counted in the repo) |
| Identity call | Connect 500 ms, read 1,500 ms, no retry, no cache, fails closed | ADR 0008 |
| Relay | Up to 100 rows per 2-second tick; about 50 events a second (calculated, not measured); 10 attempts then marked failed | ADR 0007; `OutboxRelayService` |
| Queues | Visibility 60 s; DLQ after 5 receives; notification queue keeps 4 days, DLQ 14 days; alarm on the first dead letter | ADR 0007; TF-MESSAGING; DLQ-RUNBOOK |
| Scaling policy | Commerce 2 to 6 tasks on requests per task, target 12,000 a minute (200 a second), 60 s out / 180 s in; notification 1 to 4 on backlog per task (50); identity fixed at 1 | DECK slide 14, B7; AWS-PLAN 6.4 |
| 16 Oct (one task, older single-application image) | 10 to 200 req/s, 57,600 requests per run, zero errors, p95 at most 57 ms; saturation not observed | EV-16OCT |
| 19 Oct (three services) | Commerce 1 to 2 at 15:55:02, 2 to 3 at 15:58:02; about 455 req/s on 3 tasks; load balancer p95 13 to 50 ms; one 0.5 vCPU task saturated near 280 req/s | EV-19OCT |
| 19 Oct, the limits | k6 dropped 155,554 scheduled iterations; k6 all-run p95 1,671 ms; the analyzer rejects the run as V3 evidence | EV-19OCT |
| 19 Oct, the slow window | One saturated task, 15:52 to 15:55: 272 to 286 req/s, load balancer p95 about 1.9 s | EV-19OCT |
| Cost / pipeline | US$0.49 for 7 sessions; 51 min promotion run, 115 resources destroyed in 709 s | DECK slides 12, 20 |

Backup slides you own (RB p. 27 to 28): B1a decisions AD-02 to AD-06, B3 context map, B4 aggregates, B5 layers and architecture rules, B10 API access, B13 logical view simplified. Others you may need: B2 limitations (Liu Jin), B6 payment path and B15 expiry steps (Yorik), B11 analytics and B12 scale-out and resilience results (Lim).

Rehearse first (the rows the run book gives you): A1, A2, C1, B3, E2, E3, F1, F2. Next most likely: B1, B2, B4, C2, F3, F4, F5.

---

## A. Service boundaries and why three services

### A1. Isn't commerce still a monolith? Why not one service per business area?

- **Run book:** segment 3 Q&A, first two rows (RB p. 10). Who answers: Eric.

> Yes, on purpose. When a Stripe payment comes in, one database transaction confirms the payment, creates the order, takes the stock and adds the buyer to the team. If any step fails, all of it is undone, and that protects the buyer's money. Splitting those steps across services would need undo logic everywhere. We estimated five services at fifty to fifty-five person-days, our whole budget. So our rule is: split only where no transaction crosses the line. Inside commerce we keep clear layers, and moving payments out is our documented next step.

- **Open:** slide 7 (the four steps and the rejected five-service option). B1a (AD-02). B2 row AISS-02 for "next step".
- **Source:** RB p. 8, 10; PLAN section 2.2 ("Anticipated challenge: commerce-svc is still a monolith"); ADR 0008 Consequences.
- **Limit to keep:** moving payments out is roadmap, not started.

### A2. What did Domain-Driven Design actually change? How do you stop the boundaries eroding?

- **Run book:** segment 3 Q&A, DDD row (Eric); backup B5 is yours (RB p. 10, 27).

> It gave us the candidate boundaries. We followed the life of a team: created, joined and paid, then succeeded or expired. That gave six bounded contexts. Then we checked which ones share a transaction. Catalogue, group buying, orders and payments do, so they stay together in commerce, while identity and notification stand alone. It also made us separate internal events from public, versioned ones. To stop boundaries eroding, eighteen architecture rules run with the unit tests, so a call across a boundary fails the build.

- **Open:** B3 (context map), B5 (layers and the rules), B4 (team and order states).
- **Source:** RB p. 10, 27; DECK B3 to B5; ADR 0008 Guardrails; BACKEND-PLAN section 6.
- **If pushed on B4:** a team can move only once, from OPEN. FAILED and CANCELED exist in the status table but nothing in the code triggers them. Say so (RB p. 27).

### A3. Why is identity a separate service, if every purchase depends on it?

- **Run book:** not a listed row. Follows from slide 7 ("Identity: used through one small interface").

> Because identity and notification are the two parts any other kind of group buying would reuse as they are, and no write transaction crosses into identity. Commerce uses identity through one small interface; we found that by counting the calls in the code. The price is real: checkout needs identity to be up, and identity runs as a single task. We chose to fail closed rather than risk a wrong refund, and we list it as a known limitation, in backup B2. The later fix is a cache or a circuit breaker.

- **Open:** B2 (AISS-03), B3 (context map: identity to commerce is customer and supplier, one synchronous call).
- **Source:** PLAN section 2.2 (coupling evidence: identity used through `UserAppService.requireActive`); AWS-PLAN 6.4 ("Identity scaling descoped": identity is a single point of failure for commerce's fail-closed checks); DECK B2, B3.

---

## B. Communication: the direct call, events, outbox, consumer, dead-letter queue

### B1. You say there is only one direct call. Is that really true, and why that one?

- **Run book:** not a listed row. Follows from slide 9.

> It's one dependency in one direction: commerce asks identity whether a user is still active, through a single interface. To be exact, identity serves three internal endpoints behind that interface: the active check and two nickname lookups for one admin page. It's the only place one service waits for another, so it's the only place we designed for failure: half a second to connect, one and a half seconds to read, no retry, no cache, and it fails closed. Everything else goes through events. Those endpoints aren't routed by the load balancer, but there's no service token yet.

- **Open:** B13 (simplified logical view, "the only direct call"), B10 (internal endpoints), B2 (AISS-04: network rules only, no service token).
- **Source:** ADR 0008 (Commerce reaches identity only through `UserAppService`; Internal endpoints); `InternalUserController` (active, public-nickname, public-nicknames); DECK B10.

### B2. How do you make sure events are never lost?

- **Run book:** "How do you make sure events are never lost or doubled?" (RB p. 13). Run book owner: Yorik, in the 16:36 window. In your window you may answer this half. Bridge: "Yorik built the relay and I built the consumer, so I'll cover the loss side and the duplicate side."

> Each event is saved in an outbox table in the same transaction as the business change. So an event exists only if the change committed, and a committed change always has its event. A relay then sends up to a hundred rows every two seconds to SNS. If that fails, the row stays and is retried with backoff. After ten failed attempts it's marked failed, logged as an error, and an operator can replay it from a runbook. The honest limit: delivery is at least once, and on AWS we've only checked the SNS-to-SQS routing with test events.

- **Open:** slide 17 (the three "if" cards), B15 (steps 2 to 7), B12.
- **Source:** ADR 0007 (Outbox, Relay, review amendments); `OutboxRelayService` (batch 100, 10 attempts); RELAY-RUNBOOK; DECK slide 5 ("SNS to SQS routing confirmed with test events").
- **Note:** slide 17 says a failed send "stays in the outbox and is retried". That is true up to ten attempts, then the row is marked failed. Say the ten if asked.

### B3. What is in an event, and why do events have version numbers?

- **Run book:** segment 3 Q&A, "Why do events have version numbers?" (Eric, RB p. 10).

> Every event uses the same envelope: event ID, type, version, time, producer, trace ID, the aggregate ID and a payload. The payload carries IDs and references only, never names or emails, so personal data stays in identity. Version numbers matter because other services depend on these events, like a public contract. A listener acknowledges and ignores a version it doesn't know, so we can add version 2 without updating every service at once. Changes are additive, and a breaking change becomes a new version. Today there are three events, all version 1: team succeeded, team expired and payment succeeded.

- **Open:** slide 10 (the event table), B3 (published language between commerce and notification).
- **Source:** ADR 0007 (Envelope, Consumer); `IntegrationEventEnvelope`, `TeamExpiredV1`, `PaymentSucceededV1` in `backend/blocbuy-client/.../events/contract/`; NOTIF "Event handling".

### B4. Can a message arrive twice, or out of order? What happens then?

- **Run book:** closest rows are "never lost or doubled" (Yorik) and the failure slide 17. You built the consumer (RB p. 24 effort table), so expect depth here.

> Yes to both, because standard SNS and SQS deliver at least once and don't guarantee order. For duplicates, the notification consumer records the event ID and its own name in the same transaction as the notifications, so a repeat does nothing. We key on our own event ID, never the SQS message ID, because a retry gets a new one. Order doesn't matter for notifications, since each event is an independent fact, and FIFO would cost more. A consumer that kept state would need a sequence number, which we don't have. Our duplicate tests ran locally, not on AWS.

- **Open:** slide 17, B15 (steps 6 and 7).
- **Source:** ADR 0007 (Consumer; alternatives rejected: FIFO); NOTIF "Consumed-message ledger" and "Event handling"; EV-LOCAL.
- **If pushed:** the envelope carries the aggregate ID and a timestamp, but no per-aggregate sequence number. The notification table also has a second guard: one row per event and user.

### B5. What happens to a message that keeps failing? Who is told, and what do you do with it?

- **Run book:** slide 17 ("a dead-letter queue and an alarm"). Your area: the effort table lists "dead-letter redrive" for you (RB p. 24).

> The consumer deletes a message only after its notifications and ledger record have committed. If a message is malformed or the database is failing, it stays on the queue and reappears after sixty seconds. After five receives SQS moves it to the dead-letter queue, and an alarm fires on the first message. Then a runbook applies. A malformed message never succeeds on replay, so we record and delete it. A retryable failure is redriven after the fix, which is safe because of the duplicate protection. We've tested the five-receive rule locally, not on AWS.

- **Open:** slide 17, B12 (bad message row), B7 (messaging row).
- **Source:** NOTIF "SQS consumer"; DLQ-RUNBOOK sections 1 to 5; EV-LOCAL (receive counts 1 to 5, then the dead-letter queue); DECK slide 5, B12.
- **If pushed:** the redrive move task cannot pick messages, so classify first. The alarm email only reaches people who confirmed their subscription; confirming the alarm emails is still an open item before the final release (RB p. 23, #515; DECK slide 5). The DLQ keeps messages 14 days.

---

## C. Shared login session and what happens when identity is down

### C1. How does each service know a user's role? What if the role changes, or the user is disabled?

- **Run book:** segment 3 Q&A, "How does each service know a user's role?" (Eric, RB p. 10).

> When you sign in, identity writes your roles and permissions into a shared login session in Valkey. The other services read that session on each request, so they never ask identity for your role. The trade-off is that, outside identity, a role change takes effect the next time you sign in. A disabled account is handled differently: commerce checks with identity on purchases and admin changes, so that user is stopped there. And if the session snapshot is missing or malformed, everything is denied rather than allowed.

- **Open:** B1a (AD-05 and AD-06), slide 8.
- **Source:** ADR 0008 ("Roles travel in the session"; #467 amendments); `SessionStpInterface` (missing or malformed snapshot returns no roles); BACKEND-PLAN WP-B4 and findings F4 (callers of `requireActive`).

### C2. What happens when identity is down?

- **Run book:** slide 17 Q&A (Yorik, RB p. 13). Slide 8 is yours, so you may be asked first. Bridge: "That's the fail-safe decision from slide 8. Yorik can add the payment side."

> Browsing keeps working, because it needs no identity call. Purchases and admin changes fail safely with a server error. For a Stripe payment, the whole transaction is rolled back and Stripe redelivers the webhook later, so a buyer is never wrongly rejected. The limit is that checkout needs identity, which runs as a single task. We tested this locally, with a stopped identity stand-in and real databases, and when identity came back the redelivery succeeded, with no duplicate effects. We haven't run it on AWS and we're not showing it live. The result is in backup B12.

- **Open:** B12 (identity outage row), B2 (AISS-03).
- **Source:** EV-LOCAL (identity stopped: browse 200, START and webhook 500; restored: redelivery 200, one membership, one order, zero refunds); ADR 0008; RB p. 13; NFR-66 in `docs/quality/nfr-register.md`.
- **Do not say:** "we show this live" (see "Read this first", item 1).

### C3. What if the session store goes down? Why a shared session and not signed tokens?

- **Run book:** not a listed row. Follows from slide 8 (shared session).

> If Valkey is down, services can't read sessions, so authorization fails closed: no session, no roles. The cache is a single node in one zone, a cost choice listed in backup B2, and I'm not aware that we've run an outage drill for it. We chose a shared session over signed tokens because revocation is instant, the cookie settings stay as they are, and it needed little code change. Tokens would need a short expiry with refresh, or a deny list, which we judged too much for the time we had. For production we'd add a cache replica.

- **Open:** B2 (AISS-01), B1a (AD-05).
- **Source:** `SessionStpInterface`; `docs/swe5001/architecture-options-study/02-proposed-architecture.md` section 6 (shared session chosen, JWT "rejected for the timebox, roadmap item"); AWS-PLAN 6.5 (one Valkey node, no snapshots); RB p. 13 (cache replica for production).
- **Not in the sources:** any outage test of the session store. Do not claim one.

---

## D. Data ownership and consistency across services

### D1. Each service has its own database, but it is one server. Isn't that a shared database in disguise?

- **Run book:** slide 15 is Liu Jin's (4c). **Route to Liu Jin.** Bridge: "Liu Jin owns the persistence slide. From the logical side, here is the short version."

> It's one database server with three separate databases, each with its own login role. A role only has grants on the tables its service owns, and a test proves commerce gets permission denied on identity's tables. Architecture tests also fail the build if one service's code uses another's mappers. One honest detail: every database applies the full set of migrations, so other services' tables exist but stay empty and unreachable. We'd clean that up after the other course ends. And a database can move to its own server without code changes. Liu Jin has more on slide 15.

- **Open:** slide 15, B1a (AD-03), B2 (AISS-05).
- **Source:** ADR 0008 (Data; Consequences: foreign tables stay empty and unreachable); BACKEND-PLAN WP-B8 (`ServiceGrantsIT`); DECK B1a, B1b (AD-11), B2.

### D2. How do the services share data, and what does a user see while an event is in flight?

- **Run book:** not a listed row. Follows from slides 8 and 15.

> They don't share tables. Commerce works out the recipients inside its own transaction and puts only their IDs in the event, so notification never calls commerce back. For names, such as buyer nicknames on an admin page, commerce asks identity in batches of up to a hundred. The price is time. The relay runs every two seconds, so a notification appears about two seconds after the commit, while the money path stays inside one transaction. After a fresh deployment, SNS can take up to fifteen minutes to start routing, so our deploy waits for that.

- **Open:** slide 8 (cost strip), slide 21 (finding 2, Pan's), B15.
- **Source:** ADR 0007 (Outbox, Consequences); ADR 0008 (#469 amendment); NOTIF "Use case and boundaries"; DECK slide 21.
- **Route to Pan** if asked more about the fifteen-minute SNS delay.

---

## E. Platform reuse: producers, consumers, what the platform provides

### E1. What makes this a platform rather than one application? What would a second business need?

- **Run book:** segment 1 Q&A, "What makes this a platform" (Lim, then Eric: "Eric covers this in segment 3") and "What would a second business need?" (Lim). RB p. 6.

> Two of our three services are shared: identity and notification. Another kind of group buying, like event tickets, would reuse sign-in and notifications as they are. A new listener is a new queue with a filter on the existing topic; the audit queue proves it, with two lines of Terraform and no change to commerce. A new service starts as one entry in our infrastructure template, plus a thin entry point on the shared code. A second business would still bring its own catalogue and rules. We haven't built a second business yet; the future ideas are on slide 3.

- **Open:** slide 11 (measured reuse table), slide 3, B13.
- **Source:** RB p. 6; DECK slides 3, 11; TF-MESSAGING lines 19 to 22 (`consumers` and `filters`); BACKEND-PLAN section 3 (entry points).

### E2. How would outside partners use the platform?

- **Run book:** segment 3 Q&A (Eric, RB p. 10). Backup B10 is yours.

> Through one published API description, contracts/openapi.json. The web app's client code is generated from it, and a check on every pull request catches drift. There are also the versioned events, which a new service can subscribe to. Internal endpoints aren't routed by the load balancer and aren't in the public contract; a test checks that. What isn't built, and I'd rather say so: an API gateway, keys per partner, rate limits and a token between services. The way forward is API Gateway, or load-balancer and WAF rate rules behind CloudFront.

- **Open:** B10.
- **Source:** RB p. 10; DECK B10; ADR 0008 (Internal endpoints; `InternalPathsContractTest`).

### E3. Does anything read the audit queue yet? And what does reuse really cost?

- **Run book:** segment 3 Q&A, "Does anything read the audit queue yet?" (Eric, RB p. 10); slide 11's "what reuse costs".

> Not yet. It's a queue that receives all team events, filtered by event type, to show how cheap a new listener is. The measured cost was two lines of Terraform and zero changes to commerce. On the code side, let me be precise. The platform library holds the topology switch, trace-ID propagation, the cache encryption settings and per-service database grants. The outbox is wired into commerce, and the duplicate ledger lives in notification. So the patterns are proven twice, but they aren't one packaged dependency yet.

- **Open:** slide 11, B7 (messaging row).
- **Source:** RB p. 10; TF-MESSAGING (audit filter is event type prefix "Team"); `backend/blocbuy-platform/` (topology, trace propagation, `application-aws.yml`, grants callback); ADR 0007 (Ownership: identity and notification start with no relay or writer); BACKEND-PLAN WP-B3 (rescoped).
- **Why this wording:** slide 11's strip says every new service starts with the outbox and duplicate protection built in. The repo supports "patterns proven", not "one dependency". See open point 2 at the end.

---

## F. The demo

### F1. How did you choose the scaling target?

- **Run book:** demo Q&A (Eric, RB p. 20).

> From our 16 October load test. One task handled up to 200 requests a second with fast responses and errors under one percent, and it still had room, so 200 is the highest rate we tested, not a measured limit. Times sixty, that's 12,000 requests per task per minute. On 19 October, one half-vCPU commerce task saturated near 280 a second, so the target sits below its ceiling, and with it commerce scaled itself from one task to three. A caveat: the 16 October test ran on our older single-application image.

- **Open:** slide 14 (capacity statement), B12.
- **Source:** RB p. 20; EV-16OCT ("saturation was not observed, so this is a tested lower bound"; "transferring the candidate requires V3 validation"); EV-19OCT.
- **If pushed on "why did the demo start at two tasks, but 19 Oct started at one?":** the floor was lowered to 1 for the 19 Oct run so it could show 1 to 3, then set back to 2 (the demo floor, for two-zone availability). EV-19OCT "What the operator changed"; AWS-PLAN 6.4.

### F2. Does adding tasks scale in a straight line?

- **Run book:** demo Q&A (Eric, RB p. 20).

> Not proven yet. On 19 October one task topped out near 280 requests a second, and three tasks served about 455. But the laptop sending the load hit its own limit first and dropped 155,554 scheduled iterations, so that run isn't valid acceptance evidence, and we can't claim a straight line. What we can say is that the service wasn't the limit: at 455 a second the load balancer's p95 stayed between 13 and 50 milliseconds. To prove it, we'd generate the load from inside AWS. The numbers are in backup B12.

- **Open:** B12 (first row, "Partial: the load laptop hit its limit first").
- **Source:** RB p. 20; EV-19OCT ("Does not show: ... one-to-four task linearity"; "To repeat it validly: run the generator closer to AWS").
- **If pushed on scale-out speed:** it is not instant. With one saturated task (15:52 to 15:55) the load balancer p95 was about 1.9 s until the new tasks came up, and 2 requests failed with a 5xx during the second scale-out (EV-19OCT). Scale-out cooldown is 60 s, scale-in 180 s.
- **Do not quote** a "percent of linear". The sources do not give one, and the 455 figure is the laptop's cap.

### F3. Why does k6 on the laptop show different numbers from the server dashboard?

- **Run book:** Step 2 expects it: "k6 on the laptop may show about 0.5 s and dropped requests: that is the laptop's limit, not the service" (RB p. 17, 19).

> They measure different things. k6 times each request from the load machine, through its network and Docker, CloudFront and the load balancer. The dashboard shows what the load balancer sees from the service. On 19 October the load balancer's median was five to seven milliseconds, while k6 saw about 490. That gap sits between the laptop and the load balancer. We haven't isolated which hop, and it wasn't the generator's CPU, which peaked at 56 percent. So on screen, read response time off the server dashboard. k6 tells us how much load we really offered.

- **Open:** the saved graphs in the evidence folder `20261019-v3-autoscaling` (the load-balancer latency image); B12.
- **Source:** EV-19OCT ("Client-side view (k6)"; "The exact cause was not isolated"); RB p. 17 to 19.
- **Fair follow-up:** "so a real user would see the slower number?" A real user also crosses a network, so their time includes that path. The server number says the service itself is not the bottleneck. The sources do not say how much of the 490 ms a real user would see.

### F4. What was live, what was recorded, and what was only tested locally?

- **Run book:** slide 23 footer ("live on AWS; any recorded step is marked"), Step 4, and the rule "never present a local result as an AWS one" (RB p. 18 to 20, 21).

> Live on AWS: the storefront, the admin page and notifications, the CloudWatch dashboard, and the k6 test running since 16:28. The pipeline step shows the record of our 17 October promotion; we never trigger a promotion live, because it would destroy this environment. If a live step fails, we switch to our recording and say so. Tested locally only: the identity outage, the bad message to the dead-letter queue, and queue scaling. Not run anywhere yet: stopping a task. Those results are in backup B12. We never present a local result as an AWS result.

- **Open:** slide 23, slide 5, B12.
- **Source:** RB p. 17 to 21; DECK slides 5, 23, B12; EV-LOCAL.
- **Check before the day:** the run book (19 Oct) says no fallback clips existed yet; they were due on Tue 20 Oct (RB p. 22). If they are not recorded, the fallback is Plan C: the 19 Oct dashboard graphs (RB p. 21). Adjust the "recording" sentence to match.

### F5. Why don't you show a purchase live?

- **Run book:** not a listed row. Slide 5 says "Buyer journey end to end: not run on AWS".

> Two reasons. First, checkout is switched off in this AWS environment's configuration, so Start and Join are disabled. Stripe is test mode only, and turning it on needs test keys. Second, we haven't run the buyer journey end to end on AWS, and slide 5 says so. The payment path was accepted in the Stripe sandbox on 20 September, before the split, and the three-service version is covered by local integration tests. So the live demo shows browsing and the admin page working across the services.

- **Open:** slide 5 (middle column), B6 (payment path, Yorik), B12.
- **Source:** TF-SERVICES lines 34, 111; ADR 0005 (flag defaults to false; test keys only); `backend/docs/payment/stripe-sandbox-acceptance-20260920.md`; EV-LOCAL (healthy purchase locally); DECK slide 4, 5.
- **Not in the sources:** why the flag was set to false for this environment. Do not give a reason beyond "it is off by default and needs test keys".
- **Route to Yorik** for the payment steps (B6).

### F6. The product images are broken and the notification list is empty. Is the environment healthy?

- **Run book:** not a listed row. Use only if it is still true on the day. [`speaker-script-eric.md`](speaker-script-eric.md) has the two short "If this happens" lines for the screen.

> Fair point, and it's a data problem, not an outage. The images are broken because our demo seed points at placeholder image addresses on example dot com, which serves nothing. That's issue 557, and a teammate is migrating the data to fix it. The notification list is empty because notifications come from team and payment events, and with checkout off nobody can start a team here. We checked SNS-to-SQS routing on AWS with test events, and the full event-to-notification path locally. The full path hasn't run on AWS.

- **Open:** B12; the evidence note `20261019-ecs-demo-images`.
- **Source:** EV-IMAGES (10 of 10 storefront images broken, all on `cdn.example.com`; the SWE5006 dev storefront loads); the recording notes in the task brief; `infra/swe5001/scripts/messaging-check.sh` (readiness probes use envelope version 2147483647, which the consumer acknowledges without creating notifications); `docs/swe5001/runbook-environment-session.md` (messaging fan-out check); DECK slide 5.
- **Mismatch to know about:** RB p. 17 says the demo buyer's notifications should show at least one entry "from the smoke run or a seeded expiry". The probes create no notifications. Only a real team event, or the seeded-expiry route, creates one.

---

## G. Hard and awkward questions

### G1. What would you change if you started again?

- **Run book:** not a listed row. Draws on backup B2.

> Three things, all on our limitations list. First, I'd move payments out of commerce, with a saga for refunds, so commerce stops being large. Second, I'd make the identity check more resilient, with a cache or circuit breaker, and put a service token or mutual TLS on the internal endpoints. Third, I'd run the database and cache across two zones. On process, I'd run every failure drill on AWS earlier, because several are only tested locally. And I'd package the outbox and duplicate protection as a shared library.

- **Open:** B2, slide 5.
- **Source:** DECK B2 (AISS-01 to AISS-04); slide 5; ADR 0008 Consequences. The last sentence about a shared library is your own view, consistent with E3.

### G2. What is the weakest part of the design?

- **Run book:** not a listed row.

> The weakest part is that checkout depends on one identity task. It fails safely, but it does fail: while identity is down, nobody can buy. The second weakness is evidence, not design. The failure drills and the buyer journey were verified locally, not on AWS, and slide 5 says so. Third, a role change only takes effect at the next sign-in. We chose each of these knowingly, each has a limit we understand, and each has a fix on the roadmap.

- **Open:** B2, slide 5, B12.
- **Source:** AWS-PLAN 6.4 (identity single point of failure); DECK B2, slide 5; ADR 0008 (next-login rule).

### G3. How does this scale to ten times the load?

- **Run book:** closest is "What breaks first at higher scale?" (Liu Jin, RB p. 13). Bridge: "Liu Jin has the infrastructure numbers; here is the logical view."

> Not by just adding tasks. Commerce is capped at six tasks, which we project at about 1,200 browse requests a second. That's an estimate, not a measurement. Beyond it the single database server is the first limit: we'd give busy databases their own server or read copies. After that comes the event relay, about 50 events a second in our calculation, not measured, and we'd run several relays side by side. Notification already scales on queue backlog, from one to four tasks. Our AWS load tests were browsing traffic only, so purchase throughput at ten times is unknown.

- **Open:** slide 14 (capacity statement), B2 (AISS-05), B12.
- **Source:** RB p. 12, 13; DECK slide 14; ADR 0007 Consequences (relay evolution: lease sharding or change data capture); EV-16OCT, EV-19OCT (browse workload).

### G4. Aren't the single-zone database and cache single points of failure?

- **Run book:** segment 4 Q&A (Liu Jin, RB p. 13). **Route to Liu Jin.** Bridge: "Yes, and Liu Jin can give the detail. From the logical side:"

> Yes, and it was a cost choice. Commerce runs two tasks in different zones, but identity and notification are single tasks, and the database, the cache and the NAT instance each sit in one zone. For production we'd add a standby database and a cache replica; that's AISS-01 in backup B2. Because every service has its own database, moving one to a separate server needs no code change. And this environment is rebuilt from scratch on every release, so we're not claiming durable data here: backups are off by design.

- **Open:** B2.
- **Source:** RB p. 13; DECK B2, B1b (AD-11); EV-16OCT (two commerce tasks in two zones, checked 16 Oct); AWS-PLAN 6.4, 6.5 (identity 1/1/1; RDS single-AZ, backups 0 days).

---

## Questions that belong to someone else (route, with a one-line bridge)

| If they ask about | Route to | Your bridge line | Where it is |
|---|---|---|---|
| Fargate vs Kubernetes, NAT instance, cost (US$0.49) | Liu Jin | "Liu Jin chose the runtime, so he's best placed. Liu Jin?" | RB p. 13; B1b |
| What breaks first at scale; relay ceiling | Liu Jin | "G3 covers my side; Liu Jin has the infrastructure numbers." | RB p. 13 |
| How internal endpoints are protected | Liu Jin | "Not routed by the load balancer, not in the public API, a test checks it. Liu Jin can add the network rules." | RB p. 13; B10 |
| Expiry sequence, payment path, refund retries | Yorik | "Yorik owns that sequence. Yorik?" | slide 16, B6, B15 |
| Why SNS and SQS, not Kafka or RabbitMQ | Yorik | "Fully managed, pay only for use, SNS copies each event to every queue that wants it, queue length drives scaling, and we get dead-letter queues. Kafka is far too much for our volume." | RB p. 13 |
| Pipeline, security scans, rollback, IAM users, why destroy after each release | Pan | "Pan will cover that. Pan?" | RB p. 15 to 16; B8, B9 |
| Analytics, effort split, how work was split | Lim | "Lim owns that. Lim?" | RB p. 7, 20; B11 |
| "Can you show X again?" or questions from earlier segments | Lim routes | Wait for Lim to name the owner. | RB p. 20 |

---

## If you don't know

1. **Say it plainly and offer the record.** "I don't have that number in front of me and I don't want to guess. It's in our evidence folder, and I can follow up after the session." Then stop. The run book says not to improvise on the live environment; offer to show it after the talk or in the report (RB p. 21).
2. **Hand it to the owner.** "That's Liu Jin's (or Yorik's, or Pan's) area, and he'll give you a better answer than I would. Liu Jin?" Use the routing table above.
3. **Say what has and has not been tested.** "We haven't tested that on AWS. What we have is the local result, which is X, and the gap is on our limitations list." Then open B12 or B2 if it helps. Never upgrade a local result to an AWS one.

---

## Points I could not confirm from the sources (check before Wednesday)

1. **Step 3 line "Today's environment was built from exactly these images."** The repo shows environment run 39198123242 at commit `5e83d63` for today's environment. It does not link that run, or those images, to promotion #534 (17 Oct). Ask Liu Jin.
2. **Slide 11 strip, "Every new service starts with ... Outbox, Duplicate protection".** The repo shows the outbox only in commerce and the duplicate ledger only in notification. The platform library (`backend/blocbuy-platform/`) holds the topology switch, trace propagation, TLS settings and database grants. The older plan (PLAN, "blocbuy-platform-starter") described a fuller starter that was not built that way.
3. **Step 4 and slide 23, "stopping a server ... tested locally".** No local result for self-healing appears in the sources. Slide 5 and B12 say "not run on AWS" only.
4. **Run book p. 17, "Notifications JSON ... shows at least one entry (from the smoke run or a seeded expiry)".** The smoke and readiness probes use version 2147483647 and create no notifications. The recording on 19 Oct found none. Whether a seeded expiry will be done before the talk is not in the sources.
5. **Why checkout is off on the ECS environment.** The setting is in `services.tf`; ADR 0005 says it defaults to false. No document gives a reason for this environment.
6. **Session-store outage.** I found no drill, test or measurement in the sources. C3 says so; confirm nobody has one.
7. **Alarm emails.** Whether the alarm subscriptions are confirmed on the day is open (#515). The testing opt-out `SWE5001_ALLOW_UNCONFIRMED_ALERTS` defaults to true in the up workflows (NFR-63). If the dead-letter alarm is asked about, do not promise an email.
8. **Two zones for every service.** The run book says "the services already run across two zones" (RB p. 13). The only placement evidence is for commerce at two tasks (16 Oct). Identity and notification are single tasks. G4 words it that way.
9. **The "k6 hit its 400-user limit" explanation** is in the run book (RB p. 17). The 19 Oct evidence says the exact cause was not isolated (workstation network, Docker Desktop's network path, or CloudFront). F3 uses the evidence wording.
10. **Queue scaling "local test: 141 s end to end"** (B12) could not be traced to a file in the repo. Quote it only as it appears on B12.
