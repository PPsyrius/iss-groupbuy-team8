---
name: deployment-operations
description: Verify or change BlocBuy GitHub Actions deployment settings and AWS backend/frontend infrastructure, including OIDC, SSM, EC2 runtime configuration, S3, CloudFront, Terraform import, health checks, and release evidence. Use for deployment or operator tasks, not ordinary application-code changes.
---

# Deployment operations

Use the [backend EC2 operator guide](../../../docs/deploy/backend-ec2-pipeline-operator-guide.md), [dev CloudFront runbook](../../../docs/deploy/dev-cloudfront-deployment.md), [backend runtime bundle](../../../deployment/backend/README.md), and [production operations runbook](../../../docs/deploy/prod/backend-prod-operations-runbook.md) for commands and current deployment design. This skill is a verification checklist, not a second source of cloud configuration.

## Establish the actual state

- Read `git status --short --branch`, the branch diff, the relevant workflow and its runbook before attributing a change to a PR. Distinguish local edits, pushed branches, merged `dev`, deployed image/artifact and checks made against the live site. A green workflow for an older SHA is not verification of the current branch.
- Verify the AWS account, Region, target instance or distribution, GitHub environment, deploy switch and role before a cloud operation; do not infer live settings from checked-in examples. The dev runbooks target account `402178232802` in `ap-southeast-1`, but confirm the current target in AWS/GitHub. If `gh` or AWS CLI is unavailable, report what was not checked rather than treating a board snapshot or runbook as live state.
- An AWS Organizations SCP explicit deny is not fixed by expanding a local IAM role. Check the Region and the denied action before diagnosing access; Session Manager's optional session document can remain blank for a shell, while `AWS-StartSSHSession` is a port session and is not the console shell. Use a documented alternative only if its permissions are available.

## Keep controls in the right place

- GitHub Actions variables such as `ENABLE_EC2_DEPLOY` and `ENABLE_S3_DEPLOY` gate workflows; they are not automatically container environment variables. Follow the workflow when deciding whether a variable belongs to the repository or a protected environment. Confirm `backend-dev` and `frontend-dev` allow only `dev`, and verify the immutable OIDC subject and scoped role policy before changing trust.
- Dev backend runtime settings live in `/etc/blocbuy/backend.env` on EC2 (`root:root 0600`), consumed by `deployment/backend/compose.yml`. The dev deploy workflow does not render that file. Production `bin/fetch-env.sh` instead recreates it from `/blocbuy/prod/*` SSM parameters; seed newly required keys, including `BLOCBUY_SCHEDULING_ENABLED=false`, before a production release.
- A container restart retains its old environment. After changing the host env file (and fetching SSM values first in prod), use the reviewed deployment path that force-recreates the backend service; verify the resulting container value without printing unrelated variables. Count overdue open teams before explicitly enabling an expiry scheduler, and distinguish durable refund obligations from an active refund worker.
- Never ask for or display an entire runtime env file, token, policy secret or decrypted SSM response as verification. Check only the needed non-secret key, permissions, health or redacted metadata. If a real credential is exposed, do not repeat it; coordinate rotation with the owning service and host configuration.

## Verify the delivered behavior

- For frontend delivery, match the packaged commit to the S3/CloudFront deployment run, check root, deep link, immutable asset and API health, and compare exact root and `/api/*` security-header name/value pairs with the checked-in policy. Distinguish a passed smoke test from unseeded authenticated flows.
- For backend delivery, confirm the immutable ECR digest, SSM command outcome, EC2-local and public health, runtime profile and the expected container setting. Application rollback does not reverse a Flyway migration; review migration compatibility before enabling automatic deploy.
- Do not call the dev frontend edge Terraform-managed merely because its resources exist. `infra/terraform/frontend-stack` becomes the source of truth only after reviewed import and a zero-change plan; until then, the checked-in policy/function and runbook describe the hand-managed edge.
- Record command or workflow link, SHA/digest, target, observed result, limitations and owner in the sprint log and relevant NFR row via [agile-evidence-capture](../agile-evidence-capture/SKILL.md). Do not rewrite historical snapshots or close issues merely because a deployment command was attempted.
