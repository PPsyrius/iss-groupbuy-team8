# Frontend IAM policy examples

Examples, not deployable policies: copy outside the repository, replace `AWS_ACCOUNT_ID`,
`FRONTEND_S3_BUCKET`, `CLOUDFRONT_DISTRIBUTION_ID`, validate with IAM Access Analyzer, attach.

| File | Attach/use with |
|---|---|
| `github-frontend-deploy-trust-policy.json.example` | Trust policy for `BlocBuyGitHubFrontendDeployDev` (OIDC subject = `frontend-dev` environment) |
| `github-frontend-deploy-policy.json.example` | Permissions policy: put/delete/list on the frontend bucket, invalidate the dev distribution |

Do not merge this role with the backend deploy role. Setup: `docs/deploy/dev-cloudfront-deployment.md` §16.

Operational status (2026-09-23): the role exists with the immutable repository subject and the scoped S3/CloudFront policy; `frontend-dev` permits only `dev`; `ci-frontend` run #165 successfully assumed the role, deployed and passed `e2e-dev`. Issues A3 #101 and A4 #102 remain the traceability records under Sprint 2 issue #93.
