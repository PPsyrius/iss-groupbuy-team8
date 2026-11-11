# Operator-owned edge-policy recovery — 9 November 2026

Refs #775, #757, #770; NFR-16/NFR-51. This record reconciles retained operator notes and historical GitHub execution on 10 November. No new AWS query, permission change, apply or deployment was performed for this reconciliation.

## Failure, action and corroboration

| Evidence | Supported statement | Limit |
| --- | --- | --- |
| [Failed promotion #757](../20261109-promotion-757/README.md), run 42587537517, source `e177a3fbb21bccaa9bd232f30ea7dbed6775d48c` | At 09:12:56 UTC the GitHub apply role was denied `cloudfront:UpdateResponseHeadersPolicy` for policy `03faf3c6-02b3-4a63-b3af-4154bc434e74`. | The promotion's offline edge checks did not exercise that permission. Runtime cleanup passed; no release occurred. |
| [Contemporaneous recovery account](../20261109-source-control-delivery/README.md#empty-xss-block-normalisation-and-clean-promotion-retry); [later retained runbook](https://github.com/PPsyrius/iss-groupbuy-team8/blob/5de5d5efbb512d3869f2c84d960d434d64b0042d/infra/swe5001/README.md#operator-owned-edge-updates-9-november-recovery) | After the Terraform-helper attempts stopped, the operator used the direct AWS API alternative: prepare the policy input from the clean promoted source, update the existing CloudFront policy with its current ETag, then compare API readback with that input. The account records the successful update response and its readback correction. | This was **not a successful Terraform apply** or a whole-edge zero-drift plan. The retained account, rather than a raw CloudTrail event or exported mutation receipt, supplies the method. No exact update timestamp is reconstructed. |
| Same recovery account | The update preserved the policy's Terraform name/comment, used the canonical security/custom/removal sections, and did not change S3/IAM permissions. API readback differed only by an empty `XSSProtection: {}` block, also omitted by provider 5.100.0. | An empty-block normalisation is not permission to ignore other security differences. No new grant of `UpdateResponseHeadersPolicy` to the GitHub apply role is recorded. |
| [Promotion #770](../20261109-promotion-770/README.md), run 42653347818, source `93984f615df79666f4c3ba62f4d6ae90fbf1f4e1` | The later edge step, startup, smoke, release and teardown passed. | This corroborates the later usable release path, not the exact earlier operator command or a fresh execution of the exceptional Terraform recovery path. |
| [Viewer-header observations](../20261110-spa-security-headers/README.md), 9 November 16:05:25–26 UTC | All nine expected static-response header values matched the canonical policy at one Singapore edge. | Static HTTP observations only: not API-header, browser-CSP or global-propagation acceptance. |

## Operator attribution and audit boundary

Pan (`PPsyrius`) confirmed on 10 November 2026 that he performed the repair. He could not independently recall the command in this follow-up; the method above is recovered from the contemporaneous retained account, not inferred from that confirmation or the later green workflow. That account is restored with this report's evidence; the later runbook explanation is pinned to commit `5de5d5ef` on the separate fixes branch. Neither is attributed to deployed source `e177a3fb`.

The workload account's `us-east-1` CloudTrail lookup was unavailable under its SCP in the report investigation. No new CloudTrail read was attempted here. This audit-access limit is separate from the October CloudFront **metrics** SCP correction; neither warrants expanding IAM or claiming no out-of-band changes ever occurred.

The durable boundary remains: an operator owns response-headers policy changes; the promotion role re-points the allowed distribution. Applying an already-merged policy through operator credentials does not require a new application PR or a broad CloudFront grant. Future changes still need their own reviewed scope and verification.
