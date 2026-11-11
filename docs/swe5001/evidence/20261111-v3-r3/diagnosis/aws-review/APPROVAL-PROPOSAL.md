# On-demand capacity proposal — not authorized or applied

Target only account `402178232802`, Region `ap-southeast-1`, cluster `blocbuy-swe5001`. R4 remains on hold. This is a reasoned source-scope proposal, **not a saved Terraform plan**, drift proof, authorization, or a demonstrated client-error fix.

## Decision requested

Approve either **A: commerce-only on-demand placement** (smallest isolation change) or **B: all three distributed services on-demand** (removes Spot as a future campaign confounder, including notification scale-out). Existing live floor tasks are already all on-demand; the live strategies still assign Spot weight 3 above each on-demand base. Changing the cluster default is neither necessary nor sufficient to change these explicit service strategies.

On-demand removes the documented Spot-interruption risk but does not fix the independently reproduced workstation/network errors, metric/evaluation latency, JVM warm-up, or slow-start ramp. It costs more and is not a placement-availability guarantee. Do not launch R4 on the strength of this mitigation alone.

## Critical provider-specific constraint

AWS `UpdateService.capacityProviderStrategy` **does not itself trigger a deployment**. A zero Spot weight with Spot base zero prevents future Spot placements; it does **not terminate or migrate existing Spot tasks**. The documented way to refresh existing tasks is a separately understood forced rolling deployment.

The pinned **AWS Terraform provider 5.100.0** is stricter than today's API: `capacityProviderStrategyCustomizeDiff` calls `capacityProviderStrategyForceNew` unless `force_new_deployment` is true; even with true, transitions between an empty and nonempty strategy remain ForceNew. Therefore a plain `weight = 0` edit in the current module, which has no `force_new_deployment`, can plan **service destruction/recreation**. Reject that plan; do not apply it. A nonempty-to-nonempty strategy plus `force_new_deployment = true` is the source-supported in-place path and deliberately rolls tasks.

## Exact native Terraform source shape to review later

Do not modify the sealed R3 overlay. After approval, prepare a new reviewed overlay/source revision preserving the existing session overlays, private inputs, images, pools, CPU/memory, database, routing and scaling values. Proposed module inputs in `infra/swe5001/modules/ecs-service/variables.tf`:

```hcl
variable "fargate_spot_weight" {
  type    = number
  default = 3
  validation {
    condition     = var.fargate_spot_weight >= 0 && var.fargate_spot_weight <= 1000 && floor(var.fargate_spot_weight) == var.fargate_spot_weight
    error_message = "Fargate Spot weight must be an integer from 0 to 1000."
  }
}

variable "force_new_deployment" {
  type    = bool
  default = false
}
```

Proposed changes **inside the existing** `aws_ecs_service.this` resource in `infra/swe5001/modules/ecs-service/main.tf`:

```hcl
force_new_deployment = var.force_new_deployment

# Keep the existing FARGATE block: base = var.fargate_base_capacity, weight = 1.
capacity_provider_strategy {
  capacity_provider = "FARGATE_SPOT"
  base              = 0
  weight            = var.fargate_spot_weight
}
```

For A, add `fargate_spot_weight = 0` and `force_new_deployment = true` only to the current commerce `module.client_service` invocation in `infra/swe5001/runtime/ecs.tf` (use an `each.key == "commerce-svc"` conditional if the map could contain other clients). For B, add those inputs to both distributed module invocations (`module.service` and `module.client_service`), without touching the inactive monolith path. Defaults preserve weight 3/false for unselected consumers. Keep the nonempty FARGATE/FARGATE_SPOT pair, FARGATE weight 1 and existing bases; never use all-zero weights or Spot base above zero.

Expected filtered saved-plan scope for A: **in-place update only** to `module.client_service["commerce-svc"].aws_ecs_service.this`, changed strategy weight plus `force_new_deployment`. B additionally permits only `module.service["identity-svc"].aws_ecs_service.this` and `module.service["notification-svc"].aws_ecs_service.this`. No create/delete/replace, task-definition revision, IAM, network, target group, ALB, RDS, CloudFront, cluster-capacity-provider, image or scaling change is proposed. A real reviewed private plan must prove the scope; source reasoning cannot promise a no-drift plan. Do not expose full plans/state/inputs.

## Preserved runtime contract

