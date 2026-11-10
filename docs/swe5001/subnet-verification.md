# SWE5001 subnet-separation verification

Refs #589 and NFR-70. This procedure verifies the dedicated data-subnet change (AD-15); it does not claim the change is deployed until the observations below pass. Do not use the unrelated SWE5006 dev EC2 instance as subnet-separation evidence.

## Preconditions

- Use account `402178232802`, region `ap-southeast-1`, with read access to EC2 networking, ECS, RDS and ElastiCache, plus Python 3 and the AWS CLI. The collector checks the account and fixes the Region explicitly.
- A reviewer-approved `prod` revision must contain `infra/swe5001/modules/network`'s dedicated data subnets and runtime data-store wiring. Record the full deployment SHA and workflow URL; this is not necessarily the local checkout SHA.
- The promotion workflow destroys runtime after its checks. Wait until it completes before an explicitly authorised manual session; do not collect while a promotion/teardown is running. Follow [the session runbook](runbook-environment-session.md), including readiness, alarm confirmation, cost controls and teardown. Preparation of this checklist does not authorise `up`, promotion, traffic generation or an infrastructure apply.
- Collect only after all three distributed services are ready. An absent runtime, missing service, access denial or incomplete lookup is **not verified**, never a pass. Finish before the nightly teardown at 23:00 SGT.

## 1. Collect metadata (read-only AWS calls)

Choose one of the following ways to make the collector available. At the 9 November preparation checkpoint, `inspect-subnets.py` is an uncommitted local file, not part of promotion #757 or `prod`; cloning or updating `prod` alone will not provide it. Uploading the script is preparation only: run collection after the session preconditions above are met.

### Option A: upload the standalone script to CloudShell

In CloudShell, use **Actions → Upload file** to upload the local file `infra/swe5001/scripts/inspect-subnets.py` to your home directory (`$HOME`). It uses only the Python standard library and AWS CLI; no repository checkout is needed. Confirm the uploaded filename before running:

```bash
ls -l "$HOME/inspect-subnets.py"
```

Once the promotion and cleanup have completed successfully and an authorised distributed session is ready, run:

```bash
python3 "$HOME/inspect-subnets.py" "$HOME/swe5001-subnets-$(date -u +%Y%m%dT%H%M%SZ)"
```

### Option B: run from a checkout containing the collector

From the root of a reviewed repository checkout that actually contains `infra/swe5001/scripts/inspect-subnets.py`, run:

```bash
python3 infra/swe5001/scripts/inspect-subnets.py "$HOME/swe5001-subnets-$(date -u +%Y%m%dT%H%M%SZ)"
```

A `can't open file` error means the script was not found at the supplied path; it is not an AWS permission or subnet-verification result. CloudShell starts in `$HOME`, not a repository root. Use Option A if the checkout does not contain the collector.

The script writes a new private local directory, refuses overwrites, and performs only allowlisted `get-caller-identity`, `describe-*` and `list-tasks` calls. It does not read Terraform state, task-definition environment/secrets, parameter values, ALB listener conditions or application logs. No AWS resources are changed. Non-secret task-definition ARNs and image digests bind the network observations to the deployed tasks; record the corresponding deployment workflow separately.

The final `collection.json` means metadata collection completed, **not** that separation or connectivity passed. If collection fails, retain `incomplete.json` and the partial files privately, correct the session/access problem and use a new directory. The observations span multiple API calls, not an atomic snapshot; repeat collection if tasks or networking change during the check.

## 2. Review placement and controls

