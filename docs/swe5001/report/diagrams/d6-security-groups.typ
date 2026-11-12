// Security-group rules of the SWE5001 runtime, moved out of the D6 network figure.
// Source: infra/swe5001/runtime/security-groups.tf and modules/network/fck-nat.tf (the `nat`
// group) and modules/network/main.tf (the default group). Group names drop the prefix
// `blocbuy-swe5001-`. Groups reference each other, not CIDRs, except where a cell says so.
#let d6-sg-table() = [#figure(
  table(
    columns: (auto, 1fr, 1fr, 1fr),
    table.header([Security group], [Attached to], [Inbound], [Outbound]),
    [`alb`], [ALB], [TCP 80 from the CloudFront origin-facing prefix list], [TCP 8589 to `svc`],
    [`svc`], [ECS tasks of the three services], [TCP 8589 from `alb`; TCP 8589 from `svc` (Service Connect)], [All traffic to `0.0.0.0/0` (through fck-nat, or inside the VPC)],
    [`rds`], [RDS instance], [TCP 5432 from `svc` and `dbinit`], [None],
    [`cache`], [Valkey node], [TCP 6379 from `svc`], [None],
    [`dbinit`], [One-off db-init task], [None], [TCP 5432 to `rds`; TCP 443 to `0.0.0.0/0` (AWS APIs and ECR Public)],
    [`nat`], [fck-nat instance], [All traffic from `10.50.10.0/24` and `10.50.11.0/24`], [All traffic to `0.0.0.0/0`],
    [VPC default], [Nothing], [None (all rules removed)], [None],
  ),
  kind: table,
  caption: [Security groups and their rules. Source: `infra/swe5001/runtime/security-groups.tf` and `infra/swe5001/modules/network`.],
) <tbl-security-groups>]