- Commerce definition `37`, CPU `1024`, memory `2048`, image digest `sha256:f2e65b444395c2e5c26ae8675d4a4c203fbf97f0b3a98ae8e2eb187a6a51d92e`; identity `20` and notification `34`, CPU `512`/memory `1024`; existing image digests and pool maximum **5** unchanged. No task-definition update is required by a capacity-provider strategy change.
- Commerce normal floor **2**, max **6**, all suspension flags false remain unchanged by the infrastructure change. A later separately authorized V3 one-task reference uses min **1**, max **6**, then restores **2–6**. Keep Terraform's `ignore_changes = [desired_count]`; do not raise the floor, force six tasks, or remove autoscaling to make the load result pass.
- Request target **6000 per target per minute**, 60/180-second request cooldowns, CPU scale-out-only guard **50%**, commerce slow start **120 seconds**, other slow starts zero, health-check grace **120 seconds**, deployment **100/200%**, circuit breaker/rollback, release and workload remain unchanged.
- RDS `db.t4g.small` PostgreSQL **18.3**, max connections **181**, reserved **5**. Existing conservative steady budget **75** includes pooled 55 + reserved 5 + owner 3 + operator 2 + headroom 10. Existing full-replacement budget **130** includes pooled 110 plus the same 20; remaining **51**. Provider mix changes no pool size or task maximum, so those bounds remain numerically unchanged. Do not increase pools to address client-side errors.

## Rollout implications and gate

Any approved forced deployment happens **before measurement**, at the stable normal floor, with no observer/one-task/load launch implied by approval to prepare a plan. `100/200%` allows up to twice the desired task count during a roll; the later campaign's allowance of seven commerce task lifetimes is **not** a universal ECS hard cap. A six-task deployment can allow twelve commerce tasks. The 130-connection budget already covers full doubling of maximum service counts (commerce 12, identity 2, notification 8), but this does not authorize rolling during measurement.

For B, a single apply can roll all three services concurrently. At floors 1/2/1, fully doubled CPU is **6 vCPU**; commerce-only rolling at floor two with identity/notification unchanged is **5 vCPU**. All-service maxima total **8.5 vCPU** steady and **17 vCPU** fully doubled. The live account/Region On-Demand quota is **30 vCPU**; recent regional usage is 3 vCPU at 11:56 UTC (window maximum 4), leaving snapshot headroom but not guaranteeing capacity or future regional contention. Quota and AWS/Usage checks require a fresh read immediately before any approved roll; no quota change is proposed.

Do not use `env-up.sh` as a shortcut: it intentionally pauses services and does unrelated startup/database/messaging operations. Do not use manual `StopTask` or desired-count zero to clear Spot. After an approved in-place strategy/roll operation, independently require one COMPLETED deployment per service, intended desired/running counts, zero pending/failed rollout, all expected targets healthy, two-AZ commerce floor placement, every actual task on FARGATE (for B all services; for A commerce), unchanged task definitions/images/pools and scaling bounds, no extra unexpected ENIs, and no warm-up/slow-start interval overlapping campaign launch. The provider's default `wait_for_steady_state=false` means successful apply is not a readiness receipt. Require subsequent filtered zero-change reconciliation under separate authorization; do not run Terraform now.

Rollback also needs explicit review/approval. Restoring weight 3 re-enables future Spot placements and, with this provider's forced-deployment setting, can trigger another rolling deployment; it does not restore the exact former task IDs. Keep rollback outside measurement and retain both before/after safe metadata. Do not silently toggle `force_new_deployment` back in another apply: the provider sends its value on service updates; review any cleanup plan separately.

## Sources checked 11 November 2026

- AWS UpdateService API: <https://docs.aws.amazon.com/AmazonECS/latest/APIReference/API_UpdateService.html> (`capacityProviderStrategy`, `forceNewDeployment`, deployment minimum/maximum and grace-period semantics).
- AWS Fargate capacity providers: <https://docs.aws.amazon.com/AmazonECS/latest/developerguide/fargate-capacity-providers.html> (zero weights, base/weight, Spot warning/replacement/no automatic on-demand fallback).
- Pinned provider source: <https://github.com/hashicorp/terraform-provider-aws/blob/v5.100.0/internal/service/ecs/service.go#L2010-L2039> (ForceNew custom diff); same file `resourceServiceUpdate` constructs `UpdateServiceInput.ForceNewDeployment` from the Terraform argument and `CapacityProviderStrategy` from the changed set.
- Repository source: `infra/swe5001/modules/ecs-service/main.tf`, `runtime/ecs.tf`, `runtime/observability.tf`; session source additionally verified by the existing wrapper's overlay hash checks, not mistaken for checked-in defaults.
