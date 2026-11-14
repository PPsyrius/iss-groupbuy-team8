# V4 R3 setup C1 — admission failed; trial not run; owner pause

Refs #458/#459. [Machine-readable evidence](v4-r3-setup-c1.json) binds seven approved private sources by SHA-256. This offline record preserves the original failure; no AWS calls, staging, commit or push were performed for evidence preparation.

## Original setup versus later reconciliation

Terraform apply **succeeded, exit 0**, at **18:03:55.455 SGT** (`10:03:55.455143Z`), taking about **ten seconds** according to the parent. Subsequent ECS stabilization did not meet the **18:08 SGT ON-admission cutoff**. The helper retained its original **RuntimeError** at `18:08:00.172 SGT`, reported wall **254.3 s (about 4 min 14 s)**, with no original stable receipt and no automatic retry/seed. **Seven minutes was an upper bound, not the observed wait**; the earlier cutoff protected the remaining trial/off-restoration budget before departure.

Read-only reconciliation at **18:10:54.224 SGT** (`10:10:54.224082Z`) established the later stable/source-image state, preserved released digests and the existing 1,024-CPU-unit/2,048-MiB/five-connection commerce settings. Counts were commerce **2/2/0**, identity **1/1/0** and notification **1/1/0** desired/running/pending. Masked trace was **off**, but **V4 remained on in that snapshot**. This post-cutoff proof does not retroactively pass original C1 admission or prove final normal settings.

## Classification and unused trial

**SETUP C1 FAILED ADMISSION; R3 TRIAL NOT RUN.** There were **zero producer desired-six requests, zero seed operations and no 20,000-team R3 fixture**. The authorized existing-service 2→6 increase/one fixture was not executed; no standalone commerce producer or forced notification worker was used. This is neither an R3 pass nor a business-scaling failure. V4 R1/R2 strict failures and the failed identity drill/deferred retry remain unchanged.

## Owner pause — retain runtime; no destruction

The owner selected **pause before 18:25 SGT as early as possible**, rather than extending the trial, and explicitly prohibited runtime-down/destroy. **Retain runtime, database, queues and evidence** while the owner is away until around **19:00 SGT**, accepting retained cost; that return time is not automatic destruction authority. No further producer increase or fixture is permitted before the owner returns.

The parent is restoring only temporary test configuration; trace-off has evidence above, but final V4-off/cloud readback and safe-to-sleep verification are **not established by this record**. Parent restoration was still in progress at the handoff. The separately verified generator-only timer remains **18:45 SGT** (`10:45Z`): extension command exit **0/Success** and schedule readback matched, not proof of a stopped generator or runtime destruction. No deadline fulfillment, completed normal-setting restoration or safe-to-sleep pass is claimed.

Only aggregate outcomes, timestamps and source hashes are published; private account/resource/user/trace/message/event identities, credentials, URLs, provider bodies and raw logs are omitted. Local validation covers JSON, hashes/facts, links, privacy patterns and whitespace, not fresh cloud verification.

## Dated handoff amendment — pause completed and owner resumed

**13 November 2026:** the sections above retain C1's original failed admission, 254.3-second elapsed time and zero requests/seeds at that checkpoint. Their then-pending restoration wording is historical, not the current handoff status; this amendment does not retroactively pass C1. Seven additional sources are separately hashed in the JSON amendment, leaving the original seven-source manifest unchanged.

Final V4-off/trace-off configuration was stable at **18:15:56 SGT**, preserving commerce **1,024 CPU units / 2,048 MiB / pool five** and released digests. Runtime pause checks at **18:16:24** and the completed **safe-to-sleep receipt at 18:16:34 SGT** established normal settings, healthy commerce/identity/notification **2/1/1**, authenticated **200**, quiet main/DLQ and no outstanding laptop-dependent fault/load. Runtime, database, queues and evidence were retained; no teardown occurred. The encrypted **496-file** archive was readback-verified, SHA-256 **`4748c06f9b0619c0da4385f8c56d4543941d5278e9ea115d1f0b0cfeb35c87ff`**. The final receipt and its readback both hash to **`a63df9d44b04f8923b06a975cb415aafb0874b3d6ad46175affe71b700daeb41`**.

The owner returned and approved resume at **19:04 SGT**. Parent-reported selected-SSO-token failures at **19:07/19:09** were retained; the owner refreshed sign-in and fresh readiness **passed at 19:16:23 SGT**. This was credential recovery, not a network diagnosis; no token or SSO URL was inspected. Readiness confirmed normal/off settings, released digests, healthy **2/1/1**, quiet queues, authenticated **200** and the original R3 fixture still unused. The **18:45 generator-only stop took effect**: its fresh state was **stopped**; no exact stopped timestamp is asserted. Canonical V4 does not need the generator, so no restart is authorized or needed.

Initial resume approval retained **one** commerce count **2→6** request and **one** 20,000-team fixture, unchanged policies/thresholds/images/pools/consumers/bounds, no standalone workers, no forced notification count and no repeats. Notification must scale automatically to four; independent restoration must be armed before count increase. The **initial provisional** bounds were **COUNT restore 19:55**, **NORMAL 20:10**, **OUTER 21:00 SGT**, superseded by the dated timing amendment below; the **19:45–20:00** finish window remains the parent's planning estimate, not a result or guarantee. Identity retry remains deferred and all identity/earlier V4 failures remain open.

At that initial handoff, the parent reported new ON plan/apply work in the background from **19:19 SGT**, without completed setup/count-six/seed proof; later ON stability is recorded below, not retroactively asserted at the earlier checkpoint. **No cleanup is authorized**: runtime/data/queues stay retained, and runtime-down/destruction requires future fresh explicit owner approval; neither resumption nor the 21:00 outer cap supplies that approval. No AWS, staging, commit or push was performed for this amendment.

## Dated timing amendment — 19:29 SGT, before count arm or seed

Resume1 ON setup was actually stable at **19:23:55.897 SGT**, elapsed **249.645 s**, with released digest, commerce CPU/memory/pool **1,024/2,048 MiB/five**, trace off and desired/running counts **2/1/1**. This is the resumed setup cycle, not a pass for original C1 or a business-scaling result. The **19:29:09.595 SGT** timing proof verifies that all count-arm/increase/seed markers were absent before the amendment; no count-six request or fixture execution is established here.

The original authorization bytes are preserved as **`r3-resume1-owner-authorization.initial.json`**, SHA-256 **`bad4ee770bdea13349bdff89580bfb486097f1c3f166162abb5f7fedb480a45f`**. The earlier public manifest now points to that immutable version without changing its hash or historical facts; completed ON approval and its unchanged stable receipt remain bound to the original authorization. The current canonical authorization has SHA-256 **`56a17aadb80e728ce9869265c400dc3b4446d60f4cdbaa644bea8b3fdff9de94`**, separately pinned with `r3-resume1-timing-amendment.json` and the stable-setup proof.

Within the existing owner delegation, the coordinated **current** bounds are **COUNT restore 20:05**, **NORMAL 20:20**, **OUTER 21:00 SGT**, retaining **40 minutes** after normal restoration. This permits full canonical report tails; tests, gates, source, sizes, images, pools, policies, consumers and the single **20,000-team** fixture scope are unchanged. The parent's **19:45–20:00** estimate remains the aim, not permission to wait until 20:20 or a completed result. No count-six/seed, new cleanup authority, repeated fixture or acceptance pass is claimed.
