# SWE5001 final report — part of Liu Jin

Individual part of the SWE5001 final report (#462), tracked in #742. It is written and reviewed here, on its own, and wired into the full report by the reconcile PR, together with the other members' parts. The rules for writing and reviewing it are in [`../../AGENTS.md`](../../AGENTS.md).

| | |
|---|---|
| Owner | Liu Jin (`l0sgAi`) |
| Sections | 3.2 Physical Architecture & Design; 3.2.1 Key Architectural Decisions; 3.2.2 Technology and Services; 3.2.3 Persistence Design; 3.4 Architectural Limitation |
| Not in this part | 3.2.4 Detailed Design (Yu Qianshuo) |
| Assessor remarks addressed | F6 (database in its own subnet), F8 (physical perspective), F11 (data schemas for every data store); closure is confirmed at the reconcile review |
| Diagrams | D5 physical deployment, D6 network and security groups, D9 data model per database |
| IDs used | AD-07 to AD-15, AISS-01 to AISS-15 ([`../../registers.md`](../../registers.md)) |
| Reviewers | Pan (reviewer pair), Lim (3.4) |

## Files

| File | Content |
|---|---|
| [`03-2-physical.typ`](03-2-physical.typ) | 3.2, 3.2.1, 3.2.2 and 3.2.3; starts at a level-2 heading (`==`), so it drops into chapter 3 unchanged |
| [`03-4-limitations.typ`](03-4-limitations.typ) | 3.4 with the AISS register and the roadmap |
| [`diagrams/`](diagrams/) | Typst/fletcher sources of D5, D6 and D9; they render inside the PDF, so there is no separate image export |
| [`part.typ`](part.typ) | Standalone build of this part only, with the template's section numbers; not used by the full report |

Shared files this part imports and does not own: [`../../lib.typ`](../../lib.typ) (styles and scaffold helpers) and [`../../diagrams/style.typ`](../../diagrams/style.typ) (diagram palette from the presentation kit).

Build the standalone PDF from the repository root:

```bash
typst compile --root . docs/swe5001/report/parts/liu-jin/part.typ "<output>.pdf"
```

## Status (5 November 2026)

| Section | Status |
|---|---|
| 3.2 | Drafted: logical-to-AWS mapping, D5, D6, data-subnet explanation (F6) |
| 3.2.1 | Drafted: AD-07 to AD-15 as context, options, choice, trade-off, consequence |
| 3.2.2 | Drafted: technology table, encryption and secrets, calculated cost |
| 3.2.3 | Drafted: ownership, conventions, D9, invariant indexes, aggregate tables, transactions, technical tables, Valkey key model, event envelope, queues, data classification |
| 3.4 | Drafted: AISS-01 to AISS-15 in the template's columns, roadmap R0–R2 |

No `#todo` box is left. Everything still pending is marked with `#pending[…]`, which renders as red text on a light-red highlight (`grep -n "#pending" *.typ` lists them):

- **Aggregate names (3.2.3, aggregate-to-table mapping).** The ten rows use the domain class names (`GroupDeal`, `PurchaseIntent`, `CheckoutPayment`, …). Align them with Eric's aggregate table in 3.1.4.
- **Roadmap dates (3.4).** The four phase targets, the fifteen AISS target dates, the go-live assumption behind R1 and R2, and the move of AISS-12 from R0 to R1. R0: promotion by Thu 12 Nov, evidence session Fri 13 Nov 2026. R1: 31 Mar 2027. R2: 30 Jun 2027 or a load trigger.
- **Measured cost (3.2.2).** The sentence that defers the measured cost to chapter 6, until a cost record is committed.

Not marked, because they are verified statements rather than open questions: AD-08 (Fargate Spot on ARM64 is configured, but the evidence does not show task placement) and AISS-01 (the single NAT in zone a). Reviewers may still challenge them.

## For the reconcile PR

1. In `sections/03-solution-overview.typ`, replace the placeholder sections 3.2 to 3.2.3 (everything from `== Physical Architecture & Design` up to, not including, `=== Detailed Design`) with `#include "../parts/liu-jin/03-2-physical.typ"`, and replace the `== Architectural Limitation` placeholder with `#include "../parts/liu-jin/03-4-limitations.typ"`. Imports inside the part resolve relative to the part, so no path changes are needed.
2. Check the cross-part facts this part depends on:

   | This part says | Must match |
   |---|---|
   | Aggregate names in the aggregate-to-table mapping | 3.1.4 aggregate table (Eric) |
   | AD-01 to AD-06 referenced from 3.2.1 | 3.1.1 (Eric) |
   | One 0.5 vCPU task saturated at about 280 req/s; three tasks about 455 req/s (19 Oct 2026) | 4.1 (Pan) |
   | V4, V5, V7 and identity outage are local results only (AISS-13) | 4.2 (Yu Qianshuo) |
   | Settlement lock order and the UC4 sequence | 3.2.4 (Yu Qianshuo) |
   | AISS-04, -08, -09, -15 security gaps | 4.3 (Pan) |
   | Measured cost is not cited; chapter 6 reports it once a record exists | 6 (Lim) |

3. Resolve every `#pending[…]` (red) item, then remove the marker. The full report must contain none at submission.
