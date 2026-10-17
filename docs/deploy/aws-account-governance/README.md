# AWS account governance

This page records the AWS account structure, human and machine access, and organisation guardrails under which both BlocBuy deployments run: the SWE5006 `dev` environment (single EC2 host, S3 and CloudFront) and the SWE5001 `prod` environment (ECS on Fargate). It is the canonical record of accounts and access; the SWE5001 and SWE5006 documents link here instead of restating it. The history of the SCP consolidation lives in its [dated evidence](../../swe5001/evidence/20261016-scp-consolidation/README.md).

Owner: @limjiechao, who created the AWS Organization, the project member account and IAM Identity Center on 2026-08-25 and administers them from the management account. Teammates work inside the member account only.

Verified: 2026-10-16/17 (SGT) with read-only Organizations, IAM Identity Center, Identity Store, IAM and CloudTrail calls from the management account and the project account; see [Verification](#verification).

## Account structure

| OU | Account | Name | Role | Status | Created or joined |
| --- | --- | --- | --- | --- | --- |
| Root | 592877955899 | `jiechao` | Management account: Organizations, Identity Center, SCPs; runs no workloads | Active | Organization created 2026-08-25 14:32 |
| Sandbox (`ou-5yu3-t4qga7jk`, created 2026-08-29) | 402178232802 | `nus-iss-mtech-se34-ft-swe5006-team8` | The BlocBuy project account: SWE5006 `dev` and SWE5001 `prod` | Active | Created by the organization 2026-08-25 15:06; moved into Sandbox when the OU was created |
| Sandbox | 161877660123 | `nus-iss-mtech-se34-ft-swe5001-poc` | Short-lived SWE5001 proof-of-concept account | Suspended (closed) | Created 2026-09-30 06:29 |

Organization `o-37mek1w37u` uses all features (not consolidated billing only). The root has the SCP, RCP and S3 policy types enabled. Trusted access is enabled for IAM Identity Center (`sso.amazonaws.com`, 2026-08-25 16:32) and IAM (`iam.amazonaws.com`, 2026-08-25 22:36), with centralised root access management (root credentials management and root sessions).

## Human access: IAM Identity Center

The organization instance `jiechao-aws` (`ssoins-8210b0c8e54ef88f`, home region ap-southeast-1, identity store `d-9667ae579e`) was created on 2026-08-25 16:32. People sign in through its access portal and receive one-hour sessions from a permission set; the CLI uses the same sessions (`aws sso login`).

| Permission set | AWS managed policy | Session | Created |
| --- | --- | --- | --- |
| AdministratorAccess | AdministratorAccess | 1 h | 2026-08-25 16:37 |
| PowerUserAccess | PowerUserAccess | 1 h | 2026-08-25 16:37 |
| PrivilegedRootActions | AdministratorAccess | 1 h | 2026-08-25 23:03 |

| Group or user | Members | Account | Permission set |
| --- | --- | --- | --- |
| `team8-admin` | Jiechao, Pan, Liu Jin | 402178232802 (project) | AdministratorAccess |
| `team8-developers` | Yorik, Eric | 402178232802 (project) | PowerUserAccess |
| user Jiechao | — | 592877955899 (management) | AdministratorAccess, PrivilegedRootActions |
| user Jiechao | — | 161877660123 (poc, closed) | AdministratorAccess |

Only @limjiechao can reach the management account. The management account has no IAM users and no root access keys, and its root user has MFA enabled.

## Machine access: GitHub OIDC

The project account trusts `token.actions.githubusercontent.com`. GitHub Actions assume short-lived roles instead of storing AWS keys: `BlocBuyGitHubBackendDeploy`, `BlocBuyGitHubEcrPush` and `BlocBuyGitHubFrontendDeployDev` for SWE5006 `dev`, and `blocbuy-swe5001-gh-plan`, `blocbuy-swe5001-gh-deploy` and `blocbuy-swe5001-gh-apply` for SWE5001 (scoped in [the SWE5001 AWS plan](../../swe5001/impl-aws-infrastructure.md)). The EC2 host uses the instance role `BlocBuyEc2Runtime`.

## Guardrails: service control policies

SCPs bind every principal in a member account, including AdministratorAccess sessions and the member root user, so a project administrator cannot lift them. They do not apply to the management account.

| SCP | Attached to | Effect | Document |
| --- | --- | --- | --- |
| `FullAWSAccess` (AWS managed) | Root, Sandbox OU, every account | Baseline allow that the denies below narrow | AWS managed |
| `Org-Baseline` (`p-tveef62a`) | Root | Member accounts cannot leave the organization or close themselves, cannot create their own Identity Center instance, and cannot create, change or delete the Identity Center roles under `aws-reserved/sso.amazonaws.com/` | [org-baseline.json](scp/org-baseline.json) |
| `Region-Pin` (`p-4hlb2c8g`) | Root | Denies everything outside ap-southeast-1 except global services (IAM, Organizations, STS, Budgets, Cost Explorer, Support, CloudFront, Route 53); WAF only in ap-southeast-1 and us-east-1; CloudWatch metric reads in us-east-1 only for the project account (CloudFront metrics) | [region-pin.json](scp/region-pin.json) |
| `Sandbox-Guardrails` (`p-8hwmgw9u`) | Sandbox OU | Cost guardrail by service and size: denies expensive services (for example SageMaker, Bedrock, Redshift, EKS, EMR, Kendra) and allows only `t3`, `t3a` and `t4g` EC2 and `db.t3`/`db.t4g` RDS classes; it does not set a spending limit | [sandbox-guardrails.json](scp/sandbox-guardrails.json) |

This layout dates from @limjiechao's 2026-10-16 consolidation of four overlapping policies; the before and after, the change sequence, the Access Analyzer and offline equivalence checks and the live probes are in the [SCP consolidation evidence](../../swe5001/evidence/20261016-scp-consolidation/README.md) (#530, NFR-67). CloudTrail independently shows `CreatePolicy` ×2 and `AttachPolicy` ×2 at 10:50–10:51 and `DetachPolicy` ×3 at 11:03–11:06. Region exceptions go in `Region-Pin` only. The same evidence records that the member accounts' root users have no password, access keys or MFA devices, because centralised root access manages them.

## Accepted exceptions

The project account also has two IAM users, created on 2026-09-08/09 after Identity Center was in place. Their only BlocBuy-specific policy is `BlocBuySsmPortForwarding`, so they appear to serve SSM port forwarding to the SWE5006 `dev` host; who requested them and why was not checked. They are disclosed here and accepted; no removal is planned.

| IAM user | Created (UTC) | Credentials | Attached policies |
| --- | --- | --- | --- |
| `blocbuy-dev-losgai` | 2026-09-08 | One active access key (created 2026-09-08); no console password | `BlocBuySsmPortForwarding` |
| `blocbuy-dev-ppsyrius` | 2026-09-09 | No access keys; console password with reset required | `IAMUserChangePassword`, `BlocBuySsmPortForwarding`, `AmazonEKSFargatePodExecutionRolePolicy` |

Consequence: "no IAM users and no long-lived keys" holds for the management account and for CI, not for the project account. The access key is bounded by `BlocBuySsmPortForwarding` and by every SCP above. `AmazonEKSFargatePodExecutionRolePolicy` has no use on an IAM user and is unexplained.

## Timeline

| Date (SGT) | Event | Source |
| --- | --- | --- |
| 2026-08-25 14:32 | Organization created from the management account | CloudTrail `CreateOrganization` |
| 2026-08-25 15:06 | Project account created | CloudTrail `CreateAccount`, `list-accounts` |
| 2026-08-25 16:32 | Identity Center enabled; instance `jiechao-aws` created | `EnableAWSServiceAccess`, `describe-instance` |
| 2026-08-25 16:37–23:03 | Three permission sets created | `CreatePermissionSet` ×3 |
| 2026-08-25 to 2026-09-30 | Account assignments for the five members | `CreateAccountAssignment` ×18 |
| 2026-08-25 and 2026-08-29 | First SCPs created and attached; Sandbox OU created | `CreatePolicy`, `AttachPolicy`, `CreateOrganizationalUnit` |
| 2026-09-30 06:29 | Proof-of-concept account created (since closed) | `CreateAccount` |
| 2026-10-16 10:50–11:06 | SCP consolidation | `CreatePolicy`, `AttachPolicy`, `DetachPolicy` |

## Verification

Collected on 2026-10-16/17 by a read-only agent session as `AWSReservedSSO_AdministratorAccess/jiechao` in the management account, and through the project-account SSO session: `organizations describe-organization`, `list-roots`, `list-organizational-units-for-parent`, `list-accounts`, `list-accounts-for-parent`, `list-parents`, `list-policies --filter SERVICE_CONTROL_POLICY`, `describe-policy`, `list-targets-for-policy`, `list-aws-service-access-for-organization`; `sso-admin list-instances`, `describe-instance`, `list-permission-sets`, `describe-permission-set`, `list-managed-policies-in-permission-set`, `list-account-assignments`; `identitystore list-users`, `list-groups`, `list-group-memberships`; `iam list-users`, `list-access-keys`, `get-login-profile`, `list-roles`, `list-open-id-connect-providers`, `get-account-summary`; `cloudtrail lookup-events`. No write call was made. Raw outputs are kept outside the repository because several contain members' email addresses; the three SCP documents above are the unmodified policy content.

Not proven:
- CloudTrail attributes the Organizations and Identity Center setup to the management account's root user, not to a named person; attribution to @limjiechao rests on ownership of that account.
- No `CreateInstance` event was found; the instance creation time comes from `describe-instance`.
- CloudTrail shows three `CreateAccount` requests but there are only two member accounts; the 2026-08-25 14:55 request probably failed, and its status was not checked.
- AWS Budgets was not checked; the guardrails limit services and sizes, not spend.
