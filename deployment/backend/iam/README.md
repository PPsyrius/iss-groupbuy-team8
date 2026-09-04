# IAM policy examples

These files document the minimum AWS access expected by the existing-EC2 backend pipeline. They are
examples, not directly deployable policies: copy them outside the repository, replace every uppercase
placeholder, validate with IAM Access Analyzer, and attach them to the matching role.

| File | Attach/use with |
|---|---|
| `github-ecr-push-trust-policy.json.example` | Trust policy for the GitHub ECR publisher role |
| `github-ecr-push-policy.json.example` | Permissions policy for the GitHub ECR publisher role |
| `github-deploy-trust-policy.json.example` | Trust policy for the GitHub deploy/rollback role |
| `github-ssm-deploy-policy.json.example` | Permissions policy for the GitHub deploy/rollback role |
| `ec2-ecr-pull-policy.json.example` | Permissions policy for the existing EC2 instance role |

Required replacements are `AWS_ACCOUNT_ID`, `AWS_REGION`, `ECR_REPOSITORY`, and—where present—
`BACKEND_EC2_INSTANCE_ID`. The trust examples contain the current repository name, but their OIDC
`sub` values must still be checked against the repository's actual GitHub OIDC subject format.

The EC2 role also needs Systems Manager managed-node permissions, normally through
`AmazonSSMManagedInstanceCore` or the account's Default Host Management Configuration. Do not attach
the GitHub deploy policy to the EC2 role, and do not combine the ECR publisher and SSM deploy roles.

Full setup and security considerations:
[Existing EC2 Backend Deployment Operator Guide](../../../docs/deploy/backend-ec2-pipeline-operator-guide.md).
