# 0007. Hand-managed dev CloudFront now, Terraform import in Sprint 2

Date: 2026-09-16

## Status

Accepted

## Context

The dev entry layer (ALB with the `X-Origin-Secret` listener rule, private S3 bucket with OAC,
the CloudFront distribution with its `/api/*` behavior) was built by hand from
`docs/deploy/dev-cloudfront-deployment.md` and has been live since 2026-09-06.
`infra/terraform/backend-app-stack` covers EC2, ALB, ECR and OIDC and deliberately leaves the dev
CloudFront out; #48 tracks the Terraform work and its state backend
(`blocbuy-tfstate-402178232802`). Only the AWS account holders (@l0sgAi, @PPsyrius) can change
the edge; the frontend owner holds no AWS credentials. Tier 1 needs three edge changes before the
first deploy by 2026-09-20 (SPA fallback function, response-headers policy, deploy role plus
GitHub environment), and the report needs infrastructure-as-code evidence for the frontend edge.

## Decision

Dev edge changes are runbook steps, not Terraform: §14 SPA fallback, §15 response-headers
policy, §16 OIDC deploy role, §17 `frontend-dev` environment, each with its source versioned
under `deployment/frontend/` (`cloudfront/spa-fallback.js`, `cloudfront/response-headers-policy.json`,
`iam/*.json.example`) and each tracked as a blocker the account holder executes (A1 #99, A2 #100,
A3 #101, A4 #102). `infra/terraform/frontend-stack` then imports the same resources in T2-07
(PR-J, paired with @PPsyrius, linked to #48): `import` blocks for the bucket, bucket policy,
public-access block, OAC, function, headers policy and distribution; resources generated with
`plan -generate-config-out` and hand-tidied into `s3.tf` and `cloudfront.tf`;
`terraform plan -detailed-exitcode` exiting 0 as the acceptance test; never `apply` from CI (repo
rule). `X-Origin-Secret` is a `sensitive` variable, which only redacts it from plan and apply
output; the resolved value still lands in the state file once the distribution is imported. So
`frontend-stack` state lives only in the existing remote backend (`blocbuy-tfstate-402178232802`,
`encrypt = true`, DynamoDB lock table, readable by the two account holders alone), `*.tfstate` and
`*.tfplan` stay in `.gitignore`, and no plan output is attached to a PR or issue.

Alternatives rejected: Terraform first (blocks the walking skeleton on infra review and on
credentials the frontend owner does not hold); never import (no IaC evidence for the report, and
the edge stays reproducible only from prose).

## Consequences

Until T2-07 there are two sources of truth for the edge, the runbook plus `deployment/frontend/`
and whatever the console holds, so any change made outside the runbook is drift that the import's
plan will surface as a non-zero exit. T1-03 (2026-09-16, PR-B) verified only file syntax
(`python3 -m json.tool` on the three JSON files, `node --check` on the function); no AWS command
was run, and the §14/§15 curl probes cannot be exercised while the dev backend is down (T1-01
entry, ALB 503). The OIDC trust-policy `sub` format is unconfirmed: the backend example carries
an id-suffixed subject and GitHub documents a plain one, so §16 tells the operator to decode the
subject the `frontend-dev` job actually presents and use that exact value (open until A3
executes); the `dev`-only restriction stays on the environment's deployment branch policy (§17). Execution dates land on the blocker issues and in the runbook
sections, not here. The deploy role is deliberately separate from the backend deploy role
(`deployment/frontend/iam/README.md`). The `--delete`/rollback question for the bucket (ADR 0004)
is decided in T2-07 as a lifecycle rule, since that is when the bucket becomes code.
