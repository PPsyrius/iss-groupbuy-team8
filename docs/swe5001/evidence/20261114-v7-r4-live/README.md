# V7 R4 — poison, isolation, alarm, human receipt and restoration passed

Refs #459/#448, NFR-63. **V7 R4 passed**, with a separately persisted [full acceptance receipt](acceptance.json) binding the original technical result, captured timings, matched owner email and actual restoration/readback. This is a qualified temporary-configuration result, not whole-campaign acceptance or a release promotion.

## Actual results — 14 November 2026

| Gate | Actual result |
| --- | --- |
| Poison / control publication | One poison started **02:08:12.193043 UTC**, SNS-confirmed **02:08:12.786570**; one valid notification-transport control started **02:08:20.373567**, confirmed **02:08:20.855648**. No replay. |
| Production handling | Receive counts **1–5**; successive intervals **9.953 / 9.997 / 10.001 / 10.000 s**. The control applied while poison retried. |
| Real DLQ / alarm | First sampled positive DLQ **02:09:27.090785 UTC**; actual ALARM **02:12:05.323000**; Maximum/60-second evaluation, one datapoint of 1 at **02:10:00 UTC**; alarm action succeeded. |
| Both unchanged timing screens | Injection → ALARM **233.129957 s < 300**; sampled observed DLQ → ALARM **158.232215 s < 300**. The latter is not exact enqueue latency. |
| Identified cleanup / DB | Only the identified poison deleted, once at **02:12:13.161438 UTC**. Report at **02:12:35.774969**: poison markers/notifications **0/0**; control markers/notifications/intended-recipient notifications **1/1/1**. This is not real-payment evidence. |
| Reset / technical result | Real alarm reset **02:15:05.324000 UTC**; quiet and continuous-observer gates passed; original runner exit **0**, technical acceptance **02:15:39.486396 UTC**. |
| Human email | Owner confirmed receipt at **10:12 SGT**; screenshot transition **02:12:05 UTC** matches R4 to its displayed second. Receipt time is minute-only; **no exact delivery timestamp or latency is established**. |
| Original configuration restored | Restoration requested **02:15:40.668610 UTC**; original TD stable **02:20:39.067536**, backstop exit **0**. Original consumer/source visibility **60 seconds**, not task-definition revision 60. |
| Fresh read-only closure | **02:31:54.470861 UTC**: exact original workload image/resources/roles/secret references/environment/workers and scaling/queue/alarm policies, one healthy running notification task, pending zero; experimental tasks stopped; no live standalone SQL or STOPPING tasks. Three quiet/OK samples span **38.014324 s**. |

## Configuration and evidence boundaries

The owner explicitly approved **10-second application consumer visibility for V7 only**, retaining the same application image, resources, roles, secret references, workers, source-queue policy and five production receives. The source queue remained configured for 60 seconds; the application requested 10 seconds per receive during R4, then the original TD without that override restored the 60-second default. **This does not demonstrate that the original 60-second consumer meets the injection-to-alarm timing target.** No main-queue operator receive, artificial alarm, direct DLQ injection, purge, unknown-message deletion, forced scaling or additional SQL submission was used.

The independent backstop was armed at **02:02:48.658230 UTC**, with an absolute restoration-trigger deadline **02:17:48.658222** and actually exported role expiration **03:02:47**. Restoration was requested before that deadline; stable ECS draining/readback completed afterward. This is not a claim of full restoration within fifteen minutes.

The original technical receipt correctly remains `humanEmailReceiptVerified=false`; [matched-human evidence](human-email-match.json) and the separate full acceptance record add the later confirmation without rewriting history. The incomplete null timing-watcher receipt and its reconciled authoritative capture are both preserved. The later read-only check's inventory/managed-Service-Connect comparison failures are also preserved; its final successful check is not another V7 drill or retry.

## Retained failures and remaining scope

- **R1 remains failed on injection → ALARM, 515.128671 s.** Its raw captures and conflicting historical sampled-DLQ interpretations remain private; no unsupported historical timing is carried forward as R4 evidence.
- **R2 failed before either publish** on the local rollout cutoff; runner/backstop exits **1/1** are retained, with later actual original-state/quiet reconciliation. Its one experimental TD registration was reused, not repeated.
- **R3 failed before forward update or either publish** on order-sensitive environment-array comparison; runner/backstop exits **1/0** are retained. R4 compares unique environment entries by name without changing their values.
- V8 remains parent-owned: three historical lifecycle records exist, but distributed startup **31m37s / 32m17s** still fails the under-30-minute target. Identity/Sandbox remains failed/deferred. V7 acceptance does not authorize destructive V8 scope, teardown, issue closure or promotion.

## Preservation and privacy

[Private package receipt](private-package.json) records the new V7-only archive, existing private S3 AES256 encryption, exact downloaded SHA-256 and individual-file verification. Raw AWS responses, identifiers, logs, payloads and task/policy data stay private; credentials/cookies/caches, checkout/provider bodies, Terraform plans/state/private variables and the unredacted inbox screenshot are excluded. Safe owner-confirmation metadata is sufficient for this record. Local TAR copies are private but are **not claimed encrypted**.

The earlier [109-file V4/V5 package](../20261114-v4-r5-live/private-package.json), SHA-256 `2f2be4d09d30c69a9dd8343f11020b1e696cbaf418d53db54e023cf3500e5f45`, remains unchanged. No old archive, submitted report, other operator's raw evidence, Git staging/commit/push state or release pointer was changed for this V7 evidence closure.