| Check | Evidence | Pass criterion |
| --- | --- | --- |
| Correct VPC and six subnets | `vpc.json`, `subnets.json` | One tagged runtime VPC, CIDR `10.50.0.0/16`; public `10.50.0.0/24`, `10.50.1.0/24`; application `10.50.10.0/24`, `10.50.11.0/24`; data `10.50.20.0/24`, `10.50.21.0/24`. Record each actual subnet ID and AZ. |
| Service configuration | `services.json` | Each service's `awsvpcConfiguration.subnets` is the application subnet set, with no data/public subnet and public-IP assignment disabled. All services are active with positive desired/running counts, no pending tasks. |
| Actual application placement | `tasks-*.json`, `task-details.json`, `task-enis.json` | Every listed RUNNING task has an observed ENI in an application subnet of this VPC, using the service security group and no public IP. Task counts match the ready services; repeat if autoscaling/deployment changes them. |
| RDS configuration | `rds.json`, `security-groups.json` | Instance `blocbuy-swe5001` is available, not publicly accessible, and its attached security-group set is exactly the ID of `blocbuy-swe5001-rds`, with no additional groups. Its subnet group contains exactly the two data subnet IDs in the expected VPC. |
| Valkey configuration | `cache-group.json`, `cache-node.json`, `cache-subnets.json`, `security-groups.json` | Replication group/node are available, their attached security-group set is exactly the ID of `blocbuy-swe5001-cache`, with no additional groups, and their subnet group contains exactly the two data subnet IDs in the expected VPC. |
| Actual data placement | `data-enis.json`, RDS/cache records | Each store ENI is in a data subnet with no public IP, its AZ agrees with the store record, and its attached security-group set is exactly its store's expected group above; reject any additional group. Confirm descriptions/ownership against the console when ambiguous; a subnet group's eligible IDs alone do not prove actual placement. No data ENI is in an application/public subnet. |
| Data routes | `routes.json`, `endpoints.json` | Resolve each data subnet's explicit route-table association (or the VPC main table if none). Effective routes contain only the active local VPC route: no IPv4/IPv6 default, NAT, internet/egress-only gateway, peering/transit, or endpoint route. The S3 gateway endpoint is attached only to application route tables. |
| Database ingress | `security-groups.json` | RDS: TCP 5432 only from the service and db-init group IDs. Cache: TCP 6379 only from the service group ID. No CIDR, IPv6, prefix-list, self-reference or extra port/protocol grants. Both data security groups have no outbound rules. |
| Source-group ownership | `source-enis.json`, `source-reconciliation.json`, `task-details.json`, `tasks-db-init.json`, `db-init-task-details.json` | Every ENI using the service or db-init group must reconcile to a listed application or db-init task, with exactly its expected group. No unknown or missing ENI is accepted. For every db-init task, match its exact task ARN, definition and digest to an explicitly authorised session receipt; a matching family alone is not approval. If no db-init task is authorised, require an empty db-init inventory. |
| Disjoint sets | All placement records | Actual ECS ENI subnet IDs have no intersection with RDS/Valkey ENI subnet IDs; eligible application/data subnet sets are also disjoint. |

These checks establish observed network configuration and placement. They do not demonstrate resilience to a zone loss, a denied packet probe, or end-to-end application connectivity.

## 3. Verify connectivity separately

First record the successful session workflow and its built-in smoke output. For an additional anonymous check, the following sends HTTP GETs only:

```bash
curl --fail --show-error --silent --max-time 20 \
  https://d3lvq02o2e7tcn.cloudfront.net/api/actuator/health
```

```bash
curl --fail --show-error --silent --max-time 20 \
  'https://d3lvq02o2e7tcn.cloudfront.net/api/v1/products?page=1&size=1'
```

Require health `UP` and the successful catalogue response. These checks exercise commerce but do not alone prove every service's database/cache path. Use the [session runbook's signed-in routing checks](runbook-environment-session.md#acceptance-evidence-456) for identity and notification and record status/trace IDs only. Sign-in creates a session and may create an identity account, so it is a separate operator-authorised functional check, not part of the read-only collector. Never paste cookies, credentials or private response bodies. If a data connection is not exercised or observable, report it as unverified instead of treating metadata collection as connectivity proof.

Do not open a database security group, allow public access, dump decrypted parameters, or change routing to make a check pass. A negative packet-isolation test requires its own approved source, command and cleanup; it is not supplied by this procedure.

## 4. Preserve evidence and clean up

- Record UTC collection time, account/Region, deployment SHA/workflow URL, task-definition ARNs/digests, the application/data subnet ID sets and a pass/fail/not-verified result for every row. Review projected JSON before committing it; retain no secrets, cookies, private application payloads or raw state/plans.
- The operator closed #589 after the historical placement/connectivity checks; detailed receipts remain report-side. Publish/review the missing evidence under #775 before claiming published NFR-70 acceptance. Record absent observations explicitly; issue closure alone does not establish a new session's placement.
- After saving the evidence, the authorised operator follows the session runbook's `down` procedure and records its independent absence result. The collector does not start, stop, apply or destroy anything.

## Offline verification of the collector

```bash
python3 -m unittest discover -s .github/tests -p 'subnet_inspection_test.py'
```

Mocked tests verify collection guards and read-only calls; they do not establish AWS deployment or subnet acceptance.
