# Organisation SCP consolidation

Date: 2026-10-15 to 2026-10-16. Owner: @limjiechao. Requested by @PPsyrius. No issue; NFR-67. Scope: the AWS Organizations service control policies (SCPs) over the member accounts 402178232802 (`swe5006-team8`) and 161877660123 (`swe5001-poc`), both in the `Sandbox` OU. The management account is outside SCP scope by AWS design. All changes were made live with the AWS CLI; no Terraform manages these policies.

## Ownership and access model

@limjiechao set up and administers the AWS organization and its IAM Identity Center. The project's AWS accounts are member accounts under that administration:

| Component | Role | Administered by |
| --- | --- | --- |
| Management account (named `jiechao`) | Organization created 2026-08-25 with all features enabled; owns the SCPs and other organization policies and the IAM Identity Center instance; SCPs never restrict it. No delegated administrators, so all administration happens here | @limjiechao |
| IAM Identity Center (BlocBuy access portal) | Instance `jiechao-aws` created in the management account on 2026-08-25; trusted organization service (`sso.amazonaws.com`); signs team members into member accounts through permission sets, which appear there as `AWSReservedSSO_*` roles | @limjiechao |
| `Sandbox` OU → 402178232802 (`swe5006-team8`) | Created by the organization on 2026-08-25; member account for BlocBuy development and the SWE5001 deployment; receives the SCPs and Identity Center assignments | Workloads: the team; guardrails and access: @limjiechao |
| `Sandbox` OU → 161877660123 (`swe5001-poc`) | Created by the organization on 2026-09-30; member account for the SWE5001 proof of concept; same inheritance | Workloads: the team; guardrails and access: @limjiechao |

Source: `organizations describe-organization` and `list-accounts`, `sso-admin list-instances` and `organizations list-delegated-administrators`, read on 2026-10-16.

Because the guardrails are set centrally, a team member's request to relax one (such as the CloudFront-metrics request below) goes to the organization administrator; it cannot be fixed by IAM changes inside a member account, since an SCP deny overrides any IAM allow there.

## Why

The SWE5001 CloudFront dashboard could not read CloudFront metrics, which AWS serves only from `us-east-1`. The request named one SCP, but two SCPs each pinned the Sandbox OU to `ap-southeast-1`, so the call stayed denied until both had the same exception. The `cloudwatch:ListMetrics` follow-up needed the same pair of edits again. The audit that followed found further duplication, dead rules and cost-cap gaps.

## Before (2026-10-15)

| Level | SCP | What it did |
| --- | --- | --- |
| Root | `FullAWSAccess` (AWS-managed) | Allow-all baseline |
| Root | `DenyLeaveAndCloseAccount` (`p-rhmnt2ps`) | Deny `organizations:LeaveOrganization` and `account:CloseAccount` |
| Sandbox OU | `FullAWSAccess` | Allow-all baseline |
| Sandbox OU | `DenyAllOutsideApSoutheast1` (`p-50xf4zdy`) | Region pin with a broad list of about 50 exempt global services |
| Sandbox OU | `Sandbox-Standing-Guardrails` (`p-8hwmgw9u`) | A second region pin with a narrow list of 10 exemptions, WAF scope, cost caps, and a duplicate leave-organization deny |
| Sandbox OU | `DenyMemberAccountInstances` (`p-v98stib2`) | Deny `sso:CreateInstance` |
| none | `Sandbox-Freeze` (`p-oyvru9pr`) | Unattached freeze policy whose only exemption is a role that does not exist |

Problems found:

| # | Problem | Effect |
| --- | --- | --- |
| 1 | Two region pins on one OU | An action outside `ap-southeast-1` needed an exemption in both, so every exception was two identical edits and a one-sided fix silently did nothing |
| 2 | Dead exemptions | About 40 of `p-50xf4zdy`'s exemptions (for example `kms:*`, `health:*`, `access-analyzer:*`) had no effect because `p-8hwmgw9u` still denied them; its description promised behaviour it did not deliver |
| 3 | Org-wide rules attached to one OU | `sso:CreateInstance` and the region pin applied only to `Sandbox`, so a future OU would get neither |
| 4 | Duplicate rule | `DenyLeavingOrg` in `p-8hwmgw9u` repeated the Root policy |
| 5 | Slot pressure | Sandbox used 4 of the 5 SCPs AWS allows per target |
| 6 | Unsafe freeze | Attaching `Sandbox-Freeze` would lock out every principal in both accounts (its `SandboxNuker` exemption role does not exist) |
| 7 | RDS cost-cap gap | `SmallRdsOnly` covered create and modify but not restore-from-snapshot, point-in-time restore or read replicas, which can pick any instance class |
| 8 | Missing cost cap | `aoss:*` (OpenSearch Serverless) was not in `NoExpensiveServices` |
| 9 | Identity Center roles unprotected | Member-account admins could modify, delete or impersonate `AWSReservedSSO_*` roles |

## After (2026-10-16)

