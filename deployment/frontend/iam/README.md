# Frontend IAM policy examples

Examples, not deployable policies: copy outside the repository, replace `AWS_ACCOUNT_ID`,
`FRONTEND_S3_BUCKET`, `CLOUDFRONT_DISTRIBUTION_ID`, validate with IAM Access Analyzer, attach.

| File | Attach/use with |
|---|---|
| `github-frontend-deploy-trust-policy.json.example` | Trust policy for `BlocBuyGitHubFrontendDeployDev` (OIDC subject = `frontend-dev` environment) |
| `github-frontend-deploy-policy.json.example` | Permissions policy: put/delete/list on the frontend bucket, invalidate the dev distribution |

Do not merge this role with the backend deploy role. Setup: `docs/deploy/dev-cloudfront-deployment.md` §16.

Tracking: the role is blocker A3 (#101); the `frontend-dev` environment it trusts is blocker A4 (#102).
Both hang off the Sprint 2 container issue #93.
