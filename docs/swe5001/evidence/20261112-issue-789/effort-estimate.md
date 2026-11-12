# Pan's Phase D effort — reconstruction for #789

## 12 November 2026 — owner confirmation

Pan explicitly confirmed **8 person-days for Phase D and 19.5 person-days in total** during the review of PR #798. These are now his agreed effort estimates, not measured hours. This confirmation supersedes the earlier lack of owner confirmation below; it does not confirm other members' estimates or change the reconstruction's evidence window through 12 November.

## Original reconstruction before owner confirmation

**Working estimate: 8 person-days additional, or 19.5 with the earlier 11.5.** This is an assumption-based reconstruction, not measured labour or a statement that Pan personally agreed to eight days. The owner asked for a calculation using the available conversation/session record, board snapshots and sprint logs. The result covers identified post-presentation work through **12 November**, not future work through the 16 November deadline.

## Method and exclusions

Use the repository's planning convention of **6 hours per ideal day**, not an invented eight-hour day ([Sprint 4 plan §5](../../../agile/sprint-04/plan.md#5-sprint-4-backlog)). Retain the presentation's agreed **11.5 pd through 19 October** as the baseline; do not charge its completed work again. Phase D starts on 22 October. An active day, issue age, number of commits/tests or time an AWS stack is running is not a person-day. Long-running automation and concurrent agents/tests are not added as separate human work.

The source inventory is the Pan-authored sections of the daily logs from 22 October to 12 November, the current conversation's 10–12 November operator/report work, and board snapshots. The local snapshots run through 7 November; the newer 12 November snapshot was read from `metrics/board-snapshots` at `2d127a73d73797558020f31d67d4d16a8eef0c55`. In that snapshot #589, #591, #592 and #775 have **blank estimates**. #458 retains a planned five hours spanning pre-presentation and later work; #447/#457/#467 were completed before Phase D and are excluded. #589's board column still says In progress although its closed timestamp is present: board movement cannot measure hours or override issue state. These records establish scope/progress, not an actual-hour sum.

Exclude SWE5006-only ZAP/browser-report work, general frontend/backend dependency batches, and work by other members. Shared pipeline/security changes are counted only where the logs connect them to SWE5001 image delivery, evidence or the r4 investigation. Do not assign the entire 9 November overnight runtime lifetime to supervised work, or count the 11 November test windows in addition to the same diagnosis/session block. The full history of every private chat session is unavailable; no hidden timesheet is implied.

## Reconstructed allocation

The hours below are explicitly **judgmental allocation assumptions** for the evidenced work packages. They are not extracted timestamps. The range shows sensitivity rather than statistical confidence. Related implementation, checks and evidence within a block are counted together.

| Non-overlapping work package | Base assumed hours | Plausible hours | Evidence basis |
| --- | ---: | ---: | --- |
| Feedback/report corrections and delivery/security explanations before the final audit pass | 12 | 10–16 | [22 Oct log](../../../agile/sprint-04/log/2026-10-22.md), [10 Nov report/session entry](../../../agile/sprint-04/log/2026-11-10.md), §§4/5 and D10 work in the available session context; includes #591/#592/#775 writing, not a second implementation charge |
| Subnet/edge verification, publication-tag repair and operator close-out | 6 | 4–8 | [9 Nov log](../../../agile/sprint-04/log/2026-11-09.md), [10 Nov log](../../../agile/sprint-04/log/2026-11-10.md), #589/#769/#775 records; does not count the whole unattended runtime interval |
| Catalogue/cache diagnostic implementation and rerun-tool hardening | 10 | 8–14 | [10 Nov cache/tooling entry](../../../agile/sprint-04/log/2026-11-10.md), #784 and the local diagnostic matrices; includes offline verification within this block |
| 11 Nov V2/r1–r4 operation, laptop-path diagnosis and AWS-generator preparation | 16 | 12–20 | [11 Nov session log](../../../agile/sprint-04/log/2026-11-11.md), retained run/diagnosis/generator records and the available operator conversation; includes supervised coordination/review, not duplicate automation or parallel-agent hours |
| Final cross-issue audit reconciliation, source/PDF/privacy checks and handoff | 4 | 3–6 | [12 Nov log](../../../agile/sprint-04/log/2026-11-12.md), three-audit follow-up and this #789 factual handoff; does not re-charge the earlier report block |
| **Total** | **48** | **37–64** | Sum of the five explicit assumptions |

Calculated: `12 + 6 + 10 + 16 + 4 = 48 h`; `48 / 6 = 8 pd`; `11.5 + 8 = 19.5 pd`. Sensitivity: `37 / 6 ≈ 6.2 pd` to `64 / 6 ≈ 10.7 pd` additional. Thus eight is a defensible **rounded working estimate**, not a uniquely recoverable answer. Use “reconstructed estimate” in §2.3 and the reply; do not write “all five agreed in November” on the strength of this calculation. The full-team 71.5 pd total depends on the other members' separate estimates and is not independently verified here.