| Level | SCP | What it does |
| --- | --- | --- |
| Root | `FullAWSAccess` | Allow-all baseline (unchanged) |
| Root | `Org-Baseline` (`p-tveef62a`, new) | Deny leave/close account; deny member `sso:CreateInstance`; deny `iam:CreateRole` and 12 role-modifying actions on `role/aws-reserved/sso.amazonaws.com/*` |
| Root | `Region-Pin` (`p-4hlb2c8g`, new) | The only region pin: deny outside `ap-southeast-1` except 10 global services; WAF limited to `ap-southeast-1`/`us-east-1`; `cloudwatch:GetMetricData` and `ListMetrics` in `us-east-1` for 402178232802 only |
| Sandbox OU | `FullAWSAccess` | Allow-all baseline (unchanged) |
| Sandbox OU | `Sandbox-Guardrails` (`p-8hwmgw9u`, renamed and reduced) | Cost caps only: `NoExpensiveServices` (now including `aoss:*`), `SmallInstancesOnly`, `SmallRdsOnly` (now including the restore and read-replica actions) |

`p-50xf4zdy`, `p-v98stib2` and `p-rhmnt2ps` are detached and `p-oyvru9pr` stays unattached; all four are due for deletion after 2026-10-23.

## Improvements

| Concern | Before | After |
| --- | --- | --- |
| Adding a region exception | Two identical edits in two Sandbox policies | One edit in `Region-Pin` |
| Where each rule lives | Region pin twice; leave-org deny twice | Each rule in exactly one policy |
| Placement | Org-wide rules on the Sandbox OU | Org-wide rules at Root (cover every current and future account); only sandbox cost caps on the OU |
| Policy slots used | Root 2/5, Sandbox 4/5 | Root 3/5, Sandbox 2/5 |
| Readability | ~40 exemptions overridden by another policy | Every statement in force as written |
| Cost caps | RDS restore/replica bypass; no `aoss:*` | Both closed |
| Identity Center roles | Unprotected | Create, modify, delete and tag denied for member-account principals; Identity Center provisions through its service-linked role, which SCPs do not restrict |
| Root user | Not covered by any SCP | Covered by centralized root access (no member root credentials; see Verification), so no SCP is needed |

## Change sequence

Every rule's new home was attached and verified before the old one was detached, so no account was ever less restricted than before or after.

| Step | Action | Check |
| --- | --- | --- |
| 1 | Backed up all policies and their targets | Seven policies saved with content and attachments |
| 2 | Drafted the three new policy documents | IAM Access Analyzer `validate-policy` returned 0 findings for each |
| 3 | Created `Org-Baseline` and `Region-Pin` and attached both to Root | Probes below |
| 4 | Reduced and renamed `p-8hwmgw9u` to `Sandbox-Guardrails` | Live content equals the draft; probes below |
| 5 | Detached `p-50xf4zdy`, then `p-v98stib2`, then `p-rhmnt2ps`, one at a time | Probes below after each detach |

## Verification

An independent offline evaluator compared the old and new policy sets for every action named in either set, across four request regions, both member accounts and small, large and missing instance classes. It found no unintended loosening: the only region-outcome change is that `s3:GetBucketLocation` is now allowed outside `ap-southeast-1`, which the old narrow pin already intended. The AWS Service Authorization Reference confirms `rds:DatabaseClass` is a condition key for all five RDS actions in `SmallRdsOnly`.

Probes run from the team8 account with the AdministratorAccess permission set, after steps 3, 4 and each detach in step 5:

| Probe | Expected | Observed |
| --- | --- | --- |
| `cloudwatch list-metrics --namespace AWS/CloudFront`, `us-east-1` | Allowed | 26 metrics |
| `cloudwatch get-metric-data`, CloudFront `Requests`, `us-east-1` | Allowed | `Complete`, 13–14 hourly points |
| The same two calls in `eu-west-1` | Denied | Denied by `Region-Pin` (`p-4hlb2c8g`) |
| `ec2 describe-instances`, `ap-southeast-1` | Allowed | Succeeded |
| `ec2 describe-instances`, `eu-west-1` | Denied | Denied by `Region-Pin` |
| `iam tag-role` on the caller's own `AWSReservedSSO_` role | Denied | Denied by `Org-Baseline` (`p-tveef62a`); no tag was written |

A read-only root-credential audit (`sts:AssumeRoot` with the AWS `IAMAuditRootUserCredentials` task policy) found no password, access keys, MFA devices or signing certificates for the root user of either member account, and the organization has `RootCredentialsManagement` and `RootSessions` enabled.

## Not verified

- Behaviour from the `swe5001-poc` account: its SSO session had expired; the policies scope the `us-east-1` metric exception to team8 only.
- `sso:CreateInstance` and the leave/close denies were confirmed in the live policy content, not by calling the actions, because a successful call would change the organization.

## Remaining decisions

- Delete the four detached policies after 2026-10-23.
- Test whether Auto Scaling launches, or `ModifyInstanceAttribute` instance-type changes, bypass `SmallInstancesOnly`.
- Add a scoped `us-east-1` exception for ACM certificates when a CloudFront custom domain is planned.
- Deny `iam:CreateUser` and `iam:CreateAccessKey` once the remaining member-account IAM user with an active access key has moved to SSO or OIDC roles.
