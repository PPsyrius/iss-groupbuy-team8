# SWE5001 subnet verification — 10 November 2026 SGT

Refs #589 / NFR-70. Operator collection began **9 November 2026 18:18:07 UTC / 10 November 02:18:07 SGT**, after [session up 42665267510](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42665267510) succeeded. Account `402178232802`, Region `ap-southeast-1`, deployed source `93984f615df79666f4c3ba62f4d6ae90fbf1f4e1`. This is the SWE5001 distributed ECS environment, not SWE5006 dev EC2.

Source archive: `swe5001-subnets.gCCJYHpi.tar.gz`, SHA-256 `7d691d78e9f00ffd53e26d15588af3541231d12ae8959ae75e62d40f5b74ce71`. [Selected observations](observations.json) omit public IPs, private DNS/MAC details and application response bodies. The original operator archive remains outside the repository.

## Reviewed observations

| Check | Result |
| --- | --- |
| Six expected subnets | Pass: public `10.50.0.0/24`, `10.50.1.0/24`; application `10.50.10.0/24`, `10.50.11.0/24`; data `10.50.20.0/24`, `10.50.21.0/24`, spanning the expected two AZs. |
| ECS configuration and actual placement | Pass: identity 1/1, commerce 2/2, notification 1/1; zero pending. All four RUNNING task ENIs are in application subnets, use only the service SG and have no public IP. Commerce spans both AZs. |
| Released images | Pass: all four application containers match their service's digest from promotion 42653347818. |
| RDS | Pass: available/private; subnet group contains exactly both data subnets. Its RDS-managed ENI is in `10.50.21.0/24`, AZ `ap-southeast-1b`. |
| Valkey | Pass: available; subnet group contains exactly both data subnets. Its cache-managed ENI is in `10.50.20.0/24`, AZ `ap-southeast-1a`. |
| Data routes | Pass: both data subnets explicitly use `rtb-0b56ece42a4791472`, containing only the active `10.50.0.0/16` local route. No default/NAT/internet/endpoint route. |
| S3 endpoint | Pass: gateway endpoint is associated only with the two application route tables. |
| Data security groups | Pass: PostgreSQL TCP 5432 only from service/db-init SGs; Valkey TCP 6379 only from service SG. Neither has outbound rules or CIDR/IPv6/prefix-list ingress. |
| Disjoint placement | Pass: eligible and observed application/data subnet sets do not intersect. No data ENI has a public IP. |
| Public connectivity | Pass: health `UP` and successful non-empty catalogue response. This exercises commerce, not authenticated routing to every service. |
| Authenticated identity/notification connectivity | Pass, operator-observed in the normal browser: `/auth/me` and `/notifications?page=1&size=1` both returned HTTP 200 / `COMMON_200_000`; notifications contained an items array. Trace IDs are retained in the selected observations, without cookies or personal response bodies. |

Docker/Python offline review asserted task-list/count consistency, ENI attachment identity, resource/subnet-group placement, exact release digests, effective route-table associations and SG rule sources/ports against the downloaded JSON. These assertions review AWS observations; they are not packet-isolation, failover or load tests.

## Separate limits and cleanup

All three alarm-email subscriptions were pending confirmation (`total=3`, `pending=3`). The workflow's testing opt-out allowed startup; no confirmed email delivery or graded messaging-gate acceptance is claimed. This does not negate the observed subnet separation.

Authenticated results were supplied by the operator after sign-in to this rebuilt session and recorded at 18:30:57 UTC; this is the recording time, not a reconstructed request timestamp. Notification-service log correlation, negative packet probes, zone-loss and throughput tests are not included. Keep #589 open for final acceptance review; these observations do not establish unrelated resilience or messaging acceptance.

The operator explicitly prohibits teardown. No `down` was dispatched by the agent; the manual session remains running at this checkpoint. The pre-existing 23:00 SGT nightly cleanup remains configured and has not been changed.

## 10 November reconciliation for #775

The paragraphs above retain the original collection checkpoint. The operator subsequently [closed #589 at 18:36:41 UTC on 9 November / 02:36:41 SGT on 10 November](https://github.com/PPsyrius/iss-groupbuy-team8/issues/589#issuecomment-6748231460), confirming that subnet separation and connectivity checks passed. This record supports that session's actual placement and required connectivity, not a new negative packet-isolation or failover test. Source-group ENI reconciliation added later in [PR #784](https://github.com/PPsyrius/iss-groupbuy-team8/pull/784) was not retroactively run against this archive.

The session's [connection-limit report and pool/scaling experiment](../20261110-load-preflight/README.md#10-november-disposition-for-775) and [failed V3 campaign](../20261110-v3-autoscaling/README.md) are separate results. Existing scheduled cleanup [run 42722097786](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/42722097786) completed successfully at **23:52:06 UTC on 9 November / 07:52:06 SGT on 10 November**, after destroying 120 runtime resources. Thus the original “remains running” statement is historical, not current cloud state. The agent did not dispatch that teardown; no new independent absence check is claimed.

Report citations: §1, §2.2, §3.2/§3.4, §4.3 and §6.1 F6; AD-15, AISS-07 and NFR-70 use the same dated scope. #775 still requires these local documentation changes to reach `dev`; restoring evidence locally is not issue completion.
